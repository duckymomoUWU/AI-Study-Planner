package com.studyplanner.goals;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;

class GeminiStructuredOutputTest {
    final ObjectMapper mapper = new ObjectMapper();
    final GeminiPlanService gemini = new GeminiPlanService(mapper,"","");
    @Test void parsesAllAnswerPartsAndSkipsThinkingParts() throws Exception {
        var response = mapper.readTree("""
          {"candidates":[{"content":{"parts":[
            {"thought":true,"text":"internal reasoning"},
            {"text":"{\\\"tasks\\\":["},
            {"text":"{\\\"title\\\":\\\"Explain collision\\\"}]}"}
          ]}}]}
          """);
        assertEquals(1, mapper.readTree(gemini.proposalText(response)).path("tasks").size());
    }
    @Test void blockedOrEmptyAnswersRemainErrorsWithoutExposingProviderText() throws Exception {
        var response = mapper.readTree("{\"candidates\":[{\"finishReason\":\"SAFETY\",\"content\":{\"parts\":[]}}]}");
        var error = assertThrows(PlanException.class, () -> gemini.proposalText(response));
        assertEquals("AI_INVALID_RESPONSE", error.code());
        assertEquals(0,error.context().get("textPartCount"));
        assertFalse(error.context().toString().contains("SAFETY"));
    }
    @Test void responseSchemaRestrictsTopicIdToConfirmedScope() throws Exception {
        var scope=mapper.readTree("{\"selectedTopics\":[{\"id\":\"collision\"},{\"id\":\"path\"}]}");
        JsonNode schema=mapper.valueToTree(GeminiPlanService.scopedSchema(scope));
        assertEquals(mapper.readTree("[\"collision\",\"path\"]"),schema.at("/properties/tasks/items/properties/topicId/enum"));
        assertFalse(schema.path("additionalProperties").asBoolean());
    }
}
