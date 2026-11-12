package com.studyplanner.recall;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.studyplanner.auth.AppUserPrincipal;

@RestController
public class RecallReviewController {

    private final RecallService recallService;

    public RecallReviewController(RecallService recallService) {
        this.recallService = recallService;
    }

    @PostMapping({"/api/v1/recall/cards/{cardId}/reviews", "/api/recall/cards/{cardId}/reviews"})
    public ResponseEntity<RecallDTOs.SingleDataResponse<RecallDTOs.CardReviewResponse>> recordReview(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID cardId,
            @RequestHeader(value = "Idempotency-Key", required = false) String rawKey,
            @RequestBody RecallDTOs.CardReviewRequest request
    ) {
        UUID requestKey = parseUuidKey(rawKey);
        RecallDTOs.CardReviewResponse response = recallService.recordReview(cardId, principal.id(), requestKey, request);
        return ResponseEntity.ok(new RecallDTOs.SingleDataResponse<>(response));
    }

    @GetMapping({"/api/v1/recall/decks/{deckId}/progress", "/api/recall/decks/{deckId}/progress"})
    public ResponseEntity<RecallDTOs.SingleDataResponse<RecallDTOs.DeckProgressResponse>> getProgress(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID deckId
    ) {
        RecallDTOs.DeckProgressResponse response = recallService.getDeckProgress(deckId, principal.id());
        return ResponseEntity.ok(new RecallDTOs.SingleDataResponse<>(response));
    }

    private static UUID parseUuidKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw RecallException.validation("Idempotency-Key", "required", "Header Idempotency-Key là bắt buộc.");
        }
        try {
            return UUID.fromString(rawKey.trim());
        } catch (IllegalArgumentException e) {
            throw RecallException.validation("Idempotency-Key", "invalid_uuid", "Idempotency-Key phải là UUID hợp lệ.");
        }
    }
}
