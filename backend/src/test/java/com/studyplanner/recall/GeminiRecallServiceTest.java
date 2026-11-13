package com.studyplanner.recall;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

class GeminiRecallServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GeminiRecallService service = new GeminiRecallService(objectMapper, "", "gemini-3.8-flash");

    @Test
    @DisplayName("generateFallbackCards generates valid cards with verbatim quotes from text")
    void testGenerateFallbackCards() {
        String material = """
                Kiểm thử hộp đen là phương pháp kiểm thử phần mềm dựa trên các yêu cầu chức năng mà không cần biết cấu trúc mã nguồn bên trong.
                Kiểm thử hộp trắng là phương pháp kiểm thử cấu trúc bên trong của chương trình bao gồm luồng điều khiển và luồng dữ liệu.
                Ca kiểm thử bao gồm các dữ liệu đầu vào và kết quả mong đợi tương ứng để xác minh hành vi của hệ thống.
                Kiểm thử hồi quy được thực hiện sau khi có thay đổi trong mã nguồn để đảm bảo không làm phát sinh lỗi mới.
                Kết quả mong đợi dùng để so sánh đối chiếu với kết quả thực tế khi chạy ca kiểm thử.
                """;

        List<GeminiRecallService.GeneratedCard> cards = service.generateFallbackCards(material, List.of(), 5);

        assertNotNull(cards);
        assertTrue(cards.size() >= 5);
        for (GeminiRecallService.GeneratedCard card : cards) {
            assertNotNull(card.question());
            assertFalse(card.question().isBlank());
            assertTrue(card.question().length() <= 300);

            assertNotNull(card.answer());
            assertFalse(card.answer().isBlank());
            assertTrue(card.answer().length() <= 2000);

            assertNotNull(card.quote());
            assertFalse(card.quote().isBlank());
            assertTrue(card.quote().length() <= 500);

            // Verbatim quote must exist in material text
            assertTrue(RecallTextExtractor.verifyQuote(material, List.of(), null, card.quote()),
                    "Quote must exist in material text: " + card.quote());
        }
    }

    @Test
    @DisplayName("generateCards reports missing credentials without fabricated success")
    void testGenerateCardsWithoutApiKey() {
        String material = "Kiểm thử tự động giúp tiết kiệm thời gian và nâng cao độ chính xác trong phát triển phần mềm.";
        var error = assertThrows(RecallException.class,
                () -> service.generateCards(material, List.of(), "Recall", 5));
        assertEquals("AI_UNAVAILABLE", error.getCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void providerOutputMustHaveRequestedCountAndCorrectSourcePages() throws Exception {
        var http=org.mockito.Mockito.mock(java.net.http.HttpClient.class);
        var response=(java.net.http.HttpResponse<String>)org.mockito.Mockito.mock(java.net.http.HttpResponse.class);
        org.mockito.Mockito.when(http.send(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(java.net.http.HttpResponse.BodyHandler.class))).thenReturn(response);
        org.mockito.Mockito.when(response.statusCode()).thenReturn(200);
        var source="Software testing evaluates behavior against requirements.";
        var cards=new java.util.ArrayList<java.util.Map<String,Object>>();
        for(int n=0;n<5;n++) cards.add(java.util.Map.of("question","Question "+n,"answer","Answer "+n,"quote",source,"page",1));
        var ai=new GeminiRecallService(objectMapper,"test-key","test-model",http);
        var body=objectMapper.writeValueAsString(java.util.Map.of("cards",cards));
        org.mockito.Mockito.when(response.body()).thenReturn(objectMapper.writeValueAsString(java.util.Map.of("candidates",List.of(java.util.Map.of("content",java.util.Map.of("parts",List.of(java.util.Map.of("text",body))))))));
        assertEquals(5,ai.generateCards(source,List.of(new RecallTextExtractor.PageText(1,source)),"Testing",5).size());
        var error=assertThrows(RecallException.class,()->ai.generateCards(source,List.of(new RecallTextExtractor.PageText(2,source)),"Testing",5));
        assertTrue("ALL_CARDS_FAILED_QUALITY".equals(error.getCode()) || "AI_INVALID_RESPONSE".equals(error.getCode()));
        org.mockito.Mockito.when(response.statusCode()).thenReturn(503);
        assertEquals("AI_UNAVAILABLE",assertThrows(RecallException.class,()->ai.generateCards(source,List.of(),"Testing",5)).getCode());
    }
}
