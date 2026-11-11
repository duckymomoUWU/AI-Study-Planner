package com.studyplanner.goals;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyList;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class SyllabusEvidenceTest {
    final ObjectMapper mapper = new ObjectMapper();
    final List<String> pages=List.of("Subject: Networks\nWe cover Routing and Switching using practical examples.\nExam date: 25/12/2026");
    String answer() throws Exception { return mapper.writeValueAsString(Map.of("subject","Networks","chapters",List.of(Map.of(
            "title","Routing", "topics",List.of(Map.of("title","Routing", "sources",List.of(Map.of("page",1,"text","We cover Routing and Switching using practical examples.")))))))); }
    @Test void unfamiliarProseUsesAiOnlyAfterLocalNoTopicsAndKeepsGroundedNamesAndExamDate() throws Exception {
        var gemini=mock(GeminiPlanService.class); when(gemini.isConfigured()).thenReturn(true); when(gemini.generateOutlineJson(anyList())).thenReturn(answer());
        var result=new SyllabusService(gemini).extract(mapper.valueToTree(Map.of("fileName","course.txt","content",pages.getFirst())));
        assertEquals("gemini",result.extractionMethod()); assertEquals("Routing",result.chapters().getFirst().topics().getFirst().title());
        assertTrue(result.chapters().getFirst().topics().getFirst().needsReview()); assertNull(result.startDate()); assertNull(result.minutesPerDay());
        assertEquals("2026-12-25",result.examDate().toString());
        assertNull(result.chapters().getFirst().topics().getFirst().sources().getFirst().page());
        verify(gemini).generateOutlineJson(pages);
    }
    @Test void inventedTopicsQuotesOrPageNumbersAreRejectedRatherThanDisplayed() throws Exception {
        for (String field : List.of("title","text","page")) {
            var answer=mapper.readTree(answer()); var topic=(ObjectNode)answer.at("/chapters/0/topics/0"); var source=(ObjectNode)topic.at("/sources/0");
            if(field.equals("title")) topic.put("title","Invented topic");
            if(field.equals("text")) source.put("text","A quote absent from the document");
            if(field.equals("page")) source.put("page",2);
            assertEquals("AI_INVALID_RESPONSE",assertThrows(PlanException.class,()->SyllabusEvidence.validate(answer.toString(),pages,mapper)).code());
        }
    }
    @Test void supportedLocalLayoutDoesNotCallAiAndNoKeyDoesNotCreatePlaceholderTopics() throws Exception {
        var gemini=mock(GeminiPlanService.class); when(gemini.isConfigured()).thenReturn(true);
        new SyllabusService(gemini).extract(mapper.valueToTree(Map.of("fileName","course.txt","content","Subject: Networks\n- Routing")));
        verifyNoInteractions(gemini);
        var offline=mock(GeminiPlanService.class);
        var error=assertThrows(PlanException.class,()->new SyllabusService(offline).extract(mapper.valueToTree(Map.of("fileName","course.txt","content",pages.getFirst()))));
        assertEquals("SYLLABUS_NO_TOPICS",error.code()); verify(offline,never()).generateOutlineJson(anyList());
    }
    @Test void providerFailureDoesNotProduceOrSaveAnOutline() {
        var gemini=mock(GeminiPlanService.class); when(gemini.isConfigured()).thenReturn(true);
        when(gemini.generateOutlineJson(anyList())).thenThrow(new PlanException(503,"AI_UNAVAILABLE","Unavailable"));
        assertEquals("AI_UNAVAILABLE",assertThrows(PlanException.class,()->new SyllabusService(gemini).extract(mapper.valueToTree(Map.of("fileName","course.txt","content",pages.getFirst())))).code());
    }
}
