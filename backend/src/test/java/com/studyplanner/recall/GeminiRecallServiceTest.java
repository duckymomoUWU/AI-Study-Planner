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
    @DisplayName("generateCards falls back gracefully to deterministic generation when API key is blank")
    void testGenerateCardsWithoutApiKey() {
        String material = "Kiểm thử tự động giúp tiết kiệm thời gian và nâng cao độ chính xác trong phát triển phần mềm.";
        List<GeminiRecallService.GeneratedCard> cards = service.generateCards(material, List.of(), "Kiểm thử", 5);

        assertNotNull(cards);
        assertFalse(cards.isEmpty());
    }
}
