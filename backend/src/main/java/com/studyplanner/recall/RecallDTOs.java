package com.studyplanner.recall;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;

public class RecallDTOs {

    public record MaterialRequest(
            UUID goalId,
            String title,
            String content,
            String fileName,
            String mimeType,
            String base64
    ) {}

    public record MaterialResponse(
            UUID id,
            UUID goalId,
            String title,
            String fileName,
            String kind,
            Integer pageCount,
            int characterCount,
            Instant createdAt
    ) {}

    public record CreateDeckRequest(
            UUID materialId,
            String title,
            Integer count
    ) {}

    public record DeckResponse(
            UUID id,
            UUID goalId,
            UUID materialId,
            String title,
            String status,
            int cardCount,
            int version,
            Instant createdAt
    ) {}

    public record CardSource(
            UUID materialId,
            Integer page,
            String quote
    ) {}

    public record CardResponse(
            UUID id,
            UUID deckId,
            String question,
            String answer,
            CardSource source,
            boolean editedByUser,
            int version,
            String lastRating,
            Instant lastReviewedAt,
            int reviewCount,
            String qualityWarning
    ) {
        public CardResponse(UUID id, UUID deckId, String question, String answer,
                CardSource source, boolean editedByUser, int version,
                String lastRating, Instant lastReviewedAt, int reviewCount) {
            this(id, deckId, question, answer, source, editedByUser, version, lastRating, lastReviewedAt, reviewCount, null);
        }
    }

    public record DeckDetail(
            DeckResponse deck,
            List<CardResponse> cards
    ) {}

    public record UpdateCardRequest(
            String question,
            String answer,
            Integer expectedVersion
    ) {}

    public record ActivateDeckRequest(
            Integer expectedVersion
    ) {}

    public record CardReviewRequest(
            String rating
    ) {}

    public record DeckProgressResponse(
            int totalCards,
            int reviewedCards,
            int rememberedCards,
            int forgottenCards
    ) {}

    public record CardReviewResponse(
            UUID reviewId,
            UUID cardId,
            String rating,
            Instant reviewedAt,
            DeckProgressResponse progress
    ) {}

    public record PageMeta(
            int page,
            int limit,
            long totalItems,
            int totalPages
    ) {}

    public record PagedResponse<T>(
            List<T> data,
            PageMeta meta
    ) {}

    public record SingleDataResponse<T>(
            T data
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorResponse(
            ErrorDetail error
    ) {
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record ErrorDetail(
                String code,
                String message,
                List<RecallException.FieldErrorItem> details,
                Map<String, Object> context
        ) {}
    }
}
