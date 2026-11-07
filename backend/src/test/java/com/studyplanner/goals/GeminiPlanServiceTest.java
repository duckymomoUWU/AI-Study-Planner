package com.studyplanner.goals;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class GeminiPlanServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GeminiPlanService service = new GeminiPlanService(objectMapper, "");

    @Test
    void generatesValidFallbackProposalWithTasks() throws Exception {
        var req = new GeminiPlanService.GeneratePlanRequest(
                "Giải tích 2",
                "Tích phân đường, Tích phân mặt, Phương trình vi phân cấp 2",
                LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 12, 1),
                90,
                "Asia/Ho_Chi_Minh"
        );

        String json = service.generateProposalJson(req);
        assertNotNull(json);

        JsonNode root = objectMapper.readTree(json);
        assertTrue(root.has("tasks"));
        JsonNode tasks = root.get("tasks");
        assertTrue(tasks.isArray());
        assertFalse(tasks.isEmpty());

        JsonNode first = tasks.get(0);
        assertTrue(first.has("title"));
        assertTrue(first.has("scheduledDate"));
        assertTrue(first.has("startHour"));
        assertTrue(first.has("durationHours"));
        assertTrue(first.has("estimatedMinutes"));
        assertTrue(first.has("recurrence"));
        assertTrue(first.has("recurringDays"));
        assertTrue(first.get("recurringDays").isArray());
    }
}
