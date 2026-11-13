package com.studyplanner.calendar;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studyplanner.goals.PlanException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import static com.studyplanner.calendar.CalendarScheduling.*;

@Service
public class CalendarService {
    private final CalendarStore store; private final GoogleCalendarClient google; private final ObjectMapper mapper; private final Clock clock;
    public CalendarService(CalendarStore store, GoogleCalendarClient google, ObjectMapper mapper, Clock clock) { this.store=store; this.google=google; this.mapper=mapper; this.clock=clock; }
    public record Preview(UUID id,LocalDate from,LocalDate to,Settings settings,List<Slot> slots,int alreadyExported,Instant expiresAt) {}
    public record ExportResult(UUID taskId,String title,String status,String message,String url) {}
    public record Confirmation(List<ExportResult> results,int confirmed,int failed) {}
    public Settings settings(UUID owner) { return store.settings(owner).map(value->read(value,Settings.class)).orElseGet(CalendarScheduling::defaults); }
    @Transactional
    public Settings settings(UUID owner,Settings s) { validate(s); store.lock(owner); store.settings(owner,write(s)); return s; }
    public Map<String,Object> status(UUID owner) { return Map.of("configured",google.configured(),"connected",google.connected(owner),"settings",settings(owner)); }
    public List<GoogleCalendarClient.CalendarInfo> calendars(UUID owner) { return google.calendars(owner); }
    @Transactional
    public void disconnect(UUID owner) { store.lock(owner); google.disconnect(owner); }

    @Transactional
    public Preview preview(UUID owner,LocalDate from,UUID goalId) {
        store.lock(owner); Settings s=settings(owner); validateSelection(owner,s);
        LocalDate today=clock.instant().atZone(ZoneId.of(s.timezone())).toLocalDate();
        if(from==null || from.isBefore(today.minusDays(6)) || from.isAfter(today.plusDays(90))) throw new PlanException(400,"CALENDAR_RANGE_INVALID","Chọn tuần hiện tại hoặc trong 90 ngày tới.");
        LocalDate to=from.plusDays(6); var all=store.tasks(owner,from,to).stream().filter(t->goalId==null || t.goalId().equals(goalId)).toList();
        if(all.size()>100) throw new PlanException(400,"CALENDAR_TOO_MANY_TASKS","Chỉ xuất tối đa 100 công việc mỗi lần. Hãy lọc theo mục tiêu.");
        var attempts=store.attempts(owner); var pending=new HashMap<UUID,CalendarStore.Attempt>();
        for(var attempt:attempts) if(attempt.status().equals("PENDING")) pending.put(attempt.taskId(),attempt);
        var exported=store.exported(owner);
        var tasks=all.stream().filter(t->!exported.contains(t.id()) && !pending.containsKey(t.id())).toList();
        Instant begin=from.atStartOfDay(ZoneId.of(s.timezone())).toInstant(), end=to.plusDays(1).atStartOfDay(ZoneId.of(s.timezone())).toInstant();
        var busy=new ArrayList<>(google.busy(owner,calendarIds(s),begin,end,s.timezone())); busy.addAll(store.reservations(owner,begin,end));
        var slots=new ArrayList<>(schedule(s,tasks,busy,clock.instant()));
        for(var task:all) if(pending.containsKey(task.id()) && !task.completed() && !task.date().isBefore(today)) {
            var a=pending.get(task.id());
            if(!a.calendarId().equals(s.targetCalendarId()) || !write(task).equals(a.snapshot())) throw new PlanException(409,"CALENDAR_PENDING_CHANGED","Có lần xuất trước chưa xác định kết quả và dữ liệu đã đổi. Hãy kiểm tra sự kiện trên Google Calendar trước khi xuất lại.");
            slots.add(new Slot(task,a.start(),a.end(),null));
        }
        slots.sort(Comparator.comparing((Slot slot)->slot.task().date()).thenComparing(slot->slot.task().id()));
        var preview=new Preview(UUID.randomUUID(),from,to,s,List.copyOf(slots),(int)all.stream().filter(t->exported.contains(t.id())).count(),clock.instant().plusSeconds(900));
        store.preview(owner,preview.id(),write(preview),preview.expiresAt()); return preview;
    }

