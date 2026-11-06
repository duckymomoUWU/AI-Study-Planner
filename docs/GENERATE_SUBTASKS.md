# Generate Sub-tasks — Hướng dẫn PO và Developers

> Quy trình đề xuất. **AI chỉ tạo bản nháp; Developers review và tự lập kế hoạch thực hiện.** Không tự tạo issue Jira nếu chưa được yêu cầu.

## Trước khi sinh sub-task
1. Mở User Story trên **Jira ASP** và lấy parent key thật.
2. Đọc AC trên Jira và đối chiếu `requirements.md` (tài liệu tham chiếu trong repo); Jira là nguồn chính cho Product Backlog và AC đã duyệt.
3. Kiểm tra code hiện tại trước khi đề xuất phạm vi kỹ thuật.
4. Nếu Jira và `requirements.md` có mâu thuẫn mới hoặc thiếu quyết định nghiệp vụ, dừng ở phần bị ảnh hưởng và hỏi PO; không tạo sub-task dựa trên giả định.
5. AI đề xuất lát cắt công việc và cách kiểm chứng; Developers review, điều chỉnh phụ thuộc/estimate và tự thống nhất kế hoạch. Không bắt buộc số lượng sub-task cố định.

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
Nếu Jira và requirements.md có mâu thuẫn mới, nêu rõ phần lệch và chờ PO quyết định.
```

## Ví dụ đang có trên Jira — ASP-9: Quản lý mục tiêu ôn tập

[ASP-9 — US-01: Tạo, xem, sửa và xóa mục tiêu ôn tập](https://ai-study-planner-nhom.atlassian.net/browse/ASP-9) thuộc Epic ASP-5, Priority High, trạng thái To Do. PO đã chốt đầy đủ quy tắc nghiệp vụ trên Jira; story hiện chưa có sub-task.

**Phạm vi đã chốt trên Jira:**
- Tạo/xem/sửa/xóa mục tiêu với môn học, chủ đề, ngày bắt đầu, ngày thi và số phút học mỗi ngày.
- Môn học/chủ đề không rỗng sau trim; ngày thi không ở quá khứ và phải sau ngày bắt đầu; khoảng ngày tối đa 90 ngày; ngân sách 15–480 phút/ngày.
- Server validate và kiểm tra ownership; dữ liệu được lưu và hiển thị lại sau khi tải trang.
- Khi sửa mục tiêu, kiểm tra các công việc đã có để không vượt ngày thi/ngân sách mới; từ chối lưu và thông báo nếu vi phạm.
- Xóa cần xác nhận; sau đó xóa mục tiêu cùng công việc và bản nháp kế hoạch liên quan.
- Không xếp công việc vào ngày thi/sau ngày thi; tổng thời lượng trong ngày không vượt ngân sách của mục tiêu; báo số phút thiếu nếu không thể xếp vừa.
- MVP tính ngân sách riêng theo từng mục tiêu, chưa tối ưu đồng thời nhiều môn.

**Các lát cắt kỹ thuật dưới đây chỉ là đề xuất để Developers refinement, chưa tạo trên Jira và không phải phân công:**

| Thứ tự | Sub-task đề xuất | Kết quả / kiểm chứng |
| --- | --- | --- |
| 1 | Rà soát data model và quan hệ Goal–Task–Plan Draft | Kiểm tra schema/migration hiện tại; đề xuất contract dữ liệu/API phù hợp với code thực tế |
| 2 | Backend: CRUD mục tiêu và kiểm tra ownership | Tạo/xem/sửa/xóa chỉ trên dữ liệu của tài khoản hiện tại |
| 3 | Backend: validation và kiểm tra giới hạn khi sửa mục tiêu | Kiểm tra môn/chủ đề, ngày, khoảng 90 ngày, phút/ngày và các task hiện có |
| 4 | Frontend: giao diện tạo, xem và sửa mục tiêu | Form, danh sách/chi tiết, hiển thị lỗi validation theo trường |
| 5 | Frontend/backend: xác nhận xóa và xử lý dữ liệu liên quan | Xác nhận trước khi xóa; kiểm chứng task và draft liên quan được xử lý đúng |
| 6 | Integration/security tests và demo ASP-9 | Kiểm tra persistence, boundary values, ngân sách, ngày thi, ownership và delete behavior |

**Phụ thuộc:** đăng nhập/ownership ở ASP-12. Các story ASP-10, ASP-11, ASP-13 và ASP-14 phải tuân thủ giới hạn ngày thi/ngân sách; Developers cần refinement để xác định phần enforcement dùng chung và tránh triển khai trùng. Không giả định auth, API hoặc module nghiệp vụ đã có sẵn trong code.

## Đưa vào Jira sau khi Developers review
- Mở story cha trên Jira, chọn **Add / Create subtask** (tên nút tùy giao diện).
- Chỉ tạo những sub-task mà Developers đã thống nhất sau refinement; copy Title/Description và đặt đúng Parent.
- Developers thống nhất thứ tự, người thực hiện và estimate theo Sprint Planning; PO không dùng AI để tự giao việc.
- Dùng Jira key thật trong branch/commit/PR; không tạo bản duplicate trên GitHub Issues nếu Jira là nguồn Product Backlog chính.

## Không nên làm
- Không sinh task chỉ theo từng file hoặc từng thành viên mà bỏ qua kết quả sử dụng được.
- Không coi “đã tạo sub-task” là “đã triển khai”.
- Không tự giả định React/Spring Boot đã có auth, CRUD hay AI endpoint. Kiểm chứng repo trước.
