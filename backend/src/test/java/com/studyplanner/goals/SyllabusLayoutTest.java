package com.studyplanner.goals;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

class SyllabusLayoutTest {
    @Test void englishOnlyChaptersKeepDistinctTopicsInsteadOfPairingThemAsTranslations() {
        var outline=SyllabusOutline.parse(List.of("Chapter 1: Foundations\n- Vectors\n- Matrices\n- Collision detection"),"outline.txt");
        assertEquals(3,outline.chapters().getFirst().topics().size());
        assertTrue(outline.chapters().getFirst().topics().stream().allMatch(t -> t.aliases().isEmpty()));
    }
    @Test void weeklyAndModuleLayoutsDoNotNeedAnyUniversitySectionNumber() {
        for (String prefix : List.of("Week", "Module", "Unit", "Tuần", "Buổi")) {
            var outline=SyllabusOutline.parse(List.of("Course content\n"+prefix+" 1: Networks\n• Routing\n– Switching\n"+prefix+" 2: Monitoring\n▪ SNMP\n● NetFlow\nReferences\n- Read chapter 3"),"outline.txt");
            assertEquals(2,outline.chapters().size());
            assertEquals(4,outline.chapters().stream().mapToInt(c -> c.topics().size()).sum());
            assertFalse(outline.chapters().stream().flatMap(c -> c.topics().stream()).anyMatch(t -> t.title().contains("Read chapter")));
        }
    }
    @Test void headingWithoutBulletsRetainsItsActualSourcePage() {
        var outline=SyllabusOutline.parse(List.of("Subject: Networks","Chapter IV: Network automation"),"outline.txt");
        assertEquals(2,outline.chapters().getFirst().topics().getFirst().sources().getFirst().page());
        assertTrue(outline.chapters().getFirst().topics().getFirst().needsReview());
    }
    @Test void teachingSessionWithoutChapterPrefixStillHasTopicsAndTranslationAliases() {
        var outline = SyllabusOutline.parse(List.of("""
                Subject: Network management
                6. Nội dung chi tiết của học phần (Course content)
                Buổi Nội dung (Content) Hoạt động dạy và học (Lecturing)
                1 Tổng quan Quản lý mạng và Mô hình FCAPS: L.O.1.1 [ A.O.4 ]
                - Định nghĩa quản lý mạng Lec: Giảng dạy trên lớp
                - Phân tích 5 chức năng FCAPS (Teaching in class)
                - Kiến trúc Manager-Agent Stu: Thảo luận trên lớp
                (Network Management Overview and FCAPS
                - Definition of Network Management
                - Analysis of 5 FCAPS functions
                - Manager-Agent architecture.)
                """), "network.pdf");
        assertEquals(1, outline.chapters().size());
        assertEquals(3, outline.chapters().getFirst().topics().size());
        assertTrue(outline.chapters().getFirst().topics().getFirst().title().contains("Định nghĩa"));
        assertFalse(outline.chapters().getFirst().topics().getFirst().aliases().isEmpty());
    }
    @Test @EnabledIfSystemProperty(named = "syllabus.network-pdf", matches = ".+")
    void suppliedNetworkPdfExtractsActualTeachingContent() throws Exception {
        var pages = SyllabusOutline.pdfPages(Files.readAllBytes(Path.of(System.getProperty("syllabus.network-pdf"))));
        var outline = SyllabusOutline.parse(pages, "network.pdf");
        assertEquals(12, pages.size());
        assertEquals(13, outline.chapters().size());
        var topics = outline.chapters().stream().flatMap(c -> c.topics().stream()).toList();
        for (String term : List.of("FCAPS", "STP/RSTP", "HSRP/VRRP", "VLSM", "SNMP", "NetFlow", "NMS", "Wireshark", "RADIUS/TACACS+", "Netmiko", "Agentless"))
            assertTrue(topics.stream().anyMatch(t -> t.title().contains(term)), "Missing source topic: " + term);
        assertEquals(1, topics.stream().filter(t -> t.title().equals("SNMP Traps")).count());
        assertTrue(topics.stream().allMatch(t -> t.sources().stream().allMatch(s -> s.page() >= 7 && s.page() <= 9)));
        assertFalse(outline.chapters().stream().anyMatch(c -> c.title().contains("Danh sách giảng viên")));
    }
}
