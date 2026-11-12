package com.studyplanner.goals;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipInputStream;
import javax.xml.stream.*;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.studyplanner.api.ApiErrorResponse.ErrorDetail;
import com.studyplanner.api.RequestValidationException;

@Service
public class SyllabusService {
    static final int MAX_BYTES = 5 * 1024 * 1024;
    static final int MAX_TEXT = 30000;
    private final GeminiPlanService gemini;
    public SyllabusService(GeminiPlanService gemini) { this.gemini = gemini; }
    public record Result(String subject, String topics, java.time.LocalDate startDate,
                         java.time.LocalDate examDate, Integer minutesPerDay, String extractionMethod,
                         List<SyllabusOutline.Chapter> chapters, List<SyllabusExamDate.Candidate> examDateCandidates) {}

    public Result extract(JsonNode body) {
        if (body == null || !body.isObject()) throw bad("body", "invalid_type");
        Set<String> allowed = Set.of("content", "base64", "mimeType", "fileName");
        body.fieldNames().forEachRemaining(name -> { if (!allowed.contains(name)) throw bad(name, "unknown_field"); });
        String content = text(body, "content"), base64 = text(body, "base64");
        String mime = text(body, "mimeType"), fileName = text(body, "fileName");
        if (content.isBlank() == base64.isBlank()) throw bad("content", "provide_exactly_one_input");
        if (fileName.length() > 255) throw bad("fileName", "too_long");
        String lower = fileName.toLowerCase(Locale.ROOT);
        List<String> pages;
        List<String> teachingPages = null;
        String method = "local";
        if (!base64.isBlank()) {
            if (base64.length() > ((MAX_BYTES + 2L) / 3) * 4) throw bad("base64", "file_too_large");
            byte[] bytes;
            try { bytes = Base64.getDecoder().decode(base64); }
            catch (IllegalArgumentException ex) { throw bad("base64", "invalid_base64"); }
            if (bytes.length == 0 || bytes.length > MAX_BYTES) throw bad("base64", "file_too_large");
            if (lower.endsWith(".pdf") && mime.equals("application/pdf")) {
                if (bytes.length < 5 || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-"))
                    throw bad("base64", "invalid_pdf");
                var pdf = SyllabusOutline.readPdf(bytes);
                pages = pdf.pages(); teachingPages = pdf.teachingPages();
                method = "local_pdf";
            } else if (lower.endsWith(".docx") && mime.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) {
                content = readDocx(bytes);
                pages = List.of(content);
            } else throw bad("fileName", "unsupported_file_type");
        } else {
            if (!fileName.isEmpty() && !lower.endsWith(".txt") && !lower.endsWith(".md") && !lower.endsWith(".text"))
                throw bad("fileName", "unsupported_file_type");
            requireText(content);
            pages = List.of(content);
        }
        boolean paginated = method.equals("local_pdf");
        SyllabusOutline.Outline outline;
        try { outline = SyllabusOutline.parse(teachingPages == null ? pages : teachingPages, fileName.isBlank() ? "Đề cương" : fileName); }
        catch (PlanException ex) {
            if ((!ex.code().equals("SYLLABUS_NO_TOPICS") && !ex.code().equals("SYLLABUS_INCOMPLETE")) || !gemini.isConfigured()) throw ex;
            outline = SyllabusEvidence.validate(gemini.generateOutlineJson(pages), pages, new com.fasterxml.jackson.databind.ObjectMapper());
            method = "gemini";
        }
        String topics = outline.chapters().stream().flatMap(c -> c.topics().stream()).map(SyllabusOutline.Topic::title)
                .collect(java.util.stream.Collectors.joining("\n"));
        var chapters = outline.chapters();
        if (!paginated) chapters = chapters.stream().map(c -> new SyllabusOutline.Chapter(c.id(), c.title(),
                c.topics().stream().map(t -> new SyllabusOutline.Topic(t.id(), t.title(), t.aliases(),
                        t.sources().stream().map(s -> new SyllabusOutline.Source(null, s.section())).toList(), t.needsReview())).toList())).toList();
        var examDates = SyllabusExamDate.extract(pages, paginated);
        return new Result(outline.subject(), topics, null, SyllabusExamDate.unique(examDates), null, method, chapters, examDates);
    }

    private static String readDocx(byte[] bytes) {
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            java.util.zip.ZipEntry entry;
            int expanded = 0;
            while ((entry = zip.getNextEntry()) != null) {
                byte[] xml = zip.readNBytes(2 * 1024 * 1024 + 1);
                expanded += xml.length;
                if (expanded > 8 * 1024 * 1024) throw bad("base64", "document_too_large");
                if (xml.length > 2 * 1024 * 1024) throw bad("base64", "document_too_large");
                if (!entry.getName().equals("word/document.xml")) continue;
                var factory = XMLInputFactory.newFactory();
                factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
                factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
                var reader = factory.createXMLStreamReader(new ByteArrayInputStream(xml), "UTF-8");
                StringBuilder content = new StringBuilder();
                boolean inText = false;
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.DTD) throw bad("base64", "invalid_docx");
                    if (event == XMLStreamConstants.START_ELEMENT) inText = reader.getLocalName().equals("t");
                    if (event == XMLStreamConstants.CHARACTERS && inText) content.append(reader.getText());
                    if (event == XMLStreamConstants.END_ELEMENT) {
                        if (reader.getLocalName().equals("t")) inText = false;
                        if (reader.getLocalName().equals("p")) content.append('\n');
                    }
                    if (content.length() > MAX_TEXT) throw bad("content", "too_long");
                }
                reader.close();
                requireText(content.toString());
                return content.toString();
            }
        } catch (IOException | XMLStreamException ex) { throw bad("base64", "invalid_docx"); }
        throw bad("base64", "invalid_docx");
    }
    private static String text(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) return "";
        if (!node.isTextual()) throw bad(field, "invalid_type");
        return node.textValue();
    }
    private static void requireText(String content) {
        if (content.isBlank()) throw bad("content", "required");
        if (content.length() > MAX_TEXT) throw bad("content", "too_long");
    }
    private static RequestValidationException bad(String field, String issue) {
        return new RequestValidationException(List.of(new ErrorDetail(field, issue)));
    }
}