    @Transactional
    public Confirmation confirm(UUID owner,UUID id) {
        // Match app mutation lock order. NO KEY UPDATE permits the independent receipt's FK check.
        store.lockOwner(owner);
        store.lock(owner);
        var preview=store.preview(owner,id).map(value->read(value,Preview.class)).orElseThrow(()->new PlanException(409,"CALENDAR_PREVIEW_EXPIRED","Bản xem trước đã hết hạn. Hãy xếp giờ lại."));
        if(!preview.expiresAt().isAfter(clock.instant())) throw new PlanException(409,"CALENDAR_PREVIEW_EXPIRED","Bản xem trước đã hết hạn.");
        if(!write(settings(owner)).equals(write(preview.settings()))) throw new PlanException(409,"CALENDAR_SETTINGS_CHANGED","Giờ sinh hoạt đã thay đổi. Hãy xếp giờ lại.");
        if(preview.slots().isEmpty() || preview.slots().stream().anyMatch(slot->slot.start()==null)) throw new PlanException(422,"CALENDAR_UNSCHEDULED","Cần xếp đủ giờ cho các buổi học trước khi xuất lịch.");
        validateSelection(owner,preview.settings());
        store.lockTasks(owner,preview.from(),preview.to());
        var current=new HashMap<UUID,Task>(); for(var task:store.tasks(owner,preview.from(),preview.to())) current.put(task.id(),task);
        var exported=store.exported(owner); var results=new ArrayList<ExportResult>();
        // Successful remote writes are durably receipted separately; later failures cannot roll them back.
        for(var slot:preview.slots()) {
            var task=slot.task();
            if(exported.contains(task.id())) { results.add(new ExportResult(task.id(),task.title(),"CONFIRMED","Đã xuất trước đó.","")); continue; }
            if(!task.equals(current.get(task.id())) || task.completed()) { results.add(failed(task,"Công việc đã thay đổi hoặc bị xóa. Hãy xếp giờ lại.")); continue; }
            try { results.add(export(owner,preview.settings(),slot)); }
            catch(PlanException ex) { results.add(failed(task,ex.getMessage())); }
        }
        int count=(int)results.stream().filter(r->r.status().equals("CONFIRMED")).count();
        return new Confirmation(List.copyOf(results),count,results.size()-count);
    }

