package com.studyplanner.recall;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.studyplanner.auth.AppUserPrincipal;

@RestController
public class RecallDeckController {

    private final RecallService recallService;

    public RecallDeckController(RecallService recallService) {
        this.recallService = recallService;
    }

    @PostMapping("/api/v1/recall/decks")
    public ResponseEntity<RecallDTOs.SingleDataResponse<RecallDTOs.DeckResponse>> createDeck(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String rawKey,
            @RequestBody RecallDTOs.CreateDeckRequest request
    ) {
        UUID requestKey = parseUuidKey(rawKey);
        RecallDTOs.DeckResponse response = recallService.generateDeck(principal.id(), requestKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new RecallDTOs.SingleDataResponse<>(response));
    }

    @GetMapping("/api/v1/recall/decks")
    public ResponseEntity<RecallDTOs.PagedResponse<RecallDTOs.DeckResponse>> listDecks(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(required = false) UUID goalId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        RecallDTOs.PagedResponse<RecallDTOs.DeckResponse> response = recallService.listDecks(
                principal.id(), goalId, page, limit
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/v1/recall/decks/{deckId}")
    public ResponseEntity<RecallDTOs.SingleDataResponse<RecallDTOs.DeckDetail>> getDeck(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID deckId
    ) {
        RecallDTOs.DeckDetail detail = recallService.getDeckDetail(deckId, principal.id());
        return ResponseEntity.ok(new RecallDTOs.SingleDataResponse<>(detail));
    }

    @PatchMapping("/api/v1/recall/cards/{cardId}")
    public ResponseEntity<RecallDTOs.SingleDataResponse<RecallDTOs.CardResponse>> updateCard(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID cardId,
            @RequestBody RecallDTOs.UpdateCardRequest request
    ) {
        RecallDTOs.CardResponse response = recallService.updateCard(cardId, principal.id(), request);
        return ResponseEntity.ok(new RecallDTOs.SingleDataResponse<>(response));
    }

    @PostMapping("/api/v1/recall/decks/{deckId}/activate")
    public ResponseEntity<RecallDTOs.SingleDataResponse<RecallDTOs.DeckResponse>> activateDeck(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID deckId,
            @RequestBody RecallDTOs.ActivateDeckRequest request
    ) {
        RecallDTOs.DeckResponse response = recallService.activateDeck(deckId, principal.id(), request);
        return ResponseEntity.ok(new RecallDTOs.SingleDataResponse<>(response));
    }

    @DeleteMapping("/api/v1/recall/decks/{deckId}")
    public ResponseEntity<Void> deleteDeck(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable UUID deckId
    ) {
        recallService.deleteDeck(deckId, principal.id());
        return ResponseEntity.noContent().build();
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
