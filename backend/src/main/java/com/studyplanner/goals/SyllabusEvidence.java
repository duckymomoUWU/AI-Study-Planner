package com.studyplanner.goals;

import java.text.Normalizer;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Provider names alone are not evidence: source quotes must exist on the referenced input page. */
final class SyllabusEvidence {
    private SyllabusEvidence() {}
    static SyllabusOutline.Outline validate(String raw, List<String> pages, ObjectMapper mapper) {
        try {
            var body = mapper.readTree(raw);
            PlanScheduling.strict(body, Set.of("subject", "chapters"));
            String subject = PlanScheduling.text(body, "subject", 100);
            String all = canonical(String.join("\n", pages));
            if (!all.contains(canonical(subject))) throw new IllegalArgumentException();
            var chapters = body.get("chapters");
            if (chapters == null || !chapters.isArray() || chapters.isEmpty() || chapters.size() > 40) throw new IllegalArgumentException();
            var result = new ArrayList<SyllabusOutline.Chapter>(); int chapterIndex = 0, total = 0;
            for (var chapter : chapters) {
                PlanScheduling.strict(chapter, Set.of("title", "topics"));
                String heading = PlanScheduling.text(chapter, "title", 200);
                if (!all.contains(canonical(heading))) throw new IllegalArgumentException();
                var nodes = chapter.get("topics");
                if (nodes == null || !nodes.isArray() || nodes.isEmpty() || nodes.size() > 40) throw new IllegalArgumentException();
                String id = "extracted-" + (++chapterIndex);
                var sourcesByTitle = new LinkedHashMap<String, LinkedHashSet<SyllabusOutline.Source>>();
                for (var topic : nodes) {
                    PlanScheduling.strict(topic, Set.of("title", "sources"));
                    String title = PlanScheduling.text(topic, "title", 200);
                    var sources = topic.get("sources");
                    if (sources == null || !sources.isArray() || sources.isEmpty() || sources.size() > 4) throw new IllegalArgumentException();
                    var checked = sourcesByTitle.computeIfAbsent(title, key -> new LinkedHashSet<>());
                    boolean titleSeen = false;
                    for (var source : sources) {
                        PlanScheduling.strict(source, Set.of("page", "text"));
                        JsonNode number = source.get("page");
                        if (number == null || !number.isIntegralNumber() || !number.canConvertToInt() || number.asInt() < 1 || number.asInt() > pages.size()) throw new IllegalArgumentException();
                        String quote = PlanScheduling.text(source, "text", 250);
                        if (!canonical(pages.get(number.asInt() - 1)).contains(canonical(quote))) throw new IllegalArgumentException();
                        titleSeen |= canonical(quote).contains(canonical(title));
                        checked.add(new SyllabusOutline.Source(number.asInt(), quote));
                    }
                    if (!titleSeen || ++total > 200) throw new IllegalArgumentException();
                }
                var topics = new ArrayList<SyllabusOutline.Topic>(); int topicIndex = 0;
                for (var entry : sourcesByTitle.entrySet()) topics.add(new SyllabusOutline.Topic(id + "-topic-" + (++topicIndex),
                        entry.getKey(), List.of(), List.copyOf(entry.getValue()), true));
                result.add(new SyllabusOutline.Chapter(id, heading, topics));
            }
            return new SyllabusOutline.Outline(subject, result);
        } catch (Exception ex) {
            throw new PlanException(502, "AI_INVALID_RESPONSE", "Không đối chiếu được chủ đề AI với văn bản nguồn. Hãy thử lại hoặc nhập chủ đề thủ công.");
        }
    }
    private static String canonical(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }
}
