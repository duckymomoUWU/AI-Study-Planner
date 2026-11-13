package com.studyplanner.recall;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
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
    @DisplayName("normalizeText stores Unicode NFC and LF; the comparison form also ignores curly quotes, dashes, NBSP and soft hyphens")
    void testNormalizeForms() {
        String nfd = Normalizer.normalize("Kiểm thử hộp đen", Normalizer.Form.NFD);
        assertNotEquals("Kiểm thử hộp đen", nfd, "the test string must really be decomposed");
        assertEquals("Kiểm thử hộp đen", RecallTextExtractor.normalizeText(nfd));
        assertEquals("a\nb\nc", RecallTextExtractor.normalizeText("a\r\nb\rc"));
        assertEquals("\"Xin chào\" - ok", RecallTextExtractor.normalizeWhitespace("“Xin chào” – o­k"));
    }

    @Test
    @DisplayName("verifyQuote verifies quote existence with normalized whitespace across pages and full text")
    void testVerifyQuote() {
        String fullText = "Kiểm thử hộp đen đánh giá hành vi đầu vào và đầu ra của hệ thống phần mềm.";
        List<RecallTextExtractor.PageText> pages = List.of(
                new RecallTextExtractor.PageText(1, "Kiểm thử hộp đen đánh giá hành vi đầu vào"),
                new RecallTextExtractor.PageText(2, "và đầu ra của hệ thống phần mềm.")
        );

        assertTrue(RecallTextExtractor.verifyQuote(fullText, pages, 1, "Kiểm thử hộp đen"));
        assertTrue(RecallTextExtractor.verifyQuote(fullText, pages, 1, "Kiểm   thử \n  hộp  đen"));
        assertTrue(RecallTextExtractor.verifyQuote(fullText, pages, 2, "hệ thống phần mềm."));
        assertTrue(RecallTextExtractor.verifyQuote(fullText, pages, null, "đầu vào và đầu ra"));

        assertFalse(RecallTextExtractor.verifyQuote(fullText, pages, 1, "Kiểm thử hộp trắng"));
        assertFalse(RecallTextExtractor.verifyQuote(fullText, pages, null, "Một câu hoàn toàn bịa đặt"));
        assertFalse(RecallTextExtractor.verifyQuote(fullText, pages, null, ""));
        assertFalse(RecallTextExtractor.verifyQuote(fullText, pages, null, null));
    }

    @Test
    @DisplayName("verifyQuote matches whatever Unicode form the source and the quote use")
    void testVerifyQuoteIgnoresUnicodeForm() {
        String composed = "Kiểm thử hộp đen đánh giá hành vi của hệ thống.";
        String decomposed = Normalizer.normalize(composed, Normalizer.Form.NFD);
        assertTrue(RecallTextExtractor.verifyQuote(decomposed, List.of(), null, "hộp đen đánh giá"));
        assertTrue(RecallTextExtractor.verifyQuote(composed, List.of(), null, Normalizer.normalize("hộp đen đánh giá", Normalizer.Form.NFD)));
        assertTrue(RecallTextExtractor.verifyQuote("Ông nói “kiểm thử” là cần thiết.", List.of(), null, "Ông nói \"kiểm thử\" là cần thiết."));
    }

    @Test
    @DisplayName("locatePage finds the page of a quote, the starting page when it crosses a page break, null for text sources")
    void testLocatePage() {
        List<RecallTextExtractor.PageText> pages = List.of(
                new RecallTextExtractor.PageText(1, "Trang một nói về kiểm thử hộp đen và phân vùng tương đương của đầu vào dữ liệu."),
                new RecallTextExtractor.PageText(2, "tiếp tục với giá trị biên. Trang hai nói về kiểm thử hộp trắng và độ bao phủ nhánh.")
        );
        assertEquals(1, RecallTextExtractor.locatePage(pages, "kiểm thử hộp đen"));
        assertEquals(2, RecallTextExtractor.locatePage(pages, "độ bao phủ nhánh"));
        // starts at the end of page 1 and continues on page 2
        assertEquals(1, RecallTextExtractor.locatePage(pages, "phân vùng tương đương của đầu vào dữ liệu tiếp tục với giá trị biên"));
        assertNull(RecallTextExtractor.locatePage(pages, "câu không có trong tài liệu nào cả"));
        assertNull(RecallTextExtractor.locatePage(List.of(), "kiểm thử hộp đen"));
        assertNull(RecallTextExtractor.locatePage(null, "kiểm thử hộp đen"));
    }

    @Test
    @DisplayName("extractFromPdf extracts text and page metadata correctly from real PDF, with LF line breaks only")
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
        assertFalse(result.fullText().contains("\r"), "line breaks must not depend on the operating system");
    }

    @Test
    @DisplayName("extractFromPdf rejects empty or corrupted PDF without echoing the parser's message")
    void testExtractFromPdfCorrupted() {
        assertThrows(RecallException.class, () -> RecallTextExtractor.extractFromPdf(new byte[0], "empty.pdf"));
        RecallException ex = assertThrows(RecallException.class,
                () -> RecallTextExtractor.extractFromPdf("Chỉ là một tệp chữ đổi đuôi.".getBytes(java.nio.charset.StandardCharsets.UTF_8), "fake.pdf"));
        assertEquals("VALIDATION_ERROR", ex.getCode());
        assertEquals("corrupted_pdf", ex.getDetails().get(0).issue());
        assertFalse(ex.getMessage().contains("offset"), "no internal parser text: " + ex.getMessage());
        assertFalse(ex.getMessage().contains("Error"), "no internal parser text: " + ex.getMessage());
    }

    @Test
    @DisplayName("a password protected PDF is reported as such, not as a corrupted one")
    void testExtractFromPdfPasswordProtected() throws IOException {
        byte[] protectedPdf = createProtectedPdf();
        RecallException ex = assertThrows(RecallException.class,
                () -> RecallTextExtractor.extractFromPdf(protectedPdf, "locked.pdf"));
        assertEquals("VALIDATION_ERROR", ex.getCode());
        assertEquals("password_protected", ex.getDetails().get(0).issue());
        assertTrue(ex.getMessage().contains("mật khẩu"));
        assertFalse(ex.getMessage().toLowerCase().contains("decrypt"));
    }

    @Test
    @DisplayName("a PDF without text is 422 SOURCE_NO_TEXT and one over 100 pages is rejected")
    void testExtractFromPdfNoTextAndTooManyPages() throws IOException {
        byte[] blank;
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            doc.save(out);
            blank = out.toByteArray();
        }
        assertEquals("SOURCE_NO_TEXT", assertThrows(RecallException.class,
                () -> RecallTextExtractor.extractFromPdf(blank, "blank.pdf")).getCode());

        String[] manyPages = new String[RecallTextExtractor.MAX_PDF_PAGES + 1];
        java.util.Arrays.fill(manyPages, "Page text");
        byte[] tooMany = createSamplePdf(manyPages);
        RecallException ex = assertThrows(RecallException.class, () -> RecallTextExtractor.extractFromPdf(tooMany, "many.pdf"));
        assertEquals("too_many_pages", ex.getDetails().get(0).issue());
    }

    @Test
    @DisplayName("extractFromPdf rejects PDF exceeding 5 MiB")
    void testExtractFromPdfTooLarge() {
        byte[] bigArray = new byte[(int) (RecallTextExtractor.MAX_PDF_BYTES + 10)];
        RecallException ex = assertThrows(RecallException.class,
                () -> RecallTextExtractor.extractFromPdf(bigArray, "big.pdf"));
        assertEquals("VALIDATION_ERROR", ex.getCode());
    }

    private byte[] createProtectedPdf() throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText("Secret page");
                content.endText();
            }
            StandardProtectionPolicy policy = new StandardProtectionPolicy("owner-secret", "user-secret", new AccessPermission());
            policy.setEncryptionKeyLength(128);
            doc.protect(policy);
            doc.save(baos);
            return baos.toByteArray();
        }
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
