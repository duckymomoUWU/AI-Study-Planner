# Hợp đồng task và bản nháp trên nhánh test tích hợp

Ngày 2026-11-08. Phạm vi lấy từ `requirements.md`; tài liệu mẫu của dự án khác không phải yêu cầu sản phẩm.
Nhánh `integration/sprint2-sprint3-all8-test` dùng chung session, CSRF và envelope của `docs/backend-api-rules.md`.

## Task thủ công

| Method | Path | Kết quả |
| --- | --- | --- |
| POST | `/api/v1/goals/{goalId}/tasks` | 201, `{data: Task}` |
| PATCH | `/api/v1/goals/{goalId}/tasks/{taskId}` | 200, `{data: Task}` |
| PATCH | `/api/v1/tasks/{taskId}` | 200, `{data: Task}`, tiện ích mới từ US2 |
| DELETE | `/api/v1/goals/{goalId}/tasks/{taskId}` | 204 |
| GET | `/api/v1/tasks/overdue` | 200, `{data: Task[]}`; danh sách không phân trang |

Create nhận đúng `title`, `scheduledDate`, `estimatedMinutes`. PATCH nhận ít nhất một trường trong ba trường đó và `completed`.
Không chấp nhận null, field lạ, `source`/`userId` do client gửi, phút dạng string/decimal.
Title sau trim dài 1–200 ký tự; thời lượng task 1–480 phút; ngân sách mục tiêu vẫn 15–480 phút/ngày.
Ngày task trong `[startDate, examDate)`; tổng phút tính cả task hoàn thành. Ngân sách theo từng goal.
Vi phạm ngày/input trả 400 `VALIDATION_ERROR`; vượt ngân sách trả 422 `DAILY_BUDGET_EXCEEDED`.
`error.context` chứa số liệu dạng số: `scheduledDate`, `budgetMinutes`, `scheduledMinutes`, `excessMinutes`.
Đây là phần mở rộng envelope được ghi rõ; các lỗi validation cũ vẫn có `error.details[]`.

Mỗi mutation thật tăng version goal một lần; PATCH cùng trạng thái/nội dung không tăng.
Service khóa goal và kiểm tra ngân sách trong cùng transaction. SQL lọc owner từ principal.
Overdue là task chưa hoàn thành có ngày trước ngày hiện tại trong timezone của goal; không tự dời/xóa việc.
UI dời việc bằng PATCH nguyên tử, không dùng create rồi delete.

## Bản nháp và xác nhận

| Method | Path | Kết quả |
| --- | --- | --- |
| POST | `/api/v1/goals/{goalId}/plan-drafts` | 201, `{data: Draft}`; body `{}` hoặc rỗng |
| GET | `/api/v1/goals/{goalId}/plan-drafts/latest` | 200, bản nháp gần nhất |
| GET | `/api/v1/goals/{goalId}/plan-drafts/{draftId}` | 200, bản nháp theo goal |
| POST | `/api/v1/goals/{goalId}/plan-drafts/{draftId}/confirm` | 201 lần đầu / 200 khi replay cùng key và nội dung |

Draft gồm `id`, `goalId`, `goalVersion`, `proposal: {tasks: [...]}`, `createdAt`, `expiresAt`.
Mỗi task trong proposal có `title`, `scheduledDate`, `estimatedMinutes` và tùy chọn `priority` (`HIGH`, `MEDIUM`, `LOW`; cập nhật 2026-11-10). Priority là metadata để xem/sắp thứ tự bản nháp; task đã xác nhận vẫn theo schema task hiện tại. Body confirm vẫn chỉ gửi ba trường đầu.
Tạo draft không ghi `study_tasks`; provider nhận snapshot goal và các phần thời gian đã được dùng.
Không giữ transaction/khóa trong thời gian gọi provider. Server validate JSON, ngày, phút và ngân sách trước khi lưu draft.

Confirm cần header `Idempotency-Key` dạng UUID và body:

```json
{
  "expectedGoalVersion": 0,
  "tasks": [
    {"title": "Ôn SQL", "scheduledDate": "2026-11-10", "estimatedMinutes": 30}
  ]
}
```

Client gửi danh sách đã chỉnh sửa; server ấn định source `ai`. Không nhận source từ client.
Một transaction kiểm tra owner, receipt, draft còn hạn/chưa dùng và version; validate toàn bộ danh sách;
thêm task, tăng version một lần và ghi receipt. Migration `V2__plan_confirmation_receipts.sql` thêm bảng receipt;
không sửa V1. Receipt lưu trong PostgreSQL, có khóa duy nhất theo owner/key và draft.
Receipt được kiểm tra trước expiry/version để retry sau mất response vẫn trả kết quả cũ, không tạo trùng.
Khóa owner serialize key trên các goal; khóa goal serialize kiểm tra ngân sách với CRUD thủ công.

