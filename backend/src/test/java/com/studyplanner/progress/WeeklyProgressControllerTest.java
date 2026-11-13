package com.studyplanner.progress;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.studyplanner.auth.AppUserPrincipal;
import com.studyplanner.auth.AuthUserRepository;
import com.studyplanner.auth.SecurityConfig;

/** Contract tests for GET /api/v1/progress/weekly: statuses, envelope, error codes. */
@WebMvcTest(controllers = WeeklyProgressController.class)
@Import(SecurityConfig.class)
class WeeklyProgressControllerTest {
    @Test
    void syllabusParsingStillRequiresLogin() throws Exception {
        mvc.perform(post("/api/v1/goals/parse-syllabus").with(csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String URL = "/api/v1/progress/weekly";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private WeeklyProgressService service;
    @MockBean
    private UserDetailsService userDetailsService;
    @MockBean
    private AuthUserRepository authUserRepository;

    private static MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder b) {
        var principal = new AppUserPrincipal(USER, "a@example.com", "hash");
        return b.with(authentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities())));
    }

    private static WeeklyProgressService.WeeklyProgress week(long total, long completed) {
        LocalDate start = LocalDate.of(2026, 11, 2);
        List<WeeklyProgressService.Day> days = new java.util.ArrayList<>();
        for (int i = 0; i < 7; i++) {
            days.add(new WeeklyProgressService.Day(start.plusDays(i), i == 0 ? total : 0, i == 0 ? completed : 0));
        }
        return new WeeklyProgressService.WeeklyProgress(start, start.plusDays(6), total, completed, total - completed,
                total == 0 ? 0.0 : (double) completed / total, days);
    }

    @Test
    void returnsTheWeekInTheDataEnvelopeUsingThePrincipalAsOwner() throws Exception {
        when(service.getWeek(USER, LocalDate.of(2026, 11, 4))).thenReturn(week(4, 1));
        mvc.perform(asUser(get(URL + "?date=2026-11-04")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weekStart").value("2026-11-02"))
                .andExpect(jsonPath("$.data.weekEnd").value("2026-11-08"))
                .andExpect(jsonPath("$.data.totalTasks").value(4))
                .andExpect(jsonPath("$.data.completedTasks").value(1))
                .andExpect(jsonPath("$.data.remainingTasks").value(3))
                .andExpect(jsonPath("$.data.completionRate").value(0.25))
                .andExpect(jsonPath("$.data.days.length()").value(7))
                .andExpect(jsonPath("$.data.days[0].date").value("2026-11-02"))
                .andExpect(jsonPath("$.data.days[0].totalTasks").value(4))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void withoutADateTheServiceChoosesTheCurrentWeek() throws Exception {
        when(service.getWeek(USER, null)).thenReturn(week(0, 0));
        mvc.perform(asUser(get(URL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalTasks").value(0));
        mvc.perform(asUser(get(URL + "?date=")))
                .andExpect(status().isOk());
    }

    @Test
    void aWeekWithoutTasksIsZeroOfZeroWithRateZeroAndSevenDays() throws Exception {
        when(service.getWeek(USER, LocalDate.of(2026, 11, 16))).thenReturn(week(0, 0));
        mvc.perform(asUser(get(URL + "?date=2026-11-16")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalTasks").value(0))
                .andExpect(jsonPath("$.data.completedTasks").value(0))
                .andExpect(jsonPath("$.data.completionRate").value(0.0))
                .andExpect(jsonPath("$.data.days.length()").value(7));
    }

    @Test
    void anInvalidDateIs400WithFieldDetailsAndTheServiceIsNotCalled() throws Exception {
        for (String bad : List.of("10/11/2026", "2026-13-01", "2026-02-30", "abc", "2026-11-4")) {
            mvc.perform(asUser(get(URL + "?date=" + bad)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.details[0].field").value("date"))
                    .andExpect(jsonPath("$.error.details[0].issue").value("invalid_date"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void aDateOutsideTheSupportedRangeIs400OutOfRange() throws Exception {
        when(service.getWeek(any(), any())).thenThrow(new WeeklyProgressService.DateOutOfRangeException());
        mvc.perform(asUser(get(URL + "?date=1999-01-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].issue").value("out_of_range"));
    }

    @Test
    void withoutASessionIs401AndTheServiceIsNotCalled() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        verifyNoInteractions(service);
    }

    @Test
    void onlyReadingIsAllowed() throws Exception {
        mvc.perform(asUser(post(URL)).with(csrf())).andExpect(status().is4xxClientError());
        verifyNoInteractions(service);
    }

    @Test
    void unexpectedErrorsAre500WithoutDetails() throws Exception {
        when(service.getWeek(any(), any())).thenThrow(new IllegalStateException("secret db detail"));
        mvc.perform(asUser(get(URL)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret"))));
    }
}