    private ExportResult export(UUID owner,Settings s,Slot slot) {
        Task task=slot.task(); String eventId=eventId(owner,task.id());
        var attempt=store.attempts(owner).stream().filter(a->a.taskId().equals(task.id())).findFirst();
        if(attempt.isPresent()) {
            var a=attempt.get();
            if(!a.calendarId().equals(s.targetCalendarId()) || !a.start().equals(slot.start()) || !a.end().equals(slot.end()) || !a.snapshot().equals(write(task)))
                throw new PlanException(409,"CALENDAR_EXPORT_CHANGED","Lần xuất trước dùng lịch hoặc giờ khác. Không tạo thêm bản sao.");
        }
        var existing=google.event(owner,s.targetCalendarId(),eventId);
        if(existing.isPresent()) return recover(owner,s,slot,eventId,existing.get());
        if(slot.start().isBefore(clock.instant())) throw new PlanException(409,"CALENDAR_TIME_PASSED","Giờ dự kiến đã qua. Hãy xếp giờ lại.");
        // Re-check Google immediately before each insertion, fail closed on any missing calendar.
        var busy=google.busy(owner,calendarIds(s),slot.start().minusSeconds(s.breakMinutes()*60L),slot.end().plusSeconds(s.breakMinutes()*60L),s.timezone());
        Instant bufferedStart=slot.start().minusSeconds(s.breakMinutes()*60L), bufferedEnd=slot.end().plusSeconds(s.breakMinutes()*60L);
        if(busy.stream().anyMatch(b->b.overlaps(bufferedStart,bufferedEnd))) throw new PlanException(409,"CALENDAR_TIME_CONFLICT","Khung giờ vừa có công việc khác. Hãy xếp giờ lại.");
        if(!allowed(s,slot)) throw new PlanException(409,"CALENDAR_TIME_CONFLICT","Khung giờ không còn phù hợp với giờ sinh hoạt.");
        store.begin(owner,task.id(),s.targetCalendarId(),eventId,slot.start(),slot.end(),write(task));
        ObjectNode body=mapper.createObjectNode().put("id",eventId).put("summary",task.subject()+" · "+task.title());
        body.putObject("start").put("dateTime",slot.start().toString()).put("timeZone",s.timezone());
        body.putObject("end").put("dateTime",slot.end().toString()).put("timeZone",s.timezone());
        body.putObject("extendedProperties").putObject("private").put("studyPlannerTask",task.id().toString()).put("studyPlannerOwner",owner.toString());
        return recover(owner,s,slot,eventId,google.insert(owner,s.targetCalendarId(),body));
    }
    private ExportResult recover(UUID owner,Settings s,Slot slot,String id,JsonNode event) {
        var marker=event.path("extendedProperties").path("private");
        boolean matches=event.path("id").asText().equals(id) && !event.path("status").asText().equals("cancelled")
            && marker.path("studyPlannerTask").asText().equals(slot.task().id().toString()) && marker.path("studyPlannerOwner").asText().equals(owner.toString());
        try { matches=matches && Instant.parse(event.path("start").path("dateTime").asText()).equals(slot.start()) && Instant.parse(event.path("end").path("dateTime").asText()).equals(slot.end()); }
        catch(Exception ex) { matches=false; }
        if(!matches) throw new PlanException(409,"CALENDAR_REMOTE_CHANGED","Sự kiện đã đổi trên Google. Chưa tạo thêm sự kiện; hãy kiểm tra lại.");
        String url=event.path("htmlLink").asText();
        store.begin(owner,slot.task().id(),s.targetCalendarId(),id,slot.start(),slot.end(),write(slot.task())); store.confirmed(owner,slot.task().id(),url);
        return new ExportResult(slot.task().id(),slot.task().title(),"CONFIRMED","Đã lưu trên Google Calendar.",url);
    }
    static boolean allowed(Settings s,Slot slot) {
        var zone=ZoneId.of(s.timezone()); var date=slot.task().date();
        var d=s.days().stream().filter(day->day.weekday()==date.getDayOfWeek().getValue()).findFirst().orElseThrow();
        return d.enabled() && !slot.start().isBefore(date.atTime(LocalTime.parse(d.start())).atZone(zone).toInstant())
            && !slot.end().isAfter(date.atTime(LocalTime.parse(d.end())).atZone(zone).toInstant())
            && blocked(s,date).stream().noneMatch(b->b.overlaps(slot.start(),slot.end()));
    }
    private void validateSelection(UUID owner,Settings s) {
        validate(s); var calendars=google.calendars(owner);
        var target=calendars.stream().filter(c->c.id().equals(s.targetCalendarId())).findFirst();
        if(target.isEmpty()) throw new PlanException(400,"CALENDAR_SELECTION_INVALID","Lịch nhận đã chọn không còn trong danh sách hoặc chưa được chọn. Hãy tải lại danh sách và chọn lịch nhận.");
        if(!target.get().writable()) throw new PlanException(400,"CALENDAR_SELECTION_INVALID","Bạn không có quyền ghi vào lịch nhận đã chọn. Hãy chọn lịch có quyền ghi.");
        long unavailable=s.busyCalendarIds().stream().distinct().filter(id->calendars.stream().noneMatch(c->c.id().equals(id))).count();
        if(unavailable>0) throw new PlanException(400,"CALENDAR_SELECTION_INVALID",
            "Có "+unavailable+" lịch kiểm tra giờ bận không còn trong danh sách Google. Hãy tải lại kết nối, kiểm tra mục Lịch cần kiểm tra giờ bận và bỏ chọn lịch không còn dùng hoặc khôi phục quyền truy cập.",Map.of("unavailableCalendarCount",(int)unavailable));
    }
    static List<String> calendarIds(Settings s) { var ids=new LinkedHashSet<>(s.busyCalendarIds()); ids.add(s.targetCalendarId()); return List.copyOf(ids); }
    static String eventId(UUID owner,UUID task) { try { return "sp"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((owner+":"+task).getBytes(StandardCharsets.UTF_8))); } catch(Exception ex) { throw new IllegalStateException(ex); } }
    private ExportResult failed(Task task,String message) { return new ExportResult(task.id(),task.title(),"FAILED",message,""); }
    private String write(Object value) { try { return mapper.writeValueAsString(value); } catch(Exception ex) { throw new IllegalStateException("Calendar serialization failed"); } }
    private <T> T read(String value,Class<T> type) { try { return mapper.readValue(value,type); } catch(Exception ex) { throw new PlanException(409,"CALENDAR_DATA_INVALID","Cấu hình lịch không hợp lệ. Hãy lưu lại giờ sinh hoạt."); } }
}
