package com.studyplanner.recall;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

public class RecallTextExtractor {

    public static final int MAX_TEXT_LENGTH = 30000;
    public static final int MAX_PDF_PAGES = 100;
    public static final long MAX_PDF_BYTES = 5 * 1024 * 1024; // 5 MiB

    public record PageText(int page, String text) {}

    public record ExtractedContent(
            String fullText,
            List<PageText> pages,
            int pageCount,
            int characterCount
    ) {}

    public static String normalizeWhitespace(String text) {
        if (text == null) return "";
        return text.replaceAll("\\s+", " ").trim();
    }

    public static boolean verifyQuote(String materialText, List<PageText> pages, Integer page, String quote) {
        if (quote == null || quote.isBlank()) {
            return false;
        }
        String normQuote = normalizeWhitespace(quote);
        if (normQuote.isEmpty()) {
            return false;
        }

        if (page != null && pages != null && !pages.isEmpty()) {
            for (PageText pt : pages) {
                if (pt.page() == page) {
                    String normPageText = normalizeWhitespace(pt.text());
                    if (normPageText.contains(normQuote)) {
                        return true;
                    }
                }
            }
            return false;
        }
        if (page != null && (pages == null || pages.isEmpty())) return false;

        String normFull = normalizeWhitespace(materialText);
        return normFull.contains(normQuote);
    }

    public static ExtractedContent extractFromPdf(byte[] pdfBytes, String fileName) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw RecallException.validation("base64", "empty_pdf", "Tệp PDF không được để trống.");
        }
        if (pdfBytes.length > MAX_PDF_BYTES) {
            throw RecallException.validation("base64", "file_too_large", "Kích thước tệp PDF vượt quá giới hạn 5 MiB.");
        }

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            int pageCount = document.getNumberOfPages();
            if (pageCount <= 0) {
                throw RecallException.sourceNoText("Tài liệu PDF không có trang nào.");
            }
            if (pageCount > MAX_PDF_PAGES) {
                throw RecallException.validation("base64", "too_many_pages",
                        "Tài liệu PDF vượt quá giới hạn 100 trang (" + pageCount + " trang).");
            }

            PDFTextStripper stripper = new PDFTextStripper();
            List<PageText> pages = new ArrayList<>();
            StringBuilder fullTextBuilder = new StringBuilder();

            for (int p = 1; p <= pageCount; p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String pageText = stripper.getText(document);
                if (pageText == null) {
                    pageText = "";
                }
                pages.add(new PageText(p, pageText));
                fullTextBuilder.append(pageText).append("\n");
            }

            String fullText = fullTextBuilder.toString().trim();
            if (fullText.isEmpty()) {
                throw RecallException.sourceNoText("Tài liệu không chứa văn bản có thể trích xuất (có thể là file scan hoặc rỗng).");
            }

            int charCount = fullText.length();
            if (charCount > MAX_TEXT_LENGTH) {
                throw RecallException.sourceTooLong("Văn bản trích xuất vượt quá 30.000 ký tự (" + charCount + " ký tự). Vui lòng chọn đoạn ngắn hơn.");
            }

            return new ExtractedContent(fullText, pages, pageCount, charCount);
        } catch (RecallException re) {
            throw re;
        } catch (IOException e) {
            throw RecallException.validation("base64", "corrupted_pdf", "Không thể đọc tệp PDF. Tệp có thể bị hỏng hoặc sai định dạng: " + e.getMessage());
        }
    }
}
