package com.studyplanner.recall;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RecallService {

    private final RecallRepository repository;
    private final GeminiRecallService geminiRecallService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;
    /** Requests (user and Idempotency-Key) whose Gemini call is running, so a second copy cannot start another deck. */
    private final Set<String> generationsInFlight = ConcurrentHashMap.newKeySet();

    private static final int PREVIEW_CHARS = 1200;
    private static final int FILE_NAME_MAX = 255;

    public RecallService(
            RecallRepository repository,
            GeminiRecallService geminiRecallService,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.geminiRecallService = Objects.requireNonNull(geminiRecallService, "geminiRecallService must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.tx = new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager must not be null"));
    }

    // ==========================================
    // Materials
    // ==========================================

    @Transactional
    public RecallDTOs.MaterialResponse createMaterial(UUID userId, RecallDTOs.MaterialRequest request) {
        if (request == null) {
            throw RecallException.validation("body", "missing_body", "Dữ liệu yêu cầu không được để trống.");
        }
        if (request.goalId() == null) {
            throw RecallException.validation("goalId", "required", "Mục tiêu (goalId) là bắt buộc.");
        }
        if (!repository.isGoalOwnedByUser(request.goalId(), userId)) {
            throw RecallException.notFound("Mục tiêu không tồn tại hoặc không thuộc về bạn.");
        }

        String title = request.title() != null ? request.title().trim() : "";
        if (title.isEmpty() || title.length() > 120) {
            throw RecallException.validation("title", "length_range", "Tiêu đề tài liệu phải từ 1 đến 120 ký tự.");
        }

        boolean hasText = request.content() != null && !request.content().isBlank();
        boolean hasPdf = request.base64() != null && !request.base64().isBlank();

        if (hasText && hasPdf) {
            throw RecallException.validation("content", "mutually_exclusive",
                    "Chỉ được cung cấp một trong hai dạng tài liệu: văn bản (content) hoặc tệp PDF (base64).");
        }
        if (!hasText && !hasPdf) {
            throw RecallException.validation("content", "required",
                    "Vui lòng cung cấp nội dung văn bản (content) hoặc tệp PDF (base64).");
        }

        UUID materialId = UUID.randomUUID();
        String kind;
        String fileName = null;
        Integer pageCount = null;
        int characterCount;
        String extractedText;
        String pagesJson = null;

        if (hasText) {
            kind = "TEXT";
            extractedText = RecallTextExtractor.normalizeText(request.content()).trim();
            characterCount = extractedText.length();
            if (characterCount > RecallTextExtractor.MAX_TEXT_LENGTH) {
                throw RecallException.sourceTooLong("Nội dung văn bản vượt quá 30.000 ký tự ("
                        + characterCount + " ký tự). Vui lòng chọn đoạn ngắn hơn.");
            }
        } else {
            kind = "PDF";
            fileName = request.fileName() != null && !request.fileName().isBlank()
                    ? request.fileName().trim()
                    : "document.pdf";
            if (fileName.length() > FILE_NAME_MAX) {
                throw RecallException.validation("fileName", "too_long", "Tên tệp tối đa " + FILE_NAME_MAX + " ký tự.");
            }
            if (request.base64().length() > 4 * ((RecallTextExtractor.MAX_PDF_BYTES + 2) / 3)) {
                throw RecallException.sourceTooLong("PDF exceeds 5 MiB.");
            }
            byte[] pdfBytes;
            try {
                pdfBytes = Base64.getDecoder().decode(request.base64().trim());
            } catch (IllegalArgumentException e) {
                throw RecallException.validation("base64", "invalid_base64", "Dữ liệu tệp base64 không hợp lệ.");
            }

            RecallTextExtractor.ExtractedContent extracted = RecallTextExtractor.extractFromPdf(pdfBytes, fileName);
            extractedText = extracted.fullText();
            pageCount = extracted.pageCount();
            characterCount = extracted.characterCount();
            try {
                pagesJson = objectMapper.writeValueAsString(extracted.pages());
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new IllegalStateException("Cannot store the pages of the PDF", e);
            }
        }

        repository.saveMaterial(
                materialId, userId, request.goalId(), title, kind, fileName,
                pageCount, characterCount, extractedText, pagesJson
        );

        return new RecallDTOs.MaterialResponse(
                materialId, request.goalId(), title, fileName, kind, pageCount, characterCount, Instant.now()
        );
    }

    public RecallDTOs.PagedResponse<RecallDTOs.MaterialResponse> listMaterials(
            UUID userId, UUID goalId, int page, int limit
    ) {
        if (page < 1 || limit < 1 || limit > 100 || (long)(page - 1) * limit > Integer.MAX_VALUE) {
            throw RecallException.validation("page", "out_of_range", "Invalid page or limit.");
        }

        if (goalId != null && !repository.isGoalOwnedByUser(goalId, userId)) {
            throw RecallException.notFound("Mục tiêu không tồn tại hoặc không thuộc về bạn.");
        }

        int offset = (page - 1) * limit;
        List<RecallDTOs.MaterialResponse> items = repository.findMaterials(userId, goalId, offset, limit);
        long totalItems = repository.countMaterials(userId, goalId);
        int totalPages = (int) Math.ceil((double) totalItems / limit);

        return new RecallDTOs.PagedResponse<>(items, new RecallDTOs.PageMeta(page, limit, totalItems, totalPages));
    }

    // ==========================================
    // Decks
    // ==========================================

    public RecallDTOs.DeckResponse generateDeck(
            UUID userId, UUID requestKey, RecallDTOs.CreateDeckRequest request
    ) {
        if (requestKey == null) {
            throw RecallException.validation("Idempotency-Key", "required", "Header Idempotency-Key là bắt buộc.");
        }
        if (request == null) {
            throw RecallException.validation("body", "missing_body", "Dữ liệu yêu cầu không được để trống.");
        }
        if (request.materialId() == null) {
            throw RecallException.validation("materialId", "required", "Mã tài liệu (materialId) là bắt buộc.");
        }

        String title = request.title() != null ? request.title().trim() : "";
        if (title.isEmpty() || title.length() > 120) {
            throw RecallException.validation("title", "length_range", "Tiêu đề bộ thẻ phải từ 1 đến 120 ký tự.");
        }

        int count = request.count() != null ? request.count() : 10;
        if (count < 5 || count > 20) {
            throw RecallException.validation("count", "range", "Số lượng thẻ yêu cầu phải từ 5 đến 20 thẻ.");
        }

        String payloadString = request.materialId() + ":" + title + ":" + count;
        String payloadHash = hashString(payloadString);

        // Check idempotency receipt
        var existingReceipt = repository.findReceipt(userId, requestKey, "GENERATE_DECK");
        if (existingReceipt.isPresent()) {
            return replay(existingReceipt.get(), payloadHash);
        }

        // One generation per key at a time: a copy of the request that arrives while Gemini is still working is told
        // to wait instead of starting a second deck. The receipt's unique key below is the guard across servers.
        String flightKey = userId + ":" + requestKey;
        if (!generationsInFlight.add(flightKey)) {
            throw RecallException.generationInProgress();
        }
        try {
            var settled = repository.findReceipt(userId, requestKey, "GENERATE_DECK");
            if (settled.isPresent()) {
                return replay(settled.get(), payloadHash);
            }

            // Validate material ownership
            var materialOpt = repository.findMaterialByIdAndUserId(request.materialId(), userId);
            if (materialOpt.isEmpty()) {
                throw RecallException.notFound("Tài liệu không tồn tại hoặc không thuộc về bạn.");
            }
            var material = materialOpt.get();

            String extractedText = material.extractedText() != null ? material.extractedText().trim() : "";
            if (extractedText.isEmpty()) {
                throw RecallException.insufficientMaterial("Tài liệu chưa đủ nội dung để tạo câu hỏi. Vui lòng bổ sung thêm thông tin.");
            }

            Set<String> contentTokens = RecallQualityValidator.extractContentTokens(extractedText);
            if (extractedText.length() < 60 || contentTokens.size() < 8) {
                throw RecallException.insufficientMaterial("Tài liệu chưa đủ nội dung để tạo câu hỏi. Vui lòng bổ sung thêm thông tin.");
            }

            List<RecallTextExtractor.PageText> pages = Collections.emptyList();
            if (material.pagesJson() != null && !material.pagesJson().isBlank()) {
                try {
                    pages = objectMapper.readValue(material.pagesJson(),
                            new TypeReference<List<RecallTextExtractor.PageText>>() {});
                } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                    throw new IllegalStateException("Cannot read the stored pages of the PDF", e);
                }
            }

            // REMOTE CALL GEMINI OUTSIDE OF DB TRANSACTION (AC RBE-06)
            List<GeminiRecallService.GeneratedCard> generated = geminiRecallService.generateCards(
                    material.extractedText(), pages, title, count
            );

            if (generated.isEmpty()) {
                throw RecallException.aiInvalidResponse("AI không tạo được thẻ hợp lệ nào từ tài liệu.");
            }

            // Receipt, deck and cards commit together or not at all.
            try {
                return tx.execute(status -> persistGeneratedDeck(userId, requestKey, payloadHash, material, title, generated));
            } catch (DuplicateKeyException lostRace) {
                var winner = repository.findReceipt(userId, requestKey, "GENERATE_DECK")
                        .orElseThrow(RecallException::generationInProgress);
                return replay(winner, payloadHash);
            }
        } finally {
            generationsInFlight.remove(flightKey);
        }
    }

    private RecallDTOs.DeckResponse replay(RecallRepository.ReceiptRecord receipt, String payloadHash) {
        if (!receipt.payloadHash().equals(payloadHash)) {
            throw RecallException.idempotencyConflict("Idempotency-Key đã được sử dụng cho một yêu cầu khác.");
        }
        try {
            return objectMapper.readValue(receipt.responseBody(), RecallDTOs.DeckResponse.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw RecallException.aiInvalidResponse("Không thể đọc kết quả lưu trữ trước đó.");
        }
    }

    /** Runs inside the transaction opened by {@link #generateDeck}. */
    private RecallDTOs.DeckResponse persistGeneratedDeck(
            UUID userId,
            UUID requestKey,
            String payloadHash,
            RecallRepository.MaterialEntity material,
            String title,
            List<GeminiRecallService.GeneratedCard> generated
    ) {
        repository.lockOwner(userId);
        var receipt = repository.findReceipt(userId, requestKey, "GENERATE_DECK");
        if (receipt.isPresent()) {
            if (!receipt.get().payloadHash().equals(payloadHash)) {
                throw RecallException.idempotencyConflict("Idempotency key already used.");
            }
            try { return objectMapper.readValue(receipt.get().responseBody(), RecallDTOs.DeckResponse.class); }
            catch (java.io.IOException e) { throw new IllegalStateException("Invalid stored receipt", e); }
        }
        // Re-check material & goal still exist
        if (!repository.isGoalOwnedByUser(material.goalId(), userId)) {
            throw RecallException.notFound("Mục tiêu đã bị xóa trước khi hoàn tất tạo bộ thẻ.");
        }

        UUID deckId = UUID.randomUUID();
        int cardCount = generated.size();
        RecallDTOs.DeckResponse deckResponse = new RecallDTOs.DeckResponse(
                deckId, material.goalId(), material.id(), title, "DRAFT", cardCount, 0, Instant.now()
        );

        // The receipt goes first: when two requests race on one key the loser fails here, before any deck is written.
        try {
            repository.saveReceipt(userId, requestKey, "GENERATE_DECK", payloadHash, objectMapper.writeValueAsString(deckResponse));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Cannot store the receipt of the generated deck", e);
        }
        repository.saveDeck(deckId, userId, material.goalId(), material.id(), title, "DRAFT", cardCount, 0);

        List<RecallRepository.CardEntity> cardEntities = new ArrayList<>();
        int order = 0;
        for (GeminiRecallService.GeneratedCard gc : generated) {
            cardEntities.add(new RecallRepository.CardEntity(
                    UUID.randomUUID(), deckId, gc.question(), gc.answer(), material.id(),
                    gc.page(), gc.quote(), false, 0, order++, null, null, 0, gc.qualityWarning(), Instant.now()
            ));
        }
        repository.saveCards(cardEntities);
        return deckResponse;
    }

    public RecallDTOs.MaterialDetail getMaterialDetail(UUID userId, UUID materialId) {
        var material = repository.findMaterialByIdAndUserId(materialId, userId)
                .orElseThrow(() -> RecallException.notFound("Tài liệu không tồn tại hoặc không thuộc về bạn."));
        String text = material.extractedText() == null ? "" : material.extractedText();
        boolean truncated = text.length() > PREVIEW_CHARS;
        String preview = truncated ? text.substring(0, PREVIEW_CHARS) : text;
        return new RecallDTOs.MaterialDetail(material.id(), material.goalId(), material.title(), material.fileName(),
                material.kind(), material.pageCount(), material.characterCount(), material.createdAt(), preview, truncated);
    }

    public RecallDTOs.PagedResponse<RecallDTOs.DeckResponse> listDecks(
            UUID userId, UUID goalId, int page, int limit
    ) {
        if (page < 1 || limit < 1 || limit > 100 || (long)(page - 1) * limit > Integer.MAX_VALUE) {
            throw RecallException.validation("page", "out_of_range", "Invalid page or limit.");
        }

        if (goalId != null && !repository.isGoalOwnedByUser(goalId, userId)) {
            throw RecallException.notFound("Mục tiêu không tồn tại hoặc không thuộc về bạn.");
        }

        int offset = (page - 1) * limit;
        List<RecallDTOs.DeckResponse> items = repository.findDecks(userId, goalId, offset, limit);
        long totalItems = repository.countDecks(userId, goalId);
        int totalPages = (int) Math.ceil((double) totalItems / limit);

        return new RecallDTOs.PagedResponse<>(items, new RecallDTOs.PageMeta(page, limit, totalItems, totalPages));
    }

    public RecallDTOs.DeckDetail getDeckDetail(UUID deckId, UUID userId) {
        var deckOpt = repository.findDeckByIdAndUserId(deckId, userId);
        if (deckOpt.isEmpty()) {
            throw RecallException.notFound("Bộ thẻ không tồn tại hoặc không thuộc về bạn.");
        }
        var deck = deckOpt.get();
        List<RecallDTOs.CardResponse> cards = repository.findCardsByDeckId(deckId);

        RecallDTOs.DeckResponse deckResponse = new RecallDTOs.DeckResponse(
                deck.id(), deck.goalId(), deck.materialId(), deck.title(),
                deck.status(), deck.cardCount(), deck.version(), deck.createdAt()
        );

        return new RecallDTOs.DeckDetail(deckResponse, cards);
    }

    @Transactional
    public RecallDTOs.CardResponse updateCard(UUID cardId, UUID userId, RecallDTOs.UpdateCardRequest request) {
        repository.lockOwner(userId);
        if (request == null) {
            throw RecallException.validation("body", "missing_body", "Dữ liệu yêu cầu không được để trống.");
        }
        if (request.expectedVersion() == null) {
            throw RecallException.validation("expectedVersion", "required", "expectedVersion là bắt buộc.");
        }

        var cardOpt = repository.findCardByIdAndUserId(cardId, userId);
        if (cardOpt.isEmpty()) {
            throw RecallException.notFound("Thẻ học không tồn tại hoặc không thuộc về bạn.");
        }
        var card = cardOpt.get();

        var deckOpt = repository.findDeckByIdAndUserId(card.deckId(), userId);
        if (deckOpt.isEmpty()) {
            throw RecallException.notFound("Bộ thẻ không tồn tại hoặc không thuộc về bạn.");
        }
        var deck = deckOpt.get();

        if ("ACTIVE".equalsIgnoreCase(deck.status())) {
            throw RecallException.deckStateConflict("Không thể sửa thẻ trong bộ thẻ đã kích hoạt (ACTIVE). Chỉ có thể sửa thẻ ở bản nháp (DRAFT).");
        }

        if (card.version() != request.expectedVersion()) {
            throw RecallException.cardVersionConflict(card.version());
        }

        String question = request.question() != null ? request.question().trim() : card.question();
        String answer = request.answer() != null ? request.answer().trim() : card.answer();

        if (question.isEmpty() || question.length() > 300) {
            throw RecallException.validation("question", "length_range", "Câu hỏi phải từ 1 đến 300 ký tự.");
        }
        if (answer.isEmpty() || answer.length() > 2000) {
            throw RecallException.validation("answer", "length_range", "Câu trả lời phải từ 1 đến 2000 ký tự.");
        }

        String materialText = "";
        UUID matId = card.sourceMaterialId() != null ? card.sourceMaterialId() : deck.materialId();
        if (matId != null) {
            var matOpt = repository.findMaterialByIdAndUserId(matId, userId);
            if (matOpt.isPresent() && matOpt.get().extractedText() != null) {
                materialText = matOpt.get().extractedText();
            }
        }

        List<RecallDTOs.CardResponse> otherCards = Collections.emptyList();
        try {
            List<RecallDTOs.CardResponse> allCards = repository.findCardsByDeckId(deck.id());
            if (allCards != null) {
                otherCards = allCards.stream()
                        .filter(c -> !c.id().equals(cardId))
                        .toList();
            }
        } catch (Exception ignored) {}

        var qualityResult = RecallQualityValidator.validateCardQuality(
                question, answer, card.sourceQuote(), materialText, otherCards
        );
        String qualityWarning = qualityResult.passed() ? null : qualityResult.warning();

        boolean updated = repository.updateCardQuestionAnswer(cardId, request.expectedVersion(), question, answer, qualityWarning);
        if (!updated) {
            throw RecallException.cardVersionConflict(card.version());
        }

        // Updating a card also increments the parent deck version
        repository.incrementDeckVersion(deck.id(), userId);

        // Re-evaluate other cards that had duplicate warnings to clear them if no longer duplicating
        if (!otherCards.isEmpty()) {
            final String finalMaterialText = materialText;
            var updatedCurrentCard = new RecallDTOs.CardResponse(
                    card.id(), card.deckId(), question, answer,
                    new RecallDTOs.CardSource(card.sourceMaterialId(), card.sourcePage(), card.sourceQuote()),
                    true, card.version() + 1, card.lastRating(), card.lastReviewedAt(), card.reviewCount(),
                    qualityWarning
            );
            List<RecallDTOs.CardResponse> currentDeckSnapshot = new ArrayList<>(otherCards);
            currentDeckSnapshot.add(updatedCurrentCard);

            for (RecallDTOs.CardResponse other : otherCards) {
                if (other.qualityWarning() != null && other.qualityWarning().contains("trùng lặp")) {
                    List<RecallDTOs.CardResponse> remainingForOther = currentDeckSnapshot.stream()
                            .filter(c -> !c.id().equals(other.id()))
                            .toList();
                    String otherQuote = other.source() != null ? other.source().quote() : null;
                    var otherResult = RecallQualityValidator.validateCardQuality(
                            other.question(), other.answer(), otherQuote, finalMaterialText, remainingForOther
                    );
                    String newOtherWarning = otherResult.passed() ? null : otherResult.warning();
                    if (!Objects.equals(newOtherWarning, other.qualityWarning())) {
                        repository.updateCardQualityWarning(other.id(), newOtherWarning);
                    }
                }
            }
        }

        return new RecallDTOs.CardResponse(
                card.id(), card.deckId(), question, answer,
                new RecallDTOs.CardSource(card.sourceMaterialId(), card.sourcePage(), card.sourceQuote()),
                true, card.version() + 1, card.lastRating(), card.lastReviewedAt(), card.reviewCount(),
                qualityWarning
        );
    }

    @Transactional
    public RecallDTOs.DeckResponse activateDeck(UUID deckId, UUID userId, RecallDTOs.ActivateDeckRequest request) {
        repository.lockOwner(userId);
        if (request == null || request.expectedVersion() == null) {
            throw RecallException.validation("expectedVersion", "required", "expectedVersion là bắt buộc.");
        }

        var deckOpt = repository.findDeckByIdAndUserId(deckId, userId);
        if (deckOpt.isEmpty()) {
            throw RecallException.notFound("Bộ thẻ không tồn tại hoặc không thuộc về bạn.");
        }
        var deck = deckOpt.get();

        if ("ACTIVE".equalsIgnoreCase(deck.status())) {
            if (deck.version() == request.expectedVersion()) {
                return new RecallDTOs.DeckResponse(
                        deck.id(), deck.goalId(), deck.materialId(), deck.title(),
                        deck.status(), deck.cardCount(), deck.version(), deck.createdAt()
                );
            }
            throw RecallException.deckVersionConflict(deck.version());
        }

        if (deck.version() != request.expectedVersion()) {
            throw RecallException.deckVersionConflict(deck.version());
        }

        if (repository.countCardsInDeck(deckId) == 0) {
            throw RecallException.deckStateConflict("Không thể kích hoạt bộ thẻ rỗng.");
        }

        // Quality check before deck activation:
        // Do not allow activating if any card has unresolved quality issues
        List<RecallDTOs.CardResponse> cards = repository.findCardsByDeckId(deckId);
        long invalidCardsCount = (cards != null)
                ? cards.stream().filter(c -> c.qualityWarning() != null && !c.qualityWarning().isBlank()).count()
                : 0;

        if (invalidCardsCount > 0) {
            throw RecallException.validation(
                    "deck",
                    "quality_issues",
                    "Không thể xác nhận bộ thẻ khi còn " + invalidCardsCount + " flashcard chưa đạt chất lượng. Vui lòng kiểm tra và chỉnh sửa các thẻ có cảnh báo trước khi xác nhận."
            );
        }

        boolean updated = repository.updateDeckStatus(deckId, userId, "ACTIVE", request.expectedVersion());
        if (!updated) {
            throw RecallException.deckVersionConflict(deck.version());
        }

        return new RecallDTOs.DeckResponse(
                deck.id(), deck.goalId(), deck.materialId(), deck.title(),
                "ACTIVE", deck.cardCount(), deck.version() + 1, deck.createdAt()
        );
    }

    @Transactional
    public void deleteDeck(UUID deckId, UUID userId) {
        repository.lockOwner(userId);
        var deckOpt = repository.findDeckByIdAndUserId(deckId, userId);
        if (deckOpt.isEmpty()) {
            throw RecallException.notFound("Bộ thẻ không tồn tại hoặc không thuộc về bạn.");
        }
        repository.deleteDeck(deckId, userId);
    }

    // ==========================================
    // Reviews & Progress
    // ==========================================

    @Transactional
    public RecallDTOs.CardReviewResponse recordReview(
            UUID cardId, UUID userId, UUID requestKey, RecallDTOs.CardReviewRequest request
    ) {
        if (requestKey == null) {
            throw RecallException.validation("Idempotency-Key", "required", "Header Idempotency-Key là bắt buộc.");
        }
        if (request == null || request.rating() == null) {
            throw RecallException.validation("rating", "required", "Đánh giá (rating) là bắt buộc.");
        }

        repository.lockOwner(userId);
        String rating = request.rating().trim().toUpperCase();
        if (!"REMEMBERED".equals(rating) && !"FORGOT".equals(rating)) {
            throw RecallException.validation("rating", "invalid_enum", "rating phải là 'REMEMBERED' hoặc 'FORGOT'.");
        }

        String payloadHash = hashString(cardId + ":" + rating);

        var existingReceipt = repository.findReceipt(userId, requestKey, "REVIEW");
        if (existingReceipt.isPresent()) {
            var receipt = existingReceipt.get();
            if (receipt.payloadHash().equals(payloadHash)) {
                try {
                    return objectMapper.readValue(receipt.responseBody(), RecallDTOs.CardReviewResponse.class);
                } catch (Exception e) {
                    throw RecallException.aiInvalidResponse("Không thể đọc kết quả đánh giá đã lưu.");
                }
            } else {
                throw RecallException.idempotencyConflict("Idempotency-Key đã được sử dụng với đánh giá khác.");
            }
        }

        var cardOpt = repository.findCardByIdAndUserId(cardId, userId);
        if (cardOpt.isEmpty()) {
            throw RecallException.notFound("Thẻ học không tồn tại hoặc không thuộc về bạn.");
        }
        var card = cardOpt.get();

        var deckOpt = repository.findDeckByIdAndUserId(card.deckId(), userId);
        if (deckOpt.isEmpty()) {
            throw RecallException.notFound("Bộ thẻ không tồn tại hoặc không thuộc về bạn.");
        }
        var deck = deckOpt.get();

        if (!"ACTIVE".equalsIgnoreCase(deck.status())) {
            throw RecallException.deckStateConflict("Chỉ có thể ôn tập và đánh giá các thẻ trong bộ thẻ đã kích hoạt (ACTIVE).");
        }

        UUID reviewId = UUID.randomUUID();
        Instant now = Instant.now();

        repository.saveReview(reviewId, userId, cardId, rating, now);
        repository.updateCardReviewState(cardId, rating, now);

        RecallDTOs.DeckProgressResponse progress = repository.calculateDeckProgress(deck.id());

        RecallDTOs.CardReviewResponse reviewResponse = new RecallDTOs.CardReviewResponse(
                reviewId, cardId, rating, now, progress
        );

        try {
            String responseJson = objectMapper.writeValueAsString(reviewResponse);
            repository.saveReceipt(userId, requestKey, "REVIEW", payloadHash, responseJson);
        } catch (Exception e) { throw new IllegalStateException("Cannot save review receipt", e); }

        return reviewResponse;
    }

    public RecallDTOs.DeckProgressResponse getDeckProgress(UUID deckId, UUID userId) {
        var deckOpt = repository.findDeckByIdAndUserId(deckId, userId);
        if (deckOpt.isEmpty()) {
            throw RecallException.notFound("Bộ thẻ không tồn tại hoặc không thuộc về bạn.");
        }
        return repository.calculateDeckProgress(deckId);
    }

    // ==========================================
    // Hash Helper
    // ==========================================

    private static String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not supported", e);
        }
    }
}
