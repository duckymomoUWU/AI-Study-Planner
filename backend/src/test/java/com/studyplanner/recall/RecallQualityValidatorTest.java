package com.studyplanner.recall;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecallQualityValidatorTest {

    @Test
    @DisplayName("evaluateDeck accepts high quality grounded cards without warnings")
    void testValidCardsAccepted() {
        String material = "Kiểm thử hộp đen là phương pháp kiểm thử dựa trên yêu cầu đặc tả mà không xem xét cấu trúc mã nguồn bên trong.";
        List<RecallTextExtractor.PageText> pages = List.of(new RecallTextExtractor.PageText(1, material));

        var card = new GeminiRecallService.GeneratedCard(
                "Kiểm thử hộp đen là phương pháp kiểm thử như thế nào?",
                "Là phương pháp kiểm thử dựa trên yêu cầu đặc tả mà không xem xét cấu trúc mã nguồn bên trong.",
                "Kiểm thử hộp đen là phương pháp kiểm thử dựa trên yêu cầu đặc tả mà không xem xét cấu trúc mã nguồn bên trong.",
                1
        );

        List<RecallQualityValidator.EvaluatedCard> evaluated = RecallQualityValidator.evaluateDeck(List.of(card), material, pages);
        assertEquals(1, evaluated.size());
        assertTrue(evaluated.get(0).passedQuality());
        assertNull(evaluated.get(0).warning());
    }

    @Test
    @DisplayName("evaluateDeck flags cards with non-existent quotes")
    void testNonExistentQuoteFlagged() {
        String material = "Nội dung học tập về hệ quản trị cơ sở dữ liệu quan hệ PostgreSQL.";
        var card = new GeminiRecallService.GeneratedCard(
                "PostgreSQL là gì?",
                "Là hệ quản trị cơ sở dữ liệu quan hệ.",
                "MongoDB là cơ sở dữ liệu NoSQL phổ biến.",
                null
        );

        List<RecallQualityValidator.EvaluatedCard> evaluated = RecallQualityValidator.evaluateDeck(List.of(card), material, List.of());
        assertEquals(1, evaluated.size());
        assertFalse(evaluated.get(0).passedQuality());
        assertTrue(evaluated.get(0).warning().contains("không tồn tại"));
    }

    @Test
    @DisplayName("evaluateDeck flags vague questions")
    void testVagueQuestionFlagged() {
        String material = "Chỉ mục B-Tree giúp tăng tốc độ tìm kiếm dữ liệu trên đĩa.";
        var card = new GeminiRecallService.GeneratedCard(
                "Nội dung này là gì?",
                "Chỉ mục B-Tree giúp tăng tốc độ tìm kiếm dữ liệu trên đĩa.",
                "Chỉ mục B-Tree giúp tăng tốc độ tìm kiếm dữ liệu trên đĩa.",
                null
        );

        List<RecallQualityValidator.EvaluatedCard> evaluated = RecallQualityValidator.evaluateDeck(List.of(card), material, List.of());
        assertEquals(1, evaluated.size());
        assertFalse(evaluated.get(0).passedQuality());
        assertTrue(evaluated.get(0).warning().contains("mơ hồ") || evaluated.get(0).warning().contains("ngắn"));
    }

    @Test
    @DisplayName("evaluateDeck detects duplicate questions testing the same knowledge")
    void testDuplicateQuestionDetected() {
        String material = "Kiểm thử hộp trắng phân tích cấu trúc mã nguồn và luồng điều khiển của chương trình.";
        var card1 = new GeminiRecallService.GeneratedCard(
                "Kiểm thử hộp trắng là gì?",
                "Phân tích cấu trúc mã nguồn và luồng điều khiển của chương trình.",
                "Kiểm thử hộp trắng phân tích cấu trúc mã nguồn và luồng điều khiển của chương trình.",
                null
        );
        var card2 = new GeminiRecallService.GeneratedCard(
                "Khái niệm kiểm thử hộp trắng được hiểu như thế nào?",
                "Kiểm tra dựa trên cấu trúc mã nguồn bên trong.",
                "Kiểm thử hộp trắng phân tích cấu trúc mã nguồn và luồng điều khiển của chương trình.",
                null
        );

        List<RecallQualityValidator.EvaluatedCard> evaluated = RecallQualityValidator.evaluateDeck(List.of(card1, card2), material, List.of());
        assertEquals(2, evaluated.size());
        assertTrue(evaluated.get(0).passedQuality());
        assertFalse(evaluated.get(1).passedQuality());
        assertTrue(evaluated.get(1).warning().contains("trùng lặp"));
    }

    @Test
    @DisplayName("evaluateDeck flags answers with semantic misalignment to quote")
    void testSemanticMisalignmentFlagged() {
        String material = "Kiểm thử đơn vị tập trung vào việc xác minh các hàm và lớp độc lập trong mã nguồn.";
        var card = new GeminiRecallService.GeneratedCard(
                "Mục đích của kiểm thử đơn vị là gì?",
                "Nhằm triển khai ứng dụng lên máy chủ đám mây tự động bằng Docker.",
                "Kiểm thử đơn vị tập trung vào việc xác minh các hàm và lớp độc lập trong mã nguồn.",
                null
        );

        List<RecallQualityValidator.EvaluatedCard> evaluated = RecallQualityValidator.evaluateDeck(List.of(card), material, List.of());
        assertEquals(1, evaluated.size());
        assertFalse(evaluated.get(0).passedQuality());
        assertTrue(evaluated.get(0).warning().contains("suy diễn") || evaluated.get(0).warning().contains("bám sát") || evaluated.get(0).warning().contains("không có căn cứ"));
    }

    @Test
    @DisplayName("validateCardQuality flags cards edited with off-topic knowledge outside source material")
    void testValidateCardQualityOffTopicKnowledgeDetected() {
        String dbMaterial = "Cơ sở dữ liệu quan hệ (RDBMS). Khóa chính (Primary Key) là một trường hoặc tập hợp các trường dùng để định danh duy nhất mỗi bản ghi trong bảng.";
        String oldQuote = "Khóa chính (Primary Key) là một trường hoặc tập hợp các trường dùng để định danh duy nhất mỗi bản ghi trong bảng.";

        // User intentionally edits card into off-topic knowledge about Cristiano Ronaldo
        String editedQuestion = "Cristiano Ronaldo sinh năm bao nhiêu?";
        String editedAnswer = "1985 tại Bồ Đào Nha";

        RecallQualityValidator.QualityResult result = RecallQualityValidator.validateCardQuality(
                editedQuestion, editedAnswer, oldQuote, dbMaterial, List.of()
        );

        assertFalse(result.passed());
        assertNotNull(result.warning());
        assertTrue(result.warning().contains("không có căn cứ trong tài liệu nguồn"),
                "Expected warning to mention ungrounded content, but was: " + result.warning());
    }

    @Test
    @DisplayName("validateCardQuality passes when edited question and answer are grounded in source material")
    void testValidateCardQualityValidCardPasses() {
        String dbMaterial = "Cơ sở dữ liệu quan hệ (RDBMS). Khóa chính (Primary Key) là một trường hoặc tập hợp các trường dùng để định danh duy nhất mỗi bản ghi trong bảng.";
        String quote = "Khóa chính (Primary Key) là một trường hoặc tập hợp các trường dùng để định danh duy nhất mỗi bản ghi trong bảng.";

        String editedQuestion = "Mục đích của khóa chính trong cơ sở dữ liệu quan hệ là gì?";
        String editedAnswer = "Dùng để định danh duy nhất mỗi bản ghi trong bảng.";

        RecallQualityValidator.QualityResult result = RecallQualityValidator.validateCardQuality(
                editedQuestion, editedAnswer, quote, dbMaterial, List.of()
        );

        assertTrue(result.passed(), "Expected card to pass validation, but failed with: " + result.warning());
        assertNull(result.warning());
    }

    @Test
    @DisplayName("validateCardQuality flags edited card when question duplicates another card in the deck")
    void testValidateCardQualityDuplicateCardFails() {
        String dbMaterial = "Khóa ngoại (Foreign Key) dùng để liên kết hai bảng với nhau trong cơ sở dữ liệu.";
        String quote = "Khóa ngoại (Foreign Key) dùng để liên kết hai bảng với nhau trong cơ sở dữ liệu.";

        var otherCard = new RecallDTOs.CardResponse(
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                "Khóa ngoại trong cơ sở dữ liệu là gì?",
                "Dùng để liên kết dữ liệu giữa hai bảng.",
                new RecallDTOs.CardSource(null, null, quote),
                false, 0, null, null, 0, null
        );

        String editedQuestion = "Khái niệm khóa ngoại trong cơ sở dữ liệu được hiểu là gì?";
        String editedAnswer = "Liên kết hai bảng với nhau.";

        RecallQualityValidator.QualityResult result = RecallQualityValidator.validateCardQuality(
                editedQuestion, editedAnswer, quote, dbMaterial, List.of(otherCard)
        );

        assertFalse(result.passed());
        assertNotNull(result.warning());
        assertTrue(result.warning().contains("trùng lặp"), "Expected duplicate warning, but was: " + result.warning());
    }

    @Test
    @DisplayName("validateCardQuality flags edited card when question is overly vague")
    void testValidateCardQualityVagueQuestionFails() {
        String dbMaterial = "Khóa chính định danh bản ghi.";
        String quote = "Khóa chính định danh bản ghi.";
        RecallQualityValidator.QualityResult result = RecallQualityValidator.validateCardQuality(
                "Đoạn văn này nói về cái gì?",
                "Nói về khóa chính.",
                quote, dbMaterial, List.of()
        );

        assertFalse(result.passed());
        assertNotNull(result.warning());
        assertTrue(result.warning().contains("mơ hồ") || result.warning().contains("ngắn"));
    }

    @Test
    @DisplayName("validateCardQuality flags edited card when answer is identical to question")
    void testValidateCardQualityAnswerEqualsQuestionFails() {
        String dbMaterial = "Khóa chính định danh bản ghi trong bảng.";
        String quote = "Khóa chính định danh bản ghi trong bảng.";
        RecallQualityValidator.QualityResult result = RecallQualityValidator.validateCardQuality(
                "Khóa chính dùng để làm gì trong bảng?",
                "Khóa chính dùng để làm gì trong bảng?",
                quote, dbMaterial, List.of()
        );

        assertFalse(result.passed());
        assertNotNull(result.warning());
        assertTrue(result.warning().contains("trùng lặp hoàn toàn"));
    }

    @Test
    @DisplayName("validateCardQuality flags answer with alien words or corrupted meaning even with correct quote (trai dat case)")
    void testValidateCardQualityCorruptedAnswerWithAlienWordFails() {
        String dbMaterial = "Cơ sở dữ liệu (Database) là tập hợp dữ liệu có tổ chức, được lưu trữ và quản lý nhằm giúp người dùng dễ dàng truy xuất, cập nhật và xử lý thông tin.";
        String quote = "Cơ sở dữ liệu (Database) là tập hợp dữ liệu có tổ chức, được lưu trữ và quản lý nhằm giúp người dùng dễ dàng truy xuất, cập nhật và xử lý thông tin.";

        String question = "Yếu tố nào giúp người dùng dễ dàng truy xuất, cập nhật và xử lý thông tin trong cơ sở dữ liệu?";
        // Answer contains correct quote prefix but substitutes "tổ chức" with alien nonsensical words "trái đất"
        String answer = "Đó là tập hợp dữ liệu có trái đất";

        RecallQualityValidator.QualityResult result = RecallQualityValidator.validateCardQuality(
                question, answer, quote, dbMaterial, List.of()
        );

        assertFalse(result.passed(), "Card with corrupted answer ('có trái đất') must NOT pass quality validation!");
        assertNotNull(result.warning());
        assertTrue(
                result.warning().contains("trái") ||
                result.warning().contains("đất") ||
                result.warning().contains("bất thường") ||
                result.warning().contains("thiếu ý quan trọng") ||
                result.warning().contains("sai lệch"),
                "Expected warning to explain alien words or missing core focus, but was: " + result.warning()
        );
    }

    @Test
    @DisplayName("validateCardQuality passes when answer accurately answers the question with core focus from quote")
    void testValidateCardQualityAccurateSemanticAnswerPasses() {
        String dbMaterial = "Cơ sở dữ liệu (Database) là tập hợp dữ liệu có tổ chức, được lưu trữ và quản lý nhằm giúp người dùng dễ dàng truy xuất, cập nhật và xử lý thông tin.";
        String quote = "Cơ sở dữ liệu (Database) là tập hợp dữ liệu có tổ chức, được lưu trữ và quản lý nhằm giúp người dùng dễ dàng truy xuất, cập nhật và xử lý thông tin.";

        String question = "Yếu tố nào giúp người dùng dễ dàng truy xuất, cập nhật và xử lý thông tin trong cơ sở dữ liệu?";
        String answer = "Đó là tính có tổ chức, được lưu trữ và quản lý của tập hợp dữ liệu.";

        RecallQualityValidator.QualityResult result = RecallQualityValidator.validateCardQuality(
                question, answer, quote, dbMaterial, List.of()
        );

        assertTrue(result.passed(), "Accurate semantic answer must pass, but failed with: " + result.warning());
        assertNull(result.warning());
    }

    @Test
    @DisplayName("validateCardQuality flags answer when quote is missing (uncertainty guard)")
    void testValidateCardQualityMissingQuoteFails() {
        String dbMaterial = "Khóa chính định danh bản ghi.";
        RecallQualityValidator.QualityResult result = RecallQualityValidator.validateCardQuality(
                "Khóa chính dùng để làm gì?",
                "Dùng để định danh bản ghi.",
                null, dbMaterial, List.of()
        );

        assertFalse(result.passed());
        assertNotNull(result.warning());
        assertTrue(result.warning().contains("thiếu trích dẫn nguồn"));
    }

    @Test
    @DisplayName("validateCardQuality flags answer when it contradicts the quote")
    void testValidateCardQualityContradictionFails() {
        String dbMaterial = "Khóa chính không được phép chứa giá trị NULL trong bảng.";
        String quote = "Khóa chính không được phép chứa giá trị NULL trong bảng.";

        String question = "Khóa chính có được phép chứa giá trị NULL không?";
        String answer = "Khóa chính được phép chứa giá trị NULL trong bảng.";

        RecallQualityValidator.QualityResult result = RecallQualityValidator.validateCardQuality(
                question, answer, quote, dbMaterial, List.of()
        );

        assertFalse(result.passed());
        assertNotNull(result.warning());
        assertTrue(result.warning().contains("mâu thuẫn"));
    }

    @Test
    @DisplayName("validateCardQuality flags answer when it misses the core focus answering the question")
    void testValidateCardQualityMissingCoreFocusFails() {
        String dbMaterial = "Chỉ mục B-Tree giúp tăng tốc độ tìm kiếm dữ liệu trên đĩa.";
        String quote = "Chỉ mục B-Tree giúp tăng tốc độ tìm kiếm dữ liệu trên đĩa.";

        String question = "Cấu trúc nào giúp tăng tốc độ tìm kiếm dữ liệu trên đĩa?";
        // Answer repeats question anchors but omits the actual answer "Chỉ mục B-Tree"
        String answer = "Giúp tăng tốc độ tìm kiếm dữ liệu trên đĩa nhanh hơn.";

        RecallQualityValidator.QualityResult result = RecallQualityValidator.validateCardQuality(
                question, answer, quote, dbMaterial, List.of()
        );

        assertFalse(result.passed());
        assertNotNull(result.warning());
        assertTrue(result.warning().contains("thiếu ý quan trọng") || result.warning().contains("trọng tâm"));
    }

    // =========================================================================
    // 6 Mandatory Test Cases for Duplicate Detection
    // =========================================================================

    @Test
    @DisplayName("Test 1: What is a primary key? vs What is a foreign key? -> NOT duplicate")
    void testMandatoryCase1PrimaryKeyVsForeignKeyNotDuplicate() {
        String q1 = "What is a primary key?";
        String a1 = "A field or combination of fields that uniquely identifies each record in a database table.";

        String q2 = "What is a foreign key?";
        String a2 = "A field or combination of fields in one table that references the primary key of another table.";

        boolean duplicate = RecallQualityValidator.isDuplicateQuestion(q1, q2, a1, a2);
        assertFalse(duplicate, "Primary key and Foreign key are different concepts and must not be marked as duplicate");

        // Verify in validateCardQuality as well
        String material = "In relational databases, a primary key is a field or combination of fields that uniquely identifies each record in a database table. A foreign key is a field or combination of fields in one table that references the primary key of another table.";
        String quote2 = "A foreign key is a field or combination of fields in one table that references the primary key of another table.";
        var card1Response = new RecallDTOs.CardResponse(
                UUID.randomUUID(), UUID.randomUUID(), q1, a1,
                new RecallDTOs.CardSource(null, 1, "a primary key is a field or combination of fields that uniquely identifies each record in a database table"),
                false, 0, null, null, 0, null
        );

        RecallQualityValidator.QualityResult result = RecallQualityValidator.validateCardQuality(
                q2, a2, quote2, material, List.of(card1Response)
        );
        assertTrue(result.passed(), "Card 2 must pass validation and not be flagged as duplicate: " + result.warning());
        assertNull(result.warning());
    }

    @Test
    @DisplayName("Test 2: What is a primary key? vs What is the purpose of a primary key? -> DUPLICATE")
    void testMandatoryCase2PrimaryKeyVsPurposeDuplicate() {
        String q1 = "What is a primary key?";
        String a1 = "A field or combination of fields that uniquely identifies each record in a database table.";

        String q2 = "What is the purpose of a primary key?";
        String a2 = "To uniquely identify each record in a database table.";

        boolean duplicate = RecallQualityValidator.isDuplicateQuestion(q1, q2, a1, a2);
        assertTrue(duplicate, "Both cards test the same knowledge and defining purpose of a primary key; must be marked as duplicate");

        String material = "In relational databases, a primary key uniquely identifies each record in a table.";
        String quote2 = "a primary key uniquely identifies each record in a table.";
        var card1Response = new RecallDTOs.CardResponse(
                UUID.randomUUID(), UUID.randomUUID(), q1, a1,
                new RecallDTOs.CardSource(null, 1, quote2),
                false, 0, null, null, 0, null
        );

        RecallQualityValidator.QualityResult result = RecallQualityValidator.validateCardQuality(
                q2, a2, quote2, material, List.of(card1Response)
        );
        assertFalse(result.passed());
        assertNotNull(result.warning());
        assertTrue(result.warning().contains("trùng lặp"));
    }

    @Test
    @DisplayName("Test 3: What is a foreign key? vs How does a foreign key maintain referential integrity? -> NOT duplicate")
    void testMandatoryCase3ForeignKeyVsReferentialIntegrityNotDuplicate() {
        String q1 = "What is a foreign key?";
        String a1 = "A field or combination of fields in one table that references the primary key of another table.";

        String q2 = "How does a foreign key maintain referential integrity?";
        String a2 = "By ensuring that a value in the referencing table matches a valid value in the referenced primary key table.";

        boolean duplicate = RecallQualityValidator.isDuplicateQuestion(q1, q2, a1, a2);
        assertFalse(duplicate, "Definition question vs mechanism of maintaining referential integrity test different knowledge and must not be marked as duplicate");
    }

    @Test
    @DisplayName("Test 4: What is a primary key? vs How is a primary key defined? -> DUPLICATE")
    void testMandatoryCase4PrimaryKeyVsHowDefinedDuplicate() {
        String q1 = "What is a primary key?";
        String a1 = "A field that uniquely identifies each record in a table.";

        String q2 = "How is a primary key defined?";
        String a2 = "It is defined as a field or set of fields that uniquely identifies each record in a table.";

        boolean duplicate = RecallQualityValidator.isDuplicateQuestion(q1, q2, a1, a2);
        assertTrue(duplicate, "What is vs How is defined for the same concept with equivalent answers must be marked as duplicate");
    }

    @Test
    @DisplayName("Test 5: What is a DBMS? vs What is a database? -> NOT duplicate")
    void testMandatoryCase5DbmsVsDatabaseNotDuplicate() {
        String q1 = "What is a DBMS?";
        String a1 = "A software system used to manage, store, and retrieve data from databases.";

        String q2 = "What is a database?";
        String a2 = "An organized collection of data stored and accessed electronically.";

        boolean duplicate = RecallQualityValidator.isDuplicateQuestion(q1, q2, a1, a2);
        assertFalse(duplicate, "DBMS and database are distinct concepts and must not be marked as duplicate");
    }

    @Test
    @DisplayName("Test 6: What is a database? vs Define a database. -> DUPLICATE")
    void testMandatoryCase6DatabaseVsDefineDatabaseDuplicate() {
        String q1 = "What is a database?";
        String a1 = "An organized collection of data.";

        String q2 = "Define a database.";
        String a2 = "A database is an organized collection of structured data.";

        boolean duplicate = RecallQualityValidator.isDuplicateQuestion(q1, q2, a1, a2);
        assertTrue(duplicate, "What is a database vs Define a database with equivalent answers must be marked as duplicate");
    }
}

