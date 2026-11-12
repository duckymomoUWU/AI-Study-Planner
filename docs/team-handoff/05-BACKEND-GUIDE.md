# Backend coding conventions và dữ liệu

## 1. Nền tảng và mẫu thật

Giữ Java 21, Spring Boot 3.5.x, Maven, JdbcTemplate/NamedParameterJdbcTemplate, PostgreSQL và Flyway. Không đổi package gốc `com.studyplanner`, không thêm JPA/JWT/framework mới để làm ba tính năng này.

Đọc [backend API rules](https://github.com/duckymomoUWU/AI-Study-Planner/blob/29dbf6d1c7e4f0cacdf9524aa905f1789bfb2b62/docs/backend-api-rules.md), [requirements](https://github.com/duckymomoUWU/AI-Study-Planner/blob/29dbf6d1c7e4f0cacdf9524aa905f1789bfb2b62/requirements.md), [Harness](https://github.com/duckymomoUWU/AI-Study-Planner/blob/29dbf6d1c7e4f0cacdf9524aa905f1789bfb2b62/.agents/harness/README.md) và [setup](https://github.com/duckymomoUWU/AI-Study-Planner/blob/29dbf6d1c7e4f0cacdf9524aa905f1789bfb2b62/setup.md). Tài liệu cũ còn nhãn future hoặc rule startDate cũ: đối chiếu code và contract domain cập nhật, không copy nguyên đoạn lỗi thời vào feature mới. API của đợt này nằm ở file 03 và phải được duyệt D1.

Mẫu đang chạy:

- [TaskController](https://github.com/duckymomoUWU/AI-Study-Planner/blob/29dbf6d1c7e4f0cacdf9524aa905f1789bfb2b62/backend/src/main/java/com/studyplanner/tasks/TaskController.java): principal → owner, DTO/envelope, controller mỏng.
- [TaskService](https://github.com/duckymomoUWU/AI-Study-Planner/blob/29dbf6d1c7e4f0cacdf9524aa905f1789bfb2b62/backend/src/main/java/com/studyplanner/tasks/TaskService.java): service transaction và version.
- [StudyBudgetService](https://github.com/duckymomoUWU/AI-Study-Planner/blob/29dbf6d1c7e4f0cacdf9524aa905f1789bfb2b62/backend/src/main/java/com/studyplanner/goals/StudyBudgetService.java): owner lock `FOR NO KEY UPDATE`; đọc kỹ thứ tự khóa trước khi thêm session concurrency.
- [ApiResponse](https://github.com/duckymomoUWU/AI-Study-Planner/blob/29dbf6d1c7e4f0cacdf9524aa905f1789bfb2b62/backend/src/main/java/com/studyplanner/api/ApiResponse.java), [ApiExceptionHandler](https://github.com/duckymomoUWU/AI-Study-Planner/blob/29dbf6d1c7e4f0cacdf9524aa905f1789bfb2b62/backend/src/main/java/com/studyplanner/api/ApiExceptionHandler.java), [RequestValidationException](https://github.com/duckymomoUWU/AI-Study-Planner/blob/29dbf6d1c7e4f0cacdf9524aa905f1789bfb2b62/backend/src/main/java/com/studyplanner/api/RequestValidationException.java).
- [SyllabusOutline](https://github.com/duckymomoUWU/AI-Study-Planner/blob/29dbf6d1c7e4f0cacdf9524aa905f1789bfb2b62/backend/src/main/java/com/studyplanner/goals/SyllabusOutline.java): public PDF text parser có thể tái sử dụng; **đọc raw pages cho câu hỏi**, không chỉ teachingPages đã lọc theo đề cương.
- [GeminiPlanService](https://github.com/duckymomoUWU/AI-Study-Planner/blob/29dbf6d1c7e4f0cacdf9524aa905f1789bfb2b62/backend/src/main/java/com/studyplanner/goals/GeminiPlanService.java): cấu hình/provider boundary tham khảo. Recall cần adapter/schema riêng, không thay prompt planner đang dùng.

## 2. Cấu trúc mới đề xuất

```text
backend/src/main/java/com/studyplanner/
  sessions/StudySessionController.java
           StudySessionService.java
           StudySessionRepository.java
           StudySessionRequest.java
           StudySessionPolicy.java
           StudySessionResponse.java
  recall/RecallMaterialController.java
         RecallDeckController.java
         RecallReviewController.java
         RecallService.java
         RecallRepository.java
         RecallRequest.java
         RecallResponse.java
         GeminiRecallService.java
backend/src/test/java/com/studyplanner/sessions/...
backend/src/test/java/com/studyplanner/recall/...
```

Đây là gợi ý module, không bắt buộc số class. Đừng tạo interface/factory chỉ để khớp tên sơ đồ. Controller parse request và gọi service; business validation/ownership phải ở server, không chỉ frontend.

## 3. Mô hình dữ liệu v1 đề xuất

### V8 — Người 2

- `study_sessions`: id, user_id, task_id nullable, goal_id nullable, task_title_snapshot, subject_snapshot, status, planned_focus_seconds=1500, accumulated_focus_seconds, started_at, last_resumed_at nullable, ended_at nullable, version.
- FK user cascade theo account; task/goal `ON DELETE SET NULL` để không xóa lịch sử. Lưu snapshot tên ngay lúc start, không phụ thuộc đối tượng còn tồn tại để render history.
- Check seconds trong 0..1500, status hợp lệ; partial unique index theo user khi status RUNNING/PAUSED.
- `study_session_receipts`: owner + request key duy nhất, endpoint/action fingerprint, response snapshot. Ghi receipt cùng transaction tạo/chuyển phiên.
- Không tăng `study_goals.version` chỉ vì học hay pause: goal/task kế hoạch không đổi. Không ghi cộng dồn vào task. Tổng học query từ COMPLETED sessions để không cộng hai lần.

### V9 — Người 3, sau khi V8 đã có trên branch

- `recall_materials`: owner/goal, title/kind/fileName, text/page payload đã trích, created_at. Giới hạn ở service trước khi persist.
- `recall_decks`: owner/goal/material, title, DRAFT/ACTIVE, version, created_at.
- `recall_cards`: deck, question, answer, page/quote nguồn, edited_by_user, version, sort_order.
- `recall_reviews`: owner/card, rating, timestamp. Progress lấy last rating theo reviewed_at + ID ổn định; không coi count review là count thẻ.
- `recall_receipts`: owner/key/fingerprint/response để generation/review idempotent. Chọn phạm vi key domain rõ; không dùng chung key tạo deck và review.
- Goal → material/deck/card/review cascade. Các FK deck/material/goal phải khớp cùng owner/parent do query/service và ràng buộc khả thi kiểm tra; chỉ FK đơn lẻ không chứng minh quyền.
- Review/activate/sửa card giữ transaction ngắn và thứ tự khóa ổn định. Sửa card tăng version deck trong cùng transaction với card.

V8/V9 phụ thuộc main lúc bắt đầu. Không sửa migration đã chạy, không `clean` database app. Code repository không tự tạo bảng khi controller được gọi.

## 4. Transaction, race và API errors

- Owner từ principal. SQL `WHERE user_id=?` hoặc join parent owner cho cả đọc/ghi; không load bất kỳ ID rồi kiểm tra owner ở client.
- Start session khóa owner trước khi kiểm tra active; vẫn cần unique index để chặn race. Dùng cùng trật tự owner → session/goal/task khi thực sự cần khóa; tránh khóa task rồi quay lại owner.
- Khi dùng `NO KEY UPDATE`, hiểu lý do tương thích FK của receipt độc lập; không đổi sang FOR UPDATE tùy tiện. Idempotency receipt của feature mới nên cùng transaction, không copy REQUIRES_NEW của Calendar nếu không cần.
- Tiêm Clock vào policy/service; tính seconds trên server. Unit test dùng Clock cố định/tiến thời gian có kiểm soát, không Thread.sleep để kiểm tra đồng hồ.
- Bắt malformed JSON/sai enum/field lạ thành 400; lỗi trạng thái/version thành 409; nguồn không đọc được thành 422. Lỗi không được ghi nửa session/deck.
- Exception domain mới phải được map về envelope hiện có. Người 2 điều phối chỉnh ApiExceptionHandler; Người 3 cung cấp code/HTTP/message. Không trả raw exception hoặc tự thêm format lỗi riêng.

## 5. Gemini/tài liệu

- Dùng GEMINI_API_KEY/GEMINI_MODEL backend đang có; không đổi model hoặc chia key frontend. Với unit/CI mock provider, không gọi API thật để làm test xanh.
- Parse và giới hạn PDF/text trước; mọi nội dung tài liệu là untrusted data. Không để câu “bỏ qua hướng dẫn” trong PDF điều khiển prompt.
- Output schema có question/answer/source(page,quote); kiểm tra số lượng, độ dài, trùng lặp và đối chiếu quote trước persist. Không tự bịa thẻ khi thiếu nguồn.
- Remote call không giữ DB connection/transaction. Sau response, kiểm tra nguồn/owner vẫn tồn tại, ghi deck/cards/receipt nguyên tử. Timeout/rate limit trả rõ để UI giữ nguồn và retry.
- Không log prompt/nội dung PDF, token, toàn bộ JSON provider hoặc dữ liệu cá nhân. Log code kỹ thuật/số lượng nếu cần.

## 6. Mẫu để copy và test

[StudySessionController.java.example](templates/StudySessionController.java.example) và [StudySessionService.java.example](templates/StudySessionService.java.example) là khung hợp đồng lớp, **chưa có implementation**. Không chép vào main rồi trả thành công giả từ TODO.

Test tối thiểu: policy clock/state, MockMvc auth/CSRF/validation, PostgreSQL owner/unique/version/receipt/rollback/cascade. Test fixtures nằm trong DB **study_planner_test**. Nếu thêm flag sessions.integration/recall.integration, ghi hướng dẫn và cập nhật workflow để thật sự chạy nó, không chỉ tạo annotation rồi để toàn bộ skipped.

Maven verify đóng gói, có thể khóa JAR nếu đang chạy trực tiếp từ target trên Windows; kiểm thử ở checkout/DB test riêng, không kill mọi tiến trình Java. Không thay `.env` của PO để chạy tests.
