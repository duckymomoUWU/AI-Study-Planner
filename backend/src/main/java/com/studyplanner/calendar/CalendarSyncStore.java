package com.studyplanner.calendar;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import static com.studyplanner.calendar.CalendarScheduling.*;

@Repository
public class CalendarSyncStore {
    private final JdbcTemplate jdbc;
    public CalendarSyncStore(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public record Job(UUID owner,UUID taskId,String calendar,String eventId,String title,String operation,long revision,int attempts,UUID token,Instant leaseUntil) {}
    public record Item(UUID taskId,String title,String operation,String status,String message,int attempts,Instant updatedAt) {}
    public record Summary(int pending,int blocked,int synced,int deleted,List<Item> items) {}
    @Transactional
    public Optional<Job> claim() {
        UUID token=UUID.randomUUID();
        return jdbc.query("""
            WITH due AS (
                SELECT user_id,task_id FROM calendar_sync_jobs WHERE status IN ('PENDING','RETRY') AND next_attempt_at<=now()
                AND (lease_until IS NULL OR lease_until<now()) ORDER BY next_attempt_at,updated_at FOR UPDATE SKIP LOCKED LIMIT 1
            ) UPDATE calendar_sync_jobs j SET lease_token=?,lease_until=now()+interval '5 minutes',attempts=j.attempts+1
              FROM due d WHERE j.user_id=d.user_id AND j.task_id=d.task_id
            RETURNING j.user_id,j.task_id,j.calendar_id,j.event_id,j.title,j.operation,j.revision,j.attempts,j.lease_token,j.lease_until
            """,(rs,n)->new Job(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getLong(7),rs.getInt(8),rs.getObject(9,UUID.class),rs.getTimestamp(10).toInstant()),token).stream().findFirst();
    }
    public boolean current(Job job) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM calendar_sync_jobs WHERE user_id=? AND task_id=? AND revision=? AND lease_token=? AND lease_until>now())",Boolean.class,
            job.owner(),job.taskId(),job.revision(),job.token()));
    }
    public Optional<Task> task(UUID owner,UUID task) {
        return jdbc.query("""
            SELECT t.id,t.goal_id,t.title,g.subject,t.scheduled_date,t.estimated_minutes,t.completed,g.exam_date
            FROM study_tasks t JOIN study_goals g ON g.id=t.goal_id WHERE g.user_id=? AND t.id=?
            """,(rs,n)->new Task(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),rs.getString(4),rs.getObject(5,LocalDate.class),rs.getInt(6),rs.getBoolean(7),rs.getObject(8,LocalDate.class)),owner,task).stream().findFirst();
    }
    public List<Busy> reservations(UUID owner,UUID except,Instant from,Instant to) {
        return jdbc.query("SELECT starts_at,ends_at FROM calendar_exports WHERE user_id=? AND task_id<>? AND starts_at<? AND ends_at>?",
            (rs,n)->new Busy(rs.getTimestamp(1).toInstant(),rs.getTimestamp(2).toInstant()),owner,except,java.sql.Timestamp.from(to),java.sql.Timestamp.from(from));
    }
    @Transactional
    public boolean finish(Job job,String status,String message,Slot slot,String snapshot,String url) {
        var rows=jdbc.queryForList("SELECT revision,lease_token FROM calendar_sync_jobs WHERE user_id=? AND task_id=? FOR UPDATE",job.owner(),job.taskId());
        if(rows.isEmpty() || !job.token().equals(rows.getFirst().get("lease_token")))return false;
        if(((Number)rows.getFirst().get("revision")).longValue()!=job.revision()) {
            jdbc.update("UPDATE calendar_sync_jobs SET lease_token=NULL,lease_until=NULL,next_attempt_at=now() WHERE user_id=? AND task_id=?",job.owner(),job.taskId());return false;
        }
        if(status.equals("DELETED"))jdbc.update("DELETE FROM calendar_exports WHERE user_id=? AND task_id=?",job.owner(),job.taskId());
        if(status.equals("IN_SYNC") && slot!=null)jdbc.update("UPDATE calendar_exports SET starts_at=?,ends_at=?,task_snapshot=?,event_url=?,status='CONFIRMED' WHERE user_id=? AND task_id=?",
            java.sql.Timestamp.from(slot.start()),java.sql.Timestamp.from(slot.end()),snapshot,url,job.owner(),job.taskId());
        long delay=Math.min(900,30L*(1L<<Math.min(job.attempts(),5)));
        jdbc.update("UPDATE calendar_sync_jobs SET status=?,message=?,lease_token=NULL,lease_until=NULL,next_attempt_at=now()+(? * interval '1 second'),updated_at=now() WHERE user_id=? AND task_id=?",
            status,message,delay,job.owner(),job.taskId());return true;
    }
    public Summary summary(UUID owner) {
        var counts=new HashMap<String,Integer>();jdbc.query("SELECT status,count(*)::int FROM calendar_sync_jobs WHERE user_id=? GROUP BY status",
            (org.springframework.jdbc.core.RowCallbackHandler)rs->counts.put(rs.getString(1),rs.getInt(2)),owner);
        var items=jdbc.query("SELECT task_id,title,operation,status,message,attempts,updated_at FROM calendar_sync_jobs WHERE user_id=? ORDER BY CASE WHEN status IN ('PENDING','RETRY','BLOCKED') THEN 0 ELSE 1 END,updated_at DESC LIMIT 100",
            (rs,n)->new Item(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getInt(6),rs.getTimestamp(7).toInstant()),owner);
        return new Summary(counts.getOrDefault("PENDING",0)+counts.getOrDefault("RETRY",0),counts.getOrDefault("BLOCKED",0),counts.getOrDefault("IN_SYNC",0),counts.getOrDefault("DELETED",0),items);
    }
    @Transactional
    public void retry(UUID owner) {jdbc.update("UPDATE calendar_sync_jobs SET status='PENDING',attempts=0,next_attempt_at=now(),message='',updated_at=now() WHERE user_id=? AND status IN ('RETRY','BLOCKED')",owner);}
}
