# Đánh dấu hoàn thành / mở lại công việc (ASP-63 → ASP-66, ASP-120)

**Trạng thái (2026-11-07):** API và giao diện đã hiện thực trên nhánh `dev2-sprint3` và được kiểm bằng test MockMvc, PostgreSQL thật và test client frontend (xem mục 6). Chưa có trên `main`. Quy tắc chung: [Backend API rules](../backend-api-rules.md). Yêu cầu gốc: requirements.md, chức năng 6 ("Đánh dấu hoàn thành/mở lại; hiển thị số công việc hoàn thành và còn lại").

## 1. Phạm vi

| Có trong story này | Thuộc story khác (chưa làm) |
| --- | --- |
| Đổi `completed` của một công việc sang `true` hoặc `false` | Tạo, sửa, xóa công việc, kiểm tra ngân sách khi tạo/sửa (ASP-10) |
| Đọc công việc: theo ngày, theo mục tiêu, chi tiết | Lịch theo ngày nâng cao, việc trễ hạn (ASP-11) |
| Giao diện: checkbox trên trang lịch học, số hoàn thành / còn lại của ngày đang xem | Tiến độ theo mục tiêu `GET /goals/{id}/progress`, hiển thị tiến độ tổng (ASP-16/17) |

Công việc hiện chỉ có thể xuất hiện trong DB bằng SQL hoặc sau này bằng ASP-10/ASP-14; vì vậy các test và demo chèn công việc trực tiếp vào bảng `study_tasks`.

## 2. API

Tất cả cần session (401 `UNAUTHORIZED`); `PATCH` cần CSRF (403 `CSRF_INVALID`). Công việc của người khác, công việc không thuộc `goalId` trên URL và ID sai định dạng đều trả cùng một 404 `NOT_FOUND`.

| Method | Path | Thành công |
| --- | --- | --- |
| PATCH | `/api/v1/goals/{goalId}/tasks/{taskId}` | 200 `{data: Task}` |
| GET | `/api/v1/goals/{goalId}/tasks?date=&completed=&page=&limit=` | 200 `{data: [Task], meta}` |
| GET | `/api/v1/goals/{goalId}/tasks/{taskId}` | 200 `{data: Task}` |
| GET | `/api/v1/tasks?date=YYYY-MM-DD&page=&limit=` | 200 `{data: [Task], meta}` (mọi mục tiêu của người dùng; `date` bắt buộc) |

### Task

```json
{ "id": "…", "goalId": "…", "title": "Ôn SQL", "scheduledDate": "2026-11-10", "estimatedMinutes": 30,
  "completed": false, "source": "manual", "createdAt": "2026-11-07T03:00:00Z" }
```

Sắp xếp `scheduledDate ASC, createdAt ASC, id ASC`; `page` mặc định 1, `limit` mặc định 20, tối đa 100.

### PATCH

```json
{ "completed": true }
```

- Gửi trạng thái đích (`true` = hoàn thành, `false` = mở lại), không phải "đảo trạng thái": gửi lặp không làm đổi ngược.
- Body chỉ có đúng `completed`, kiểu boolean. Thiếu, `null`, chuỗi hay số → 400 `VALIDATION_ERROR` (`required`, `invalid_type`). `title`, `scheduledDate`, `estimatedMinutes` → 400 `not_supported` (ASP-10 sẽ thêm); trường khác (`goalId`, `userId`, ...) → 400 `unknown_field`.
- Response là công việc như đã lưu.

### Tác động lên version của mục tiêu

Theo quy tắc chung, một thay đổi thật của công việc làm `study_goals.version` tăng đúng một lần, trong cùng transaction và sau khi khóa dòng mục tiêu (`SELECT … FOR UPDATE`, cùng khóa với sửa mục tiêu). Yêu cầu đúng trạng thái đang có thì không đổi gì và không tăng version. Hệ quả cần biết:

