# Timer và thời gian học thực tế

## Hành vi

Giao diện `#timer` mở trực tiếp hoặc từ công việc ở lịch tuần/ngày. Chọn task thật của
tài khoản, bắt đầu, tạm dừng, tiếp tục, kết thúc sớm hoặc hủy. Mỗi người tối đa một phiên
RUNNING/PAUSED. Công việc đã hoàn thành vẫn có thể ôn lại.

Một phiên tập trung tối đa 1500 giây (25 phút), do Clock server tính. PAUSED không cộng
thời gian. Nghỉ 5 phút là bộ đếm UI riêng, không tạo phiên thực học. FINISH lưu COMPLETED;
CANCEL lưu CANCELLED nhưng không cộng vào tổng đã học.

Đồng hồ UI dựa `remainingSeconds` của snapshot và thời gian monotonic từ lúc nhận.
Reload lấy active session từ backend; quay lại tab đồng bộ lại. GET không tự FINISH:
khi hết thời gian, UI gửi FINISH có idempotency key; lỗi mạng giữ yêu cầu để thử lại.

## API

Các route sau nối sau `/api/v1`. Cần session; thao tác ghi cần CSRF.

| Method + route | Request | Data |
| --- | --- | --- |
| GET `/study-sessions/active` | Không body | `{session: Session hoặc null}` |
| POST `/study-sessions` | `{taskId}` + Idempotency-Key UUID | Session; 201 mới, 200 replay |
| GET `/study-sessions/{id}` | Không body | Session |
| POST `/study-sessions/{id}/actions` | `{action,expectedVersion}` + Idempotency-Key | Session |
| GET `/study-sessions?taskId=&page=1&limit=20` | taskId tùy chọn | Danh sách phân trang, startedAt DESC/id ASC |
| GET `/goals/{id}/study-time` | Goal của owner | `{completedSessionCount,totalFocusSeconds}` |
| GET `/study-sessions/task-totals?taskIds=<uuid>,<uuid>` | 1–100 UUID | Danh sách `{taskId,completedSessionCount,totalFocusSeconds}` |

Session gồm `id`, `taskId`, `goalId`, `taskTitle`, `subject`, `status`,
`plannedFocusSeconds`, `focusedSeconds`, `remainingSeconds`, `focusComplete`,
`startedAt`, `lastResumedAt`, `endedAt`, `serverNow`, `version`.
Task/goal ID có thể null sau khi xóa; snapshot tên vẫn giữ.

Action chỉ là PAUSE/RESUME/FINISH/CANCEL. Version cũ bị từ chối, receipt được kiểm tra
trước version để retry không nhận stale giả. Key cùng payload trả kết quả cũ, khác payload
trả 409 IDEMPOTENCY_CONFLICT. Bắt đầu khi có phiên active trả SESSION_ALREADY_ACTIVE;
version/trạng thái sai trả SESSION_VERSION_CONFLICT/SESSION_STATE_CONFLICT;
hết focus mà tiếp tục pause/resume trả FOCUS_FINISH_REQUIRED.

Owner lấy từ principal; task/goal/session của người khác không đọc được. Task-totals
phải sở hữu mọi ID, nếu có ID thiếu/ngoại tài khoản thì cả batch 404; task không có phiên
COMPLETED trả số 0 thật. Batch không phân trang; frontend chia nhóm khi trên 100 task.

## Hoàn thành công việc sau phiên học

FINISH không tự đánh dấu task. Sau khi lưu COMPLETED, UI đọc lại task; chỉ với task
còn tồn tại và chưa hoàn thành mới hỏi “Bạn đã hoàn thành công việc này chưa?”.

- Xác nhận gọi task PATCH `{completed:true}`; gửi lại không đảo về chưa xong.
- Chọn học tiếp sau không ghi task. Lỗi cập nhật không làm mất phiên học đã lưu.
- Task đã hoàn thành/bị xóa và phiên CANCELLED không hiện câu hỏi.

## Dự kiến và thực học

Lịch tuần/ngày và task đã biết trong Timer hiển thị hai số riêng. Thực học cộng mọi
COMPLETED session của task, không lọc theo ngày session; dời task không mất tổng.
Giữ giây lẻ, không làm tròn lên hoặc suy ra thời gian còn lại bằng phép trừ.

Nút Điều chỉnh thời lượng gửi task PATCH `estimatedMinutes` nguyên 1–480. Server vẫn
kiểm tra quỹ chung; thất bại giữ estimate đã lưu và input để sửa. Không tự sửa estimate,
đánh dấu hoàn thành hoặc thay phiên Timer khi tổng thực học vượt dự kiến.

## Dữ liệu và bằng chứng

V9 thêm `study_sessions` và `study_session_receipts`, unique index cho một phiên active,
CHECK giới hạn giây/trạng thái. Task/goal FK dùng SET NULL để giữ lịch sử; owner dùng
CASCADE khi xóa tài khoản. Owner lock serialize ghi và receipt cùng transaction.

Code: `backend/.../sessions`, `frontend/src/pages/TimerPage.jsx`, client `studySessions.js`,
`TaskTimeDetails` và `SessionTaskCompletion`. Bằng chứng:
[tích hợp](../plans/completed/dev2-recall-timer-integration-2026-11-13.md),
[hoàn thành task](../plans/completed/timer-task-completion-2026-11-13.md),
[dự kiến/thực học](../plans/completed/planned-actual-study-time-2026-11-13.md).
Browser hai tab/offline và khả năng chịu tải chưa được xác minh đầy đủ.
