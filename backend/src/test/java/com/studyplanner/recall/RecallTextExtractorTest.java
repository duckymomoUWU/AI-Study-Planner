package com.studyplanner.recall;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecallTextExtractorTest {

    @Test
    @DisplayName("normalizeWhitespace collapses multi-whitespace and trims")
    void testNormalizeWhitespace() {
        assertEquals("", RecallTextExtractor.normalizeWhitespace(null));
        assertEquals("", RecallTextExtractor.normalizeWhitespace("   "));
        assertEquals("Hello world", RecallTextExtractor.normalizeWhitespace("  Hello   world \n \t "));
        assertEquals("Kiểm thử phần mềm", RecallTextExtractor.normalizeWhitespace("Kiểm   thử \n\t  phần   mềm"));
    }

    @Test
    @DisplayName("verifyQuote verifies quote existence with normalized whitespace across pages and full text")
    void testVerifyQuote() {
        String fullText = "Kiểm thử hộp đen đánh giá hành vi đầu vào và đầu ra của hệ thống phần mềm.";
        List<RecallTextExtractor.PageText> pages = List.of(
                new RecallTextExtractor.PageText(1, "Kiểm thử hộp đen đánh giá hành vi đầu vào"),
                new RecallTextExtractor.PageText(2, "và đầu ra của hệ thống phần mềm.")
        );

        // Exact match
        assertTrue(RecallTextExtractor.verifyQuote(fullText, pages, 1, "Kiểm thử hộp đen"));

        // Match with different whitespace
        assertTrue(RecallTextExtractor.verifyQuote(fullText, pages, 1, "Kiểm   thử \n  hộp  đen"));

        // Match on page 2
        assertTrue(RecallTextExtractor.verifyQuote(fullText, pages, 2, "hệ thống phần mềm."));

        // Null page matches full text
        assertTrue(RecallTextExtractor.verifyQuote(fullText, pages, null, "đầu vào và đầu ra"));

        // Quote not in text returns false
        assertFalse(RecallTextExtractor.verifyQuote(fullText, pages, 1, "Kiểm thử hộp trắng"));
        assertFalse(RecallTextExtractor.verifyQuote(fullText, pages, null, "Một câu hoàn toàn bịa đặt"));
        assertFalse(RecallTextExtractor.verifyQuote(fullText, pages, null, ""));
        assertFalse(RecallTextExtractor.verifyQuote(fullText, pages, null, null));
    }

    @Test
    @DisplayName("extractFromPdf extracts text and page metadata correctly from real PDF")
    void testExtractFromPdf() throws IOException {
        byte[] pdfBytes = createSamplePdf("Page 1: Black box testing.\nBasic concepts.", "Page 2: White box testing.\nSource code structure.");

        RecallTextExtractor.ExtractedContent result = RecallTextExtractor.extractFromPdf(pdfBytes, "test.pdf");

        assertNotNull(result);
        assertEquals(2, result.pageCount());
        assertTrue(result.characterCount() > 0);
        assertTrue(result.fullText().contains("Black box testing"));
        assertTrue(result.fullText().contains("White box testing"));
        assertEquals(2, result.pages().size());
        assertEquals(1, result.pages().get(0).page());
        assertTrue(result.pages().get(0).text().contains("Page 1"));
        assertEquals(2, result.pages().get(1).page());
        assertTrue(result.pages().get(1).text().contains("Page 2"));
    }

    @Test
    @DisplayName("extractFromPdf rejects empty or corrupted PDF")
    void testExtractFromPdfCorrupted() {
        assertThrows(RecallException.class, () -> RecallTextExtractor.extractFromPdf(new byte[0], "empty.pdf"));
        assertThrows(RecallException.class, () -> RecallTextExtractor.extractFromPdf(new byte[]{1, 2, 3, 4}, "invalid.pdf"));
    }

    @Test
    @DisplayName("extractFromPdf rejects PDF exceeding 5 MiB")
    void testExtractFromPdfTooLarge() {
        byte[] bigArray = new byte[(int) (RecallTextExtractor.MAX_PDF_BYTES + 10)];
        RecallException ex = assertThrows(RecallException.class,
                () -> RecallTextExtractor.extractFromPdf(bigArray, "big.pdf"));
        assertEquals("VALIDATION_ERROR", ex.getCode());
    }

    private byte[] createSamplePdf(String... pageTexts) throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            for (String text : pageTexts) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(50, 700);
                    for (String line : text.split("\n")) {
                        content.showText(line);
                        content.newLineAtOffset(0, -15);
                    }
                    content.endText();
                }
            }
            doc.save(baos);
            return baos.toByteArray();
        }
    }
}
