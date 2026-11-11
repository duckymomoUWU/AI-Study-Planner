package com.studyplanner.goals;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;

class SyllabusExamDateTest {
    @Test void extractsExplicitDateAndItsPageWithoutUsingPublicationDate() {
        var candidates=SyllabusExamDate.extract(List.of("Biên soạn ngày 27/05/2026\nThi cuối kỳ 60 phút", "Ngày thi cuối kỳ: 25/12/2026"),true);
        assertEquals(LocalDate.of(2026,12,25),SyllabusExamDate.unique(candidates));
        assertEquals(2,candidates.getFirst().source().page());
    }
    @Test void acceptsIsoVietnameseAndSeparateLabelValue() {
        for(String content:List.of("Final exam date: 2026-12-25", "Thi cuối kỳ: ngày 25 tháng 12 năm 2026", "Ngày thi:\n25.12.2026", "Final exam: December 25, 2026", "Exam date: 25 December 2026"))
            assertEquals(LocalDate.of(2026,12,25),SyllabusExamDate.unique(SyllabusExamDate.extract(List.of(content),false)));
    }
    @Test void absentInvalidOrAmbiguousDatesAreNeverGuessed() {
        for(String content:List.of("Thi cuối kỳ 60 phút\nBiên soạn 27/05/2026", "Ngày thi: 31/02/2026", "Ngày thi giữa kỳ: 15/11/2026", "Ngày thi: 25/12", "Ngày thi: 25/12/2026 hoặc 26/12/2026"))
            assertNull(SyllabusExamDate.unique(SyllabusExamDate.extract(List.of(content),true)));
    }
    @Test void duplicateBilingualDateIsOneDateAndApiReturnsItWithoutPersonalBudget() {
        var mapper=new ObjectMapper();
        var service=new SyllabusService(new GeminiPlanService(mapper,"",""));
        var result=service.extract(mapper.valueToTree(Map.of("content","Subject: Game\n- Collision\nNgày thi: 25/12/2026\nFinal exam date: 2026-12-25","fileName","course.txt")));
        assertEquals(LocalDate.of(2026,12,25),result.examDate());
        assertEquals(2,result.examDateCandidates().size()); assertNull(result.minutesPerDay()); assertNull(result.startDate());
        assertNull(result.examDateCandidates().getFirst().source().page());
    }
}
