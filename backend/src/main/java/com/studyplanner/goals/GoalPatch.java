package com.studyplanner.goals;

import java.time.LocalDate;

/**
 * Fields a client asked to change. A null component means "not sent": the API rejects an explicit JSON null
 * for these fields, so null can safely stand for "keep the stored value".
 */
public record GoalPatch(String subject, String topics, LocalDate startDate, LocalDate examDate,
                        Integer minutesPerDay, String timezone, com.fasterxml.jackson.databind.JsonNode planningScope) {
    public GoalPatch(String subject,String topics,LocalDate startDate,LocalDate examDate,Integer minutesPerDay,String timezone) {this(subject,topics,startDate,examDate,minutesPerDay,timezone,null);}
}
