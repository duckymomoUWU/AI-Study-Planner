package com.studyplanner.tasks;

public class TaskNotFoundException extends RuntimeException {
    public TaskNotFoundException() {
        super("Không tìm thấy công việc hoặc bạn không có quyền truy cập.");
    }

    public TaskNotFoundException(String message) {
        super(message);
    }
}
