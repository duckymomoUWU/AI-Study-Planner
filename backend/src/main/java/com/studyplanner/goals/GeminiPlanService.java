package com.studyplanner.goals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class GeminiPlanService {

    private static final Logger log = LoggerFactory.getLogger(GeminiPlanService.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String modelName;

    public GeminiPlanService(
            ObjectMapper objectMapper,
            @Value("${gemini.api-key:#{systemEnvironment['GEMINI_API_KEY'] ?: ''}}") String apiKey,
            @Value("${gemini.model:gemini-3.8-flash}") String modelName
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.modelName = (modelName != null && !modelName.isBlank()) ? modelName.trim() : "gemini-3.8-flash";
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public record GeneratePlanRequest(
            String subject,
            String topics,
            LocalDate startDate,
            LocalDate examDate,
            int minutesPerDay,
            String timezone
    ) {}

    public record SyllabusData(
            String subject,
            String topics,
            LocalDate startDate,
            LocalDate examDate,
            int minutesPerDay
    ) {}

    /**
     * Generates a study plan proposal JSON string using Gemini Flash (gemini-3.8-flash), with resilient heuristic fallback.
     */
    public String generateProposalJson(GeneratePlanRequest req) {
        if (!apiKey.isBlank()) {
            try {
                String result = callGeminiApi(req);
                if (result != null && !result.isBlank()) {
                    return result;
                }
            } catch (Exception e) {
                log.warn("Gemini Flash API call failed or timed out. Falling back to intelligent heuristic planner: {}", e.getMessage());
            }
        }
        return generateFallbackProposal(req);
    }

    private String callGeminiApi(GeneratePlanRequest req) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey;

        String prompt = """
                Bạn là một cố vấn học thuật và chuyên gia lập kế hoạch học tập AI.
                Hãy xây dựng kế hoạch học tập định kỳ hàng tuần có chiều sâu và chi tiết cho sinh viên sau:
                - Môn học: %s
                - Các chủ đề cần ôn tập: %s
                - Ngày bắt đầu: %s
                - Ngày thi mục tiêu: %s
                - Ngân sách tự học mỗi ngày: %d phút
                - Múi giờ: %s

                Yêu cầu bắt buộc cho mỗi buổi học (task):
                1. "title": Tiêu đề súc tích, bắt đầu bằng "Focus Time: [Tên chủ đề]" hoặc "Luyện đề: [Tên chủ đề]".
                2. "startHour": BẮT BUỘC VÀ DUY NHẤT LÀ 12.0 (tức đúng 12:00 trưa). Tuyệt đối không chọn bất kỳ giờ nào khác ngoài 12.0.
                3. "durationHours": Số giờ học phù hợp với ngân sách thời gian (ví dụ 1.5 hoặc 2.0).
                4. "estimatedMinutes": Số phút học (ví dụ %d).
                5. "scheduledDate": Ngày đầu tiên diễn ra buổi học (YYYY-MM-DD) trong khoảng [%s đến %s).
                6. "recurrence": "weekly".
                7. "recurringDays": Mảng các thứ lặp lại hàng tuần (0=Chủ nhật, 1=Thứ 2, 2=Thứ 3, 3=Thứ 4, 4=Thứ 5, 5=Thứ 6, 6=Thứ 7). Bắt buộc phải chứa thứ tương ứng với "scheduledDate".
                8. "targetGrade": Mục tiêu điểm số cụ thể. Ví dụ: "Điểm 8.5+ (Điểm A) - Hiểu sâu bản chất, làm chủ các câu hỏi lý thuyết và bài tập nâng cao."
                9. "effort": Mức độ nỗ lực và thời gian đầu tư. Ví dụ: "Mức độ nỗ lực: Cao (khoảng 5 - 7 giờ/tuần ôn tập chủ động, tập trung cao độ)."
                10. "courseWeight": Đánh giá độ nặng môn học. Ví dụ: "Độ nặng môn: Nặng (3 - 4 tín chỉ chuyên ngành), khối lượng bài tập lớn và tính toán phức tạp."
                11. "practicalValue": Giá trị ứng dụng thực tế. Ví dụ: "Giá trị thực tế: Ứng dụng trực tiếp khi xây dựng hệ thống phần mềm thực tế, tối ưu cơ sở dữ liệu và câu hỏi phỏng vấn kỹ sư."
                12. "specificLearning": Nội dung học tập cụ thể theo từng bước thực hiện. Ví dụ: "1) Đọc lại lý thuyết định nghĩa và tính chất cơ bản; 2) Phân tích 3 bài tập mẫu; 3) Tự tay giải 2 bài tập đề thi các năm trước; 4) Tóm tắt lỗi sai thường gặp."
                13. "references": Giáo trình, slide hoặc tài liệu tham khảo. Ví dụ: "Giáo trình chính (Chương 3 & 4), Slide bài giảng của khoa, Bộ đề thi mẫu 2024-2025."
                14. "topics": Tóm tắt chủ đề buổi học.
                15. "description": Đoạn văn bản định dạng markdown chuẩn hóa tổng hợp đầy đủ các mục trên:
                🎯 **Mục tiêu điểm số:** ...
                ⚡ **Mức độ nỗ lực (Effort):** ...
                ⚖️ **Độ nặng môn học:** ...
                💼 **Giá trị thực tế:** ...
                📖 **Nội dung học cụ thể:** ...
                📚 **Tài liệu tham khảo:** ...

                Định dạng trả về:
                Trả về DUY NHẤT một JSON hợp lệ, không bọc markdown hay giải thích thêm:
                {
                  "tasks": [
                    {
                      "title": "Focus Time: ...",
                      "scheduledDate": "%s",
                      "startHour": 12.0,
                      "durationHours": 1.5,
                      "estimatedMinutes": %d,
                      "recurrence": "weekly",
                      "recurringDays": [1, 3, 5],
                      "targetGrade": "...",
                      "effort": "...",
                      "courseWeight": "...",
                      "practicalValue": "...",
                      "specificLearning": "...",
                      "references": "...",
                      "topics": "...",
                      "description": "..."
                    }
                  ]
                }
                """.formatted(
                req.subject(), req.topics(), req.startDate(), req.examDate(),
                req.minutesPerDay(), req.timezone(), Math.min(req.minutesPerDay(), 90),
                req.startDate(), req.examDate(), req.startDate(), Math.min(req.minutesPerDay(), 90)
        );

        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                ),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "temperature", 0.3
                )
        );

        String jsonBody = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode textNode = root.at("/candidates/0/content/parts/0/text");
            if (!textNode.isMissingNode()) {
                String rawText = cleanJsonString(textNode.asText());
                JsonNode parsed = objectMapper.readTree(rawText);
                if (parsed.has("tasks") && parsed.get("tasks").isArray() && !parsed.get("tasks").isEmpty()) {
                    return rawText;
                }
            }
        } else {
            log.warn("Gemini API returned status {}: {}", response.statusCode(), response.body());
        }

        return null;
    }

    /**
     * Parses course syllabus or document to quickly extract goal fields using Gemini 3.8 Flash or heuristic fallback.
     */
    public SyllabusData parseSyllabus(String content, String base64, String mimeType, String fileName) {
        if (!apiKey.isBlank()) {
            try {
                SyllabusData result = callGeminiSyllabusApi(content, base64, mimeType, fileName);
                if (result != null && result.subject() != null && !result.subject().isBlank()) {
                    return result;
                }
            } catch (Exception e) {
                log.warn("Gemini syllabus parse failed or timed out. Falling back to heuristic extractor: {}", e.getMessage());
            }
        }
        return parseFallbackSyllabus(content, fileName);
    }

    private SyllabusData callGeminiSyllabusApi(String content, String base64, String mimeType, String fileName) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey;

        LocalDate today = LocalDate.now();
        String prompt = """
                Bạn là một trợ lý AI thông minh chuyên hỗ trợ sinh viên lập kế hoạch học tập.
                Hãy phân tích tài liệu đề cương môn học / syllabus được cung cấp và trích xuất các thông tin sau để điền vào biểu mẫu mục tiêu ôn tập:
                1. "subject": Tên môn học chính xác, ngắn gọn (tối đa 100 ký tự). Ví dụ: "Cơ sở dữ liệu", "Hệ điều hành", "Lập trình Web".
                2. "topics": Các chủ đề / chương mục / bài học trọng tâm cần ôn thi, phân cách bởi dấu phẩy hoặc dòng gạch đầu dòng. Ví dụ: "Mô hình ER, SQL JOIN và VIEW, Chuẩn hóa 1NF-3NF, Quản lý giao dịch và khóa".
                3. "startDate": Ngày bắt đầu ôn tập (định dạng YYYY-MM-DD). Nếu trong đề cương không nêu rõ, hãy dùng ngày hôm nay: %s.
                4. "examDate": Ngày thi hoặc hạn chót kỳ thi (định dạng YYYY-MM-DD). Nếu đề cương có ghi ngày thi, hãy lấy ngày đó. Nếu không có hoặc đã trong quá khứ, hãy đề xuất ngày thi hợp lý cách ngày bắt đầu khoảng 20-30 ngày (YYYY-MM-DD).
                5. "minutesPerDay": Số phút tự học mỗi ngày đề xuất (số nguyên từ 30 đến 180, ví dụ 60, 90 hoặc 120).

                Yêu cầu định dạng:
                Chỉ trả về DUY NHẤT một đối tượng JSON hợp lệ, không có markdown formatting hay giải thích thêm:
                {
                  "subject": "Tên môn học",
                  "topics": "Chủ đề 1, Chủ đề 2, Chủ đề 3",
                  "startDate": "YYYY-MM-DD",
                  "examDate": "YYYY-MM-DD",
                  "minutesPerDay": 90
                }
                """.formatted(today);

        List<Map<String, Object>> parts = new ArrayList<>();
        if (base64 != null && !base64.isBlank() && mimeType != null && mimeType.contains("pdf")) {
            parts.add(Map.of("inline_data", Map.of("mime_type", "application/pdf", "data", base64)));
            parts.add(Map.of("text", prompt));
        } else {
            String combinedText = prompt + "\n\n--- NỘI DUNG ĐỀ CƯƠNG TÀI LIỆU (" + (fileName != null ? fileName : "tai_lieu") + ") ---\n"
                    + (content != null ? content : "");
            if (combinedText.length() > 30000) {
                combinedText = combinedText.substring(0, 30000);
            }
            parts.add(Map.of("text", combinedText));
        }

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", parts)),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "temperature", 0.2
                )
        );

        String jsonBody = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode textNode = root.at("/candidates/0/content/parts/0/text");
            if (!textNode.isMissingNode()) {
                String rawJson = cleanJsonString(textNode.asText());
                JsonNode parsed = objectMapper.readTree(rawJson);

                String subject = parsed.path("subject").asText("").trim();
                String topics = parsed.path("topics").asText("").trim();

                LocalDate start = today;
                try {
                    String startStr = parsed.path("startDate").asText("");
                    if (!startStr.isBlank()) start = LocalDate.parse(startStr);
                } catch (Exception ignored) {}

                LocalDate exam = start.plusDays(30);
                try {
                    String examStr = parsed.path("examDate").asText("");
                    if (!examStr.isBlank()) {
                        LocalDate parsedExam = LocalDate.parse(examStr);
                        if (parsedExam.isAfter(start)) exam = parsedExam;
                    }
                } catch (Exception ignored) {}

                int minutes = parsed.path("minutesPerDay").asInt(90);
                if (minutes < 15 || minutes > 480) minutes = 90;

                if (!subject.isBlank()) {
                    if (subject.length() > 100) subject = subject.substring(0, 100);
                    return new SyllabusData(subject, topics, start, exam, minutes);
                }
            }
        } else {
            log.warn("Gemini syllabus parse returned status {}: {}", response.statusCode(), response.body());
        }

        return null;
    }

    /**
     * Resilient heuristic syllabus extractor when offline or without API key.
     */
    public SyllabusData parseFallbackSyllabus(String content, String fileName) {
        String text = content != null ? content : "";
        LocalDate today = LocalDate.now();

        // 1. Extract subject
        String subject = "";
        Pattern subjectPattern = Pattern.compile("(?im)(?:m[oô]n(?:\\s*h[oọ]c)?|h[oọ]c\\s*ph[aầ]n|subject|course|đ?[eề]\\s*c[uư][oơ]ng(?:\\s*m[oô]n)?|gi[aá]o\\s*tr[iì]nh)\\s*[:：\\-]\\s*([^\\r\\n]+)");
        Matcher sm = subjectPattern.matcher(text);
        if (sm.find()) {
            subject = sm.group(1).trim();
        } else if (fileName != null && !fileName.isBlank()) {
            String nameOnly = fileName.replaceAll("\\.[^.]+$", "");
            nameOnly = nameOnly.replaceAll("(?i)(?:de_?cuong|syllabus|tai_?lieu)[_\\-\\s]*", "");
            nameOnly = nameOnly.replace('_', ' ').replace('-', ' ').trim();
            if (!nameOnly.isBlank()) {
                subject = nameOnly;
            }
        }
        if (subject.isBlank()) {
            // Check first Markdown heading
            Pattern h1Pattern = Pattern.compile("(?m)^#+\\s+([^\\r\\n]+)");
            Matcher hm = h1Pattern.matcher(text);
            if (hm.find()) {
                subject = hm.group(1).trim();
            } else {
                subject = "Môn học ôn tập";
            }
        }
        if (subject.length() > 100) {
            subject = subject.substring(0, 100).trim();
        }

        // 2. Extract topics / chapters
        List<String> topicsList = new ArrayList<>();
        Pattern topicPattern = Pattern.compile("(?im)^(?:[\\*\\-\\•]|(?:chương|bài|phần|chủ\\s*đề|topic|module|unit|\\d+\\.)\\s*\\d*[:：\\-\\.]?)\\s*([^\\r\\n]+)");
        Matcher tm = topicPattern.matcher(text);
        while (tm.find() && topicsList.size() < 8) {
            String topic = tm.group(1).trim();
            if (topic.length() > 3 && topic.length() < 120 && !topicsList.contains(topic)) {
                topicsList.add(topic);
            }
        }

        String topics = String.join(", ", topicsList);
        if (topics.isBlank()) {
            topics = "Tổng quan kiến thức cốt lõi, Các dạng bài tập trọng tâm, Luyện giải đề thi thực tế";
        }

        return new SyllabusData(subject, topics, today, today.plusDays(30), 90);
    }

    private String cleanJsonString(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    /**
     * Deterministic, high-quality fallback planner with structured fields when API key is missing or offline.
     */
    public String generateFallbackProposal(GeneratePlanRequest req) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode tasksArray = root.putArray("tasks");

        String rawTopics = req.topics() != null ? req.topics() : "";
        List<String> topicList = Arrays.stream(rawTopics.split("[,;\\n•]+"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

        if (topicList.isEmpty()) {
            topicList = List.of("Tổng quan lý thuyết", "Giải bài tập nâng cao", "Luyện giải đề thi mẫu");
        }

        // Minutes & hours per session
        int minutes = Math.min(req.minutesPerDay(), 120);
        double durationHours = Math.round((minutes / 60.0) * 100.0) / 100.0;
        if (durationHours <= 0) durationHours = 1.5;

        // Choose weekly recurring days (e.g. Mon, Wed, Fri if >= 3, or Tue, Thu)
        int[][] daySchedules = new int[][]{
                {1, 3, 5}, // T2, T4, T6
                {2, 4, 6}, // T3, T5, T7
                {1, 4},    // T2, T5
                {0, 6}     // T7, CN
        };
        int[] chosenDays = daySchedules[Math.abs(req.subject().hashCode()) % daySchedules.length];
        double[] preferredHours = new double[]{12.0, 12.0, 12.0, 12.0};

        LocalDate startDate = req.startDate() != null ? req.startDate() : LocalDate.now();

        int count = Math.min(topicList.size(), 4);
        for (int i = 0; i < count; i++) {
            String topic = topicList.get(i);
            LocalDate taskDate = startDate.plusDays(i);
            int dow = taskDate.getDayOfWeek().getValue() % 7; // Java DayOfWeek: 1=Mon..7=Sun -> 0=Sun..6=Sat

            ObjectNode task = tasksArray.addObject();
            task.put("title", "Focus Time: " + topic);
            task.put("scheduledDate", taskDate.toString());
            task.put("startHour", preferredHours[i % preferredHours.length]);
            task.put("durationHours", durationHours);
            task.put("estimatedMinutes", minutes);
            task.put("recurrence", "weekly");

            ArrayNode recDays = task.putArray("recurringDays");
            java.util.Set<Integer> daySet = new java.util.TreeSet<>();
            daySet.add(dow);
            for (int d : chosenDays) {
                daySet.add(d);
            }
            for (int d : daySet) {
                recDays.add(d);
            }

            String targetGrade = "Điểm 8.5+ (Điểm A) - Hiểu sâu bản chất các dạng lý thuyết và giải trọn vẹn bài tập đề thi";
            String effort = "Mức độ nỗ lực: " + (minutes >= 90 ? "Cao (5 - 7 giờ/tuần)" : "Trung bình (3 - 5 giờ/tuần)") + " ôn tập chủ động";
            String courseWeight = "Độ nặng môn học: 3 - 4 tín chỉ - Khối lượng bài tập và kiến thức trọng tâm";
            String practicalValue = "Giá trị thực tế: Nền tảng cốt lõi cho kiến trúc phần mềm, hệ thống ứng dụng và phỏng vấn tuyển dụng kỹ sư";
            String specificLearning = "1) Ôn tập định nghĩa và công thức cốt lõi của " + topic + ";\n2) Phân tích 2-3 bài tập mẫu điển hình;\n3) Tự tay giải bài tập trích từ đề thi các năm trước;\n4) Ghi chú các bẫy thường gặp.";
            String references = "Giáo trình chính môn " + req.subject() + " (Chương trọng tâm), Slide bài giảng của khoa, Bộ đề ôn thi mẫu 2024-2025";

            String description = String.format(
                    "🎯 **Mục tiêu điểm số:** %s\n" +
                    "⚡ **Mức độ nỗ lực (Effort):** %s\n" +
                    "⚖️ **Độ nặng môn học:** %s\n" +
                    "💼 **Giá trị thực tế:** %s\n" +
                    "📖 **Nội dung học cụ thể:**\n%s\n" +
                    "📚 **Tài liệu tham khảo:** %s",
                    targetGrade, effort, courseWeight, practicalValue, specificLearning, references
            );

            task.put("targetGrade", targetGrade);
            task.put("effort", effort);
            task.put("courseWeight", courseWeight);
            task.put("practicalValue", practicalValue);
            task.put("specificLearning", specificLearning);
            task.put("references", references);
            task.put("topics", "Nội dung trọng tâm: " + topic);
            task.put("description", description);
            task.put("source", "ai");
        }

        return root.toString();
    }
}
