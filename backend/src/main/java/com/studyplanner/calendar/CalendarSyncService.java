package com.studyplanner.calendar;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studyplanner.goals.PlanException;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
import static com.studyplanner.calendar.CalendarScheduling.*;

/** No database transaction spans a Google request. Revisions preserve later edits/deletions. */
@Service
public class CalendarSyncService {
    private final CalendarSyncStore queue;private final CalendarStore exports;private final GoogleCalendarClient google;private final ObjectMapper mapper;private final Clock clock;
    public CalendarSyncService(CalendarSyncStore queue,CalendarStore exports,GoogleCalendarClient google,ObjectMapper mapper,Clock clock) {
        this.queue=queue;this.exports=exports;this.google=google;this.mapper=mapper;this.clock=clock;
    }
    public CalendarSyncStore.Summary summary(UUID owner){return queue.summary(owner);}
    public CalendarSyncStore.Summary retry(UUID owner){queue.retry(owner);return queue.summary(owner);}
    public boolean processNext() {
        var next=queue.claim();if(next.isEmpty())return false;var job=next.get();
        try { sync(job); }
        catch(PlanException ex) {
            String state=Set.of("CALENDAR_NO_SLOT","CALENDAR_TIME_PASSED","CALENDAR_REMOTE_MISSING","CALENDAR_MARKER_MISMATCH","CALENDAR_SELECTION_INVALID","CALENDAR_PERMISSION_DENIED","CALENDAR_SETTINGS_INVALID","CALENDAR_MAPPING_INVALID").contains(ex.code())?"BLOCKED":"RETRY";
            queue.finish(job,state,ex.getMessage(),null,null,null);
        }catch(Exception ex) {
            // Do not expose provider payloads or credentials; keep the durable job for retry.
            queue.finish(job,"RETRY","Chưa đồng bộ được với Google. App giữ thay đổi của bạn và sẽ thử lại.",null,null,null);
        }
        return true;
    }
    private void sync(CalendarSyncStore.Job job) throws Exception {
        var owner=job.owner();
        if(!google.connected(owner))throw new PlanException(401,"CALENDAR_RECONNECT","Thay đổi đã lưu trong app. Hãy kết nối lại Google Calendar để đồng bộ.");
        if(job.operation().equals("DELETE") && google.calendars(owner).stream().noneMatch(c->c.id().equals(job.calendar())&&c.writable()))
            throw new PlanException(400,"CALENDAR_SELECTION_INVALID","Không còn quyền ghi vào lịch chứa sự kiện cần xóa. Hãy kiểm tra kết nối Google rồi thử lại.");
        var remote=google.event(owner,job.calendar(),job.eventId());
        if(job.operation().equals("DELETE")) {
            if(remote.isPresent() && !remote.get().path("status").asText().equals("cancelled")) {
                verify(job,remote.get());if(!queue.current(job)){queue.finish(job,"RETRY","Có thay đổi mới, đang chờ đồng bộ bản mới nhất.",null,null,null);return;}
                google.delete(owner,job.calendar(),job.eventId(),etag(remote.get()));
            }
            queue.finish(job,"DELETED","Đã xóa sự kiện trên Google Calendar.",null,null,null);return;
        }
        var task=queue.task(owner,job.taskId());
        if(task.isEmpty())throw new PlanException(409,"CALENDAR_REMOTE_CHANGED","Công việc vừa bị xóa. Đang chờ đồng bộ yêu cầu xóa mới nhất.");
        if(remote.isEmpty() || remote.get().path("status").asText().equals("cancelled"))throw new PlanException(409,"CALENDAR_REMOTE_MISSING","Sự kiện đã bị xóa trên Google. App không tự tạo bản sao; hãy kiểm tra lịch Google.");
        var event=remote.get();verify(job,event);var current=task.get();
        var mapping=exports.attempts(owner).stream().filter(a->a.taskId().equals(job.taskId())).findFirst().orElseThrow();
        Task old;try{old=mapper.readValue(mapping.snapshot(),Task.class);}catch(Exception ex){throw new PlanException(409,"CALENDAR_MAPPING_INVALID","Liên kết sự kiện cũ chưa đọc được. App chưa ghi đè Google Calendar; hãy kiểm tra lại liên kết.");}
        boolean timingChanged=!old.date().equals(current.date()) || old.minutes()!=current.minutes()
            || !Instant.parse(event.path("start").path("dateTime").asText()).equals(mapping.start())
            || !Instant.parse(event.path("end").path("dateTime").asText()).equals(mapping.end());
        Slot slot=new Slot(current,mapping.start(),mapping.end(),null);
        if(timingChanged)slot=place(job,current,mapping);
        var body=mapper.createObjectNode().put("summary",current.subject()+" · "+current.title());
        if(timingChanged) {
            var s=settings(job);
            var busy=busy(job,s,slot.start().minusSeconds(s.breakMinutes()*60L),slot.end().plusSeconds(s.breakMinutes()*60L));
            var bufferedStart=slot.start().minusSeconds(s.breakMinutes()*60L);var bufferedEnd=slot.end().plusSeconds(s.breakMinutes()*60L);
            if(busy.stream().anyMatch(b->b.overlaps(bufferedStart,bufferedEnd)) || !CalendarService.allowed(s,slot))throw noSlot();
            body.putObject("start").put("dateTime",slot.start().toString()).put("timeZone",s.timezone());
            body.putObject("end").put("dateTime",slot.end().toString()).put("timeZone",s.timezone());
        }
        if(!queue.current(job)){queue.finish(job,"RETRY","Có thay đổi mới, đang chờ đồng bộ bản mới nhất.",null,null,null);return;}
        var updated=google.patch(owner,job.calendar(),job.eventId(),body,etag(event));
        verify(job,updated);
        queue.finish(job,"IN_SYNC","Đã cập nhật sự kiện trên Google Calendar.",slot,mapper.writeValueAsString(current),updated.path("htmlLink").asText(mapping.url()));
    }
    private Slot place(CalendarSyncStore.Job job,Task task,CalendarStore.Attempt mapping) throws Exception {
        var s=settings(job);var zone=ZoneId.of(s.timezone());var today=LocalDate.now(clock.withZone(zone));
        if(task.date().isBefore(today) || !task.date().isBefore(task.examDate()))throw new PlanException(409,"CALENDAR_TIME_PASSED","Ngày mới đã qua hoặc không còn trước ngày thi. Sự kiện Google cũ được giữ để bạn kiểm tra.");
        var from=task.date().atStartOfDay(zone).toInstant();var to=task.date().plusDays(1).atStartOfDay(zone).toInstant();
        var occupied=busy(job,s,from.minusSeconds(s.breakMinutes()*60L),to.plusSeconds(s.breakMinutes()*60L));
        var start=task.date().atTime(mapping.start().atZone(zone).toLocalTime()).atZone(zone).toInstant();
        var preferred=new Slot(task,start,start.plusSeconds(task.minutes()*60L),null);
        if(!start.isBefore(clock.instant()) && CalendarService.allowed(s,preferred)
            && occupied.stream().noneMatch(b->b.overlaps(preferred.start().minusSeconds(s.breakMinutes()*60L),preferred.end().plusSeconds(s.breakMinutes()*60L))))return preferred;
        // Completed tasks still need their date/duration edits synchronized; allocator must not skip them.
        var candidate=new Task(task.id(),task.goalId(),task.title(),task.subject(),task.date(),task.minutes(),false,task.examDate());
        var planned=schedule(s,List.of(candidate),occupied,clock.instant()).getFirst();
        if(planned.start()==null)throw noSlot();
        return new Slot(task,planned.start(),planned.end(),null);
    }
    private Settings settings(CalendarSyncStore.Job job) throws Exception {
        var original=exports.settings(job.owner()).map(value->{try{return mapper.readValue(value,Settings.class);}catch(Exception ex){throw new PlanException(409,"CALENDAR_SELECTION_INVALID","Hãy lưu lại giờ học và giờ sinh hoạt trước khi đồng bộ.");}}).orElseGet(CalendarScheduling::defaults);
        var ids=new LinkedHashSet<>(original.busyCalendarIds());if(!original.targetCalendarId().isBlank()&&!original.targetCalendarId().equals(job.calendar()))ids.add(original.targetCalendarId());ids.remove(job.calendar());
        var s=new Settings(original.timezone(),original.days(),original.blocks(),original.breakMinutes(),List.copyOf(ids),job.calendar());validate(s);
        var calendars=google.calendars(job.owner());
        if(calendars.stream().noneMatch(c->c.id().equals(job.calendar())&&c.writable()) || CalendarService.calendarIds(s).stream().anyMatch(id->calendars.stream().noneMatch(c->c.id().equals(id))))
            throw new PlanException(400,"CALENDAR_SELECTION_INVALID","Không còn đọc/ghi đủ các lịch đã chọn. Hãy kiểm tra kết nối và danh sách lịch rồi thử lại.");
        return s;
    }
    private List<Busy> busy(CalendarSyncStore.Job job,Settings s,Instant from,Instant to) {
        var result=new ArrayList<>(google.busyExceptEvent(job.owner(),job.calendar(),job.eventId(),from,to,s.timezone()));
        if(!s.busyCalendarIds().isEmpty())result.addAll(google.busy(job.owner(),s.busyCalendarIds(),from,to,s.timezone()));
        result.addAll(queue.reservations(job.owner(),job.taskId(),from,to));return result;
    }
    private static String etag(JsonNode event) {
        var etag=event.path("etag").asText();if(etag.isBlank())throw new PlanException(502,"CALENDAR_UNAVAILABLE","Google chưa trả phiên bản sự kiện để cập nhật an toàn. App sẽ thử lại.");return etag;
    }
    private static void verify(CalendarSyncStore.Job job,JsonNode event) {
        var marker=event.path("extendedProperties").path("private");
        if(!event.path("id").asText().equals(job.eventId()) || !marker.path("studyPlannerTask").asText().equals(job.taskId().toString()) || !marker.path("studyPlannerOwner").asText().equals(job.owner().toString()))
            throw new PlanException(409,"CALENDAR_MARKER_MISMATCH","Sự kiện không còn khớp liên kết của app. Chưa cập nhật/xóa; hãy kiểm tra Google Calendar.");
    }
    private static PlanException noSlot(){return new PlanException(422,"CALENDAR_NO_SLOT","Ngày mới không còn khung giờ phù hợp. Sự kiện cũ được giữ; hãy chỉnh ngày/giờ học rồi thử đồng bộ lại.");}
}
