package com.studyplanner.goals;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.*;
import java.util.*;
import java.util.regex.*;

/** Only dates explicitly labelled as an exam, never publication dates or exam durations. */
public final class SyllabusExamDate {
    private SyllabusExamDate() {}
    public record Candidate(LocalDate date, SyllabusOutline.Source source) {}
    private static final Pattern LABEL = Pattern.compile("(?iu)(?:ngày\\s+thi(?:\\s+cuối\\s+k[ỳì])?|thi\\s+cuối\\s+k[ỳì]|final\\s+exam(?:ination)?(?:\\s+date)?|exam(?:ination)?\\s+date)");
    private static final String MONTH = "(?:January|February|March|April|May|June|July|August|September|October|November|December)";
    private static final Pattern DATE = Pattern.compile("(?<!\\d)(?:\\d{4}-\\d{1,2}-\\d{1,2}|\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{4}|ngày\\s+\\d{1,2}\\s+tháng\\s+\\d{1,2}\\s+năm\\s+\\d{4}|\\d{1,2}\\s+" + MONTH + "\\s+\\d{4}|" + MONTH + "\\s+\\d{1,2},?\\s+\\d{4})(?!\\d)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    public static List<Candidate> extract(List<String> pages, boolean paginated) {
        List<Candidate> result = new ArrayList<>();
        for (int page = 0; page < pages.size(); page++) {
            String[] lines = Normalizer.normalize(pages.get(page), Normalizer.Form.NFKC).split("\\R");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].strip(); var label = LABEL.matcher(line);
                if (!label.find()) continue;
                String suffix = line.substring(label.end());
                if (suffix.matches("(?iu)^\\s*(?:giữa\\s+k[ỳì]|midterm).*")) continue;
                // A standalone label may have its value on the next line; never scan arbitrary later rows.
                if (suffix.matches("\\s*[:：-]?\\s*") && i + 1 < lines.length) suffix = lines[i + 1].strip();
                var dates = DATE.matcher(suffix);
                while (dates.find()) {
                    LocalDate parsed = parse(dates.group());
                    if (parsed != null) result.add(new Candidate(parsed, new SyllabusOutline.Source(paginated ? page + 1 : null, line)));
                }
            }
        }
        return List.copyOf(result);
    }
    static LocalDate unique(List<Candidate> candidates) {
        var dates = candidates.stream().map(Candidate::date).distinct().toList();
        return dates.size() == 1 ? dates.getFirst() : null;
    }
    private static LocalDate parse(String raw) {
        try {
            if (raw.matches("(?i).*" + MONTH + ".*")) {
                String pattern = Character.isDigit(raw.charAt(0)) ? "d MMMM uuuu" : "MMMM d uuuu";
                return LocalDate.parse(raw.replace(",", ""), new DateTimeFormatterBuilder().parseCaseInsensitive()
                        .appendPattern(pattern).toFormatter(Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT));
            }
            if (raw.matches("\\d{4}-.*")) {
                String[] parts = raw.split("-"); return LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            }
            var numbers = Pattern.compile("\\d+").matcher(raw); var values = new ArrayList<Integer>();
            while (numbers.find()) values.add(Integer.parseInt(numbers.group()));
            return values.size() == 3 ? LocalDate.of(values.get(2), values.get(1), values.get(0)) : null;
        } catch (RuntimeException ex) { return null; }
    }
}
