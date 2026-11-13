package com.studyplanner.recall;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class RecallQualityValidator {

    private RecallQualityValidator() {}

    private static final Set<String> STOP_WORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            // Vietnamese stop words & markers
            "là", "và", "của", "các", "những", "cho", "với", "được", "trong", "đã",
            "sẽ", "đang", "thì", "một", "này", "đó", "theo", "về", "có", "không",
            "như", "để", "ở", "tại", "khi", "do", "nên", "hãy", "gì", "thế",
            "nào", "sao", "bao", "nhiêu", "ai", "đâu", "qua", "từ", "ra", "vào",
            "khái", "niệm", "định", "nghĩa", "vai", "trò", "ý", "mục", "đích",
            "đặc", "điểm", "biết", "hỏi", "câu", "nêu", "giải", "thích", "bằng",
            "trên", "dưới", "giữa", "bên", "dựa", "thuộc", "cùng",
            // English stop words & markers
            "what", "is", "are", "a", "an", "the", "of", "in", "to", "for",
            "with", "on", "at", "by", "from", "as", "how", "does", "do", "did",
            "why", "which", "where", "when", "who", "can", "could", "would",
            "should", "define", "definition", "explain", "describe", "meaning",
            "purpose", "role", "it", "its", "they", "them", "their", "this",
            "that", "these", "those", "and", "or", "be", "been", "being"
    )));

    /**
     * Common Vietnamese grammatical, structural, and framing words permitted in answers
     * even if not literally appearing in the source text.
     */
    private static final Set<String> META_STOP_WORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "tôi", "chúng", "ta", "nó", "họ", "đó", "này", "kia", "đây", "đấy", "thế", "vậy",
            "người", "ai", "gì", "nào", "sao", "bao", "nhiêu", "đâu", "và", "hoặc", "hay",
            "cùng", "với", "cho", "của", "thuộc", "về", "trong", "ngoài", "trên", "dưới",
            "giữa", "bên", "tại", "ở", "từ", "đến", "tới", "qua", "sang", "vào", "ra",
            "lên", "xuống", "theo", "như", "bằng", "do", "bởi", "vì", "nhằm", "để", "nếu",
            "thì", "mà", "dù", "tuy", "nhưng", "song", "đồng", "thời", "hơn", "nữa",
            "là", "có", "không", "chưa", "chẳng", "đã", "đang", "sẽ", "vừa", "mới", "từng",
            "luôn", "thường", "cũng", "đều", "rất", "quá", "lắm", "hơi", "khá", "cực",
            "kỳ", "hết", "sức", "thực", "sự", "chính", "thể", "được", "bị", "phải", "cần",
            "nên", "hãy", "muốn", "thích", "câu", "hỏi", "trả", "lời", "đáp", "án", "ý",
            "nghĩa", "nội", "dung", "khái", "niệm", "định", "nghĩa", "vai", "trò", "mục",
            "đích", "đặc", "điểm", "tính", "chất", "yếu", "tố", "thành", "phần", "bộ", "phận",
            "phương", "pháp", "cách", "thức", "quá", "trình", "bước", "kết", "quả", "nguyên",
            "nhân", "lý", "do", "ví", "dụ", "bao", "gồm", "hiểu", "xác", "hiện",
            "dựa", "liên", "quan", "áp", "dụng", "sử", "dụng", "dùng", "tạo", "giúp", "làm",
            "mang", "đóng", "trọng", "chủ", "yếu", "cơ", "bản", "cụ", "thể", "rõ",
            "ràng", "chính", "xác", "đầy", "đủ", "duy", "nhất", "nhanh", "chậm", "nhiều",
            "ít", "cao", "thấp", "tốt", "xấu", "đúng", "sai", "trước", "sau", "chỉ"
    )));

    private static final Pattern VAGUE_QUESTION_PATTERN = Pattern.compile(
            "(?iu).*(đoạn văn|đoạn trích|nội dung|câu văn|phần|thông tin|khái niệm|ý kiến)\\s+(này|trên|sau|dưới đây)\\s+(nói về|có ý nghĩa|là gì|như thế nào|đề cập).*"
    );

    public record QualityResult(boolean passed, String warning) {
        public static QualityResult pass() {
            return new QualityResult(true, null);
        }
        public static QualityResult fail(String warning) {
            return new QualityResult(false, warning);
        }
    }

    public record EvaluatedCard(
            GeminiRecallService.GeneratedCard card,
            boolean passedQuality,
            String warning
    ) {}

    /**
     * Validates an individual card (e.g. after user edit or before activation)
     * against the source material and against other cards in the deck.
     */
    public static QualityResult validateCardQuality(
            String question,
            String answer,
            String sourceQuote,
            String materialText,
            List<RecallDTOs.CardResponse> otherCards
    ) {
        // 1. Check question clarity & single concept
        String clarityWarning = checkQuestionClarity(question);
        if (clarityWarning != null) {
            return QualityResult.fail(clarityWarning);
        }

        // 2. Basic answer validation
        if (answer == null || answer.trim().isEmpty()) {
            return QualityResult.fail("Đáp án không được để trống.");
        }
        String qTrim = question.trim();
        String aTrim = answer.trim();
        if (aTrim.equalsIgnoreCase(qTrim)) {
            return QualityResult.fail("Đáp án bị trùng lặp hoàn toàn với câu hỏi.");
        }

        // 3. Duplicate check against other cards in the same deck
        if (otherCards != null) {
            for (RecallDTOs.CardResponse other : otherCards) {
                if (isDuplicateQuestion(qTrim, other.question(), aTrim, other.answer())) {
                    String prevSummary = other.question().length() > 50
                            ? other.question().substring(0, 47) + "..."
                            : other.question();
                    return QualityResult.fail("Câu hỏi có thể trùng lặp nội dung với câu: \"" + prevSummary + "\".");
                }
            }
        }

        // 4. Source quote existence (Cannot be certain without source verification quote)
        if (sourceQuote == null || sourceQuote.trim().isEmpty()) {
            return QualityResult.fail("Thẻ thiếu trích dẫn nguồn để xác minh tính chính xác của đáp án.");
        }
        String quoteTrim = sourceQuote.trim();

        // 5. Detect alien / unsubstantiated terms in answer (not in material and not standard meta words)
        if (materialText != null && !materialText.isBlank()) {
            String alienWarning = detectAlienTerms(aTrim, materialText);
            if (alienWarning != null) {
                return QualityResult.fail(alienWarning);
            }
        }

        // 6. Detect missing core focus (does answer actually answer what the question asks from the quote?)
        String focusWarning = checkMissingCoreFocus(qTrim, aTrim, quoteTrim);
        if (focusWarning != null) {
            return QualityResult.fail(focusWarning);
        }

        // 7. Detect corrupted / substituted phrases compared to quote
        String corruptionWarning = detectCorruptedPhrases(aTrim, quoteTrim);
        if (corruptionWarning != null) {
            return QualityResult.fail(corruptionWarning);
        }

        // 8. Detect semantic contradictions with quote
        String contradictionWarning = detectContradictions(aTrim, quoteTrim);
        if (contradictionWarning != null) {
            return QualityResult.fail(contradictionWarning);
        }

        // 9. Grounding check against source material
        if (materialText != null && !materialText.isBlank()) {
            String groundingWarning = checkGroundingInSource(qTrim, aTrim, materialText);
            if (groundingWarning != null) {
                return QualityResult.fail(groundingWarning);
            }
        }

        // 10. Semantic alignment with quote
        String semanticWarning = checkAnswerSemanticAlignment(qTrim, aTrim, quoteTrim);
        if (semanticWarning != null) {
            return QualityResult.fail(semanticWarning);
        }

        return QualityResult.pass();
    }

    /**
     * Detects substantive content words in the answer that do not exist in the source material
     * and are not standard meta/grammatical words.
     * Prevents insertion of nonsensical, off-topic, or alien words like "trái đất".
     */
    public static String detectAlienTerms(String answer, String materialText) {
        if (materialText == null || materialText.isBlank() || answer == null || answer.isBlank()) {
            return null;
        }

        String matLower = materialText.toLowerCase(Locale.ROOT);
        String cleaned = answer.toLowerCase(Locale.ROOT).replaceAll("[^a-zà-ỹ0-9\\s]", " ");
        String[] words = cleaned.split("\\s+");

        List<String> alienWords = new ArrayList<>();
        for (String w : words) {
            String trimmed = w.trim();
            if (trimmed.length() >= 2 && !META_STOP_WORDS.contains(trimmed)) {
                // Check if this word exists in the material text
                if (!matLower.contains(trimmed)) {
                    alienWords.add(trimmed);
                }
            }
        }

        if (!alienWords.isEmpty()) {
            return "Nội dung đáp án không có căn cứ trong tài liệu nguồn (chứa từ ngữ bất thường hoặc không có trong tài liệu: \""
                    + String.join(" ", alienWords) + "\").";
        }

        return null;
    }

    /**
     * Verifies that the answer actually addresses the core target of the question
     * based on the source quote.
     * Identifies what the question is asking from the quote (focus candidates),
     * and verifies that the answer contains the necessary focus terms.
     */
    public static String checkMissingCoreFocus(String question, String answer, String quote) {
        if (question == null || answer == null || quote == null) return null;

        Set<String> qTokens = extractContentTokens(question);
        Set<String> quoteTokens = extractContentTokens(quote);
        Set<String> aTokens = extractContentTokens(answer);

        if (quoteTokens.size() < 3) return null;

        // Anchor tokens are tokens that the question and quote share
        Set<String> anchorTokens = new HashSet<>(quoteTokens);
        anchorTokens.retainAll(qTokens);

        // Focus candidates are the non-anchor tokens in the quote that provide the answer
        Set<String> focusCandidates = new HashSet<>(quoteTokens);
        focusCandidates.removeAll(anchorTokens);

        // If there are clear focus candidates in the quote (>= 2 tokens),
        // the answer MUST contain at least one of these core focus concepts!
        if (focusCandidates.size() >= 2) {
            Set<String> matchedFocus = new HashSet<>(focusCandidates);
            matchedFocus.retainAll(aTokens);

            if (matchedFocus.isEmpty()) {
                return "Đáp án thiếu ý quan trọng hoặc chưa thực sự trả lời trọng tâm câu hỏi từ tài liệu nguồn.";
            }
        }

        return null;
    }

    /**
     * Detects phrase substitution/corruption where an answer copies a prefix of a quote's phrase
     * but substitutes a key word with an alien/divergent word.
     */
    public static String detectCorruptedPhrases(String answer, String quote) {
        if (answer == null || quote == null) return null;

        String aClean = answer.toLowerCase(Locale.ROOT).replaceAll("[^a-zà-ỹ0-9\\s]", " ").replaceAll("\\s+", " ").trim();
        String qClean = quote.toLowerCase(Locale.ROOT).replaceAll("[^a-zà-ỹ0-9\\s]", " ").replaceAll("\\s+", " ").trim();

        String[] qWords = qClean.split(" ");

        // Look for 3-word phrase matches from quote that diverge immediately on the next word
        for (int i = 0; i <= qWords.length - 4; i++) {
            String trigram = qWords[i] + " " + qWords[i + 1] + " " + qWords[i + 2];
            int aIdx = aClean.indexOf(trigram);
            if (aIdx >= 0) {
                // Find what comes after the trigram in the answer
                String afterTrigram = aClean.substring(aIdx + trigram.length()).trim();
                String[] afterWords = afterTrigram.split(" ");
                if (afterWords.length > 0 && !afterWords[0].isEmpty()) {
                    String expectedNextWord = qWords[i + 3];
                    String actualNextWord = afterWords[0];
                    if (!expectedNextWord.equals(actualNextWord) && !qClean.contains(actualNextWord) && !META_STOP_WORDS.contains(actualNextWord)) {
                        return "Đáp án có dấu hiệu thay đổi từ ngữ làm sai lệch hoặc vô nghĩa so với câu văn gốc trong tài liệu nguồn (tại cụm từ \""
                                + trigram + "...\").";
                    }
                }
            }
        }

        return null;
    }

    /**
     * Detects semantic contradictions (e.g. negation mismatch between quote and answer).
     */
    public static String detectContradictions(String answer, String quote) {
        if (answer == null || quote == null) return null;

        String aLower = answer.toLowerCase(Locale.ROOT);
        String qLower = quote.toLowerCase(Locale.ROOT);

        boolean quoteNegative = qLower.contains("không được") || qLower.contains("không thể") || qLower.contains("không cho phép") || qLower.contains("không có");
        boolean answerPositive = (aLower.contains("được phép") || aLower.contains("có thể") || aLower.contains("cho phép")) && !aLower.contains("không");

        if (quoteNegative && answerPositive) {
            return "Đáp án có dấu hiệu mâu thuẫn trực tiếp với nội dung đoạn trích dẫn nguồn (khẳng định điều tài liệu phủ định).";
        }

        boolean quotePositive = qLower.contains("bắt buộc") || qLower.contains("luôn luôn") || qLower.contains("duy nhất");
        boolean answerNegative = aLower.contains("không bắt buộc") || aLower.contains("không cần") || aLower.contains("không duy nhất");

        if (quotePositive && answerNegative) {
            return "Đáp án có dấu hiệu mâu thuẫn trực tiếp với nội dung đoạn trích dẫn nguồn (phủ định điều tài liệu khẳng định).";
        }

        return null;
    }

    /**
     * Verifies that question and answer content are factually grounded in the source material.
     * Detects off-topic, hallucinated, or completely external knowledge.
     */
    public static String checkGroundingInSource(String question, String answer, String materialText) {
        if (materialText == null || materialText.isBlank()) {
            return null;
        }

        String matLower = materialText.toLowerCase(Locale.ROOT);
        Set<String> qTokens = extractContentTokens(question);
        Set<String> aTokens = extractContentTokens(answer);

        long qMatched = qTokens.stream().filter(matLower::contains).count();
        long aMatched = aTokens.stream().filter(matLower::contains).count();

        boolean qGrounded = true;
        if (!qTokens.isEmpty()) {
            if (qTokens.size() >= 2) {
                double qRatio = (double) qMatched / qTokens.size();
                if (qMatched == 0 || qRatio < 0.34) {
                    qGrounded = false;
                }
            } else {
                if (qMatched == 0) {
                    qGrounded = false;
                }
            }
        }

        boolean aGrounded = true;
        if (!aTokens.isEmpty()) {
            if (aTokens.size() >= 2) {
                double aRatio = (double) aMatched / aTokens.size();
                if (aMatched == 0 || aRatio < 0.30) {
                    aGrounded = false;
                }
            } else {
                if (aMatched == 0) {
                    aGrounded = false;
                }
            }
        }

        if (!qGrounded && !aGrounded) {
            return "Cả câu hỏi và đáp án đều không có căn cứ trong tài liệu nguồn.";
        }
        if (!qGrounded) {
            return "Nội dung câu hỏi không có căn cứ trong tài liệu nguồn.";
        }
        if (!aGrounded) {
            return "Nội dung đáp án không có căn cứ trong tài liệu nguồn.";
        }

        return null;
    }

    /**
     * Evaluates a deck of cards against source material and against each other.
     * Verifies grounding, semantic alignment, question clarity, and detects duplicates.
     */
    public static List<EvaluatedCard> evaluateDeck(
            List<GeminiRecallService.GeneratedCard> cards,
            String materialText,
            List<RecallTextExtractor.PageText> pages
    ) {
        if (cards == null || cards.isEmpty()) {
            return Collections.emptyList();
        }

        List<EvaluatedCard> results = new ArrayList<>(cards.size());

        for (int i = 0; i < cards.size(); i++) {
            GeminiRecallService.GeneratedCard card = cards.get(i);
            String warning = null;

            // 1. Check quote existence in source document
            boolean quoteVerified = RecallTextExtractor.verifyQuote(materialText, pages, card.page(), card.quote());
            if (!quoteVerified) {
                warning = "Đoạn trích dẫn không tồn tại trong tài liệu nguồn.";
            }

            // 2. Check question clarity & single concept
            if (warning == null) {
                String questionWarning = checkQuestionClarity(card.question());
                if (questionWarning != null) {
                    warning = questionWarning;
                }
            }

            // 3. Duplicate check against preceding cards in the deck
            if (warning == null) {
                for (int j = 0; j < i; j++) {
                    GeminiRecallService.GeneratedCard previous = cards.get(j);
                    if (isDuplicateQuestion(card.question(), previous.question(), card.answer(), previous.answer())) {
                        String prevSummary = previous.question().length() > 50
                                ? previous.question().substring(0, 47) + "..."
                                : previous.question();
                        warning = "Câu hỏi có thể trùng lặp nội dung với câu: \"" + prevSummary + "\".";
                        break;
                    }
                }
            }

            // 4. Detect alien terms in answer
            if (warning == null && materialText != null) {
                warning = detectAlienTerms(card.answer(), materialText);
            }

            // 5. Missing core focus
            if (warning == null && card.quote() != null) {
                warning = checkMissingCoreFocus(card.question(), card.answer(), card.quote());
            }

            // 6. Corrupted phrases
            if (warning == null && card.quote() != null) {
                warning = detectCorruptedPhrases(card.answer(), card.quote());
            }

            // 7. Contradictions with quote
            if (warning == null && card.quote() != null) {
                warning = detectContradictions(card.answer(), card.quote());
            }

            // 8. Grounding check against source material
            if (warning == null && materialText != null && !materialText.isBlank()) {
                String groundingWarning = checkGroundingInSource(card.question(), card.answer(), materialText);
                if (groundingWarning != null) {
                    warning = groundingWarning;
                }
            }

            // 9. Check answer semantic accuracy against quote
            if (warning == null && card.quote() != null) {
                String semanticWarning = checkAnswerSemanticAlignment(card.question(), card.answer(), card.quote());
                if (semanticWarning != null) {
                    warning = semanticWarning;
                }
            }

            boolean passed = (warning == null);
            results.add(new EvaluatedCard(card, passed, warning));
        }

        return results;
    }

    private static String checkQuestionClarity(String question) {
        if (question == null || question.trim().isEmpty()) {
            return "Câu hỏi không được để trống.";
        }
        String q = question.trim();
        if (q.length() < 10) {
            return "Câu hỏi quá ngắn, chưa đủ thông tin cụ thể.";
        }

        // Check for overly vague generic questions
        if (VAGUE_QUESTION_PATTERN.matcher(q).matches() && q.length() < 50) {
            return "Câu hỏi mơ hồ, thiếu từ khóa hoặc chủ ngữ kiến thức cụ thể.";
        }

        // Check for overloaded question testing multiple concepts
        int qMarks = 0;
        for (char c : q.toCharArray()) {
            if (c == '?') qMarks++;
        }
        if (qMarks > 1) {
            return "Câu hỏi kiểm tra nhiều ý cùng lúc, nên tách thành từng câu đơn.";
        }

        return null;
    }

    private static String checkAnswerSemanticAlignment(String question, String answer, String quote) {
        if (answer == null || answer.trim().isEmpty()) {
            return "Đáp án không được để trống.";
        }
        if (quote == null || quote.trim().isEmpty()) {
            return "Trích dẫn chứng minh không được để trống.";
        }

        String a = answer.trim();
        String q = question != null ? question.trim() : "";
        if (a.equalsIgnoreCase(q)) {
            return "Đáp án bị trùng lặp hoàn toàn với câu hỏi.";
        }

        // Check keyword overlap between answer and quote
        Set<String> quoteTokens = extractContentTokens(quote);
        Set<String> answerTokens = extractContentTokens(a);

        if (quoteTokens.size() >= 3 && answerTokens.size() >= 2) {
            Set<String> common = new HashSet<>(answerTokens);
            common.retainAll(quoteTokens);

            // If answer has 0 keyword overlap with quote
            if (common.isEmpty()) {
                return "Đáp án có thể suy diễn hoặc chưa bám sát ý nghĩa của đoạn trích dẫn nguồn.";
            }
        }

        return null;
    }

    /**
     * Determines whether two cards test essentially the same question or concept.
     * Evaluates both question intent and answer semantics to prevent false positives.
     */
    public static boolean isDuplicateQuestion(String q1, String q2, String a1, String a2) {
        if (q1 == null || q2 == null) return false;

        String normQ1 = normalizeQuestion(q1);
        String normQ2 = normalizeQuestion(q2);

        if (normQ1.isEmpty() || normQ2.isEmpty()) return false;

        String subj1 = extractQuestionSubject(q1);
        String subj2 = extractQuestionSubject(q2);

        Set<String> subjTokens1 = extractContentTokens(subj1);
        Set<String> subjTokens2 = extractContentTokens(subj2);

        // Analyze subject differences
        if (!subjTokens1.isEmpty() && !subjTokens2.isEmpty()) {
            Set<String> diff1 = new HashSet<>(subjTokens1);
            diff1.removeAll(subjTokens2);

            Set<String> diff2 = new HashSet<>(subjTokens2);
            diff2.removeAll(subjTokens1);

            // If both questions have distinct distinguishing concept tokens,
            // they are asking about different concepts!
            // Example: "primary key" vs "foreign key" (diff1=[primary], diff2=[foreign])
            // Example: "dbms" vs "database" (diff1=[dbms], diff2=[database])
            if (!diff1.isEmpty() && !diff2.isEmpty()) {
                return false;
            }

            // If one question asks about a specific sub-topic / mechanism not in the other
            // Example: "foreign key" vs "foreign key maintain referential integrity" (diff2=[maintain, referential, integrity])
            if (diff1.size() >= 2 || diff2.size() >= 2) {
                return false;
            }
        }

        // Both questions share the same subject concept (or one is an unelaborated version).
        // Now check Answer semantics:
        // Are the two cards testing the same knowledge / can they be answered with equivalent content?
        if (a1 != null && a2 != null && !a1.isBlank() && !a2.isBlank()) {
            Set<String> ansTokens1 = extractContentTokens(a1);
            Set<String> ansTokens2 = extractContentTokens(a2);

            if (!ansTokens1.isEmpty() && !ansTokens2.isEmpty()) {
                Set<String> commonAns = new HashSet<>(ansTokens1);
                commonAns.retainAll(ansTokens2);

                Set<String> unionAns = new HashSet<>(ansTokens1);
                unionAns.addAll(ansTokens2);

                double jaccardAns = (double) commonAns.size() / unionAns.size();
                double containmentAns = (double) commonAns.size() / Math.min(ansTokens1.size(), ansTokens2.size());

                // Answers convey essentially the same defining knowledge/content
                if ((containmentAns >= 0.40 || jaccardAns >= 0.30) && commonAns.size() >= 2) {
                    return true;
                }

                // If answers are substantially different (containment < 0.30 and few common tokens),
                // they test different knowledge or distinct aspects
                if (containmentAns < 0.30 && commonAns.size() < 2) {
                    return false;
                }
            }
        }

        // If answers are unavailable or inconclusive, check if normalized subjects match
        if (!subj1.isEmpty() && subj1.equals(subj2)) {
            return true;
        }

        return false;
    }

    public static String extractQuestionSubject(String question) {
        if (question == null) return "";
        String text = question.toLowerCase(Locale.ROOT).trim();

        // Remove punctuation
        text = text.replaceAll("[?.,!:;\"'()\\[\\]{}]", " ").replaceAll("\\s+", " ").trim();

        // 1. Strip English framing prefixes
        text = text.replaceFirst("^(what\\s+is\\s+(a|an|the)?\\s*)", "");
        text = text.replaceFirst("^(what\\s+are\\s+(the)?\\s*)", "");
        text = text.replaceFirst("^(what\\s+does\\s+(a|an|the)?\\s*)", "");
        text = text.replaceFirst("^(what\\s+is\\s+the\\s+(purpose|role|meaning|definition|concept)\\s+of\\s+(a|an|the)?\\s*)", "");
        text = text.replaceFirst("^(what\\s+is\\s+(meant\\s+by|the\\s+difference\\s+between)\\s*)", "");
        text = text.replaceFirst("^(how\\s+is\\s+(a|an|the)?\\s*)", "");
        text = text.replaceFirst("^(how\\s+does\\s+(a|an|the)?\\s*)", "");
        text = text.replaceFirst("^(how\\s+do\\s+) ", "");
        text = text.replaceFirst("^(how\\s+can\\s+(a|an|the)?\\s*)", "");
        text = text.replaceFirst("^(define\\s+(a|an|the)?\\s*)", "");
        text = text.replaceFirst("^(explain\\s+(a|an|the)?\\s*)", "");
        text = text.replaceFirst("^(describe\\s+(a|an|the)?\\s*)", "");
        text = text.replaceFirst("^(why\\s+is\\s+(a|an|the)?\\s*)", "");

        // 2. Strip Vietnamese framing prefixes
        text = text.replaceFirst("^(khái\\s+niệm\\s+(về)?\\s*)", "");
        text = text.replaceFirst("^(định\\s+nghĩa\\s+(về)?\\s*)", "");
        text = text.replaceFirst("^(vai\\s+trò\\s+của\\s*)", "");
        text = text.replaceFirst("^(mục\\s+đích\\s+của\\s*)", "");
        text = text.replaceFirst("^(ý\\s+nghĩa\\s+của\\s*)", "");
        text = text.replaceFirst("^(hãy\\s+cho\\s+biết\\s*)", "");
        text = text.replaceFirst("^(hãy\\s+nêu\\s*)", "");
        text = text.replaceFirst("^(giải\\s+thích\\s*)", "");
        text = text.replaceFirst("^(làm\\s+thế\\s+nào\\s+(để)?\\s*)", "");

        // 3. Strip trailing fluff in English & Vietnamese (longest patterns first)
        text = text.replaceFirst("\\s+\\b(how\\s+is\\s+it\\s+defined|is\\s+it\\s+defined|defined|work|works|used for|mean|means)\\b.*$", "");
        text = text.replaceFirst("\\s+\\b(được định nghĩa như thế nào|được hiểu như thế nào|được hiểu là gì|dùng để làm gì|hoạt động như thế nào|như thế nào|thế nào|là gì)\\b.*$", "");

        return text.trim();
    }

    public static String normalizeQuestion(String text) {
        if (text == null) return "";
        String s = extractQuestionSubject(text);
        return s.replaceAll("\\s+", " ").trim();
    }

    private static String stem(String w) {
        if (w == null || w.length() <= 3) return w;
        if (w.endsWith("ies") && w.length() > 4) {
            return w.substring(0, w.length() - 3) + "y";
        }
        if (w.endsWith("es") && w.length() > 4) {
            return w.substring(0, w.length() - 2);
        }
        if (w.endsWith("s") && !w.endsWith("ss") && w.length() > 3) {
            return w.substring(0, w.length() - 1);
        }
        if (w.endsWith("ing") && w.length() > 5) {
            return w.substring(0, w.length() - 3);
        }
        if (w.endsWith("ed") && w.length() > 4) {
            return w.substring(0, w.length() - 2);
        }
        return w;
    }

    public static Set<String> extractContentTokens(String text) {
        if (text == null) return Collections.emptySet();
        String lower = text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-zà-ỹ0-9\\s]", " ");
        String[] words = lower.split("\\s+");
        Set<String> tokens = new HashSet<>();
        for (String w : words) {
            String trimmed = w.trim();
            if (trimmed.length() >= 2 && !STOP_WORDS.contains(trimmed)) {
                tokens.add(stem(trimmed));
            }
        }
        return tokens;
    }
}
