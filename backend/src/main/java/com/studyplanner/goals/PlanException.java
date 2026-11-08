package com.studyplanner.goals;

import java.util.Map;

public class PlanException extends RuntimeException {
    private final int status;
    private final String code;
    private final Map<String, Object> context;
    public PlanException(int status, String code, String message) { this(status, code, message, Map.of()); }
    public PlanException(int status, String code, String message, Map<String, Object> context) {
        super(message); this.status = status; this.code = code; this.context = Map.copyOf(context);
    }
    public int status() { return status; }
    public String code() { return code; }
    public Map<String, Object> context() { return context; }
}
