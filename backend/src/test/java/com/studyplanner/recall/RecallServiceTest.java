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
import org.springframework.http.HttpStatus;

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
        service = new RecallService(repository, geminiRecallService, objectMapper, mock(org.springframework.transaction.PlatformTransactionManager.class));
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
        when(repository.updateCardQuestionAnswer(cardId, 0, "Câu hỏi mới", "Đáp án mới")).thenReturn(true);
        when(repository.incrementDeckVersion(deckId, userId)).thenReturn(true);

        RecallDTOs.UpdateCardRequest request = new RecallDTOs.UpdateCardRequest("Câu hỏi mới", "Đáp án mới", 0);
        RecallDTOs.CardResponse response = service.updateCard(cardId, userId, request);

        assertNotNull(response);
        assertEquals("Câu hỏi mới", response.question());
        assertEquals("Đáp án mới", response.answer());
        assertTrue(response.editedByUser());
        assertEquals(1, response.version());

        verify(repository).updateCardQuestionAnswer(cardId, 0, "Câu hỏi mới", "Đáp án mới");
        verify(repository).incrementDeckVersion(deckId, userId);
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
        when(repository.updateDeckStatus(deckId, userId, "ACTIVE", 0)).thenReturn(true);

        RecallDTOs.ActivateDeckRequest request = new RecallDTOs.ActivateDeckRequest(0);
        RecallDTOs.DeckResponse response = service.activateDeck(deckId, userId, request);

        assertNotNull(response);
        assertEquals("ACTIVE", response.status());
        assertEquals(1, response.version());
        verify(repository).updateDeckStatus(deckId, userId, "ACTIVE", 0);
    }

    @Test
    @DisplayName("activateDeck rejects empty deck (409 DECK_STATE_CONFLICT)")
    void testActivateDeckRejectsEmpty() {
        RecallRepository.DeckEntity deck = new RecallRepository.DeckEntity(
                deckId, userId, goalId, materialId, "Bộ thẻ rỗng", "DRAFT", 0, 0, Instant.now()
        );
        when(repository.findDeckByIdAndUserId(deckId, userId)).thenReturn(Optional.of(deck));

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
}
