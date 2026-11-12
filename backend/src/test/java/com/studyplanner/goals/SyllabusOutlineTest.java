package com.studyplanner.goals;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

class SyllabusOutlineTest {
    @Test void mergesTranslationsAndContinuationButNeverReadingAssignments() {
        var outline = SyllabusOutline.parse(List.of("""
                Tên học phần: Lập trình Game
                6. Nội dung chi tiết của học phần
                Chương 2. Toán học và vật lý trong game
                - Vector             Lec: Giảng lý thuyết
                - Matrix             Stu: Đọc chương 3 và chương 7 trong [1]
                - Xác định đụng độ
                (Chapter 2. Math and physics
                - Vector
                - Matrix
                - Collision)
                Chương 6. Lập trình Game 3D
                - Cơ bản
                - Không gian tọa độ
                (Chapter 6. 3D Game Programming
                - Basic
                - Coordinate space)
                """, """
                Chương 6. Lập trình Game 3D (tt)
                - Demo quá trình xây dựng game trên Unity
                - Thảo luận về các thể loại game
                (Chapter 6. Programming 3D Games (cont'd)
                - Demo building a game in Unity
                - Discuss game genres)
                """), "course.pdf");
        assertEquals(2, outline.chapters().size());
        var math = outline.chapters().get(0);
        assertEquals(3, math.topics().size());
        assertEquals(List.of("Collision"), math.topics().get(2).aliases());
        assertEquals(4, outline.chapters().get(1).topics().size());
        assertTrue(outline.chapters().get(1).topics().stream().anyMatch(t -> t.sources().getFirst().page() == 2));
    }
    @Test void noFabricatedTopicsOrPersonalTimes() {
        assertThrows(PlanException.class, () -> SyllabusOutline.parse(List.of("Thi cuối kỳ 60 phút\nTự học 60 tiết"), "course.pdf"));
        var result = new SyllabusService(new GeminiPlanService(new ObjectMapper(), "", "")).extract(new ObjectMapper().valueToTree(Map.of(
                "content", "Subject: Game\n- Collision\nThi cuối kỳ 60 phút", "fileName", "course.txt")));
        assertNull(result.minutesPerDay()); assertNull(result.examDate()); assertNull(result.startDate());
        assertEquals("Collision", result.chapters().getFirst().topics().getFirst().title());
        assertTrue(result.chapters().getFirst().topics().getFirst().needsReview());
    }
    @Test @EnabledIfSystemProperty(named = "syllabus.pdf", matches = ".+")
    void suppliedCo3045PdfReallyExtractsAndMerges() throws Exception {
        var pages = SyllabusOutline.pdfPages(Files.readAllBytes(Path.of(System.getProperty("syllabus.pdf"))));
        var outline = SyllabusOutline.parse(pages, "CO3045.pdf");
        assertEquals(9, pages.size()); assertEquals(6, outline.chapters().size());
        var math = outline.chapters().stream().filter(c -> c.id().equals("chapter-2")).findFirst().orElseThrow();
        assertEquals(3, math.topics().size());
        assertEquals(1, math.topics().stream().filter(t -> t.title().equals("Vector")).count());
        assertEquals(1, math.topics().stream().filter(t -> t.title().equals("Matrix")).count());
        assertEquals(1, math.topics().stream().filter(t -> t.title().contains("đụng độ")).count());
        var chapter6 = outline.chapters().stream().filter(c -> c.id().equals("chapter-6")).findFirst().orElseThrow();
        assertEquals(6, chapter6.topics().size());
        assertTrue(chapter6.topics().stream().anyMatch(t -> t.sources().stream().anyMatch(s -> s.page() == 8)));
        assertFalse(outline.chapters().stream().flatMap(c -> c.topics().stream()).anyMatch(t -> t.title().contains("Đọc chương")));
    }
}
