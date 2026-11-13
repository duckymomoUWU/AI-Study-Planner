package com.studyplanner.recall;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;

public class RecallTextExtractor {

    public static final int MAX_TEXT_LENGTH = 30000;
    public static final int MAX_PDF_PAGES = 100;
    public static final long MAX_PDF_BYTES = 5 * 1024 * 1024; // 5 MiB

    /** Whitespace in the Unicode sense plus the invisible characters PDF producers like to insert. */
    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\u00A0\\u2007\\u202F]+");
    private static final Pattern INVISIBLE = Pattern.compile("[\\u00AD\\u200B\\u200C\\u200D\\uFEFF]");

    /** How many leading characters of a quote locate the page when the quote runs across a page break. */
    private static final int PAGE_PROBE_LENGTH = 40;

    public record PageText(int page, String text) {}

    public record ExtractedContent(
            String fullText,
            List<PageText> pages,
            int pageCount,
            int characterCount
    ) {}

    /**
     * The stored form of every source text: Unicode NFC (so a PDF that decomposes Vietnamese diacritics
     * compares equal to what a model returns) and "\n" line breaks on every operating system.
     */
    public static String normalizeText(String text) {
        if (text == null) return "";
        String lines = text.replace("\r\n", "\n").replace('\r', '\n');
        return Normalizer.normalize(lines, Normalizer.Form.NFC);
    }

    /** The form used to compare a quote with a source: NFC, straight quotes and dashes, single spaces. */
    public static String normalizeWhitespace(String text) {
        if (text == null) return "";
        String s = Normalizer.normalize(text, Normalizer.Form.NFC);
        s = INVISIBLE.matcher(s).replaceAll("");
        s = s.replace('‘', '\'').replace('’', '\'').replace('“', '"').replace('”', '"')
                .replace('–', '-').replace('—', '-');
        return WHITESPACE.matcher(s).replaceAll(" ").trim();
    }

    /** True when the quote really occurs in the source text (compared in the normalized form). */
    public static boolean verifyQuote(String materialText, List<PageText> pages, Integer page, String quote) {
        if (quote == null || quote.isBlank()) {
            return false;
        }
        String normQuote = normalizeWhitespace(quote);
        if (normQuote.isEmpty()) {
            return false;
        }
        return normalizeWhitespace(materialText).contains(normQuote);
    }

    /**
     * The 1-based page that holds the quote, or null for a text source or when it cannot be placed.
     * A quote that runs across a page break is placed on the page where it starts.
     */
    public static Integer locatePage(List<PageText> pages, String quote) {
        if (pages == null || pages.isEmpty() || quote == null) {
            return null;
        }
        String normQuote = normalizeWhitespace(quote);
        if (normQuote.isEmpty()) {
            return null;
        }
        for (PageText pt : pages) {
            if (normalizeWhitespace(pt.text()).contains(normQuote)) {
                return pt.page();
            }
        }
        String probe = normQuote.length() > PAGE_PROBE_LENGTH ? normQuote.substring(0, PAGE_PROBE_LENGTH) : normQuote;
        for (PageText pt : pages) {
            if (normalizeWhitespace(pt.text()).contains(probe)) {
                return pt.page();
            }
        }
        return null;
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
            stripper.setLineSeparator("\n");
            List<PageText> pages = new ArrayList<>();
            StringBuilder fullTextBuilder = new StringBuilder();

            for (int p = 1; p <= pageCount; p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String pageText = normalizeText(stripper.getText(document));
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
        } catch (InvalidPasswordException e) {
            // Never echo the parser's own message: it is English, internal and wrong for this case.
            throw RecallException.validation("base64", "password_protected",
                    "Tệp PDF có mật khẩu. Hãy bỏ mật khẩu rồi tải lại.");
        } catch (IOException | RuntimeException e) {
            throw RecallException.validation("base64", "corrupted_pdf",
                    "Không đọc được tệp PDF. Tệp có thể bị hỏng hoặc không phải PDF.");
        }
    }
}
