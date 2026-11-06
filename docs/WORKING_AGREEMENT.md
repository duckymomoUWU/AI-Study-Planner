# Working Agreement — AI Study Planner

> **Bản đề xuất, chưa được Scrum Team thông qua.** PO đưa ra để cả nhóm trao đổi tại buổi họp; các mục có **[Cần chốt]** không được coi là cam kết đã có.

## 1. Mục đích
Cùng thống nhất cách trao đổi, làm việc với Jira/GitHub, review code, xử lý trở ngại và sử dụng AI. Working Agreement là quy ước làm việc của **toàn Scrum Team**, không phải nội quy do PO đơn phương áp đặt.

## 2. Vai trò
- **PO:** truyền đạt Product Goal, làm rõ yêu cầu/Acceptance Criteria (AC), sắp thứ tự Product Backlog, trả lời câu hỏi về giá trị và phạm vi; chấp thuận thay đổi hành vi sản phẩm.
- **Scrum Master:** hỗ trợ Scrum Team thống nhất và tuân thủ cách làm việc, xử lý trở ngại và cải tiến quy trình.
- **Developers:** cùng tạo Sprint Backlog, tự chia và nhận việc, thiết kế giải pháp, kiểm thử, review, chịu trách nhiệm chất lượng Increment. PO không tự phân công kỹ thuật thay Developers.

## 3. Công cụ và nguồn thông tin
- **Jira ASP:** đề xuất làm **nguồn chính cho Product Backlog, user story, sub-task, trạng thái và phân Sprint**. [Cần chốt: xác nhận mọi thành viên có quyền truy cập và đã chuyển các story cần thiết từ GitHub Issues.]
- **`requirements.md`:** nguồn trong repo cho **quy tắc sản phẩm đã được PO xác nhận**. Nếu Jira và tài liệu này mâu thuẫn, đánh dấu cần PO quyết định, không tự chọn phiên bản có lợi cho code.
- **GitHub:** code, branch, pull request, kết quả CI và lịch sử triển khai. Mọi thay đổi hành vi phải được kiểm tra với AC và `requirements.md`.
- **`docs/REQUIREMENTS_CODE_SYNC.md`:** quy trình đề xuất đồng bộ yêu cầu ↔ code. Liên kết phát triển GitHub–Jira không tự viết code.

## 4. Giao tiếp và phối hợp
- **[Cần chốt]** Kênh nhóm chính: [điền]; mọi quyết định thay đổi yêu cầu phải được ghi lại ở Jira và, nếu ảnh hưởng quy tắc chung, ở `requirements.md`.
- **[Cần chốt]** Lịch Daily Scrum: [điền]; tối đa 15 phút khi dùng Scrum. Tập trung vào tiến độ Sprint Goal và kế hoạch phối hợp, không biến thành báo cáo riêng cho PO.
- Khi vướng yêu cầu thiếu rõ ràng, dev bình luận vào story và gắn PO; PO phản hồi sớm, không tự suy đoán hành vi.
- **[Đề xuất cần chốt]** Thông báo blocker ngay khi nhận ra; người được yêu cầu review cố gắng phản hồi trong 24 giờ học/làm việc (không coi đây là SLA đã cam kết).

## 5. Làm việc với story và sub-task
1. PO cung cấp **User Story, giá trị, AC, phạm vi, độ ưu tiên, phụ thuộc**; Developers tham gia refinement.
2. Có thể dùng AI **đề xuất** sub-task; Developers kiểm tra lại độ vừa sức, thứ tự, phụ thuộc, cách chứng minh kết quả và nhận việc.
3. Sub-task nên tạo ra **đầu ra kiểm tra được** (giao diện, API, validation, test...), tránh chỉ ghi “làm backend” hay chia việc theo từng người.
4. **[Cần chốt]** Độ dài Sprint, ước lượng và capacity: thống nhất tại Sprint Planning.
5. Không tự chuyển một đề xuất AI thành yêu cầu đã được PO duyệt.

## 6. Branch, Pull Request và chất lượng
- **Đề xuất** tên branch: `ASP-<ID>-mo-ta-ngan` (ví dụ minh họa: `ASP-123-goal-crud`; thay bằng mã issue thật).
- **Đề xuất** PR title/commit ghi mã Jira như `ASP-123` để liên kết trên Jira khi tích hợp GitHub for Atlassian đã được bật.
- PR mô tả: story/AC liên quan, thay đổi code, tác động tới requirements, lệnh test **đã thực sự chạy** và bằng chứng, giới hạn chưa test, bước demo.
- **Đề xuất cần nhóm thống nhất:** ít nhất 1 người khác review PR; không tự merge code nghiệp vụ chưa đủ AC. Không đẩy secrets, mật khẩu, API key lên repo.
- Definition of Done: tham chiếu bản DoD đã thống nhất của nhóm; một checklist trong PR **không chứng minh** test đã pass.
- CI hiện mới có frontend; backend và end-to-end cần xác minh riêng; không gọi build thành “test nghiệp vụ đã đạt”.

## 7. Thay đổi yêu cầu và xung đột
- PO đổi yêu cầu: ghi rõ lý do, AC trước/sau, tác động Sprint; tạo yêu cầu thay đổi code/test qua PR. Không tự sửa `main` chỉ vì mô tả Jira đổi.
- Developer phát hiện code cần thay đổi hành vi: đề xuất cập nhật story/requirements, **chờ PO quyết định**, rồi thực hiện/thống nhất trong PR tương ứng.
- Khi requirements, Jira, code hoặc test lệch nhau, dùng trạng thái **Needs PO decision**; không tự giả định code hiện tại là “đúng”.
- Không cam kết tự động merge hoặc tự động sửa yêu cầu bằng AI.

## 8. Chấp thuận Working Agreement
| Vai trò | Đại diện | Ngày xác nhận |
| --- | --- | --- |
| PO | [điền] | [điền] |
| Scrum Master | [điền] | [điền] |
| Developers | [các thành viên xác nhận] | [điền] |

Thống nhất lại sau Sprint Retrospective khi có lý do thực tế cần điều chỉnh.
