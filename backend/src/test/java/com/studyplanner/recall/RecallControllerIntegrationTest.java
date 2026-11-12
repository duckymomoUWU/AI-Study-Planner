package com.studyplanner.recall;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyplanner.auth.AppUserPrincipal;
import com.studyplanner.auth.AuthService;
import com.studyplanner.auth.AuthUserRepository;
import com.studyplanner.auth.SecurityConfig;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {
        RecallMaterialController.class,
        RecallDeckController.class,
        RecallReviewController.class
})
@Import({SecurityConfig.class, RecallExceptionHandler.class})
class RecallControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RecallService recallService;

    @MockBean
    private AuthService authService;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private AuthUserRepository authUserRepository;

    private final UUID sampleUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID sampleGoalId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID sampleMaterialId = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private final UUID sampleDeckId = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private final UUID sampleCardId = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private final UUID sampleKey = UUID.fromString("99999999-9999-9999-9999-999999999999");

    private AppUserPrincipal samplePrincipal() {
        return new AppUserPrincipal(sampleUserId, "student@example.com", "hash");
    }

    // ==========================================
    // Material Endpoints
    // ==========================================

    @Test
    @DisplayName("POST /api/v1/recall/materials succeeds with 201 Created when authenticated and CSRF valid")
    void createMaterialSucceeds() throws Exception {
        var request = new RecallDTOs.MaterialRequest(sampleGoalId, "Chương 1", "Nội dung ôn tập.", null, null, null);
        var response = new RecallDTOs.MaterialResponse(
                sampleMaterialId, sampleGoalId, "Chương 1", null, "TEXT", null, 16, Instant.now()
        );
        when(recallService.createMaterial(eq(sampleUserId), any(RecallDTOs.MaterialRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/recall/materials")
                        .with(user(samplePrincipal()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(sampleMaterialId.toString()))
                .andExpect(jsonPath("$.data.title").value("Chương 1"))
                .andExpect(jsonPath("$.data.kind").value("TEXT"));
    }

    @Test
    @DisplayName("POST /api/v1/recall/materials rejects with 403 Forbidden when CSRF is missing")
    void createMaterialRejectsMissingCsrf() throws Exception {
        var request = new RecallDTOs.MaterialRequest(sampleGoalId, "Chương 1", "Nội dung.", null, null, null);

        mockMvc.perform(post("/api/v1/recall/materials")
                        .with(user(samplePrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CSRF_INVALID"));
    }

    @Test
    @DisplayName("GET /api/v1/recall/materials returns paginated materials envelope")
    void listMaterialsSucceeds() throws Exception {
        var item = new RecallDTOs.MaterialResponse(sampleMaterialId, sampleGoalId, "Chương 1", null, "TEXT", null, 16, Instant.now());
        var paged = new RecallDTOs.PagedResponse<>(List.of(item), new RecallDTOs.PageMeta(1, 20, 1L, 1));
        when(recallService.listMaterials(eq(sampleUserId), eq(sampleGoalId), eq(1), eq(20)))
                .thenReturn(paged);

        mockMvc.perform(get("/api/v1/recall/materials?goalId=" + sampleGoalId + "&page=1&limit=20")
                        .with(user(samplePrincipal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(sampleMaterialId.toString()))
                .andExpect(jsonPath("$.meta.totalItems").value(1));
    }

    // ==========================================
    // Deck Endpoints
    // ==========================================

    @Test
    @DisplayName("POST /api/v1/recall/decks succeeds with 201 Created and Idempotency-Key")
    void createDeckSucceeds() throws Exception {
        var request = new RecallDTOs.CreateDeckRequest(sampleMaterialId, "Bộ thẻ ôn thi", 10);
        var response = new RecallDTOs.DeckResponse(
                sampleDeckId, sampleGoalId, sampleMaterialId, "Bộ thẻ ôn thi", "DRAFT", 10, 0, Instant.now()
        );
        when(recallService.generateDeck(eq(sampleUserId), eq(sampleKey), any(RecallDTOs.CreateDeckRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/recall/decks")
                        .with(user(samplePrincipal()))
                        .with(csrf())
                        .header("Idempotency-Key", sampleKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(sampleDeckId.toString()))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.cardCount").value(10));
    }

    @Test
    @DisplayName("GET /api/v1/recall/decks/{deckId} returns deck detail with cards")
    void getDeckDetailSucceeds() throws Exception {
        var deck = new RecallDTOs.DeckResponse(sampleDeckId, sampleGoalId, sampleMaterialId, "Bộ thẻ", "DRAFT", 1, 0, Instant.now());
        var card = new RecallDTOs.CardResponse(
                sampleCardId, sampleDeckId, "Câu hỏi?", "Đáp án.",
                new RecallDTOs.CardSource(sampleMaterialId, null, "Trích dẫn"),
                false, 0, null, null, 0
        );
        var detail = new RecallDTOs.DeckDetail(deck, List.of(card));
        when(recallService.getDeckDetail(eq(sampleDeckId), eq(sampleUserId)))
                .thenReturn(detail);

        mockMvc.perform(get("/api/v1/recall/decks/" + sampleDeckId)
                        .with(user(samplePrincipal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deck.id").value(sampleDeckId.toString()))
                .andExpect(jsonPath("$.data.cards[0].id").value(sampleCardId.toString()))
                .andExpect(jsonPath("$.data.cards[0].question").value("Câu hỏi?"));
    }

    @Test
    @DisplayName("PATCH /api/v1/recall/cards/{cardId} updates card and returns 200")
    void updateCardSucceeds() throws Exception {
        var request = new RecallDTOs.UpdateCardRequest("Câu hỏi sửa", "Đáp án sửa", 0);
        var response = new RecallDTOs.CardResponse(
                sampleCardId, sampleDeckId, "Câu hỏi sửa", "Đáp án sửa",
                new RecallDTOs.CardSource(sampleMaterialId, null, "Trích dẫn"),
                true, 1, null, null, 0
        );
        when(recallService.updateCard(eq(sampleCardId), eq(sampleUserId), any(RecallDTOs.UpdateCardRequest.class)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/v1/recall/cards/" + sampleCardId)
                        .with(user(samplePrincipal()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(sampleCardId.toString()))
                .andExpect(jsonPath("$.data.editedByUser").value(true))
                .andExpect(jsonPath("$.data.version").value(1));
    }

    @Test
    @DisplayName("POST /api/v1/recall/decks/{deckId}/activate activates deck")
    void activateDeckSucceeds() throws Exception {
        var request = new RecallDTOs.ActivateDeckRequest(0);
        var response = new RecallDTOs.DeckResponse(
                sampleDeckId, sampleGoalId, sampleMaterialId, "Bộ thẻ", "ACTIVE", 5, 1, Instant.now()
        );
        when(recallService.activateDeck(eq(sampleDeckId), eq(sampleUserId), any(RecallDTOs.ActivateDeckRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/recall/decks/" + sampleDeckId + "/activate")
                        .with(user(samplePrincipal()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.version").value(1));
    }

    @Test
    @DisplayName("DELETE /api/v1/recall/decks/{deckId} deletes deck and returns 204")
    void deleteDeckSucceeds() throws Exception {
        doNothing().when(recallService).deleteDeck(sampleDeckId, sampleUserId);

        mockMvc.perform(delete("/api/v1/recall/decks/" + sampleDeckId)
                        .with(user(samplePrincipal()))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(recallService).deleteDeck(sampleDeckId, sampleUserId);
    }

    // ==========================================
    // Review & Progress Endpoints
    // ==========================================

    @Test
    @DisplayName("POST /api/v1/recall/cards/{cardId}/reviews records rating and returns progress")
    void recordReviewSucceeds() throws Exception {
        var request = new RecallDTOs.CardReviewRequest("REMEMBERED");
        var progress = new RecallDTOs.DeckProgressResponse(5, 1, 1, 0);
        var response = new RecallDTOs.CardReviewResponse(UUID.randomUUID(), sampleCardId, "REMEMBERED", Instant.now(), progress);
        when(recallService.recordReview(eq(sampleCardId), eq(sampleUserId), eq(sampleKey), any(RecallDTOs.CardReviewRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/recall/cards/" + sampleCardId + "/reviews")
                        .with(user(samplePrincipal()))
                        .with(csrf())
                        .header("Idempotency-Key", sampleKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cardId").value(sampleCardId.toString()))
                .andExpect(jsonPath("$.data.rating").value("REMEMBERED"))
                .andExpect(jsonPath("$.data.progress.totalCards").value(5))
                .andExpect(jsonPath("$.data.progress.reviewedCards").value(1))
                .andExpect(jsonPath("$.data.progress.rememberedCards").value(1))
                .andExpect(jsonPath("$.data.progress.forgottenCards").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/recall/decks/{deckId}/progress returns deck progress")
    void getProgressSucceeds() throws Exception {
        var progress = new RecallDTOs.DeckProgressResponse(10, 6, 4, 2);
        when(recallService.getDeckProgress(eq(sampleDeckId), eq(sampleUserId)))
                .thenReturn(progress);

        mockMvc.perform(get("/api/v1/recall/decks/" + sampleDeckId + "/progress")
                        .with(user(samplePrincipal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCards").value(10))
                .andExpect(jsonPath("$.data.reviewedCards").value(6))
                .andExpect(jsonPath("$.data.rememberedCards").value(4))
                .andExpect(jsonPath("$.data.forgottenCards").value(2));
    }
}
