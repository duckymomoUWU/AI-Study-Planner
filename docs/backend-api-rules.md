# Quy tắc backend API

Áp dụng cho code trong `backend/`. [Requirements](../requirements.md) quyết định
quy tắc sản phẩm; [hợp đồng chức năng](product/README.md) mô tả JSON và lỗi chi tiết.
Tài liệu này giữ quy tắc chung, không lặp toàn bộ spec của từng module.

## Route và HTTP

- API nghiệp vụ dưới `/api/v1`; health ở `GET /actuator/health`.
- Đọc dùng GET; tạo dùng POST; sửa một phần dùng PATCH. `PUT /study-budget` thay toàn bộ cấu hình quỹ chung.
- Tạo trả 201, đọc/sửa trả 200; xóa/logout trả 204 không body. Replay idempotent trả theo hợp đồng từng module.
- Resource lồng phải kiểm tra cả quyền sở hữu và quan hệ cha–con, không chỉ child ID.

| Nhóm | Route chính | Hợp đồng |
| --- | --- | --- |
| Auth | `/auth/*`, `/me` | [Authentication](ASP-12-auth-api-guide.md) |
| Mục tiêu | `/goals`, `/goals/{id}` | [Goals](product/ASP-9-goals-api.md) |
| Công việc | `/tasks`, `/tasks/overdue`, `/goals/{id}/tasks` | [Tasks](product/integrated-tasks-and-plans.md) |
| Đề cương/bản nháp | `/goals/parse-syllabus`, `/goals/{id}/plan-drafts/*` | [Scoped planning](product/syllabus-revision-plan.md) |
| Quỹ chung | `/study-budget` | [Budget](product/shared-study-budget.md) |
| Tiến độ | `/goals/{id}/progress`, `/progress/weekly` | [Goal](product/goal-progress.md), [weekly](product/weekly-progress.md) |
| Timer/thực học | `/study-sessions/*`, `/goals/{id}/study-time` | [Sessions](product/study-sessions.md) |
| Recall | `/recall/materials`, `/recall/decks`, `/recall/cards/*` | [Recall](product/active-recall.md) |
| Calendar | `/calendar/*` | [Calendar](product/google-calendar.md) |

Các đường trong bảng được nối sau `/api/v1`. Tài liệu theo chức năng là nơi tra method,
request, response và các nhánh thao tác; không suy ra API chỉ từ tên route trong bảng.

## Dữ liệu và envelope

- JSON camelCase, UUID dạng chuỗi, ngày lịch `YYYY-MM-DD`, timestamp UTC ISO 8601.
- Ngày học theo múi giờ mục tiêu; không chia chênh lệch UTC cho 24 giờ để đếm ngày.
- Phút/giây là số nguyên; không nhận chuỗi số, số thập phân, null hoặc field người dùng không được đặt.
- Một tài nguyên: `{data:{...}}`. Danh sách phân trang: `{data:[...],meta:{page,limit,totalItems,totalPages}}`.
- Phân trang mặc định page 1/limit 20, tối đa 100. Ngoại lệ có chủ đích như overdue hoặc
  batch task-totals phải được ghi ở hợp đồng, không tự thêm meta giả.
- Không trả password hash, token provider, khóa mã hóa hoặc owner ID nội bộ trong DTO nghiệp vụ.

Lỗi theo dạng:

```json
{"error":{"code":"VALIDATION_ERROR","message":"...","details":[{"field":"examDate","issue":"invalid_date","message":"..."}],"context":{}}}
```

`details[].message` và context tùy lỗi. Client dựa code/field/issue, không so sánh câu
message. 400 cho đầu vào sai; 401 thiếu phiên; 403 CSRF; 404 tài nguyên không có hoặc
không thuộc owner; 409 phiên bản/trạng thái/replay xung đột; 422 không thỏa quỹ hoặc
điều kiện nghiệp vụ; 502/503/504 cho lỗi provider theo hợp đồng. ID sai định dạng theo
validation của endpoint, không dùng lỗi đó để tiết lộ tài nguyên tài khoản khác.

## Session và quyền sở hữu

- Owner lấy từ principal, không nhận userId do client gửi.
- Truy vấn lọc owner trực tiếp hoặc qua goal; khóa ngoại không thay thế authorization.
- Session cookie HttpOnly, SameSite=Lax; Secure bật khi HTTPS. Đăng nhập đổi session ID,
  logout hủy phiên. Backend register không tự login; frontend gọi login riêng sau đăng ký.
- Mọi thao tác ghi, kể cả auth, cần `X-CSRF-TOKEN` từ `/auth/csrf`. Sau đổi phiên lấy token mới.
- Thiếu phiên trả JSON 401, không redirect HTML.

## Quỹ thời gian và đồng thời

- Quỹ chung là giới hạn cứng liên môn, tính cả task hoàn thành; phút/ngày từng môn là
  mức mong muốn. Xem [preferences](product/study-time-preferences.md).
- Thao tác phụ thuộc quỹ khóa owner trước goal, kiểm tra và ghi trong cùng transaction.
  Không đọc tổng ở transaction này rồi ghi ở transaction khác.
- Goal PATCH dùng expectedVersion; mutation goal/task tăng goal version theo hợp đồng.
  PATCH completed về đúng giá trị hiện có là no-op. Các module khác dùng version riêng.
- Draft chỉ là đề xuất; sinh AI không giữ transaction/row lock trong lúc chờ provider.
  Confirm kiểm tra lại phiên bản/quỹ, ghi task/draft/receipt nguyên tử.
- Idempotency-Key và payload cùng nhau xác định retry. Kiểm receipt trước lỗi version
  khi hợp đồng cho phép replay; không nuốt lỗi lưu receipt hoặc tạo thành công giả.
- Timer server tính giây và giới hạn một phiên active bằng khóa/unique index. Thực học
  không tự thay estimatedMinutes, trạng thái task hoặc lịch Google.

## Tổ chức code, dữ liệu và bảo mật

Giữ package `com.studyplanner.*`; controller xử lý HTTP, service điều phối/transaction,
policy giữ quy tắc và repository giữ SQL. Dùng envelope/error handler chung; Recall có
handler giới hạn package cho lỗi miền riêng. Không di chuyển `target/` vào source.

Schema thay bằng migration mới. Hiện repo dùng V8 Recall, V9 sessions; xem cảnh báo
lịch sử nhánh ở [setup](../setup.md) trước khi ghép môi trường đã migrate.

Không log key, password, cookie, token, nội dung học hoặc raw response provider.
Chỉ gửi cho provider dữ liệu cần xử lý, không gửi credentials/email/session.
Key chỉ nằm ở backend, không đưa vào VITE_* hoặc file được Git theo dõi.

Đổi API cần cập nhật contract, client và test tương ứng. Test PostgreSQL chỉ dùng DB
kiểm thử riêng theo [harness](../.agents/harness/README.md). Không sửa code hay tắt bảo vệ
chỉ để test xanh; báo rõ phần chưa xác minh và điều kiện cần kiểm tra tiếp.
