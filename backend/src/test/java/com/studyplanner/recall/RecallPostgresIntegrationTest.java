package com.studyplanner.recall;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The upload and generation paths over real HTTP, session auth and PostgreSQL. Gemini is replaced by a stub so the
 * test proves what the application does with an answer, not what a provider says. Disposable database only:
 * mvn -f backend/pom.xml test -Dtest=RecallPostgresIntegrationTest -Dgoal.integration=true
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named = "goal.integration", matches = "true")
class RecallPostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper mapper;
    @MockBean
    private GeminiRecallService gemini;

    private static final String SOURCE =
            "Kiểm thử hộp đen đánh giá hành vi của hệ thống. Kiểm thử hộp trắng xem xét cấu trúc bên trong. "
                    + "Độ bao phủ câu lệnh đo tỷ lệ câu lệnh đã chạy. Kiểm thử hồi quy chạy lại sau mỗi thay đổi. "
                    + "Kiểm thử tích hợp kiểm tra sự phối hợp giữa các mô-đun.";

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM app_users");
        List<GeminiRecallService.GeneratedCard> cards = new ArrayList<>();
        String[] quotes = SOURCE.split("(?<=\\.) ");
        for (int i = 0; i < 5; i++) {
            cards.add(new GeminiRecallService.GeneratedCard("Câu hỏi " + (i + 1) + "?", "Đáp án " + (i + 1) + ".", quotes[i].trim(), null));
        }
        when(gemini.generateCards(anyString(), anyList(), anyString(), anyInt())).thenAnswer(invocation -> {
            Thread.sleep(300); // long enough for a second request to arrive while the first is "calling Gemini"
            return cards;
        });
    }

    private MockHttpSession login(String email) throws Exception {
        String creds = "{\"email\":\"" + email + "\",\"password\":\"Password123!\"}";
        mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(creds))
                .andExpect(status().isCreated());
        var result = mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(creds)).andExpect(status().isOk()).andReturn();
        jdbc.update("UPDATE app_users SET study_daily_minutes=1440 WHERE email=?", email);
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String goal(MockHttpSession s) throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        String body = "{\"subject\":\"Kiểm thử\",\"topics\":\"Hộp đen\",\"startDate\":\"" + today + "\",\"examDate\":\""
                + today.plusDays(30) + "\",\"minutesPerDay\":60}";
        String res = mvc.perform(post("/api/v1/goals").session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(res).get("data").get("id").asText();
    }

    private ResultActions postJson(MockHttpSession s, String url, String body, String key) throws Exception {
        var request = post(url).session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(key == null ? request : request.header("Idempotency-Key", key));
    }

    private String textMaterial(MockHttpSession s, String goalId, String content) throws Exception {
        String body = mapper.writeValueAsString(java.util.Map.of("goalId", goalId, "title", "Nguồn", "content", content));
        String res = postJson(s, "/api/v1/recall/materials", body, null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(res).get("data").get("id").asText();
    }

    private String deckBody(String materialId, String title) {
        return "{\"materialId\":\"" + materialId + "\",\"title\":\"" + title + "\",\"count\":5}";
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private static byte[] pdf(boolean encrypted, String... pages) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String text : pages) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(50, 700);
                    content.showText(text);
                    content.endText();
                }
            }
            if (encrypted) {
                StandardProtectionPolicy policy = new StandardProtectionPolicy("owner-secret", "user-secret", new AccessPermission());
                policy.setEncryptionKeyLength(128);
                doc.protect(policy);
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private String pdfBody(String goalId, String fileName, byte[] bytes) throws Exception {
        return mapper.writeValueAsString(java.util.Map.of("goalId", goalId, "title", "Tệp", "fileName", fileName,
                "mimeType", "application/pdf", "base64", Base64.getEncoder().encodeToString(bytes)));
    }

    @Test
    @DisplayName("a failure while saving the cards leaves no deck and no receipt, and the same key works on the next try")
    void generationIsAtomic() throws Exception {
        var s = login("atomic@example.com");
        String materialId = textMaterial(s, goal(s), SOURCE);
        String key = UUID.randomUUID().toString();

        jdbc.execute("CREATE OR REPLACE FUNCTION audit_fail_cards() RETURNS trigger AS $$ BEGIN RAISE EXCEPTION 'simulated card insert failure'; END $$ LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER audit_fail BEFORE INSERT ON recall_cards FOR EACH ROW EXECUTE FUNCTION audit_fail_cards()");
        try {
            postJson(s, "/api/v1/recall/decks", deckBody(materialId, "Bộ thẻ"), key).andExpect(status().isInternalServerError());
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS audit_fail ON recall_cards");
            jdbc.execute("DROP FUNCTION IF EXISTS audit_fail_cards()");
        }
        assertEquals(0, count("SELECT count(*) FROM recall_decks"), "no half-saved deck may remain");
        assertEquals(0, count("SELECT count(*) FROM recall_cards"));
        assertEquals(0, count("SELECT count(*) FROM recall_receipts"), "the receipt rolls back too, so the key is still free");

        postJson(s, "/api/v1/recall/decks", deckBody(materialId, "Bộ thẻ"), key).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.cardCount").value(5));
        assertEquals(1, count("SELECT count(*) FROM recall_decks"));
        assertEquals(5, count("SELECT count(*) FROM recall_cards"));
        assertEquals(1, count("SELECT count(*) FROM recall_receipts"));
    }

    @Test
    @DisplayName("two simultaneous requests with the same Idempotency-Key make exactly one deck")
    void sameKeyConcurrentRequestsMakeOneDeck() throws Exception {
        var s = login("race@example.com");
        String materialId = textMaterial(s, goal(s), SOURCE);
        String key = UUID.randomUUID().toString();

        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<String> ids = new ArrayList<>();
        int conflicts = 0;
        try {
            List<Callable<String[]>> calls = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                calls.add(() -> {
                    var response = postJson(s, "/api/v1/recall/decks", deckBody(materialId, "Bộ thẻ"), key).andReturn().getResponse();
                    return new String[]{String.valueOf(response.getStatus()), response.getContentAsString()};
                });
            }
            for (Future<String[]> f : pool.invokeAll(calls)) {
                String[] result = f.get(30, TimeUnit.SECONDS);
                JsonNode body = mapper.readTree(result[1]);
                if (result[0].equals("201")) {
                    ids.add(body.at("/data/id").asText());
                } else {
                    assertEquals("409", result[0]);
                    assertEquals("GENERATION_IN_PROGRESS", body.at("/error/code").asText());
                    conflicts++;
                }
            }
        } finally {
            pool.shutdownNow();
        }
        assertTrue(ids.size() >= 1 && conflicts + ids.size() == 4);
        assertEquals(1, ids.stream().distinct().count(), "every success is the same deck");
        assertEquals(1, count("SELECT count(*) FROM recall_decks"));
        assertEquals(1, count("SELECT count(*) FROM recall_receipts"));

        // a later retry replays the stored result
        postJson(s, "/api/v1/recall/decks", deckBody(materialId, "Bộ thẻ"), key).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(ids.get(0)));
        assertEquals(1, count("SELECT count(*) FROM recall_decks"));
    }

    @Test
    @DisplayName("a deck with no stored cards cannot be activated, whatever its cached card_count says")
    void emptyDeckCannotBeActivated() throws Exception {
        var s = login("empty@example.com");
        String goalId = goal(s);
        String materialId = textMaterial(s, goalId, SOURCE);
        UUID deckId = UUID.randomUUID();
        jdbc.update("INSERT INTO recall_decks (id, user_id, goal_id, material_id, title, status, card_count, version) "
                        + "SELECT ?, user_id, goal_id, id, 'Rỗng', 'DRAFT', 5, 0 FROM recall_materials WHERE id = ?::uuid",
                deckId, materialId);

        postJson(s, "/api/v1/recall/decks/" + deckId + "/activate", "{\"expectedVersion\":0}", null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("DECK_STATE_CONFLICT"));
        assertEquals("DRAFT", jdbc.queryForObject("SELECT status FROM recall_decks WHERE id = ?", String.class, deckId));
    }

    @Test
    @DisplayName("an AI failure leaves no deck and no receipt and is reported as 503, not turned into made-up cards")
    void aiFailureStoresNothing() throws Exception {
        var s = login("ai@example.com");
        String materialId = textMaterial(s, goal(s), SOURCE);
        when(gemini.generateCards(anyString(), anyList(), anyString(), anyInt()))
                .thenThrow(RecallException.aiUnavailable("Dịch vụ AI tạm thời không dùng được."));

        postJson(s, "/api/v1/recall/decks", deckBody(materialId, "Bộ thẻ"), UUID.randomUUID().toString())
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value("AI_UNAVAILABLE"));
        assertEquals(0, count("SELECT count(*) FROM recall_decks"));
        assertEquals(0, count("SELECT count(*) FROM recall_receipts"));
        assertEquals(1, count("SELECT count(*) FROM recall_materials"), "the source is kept");
    }

    @Test
    @DisplayName("pasted text is stored in NFC with LF, and the preview shows what was stored")
    void textIsStoredNormalizedAndPreviewable() throws Exception {
        var s = login("nfc@example.com");
        String goalId = goal(s);
        String decomposed = java.text.Normalizer.normalize("Kiểm thử hộp đen", java.text.Normalizer.Form.NFD) + "\r\nDòng hai";
        String materialId = textMaterial(s, goalId, decomposed);

        String stored = jdbc.queryForObject("SELECT extracted_text FROM recall_materials WHERE id = ?::uuid", String.class, materialId);
        assertEquals("Kiểm thử hộp đen\nDòng hai", stored);
        mvc.perform(get("/api/v1/recall/materials/" + materialId).session(s)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.preview").value("Kiểm thử hộp đen\nDòng hai"))
                .andExpect(jsonPath("$.data.previewTruncated").value(false))
                .andExpect(jsonPath("$.data.kind").value("TEXT"));

        var other = login("other@example.com");
        mvc.perform(get("/api/v1/recall/materials/" + materialId).session(other)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a PDF is read page by page with LF line breaks; bad files get clear Vietnamese errors without parser text")
    void pdfUploadRules() throws Exception {
        var s = login("pdf@example.com");
        String goalId = goal(s);

        String res = postJson(s, "/api/v1/recall/materials", pdfBody(goalId, "hai-trang.pdf",
                pdf(false, "Trang mot noi ve kiem thu hop den.", "Trang hai noi ve kiem thu hop trang.")), null)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.kind").value("PDF"))
                .andExpect(jsonPath("$.data.pageCount").value(2)).andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(res).at("/data/id").asText();
        String pages = jdbc.queryForObject("SELECT pages_json::text FROM recall_materials WHERE id = ?::uuid", String.class, id);
        assertEquals(2, mapper.readTree(pages).size());
        assertEquals(2, mapper.readTree(pages).get(1).get("page").asInt());
        assertFalse(jdbc.queryForObject("SELECT extracted_text FROM recall_materials WHERE id = ?::uuid", String.class, id).contains("\r"));

        postJson(s, "/api/v1/recall/materials", pdfBody(goalId, "khoa.pdf", pdf(true, "Noi dung bi khoa.")), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].issue").value("password_protected"))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("mật khẩu")));
        String fake = mapper.writeValueAsString(java.util.Map.of("goalId", goalId, "title", "Giả", "fileName", "gia.pdf",
                "base64", Base64.getEncoder().encodeToString("không phải PDF".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        postJson(s, "/api/v1/recall/materials", fake, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].issue").value("corrupted_pdf"))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("offset"))));
        postJson(s, "/api/v1/recall/materials", pdfBody(goalId, "n".repeat(300) + ".pdf", pdf(false, "Ten tep qua dai.")), null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details[0].field").value("fileName"))
                .andExpect(jsonPath("$.error.details[0].issue").value("too_long"));
        assertEquals(1, count("SELECT count(*) FROM recall_materials"), "only the good PDF was stored");
    }

    @Test
    @DisplayName("the old /api/recall routes without the v1 prefix are gone")
    void legacyRoutesAreGone() throws Exception {
        var s = login("legacy@example.com");
        mvc.perform(get("/api/recall/materials").session(s)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/recall/materials").session(s)).andExpect(status().isOk());
    }
}
