package com.studyplanner.recall;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecallServiceTest {

    @Mock
    private RecallRepository repository;

    @Mock
    private GeminiRecallService geminiRecallService;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private RecallService service;

    private final UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID goalId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID materialId = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private final UUID deckId = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private final UUID cardId = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private final UUID requestKey = UUID.fromString("99999999-9999-9999-9999-999999999999");

    @BeforeEach
    void setUp() {
        // A transaction manager that does nothing: the transaction itself is proven against PostgreSQL elsewhere.
        service = new RecallService(repository, geminiRecallService, objectMapper, mock(PlatformTransactionManager.class));
    }

    // ==========================================
    // Material Tests (RBE-01, RBE-02)
    // ==========================================

    @Test
    @DisplayName("createMaterial creates TEXT material when content is valid and goal is owned")
    void testCreateMaterialTextSuccess() {
        when(repository.isGoalOwnedByUser(goalId, userId)).thenReturn(true);

        RecallDTOs.MaterialRequest request = new RecallDTOs.MaterialRequest(
                goalId, "Chương 1: Tổng quan", "Nội dung ôn tập kiểm thử phần mềm.", null, null, null
        );

        RecallDTOs.MaterialResponse response = service.createMaterial(userId, request);

        assertNotNull(response);
        assertEquals(goalId, response.goalId());
        assertEquals("Chương 1: Tổng quan", response.title());
        assertEquals("TEXT", response.kind());
        assertNull(response.pageCount());
        assertNull(response.fileName());
        assertEquals("Nội dung ôn tập kiểm thử phần mềm.".length(), response.characterCount());

        verify(repository).saveMaterial(
                any(UUID.class), eq(userId), eq(goalId), eq("Chương 1: Tổng quan"),
                eq("TEXT"), isNull(), isNull(), eq(response.characterCount()),
                eq("Nội dung ôn tập kiểm thử phần mềm."), isNull()
        );
    }

    @Test
    @DisplayName("createMaterial rejects when goal is not owned by user (404)")
    void testCreateMaterialGoalNotOwned() {
        when(repository.isGoalOwnedByUser(goalId, userId)).thenReturn(false);

        RecallDTOs.MaterialRequest request = new RecallDTOs.MaterialRequest(
                goalId, "Chương 1", "Nội dung", null, null, null
        );

        RecallException ex = assertThrows(RecallException.class, () -> service.createMaterial(userId, request));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals("NOT_FOUND", ex.getCode());
    }

    @Test
    @DisplayName("createMaterial rejects when both content and base64 are provided (mutually exclusive)")
    void testCreateMaterialMutuallyExclusive() {
        when(repository.isGoalOwnedByUser(goalId, userId)).thenReturn(true);

        RecallDTOs.MaterialRequest request = new RecallDTOs.MaterialRequest(
                goalId, "Chương 1", "Nội dung", "doc.pdf", "application/pdf", "dGVzdA=="
        );

        RecallException ex = assertThrows(RecallException.class, () -> service.createMaterial(userId, request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("VALIDATION_ERROR", ex.getCode());
    }

    @Test
    @DisplayName("createMaterial rejects when text exceeds 30,000 characters (422 SOURCE_TOO_LONG)")
    void testCreateMaterialSourceTooLong() {
        when(repository.isGoalOwnedByUser(goalId, userId)).thenReturn(true);

        String longContent = "A".repeat(30001);
        RecallDTOs.MaterialRequest request = new RecallDTOs.MaterialRequest(
                goalId, "Quá dài", longContent, null, null, null
        );

        RecallException ex = assertThrows(RecallException.class, () -> service.createMaterial(userId, request));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
        assertEquals("SOURCE_TOO_LONG", ex.getCode());
    }

    // ==========================================
    // Deck Generation Tests (RBE-03, RBE-04, RBE-05, RBE-06, RBE-07)
    // ==========================================

    @Test
    @DisplayName("generateDeck generates DRAFT deck with cards and persists idempotency receipt")
    void testGenerateDeckSuccess() {
        when(repository.findReceipt(userId, requestKey, "GENERATE_DECK")).thenReturn(Optional.empty());

        RecallRepository.MaterialEntity material = new RecallRepository.MaterialEntity(
                materialId, userId, goalId, "Tài liệu", "TEXT", null, null, 100,
                "Kiểm thử hộp đen đánh giá hành vi. Kiểm thử hộp trắng xem xét cấu trúc.", null, Instant.now()
        );
        when(repository.findMaterialByIdAndUserId(materialId, userId)).thenReturn(Optional.of(material));
        when(repository.isGoalOwnedByUser(goalId, userId)).thenReturn(true);

        List<GeminiRecallService.GeneratedCard> mockCards = List.of(
                new GeminiRecallService.GeneratedCard("Hộp đen là gì?", "Đánh giá hành vi.", "Kiểm thử hộp đen đánh giá hành vi.", null),
                new GeminiRecallService.GeneratedCard("Hộp trắng là gì?", "Xem xét cấu trúc.", "Kiểm thử hộp trắng xem xét cấu trúc.", null)
        );
        when(geminiRecallService.generateCards(eq(material.extractedText()), anyList(), eq("Bộ thẻ ôn thi"), eq(10)))
                .thenReturn(mockCards);

        RecallDTOs.CreateDeckRequest request = new RecallDTOs.CreateDeckRequest(materialId, "Bộ thẻ ôn thi", 10);
        RecallDTOs.DeckResponse response = service.generateDeck(userId, requestKey, request);

        assertNotNull(response);
        assertEquals("Bộ thẻ ôn thi", response.title());
        assertEquals("DRAFT", response.status());
        assertEquals(2, response.cardCount());
        assertEquals(0, response.version());

        verify(repository).saveDeck(any(UUID.class), eq(userId), eq(goalId), eq(materialId), eq("Bộ thẻ ôn thi"), eq("DRAFT"), eq(2), eq(0));
        verify(repository).saveCards(anyList());
        verify(repository).saveReceipt(eq(userId), eq(requestKey), eq("GENERATE_DECK"), anyString(), anyString());
    }

    @Test
    @DisplayName("generateDeck returns cached result when called again with same key and payload (idempotent replay)")
    void testGenerateDeckReplay() throws Exception {
        RecallDTOs.DeckResponse cachedDeck = new RecallDTOs.DeckResponse(
                deckId, goalId, materialId, "Bộ thẻ ôn thi", "DRAFT", 5, 0, Instant.now()
        );
        String cachedJson = objectMapper.writeValueAsString(cachedDeck);

        // Pre-compute payload hash for materialId:title:count
        RecallDTOs.CreateDeckRequest request = new RecallDTOs.CreateDeckRequest(materialId, "Bộ thẻ ôn thi", 10);
        String payloadString = materialId + ":Bộ thẻ ôn thi:10";
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        String payloadHash = java.util.HexFormat.of().formatHex(md.digest(payloadString.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        RecallRepository.ReceiptRecord receipt = new RecallRepository.ReceiptRecord(
                UUID.randomUUID(), userId, requestKey, "GENERATE_DECK", payloadHash, cachedJson, Instant.now()
        );
        when(repository.findReceipt(userId, requestKey, "GENERATE_DECK")).thenReturn(Optional.of(receipt));

        RecallDTOs.DeckResponse response = service.generateDeck(userId, requestKey, request);

        assertNotNull(response);
        assertEquals(deckId, response.id());
        assertEquals("Bộ thẻ ôn thi", response.title());
        verify(geminiRecallService, never()).generateCards(anyString(), anyList(), anyString(), anyInt());
    }

    @Test
    @DisplayName("generateDeck throws 409 IDEMPOTENCY_CONFLICT when same key is used with different payload")
    void testGenerateDeckIdempotencyConflict() {
        RecallRepository.ReceiptRecord receipt = new RecallRepository.ReceiptRecord(
                UUID.randomUUID(), userId, requestKey, "GENERATE_DECK", "different-hash", "{}", Instant.now()
        );
        when(repository.findReceipt(userId, requestKey, "GENERATE_DECK")).thenReturn(Optional.of(receipt));

        RecallDTOs.CreateDeckRequest request = new RecallDTOs.CreateDeckRequest(materialId, "Bộ thẻ ôn thi", 10);

        RecallException ex = assertThrows(RecallException.class, () -> service.generateDeck(userId, requestKey, request));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("IDEMPOTENCY_CONFLICT", ex.getCode());
    }

    @Test
    @DisplayName("generateDeck rejects when material is too short or just a single brief sentence (422 INSUFFICIENT_MATERIAL)")
    void testGenerateDeckRejectsWhenMaterialTooShortOrSingleSentence() {
        RecallRepository.MaterialEntity material = new RecallRepository.MaterialEntity(
                materialId, userId, goalId, "Tài liệu ngắn", "TEXT", null, null, 18,
                "Hôm nay trời đẹp.", null, Instant.now()
        );
        when(repository.findMaterialByIdAndUserId(materialId, userId)).thenReturn(Optional.of(material));

        RecallDTOs.CreateDeckRequest request = new RecallDTOs.CreateDeckRequest(materialId, "Bộ thẻ", 10);
        RecallException ex = assertThrows(RecallException.class, () -> service.generateDeck(userId, requestKey, request));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
        assertEquals("INSUFFICIENT_MATERIAL", ex.getCode());
        assertEquals("Tài liệu chưa đủ nội dung để tạo câu hỏi. Vui lòng bổ sung thêm thông tin.", ex.getMessage());
    }

    @Test
    @DisplayName("generateDeck handles NO_SUITABLE_CONTENT when text has no educational facts (422 NO_SUITABLE_CONTENT)")
    void testGenerateDeckRejectsWhenNoSuitableContent() {
        RecallRepository.MaterialEntity material = new RecallRepository.MaterialEntity(
                materialId, userId, goalId, "Chào hỏi", "TEXT", null, null, 120,
                "Xin chào các bạn. Chào mừng các bạn. Cảm ơn rất nhiều. Tạm biệt nhé. Hẹn gặp lại.", null, Instant.now()
        );
        when(repository.findMaterialByIdAndUserId(materialId, userId)).thenReturn(Optional.of(material));
        when(geminiRecallService.generateCards(anyString(), anyList(), anyString(), anyInt()))
                .thenThrow(RecallException.noSuitableContent("Không tìm thấy nội dung phù hợp để tạo flashcard."));

        RecallDTOs.CreateDeckRequest request = new RecallDTOs.CreateDeckRequest(materialId, "Bộ thẻ", 10);
        RecallException ex = assertThrows(RecallException.class, () -> service.generateDeck(userId, requestKey, request));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
        assertEquals("NO_SUITABLE_CONTENT", ex.getCode());
        assertEquals("Không tìm thấy nội dung phù hợp để tạo flashcard.", ex.getMessage());
    }

    @Test
    @DisplayName("generateDeck handles AI timeout (504 AI_TIMEOUT)")
    void testGenerateDeckHandlesAiTimeout() {
        RecallRepository.MaterialEntity material = new RecallRepository.MaterialEntity(
                materialId, userId, goalId, "Tài liệu", "TEXT", null, null, 150,
                "Kiến trúc hệ quản trị cơ sở dữ liệu quan hệ bao gồm các thành phần lưu trữ và thực thi truy vấn.", null, Instant.now()
        );
        when(repository.findMaterialByIdAndUserId(materialId, userId)).thenReturn(Optional.of(material));
        when(geminiRecallService.generateCards(anyString(), anyList(), anyString(), anyInt()))
                .thenThrow(RecallException.aiTimeout("AI hiện phản hồi quá lâu. Vui lòng thử lại sau."));

        RecallDTOs.CreateDeckRequest request = new RecallDTOs.CreateDeckRequest(materialId, "Bộ thẻ", 10);
        RecallException ex = assertThrows(RecallException.class, () -> service.generateDeck(userId, requestKey, request));

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, ex.getStatus());
        assertEquals("AI_TIMEOUT", ex.getCode());
        assertEquals("AI hiện phản hồi quá lâu. Vui lòng thử lại sau.", ex.getMessage());
    }

    @Test
    @DisplayName("generateDeck handles AI malformed response (502 AI_MALFORMED_RESPONSE)")
    void testGenerateDeckHandlesAiMalformedResponse() {
        RecallRepository.MaterialEntity material = new RecallRepository.MaterialEntity(
                materialId, userId, goalId, "Tài liệu", "TEXT", null, null, 150,
                "Kiến trúc hệ quản trị cơ sở dữ liệu quan hệ bao gồm các thành phần lưu trữ và thực thi truy vấn.", null, Instant.now()
        );
        when(repository.findMaterialByIdAndUserId(materialId, userId)).thenReturn(Optional.of(material));
        when(geminiRecallService.generateCards(anyString(), anyList(), anyString(), anyInt()))
                .thenThrow(RecallException.aiMalformedResponse("Không thể xử lý kết quả tạo thẻ. Vui lòng thử lại."));

        RecallDTOs.CreateDeckRequest request = new RecallDTOs.CreateDeckRequest(materialId, "Bộ thẻ", 10);
        RecallException ex = assertThrows(RecallException.class, () -> service.generateDeck(userId, requestKey, request));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
        assertEquals("AI_MALFORMED_RESPONSE", ex.getCode());
        assertEquals("Không thể xử lý kết quả tạo thẻ. Vui lòng thử lại.", ex.getMessage());
    }

    @Test
    @DisplayName("generateDeck handles AI unavailable (503 AI_UNAVAILABLE)")
    void testGenerateDeckHandlesAiUnavailable() {
        RecallRepository.MaterialEntity material = new RecallRepository.MaterialEntity(
                materialId, userId, goalId, "Tài liệu", "TEXT", null, null, 150,
                "Kiến trúc hệ quản trị cơ sở dữ liệu quan hệ bao gồm các thành phần lưu trữ và thực thi truy vấn.", null, Instant.now()
        );
        when(repository.findMaterialByIdAndUserId(materialId, userId)).thenReturn(Optional.of(material));
        when(geminiRecallService.generateCards(anyString(), anyList(), anyString(), anyInt()))
                .thenThrow(RecallException.aiUnavailable("Không thể kết nối đến dịch vụ tạo flashcard. Vui lòng thử lại sau."));

        RecallDTOs.CreateDeckRequest request = new RecallDTOs.CreateDeckRequest(materialId, "Bộ thẻ", 10);
        RecallException ex = assertThrows(RecallException.class, () -> service.generateDeck(userId, requestKey, request));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        assertEquals("AI_UNAVAILABLE", ex.getCode());
        assertEquals("Không thể kết nối đến dịch vụ tạo flashcard. Vui lòng thử lại sau.", ex.getMessage());
    }

    @Test
    @DisplayName("generateDeck preserves deck and cards when only a partial subset has quality warnings")
    void testGenerateDeckPreservesCardsWhenSomeHaveQualityWarnings() {
        RecallRepository.MaterialEntity material = new RecallRepository.MaterialEntity(
                materialId, userId, goalId, "Tài liệu", "TEXT", null, null, 150,
                "Kiến trúc hệ quản trị cơ sở dữ liệu quan hệ bao gồm các thành phần lưu trữ và thực thi truy vấn.", null, Instant.now()
        );
        when(repository.findMaterialByIdAndUserId(materialId, userId)).thenReturn(Optional.of(material));
        when(repository.isGoalOwnedByUser(goalId, userId)).thenReturn(true);

        List<GeminiRecallService.GeneratedCard> mockCards = List.of(
                new GeminiRecallService.GeneratedCard("Hệ quản trị CSDL quan hệ là gì?", "Bao gồm lưu trữ và thực thi.", "Kiến trúc hệ quản trị cơ sở dữ liệu quan hệ bao gồm các thành phần lưu trữ và thực thi truy vấn.", 1, null),
                new GeminiRecallService.GeneratedCard("Thành phần nào thực thi truy vấn?", "Thực thi truy vấn.", "Kiến trúc hệ quản trị cơ sở dữ liệu quan hệ bao gồm các thành phần lưu trữ và thực thi truy vấn.", 1, "Cần xem lại độ rõ ràng của câu hỏi.")
        );
        when(geminiRecallService.generateCards(eq(material.extractedText()), anyList(), anyString(), anyInt()))
                .thenReturn(mockCards);

        RecallDTOs.CreateDeckRequest request = new RecallDTOs.CreateDeckRequest(materialId, "Bộ thẻ", 10);
        RecallDTOs.DeckResponse response = service.generateDeck(userId, requestKey, request);

        assertNotNull(response);
        assertEquals("DRAFT", response.status());
        assertEquals(2, response.cardCount());

        verify(repository).saveDeck(any(UUID.class), eq(userId), eq(goalId), eq(materialId), eq("Bộ thẻ"), eq("DRAFT"), eq(2), eq(0));
        verify(repository).saveCards(argThat(cards ->
                cards.size() == 2 &&
                cards.get(0).qualityWarning() == null &&
                cards.get(1).qualityWarning() != null
        ));
    }

    @Test
    @DisplayName("generateDeck rejects when all generated cards fail quality criteria (422 ALL_CARDS_FAILED_QUALITY)")
    void testGenerateDeckRejectsWhenAllCardsFailQuality() {
        RecallRepository.MaterialEntity material = new RecallRepository.MaterialEntity(
                materialId, userId, goalId, "Tài liệu", "TEXT", null, null, 150,
                "Kiến trúc hệ quản trị cơ sở dữ liệu quan hệ bao gồm các thành phần lưu trữ và thực thi truy vấn.", null, Instant.now()
        );
        when(repository.findMaterialByIdAndUserId(materialId, userId)).thenReturn(Optional.of(material));
        when(geminiRecallService.generateCards(anyString(), anyList(), anyString(), anyInt()))
                .thenThrow(RecallException.allCardsFailedQuality("Các thẻ được tạo chưa đáp ứng tiêu chí chất lượng. Vui lòng bổ sung hoặc chỉnh sửa tài liệu."));

        RecallDTOs.CreateDeckRequest request = new RecallDTOs.CreateDeckRequest(materialId, "Bộ thẻ", 10);
        RecallException ex = assertThrows(RecallException.class, () -> service.generateDeck(userId, requestKey, request));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
        assertEquals("ALL_CARDS_FAILED_QUALITY", ex.getCode());
        assertEquals("Các thẻ được tạo chưa đáp ứng tiêu chí chất lượng. Vui lòng bổ sung hoặc chỉnh sửa tài liệu.", ex.getMessage());
    }

    // ==========================================
    // Card Update Tests (RBE-08, RBE-09)
    // ==========================================

    @Test
    @DisplayName("updateCard updates question/answer in DRAFT deck, sets editedByUser=true, increments versions")
    void testUpdateCardSuccess() {
        RecallRepository.CardEntity card = new RecallRepository.CardEntity(
                cardId, deckId, "Câu hỏi cũ", "Đáp án cũ", materialId, 1, "Trích dẫn",
                false, 0, 0, null, null, 0, Instant.now()
        );
        when(repository.findCardByIdAndUserId(cardId, userId)).thenReturn(Optional.of(card));

        RecallRepository.DeckEntity deck = new RecallRepository.DeckEntity(
                deckId, userId, goalId, materialId, "Bộ thẻ", "DRAFT", 1, 0, Instant.now()
        );
        when(repository.findDeckByIdAndUserId(deckId, userId)).thenReturn(Optional.of(deck));
        when(repository.updateCardQuestionAnswer(cardId, 0, "Câu hỏi mới", "Đáp án mới", null)).thenReturn(true);
        when(repository.incrementDeckVersion(deckId, userId)).thenReturn(true);

        RecallDTOs.UpdateCardRequest request = new RecallDTOs.UpdateCardRequest("Câu hỏi mới", "Đáp án mới", 0);
        RecallDTOs.CardResponse response = service.updateCard(cardId, userId, request);

        assertNotNull(response);
        assertEquals("Câu hỏi mới", response.question());
        assertEquals("Đáp án mới", response.answer());
        assertTrue(response.editedByUser());
        assertEquals(1, response.version());

        verify(repository).updateCardQuestionAnswer(cardId, 0, "Câu hỏi mới", "Đáp án mới", null);
        verify(repository).incrementDeckVersion(deckId, userId);
    }

    @Test
    @DisplayName("updateCard flags quality warning when edited into off-topic knowledge outside source material")
    void testUpdateCardOffTopicContentFlagsQualityWarning() {
        RecallRepository.CardEntity card = new RecallRepository.CardEntity(
                cardId, deckId, "Khóa chính là gì?", "Là định danh duy nhất", materialId, 1, "Primary key là khóa chính",
                false, 0, 0, null, null, 0, Instant.now()
        );
        when(repository.findCardByIdAndUserId(cardId, userId)).thenReturn(Optional.of(card));

        RecallRepository.DeckEntity deck = new RecallRepository.DeckEntity(
                deckId, userId, goalId, materialId, "Cơ sở dữ liệu", "DRAFT", 1, 0, Instant.now()
        );
        when(repository.findDeckByIdAndUserId(deckId, userId)).thenReturn(Optional.of(deck));

        RecallRepository.MaterialEntity material = new RecallRepository.MaterialEntity(
                materialId, userId, goalId, "Chương 1 CSDL", "TEXT", null, 1, 100,
                "Khóa chính (Primary Key) là một trường dùng để định danh duy nhất mỗi bản ghi trong bảng cơ sở dữ liệu quan hệ.",
                null, Instant.now()
        );
        when(repository.findMaterialByIdAndUserId(materialId, userId)).thenReturn(Optional.of(material));
        when(repository.updateCardQuestionAnswer(eq(cardId), eq(0), anyString(), anyString(), anyString())).thenReturn(true);
        when(repository.incrementDeckVersion(deckId, userId)).thenReturn(true);

        // Edit into completely off-topic content (Cristiano Ronaldo)
        RecallDTOs.UpdateCardRequest request = new RecallDTOs.UpdateCardRequest(
                "Cristiano Ronaldo sinh năm bao nhiêu?",
                "1985 tại Bồ Đào Nha",
                0
        );
        RecallDTOs.CardResponse response = service.updateCard(cardId, userId, request);

        assertNotNull(response);
        assertEquals("Cristiano Ronaldo sinh năm bao nhiêu?", response.question());
        assertEquals("1985 tại Bồ Đào Nha", response.answer());
        assertTrue(response.editedByUser());
        assertNotNull(response.qualityWarning());
        assertTrue(response.qualityWarning().contains("không có căn cứ trong tài liệu nguồn"));

        verify(repository).updateCardQuestionAnswer(
                eq(cardId), eq(0),
                eq("Cristiano Ronaldo sinh năm bao nhiêu?"),
                eq("1985 tại Bồ Đào Nha"),
                argThat(w -> w != null && w.contains("không có căn cứ trong tài liệu nguồn"))
        );
    }

    @Test
    @DisplayName("updateCard rejects edit when deck is ACTIVE (409 DECK_STATE_CONFLICT)")
    void testUpdateCardRejectsWhenActive() {
        RecallRepository.CardEntity card = new RecallRepository.CardEntity(
                cardId, deckId, "Q", "A", materialId, 1, "Quote",
                false, 0, 0, null, null, 0, Instant.now()
        );
        when(repository.findCardByIdAndUserId(cardId, userId)).thenReturn(Optional.of(card));

        RecallRepository.DeckEntity deck = new RecallRepository.DeckEntity(
                deckId, userId, goalId, materialId, "Bộ thẻ", "ACTIVE", 1, 1, Instant.now()
        );
        when(repository.findDeckByIdAndUserId(deckId, userId)).thenReturn(Optional.of(deck));

        RecallDTOs.UpdateCardRequest request = new RecallDTOs.UpdateCardRequest("Q mới", "A mới", 0);
        RecallException ex = assertThrows(RecallException.class, () -> service.updateCard(cardId, userId, request));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("DECK_STATE_CONFLICT", ex.getCode());
    }

    @Test
    @DisplayName("updateCard rejects edit when expectedVersion mismatches (409 CARD_VERSION_CONFLICT)")
    void testUpdateCardVersionConflict() {
        RecallRepository.CardEntity card = new RecallRepository.CardEntity(
                cardId, deckId, "Q", "A", materialId, 1, "Quote",
                false, 2, 0, null, null, 0, Instant.now()
        );
        when(repository.findCardByIdAndUserId(cardId, userId)).thenReturn(Optional.of(card));

        RecallRepository.DeckEntity deck = new RecallRepository.DeckEntity(
                deckId, userId, goalId, materialId, "Bộ thẻ", "DRAFT", 1, 0, Instant.now()
        );
        when(repository.findDeckByIdAndUserId(deckId, userId)).thenReturn(Optional.of(deck));

        RecallDTOs.UpdateCardRequest request = new RecallDTOs.UpdateCardRequest("Q mới", "A mới", 0);
        RecallException ex = assertThrows(RecallException.class, () -> service.updateCard(cardId, userId, request));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("CARD_VERSION_CONFLICT", ex.getCode());
        assertEquals(2, ex.getContext().get("currentVersion"));
    }

    // ==========================================
    // Deck Activation Tests (RBE-10)
    // ==========================================

    @Test
    @DisplayName("activateDeck changes status to ACTIVE with matching version")
    void testActivateDeckSuccess() {
        RecallRepository.DeckEntity deck = new RecallRepository.DeckEntity(
                deckId, userId, goalId, materialId, "Bộ thẻ", "DRAFT", 5, 0, Instant.now()
        );
        when(repository.findDeckByIdAndUserId(deckId, userId)).thenReturn(Optional.of(deck));
        when(repository.countCardsInDeck(deckId)).thenReturn(5);
        when(repository.updateDeckStatus(deckId, userId, "ACTIVE", 0)).thenReturn(true);

        RecallDTOs.ActivateDeckRequest request = new RecallDTOs.ActivateDeckRequest(0);
        RecallDTOs.DeckResponse response = service.activateDeck(deckId, userId, request);

        assertNotNull(response);
        assertEquals("ACTIVE", response.status());
        assertEquals(1, response.version());
        verify(repository).updateDeckStatus(deckId, userId, "ACTIVE", 0);
    }

    @Test
    @DisplayName("activateDeck rejects activation when deck contains cards with quality warnings (400 VALIDATION_ERROR)")
    void testActivateDeckRejectsWhenCardsHaveQualityWarning() {
        RecallRepository.DeckEntity deck = new RecallRepository.DeckEntity(
                deckId, userId, goalId, materialId, "Bộ thẻ", "DRAFT", 1, 0, Instant.now()
        );
        when(repository.findDeckByIdAndUserId(deckId, userId)).thenReturn(Optional.of(deck));
        when(repository.countCardsInDeck(deckId)).thenReturn(1);

        RecallDTOs.CardResponse warnedCard = new RecallDTOs.CardResponse(
                cardId, deckId, "Cristiano Ronaldo sinh năm bao nhiêu?", "1985",
                new RecallDTOs.CardSource(materialId, 1, "Quote"),
                true, 1, null, null, 0,
                "Cả câu hỏi và đáp án đều không có căn cứ trong tài liệu nguồn."
        );
        when(repository.findCardsByDeckId(deckId)).thenReturn(List.of(warnedCard));

        RecallDTOs.ActivateDeckRequest request = new RecallDTOs.ActivateDeckRequest(0);
        RecallException ex = assertThrows(RecallException.class, () -> service.activateDeck(deckId, userId, request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("VALIDATION_ERROR", ex.getCode());
        assertTrue(ex.getMessage().contains("chưa đạt chất lượng"));
        verify(repository, never()).updateDeckStatus(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("activateDeck rejects a deck with no stored cards even when its cached card_count says otherwise (409)")
    void testActivateDeckRejectsEmpty() {
        RecallRepository.DeckEntity deck = new RecallRepository.DeckEntity(
                deckId, userId, goalId, materialId, "Bộ thẻ rỗng", "DRAFT", 5, 0, Instant.now()
        );
        when(repository.findDeckByIdAndUserId(deckId, userId)).thenReturn(Optional.of(deck));
        when(repository.countCardsInDeck(deckId)).thenReturn(0);

        RecallDTOs.ActivateDeckRequest request = new RecallDTOs.ActivateDeckRequest(0);
        RecallException ex = assertThrows(RecallException.class, () -> service.activateDeck(deckId, userId, request));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("DECK_STATE_CONFLICT", ex.getCode());
    }

    @Test
    @DisplayName("activateDeck rejects version mismatch (409 DECK_VERSION_CONFLICT)")
    void testActivateDeckVersionMismatch() {
        RecallRepository.DeckEntity deck = new RecallRepository.DeckEntity(
                deckId, userId, goalId, materialId, "Bộ thẻ", "DRAFT", 5, 2, Instant.now()
        );
        when(repository.findDeckByIdAndUserId(deckId, userId)).thenReturn(Optional.of(deck));

        RecallDTOs.ActivateDeckRequest request = new RecallDTOs.ActivateDeckRequest(1);
        RecallException ex = assertThrows(RecallException.class, () -> service.activateDeck(deckId, userId, request));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("DECK_VERSION_CONFLICT", ex.getCode());
    }

    // ==========================================
    // Review Tests (RBE-11, RBE-12, RBE-13)
    // ==========================================

    @Test
    @DisplayName("recordReview records self-rating in ACTIVE deck and calculates progress based on last rating")
    void testRecordReviewSuccess() {
        when(repository.findReceipt(userId, requestKey, "REVIEW")).thenReturn(Optional.empty());

        RecallRepository.CardEntity card = new RecallRepository.CardEntity(
                cardId, deckId, "Q", "A", materialId, 1, "Quote",
                false, 0, 0, null, null, 0, Instant.now()
        );
        when(repository.findCardByIdAndUserId(cardId, userId)).thenReturn(Optional.of(card));

        RecallRepository.DeckEntity deck = new RecallRepository.DeckEntity(
                deckId, userId, goalId, materialId, "Bộ thẻ", "ACTIVE", 5, 1, Instant.now()
        );
        when(repository.findDeckByIdAndUserId(deckId, userId)).thenReturn(Optional.of(deck));

        RecallDTOs.DeckProgressResponse mockProgress = new RecallDTOs.DeckProgressResponse(5, 1, 1, 0);
        when(repository.calculateDeckProgress(deckId)).thenReturn(mockProgress);

        RecallDTOs.CardReviewRequest request = new RecallDTOs.CardReviewRequest("REMEMBERED");
        RecallDTOs.CardReviewResponse response = service.recordReview(cardId, userId, requestKey, request);

        assertNotNull(response);
        assertEquals(cardId, response.cardId());
        assertEquals("REMEMBERED", response.rating());
        assertEquals(5, response.progress().totalCards());
        assertEquals(1, response.progress().reviewedCards());
        assertEquals(1, response.progress().rememberedCards());
        assertEquals(0, response.progress().forgottenCards());

        verify(repository).saveReview(any(UUID.class), eq(userId), eq(cardId), eq("REMEMBERED"), any(Instant.class));
        verify(repository).updateCardReviewState(eq(cardId), eq("REMEMBERED"), any(Instant.class));
        verify(repository).saveReceipt(eq(userId), eq(requestKey), eq("REVIEW"), anyString(), anyString());
    }

    @Test
    @DisplayName("recordReview rejects review when deck is DRAFT (409 DECK_STATE_CONFLICT)")
    void testRecordReviewRejectsDraftDeck() {
        when(repository.findReceipt(userId, requestKey, "REVIEW")).thenReturn(Optional.empty());

        RecallRepository.CardEntity card = new RecallRepository.CardEntity(
                cardId, deckId, "Q", "A", materialId, 1, "Quote",
                false, 0, 0, null, null, 0, Instant.now()
        );
        when(repository.findCardByIdAndUserId(cardId, userId)).thenReturn(Optional.of(card));

        RecallRepository.DeckEntity deck = new RecallRepository.DeckEntity(
                deckId, userId, goalId, materialId, "Bộ thẻ", "DRAFT", 5, 0, Instant.now()
        );
        when(repository.findDeckByIdAndUserId(deckId, userId)).thenReturn(Optional.of(deck));

        RecallDTOs.CardReviewRequest request = new RecallDTOs.CardReviewRequest("REMEMBERED");
        RecallException ex = assertThrows(RecallException.class, () -> service.recordReview(cardId, userId, requestKey, request));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("DECK_STATE_CONFLICT", ex.getCode());
    }

    @Test
    @DisplayName("recordReview rejects invalid rating enum (400 VALIDATION_ERROR)")
    void testRecordReviewInvalidRating() {
        RecallDTOs.CardReviewRequest request = new RecallDTOs.CardReviewRequest("EXCELLENT");
        RecallException ex = assertThrows(RecallException.class, () -> service.recordReview(cardId, userId, requestKey, request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("VALIDATION_ERROR", ex.getCode());
    }

    // ==========================================
    // Upload and generation fixes
    // ==========================================

    private static String sha256(String text) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        return java.util.HexFormat.of().formatHex(md.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private RecallRepository.MaterialEntity textMaterial() {
        return new RecallRepository.MaterialEntity(
                materialId, userId, goalId, "Tài liệu", "TEXT", null, null, 100,
                "Kiểm thử hộp đen đánh giá hành vi. Kiểm thử hộp trắng xem xét cấu trúc.", null, Instant.now());
    }

    private static final List<GeminiRecallService.GeneratedCard> TWO_CARDS = List.of(
            new GeminiRecallService.GeneratedCard("Hộp đen là gì?", "Đánh giá hành vi.", "Kiểm thử hộp đen đánh giá hành vi.", null),
            new GeminiRecallService.GeneratedCard("Hộp trắng là gì?", "Xem xét cấu trúc.", "Kiểm thử hộp trắng xem xét cấu trúc.", null));

    @Test
    @DisplayName("createMaterial stores pasted text in Unicode NFC with LF line breaks")
    void testCreateMaterialNormalizesText() {
        when(repository.isGoalOwnedByUser(goalId, userId)).thenReturn(true);
        String decomposed = java.text.Normalizer.normalize("Kiểm thử hộp đen", java.text.Normalizer.Form.NFD) + "\r\nDòng hai";
        String expected = "Kiểm thử hộp đen\nDòng hai";

        RecallDTOs.MaterialResponse response = service.createMaterial(userId,
                new RecallDTOs.MaterialRequest(goalId, "NFD", decomposed, null, null, null));

        assertEquals(expected.length(), response.characterCount());
        verify(repository).saveMaterial(any(UUID.class), eq(userId), eq(goalId), eq("NFD"), eq("TEXT"),
                isNull(), isNull(), eq(expected.length()), eq(expected), isNull());
    }

    @Test
    @DisplayName("createMaterial rejects a file name over 255 characters with 400 fileName too_long (not a database 500)")
    void testCreateMaterialRejectsLongFileName() {
        when(repository.isGoalOwnedByUser(goalId, userId)).thenReturn(true);
        RecallDTOs.MaterialRequest request = new RecallDTOs.MaterialRequest(
                goalId, "Tài liệu", null, "n".repeat(256) + ".pdf", "application/pdf", "dGVzdA==");

        RecallException ex = assertThrows(RecallException.class, () -> service.createMaterial(userId, request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals("fileName", ex.getDetails().get(0).field());
        assertEquals("too_long", ex.getDetails().get(0).issue());
        verify(repository, never()).saveMaterial(any(), any(), any(), any(), any(), any(), any(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("a second request with the same key while Gemini is still working gets 409 GENERATION_IN_PROGRESS")
    void testGenerateDeckSameKeyInFlight() throws Exception {
        when(repository.findReceipt(userId, requestKey, "GENERATE_DECK")).thenReturn(Optional.empty());
        when(repository.findMaterialByIdAndUserId(materialId, userId)).thenReturn(Optional.of(textMaterial()));
        when(repository.isGoalOwnedByUser(goalId, userId)).thenReturn(true);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(geminiRecallService.generateCards(anyString(), anyList(), anyString(), anyInt())).thenAnswer(invocation -> {
            started.countDown();
            release.await(10, TimeUnit.SECONDS);
            return TWO_CARDS;
        });
        RecallDTOs.CreateDeckRequest request = new RecallDTOs.CreateDeckRequest(materialId, "Bộ thẻ ôn thi", 10);

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<RecallDTOs.DeckResponse> first = pool.submit(() -> service.generateDeck(userId, requestKey, request));
            assertTrue(started.await(10, TimeUnit.SECONDS));

            RecallException ex = assertThrows(RecallException.class, () -> service.generateDeck(userId, requestKey, request));
            assertEquals(HttpStatus.CONFLICT, ex.getStatus());
            assertEquals("GENERATION_IN_PROGRESS", ex.getCode());

            release.countDown();
            assertEquals("DRAFT", first.get(10, TimeUnit.SECONDS).status());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
        verify(geminiRecallService, times(1)).generateCards(anyString(), anyList(), anyString(), anyInt());
        verify(repository, times(1)).saveDeck(any(), any(), any(), any(), any(), any(), anyInt(), anyInt());

        // the key is free again once the first request is done
        assertDoesNotThrow(() -> service.generateDeck(userId, requestKey, request));
    }

    @Test
    @DisplayName("losing the race on the receipt returns the winner's deck and writes no deck or cards of its own")
    void testGenerateDeckLostRace() throws Exception {
        RecallDTOs.DeckResponse winner = new RecallDTOs.DeckResponse(
                deckId, goalId, materialId, "Bộ thẻ ôn thi", "DRAFT", 2, 0, Instant.now());
        RecallRepository.ReceiptRecord receipt = new RecallRepository.ReceiptRecord(
                UUID.randomUUID(), userId, requestKey, "GENERATE_DECK", sha256(materialId + ":Bộ thẻ ôn thi:10"),
                objectMapper.writeValueAsString(winner), Instant.now());
        when(repository.findReceipt(userId, requestKey, "GENERATE_DECK"))
                .thenReturn(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(receipt));
        when(repository.findMaterialByIdAndUserId(materialId, userId)).thenReturn(Optional.of(textMaterial()));
        when(repository.isGoalOwnedByUser(goalId, userId)).thenReturn(true);
        when(geminiRecallService.generateCards(anyString(), anyList(), anyString(), anyInt())).thenReturn(TWO_CARDS);
        doThrow(new DuplicateKeyException("duplicate key")).when(repository)
                .saveReceipt(eq(userId), eq(requestKey), eq("GENERATE_DECK"), anyString(), anyString());

        RecallDTOs.DeckResponse response = service.generateDeck(userId, requestKey,
                new RecallDTOs.CreateDeckRequest(materialId, "Bộ thẻ ôn thi", 10));

        assertEquals(deckId, response.id());
        verify(repository, never()).saveDeck(any(), any(), any(), any(), any(), any(), anyInt(), anyInt());
        verify(repository, never()).saveCards(anyList());
    }

    @Test
    @DisplayName("an AI failure saves nothing and the error reaches the caller")
    void testGenerateDeckAiFailureSavesNothing() {
        when(repository.findReceipt(userId, requestKey, "GENERATE_DECK")).thenReturn(Optional.empty());
        when(repository.findMaterialByIdAndUserId(materialId, userId)).thenReturn(Optional.of(textMaterial()));
        when(geminiRecallService.generateCards(anyString(), anyList(), anyString(), anyInt()))
                .thenThrow(RecallException.aiUnavailable("Dịch vụ AI tạm thời không dùng được."));

        RecallException ex = assertThrows(RecallException.class, () -> service.generateDeck(userId, requestKey,
                new RecallDTOs.CreateDeckRequest(materialId, "Bộ thẻ ôn thi", 10)));
        assertEquals("AI_UNAVAILABLE", ex.getCode());
        verify(repository, never()).saveDeck(any(), any(), any(), any(), any(), any(), anyInt(), anyInt());
        verify(repository, never()).saveReceipt(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("getMaterialDetail returns a capped preview of the extracted text and hides other users' sources")
    void testMaterialDetail() {
        RecallRepository.MaterialEntity big = new RecallRepository.MaterialEntity(
                materialId, userId, goalId, "Tài liệu dài", "PDF", "a.pdf", 3, 3000, "x".repeat(3000), "[]", Instant.now());
        when(repository.findMaterialByIdAndUserId(materialId, userId)).thenReturn(Optional.of(big));
        RecallDTOs.MaterialDetail detail = service.getMaterialDetail(userId, materialId);
        assertEquals(1200, detail.preview().length());
        assertTrue(detail.previewTruncated());
        assertEquals(3, detail.pageCount());

        RecallRepository.MaterialEntity small = new RecallRepository.MaterialEntity(
                materialId, userId, goalId, "Ngắn", "TEXT", null, null, 5, "Ngắn.", null, Instant.now());
        when(repository.findMaterialByIdAndUserId(materialId, userId)).thenReturn(Optional.of(small));
        assertEquals("Ngắn.", service.getMaterialDetail(userId, materialId).preview());
        assertFalse(service.getMaterialDetail(userId, materialId).previewTruncated());

        UUID stranger = UUID.fromString("66666666-6666-6666-6666-666666666666");
        when(repository.findMaterialByIdAndUserId(materialId, stranger)).thenReturn(Optional.empty());
        assertEquals("NOT_FOUND", assertThrows(RecallException.class, () -> service.getMaterialDetail(stranger, materialId)).getCode());
    }
}
