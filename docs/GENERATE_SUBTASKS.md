# Generate Sub-tasks — Hướng dẫn PO và Developers

> Quy trình đề xuất. **AI chỉ tạo bản nháp; Developers review và tự lập kế hoạch thực hiện.** Không tự tạo issue Jira nếu chưa được yêu cầu.

## Trước khi sinh sub-task
1. Mở user story trên **Jira ASP** và lấy parent key thật.
2. Đọc AC trên Jira và đối chiếu `requirements.md` (tài liệu tham chiếu trong repo); Jira là nguồn chính cho Product Backlog và AC đã duyệt.
3. Kiểm tra code hiện tại trước khi đề xuất phạm vi kỹ thuật.
4. Nếu Jira và `requirements.md` mâu thuẫn hoặc thiếu quyết định nghiệp vụ, dừng và hỏi PO; không tạo sub-task dựa trên giả định.
5. Chọn một luồng nhỏ có thể demo. AI đề xuất số lượng sub-task phù hợp với độ phức tạp, không bắt buộc số lượng cố định.

## Prompt để dùng với Codex / AI

```text
Bạn đang hỗ trợ Developers của AI Study Planner.
Đọc AGENTS.md, requirements.md, docs/WORKING_AGREEMENT.md,
docs/GENERATE_SUBTASKS.md và Jira Story [MÃ JIRA THẬT] được cung cấp.
Jira là nguồn chính cho User Story và Acceptance Criteria đã được duyệt.
Kiểm tra code repo hiện tại trước khi chia việc.
Hãy đề xuất các sub-task triển khai có thứ tự phụ thuộc, không tự đăng Jira.

Với mỗi sub-task, trả về:
- Title: ngắn, bắt đầu bằng kết quả cần có;
- Description: mục đích và việc cụ thể;
- Parent: mã story Jira thật;
- Phạm vi tệp / module dự kiến (nếu đã kiểm chứng);
- Phụ thuộc;
- AC nào được chứng minh;
- Kiểm chứng / cách demo có thể quan sát;
- Câu hỏi chờ PO (nếu có).

Không bịa mã Jira, người thực hiện, ước lượng, test đã chạy, endpoint đã tồn tại,
hoặc quyết định sản phẩm. Tách phần đã chốt khỏi đề xuất và câu hỏi mở.
Nếu Jira và requirements.md khác nhau, dừng ở phần bị ảnh hưởng và hỏi PO.
```

## Ví dụ đang có trên Jira — ASP-9: Tạo mục tiêu ôn tập

[ASP-9 — US-01: Tạo mục tiêu ôn tập](https://ai-study-planner-nhom.atlassian.net/browse/ASP-9) thuộc Epic ASP-5, Priority High, trạng thái To Do và hiện chưa có sub-task.

**AC hiện tại trên Jira:**
- Bắt buộc có môn, ngày thi, thời gian mỗi ngày.
- Ngày thi không ở quá khứ; nếu sai thì báo lỗi.
- Mục tiêu đã tạo được lưu và hiển thị lại.

**Chênh lệch cần PO làm rõ trước khi tạo sub-task:** `requirements.md` trong repo còn nêu chủ đề, ngày bắt đầu, khoảng lập lịch tối đa 90 ngày, thời gian 15–480 phút/ngày và quy tắc khi sửa mục tiêu ảnh hưởng công việc đã có. PO cần quyết định các rule này thuộc AC của ASP-9, story khác hay chưa nằm trong MVP. Không tự bổ sung vào ticket.

Sau khi PO làm rõ, Developers có thể cân nhắc các lát cắt sau (đây là **đề xuất**, chưa tạo trên Jira):

| Thứ tự | Sub-task đề xuất | Kết quả / kiểm chứng |
| --- | --- | --- |
| 1 | Rà soát Goal schema và chốt API contract | Đối chiếu migration hiện có, xác nhận dữ liệu và hợp đồng API cần thiết |
| 2 | Backend: tạo và lấy mục tiêu của tài khoản hiện tại | Kiểm tra dữ liệu được lưu và chỉ người sở hữu truy cập được |
| 3 | Backend: validate dữ liệu mục tiêu | Kiểm tra các rule đã được PO chốt ở Jira |
| 4 | Frontend: form tạo và danh sách mục tiêu | Nhập dữ liệu, hiển thị lỗi rõ ràng, tải lại vẫn thấy mục tiêu |
| 5 | Test tích hợp và demo luồng tạo mục tiêu | Chứng minh AC bằng test/đầu ra demo; ghi rõ phần chưa chạy |

**Phụ thuộc:** story đăng nhập/phân quyền ASP-12; Developers cần xác định phụ thuộc triển khai thực tế khi refinement. Không giả định auth hoặc API nghiệp vụ đã có sẵn trong code.

## Đưa vào Jira sau khi nhóm review
- Mở story cha trên Jira, chọn **Add / Create subtask** (tên nút tùy giao diện).
- Copy Title/Description cho từng sub-task, đặt đúng Parent và liên kết AC.
- Developers thống nhất thứ tự, người làm, estimate theo Sprint Planning; PO không dùng AI để tự giao việc.
- Dùng Jira key thật trong branch/commit/PR; không tạo bản duplicate trên GitHub Issues nếu Jira là nguồn Product Backlog chính.

## Không nên làm
- Không sinh task chỉ theo từng file hoặc từng thành viên mà bỏ qua kết quả sử dụng được.
- Không coi “đã tạo sub-task” là “đã triển khai”.
- Không tự giả định React/Spring Boot hiện đã có auth, CRUD hay AI endpoint. Kiểm chứng repo trước.