Mã lỗi: 400 `VALIDATION_ERROR`; 404 `NOT_FOUND` (cả tài nguyên của người khác);
409 `GOAL_VERSION_CONFLICT`, `IDEMPOTENCY_CONFLICT`, `DRAFT_ALREADY_CONFIRMED`;
410 `DRAFT_EXPIRED`; 422 `INSUFFICIENT_TIME` hoặc `DAILY_BUDGET_EXCEEDED`;
502 `AI_INVALID_RESPONSE`; 503 `AI_UNAVAILABLE`; 504 `AI_TIMEOUT`.
`INSUFFICIENT_TIME` có `error.context.requiredMinutes`, `availableMinutes`, `shortfallMinutes`.
Các số liệu lỗi nằm trong context; không chứa nội dung học, SQL hoặc secret.

## Frontend và cấu hình AI

Trong Mục tiêu → Chi tiết có tạo/mở bản nháp, sửa tiêu đề/ngày/phút, bỏ task và xác nhận rõ ràng.
Chỉ sau response thành công mới báo lưu; khi retry sau lỗi mạng giữ key và nội dung; sửa nội dung bắt đầu thao tác mới.
Lịch theo ngày hỗ trợ tạo/sửa/xóa/hoàn thành/mở lại qua API thật. Tiến độ vẫn tính số task lưu trong DB.

Cấu hình `GEMINI_API_KEY` và `GEMINI_MODEL` ở backend. Không mặc định model chưa được xác minh tồn tại,
không đưa key vào frontend/Git hoặc URL log. Không có cấu hình thì trả 503 và vẫn dùng CRUD thủ công.
Không tự thay lỗi mạng/401/422 bằng thành công giả hay lịch sinh trên localStorage.

Lịch mẫu tại `#calendar-preview` vẫn là màn hình mô phỏng được ghi nhãn, không chứng minh lưu DB.
Không đưa recurrence/startHour/durationHours, best-fit đa môn, tự xóa cache theo email hoặc nhập PDF vào API MVP.
Tiện ích syllabus/fallback của upstream còn trong source để review; chưa mở endpoint PDF/syllabus.
Phần đó cần story/schema riêng nếu nhóm muốn nhận vào phạm vi sau này.

## Kiểm chứng

`CombinedFeaturesPostgresIntegrationTest` kiểm tra CRUD, ownership, ngày, ngân sách kể cả task hoàn thành,
version/no-op, overdue, draft không tự lưu, confirm nguyên tử, receipt replay sau expiry,
xung đột payload/key, expiry/version, hai xác nhận đồng thời, AI lỗi và thao tác thủ công sau lỗi AI.
`TaskControllerTest` và `PlanDraftControllerTest` kiểm tra HTTP/envelope/CSRF/input.
`ai-planner.test.js` kiểm tra API client không giả thành công và giữ version/key/body khi retry.
Provider được mock trong test; chưa phải kiểm chứng một model Gemini thật.


## Cập nhật AIfeat ngày 2026-11-10

- Nhận các thay đổi từ `3c79a01..bc6ebdd`: CSRF warmup, cookie fallback, retry auth khi CSRF hết hạn, cookie/session 7 ngày, ưu tiên AI và UI gọn hơn.
- CSRF request không cache HTTP và dùng chung một request đang chạy để tránh race warmup/submit. Lỗi `403 CSRF_INVALID` được refresh rồi retry đúng một lần; các lỗi khác không tự gửi lại.
- Cookie fallback chỉ lấy XSRF-TOKEN; không dùng cookie chỉ báo `active` để xem người dùng đã đăng nhập. `/me` và session HttpOnly vẫn là nguồn xác thực.
- JSESSIONID và XSRF cookie có Max-Age 7 ngày; session server có idle timeout 7 ngày. Session vẫn nằm trong bộ nhớ backend, không cam kết giữ đăng nhập sau khi backend khởi động lại.
- Logout xóa JSESSIONID, XSRF-TOKEN và marker cũ nếu có. Mã lỗi giữ nguyên chữ hoa và envelope chung.
- Giữ 401 INVALID_CREDENTIALS chung cho tài khoản không tồn tại và sai mật khẩu theo quy tắc API; không nhận thay đổi USER_NOT_FOUND làm lộ sự tồn tại tài khoản.
- Prompt ưu tiên kiến thức nền tảng/bài tập cốt lõi; HTTP timeout AI là 60 giây. Priority trong proposal được kiểm tra trước lưu; hiển thị/sắp ưu tiên không tự đổi ngày hoặc thời lượng.
- Sửa nhãn trống ở calendar-preview; lịch mẫu vẫn tách khỏi luồng API thật. Best-fit giờ/lặp tuần của prototype chưa được bật vì schema MVP vẫn lập lịch theo ngày.


> Cập nhật upload 2026-11-10: người dùng yêu cầu khôi phục upload từ AIfeat. PDF/DOCX/TXT/MD và dán văn bản đã được nhận vào nhánh test qua `POST /api/v1/goals/parse-syllabus`; các ghi chú chưa có endpoint upload ở phần lịch sử phía trên không còn áp dụng. Xem [hợp đồng upload](syllabus-upload.md).
