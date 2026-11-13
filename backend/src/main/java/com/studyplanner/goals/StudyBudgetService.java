package com.studyplanner.goals;

import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studyplanner.api.ApiErrorResponse.ErrorDetail;
import com.studyplanner.api.RequestValidationException;
import com.studyplanner.tasks.TaskPolicy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Account-wide date budgets. Every capacity-increasing write locks the owner before its goal. */
@Service
public class StudyBudgetService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Clock clock;
    public StudyBudgetService(JdbcTemplate jdbc, ObjectMapper mapper, Clock clock) {
        this.jdbc=jdbc;this.mapper=mapper;this.clock=clock;
    }
    public record Day(String date, Integer budgetMinutes, int usedMinutes, Integer remainingMinutes) {}
    public record Settings(Integer defaultMinutes, JsonNode dailyMinutes, int version, List<Day> days) {}
    private record Config(Integer minutes, JsonNode overrides, int version) {
        int minutes(LocalDate day) { return overrides.has(day.toString()) ? overrides.path(day.toString()).asInt() : minutes; }
    }
    public void lock(UUID owner) {
        jdbc.queryForList("SELECT id FROM app_users WHERE id=? FOR NO KEY UPDATE",owner);
    }
    private Config config(UUID owner) {
        return jdbc.query("SELECT study_daily_minutes,study_daily_overrides::text,study_budget_version FROM app_users WHERE id=?",
            (rs,n)->new Config((Integer)rs.getObject(1),GoalPlanning.read(rs.getString(2)),rs.getInt(3)),owner)
            .stream().findFirst().orElseThrow(GoalService.GoalNotFoundException::new);
    }
    private Config required(UUID owner) {
        var c=config(owner);
        if(c.minutes()==null) throw new PlanException(422,"STUDY_BUDGET_REQUIRED","Hãy lưu quỹ phút chung mỗi ngày trước khi thêm hoặc xếp công việc.");
        return c;
    }
    public void requireConfigured(UUID owner) {required(owner);}
    public LocalDate today() {return LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh")));}
    @Transactional(readOnly=true)
    public Settings get(UUID owner,LocalDate from,LocalDate to) {
        if(from==null)from=today(); if(to==null)to=from.plusDays(6);
        if(to.isBefore(from) || to.isAfter(from.plusDays(89)))throw bad("dateRange");
        var c=config(owner);var used=usage(owner,from,to,null);
        var days=new ArrayList<Day>();
        for(var d=from;!d.isAfter(to);d=d.plusDays(1)) {
            int count=used.getOrDefault(d,0); Integer cap=c.minutes()==null?null:c.minutes(d);
            days.add(new Day(d.toString(),cap,count,cap==null?null:Math.max(0,cap-count)));
        }
        return new Settings(c.minutes(),c.overrides(),c.version(),days);
    }
    @Transactional
    public Settings save(UUID owner,JsonNode body) {
        PlanScheduling.strict(body,Set.of("defaultMinutes","dailyMinutes","expectedVersion"));
        int minutes=number(body.get("defaultMinutes"),"defaultMinutes",1440);
        int version=number(body.get("expectedVersion"),"expectedVersion",Integer.MAX_VALUE);
        var overrides=mapper.createObjectNode();
        var custom=body.get("dailyMinutes");
        if(custom==null || !custom.isObject() || custom.size()>366)throw bad("dailyMinutes");
        custom.fields().forEachRemaining(e->{
            LocalDate d;try{d=LocalDate.parse(e.getKey());}catch(Exception ex){throw bad("dailyMinutes");}
            if(!d.toString().equals(e.getKey()))throw bad("dailyMinutes");
            int value=number(e.getValue(),"dailyMinutes",1440);
            if(value!=minutes)overrides.put(e.getKey(),value);
        });
        lock(owner);var current=config(owner);
        if(current.version()!=version)throw new PlanException(409,"STUDY_BUDGET_VERSION_CONFLICT","Quỹ phút đã thay đổi. Tải lại cấu hình rồi thử lại.");
        var next=new Config(minutes,overrides,version+1);
        var used=jdbc.query("""
            SELECT t.scheduled_date,SUM(t.estimated_minutes)::int FROM study_tasks t
            JOIN study_goals g ON g.id=t.goal_id WHERE g.user_id=? AND t.scheduled_date>=?
            GROUP BY t.scheduled_date
            """,(rs,n)->Map.entry(rs.getObject(1,LocalDate.class),rs.getInt(2)),owner,today());
        for(var day:used)if(day.getValue()>next.minutes(day.getKey()))throw exceeded(day.getKey(),day.getValue(),next.minutes(day.getKey()));
        jdbc.update("UPDATE app_users SET study_daily_minutes=?,study_daily_overrides=?::jsonb,study_budget_version=study_budget_version+1 WHERE id=?",
            minutes,overrides.toString(),owner);
        return get(owner,null,null);
    }
    private Map<LocalDate,Integer> usage(UUID owner,LocalDate from,LocalDate to,UUID excludeGoal) {
        var result=new HashMap<LocalDate,Integer>();
        jdbc.query("""
            SELECT t.scheduled_date,SUM(t.estimated_minutes)::int FROM study_tasks t JOIN study_goals g ON g.id=t.goal_id
            WHERE g.user_id=? AND t.scheduled_date BETWEEN ? AND ?
            """+(excludeGoal==null?"":" AND g.id<>? ")+" GROUP BY t.scheduled_date",
            (org.springframework.jdbc.core.RowCallbackHandler)rs->result.put(rs.getObject(1,LocalDate.class),rs.getInt(2)),
            excludeGoal==null?new Object[]{owner,from,to}:new Object[]{owner,from,to,excludeGoal});
        return result;
    }
    public void constrain(UUID owner,UUID goal,ObjectNode scope) {
        var c=required(owner);var days=(ObjectNode)scope.get("dailyMinutes");
        scope.set("preferredDailyMinutes", days.deepCopy());
        var entries=new ArrayList<String>();days.fieldNames().forEachRemaining(entries::add);
        if(entries.isEmpty())return;
        var dates=entries.stream().map(LocalDate::parse).sorted().toList();
        var other=usage(owner,dates.getFirst(),dates.getLast(),goal);
        var all=usage(owner,dates.getFirst(),dates.getLast(),null);
        var own=scope.putObject("existingGoalMinutes");
        var shared=scope.putObject("sharedDailyMinutes");
        var others=scope.putObject("otherGoalMinutes");
        int limitedDays=0;
        for(var d:dates) {
            int goalCap=days.path(d.toString()).asInt();
            int sharedLeft=Math.max(0,c.minutes(d)-other.getOrDefault(d,0));
            if(sharedLeft<goalCap)limitedDays++;
            own.put(d.toString(),all.getOrDefault(d,0)-other.getOrDefault(d,0));
            shared.put(d.toString(),c.minutes(d));
            others.put(d.toString(),other.getOrDefault(d,0));
            days.put(d.toString(),goalCap==0 ? 0 : sharedLeft);
        }
        scope.put("sharedBudgetApplied",true);
        scope.put("sharedBudgetLimitedDays",limitedDays);
    }
    public void check(UUID owner,LocalDate day,int minutes,UUID excludeTask) {
        var c=required(owner);
        int used=jdbc.queryForObject("SELECT COALESCE(SUM(t.estimated_minutes),0)::int FROM study_tasks t JOIN study_goals g ON g.id=t.goal_id WHERE g.user_id=? AND t.scheduled_date=?"
            +(excludeTask==null?"":" AND t.id<>?"),Integer.class,excludeTask==null?new Object[]{owner,day}:new Object[]{owner,day,excludeTask});
        if(used+minutes>c.minutes(day))throw exceeded(day,used+minutes,c.minutes(day));
    }
    public void checkPlan(UUID owner,List<TaskPolicy.TaskInput> tasks) {
        var totals=new HashMap<LocalDate,Integer>();tasks.forEach(t->totals.merge(t.scheduledDate(),t.estimatedMinutes(),Integer::sum));
        totals.forEach((day,minutes)->check(owner,day,minutes,null));
    }
    private static PlanException exceeded(LocalDate day,int total,int cap) {
        return new PlanException(422,"SHARED_DAILY_BUDGET_EXCEEDED","Tổng các môn ngày "+day+" là "+total+" phút, vượt quỹ chung "+cap+" phút.",
            Map.of("scheduledDate",day.toString(),"requiredMinutes",total,"budgetMinutes",cap,"excessMinutes",total-cap));
    }
    private static int number(JsonNode n,String field,int max) {
        if(n==null || !n.isIntegralNumber() || !n.canConvertToInt() || n.asInt()<0 || n.asInt()>max)throw bad(field);
        return n.asInt();
    }
    private static RequestValidationException bad(String field) {return new RequestValidationException(List.of(new ErrorDetail(field,"invalid_study_budget")));}
}
