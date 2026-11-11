package com.studyplanner.goals;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studyplanner.api.ApiErrorResponse.ErrorDetail;
import com.studyplanner.api.RequestValidationException;
import java.time.LocalDate;
import java.util.*;

/** Durable goal settings, distinct from a proposed set of tasks or remaining budget. */
public final class GoalPlanning {
    private static final ObjectMapper JSON=new ObjectMapper();
    private GoalPlanning() {}
    public static JsonNode read(String value) {
        if(value==null || value.isBlank()) return JSON.createObjectNode();
        try { return JSON.readTree(value); } catch(Exception ex) { throw new IllegalStateException("Invalid stored planning settings"); }
    }
    public static boolean configured(GoalJdbcRepository.StoredGoal goal) {
        return goal.planningScope()!=null && goal.planningScope().has("selectedTopics");
    }
    public static ObjectNode defaults(String text) {
        var result=JSON.createObjectNode();var topics=result.putArray("selectedTopics");int i=0;
        for(String title:new LinkedHashSet<>(Arrays.asList(text.split("[\\n;,]+")))) if(!title.isBlank())
            topics.addObject().put("id","manual-"+(++i)).put("title",title.strip());
        result.putObject("dailyMinutes");return result;
    }
    public static ObjectNode normalize(JsonNode input,GoalPolicy.GoalInput goal) {
        PlanScheduling.validateTopics(input);
        var result=JSON.createObjectNode();result.set("selectedTopics",input.get("selectedTopics").deepCopy());
        var budgets=result.putObject("dailyMinutes");
        if(input.has("dailyMinutes")) {
            var custom=input.get("dailyMinutes");if(!custom.isObject() || custom.size()>90) throw bad("dailyMinutes");
            custom.fields().forEachRemaining(e->{
                LocalDate day;try {day=LocalDate.parse(e.getKey());} catch(Exception ex){throw bad("dailyMinutes");}
                var value=e.getValue();
                if(day.isBefore(goal.startDate()) || !day.isBefore(goal.examDate()) || !value.isIntegralNumber()
                    || !value.canConvertToInt() || value.intValue()<0 || value.intValue()>goal.minutesPerDay()) throw bad("dailyMinutes");
                if(value.intValue()!=goal.minutesPerDay()) budgets.put(day.toString(),value.intValue());
            });
        }
        return result;
    }
    public static int minutes(GoalJdbcRepository.StoredGoal goal,LocalDate date) {
        var scope=goal.planningScope();
        return scope!=null && scope.path("dailyMinutes").has(date.toString()) ? scope.path("dailyMinutes").get(date.toString()).asInt() : goal.minutesPerDay();
    }
    public static JsonNode forPatch(GoalJdbcRepository.StoredGoal old,GoalPolicy.GoalInput next,JsonNode explicit) {
        if(explicit!=null) return normalize(explicit,next);
        if(!configured(old)) return null;
        var preserved=(ObjectNode)old.planningScope().deepCopy();
        if(!old.topics().equals(next.topics())) preserved.set("selectedTopics",defaults(next.topics()).get("selectedTopics"));
        var budgets=preserved.putObject("dailyMinutes");
        old.planningScope().path("dailyMinutes").fields().forEachRemaining(e->{
            var day=LocalDate.parse(e.getKey());
            if(!day.isBefore(next.startDate()) && day.isBefore(next.examDate())) budgets.set(e.getKey(),e.getValue());
        });
        return normalize(preserved,next);
    }
    public static void validateExisting(GoalPolicy.GoalInput input,JsonNode scope,List<GoalPolicy.ScheduledTask> tasks) {
        var used=new TreeMap<LocalDate,Integer>();
        for(var task:tasks) {
            if(task.scheduledDate().isBefore(input.startDate())) throw new PlanException(409,"GOAL_UPDATE_CONFLICT","Ngày bắt đầu mới nằm sau một công việc đã lưu. Giữ ngày bắt đầu cũ hoặc điều chỉnh công việc chưa hoàn thành trước.");
            used.merge(task.scheduledDate(),task.estimatedMinutes(),Integer::sum);
        }
        for(var day:used.entrySet()) {
            int budget=scope!=null && scope.path("dailyMinutes").has(day.getKey().toString()) ? scope.path("dailyMinutes").get(day.getKey().toString()).asInt() : input.minutesPerDay();
            if(day.getValue()>budget) throw new PlanException(422,"DAILY_BUDGET_EXCEEDED","Ngày "+day.getKey()+" đã có "+day.getValue()+" phút; không thể giảm quỹ xuống "+budget+" phút khi vẫn còn công việc đã lưu.",Map.of("excessMinutes",day.getValue()-budget,"scheduledDate",day.getKey().toString()));
        }
    }
    static GoalPolicy.GoalInput input(GoalJdbcRepository.StoredGoal goal) {return new GoalPolicy.GoalInput(goal.subject(),goal.topics(),goal.startDate(),goal.examDate(),goal.minutesPerDay());}
    private static RequestValidationException bad(String field) {return new RequestValidationException(List.of(new ErrorDetail(field,"invalid_planning_settings")));}
}
