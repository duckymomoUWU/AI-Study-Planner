# Phiên học tập trung (Pomodoro): backend

> Integration update 2026-11-13: Timer UI at #timer now calls this backend on
> integration/dev3-recall-test. The test environment already applied Recall V8;
> preserve it and append study sessions as V9. Source dev2-sprint4 uses the reverse
> numbering (Timer V8, Recall V9). Do not swap applied migrations or repair databases
> to match filenames. See the integration delivery record for verification and limits.


**Trạng thái (2026-11-13):** backend đã hiện thực trên nhánh `dev2` (remote `dev2-sprint4`, tách từ `main` 29dbf6d), kiểm bằng test đơn vị, MockMvc và PostgreSQL thật. **Chưa có giao diện Timer** (việc của Người 1) và chưa gộp vào `main`. Hợp đồng gốc: bộ giao việc nhóm, `03-API-CONTRACT.md` mục B (ngoài repo). Quy tắc chung: [Backend API rules](../backend-api-rules.md).

Mục tiêu: lưu bền thời gian học thật của một công việc. Thời gian do **máy chủ** tính bằng `Clock`, không nhận số giây từ client, nên tải lại trang, hai tab hay đóng tab không làm sai số.

## 1. Quy tắc

- Một tài khoản có tối đa một phiên `RUNNING` hoặc `PAUSED`. Đây là ràng buộc ở DB (chỉ mục duy nhất từng phần), không chỉ kiểm tra trước khi ghi.
- Trạng thái: `RUNNING ⇄ PAUSED`; từ hai trạng thái đó có thể `FINISH` (thành `COMPLETED`) hoặc `CANCEL` (thành `CANCELLED`). Trạng thái cuối không mở lại.
- Pomodoro cố định 1500 giây (25 phút). Thời gian nghỉ 5 phút là đồng hồ riêng của giao diện, không lưu thành phiên học.
- Số giây tập trung = giây lưu sẵn + thời gian từ `lastResumedAt` đến "bây giờ" khi đang `RUNNING`, làm tròn xuống giây, tối đa 1500. `PAUSED` và trạng thái cuối không tăng theo giờ thực. Đồng hồ lùi không làm giảm số giây.
- `GET` chỉ tính ảnh chụp tại `serverNow`, **không ghi**. Khi đủ 1500 giây, `focusComplete=true` và `remainingSeconds=0`; giao diện phải gửi `FINISH`. `PAUSE` hoặc `RESUME` lúc đó trả 409 `FOCUS_FINISH_REQUIRED`.
- `FINISH` sớm lưu đúng số giây máy chủ tính. `CANCEL` giữ số giây trong lịch sử nhưng **không** tính vào tổng đã học; chỉ `COMPLETED` được tính.
- Học không thay đổi kế hoạch: không đổi `estimatedMinutes`, `completed`, `version` của mục tiêu, quỹ thời gian chung hay Google Calendar. Công việc đã hoàn thành vẫn học lại được.
- Xóa công việc hoặc mục tiêu không xóa lịch sử: liên kết `taskId`/`goalId` thành `null`, tên môn và tên việc đã chép lúc bắt đầu vẫn còn. Phiên đang mở vẫn kết thúc được. Xóa tài khoản xóa phiên và biên nhận của tài khoản đó.

## 2. API (tất cả dưới `/api/v1`, cần phiên đăng nhập; ghi cần `X-CSRF-TOKEN`)

| Method và đường dẫn | Body, header | Kết quả |
| --- | --- | --- |
| `GET /study-sessions/active` | không | `{data:{session: Session hoặc null}}` |
| `POST /study-sessions` | `{taskId}` + `Idempotency-Key` | Session; **201** lần đầu, **200** khi replay cùng key |
| `GET /study-sessions/{id}` | không | Session |
| `POST /study-sessions/{id}/actions` | `{action, expectedVersion}` + `Idempotency-Key` | Session sau chuyển trạng thái; `action` là `PAUSE`, `RESUME`, `FINISH` hoặc `CANCEL` |
| `GET /study-sessions?taskId=&page=&limit=` | không | Danh sách Session, `startedAt` giảm dần rồi `id` tăng dần, có `meta` phân trang (mặc định 20, tối đa 100) |
| `GET /goals/{goalId}/study-time` | không | `{completedSessionCount, totalFocusSeconds}` của các phiên `COMPLETED` |

Session gồm: `id, taskId, goalId, taskTitle, subject, status, plannedFocusSeconds, focusedSeconds, remainingSeconds, focusComplete, startedAt, lastResumedAt, endedAt, serverNow, version` (`taskId`, `goalId`, `lastResumedAt`, `endedAt` có thể `null`; không trả `userId`).

Body nghiêm ngặt: trường lạ (kể cả `userId`, `elapsedSeconds`), `null`, sai kiểu, `action` ngoài bốn giá trị, `expectedVersion` âm hoặc không nguyên đều là 400. Thiếu hoặc sai `Idempotency-Key` (không phải UUID) là 400 trên hai thao tác ghi.

## 3. Idempotency và phiên bản

- Mọi ghi giữ khóa chủ sở hữu (`app_users ... FOR NO KEY UPDATE`, cùng thứ tự khóa với quỹ thời gian chung), kiểm biên nhận **trước** `expectedVersion`, rồi ghi thay đổi và biên nhận trong **cùng một giao dịch**. Nhờ vậy thử lại sau timeout không bị báo cũ giả.
- Cùng chủ sở hữu, cùng key, cùng nội dung: trả lại phản hồi đã lưu (200), không thêm phiên, không cộng thời gian lần hai. Phản hồi lưu là bản chụp lần đầu, nên `serverNow` và số giây trong đó là của lần đầu.
- Cùng key nhưng khác thao tác, đích hoặc nội dung: 409 `IDEMPOTENCY_CONFLICT`. Key dùng chung một không gian cho mọi thao tác của một tài khoản; hai tài khoản có thể dùng cùng giá trị key.

