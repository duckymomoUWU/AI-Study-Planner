package com.studyplanner.goals;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GoalErrorResponse(String code, String message, List<GoalPolicy.Violation> fieldErrors) {
    public GoalErrorResponse(String code, String message) {
        this(code, message, null);
    }
}
