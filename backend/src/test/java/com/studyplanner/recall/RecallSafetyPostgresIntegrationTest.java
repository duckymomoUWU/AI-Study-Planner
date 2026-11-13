package com.studyplanner.recall;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.*;
import com.studyplanner.auth.AppUserPrincipal;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties={"spring.datasource.url=${planning.test-db-url:jdbc:postgresql://127.0.0.1:5432/study_planner_test}","spring.datasource.username=study_planner","spring.datasource.password=unused-local"})
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named="planning.integration", matches="true")
class RecallSafetyPostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @MockBean GeminiRecallService ai;
    @SpyBean RecallRepository repository;
    UUID owner, other, goal, material;
    final String quote="Software testing evaluates software behavior against requirements.";

    @BeforeEach void seed() throws Exception {
        try(var c=jdbc.getDataSource().getConnection()) {
            assertTrue(c.getMetaData().getURL().endsWith("study_planner_test"));
        }
        owner=UUID.randomUUID();other=UUID.randomUUID();
        for(var id:List.of(owner,other)) jdbc.update("INSERT INTO app_users(id,email,password_hash) VALUES(?,?,?)",id,id+"@example.test","unused");
        var today=LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        goal=UUID.fromString(data(call(owner,post("/api/v1/goals"),Map.of("subject","Recall integration","topics","Testing","startDate",today.toString(),"examDate",today.plusDays(10).toString(),"minutesPerDay",60)).andExpect(status().isCreated())).path("id").asText());
        material=UUID.fromString(data(call(owner,post("/api/v1/recall/materials"),Map.of("goalId",goal,"title","Testing notes","content",quote)).andExpect(status().isCreated())).path("id").asText());
        var cards=new ArrayList<GeminiRecallService.GeneratedCard>();
        for(int i=0;i<5;i++) cards.add(new GeminiRecallService.GeneratedCard("Question "+i, "Answer "+i,quote,null));
        when(ai.generateCards(anyString(),anyList(),anyString(),anyInt())).thenAnswer(invocation -> {
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            return cards;
        });
    }
    @AfterEach void clean() {
        for(var id:List.of(owner,other)) jdbc.update("DELETE FROM app_users WHERE id=?",id);
    }
    ResultActions call(UUID user, MockHttpServletRequestBuilder req,Object body)throws Exception {
        return mvc.perform(req.with(user(new AppUserPrincipal(user,user+"@example.test","unused"))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body)));
    }
    JsonNode data(ResultActions r)throws Exception {return mapper.readTree(r.andReturn().getResponse().getContentAsString()).path("data");}
    JsonNode generate(UUID key)throws Exception {
        return data(call(owner,post("/api/v1/recall/decks").header("Idempotency-Key",key),Map.of("materialId",material,"title","Testing","count",5)).andExpect(status().isCreated()));
    }
    int count(String table) {return jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE user_id=?",Integer.class,owner);}

    @Test void lifecycleIsPersistentOwnedAndReplaySafe()throws Exception {
        UUID key=UUID.randomUUID();var deck=generate(key);assertEquals(deck,generate(key));
        assertEquals(1,count("recall_decks"));verify(ai,times(1)).generateCards(anyString(),anyList(),anyString(),anyInt());
        String deckPath="/api/v1/recall/decks/"+deck.path("id").asText();
        call(other,get(deckPath),Map.of()).andExpect(status().isNotFound());
        var detail=data(call(owner,get(deckPath),Map.of()).andExpect(status().isOk()));
        var card=detail.path("cards").get(0);String cardPath="/api/v1/recall/cards/"+card.path("id").asText();
        assertEquals(quote,card.at("/source/quote").asText());
        call(owner,post(cardPath+"/reviews").header("Idempotency-Key",UUID.randomUUID()),Map.of("rating","REMEMBERED")).andExpect(status().isConflict());
        call(owner,patch(cardPath),Map.of("question","Edited question","expectedVersion",0)).andExpect(status().isOk());
        call(owner,patch(cardPath),Map.of("question","Stale edit","expectedVersion",0)).andExpect(status().isConflict());
        call(owner,post(deckPath+"/activate"),Map.of("expectedVersion",1)).andExpect(status().isOk());
        var reviewKey=UUID.randomUUID();
        for(int i=0;i<2;i++) call(owner,post(cardPath+"/reviews").header("Idempotency-Key",reviewKey),Map.of("rating","REMEMBERED")).andExpect(status().isOk());
        assertEquals(1,count("recall_reviews"));
        call(owner,get(deckPath+"/progress"),Map.of()).andExpect(jsonPath("$.data.rememberedCards").value(1));
        call(other,post(cardPath+"/reviews").header("Idempotency-Key",UUID.randomUUID()),Map.of("rating","FORGOT")).andExpect(status().isNotFound());
        call(owner,delete("/api/v1/goals/"+goal),Map.of()).andExpect(status().isNoContent());
        assertEquals(0,count("recall_decks"));assertEquals(0,count("recall_reviews"));assertEquals(0,count("recall_materials"));
    }

    @Test void persistenceFailureRollsBackDeckCardsAndReceipt()throws Exception {
        doThrow(new IllegalStateException("simulated card write failure")).when(repository).saveCards(anyList());
        call(owner,post("/api/v1/recall/decks").header("Idempotency-Key",UUID.randomUUID()),Map.of("materialId",material,"title","Testing","count",5)).andExpect(status().isInternalServerError());
        assertEquals(0,count("recall_decks"));assertEquals(0,count("recall_receipts"));
    }

    @Test void concurrentGenerationWithSameKeyCreatesOnlyOneDeck()throws Exception {
        var entered=new CountDownLatch(1);
        var release=new CountDownLatch(1);
        var cards=List.of(new GeminiRecallService.GeneratedCard("Question","Answer",quote,null));
        when(ai.generateCards(anyString(),anyList(),anyString(),anyInt())).thenAnswer(invocation -> {
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            entered.countDown();assertTrue(release.await(10,TimeUnit.SECONDS));return cards;
        });
        UUID key=UUID.randomUUID();var pool=Executors.newSingleThreadExecutor();
        try {
            Future<JsonNode> first=pool.submit(()->generate(key));
            assertTrue(entered.await(10,TimeUnit.SECONDS));
            call(owner,post("/api/v1/recall/decks").header("Idempotency-Key",key),Map.of("materialId",material,"title","Testing","count",5))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("GENERATION_IN_PROGRESS"));
            release.countDown();
            assertEquals(first.get(20,TimeUnit.SECONDS).path("id"),generate(key).path("id"));
            assertEquals(1,count("recall_decks"));assertEquals(1,count("recall_receipts"));
            verify(ai,times(1)).generateCards(anyString(),anyList(),anyString(),anyInt());
        } finally {release.countDown();pool.shutdownNow();}
    }

    @Test void providerFailurePreservesMaterialWithoutCreatingDeck()throws Exception {
        when(ai.generateCards(anyString(),anyList(),anyString(),anyInt())).thenThrow(RecallException.aiUnavailable("Unavailable"));
        call(owner,post("/api/v1/recall/decks").header("Idempotency-Key",UUID.randomUUID()),Map.of("materialId",material,"title","Testing","count",5)).andExpect(status().isServiceUnavailable());
        assertEquals(1,count("recall_materials"));assertEquals(0,count("recall_decks"));
        mvc.perform(get("/api/v1/recall/decks")).andExpect(status().isUnauthorized());
        call(owner,get("/api/v1/recall/decks?limit=101"),Map.of()).andExpect(status().isBadRequest());
    }

    @Test void unknownAndCoercedInputAreRejectedWithoutWrites()throws Exception {
        for(var extra:List.of("\"userId\":\"ignored\"", "\"count\":\"5\"", "\"count\":5.5")) {
            String body="{\"materialId\":\""+material+"\",\"title\":\"Testing\","+extra+"}";
            mvc.perform(post("/api/v1/recall/decks").with(user(new AppUserPrincipal(owner,"owner@example.test","unused"))).with(csrf()).header("Idempotency-Key",UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        assertEquals(0,count("recall_decks"));
    }
}
