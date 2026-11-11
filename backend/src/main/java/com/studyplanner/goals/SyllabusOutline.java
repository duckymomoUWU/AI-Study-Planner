package com.studyplanner.goals;

import java.io.IOException;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/** Extracts teaching content; exam date extraction is handled separately from personal availability. */
public final class SyllabusOutline {
    private SyllabusOutline() {}
    public record Source(Integer page, String section) {}
    public record Topic(String id, String title, List<String> aliases, List<Source> sources, boolean needsReview) {}
    public record Chapter(String id, String title, List<Topic> topics) {}
    public record Outline(String subject, List<Chapter> chapters) {}
    public record PdfText(List<String> pages, List<String> teachingPages) {}
    private static final Pattern HEADING = Pattern.compile("^(?:\\d+(?:[,–-]\\d+)*\\s+)?(\\(?)(Chương|Chapter)\\s+(\\d+|[IVXLCDM]+)\\s*[.:-]?(?:\\s+(.+))?$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern UNIT = Pattern.compile("(?iu)^(?:buổi|tuần|week|session|module|unit|topic|bài|lesson|lecture)\\s+(\\d+(?:\\s*[,–-]\\s*\\d+)*)\\s*[.:)–-]?\\s+(.+)$");
    private static final Pattern NUMBERED = Pattern.compile("^(\\d+(?:\\s*[,–-]\\s*\\d+)*)[.)]?\\s+(.+)$");

