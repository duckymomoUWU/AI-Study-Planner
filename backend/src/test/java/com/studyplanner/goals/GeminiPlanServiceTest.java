package com.studyplanner.goals;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class GeminiPlanServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GeminiPlanService service = new GeminiPlanService(objectMapper, "", "test-model");

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

        String json = service.generateFallbackProposal(req);
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

    @Test
    void parseSyllabusFallbackExtractsSubjectAndTopicsCorrectly() {
        String outlineText = """
                ĐỀ CƯƠNG CHI TIẾT HỌC PHẦN
                Môn học: Mạng máy tính nâng cao
                
                Nội dung ôn thi bao gồm:
                Chương 1: Kiến trúc TCP/IP và mô hình phân tầng
                Chương 2: Giao thức định tuyến OSPF và BGP
                Chương 3: An ninh mạng và mã hóa TLS/SSL
                Chương 4: Lập trình mạng Socket với TCP và UDP
                """;

        GeminiPlanService.SyllabusData data = service.parseSyllabus(outlineText, null, "text/plain", "de_cuong.txt");

        assertNotNull(data);
        assertEquals("Mạng máy tính nâng cao", data.subject());
        assertTrue(data.topics().contains("Kiến trúc TCP/IP"));
        assertTrue(data.topics().contains("Giao thức định tuyến"));
        assertEquals(90, data.minutesPerDay());
        assertNotNull(data.startDate());
        assertNotNull(data.examDate());
        assertTrue(data.examDate().isAfter(data.startDate()));
    }

    @Test
    void parseSyllabusFallbackExtractsSubjectFromFilenameIfTextLacksHeader() {
        String outlineText = """
                - 1. Phép tính vi phân một biến
                - 2. Ứng dụng đạo hàm
                - 3. Tích phân suy rộng
                """;

        GeminiPlanService.SyllabusData data = service.parseSyllabus(outlineText, null, "text/plain", "De_cuong_Toan_cao_cap_A1.pdf");

        assertNotNull(data);
        assertEquals("Toan cao cap A1", data.subject());
        assertTrue(data.topics().contains("Phép tính vi phân"));
    }
}
