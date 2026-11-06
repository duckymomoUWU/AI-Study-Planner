# Generate Sub-tasks — Hướng dẫn PO và Developers

> Quy trình đề xuất. **AI chỉ tạo bản nháp; Developers tự lập kế hoạch thực hiện, PO làm rõ giá trị/AC.** Không có bằng chứng rằng Jira đã được kết nối hay các sub-task dưới đây đã được tạo trên Jira.

## Trước khi sinh sub-task
1. Mở user story thật trên Jira ASP và lấy **mã issue chính xác** (không dùng số GitHub Issue làm mã Jira).
2. Đọc `requirements.md`, AC và phụ thuộc trong story; kiểm tra code hiện tại thay vì giả định các API đã có.
3. Hỏi PO nếu còn thiếu quyết định sản phẩm. Không tạo sub-task cho phần chưa được chốt.
4. Chọn một luồng nhỏ có thể demo. AI hỗ trợ gợi ý 3–7 sub-task tùy độ phức tạp, không bắt buộc một số lượng cố định.

## Prompt để dùng với Codex / AI

```text
Bạn đang hỗ trợ Developers của AI Study Planner.
Đọc AGENTS.md, requirements.md, docs/WORKING_AGREEMENT.md,
docs/GENERATE_SUBTASKS.md và Jira story [MÃ JIRA THẬT] được cung cấp.
Kiểm tra code repo hiện tại trước khi chia việc.
Hãy đề xuất các sub-task triển khai có thứ tự phụ thuộc, không tự đăng Jira.

Với mỗi sub-task, trả về:
- Title: ngắn, bắt đầu bằng kết quả cần có;
- Description: mục đích và việc cụ thể;
- Parent: mã story Jira;
- Phạm vi tệp / module dự kiến (nếu đã kiểm chứng);
- Phụ thuộc;
- AC nào được chứng minh;
- Kiểm chứng / cách demo có thể quan sát;
- Câu hỏi chờ PO (nếu có).

Không bịa mã Jira, người thực hiện, ước lượng, test đã chạy, endpoint đã tồn tại,
hoặc quyết định sản phẩm. Tách phần đề xuất khỏi phần đã được chốt.
```

## Ví dụ — Story tạo mục tiêu ôn tập

Nguồn tham khảo trong GitHub: [Issue #2](https://github.com/duckymomoUWU/AI-Study-Planner/issues/2). **Parent Jira ID: [điền mã ASP thật]**. Ví dụ này là bản nháp để Developers refinement.

| Thứ tự | Sub-task đề xuất | Kết quả / kiểm chứng |
| --- | --- | --- |
| 1 | Kiểm tra schema Goal và hợp đồng API | Xác nhận cấu trúc hiện có; đề xuất migration/API cần thiết, không tự đổi rule |
| 2 | Backend: tạo / lấy mục tiêu của tài khoản hiện tại | Kiểm chứng dữ liệu được lưu và không lộ mục tiêu của tài khoản khác |
| 3 | Backend: sửa, xóa và validate mục tiêu | Kiểm tra ngày thi sau ngày bắt đầu, tối đa 90 ngày, thời gian 15–480 phút, quan hệ với task cũ |
| 4 | Frontend: form và danh sách mục tiêu | Có thông báo lỗi theo trường, tải lại vẫn thấy dữ liệu đã lưu |
| 5 | Test và demo luồng Goal end-to-end | Ca đúng/sai/biên/ownership, nêu phần kiểm tra thực tế đã chạy và chưa chạy |

**Phụ thuộc:** story đăng nhập / phân quyền; cần thống nhất hành vi sửa mục tiêu khi ảnh hưởng công việc đã có.

## Đưa vào Jira sau khi nhóm review
- Trong user story cha, chọn **Add / Create subtask** (tên nút tùy cấu hình Jira).
- Copy Title/Description cho từng sub-task, đặt đúng Parent và liên kết AC.
- Developers thống nhất thứ tự, người làm, estimate theo Sprint Planning; PO không dùng AI để tự giao việc.
- Gắn code/PR bằng mã issue Jira thật; không tạo bản duplicate trên GitHub Issues nếu Jira đã là nguồn chính.

## Không nên làm
- Không sinh task chỉ theo từng file hoặc từng thành viên mà bỏ qua kết quả sử dụng được.
- Không coi “đã tạo sub-task” là “đã triển khai”.
- Không tự giả định React/Spring Boot hiện đã có auth, CRUD hay AI endpoint. Kiểm chứng repo trước.
