# Công việc và bản nháp AI

API dưới `/api/v1`, theo [quy tắc chung](../backend-api-rules.md).
Đây là hợp đồng task CRUD và nhánh draft tương thích cũ; luồng lập kế hoạch theo
chủ đề/nguồn dùng [scoped planning](syllabus-revision-plan.md).

## Công việc

| Method | Route | Kết quả |
| --- | --- | --- |
| POST | `/goals/{goalId}/tasks` | 201, Task |
| GET | `/tasks?date=YYYY-MM-DD`, `/goals/{goalId}/tasks` | Danh sách phân trang |
| GET | `/goals/{goalId}/tasks/{taskId}` | Task |
| PATCH | `/goals/{goalId}/tasks/{taskId}`, `/tasks/{taskId}` | Task |
| DELETE | `/goals/{goalId}/tasks/{taskId}` | 204 |
| GET | `/tasks/overdue` | Task[] chưa hoàn thành trước hôm nay, không phân trang |

Create nhận `title,scheduledDate,estimatedMinutes`. PATCH nhận ít nhất một trong ba
trường đó hoặc `completed`; không nhận source/userId/field lạ/null. Title 1–200 sau trim,
phút nguyên 1–480; ngày nằm trong `[startDate,examDate)`.

Task thuộc goal của principal. Mutation tăng goal version theo hợp đồng; đặt completed
về đúng giá trị hiện có không đảo trạng thái. Tổng phút mọi môn trong ngày gồm task đã
hoàn thành phải nằm trong [quỹ chung](shared-study-budget.md). Mức mong muốn của môn
không chặn thao tác thủ công. Kiểm tra/ghi cùng transaction, owner trước goal.

Xóa goal cascade task/draft; lịch sử Timer dùng SET NULL và giữ tên snapshot. Sửa/xóa
việc đã xuất Calendar enqueue đồng bộ theo [Calendar contract](google-calendar.md).

## Bản nháp và xác nhận

- POST `/goals/{goalId}/plan-drafts`: sinh draft, không tạo task và không khóa DB khi chờ AI.
- GET `/goals/{goalId}/plan-drafts/latest` hoặc `/.../{draftId}`: xem draft/lịch sử theo owner.
- POST `/goals/{goalId}/plan-drafts/{draftId}/confirm`: Idempotency-Key UUID,
  `expectedGoalVersion` và danh sách task người dùng đã chỉnh.

Draft cũ không có scope giữ tương thích với `{title,scheduledDate,estimatedMinutes}`.
Draft mới có scope phải giữ ID/chủ đề, phần chưa xếp và các cờ xác nhận trong
[scoped contract](syllabus-revision-plan.md) và [preferences](study-time-preferences.md).
Không áp payload draft cũ cho draft scoped để bỏ qua kiểm tra.

Confirm kiểm owner, receipt, trạng thái/expiry/version và quỹ hiện tại. Task, thay đổi
draft, goal version và receipt được lưu nguyên tử. Retry cùng key/payload trả kết quả
cũ; đổi payload với cùng key là 409, không tạo bản sao hoặc chèn một phần ngoài ý muốn.
Lưu một phần chỉ khi người dùng chọn rõ; phần chưa xếp vẫn được giữ theo scoped contract.

Các lỗi quan trọng: VALIDATION_ERROR, NOT_FOUND, GOAL_VERSION_CONFLICT,
DRAFT_ALREADY_CONFIRMED, IDEMPOTENCY_CONFLICT, DRAFT_EXPIRED,
SHARED_DAILY_BUDGET_EXCEEDED, PREFERENCE_EXCEEDANCE_REQUIRES_CONFIRMATION và lỗi AI.
Mã cụ thể cùng context lấy ở hợp đồng từng luồng; không log nội dung học/provider.

## Frontend và thời gian học

Lịch tuần/ngày hỗ trợ tạo/sửa/xóa/hoàn thành qua API thật; không có trang lịch mẫu.
[Số phút dự kiến/thực học](study-sessions.md) là hai số độc lập. Sửa dự kiến chủ động
vẫn dùng task PATCH và quỹ chung; FINISH Timer không tự sửa task hoặc thời lượng.

Bằng chứng: [core delivery](../plans/completed/core-study-calendar-2026-11-11.md),
[partial planning](../plans/completed/partial-planning-2026-11-11.md),
[planned/actual time](../plans/completed/planned-actual-study-time-2026-11-13.md).