- Sau khi đánh dấu một việc, `version` của mục tiêu đã tăng; sửa mục tiêu bằng `expectedVersion` cũ trả 409 `GOAL_VERSION_CONFLICT` cho tới khi tải lại mục tiêu. Giao diện mục tiêu tải lại dữ liệu mỗi lần mở trang nên không bị ảnh hưởng trong luồng bình thường.
- Hai yêu cầu đồng thời cho cùng thay đổi chỉ ghi một lần và tăng version một lần (đã có test).

## 3. Giao diện (`frontend/src/pages/DailyTasksPage.jsx`)

- Checkbox theo shadcn/ui (`components/ui/checkbox.jsx`, `badge.jsx`), `aria-label` nêu hành động ("Đánh dấu hoàn thành: …" hoặc "Mở lại: …").
- Hàng chờ máy chủ xác nhận rồi mới đổi (không cập nhật lạc quan); trong lúc chờ checkbox bị khóa nên không gửi trùng; sau khi xong hàng được thay bằng công việc server trả về.
- Số "n hoàn thành" và "m còn lại" của ngày đang xem tính lại từ danh sách; ngày không có việc hiển thị 0/0.
- Lỗi: 401 → hướng dẫn đăng nhập; 404 → thông báo và tải lại danh sách; 403 → tải lại trang; lỗi khác hiện thông báo của máy chủ. Không bao giờ hiện "thành công" khi máy chủ từ chối, và không dùng dữ liệu demo giả.
- Thêm/xóa công việc và "việc trễ" vẫn là giao diện của ASP-10/11: gọi API thật dưới `/api/v1` và hiện lỗi nếu máy chủ chưa hỗ trợ, thay vì lưu giả trong bộ nhớ.

## 4. Quyết định cần PO xác nhận (chưa chốt trong requirements)

1. Công việc đã hoàn thành vẫn chiếm phút trong ngân sách của ngày đã xếp (đề xuất trong đặc tả; chưa có code vì chưa có kiểm tra ngân sách khi tạo/sửa).
2. Đổi trạng thái hoàn thành làm tăng version mục tiêu, nên làm cũ mọi bản nháp AI của mục tiêu đó (đề xuất trong đặc tả; đã áp dụng ở API này).
3. Tiến độ theo mục tiêu (`completed / total`, 0/0 khi chưa có việc) sẽ làm ở ASP-16/17; hiện giao diện chỉ hiển thị số của ngày đang xem.

## 5. Ánh xạ tiêu chí → mã → bằng chứng

| Tiêu chí | Hiện thực | Bằng chứng |
| --- | --- | --- |
| Đánh dấu hoàn thành và mở lại, lưu bền | `PATCH`, cột `study_tasks.completed` | `TaskApiPostgresIntegrationTest.markDoneRepeatAndReopen`, `listsReflectTheStoredState` |
| Gửi lặp không đảo ngược, không tăng version | So sánh trạng thái hiện tại trước khi ghi | Cùng test; `TaskServiceTest.askingForTheStateTheTaskAlreadyHasChangesNothing…` |
| Chỉ chủ sở hữu | Truy vấn nối `study_goals` và lọc `user_id` | `crossAccountAccessIsRejected`, `taskMustBelongToTheGoalInTheUrl`, `TaskJdbcRepositoryTest` |
| Xác thực, CSRF, validate ở server | Security chain, `TaskController.parseCompleted` | `authCsrfAndValidation`, `TaskControllerTest` |
| Không ghi nửa chừng, đồng thời | Khóa dòng mục tiêu + một transaction | `simultaneousRequestsAreSerialized`, `TaskServiceTest.realChange…` |
| Hiển thị số hoàn thành / còn lại, 0/0 | `summarizeTasks`, thẻ "TIẾN ĐỘ HOÀN THÀNH" | `tasks.test.js` (`summarizeTasks…`) |
| Lỗi rõ ràng, không báo thành công giả | `describeTaskError`, không fallback demo | `taskErrors.test.js`, `tasks.test.js` (`setTaskCompleted surfaces server errors…`) |

## 6. Kết quả kiểm chứng

Xem mục "Kiểm chứng ASP-63 → ASP-66" ở cuối [ASP-9 verification](ASP-9-verification.md) cho lệnh, kết quả và giới hạn.
