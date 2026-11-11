package com.studyplanner.goals;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyplanner.auth.*;

@WebMvcTest(controllers = PlanDraftController.class)
@Import(SecurityConfig.class)
class PlanDraftControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean PlanDraftService service;
    @MockBean UserDetailsService users;
    @MockBean AuthUserRepository userRepository;
    final UUID user = UUID.randomUUID(), goal = UUID.randomUUID(), draft = UUID.randomUUID();
    String base() { return "/api/v1/goals/" + goal + "/plan-drafts"; }
    MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder request) {
        var p = new AppUserPrincipal(user, "a@example.test", "unused");
        return request.with(authentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()))).with(csrf());
    }
    @Test void requiresSessionAndCsrfAndRejectsClientSuppliedGoalData() throws Exception {
        mvc.perform(post(base()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        var p = new AppUserPrincipal(user, "a@example.test", "unused");
        mvc.perform(post(base()).with(authentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities())))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("CSRF_INVALID"));
        mvc.perform(asUser(post(base())).contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"foreign\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }
    @Test void generateReturnsDraftEnvelopeAndReadRoutesUseServerOwner() throws Exception {
        var value = new PlanDraftService.DraftResponse(draft, goal, 4, mapper.readTree("{\"tasks\":[]}"),
                Instant.parse("2026-11-08T01:00:00Z"), Instant.parse("2026-11-09T01:00:00Z"));
        when(service.generate(user, goal)).thenReturn(value);
        mvc.perform(asUser(post(base())).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.goalVersion").value(4));
        when(service.get(user, goal, draft)).thenReturn(value);
        mvc.perform(asUser(get(base() + "/" + draft))).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(draft.toString()));
        when(service.latest(user, goal)).thenReturn(value);
        mvc.perform(asUser(get(base() + "/latest"))).andExpect(status().isOk());
    }
    @Test void confirmRequiresUuidKeyAndReturns201Then200ForReceiptReplay() throws Exception {
        String path = base() + "/" + draft + "/confirm";
        for (String key : List.of("", "bad", "1-1-1-1-1")) {
            mvc.perform(asUser(post(path)).header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
        }
        var value = new PlanDraftService.ConfirmResponse(draft, goal, 5, 1, List.of(UUID.randomUUID()));
        when(service.confirm(eq(user), eq(goal), eq(draft), any(), any()))
                .thenReturn(new PlanDraftService.Confirmation(value, false), new PlanDraftService.Confirmation(value, true));
        String key = UUID.randomUUID().toString();
        mvc.perform(asUser(post(path)).header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.createdCount").value(1));
        mvc.perform(asUser(post(path)).header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }
    @Test void aiErrorsUseSharedEnvelope() throws Exception {
        when(service.generate(user, goal)).thenThrow(new PlanException(503, "AI_UNAVAILABLE", "Unavailable"));
        mvc.perform(asUser(post(base())).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value("AI_UNAVAILABLE"));
    }
    @Test void scopedGenerationForwardsOnlyConfirmedScopeAndTrustedOwner() throws Exception {
        var value = new PlanDraftService.DraftResponse(draft, goal, 0, mapper.readTree("{\"tasks\":[],\"unscheduled\":[]}"),
                Instant.parse("2026-11-10T01:00:00Z"), Instant.parse("2026-11-11T01:00:00Z"));
        when(service.generateScoped(eq(user), eq(goal), any())).thenReturn(value);
        mvc.perform(asUser(post(base())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"selectedTopics\":[{\"id\":\"collision\",\"title\":\"Collision\"}],\"dailyMinutes\":{}}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.proposal.unscheduled").isArray());
        verify(service).generateScoped(eq(user), eq(goal), argThat(body -> body.path("selectedTopics").get(0).path("id").asText().equals("collision")));
        mvc.perform(asUser(post(base())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"selectedTopics\":[],\"userId\":\"foreign\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
