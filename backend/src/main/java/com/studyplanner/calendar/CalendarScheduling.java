package com.studyplanner.calendar;

import com.studyplanner.goals.PlanException;
import java.time.*;
import java.util.*;

/** Deterministic placement only. No provider calls, no AI, no splitting or date changes. */
public final class CalendarScheduling {
    private CalendarScheduling() {}
    public record DayHours(int weekday, boolean enabled, String start, String end) {}
    public record Block(String label, String start, String end, List<Integer> weekdays) {}
    public record Settings(String timezone, List<DayHours> days, List<Block> blocks,
                           int breakMinutes, List<String> busyCalendarIds, String targetCalendarId) {}
    public record Busy(Instant start, Instant end) {
        public boolean overlaps(Instant from, Instant to) { return start.isBefore(to) && end.isAfter(from); }
    }
    public record Task(UUID id, UUID goalId, String title, String subject, LocalDate date,
                       int minutes, boolean completed, LocalDate examDate) {}
    public record Slot(Task task, Instant start, Instant end, String reason) {}

    public static Settings defaults() {
        var all = List.of(1, 2, 3, 4, 5, 6, 7);
        return new Settings("Asia/Ho_Chi_Minh", all.stream().map(day -> new DayHours(day, true, "08:00", "22:00")).toList(),
            List.of(new Block("Ngủ", "23:00", "07:00", all), new Block("Ăn sáng", "07:30", "08:00", all),
                new Block("Ăn trưa / nghỉ trưa", "12:00", "13:00", all), new Block("Ăn tối", "18:00", "19:00", all)),
            10, List.of(), "");
    }

    public static void validate(Settings s) {
        try {
            Objects.requireNonNull(s); ZoneId.of(s.timezone());
            if (s.days() == null || s.days().size() != 7 || s.blocks() == null || s.blocks().size() > 30
                    || s.breakMinutes() < 0 || s.breakMinutes() > 60 || s.busyCalendarIds() == null
                    || s.busyCalendarIds().size() > 49 || s.targetCalendarId() == null || s.targetCalendarId().length() > 1024) throw new IllegalArgumentException();
            Set<Integer> found = new HashSet<>();
            for (var d : s.days()) {
                if (d.weekday() < 1 || d.weekday() > 7 || !found.add(d.weekday()) || !time(d.start()).isBefore(time(d.end()))) throw new IllegalArgumentException();
            }
            for (var b : s.blocks()) {
                if (b.label() == null || b.label().length() > 100 || time(b.start()).equals(time(b.end())) || b.weekdays() == null
                        || b.weekdays().isEmpty() || b.weekdays().stream().anyMatch(day -> day == null || day < 1 || day > 7)) throw new IllegalArgumentException();
            }
            if (s.busyCalendarIds().stream().anyMatch(id -> id == null || id.isBlank() || id.length() > 1024)) throw new IllegalArgumentException();
        } catch (RuntimeException ex) { throw new PlanException(400, "CALENDAR_SETTINGS_INVALID", "Kiểm tra lại giờ học, giờ nghỉ và múi giờ."); }
    }
    private static LocalTime time(String value) {
        if (value == null || !value.matches("\\d{2}:\\d{2}")) throw new IllegalArgumentException();
        return LocalTime.parse(value);
    }
    private static Instant ceilMinute(Instant value) {
        Instant minute=value.truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        return minute.isBefore(value)?minute.plusSeconds(60):minute;
    }
    public static List<Busy> blocked(Settings s, LocalDate date) {
        var result = new ArrayList<Busy>(); var zone = ZoneId.of(s.timezone());
        // Yesterday's overnight sleep also blocks the beginning of this day.
        for (var day : List.of(date.minusDays(1), date)) for (var b : s.blocks()) {
            if (!b.weekdays().contains(day.getDayOfWeek().getValue())) continue;
            LocalTime start = time(b.start()), end = time(b.end());
            result.add(new Busy(day.atTime(start).atZone(zone).toInstant(),
                (end.isAfter(start) ? day : day.plusDays(1)).atTime(end).atZone(zone).toInstant()));
        }
        return result;
    }
    public static List<Slot> schedule(Settings s, List<Task> tasks, List<Busy> remoteBusy, Instant now) {
        validate(s); var zone = ZoneId.of(s.timezone());
        var busy = new ArrayList<>(remoteBusy); var result = new ArrayList<Slot>();
        for (var task : tasks.stream().sorted(Comparator.comparing(Task::date).thenComparing(Task::id)).toList()) {
            if (task.completed() || task.date().isBefore(now.atZone(zone).toLocalDate())) continue;
            var hours = s.days().stream().filter(d -> d.weekday() == task.date().getDayOfWeek().getValue()).findFirst().orElseThrow();
            if (!hours.enabled() || !task.date().isBefore(task.examDate())) { result.add(new Slot(task, null, null, "Ngày này không có giờ học hợp lệ.")); continue; }
            Instant cursor = task.date().atTime(time(hours.start())).atZone(zone).toInstant();
            if (cursor.isBefore(now)) cursor = ceilMinute(now);
            Instant limit = task.date().atTime(time(hours.end())).atZone(zone).toInstant();
            var occupied = new ArrayList<>(blocked(s, task.date()));
            for (var interval : busy) occupied.add(new Busy(interval.start().minusSeconds(s.breakMinutes() * 60L), interval.end().plusSeconds(s.breakMinutes() * 60L)));
            occupied.sort(Comparator.comparing(Busy::start));
            for (var interval : occupied) {
                if (interval.overlaps(cursor, cursor.plusSeconds(task.minutes() * 60L))) cursor = ceilMinute(interval.end());
            }
            Instant end = cursor.plusSeconds(task.minutes() * 60L);
            if (task.minutes() < 1 || end.isAfter(limit)) result.add(new Slot(task, null, null, "Không còn khoảng trống liên tục đủ " + task.minutes() + " phút trong ngày."));
            else { result.add(new Slot(task, cursor, end, null)); busy.add(new Busy(cursor, end)); }
        }
        return List.copyOf(result);
    }
}
