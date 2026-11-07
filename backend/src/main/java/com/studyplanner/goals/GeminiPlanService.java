package com.studyplanner.goals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
                .connectTimeout(Duration.ofSeconds(10))
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
                You are an expert AI study planner. Create a repetitive weekly study plan for this student:
                - Subject: %s
                - Topics to cover: %s
                - Start date: %s
                - Exam date: %s
                - Study budget per day: %d minutes
                - Timezone: %s

                Format requirements:
                Return strictly a valid JSON object matching this schema, without markdown formatting or code blocks:
                {
                  "tasks": [
                    {
                      "title": "Clear concise study task title",
                      "scheduledDate": "%s",
                      "startHour": 14.0,
                      "durationHours": 1.5,
                      "estimatedMinutes": %d,
                      "recurrence": "weekly",
                      "recurringDays": [1, 3, 5],
                      "topics": "Specific topic subpart"
                    }
                  ]
                }
                Constraints:
                - The schedule must repeat weekly across the study period.
                - recurringDays are day-of-week integers (0=Sunday, 1=Monday, 2=Tuesday, 3=Wednesday, 4=Thursday, 5=Friday, 6=Saturday).
                - scheduledDate must be between startDate (%s) and examDate (%s).
                - durationHours must match estimatedMinutes / 60.
                """.formatted(
                req.subject(), req.topics(), req.startDate(), req.examDate(),
                req.minutesPerDay(), req.timezone(), req.startDate(),
                Math.min(req.minutesPerDay(), 90), req.startDate(), req.examDate()
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
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode textNode = root.at("/candidates/0/content/parts/0/text");
            if (!textNode.isMissingNode()) {
                String rawText = textNode.asText().trim();
                // Strip markdown backticks if returned
                if (rawText.startsWith("```json")) {
                    rawText = rawText.substring(7);
                } else if (rawText.startsWith("```")) {
                    rawText = rawText.substring(3);
                }
                if (rawText.endsWith("```")) {
                    rawText = rawText.substring(0, rawText.length() - 3);
                }
                rawText = rawText.trim();
                // Validate that it's valid JSON with "tasks"
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
     * Deterministic, high-quality fallback planner when API key is missing or offline.
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
        if (durationHours <= 0) durationHours = 1.0;

        // Choose weekly recurring days (e.g. Mon, Wed, Fri if >= 3, or Tue, Thu)
        int[][] daySchedules = new int[][]{
                {1, 3, 5}, // T2, T4, T6
                {2, 4, 6}, // T3, T5, T7
                {1, 4},    // T2, T5
                {0, 6}     // T7, CN
        };
        int[] chosenDays = daySchedules[Math.abs(req.subject().hashCode()) % daySchedules.length];
        double[] preferredHours = new double[]{14.0, 19.5, 9.0};

        LocalDate startDate = req.startDate() != null ? req.startDate() : LocalDate.now();

        int count = Math.min(topicList.size(), 4);
        for (int i = 0; i < count; i++) {
            String topic = topicList.get(i);
            ObjectNode task = tasksArray.addObject();
            task.put("title", "Focus Time: " + topic);
            task.put("scheduledDate", startDate.plusDays(i).toString());
            task.put("startHour", preferredHours[i % preferredHours.length]);
            task.put("durationHours", durationHours);
            task.put("estimatedMinutes", minutes);
            task.put("recurrence", "weekly");

            ArrayNode recDays = task.putArray("recurringDays");
            for (int d : chosenDays) {
                recDays.add(d);
            }
            task.put("topics", "Nội dung trọng tâm: " + topic + " - Ôn tập lý thuyết và bài tập thực hành.");
            task.put("source", "ai");
        }

        return root.toString();
    }
}