## 4. Lỗi (khuôn chung `{error:{code,message,details?,context?}}`)

| HTTP | `code` | Khi nào |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Body, header, `taskId` hoặc phân trang sai |
| 401 | `UNAUTHORIZED` | Chưa đăng nhập |
| 403 | `CSRF_INVALID` | Thiếu hoặc sai CSRF khi ghi |
| 404 | `NOT_FOUND` | Công việc, phiên hoặc mục tiêu không tồn tại hoặc của người khác (không phân biệt) |
| 409 | `SESSION_ALREADY_ACTIVE` | Đã có phiên đang mở; `context.activeSessionId` là phiên của chính người gọi |
| 409 | `SESSION_VERSION_CONFLICT` | `expectedVersion` cũ; `context.currentVersion`, `details[0]` là `expectedVersion`/`stale_version`. Không đổi gì |
| 409 | `SESSION_STATE_CONFLICT` | Thao tác không hợp lệ ở trạng thái hiện tại; `context.status` |
| 409 | `FOCUS_FINISH_REQUIRED` | Đã đủ 1500 giây, chỉ còn kết thúc được |
| 409 | `IDEMPOTENCY_CONFLICT` | Key đã dùng cho yêu cầu khác |

## 5. Dữ liệu (migration V9)

`study_sessions`: chủ sở hữu, `task_id`/`goal_id` (`ON DELETE SET NULL`), tên việc và môn đã chép, trạng thái, `planned_focus_seconds` (luôn 1500), `accumulated_focus_seconds` (0..1500), `started_at`, `last_resumed_at`, `ended_at`, `version`. Ràng buộc CHECK: `RUNNING` có điểm tiếp tục và chỉ trạng thái đó có; chỉ trạng thái cuối có `ended_at`. Chỉ mục duy nhất từng phần theo chủ sở hữu cho `RUNNING`/`PAUSED`. `study_session_receipts`: khóa `(user_id, idempotency_key)`, dấu vân tay SHA-256 của thao tác, đích và nội dung, phản hồi JSONB. Chỉ thêm bảng mới; không sửa V1 đến V7.

## 6. Mã và bằng chứng

| Phần | Mã | Test |
| --- | --- | --- |
| Quy tắc thời gian và trạng thái | `StudySessionPolicy` | `StudySessionPolicyTest` (11 ca, thời gian cố định, không `sleep`) |
| Hợp đồng HTTP | `StudySessionController`, `StudySessionRequest`, `ApiExceptionHandler` | `StudySessionControllerTest` (13 ca MockMvc, gồm 2 ca so khớp từng trường với fixture dùng chung `session-active.json` và `api-error.json` trong `backend/src/test/resources/fixtures/`) |
| Giao dịch, khóa, replay, sở hữu | `StudySessionService`, `StudySessionRepository`, V9 | `StudySessionApiPostgresIntegrationTest` (16 ca, PostgreSQL thật, đồng hồ chỉnh tay) |

Test PostgreSQL gồm: 6 yêu cầu bắt đầu đồng thời với key khác nhau chỉ tạo đúng 1 phiên; **6 thao tác đồng thời (PAUSE, CANCEL, FINISH) cùng `expectedVersion` với key khác nhau chỉ áp dụng đúng 1, 5 cái còn lại nhận 409 `SESSION_VERSION_CONFLICT` với `currentVersion` 1, số giây chỉ tính một lần; 6 lần thử lại `FINISH` đồng thời cùng key đều nhận 200 cùng kết quả, chỉ một thay đổi và một biên nhận (bỏ khóa chủ sở hữu thì ca này đỏ, ca khác key vẫn an toàn nhờ kiểm tra phiên bản)**; replay cùng key; key dùng cho yêu cầu khác; `expectedVersion` cũ không đổi dữ liệu; chuyển trạng thái sai; mở quá 8 giờ vẫn tối đa 1500 giây và `GET` không ghi; `CANCEL` và mục tiêu khác không vào tổng; tài khoản khác nhận 404 ở mọi đường; xóa việc hoặc mục tiêu vẫn giữ lịch sử; việc đã hoàn thành vẫn học lại được; xóa tài khoản xóa phiên; phân trang, sắp xếp, lọc theo việc; 401, 403 và 400 không để lại dữ liệu.

## 7. Giới hạn chưa kiểm chứng

- Chưa có giao diện Timer nên chưa có kiểm tra bằng trình duyệt, hai tab thật, hay mất mạng thật; chỉ có HTTP/MockMvc.
- Fixture dùng chung mới được dùng ở phía MockMvc; Timer UI (Người 1) chưa có client nên chưa có test client dùng cùng fixture.
- Chưa có chỉ mục hay đo hiệu năng cho lịch sử rất dài; chưa có dọn biên nhận cũ.
- Chưa có chống gian lận, đo mức tập trung thật, hay nhắc việc nền (ngoài phạm vi).
- Hợp đồng và các tham số (25 phút, giây là đơn vị) là đề xuất của bộ giao việc, **chờ PO/nhóm duyệt**; chưa sửa `requirements.md`.

## Explicit completion after Timer (2026-11-13)

The integrated Timer UI now offers task completion only after a saved COMPLETED session and a fresh incomplete-task read. Confirm uses the existing task PATCH with completed=true; defer writes nothing. Already completed/deleted tasks and cancelled sessions do not prompt. FINISH itself still never changes tasks. Task write failures retain the session and allow retry. [Evidence](../plans/completed/timer-task-completion-2026-11-13.md).
