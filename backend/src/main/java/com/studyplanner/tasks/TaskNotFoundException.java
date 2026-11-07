package com.studyplanner.tasks;

public class TaskNotFoundException extends RuntimeException {
    public TaskNotFoundException() {
        super("KhÃ´ng tÃ¬m tháº¥y cÃ´ng viá»‡c hoáº·c báº¡n khÃ´ng cÃ³ quyá»n truy cáº­p.");
    }

    public TaskNotFoundException(String message) {
        super(message);
    }
}
