package com.studyplanner.calendar;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.*;
import org.springframework.transaction.annotation.*;

@Repository
public class CalendarStore {
    private final JdbcTemplate jdbc;
    public CalendarStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<String> tokens(UUID owner) { return jdbc.query("SELECT encrypted_tokens FROM calendar_connections WHERE user_id=?", (rs,n) -> rs.getString(1), owner).stream().findFirst(); }
    public void tokens(UUID owner, String value) { jdbc.update("INSERT INTO calendar_connections(user_id,encrypted_tokens) VALUES (?,?) ON CONFLICT(user_id) DO UPDATE SET encrypted_tokens=excluded.encrypted_tokens,updated_at=now()", owner, value); }
    public void disconnect(UUID owner) { jdbc.update("DELETE FROM calendar_connections WHERE user_id=?", owner); }
    public Optional<String> settings(UUID owner) { return jdbc.query("SELECT settings FROM calendar_preferences WHERE user_id=?", (rs,n) -> rs.getString(1), owner).stream().findFirst(); }
    public void settings(UUID owner, String json) { jdbc.update("INSERT INTO calendar_preferences(user_id,settings) VALUES (?,?) ON CONFLICT(user_id) DO UPDATE SET settings=excluded.settings,version=calendar_preferences.version+1", owner, json); }
    public void lock(UUID owner) { jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", owner.toString()); }
    public void lockOwner(UUID owner) { jdbc.queryForList("SELECT id FROM app_users WHERE id=? FOR NO KEY UPDATE",owner); }
    public List<CalendarScheduling.Task> tasks(UUID owner, LocalDate from, LocalDate to) {
        return jdbc.query("""
            SELECT t.id,t.goal_id,t.title,g.subject,t.scheduled_date,t.estimated_minutes,t.completed,g.exam_date
            FROM study_tasks t JOIN study_goals g ON g.id=t.goal_id
            WHERE g.user_id=? AND t.scheduled_date>=? AND t.scheduled_date<=?
            ORDER BY t.scheduled_date,t.id
            """, (rs,n) -> new CalendarScheduling.Task(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),rs.getString(4),
                rs.getObject(5,LocalDate.class),rs.getInt(6),rs.getBoolean(7),rs.getObject(8,LocalDate.class)), owner, from, to);
    }
    public void preview(UUID owner, UUID id, String payload, Instant expiry) {
        jdbc.update("DELETE FROM calendar_previews WHERE user_id=? AND expires_at<now()", owner);
        jdbc.update("INSERT INTO calendar_previews(id,user_id,payload,expires_at) VALUES (?,?,?,?)",id,owner,payload,java.sql.Timestamp.from(expiry));
    }
    public Optional<String> preview(UUID owner, UUID id) { return jdbc.query("SELECT payload FROM calendar_previews WHERE user_id=? AND id=? AND expires_at>now() FOR UPDATE", (rs,n) -> rs.getString(1),owner,id).stream().findFirst(); }
    public Set<UUID> exported(UUID owner) { return new HashSet<>(jdbc.query("SELECT task_id FROM calendar_exports WHERE user_id=? AND status='CONFIRMED'", (rs,n) -> rs.getObject(1,UUID.class), owner)); }
    public List<CalendarScheduling.Busy> reservations(UUID owner, Instant from, Instant to) {
        return jdbc.query("SELECT starts_at,ends_at FROM calendar_exports WHERE user_id=? AND starts_at<? AND ends_at>?", (rs,n) -> new CalendarScheduling.Busy(rs.getTimestamp(1).toInstant(),rs.getTimestamp(2).toInstant()), owner,java.sql.Timestamp.from(to),java.sql.Timestamp.from(from));
    }
    public record Attempt(UUID taskId,String calendarId,String eventId,Instant start,Instant end,String snapshot,String status,String url) {}
    public List<Attempt> attempts(UUID owner) {
        return jdbc.query("SELECT task_id,calendar_id,event_id,starts_at,ends_at,task_snapshot,status,event_url FROM calendar_exports WHERE user_id=?",(rs,n)->new Attempt(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getTimestamp(4).toInstant(),rs.getTimestamp(5).toInstant(),rs.getString(6),rs.getString(7),rs.getString(8)),owner);
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void begin(UUID owner, UUID task, String calendar, String event, Instant start, Instant end, String snapshot) {
        jdbc.update("INSERT INTO calendar_exports(user_id,task_id,calendar_id,event_id,starts_at,ends_at,event_url,task_snapshot,status) VALUES (?,?,?,?,?,?,'',?,'PENDING') ON CONFLICT(user_id,task_id) DO NOTHING",owner,task,calendar,event,java.sql.Timestamp.from(start),java.sql.Timestamp.from(end),snapshot);
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void confirmed(UUID owner, UUID task, String url) {
        jdbc.update("UPDATE calendar_exports SET status='CONFIRMED',event_url=? WHERE user_id=? AND task_id=?",url,owner,task);
    }
    public void lockTasks(UUID owner, LocalDate from, LocalDate to) {
        jdbc.queryForList("SELECT t.id FROM study_tasks t JOIN study_goals g ON g.id=t.goal_id WHERE g.user_id=? AND t.scheduled_date>=? AND t.scheduled_date<=? ORDER BY g.id,t.id FOR UPDATE OF g,t",owner,from,to);
    }
}
