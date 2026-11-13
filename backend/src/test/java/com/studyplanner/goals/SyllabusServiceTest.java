package com.studyplanner.goals;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyplanner.api.RequestValidationException;

class SyllabusServiceTest {
    final ObjectMapper mapper = new ObjectMapper();
    final SyllabusService service = new SyllabusService(new GeminiPlanService(mapper, "", ""));
    String encode(String name, String xml) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(name)); zip.write(xml.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }
    Map<String,String> docx(String xml) throws Exception {
        return Map.of("fileName", "course.docx", "mimeType", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "base64", encode("word/document.xml", xml));
    }
    @Test void plainTextHasHonestLocalModeAndExtractedSubject() throws Exception {
        var result = service.extract(mapper.valueToTree(Map.of("content", "Subject: Databases\n- SQL JOIN exercises", "fileName", "course.md")));
        assertEquals("Databases", result.subject()); assertTrue(result.topics().contains("SQL JOIN"));
        assertEquals("local", result.extractionMethod());
    }
    @Test void docxIsReadAsXmlRatherThanBinaryText() throws Exception {
        String xml = "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>"
                + "<w:p><w:r><w:t>Subject: Database</w:t></w:r><w:r><w:t>s</w:t></w:r></w:p>"
                + "<w:p><w:r><w:t>- SQL JOIN exercises</w:t></w:r></w:p></w:body></w:document>";
        var result = service.extract(mapper.valueToTree(docx(xml)));
        assertEquals("Databases", result.subject()); assertTrue(result.topics().contains("SQL JOIN"));
        assertEquals("local", result.extractionMethod());
    }
    @Test void malformedPdfIsUnreadableRatherThanPretendExtraction() throws Exception {
        var error = assertThrows(PlanException.class, () -> service.extract(mapper.valueToTree(Map.of(
                "fileName", "course.pdf", "mimeType", "application/pdf", "base64", Base64.getEncoder().encodeToString("%PDF-1.7".getBytes())))));
        assertEquals(422, error.status()); assertEquals("SYLLABUS_UNREADABLE", error.code());
    }
    @Test void rejectsAmbiguousOversizedAndMalformedInputs() throws Exception {
        for (var body : java.util.List.of(Map.of(), Map.of("content", "text", "base64", "aA=="),
                Map.of("content", "text", "userId", "foreign"), Map.of("content", "text", "fileName", "file.exe"),
                Map.of("content", "x".repeat(30001)), Map.of("fileName", "a.pdf", "mimeType", "application/pdf", "base64", "bad!"),
                Map.of("fileName", "a.pdf", "mimeType", "application/pdf", "base64", "aGVsbG8="))) {
            assertThrows(RequestValidationException.class, () -> service.extract(mapper.valueToTree(body)));
        }
    }
    @Test void rejectsMissingWordXmlEntitiesAndOversizedXml() throws Exception {
        var missing = Map.of("fileName", "a.docx", "mimeType", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "base64", encode("other.xml", "<x/>"));
        assertThrows(RequestValidationException.class, () -> service.extract(mapper.valueToTree(missing)));
        var entity = docx("<!DOCTYPE document [<!ENTITY xxe SYSTEM 'file:///nonexistent'>]><document><p><t>&xxe;</t></p></document>");
        assertThrows(RequestValidationException.class, () -> service.extract(mapper.valueToTree(entity)));
        var big = docx("<document><p><t>" + "x".repeat(2 * 1024 * 1024) + "</t></p></document>");
        assertThrows(RequestValidationException.class, () -> service.extract(mapper.valueToTree(big)));
    }
}
