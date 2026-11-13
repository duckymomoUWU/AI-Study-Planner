package com.studyplanner.recall;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class RecallRepository {

    private final JdbcTemplate jdbcTemplate;

    public RecallRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record MaterialEntity(
            UUID id,
            UUID userId,
            UUID goalId,
            String title,
            String kind,
            String fileName,
            Integer pageCount,
            int characterCount,
            String extractedText,
            String pagesJson,
            Instant createdAt
    ) {}

    public record DeckEntity(
            UUID id,
            UUID userId,
            UUID goalId,
            UUID materialId,
            String title,
            String status,
            int cardCount,
            int version,
            Instant createdAt
    ) {}

    public record CardEntity(
            UUID id,
            UUID deckId,
            String question,
            String answer,
            UUID sourceMaterialId,
            Integer sourcePage,
            String sourceQuote,
            boolean editedByUser,
            int version,
            int sortOrder,
            String lastRating,
            Instant lastReviewedAt,
            int reviewCount,
            Instant createdAt
    ) {}

    public record ReceiptRecord(
            UUID id,
            UUID userId,
            UUID requestKey,
            String actionType,
            String payloadHash,
            String responseBody,
            Instant createdAt
    ) {}

    // ==========================================
    // Goals Ownership Verification
    // ==========================================

    public void lockOwner(UUID userId) {
        jdbcTemplate.queryForObject("SELECT id FROM app_users WHERE id = ? FOR UPDATE", UUID.class, userId);
    }

    public boolean isGoalOwnedByUser(UUID goalId, UUID userId) {
        String sql = "SELECT COUNT(*) FROM study_goals WHERE id = ? AND user_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, goalId, userId);
        return count != null && count > 0;
    }

    // ==========================================
    // Materials
    // ==========================================

    public void saveMaterial(
            UUID id,
            UUID userId,
            UUID goalId,
            String title,
            String kind,
            String fileName,
            Integer pageCount,
            int characterCount,
            String extractedText,
            String pagesJson
    ) {
        String sql = """
                INSERT INTO recall_materials (
                    id, user_id, goal_id, title, kind, file_name,
                    page_count, character_count, extracted_text, pages_json, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, now())
                """;
        jdbcTemplate.update(sql,
                id, userId, goalId, title, kind, fileName,
                pageCount, characterCount, extractedText, pagesJson);
    }

    public Optional<MaterialEntity> findMaterialByIdAndUserId(UUID id, UUID userId) {
        String sql = """
                SELECT id, user_id, goal_id, title, kind, file_name,
                       page_count, character_count, extracted_text, pages_json, created_at
                FROM recall_materials
                WHERE id = ? AND user_id = ?
                """;
        try {
            MaterialEntity entity = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new MaterialEntity(
                    (UUID) rs.getObject("id"),
                    (UUID) rs.getObject("user_id"),
                    (UUID) rs.getObject("goal_id"),
                    rs.getString("title"),
                    rs.getString("kind"),
                    rs.getString("file_name"),
                    rs.getObject("page_count") != null ? rs.getInt("page_count") : null,
                    rs.getInt("character_count"),
                    rs.getString("extracted_text"),
                    rs.getString("pages_json"),
                    rs.getTimestamp("created_at").toInstant()
            ), id, userId);
            return Optional.ofNullable(entity);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<RecallDTOs.MaterialResponse> findMaterials(UUID userId, UUID goalId, int offset, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, goal_id, title, file_name, kind, page_count, character_count, created_at
                FROM recall_materials
                WHERE user_id = ?
                """);
        List<Object> params = new ArrayList<>();
        params.add(userId);

        if (goalId != null) {
            sql.append(" AND goal_id = ?");
            params.add(goalId);
        }

        sql.append(" ORDER BY created_at DESC, id ASC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new RecallDTOs.MaterialResponse(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("goal_id"),
                rs.getString("title"),
                rs.getString("file_name"),
                rs.getString("kind"),
                rs.getObject("page_count") != null ? rs.getInt("page_count") : null,
                rs.getInt("character_count"),
                rs.getTimestamp("created_at").toInstant()
        ), params.toArray());
    }

    public long countMaterials(UUID userId, UUID goalId) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM recall_materials WHERE user_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(userId);

        if (goalId != null) {
            sql.append(" AND goal_id = ?");
            params.add(goalId);
        }

        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count != null ? count : 0L;
    }

    // ==========================================
    // Decks
    // ==========================================

    public void saveDeck(
            UUID id,
            UUID userId,
            UUID goalId,
            UUID materialId,
            String title,
            String status,
            int cardCount,
            int version
    ) {
        String sql = """
                INSERT INTO recall_decks (
                    id, user_id, goal_id, material_id, title, status, card_count, version, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, now())
                """;
        jdbcTemplate.update(sql, id, userId, goalId, materialId, title, status, cardCount, version);
    }

    public Optional<DeckEntity> findDeckByIdAndUserId(UUID id, UUID userId) {
        String sql = """
                SELECT id, user_id, goal_id, material_id, title, status, card_count, version, created_at
                FROM recall_decks
                WHERE id = ? AND user_id = ?
                """;
        try {
            DeckEntity entity = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new DeckEntity(
                    (UUID) rs.getObject("id"),
                    (UUID) rs.getObject("user_id"),
                    (UUID) rs.getObject("goal_id"),
                    (UUID) rs.getObject("material_id"),
                    rs.getString("title"),
                    rs.getString("status"),
                    rs.getInt("card_count"),
                    rs.getInt("version"),
                    rs.getTimestamp("created_at").toInstant()
            ), id, userId);
            return Optional.ofNullable(entity);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<RecallDTOs.DeckResponse> findDecks(UUID userId, UUID goalId, int offset, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, goal_id, material_id, title, status, card_count, version, created_at
                FROM recall_decks
                WHERE user_id = ?
                """);
        List<Object> params = new ArrayList<>();
        params.add(userId);

        if (goalId != null) {
            sql.append(" AND goal_id = ?");
            params.add(goalId);
        }

        sql.append(" ORDER BY created_at DESC, id ASC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new RecallDTOs.DeckResponse(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("goal_id"),
                (UUID) rs.getObject("material_id"),
                rs.getString("title"),
                rs.getString("status"),
                rs.getInt("card_count"),
                rs.getInt("version"),
                rs.getTimestamp("created_at").toInstant()
        ), params.toArray());
    }

    public long countDecks(UUID userId, UUID goalId) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM recall_decks WHERE user_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(userId);

        if (goalId != null) {
            sql.append(" AND goal_id = ?");
            params.add(goalId);
        }

        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count != null ? count : 0L;
    }

    public boolean updateDeckStatus(UUID deckId, UUID userId, String newStatus, int expectedVersion) {
        String sql = """
                UPDATE recall_decks
                SET status = ?, version = version + 1
                WHERE id = ? AND user_id = ? AND version = ?
                """;
        int updated = jdbcTemplate.update(sql, newStatus, deckId, userId, expectedVersion);
        return updated > 0;
    }

    public boolean incrementDeckVersion(UUID deckId, UUID userId) {
        String sql = """
                UPDATE recall_decks
                SET version = version + 1
                WHERE id = ? AND user_id = ?
                """;
        int updated = jdbcTemplate.update(sql, deckId, userId);
        return updated > 0;
    }

    /** The cards actually stored for a deck (the card_count column is only a cached copy). */
    public int countCardsInDeck(UUID deckId) {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM recall_cards WHERE deck_id = ?", Integer.class, deckId);
        return count == null ? 0 : count;
    }

    public boolean deleteDeck(UUID deckId, UUID userId) {
        String sql = "DELETE FROM recall_decks WHERE id = ? AND user_id = ?";
        int deleted = jdbcTemplate.update(sql, deckId, userId);
        return deleted > 0;
    }

    // ==========================================
    // Cards
    // ==========================================

    public void saveCards(List<CardEntity> cards) {
        if (cards == null || cards.isEmpty()) return;
        String sql = """
                INSERT INTO recall_cards (
                    id, deck_id, question, answer, source_material_id, source_page, source_quote,
                    edited_by_user, version, sort_order, last_rating, last_reviewed_at, review_count, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                """;
        List<Object[]> batchParams = new ArrayList<>();
        for (CardEntity c : cards) {
            batchParams.add(new Object[]{
                    c.id(), c.deckId(), c.question(), c.answer(), c.sourceMaterialId(),
                    c.sourcePage(), c.sourceQuote(), c.editedByUser(), c.version(), c.sortOrder(),
                    c.lastRating(),
                    c.lastReviewedAt() != null ? Timestamp.from(c.lastReviewedAt()) : null,
                    c.reviewCount()
            });
        }
        jdbcTemplate.batchUpdate(sql, batchParams);
    }

    public List<RecallDTOs.CardResponse> findCardsByDeckId(UUID deckId) {
        String sql = """
                SELECT id, deck_id, question, answer, source_material_id, source_page, source_quote,
                       edited_by_user, version, last_rating, last_reviewed_at, review_count
                FROM recall_cards
                WHERE deck_id = ?
                ORDER BY sort_order ASC, created_at ASC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new RecallDTOs.CardResponse(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("deck_id"),
                rs.getString("question"),
                rs.getString("answer"),
                new RecallDTOs.CardSource(
                        (UUID) rs.getObject("source_material_id"),
                        rs.getObject("source_page") != null ? rs.getInt("source_page") : null,
                        rs.getString("source_quote")
                ),
                rs.getBoolean("edited_by_user"),
                rs.getInt("version"),
                rs.getString("last_rating"),
                rs.getTimestamp("last_reviewed_at") != null ? rs.getTimestamp("last_reviewed_at").toInstant() : null,
                rs.getInt("review_count")
        ), deckId);
    }

    public Optional<CardEntity> findCardByIdAndUserId(UUID cardId, UUID userId) {
        String sql = """
                SELECT c.id, c.deck_id, c.question, c.answer, c.source_material_id, c.source_page, c.source_quote,
                       c.edited_by_user, c.version, c.sort_order, c.last_rating, c.last_reviewed_at, c.review_count, c.created_at
                FROM recall_cards c
                JOIN recall_decks d ON c.deck_id = d.id
                WHERE c.id = ? AND d.user_id = ?
                """;
        try {
            CardEntity entity = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new CardEntity(
                    (UUID) rs.getObject("id"),
                    (UUID) rs.getObject("deck_id"),
                    rs.getString("question"),
                    rs.getString("answer"),
                    (UUID) rs.getObject("source_material_id"),
                    rs.getObject("source_page") != null ? rs.getInt("source_page") : null,
                    rs.getString("source_quote"),
                    rs.getBoolean("edited_by_user"),
                    rs.getInt("version"),
                    rs.getInt("sort_order"),
                    rs.getString("last_rating"),
                    rs.getTimestamp("last_reviewed_at") != null ? rs.getTimestamp("last_reviewed_at").toInstant() : null,
                    rs.getInt("review_count"),
                    rs.getTimestamp("created_at").toInstant()
            ), cardId, userId);
            return Optional.ofNullable(entity);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public boolean updateCardQuestionAnswer(UUID cardId, int expectedVersion, String question, String answer) {
        String sql = """
                UPDATE recall_cards
                SET question = ?, answer = ?, edited_by_user = true, version = version + 1
                WHERE id = ? AND version = ?
                """;
        int updated = jdbcTemplate.update(sql, question, answer, cardId, expectedVersion);
        return updated > 0;
    }

    public void updateCardReviewState(UUID cardId, String rating, Instant reviewedAt) {
        String sql = """
                UPDATE recall_cards
                SET last_rating = ?, last_reviewed_at = ?, review_count = review_count + 1
                WHERE id = ?
                """;
        jdbcTemplate.update(sql, rating, Timestamp.from(reviewedAt), cardId);
    }

    // ==========================================
    // Reviews
    // ==========================================

    public void saveReview(UUID reviewId, UUID userId, UUID cardId, String rating, Instant reviewedAt) {
        String sql = """
                INSERT INTO recall_reviews (id, user_id, card_id, rating, reviewed_at)
                VALUES (?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(sql, reviewId, userId, cardId, rating, Timestamp.from(reviewedAt));
    }

    public RecallDTOs.DeckProgressResponse calculateDeckProgress(UUID deckId) {
        String sql = """
                SELECT
                    COUNT(*) as total_cards,
                    COUNT(last_rating) as reviewed_cards,
                    COUNT(CASE WHEN last_rating = 'REMEMBERED' THEN 1 END) as remembered_cards,
                    COUNT(CASE WHEN last_rating = 'FORGOT' THEN 1 END) as forgotten_cards
                FROM recall_cards
                WHERE deck_id = ?
                """;
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new RecallDTOs.DeckProgressResponse(
                rs.getInt("total_cards"),
                rs.getInt("reviewed_cards"),
                rs.getInt("remembered_cards"),
                rs.getInt("forgotten_cards")
        ), deckId);
    }

    // ==========================================
    // Receipts (Idempotency)
    // ==========================================

    public Optional<ReceiptRecord> findReceipt(UUID userId, UUID requestKey, String actionType) {
        String sql = """
                SELECT id, user_id, request_key, action_type, payload_hash, response_body, created_at
                FROM recall_receipts
                WHERE user_id = ? AND request_key = ? AND action_type = ?
                """;
        try {
            ReceiptRecord record = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new ReceiptRecord(
                    (UUID) rs.getObject("id"),
                    (UUID) rs.getObject("user_id"),
                    (UUID) rs.getObject("request_key"),
                    rs.getString("action_type"),
                    rs.getString("payload_hash"),
                    rs.getString("response_body"),
                    rs.getTimestamp("created_at").toInstant()
            ), userId, requestKey, actionType);
            return Optional.ofNullable(record);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void saveReceipt(
            UUID userId,
            UUID requestKey,
            String actionType,
            String payloadHash,
            String responseBodyJson
    ) {
        String sql = """
                INSERT INTO recall_receipts (
                    id, user_id, request_key, action_type, payload_hash, response_body, created_at
                ) VALUES (gen_random_uuid(), ?, ?, ?, ?, ?::jsonb, now())
                """;
        jdbcTemplate.update(sql, userId, requestKey, actionType, payloadHash, responseBodyJson);
    }
}
