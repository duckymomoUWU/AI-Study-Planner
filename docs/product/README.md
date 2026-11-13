# Hợp đồng chức năng

[Yêu cầu sản phẩm](../../requirements.md) xác định phạm vi. Các tài liệu dưới đây mô tả
API và quy tắc chi tiết; [quy tắc backend](../backend-api-rules.md) áp dụng chung.
Trạng thái triển khai và giới hạn kiểm chứng nằm ở [status](../status.md).

| Chức năng | Tài liệu |
| --- | --- |
| Đăng ký, đăng nhập, session và CSRF | [Authentication](../ASP-12-auth-api-guide.md) |
| Tạo/sửa/xóa mục tiêu | [Goal API](ASP-9-goals-api.md) |
| Công việc thủ công và bản nháp AI cũ | [Tasks và drafts](integrated-tasks-and-plans.md) |
| Tải đề cương và xác nhận nguồn | [Upload](syllabus-upload.md) |
| Lập kế hoạch theo chủ đề, lưu một phần, xếp lại | [Scoped planning](syllabus-revision-plan.md) |
| Quỹ chung/ngày | [Shared budget](shared-study-budget.md) |
| Mức mong muốn từng môn và đồng ý phần vượt | [Study-time preferences](study-time-preferences.md) |
| Hoàn thành/mở lại và tiến độ mục tiêu | [Task completion](task-completion.md), [goal progress](goal-progress.md) |
| Tiến độ theo tuần | [Weekly progress](weekly-progress.md) |
| Timer, thời gian thực học và chỉnh thời lượng | [Study sessions](study-sessions.md) |
| Flashcard và đánh giá Nhớ/Chưa nhớ | [Active Recall](active-recall.md) |
| Google Calendar và đồng bộ sửa/xóa một chiều | [Calendar](google-calendar.md) |

Cách khởi động: [setup](../../setup.md). Cách kiểm thử: [harness](../../.agents/harness/README.md).
Tài liệu có ngày trong `plans/completed/` và `archive/` là bằng chứng lịch sử, không phải hợp đồng mới.
