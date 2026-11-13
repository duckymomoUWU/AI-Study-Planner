package com.studyplanner.goals;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

class SyllabusAdhocTest {
    @Test void referencesWithinLessonsDoNotEndTheCourseContent() {
        var outline = SyllabusOutline.parse(List.of("""
                6. Nội dung chi tiết của học phần (Course content)
                Chương 1. Tổng quát về kiểm thử
                - Testcase
                Tham khảo: [1] - Chapter 1
                (Course information
                Chapter 1. Introduction to testing
                - Testcases
                References: [1] - Chapter 1.)
                3,4 Chương 2. Kiểm thử hộp trắng: kiểm thử dòng điều
                khiển
                - Đồ thị dòng điều khiển
                Tham khảo: [1] - Chapter 8
                (Chapter 2. White-box testing: Control flow
                testing
                - Control flow graph)
                7. Danh sách giảng viên tham gia giảng dạy
                - Thông tin nhân sự
                """), "testing.pdf");
        assertEquals(2, outline.chapters().size());
        assertEquals("Chương 2. Kiểm thử hộp trắng: kiểm thử dòng điều khiển", outline.chapters().get(1).title());
        assertEquals(1, outline.chapters().getFirst().topics().size());
        assertEquals(List.of("Testcases"), outline.chapters().getFirst().topics().getFirst().aliases());
        assertTrue(outline.chapters().get(1).topics().getFirst().title().contains("Đồ thị"));
        assertFalse(outline.chapters().stream().flatMap(c -> c.topics().stream())
                .anyMatch(t -> t.title().contains("Chapter 8") || t.title().contains("nhân sự")));
    }

    @Test @EnabledIfSystemProperty(named = "syllabus.testing-pdf", matches = ".+")
    void suppliedSoftwareTestingPdfExtractsAllTenChapters() throws Exception {
        var path = Path.of(System.getProperty("syllabus.testing-pdf"));
        var pages = SyllabusOutline.pdfPages(Files.readAllBytes(path));
        var outline = SyllabusOutline.parse(pages, path.getFileName().toString());
        assertEquals(17, pages.size());
        assertEquals(10, outline.chapters().size(), "All ten numbered teaching chapters must be extracted");
        var topics = outline.chapters().stream().flatMap(c -> c.topics().stream()).toList();
        for (String text : List.of("Testcase", "giá trị biên", "cặp đôi", "dòng điều khiển", "Kiểm thử tự động"))
            assertTrue(topics.stream().anyMatch(t -> t.title().contains(text)), "Missing teaching topic: " + text);
        assertTrue(outline.chapters().get(2).title().endsWith("dòng điều khiển"));
        assertTrue(outline.chapters().get(8).title().endsWith("kiểm thử tự động"));
        assertTrue(topics.stream().allMatch(t -> t.sources().stream().allMatch(s -> s.page() >= 7 && s.page() <= 16)));
        assertFalse(topics.stream().anyMatch(t -> t.title().contains("References:")
                || t.title().contains("Tham khảo:") || t.title().contains("www.") || t.title().contains("giảng viên")));
    }
}