    public static List<String> pdfPages(byte[] bytes) {
        return readPdf(bytes).teachingPages();
    }
    public static PdfText readPdf(byte[] bytes) {
        try (var document = Loader.loadPDF(bytes)) {
            if (document.getNumberOfPages() > 100) throw new IOException();
            List<String> pages = new ArrayList<>(), teachingPages = new ArrayList<>();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                List<TextPosition> glyphs = new ArrayList<>();
                var stripper = new PDFTextStripper() {
                    float end = -1, y = -1;
                    @Override protected void writeString(String text, List<TextPosition> positions) throws IOException {
                        for (var position : positions) {
                            glyphs.add(position);
                            if (Math.abs(position.getYDirAdj() - y) > 3) end = -1;
                            if (end >= 0 && position.getXDirAdj() - end > 9) getOutput().write("    ");
                            getOutput().write(position.getUnicode());
                            end = position.getXDirAdj() + position.getWidthDirAdj(); y = position.getYDirAdj();
                        }
                    }
                };
                stripper.setSortByPosition(true); stripper.setStartPage(page); stripper.setEndPage(page);
                String raw = stripper.getText(document); pages.add(raw);
                TableBoundary boundary = teachingBoundary(glyphs);
                if (boundary == null) teachingPages.add(raw);
                else {
                    var content = new PDFTextStripper() {
                        @Override protected void processTextPosition(TextPosition position) {
                            if (position.getYDirAdj() > boundary.y() + 3 && position.getXDirAdj() >= boundary.x() - 6) return;
                            super.processTextPosition(position);
                        }
                    };
                    content.setSortByPosition(true); content.setStartPage(page); content.setEndPage(page);
                    teachingPages.add(content.getText(document));
                }
                if (pages.stream().mapToInt(String::length).sum() > 150000) throw new IOException();
            }
            if (pages.stream().allMatch(String::isBlank)) throw new IOException();
            return new PdfText(List.copyOf(pages), List.copyOf(teachingPages));
        } catch (IOException | RuntimeException ex) {
            throw new PlanException(422, "SYLLABUS_UNREADABLE", "Không đọc được văn bản PDF. Hãy tải PDF có lớp văn bản hoặc nhập chủ đề thủ công.");
        }
    }

    private record TableBoundary(float x, float y) {}
    private static TableBoundary teachingBoundary(List<TextPosition> glyphs) {
        StringBuilder text = new StringBuilder(); List<TextPosition> at = new ArrayList<>();
        for (var glyph : glyphs) {
            String value = key(glyph.getUnicode()).replaceAll("\\s+", "");
            for (int i = 0; i < value.length(); i++) { text.append(value.charAt(i)); at.add(glyph); }
        }
        String all = text.toString();
        for (String rightLabel : List.of("hoatdongdayvahoc", "lecturing", "learningactivities", "teachingactivities")) {
            int right = all.indexOf(rightLabel);
            if (right < 0) continue;
            TextPosition position = at.get(right);
            for (String contentLabel : List.of("noidung", "content", "topic")) {
                int start = 0, left;
                while ((left = all.indexOf(contentLabel, start)) >= 0) {
                    TextPosition candidate = at.get(left);
                    if (candidate.getXDirAdj() < position.getXDirAdj() - 20 && Math.abs(candidate.getYDirAdj() - position.getYDirAdj()) < 8) {
                        float columnStart = position.getXDirAdj();
                        // Centered column headers start farther right than the text inside their cells.
                        for (String marker : List.of("lec:", "stu:", "l.o.")) {
                            int offset = 0, found;
                            while ((found = all.indexOf(marker, offset)) >= 0) {
                                var cell = at.get(found);
                                if (cell.getYDirAdj() > position.getYDirAdj() + 3 && cell.getXDirAdj() > candidate.getXDirAdj() + 50)
                                    columnStart = Math.min(columnStart, cell.getXDirAdj());
                                offset = found + marker.length();
                            }
                        }
                        return new TableBoundary(columnStart, position.getYDirAdj());
                    }
                    start = left + contentLabel.length();
                }
            }
        }
        return null;
    }

    private static final class ChapterBuilder {
        String id, title; final int sourcePage; final List<TopicBuilder> topics = new ArrayList<>();
        ChapterBuilder(String id, String title, int sourcePage) { this.id = id; this.title = title; this.sourcePage = sourcePage; }
    }
    private static final class TopicBuilder {
        String title; final Set<String> aliases = new LinkedHashSet<>(); final Set<Source> sources = new LinkedHashSet<>(); boolean review;
        TopicBuilder(String title, boolean review) { this.title = title; this.review = review; }
    }

    public static Outline parse(List<String> pages, String fileName) {
        Map<String, ChapterBuilder> chapters = new LinkedHashMap<>();
        String subject = fileName.replaceFirst("(?i)\\.(pdf|docx|txt|md|text)$", "").replace('_', ' ');
        ChapterBuilder current = null;
        List<TopicBuilder> primaryBlock = new ArrayList<>();
        boolean translated = false, inContent = false;
        int translatedIndex = 0;
        boolean hasContentSection = pages.stream().flatMap(p -> p.lines()).anyMatch(SyllabusOutline::contentHeading);
        Map<String, Integer> repeated = new HashMap<>();
        pages.forEach(p -> p.lines().map(String::strip).filter(s -> !s.isBlank()).distinct().forEach(s -> repeated.merge(s, 1, Integer::sum)));
        TopicBuilder last = null;
        for (int page = 0; page < pages.size(); page++) {
            final int sourcePage = page + 1;
            String[] lines = Normalizer.normalize(pages.get(page), Normalizer.Form.NFKC).split("\\R");
            for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
                String raw = lines[lineIndex];
                String line = raw.strip();
                if (line.matches("(?i).*?(Tên học phần|Môn học|Subject|Course title)\\s*:.*")) {
                    String candidate = line.replaceFirst("(?i)^.*?(Tên học phần|Môn học|Subject|Course title)\\s*:\\s*", "").strip();
                    if (!candidate.isBlank()) subject = candidate;
                }
                if (contentHeading(line)) { inContent = true; current = null; last = null; continue; }
                // References inside a course-content row belong to that lesson, not to a new document section.
                // They are reading sources, not topics to put on the student's study plan.
                if (inContent && lessonReference(line)) { last = null; continue; }
                if (inContent && endHeading(line)) { inContent = false; current = null; last = null; }
                if (hasContentSection && !inContent) continue;
                // First column can be a session number; ignore the teaching/activity column.
                line = line.replaceFirst("^\\d+(?:[,–-]\\d+)*\\s+(?=Chương|Chapter|[-(])", "");
                String left = line.split("\\s{3,}", 2)[0].strip();
                // PDF writers often emit each column in separate text operators with a single separator.
                left = left.replaceFirst("\\s+(L\\.O\\.|Lec:|Stu:|\\(Teach(?:ing)? |\\(Giảng dạy|\\(Discuss |\\(Read |\\(Listen,|\\(Comment |\\(Present).*$", "").strip();
                if (left.matches("(?iu)^(Lec:|Stu:|L\\.O\\.|A\\.O\\.|\\(Teach|\\(Discuss|\\(Read |\\(Giảng dạy|\\(Comment|\\(Present|\\(Listen).*")) continue;
                if (left.matches("(?iu)^\\(?\\s*(course information|thông tin học phần)\\s*\\)?$")
                        || left.matches("(?i)^(?:https?://|www\\.).*")) { last = null; continue; }
                // PDF line wrapping can split a long chapter heading before the first bullet.
                // Only join the immediate next line when a bullet follows; never absorb a topic.
                if (HEADING.matcher(left).matches() && lineIndex + 2 < lines.length) {
                    String continuation = lines[lineIndex + 1].strip();
                    if (!continuation.isEmpty() && !continuation.matches("(?i)^(?:[-–—•*▪●]|\\(?\\s*(?:Chương|Chapter)\\s+\\d+|(?:tham khảo|references)\\s*:|https?://).*" )
                            && !continuation.matches("(?i)^(?:L\\.O\\.|Lec:|Stu:|\\d+\\s+).*" )
                            && nextIsBullet(lines, lineIndex + 2)) {
                        left += " " + continuation;
                        lineIndex++;
                    }
                }
                Matcher heading = HEADING.matcher(left);
                if (heading.matches()) {
                    String id = "chapter-" + heading.group(3).toLowerCase(Locale.ROOT);
                    translated = current != null && current.id.equals(id) && !primaryBlock.isEmpty()
                            && (heading.group(1).equals("(") || heading.group(2).equalsIgnoreCase("Chapter"));
                    String title = left.replaceFirst("^\\(", "").replaceFirst("(?i)\\s*\\((tt|cont'd)\\)\\s*$", "");
                    current = chapters.computeIfAbsent(id, k -> new ChapterBuilder(id, title, sourcePage));
                    if (!translated) primaryBlock = new ArrayList<>();
                    translatedIndex = 0; last = null;
                    continue;
                }
                Matcher unit = UNIT.matcher(left);
                Matcher numbered = NUMBERED.matcher(left);
                boolean prefixedUnit = unit.matches();
                if (inContent && numbered.matches() && administrative(numbered.group(2))) {
                    current = null; last = null; continue;
                }
                boolean numberedUnit = inContent && numbered.matches()
                        && (line.contains("L.O.") || nextIsBullet(lines, lineIndex + 1));
                if (prefixedUnit || numberedUnit) {
                    Matcher match = prefixedUnit ? unit : numbered;
                    String title = match.group(2).strip();
                    if (administrative(title)) { current = null; last = null; continue; }
                    String id = "unit-" + match.group(1).replaceAll("\\s+", "").replaceAll("[,–]", "-");
                    current = chapters.computeIfAbsent(id, k -> new ChapterBuilder(id, title, sourcePage));
                    translated = false; translatedIndex = 0; primaryBlock = new ArrayList<>(); last = null;
                    continue;
                }
                // A bilingual unit title need not contain the words Chapter/Chương.
                if (current != null && !primaryBlock.isEmpty() && !translated && left.startsWith("(")
                        && nextIsBullet(lines, lineIndex + 1)) {
                    translated = true; translatedIndex = 0; last = null; continue;
                }
                if (left.matches("^\\d+(?:[,–-]\\d+)*$") || left.matches("^\\(?-?\\s*(Các nhóm|Groups? present|Group present).*")) {
                    current = null; last = null; continue;
                }
                if (current == null && hasContentSection) continue;
                if (left.matches("^[-–—•*▪●]\\s+.+")) {
                    String title = left.replaceFirst("^[-–—•*▪●]\\s+", "").strip();
                    if (translated && title.endsWith(")") && title.chars().filter(c -> c == ')').count() > title.chars().filter(c -> c == '(').count())
                        title = title.substring(0, title.length() - 1).strip();
                    String key = key(title);
                    if (administrative(title)) continue;
                    if (current == null) current = chapters.computeIfAbsent("manual", k -> new ChapterBuilder(k, "Nội dung cần rà soát", sourcePage));
                    Source source = new Source(page + 1, current.title);
                    if (translated && translatedIndex < primaryBlock.size()) {
                        last = primaryBlock.get(translatedIndex++); last.aliases.add(title); last.sources.add(source);
                        if (!key(last.title).equals(key(title))) last.review = true;
                    } else {
                        last = current.topics.stream().filter(t -> key(t.title).equals(key)).findFirst().orElse(null);
                        if (last == null) { last = new TopicBuilder(title, current.id.equals("manual") || translated); current.topics.add(last); }
                        last.sources.add(source); primaryBlock.add(last);
                    }
                } else if (last != null && !left.isBlank() && !left.equals(")") && repeated.getOrDefault(raw.strip(), 0) < 2
                        && !left.matches("(?iu)^(about:|\\d+/\\d+|phone|điện thoại|buổi|\\(session|week\\s+topic|thi\\b|ngày thi|final exam|exam date|published|subject:).*")) {
                    if (translated && !last.aliases.isEmpty()) {
                        String previous = new ArrayList<>(last.aliases).getLast();
                        last.aliases.remove(previous); last.aliases.add(previous + " " + left.replaceFirst("\\)$", ""));
                    } else if (!translated) last.title += " " + left;
                }
            }
        }
        List<Chapter> result = new ArrayList<>();
        for (var chapter : chapters.values()) {
            if (chapter.topics.isEmpty()) {
                var topic = new TopicBuilder(chapter.title, true); topic.sources.add(new Source(chapter.sourcePage, chapter.title)); chapter.topics.add(topic);
            }
            List<Topic> topics = new ArrayList<>(); int i = 0;
            for (var topic : chapter.topics) topics.add(new Topic(chapter.id + "-topic-" + (++i), topic.title,
                    List.copyOf(topic.aliases), List.copyOf(topic.sources), topic.review));
            result.add(new Chapter(chapter.id, chapter.title, topics));
        }
        ensureChapterCoverage(pages, chapters.keySet(), hasContentSection);
        if (result.isEmpty()) throw new PlanException(422, "SYLLABUS_NO_TOPICS", "Không trích được chủ đề giảng dạy. Hãy thử tài liệu khác hoặc nhập chủ đề thủ công.");
        return new Outline(subject.substring(0, Math.min(100, subject.length())), result);
    }
    // Check chapter coverage against the source independently of the topic-extraction state.
    // Otherwise one prematurely closed section can silently turn a ten-chapter course into one chapter.
    private static void ensureChapterCoverage(List<String> pages, Set<String> parsed, boolean hasContentSection) {
        Set<String> expected = new LinkedHashSet<>();
        boolean inContent = false;
        for (String page : pages) {
            for (String raw : page.split("\\R")) {
                String line = Normalizer.normalize(raw, Normalizer.Form.NFKC).strip();
                if (contentHeading(line)) { inContent = true; continue; }
                if (hasContentSection && !inContent) continue;
                if (inContent && lessonReference(line)) continue;
                if (inContent && endHeading(line)) { inContent = false; continue; }
                Matcher heading = HEADING.matcher(line.split("\\s{3,}", 2)[0]);
                if (heading.matches()) expected.add("chapter-" + heading.group(3).toLowerCase(Locale.ROOT));
            }
        }
        if (!parsed.containsAll(expected))
            throw new PlanException(422, "SYLLABUS_INCOMPLETE", "Đề cương có chương chưa trích xuất được. Hãy thử lại hoặc nhập chủ đề thủ công để tránh thiếu lịch ôn tập.");
    }
    private static boolean nextIsBullet(String[] lines, int from) {
        for (int i = from; i < Math.min(lines.length, from + 3); i++) {
            if (lines[i].isBlank()) continue;
            return lines[i].strip().matches("^[-–—•*▪●]\\s+.+");
        }
        return false;
    }
    private static boolean contentHeading(String line) {
        String value = key(line).replaceFirst("^\\d+(?:\\.\\d+)*[.)]?\\s*", "");
        return value.matches("^(noi dung chi tiet(?: cua hoc phan)?|noi dung hoc phan|ke hoach giang day|lich trinh giang day|detailed course content|course content|course schedule|teaching schedule|weekly schedule|syllabus outline|topics covered)(?:\\s.*|[:(].*)?");
    }
    private static boolean endHeading(String line) {
        String value = key(line).replaceFirst("^\\d+(?:\\.\\d+)*[.)]?\\s*", "");
        return value.matches("^(danh sach giang vien|bien soan va cap nhat|giao trinh va tai lieu|tai lieu tham khao|course materials|references|reading list|list of lecturers|editing information|assessment methods|grading policy)(?:\\s.*|[:(].*)?");
    }
    private static boolean lessonReference(String line) {
        String value = key(line);
        return value.matches("^\\(?\\s*(?:tham khao|references?)\\s*:(?:.*)$")
                || value.matches("^(?:https?://|www\\.).*");
    }
    private static boolean administrative(String title) {
        String value = key(title).replaceFirst("^[-(]\\s*", "").replaceFirst("[\\p{Punct}\\s]+$", "");
        return value.matches(".*(bat dau|started making|thuyet trinh|presenting|presentation|btl\\d|ass\\d).*" )
                || value.matches("(discussion|thao luan|tong ket|review|on tap)") || value.matches("^(doc chuong|read chapter).*");
    }
    private static String key(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").replace('đ', 'd').toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }
}
