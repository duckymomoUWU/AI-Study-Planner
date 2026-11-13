package com.studyplanner.recall;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rules that decide which model answers become cards. No network: only the parsing and checking logic. */
class GeminiRecallServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GeminiRecallService service = new GeminiRecallService(objectMapper, "", "gemini-test");

    private static final String[] FACTS = {
            "Kiểm thử hộp đen đánh giá hành vi của hệ thống dựa trên đầu vào và đầu ra.",
            "Kiểm thử hộp trắng xem xét cấu trúc bên trong của chương trình.",
            "Độ bao phủ câu lệnh đo tỷ lệ các câu lệnh đã được thực thi ít nhất một lần.",
            "Độ bao phủ nhánh yêu cầu mỗi nhánh quyết định được thực thi cả hai hướng.",
            "Kiểm thử hồi quy được thực hiện sau mỗi thay đổi để bảo đảm chức năng cũ vẫn đúng.",
            "Kiểm thử tích hợp kiểm tra sự phối hợp giữa các mô-đun đã được kiểm thử đơn vị.",
            "Kiểm thử chấp nhận do khách hàng thực hiện để xác nhận hệ thống đáp ứng yêu cầu.",
            "Phân tích giá trị biên tập trung vào các giá trị nằm ở ranh giới của miền hợp lệ."
    };
    private static final String MATERIAL = String.join(" ", FACTS);

    private static String card(String question, String answer, String quote) {
        return "{\"question\":\"" + question + "\",\"answer\":\"" + answer + "\",\"quote\":\"" + quote + "\"}";
    }

    /** n cards whose quote is the i-th fact. */
    private static List<String> goodCards(int n) {
        List<String> cards = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            cards.add(card("Câu hỏi số " + (i + 1) + "?", "Đáp án số " + (i + 1) + ".", FACTS[i]));
        }
        return cards;
    }

    private static String json(List<String> cards) {
        return "{\"cards\":[" + String.join(",", cards) + "]}";
    }

    private RecallException invalid(String modelText) {
        return assertThrows(RecallException.class, () -> service.validateCards(modelText, MATERIAL, List.of(), 10));
    }

    @Test
    @DisplayName("without an API key nothing is generated: 503 AI_UNAVAILABLE, never made-up cards")
    void noKeyIsAnErrorNotFakeCards() {
        RecallException ex = assertThrows(RecallException.class,
                () -> service.generateCards("Kiểm thử tự động giúp tiết kiệm thời gian.", List.of(), "Kiểm thử", 5));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        assertEquals("AI_UNAVAILABLE", ex.getCode());
    }

    @Test
    @DisplayName("valid cards pass, in order, with the original text")
    void validCardsPass() {
        List<GeminiRecallService.GeneratedCard> cards = service.validateCards(json(goodCards(6)), MATERIAL, List.of(), 10);
        assertEquals(6, cards.size());
        assertEquals("Câu hỏi số 1?", cards.get(0).question());
        assertEquals(FACTS[0], cards.get(0).quote());
        assertNull(cards.get(0).page(), "a text source has no page");
    }

    @Test
    @DisplayName("a quote that is not in the source is dropped, the rest are kept")
    void inventedQuotesAreDropped() {
        List<String> cards = goodCards(6);
        cards.add(card("Câu bịa?", "Đáp án bịa.", "Câu này không hề có trong tài liệu nguồn."));
        List<GeminiRecallService.GeneratedCard> result = service.validateCards(json(cards), MATERIAL, List.of(), 10);
        assertEquals(6, result.size());
        assertTrue(result.stream().noneMatch(c -> c.question().equals("Câu bịa?")));
    }

    @Test
    @DisplayName("decomposed (NFD) diacritics in the source or the quote still match")
    void unicodeFormsDoNotMatter() {
        String nfdMaterial = Normalizer.normalize(MATERIAL, Normalizer.Form.NFD);
        List<GeminiRecallService.GeneratedCard> fromNfdSource = service.validateCards(json(goodCards(6)), nfdMaterial, List.of(), 10);
        assertEquals(6, fromNfdSource.size());

        List<String> nfdQuotes = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            nfdQuotes.add(card("Câu hỏi số " + (i + 1) + "?", "Đáp án.", Normalizer.normalize(FACTS[i], Normalizer.Form.NFD)));
        }
        assertEquals(6, service.validateCards(json(nfdQuotes), MATERIAL, List.of(), 10).size());
    }

    @Test
    @DisplayName("the same question asked twice is kept once")
    void duplicateQuestionsAreDropped() {
        List<String> cards = goodCards(6);
        cards.add(card("CÂU   HỎI SỐ 1?", "Một đáp án khác.", FACTS[6]));
        List<GeminiRecallService.GeneratedCard> result = service.validateCards(json(cards), MATERIAL, List.of(), 10);
        assertEquals(6, result.size());
    }

    @Test
    @DisplayName("an answer over 2000 characters is dropped, never cut mid-sentence")
    void overlongAnswerIsDroppedNotTruncated() {
        List<String> cards = goodCards(6);
        cards.add(card("Câu hỏi dài?", "x".repeat(2001), FACTS[7]));
        List<GeminiRecallService.GeneratedCard> result = service.validateCards(json(cards), MATERIAL, List.of(), 10);
        assertEquals(6, result.size());
        assertTrue(result.stream().allMatch(c -> c.answer().length() <= 2000));
    }

    @Test
    @DisplayName("a quote over 500 characters is shortened to a still-verbatim prefix")
    void overlongQuoteKeepsAVerbatimPrefix() {
        String longSource = ("Đoạn dài số một " + "chữ ".repeat(200)).trim();
        String material = longSource + " " + String.join(" ", FACTS);
        List<String> cards = goodCards(5);
        cards.add(card("Câu hỏi về đoạn dài?", "Đáp án.", longSource));
        List<GeminiRecallService.GeneratedCard> result = service.validateCards(json(cards), material, List.of(), 10);
        GeminiRecallService.GeneratedCard longCard = result.get(result.size() - 1);
        assertTrue(longCard.quote().length() <= 500);
        assertTrue(longSource.startsWith(longCard.quote()));
    }

    @Test
    @DisplayName("[Trang N] markers the model copies into a quote are removed before checking")
    void pageMarkersAreStrippedFromQuotes() {
        List<String> cards = goodCards(5);
        cards.add(card("Câu hỏi có dấu trang?", "Đáp án.", "[Trang 1] " + FACTS[5]));
        List<GeminiRecallService.GeneratedCard> result = service.validateCards(json(cards), MATERIAL, List.of(), 10);
        assertEquals(6, result.size());
        assertEquals(FACTS[5], result.get(5).quote());
    }

    @Test
    @DisplayName("the page is the one where the server finds the quote, whatever the model says")
    void pageComesFromTheSource() {
        List<RecallTextExtractor.PageText> pages = List.of(
                new RecallTextExtractor.PageText(1, String.join(" ", FACTS[0], FACTS[1], FACTS[2], FACTS[3])),
                new RecallTextExtractor.PageText(2, String.join(" ", FACTS[4], FACTS[5], FACTS[6], FACTS[7])));
        List<String> cards = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            // the model claims page 1 for everything
            cards.add("{\"question\":\"Câu hỏi " + (i + 1) + "?\",\"answer\":\"Đáp án.\",\"quote\":\"" + FACTS[i] + "\",\"page\":1}");
        }
        List<GeminiRecallService.GeneratedCard> result = service.validateCards(json(cards), MATERIAL, pages, 10);
        assertEquals(8, result.size());
        assertEquals(1, result.get(0).page());
        assertEquals(1, result.get(3).page());
        assertEquals(2, result.get(4).page());
        assertEquals(2, result.get(7).page());
    }

    @Test
    @DisplayName("the card count asked for is a ceiling")
    void countIsACeiling() {
        assertEquals(5, service.validateCards(json(goodCards(8)), MATERIAL, List.of(), 5).size());
    }

    @Test
    @DisplayName("a JSON answer wrapped in a markdown fence is accepted")
    void markdownFenceIsAccepted() {
        String fenced = "```json\n" + json(goodCards(6)) + "\n```";
        assertEquals(6, service.validateCards(fenced, MATERIAL, List.of(), 10).size());
    }

    @Test
    @DisplayName("fewer than 5 valid cards is a 502 AI_INVALID_RESPONSE that names the count, not a padded deck")
    void tooFewValidCardsIsAnError() {
        List<String> cards = goodCards(4);
        cards.add(card("Câu bịa?", "Đáp án.", "Không có trong tài liệu."));
        RecallException ex = invalid(json(cards));
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
        assertEquals("AI_INVALID_RESPONSE", ex.getCode());
        assertTrue(ex.getMessage().contains("4"), ex.getMessage());
    }

    @Test
    @DisplayName("an answer that is not JSON, or has no cards, is a 502 AI_INVALID_RESPONSE")
    void unusableAnswersAreErrors() {
        assertEquals("AI_INVALID_RESPONSE", invalid("Xin lỗi, tôi không thể giúp việc này.").getCode());
        assertEquals("AI_INVALID_RESPONSE", invalid("{\"cards\":[]}").getCode());
        assertEquals("AI_INVALID_RESPONSE", invalid("{\"other\":1}").getCode());
    }

    @Test
    @DisplayName("pages are labelled for the model so a quote stays on one page; a text source is sent as it is")
    void pageMarkersInThePrompt() {
        List<RecallTextExtractor.PageText> pages = List.of(
                new RecallTextExtractor.PageText(1, "Nội dung trang một."), new RecallTextExtractor.PageText(2, "Nội dung trang hai."));
        String withPages = GeminiRecallService.withPageMarkers("bỏ qua", pages);
        assertTrue(withPages.contains("[Trang 1]\nNội dung trang một."));
        assertTrue(withPages.contains("[Trang 2]\nNội dung trang hai."));
        assertEquals("Văn bản dán vào.", GeminiRecallService.withPageMarkers("Văn bản dán vào.", List.of()));
    }
}
