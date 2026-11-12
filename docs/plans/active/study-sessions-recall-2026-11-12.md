# Execution Plan: Study sessions backend and Active Recall UI (Người 2)

Date: 2026-11-12

## Status

Active. Phần của Người 2 đã hiện thực và kiểm chứng ở mức đã ghi bên dưới; tích hợp với Timer UI (Người 1) và API recall (Người 3) chưa làm, nên kế hoạch chưa chuyển sang `completed/`.

## Outcome

- Người học bắt đầu, tạm dừng, tiếp tục, kết thúc hoặc hủy một phiên Pomodoro gắn với một công việc; thời gian do máy chủ tính và lưu bền, không sai khi tải lại, hai tab hay đóng tab.
- Người học tạo thẻ hỏi–đáp từ tài liệu của mình, xem và sửa bản nháp, xác nhận, rồi lật đáp án và tự đánh giá nhớ hoặc chưa nhớ.

## Context

- Bộ giao việc nhóm 11/11/2026 (ngoài repo): `02-GIAO-VIEC-VA-AC.md` (TMR-BE-01 AC TBE-01 đến 09; AR-UI-01 AC RUI-01 đến 07), `03-API-CONTRACT.md`, `05-BACKEND-GUIDE.md`. Nhánh gốc `main` 29dbf6d, nhánh làm việc `feat/study-sessions-recall-ui`.
- Hợp đồng đã hiện thực: [study-sessions.md](../../product/study-sessions.md), [active-recall.md](../../product/active-recall.md). Quy tắc: [backend-api-rules.md](../../backend-api-rules.md).
- Phụ thuộc: Timer UI của Người 1 dùng session API; Recall UI dùng API của Người 3. V9 (recall) phải đặt sau V8 (phiên học).

## Scope

In scope:

- Migration V8, gói `sessions`, handler lỗi, test đơn vị, MockMvc và PostgreSQL.
- Trang `#recall`, `lib/recall.js`, mock phát triển, test logic và kiểm tra thủ công trong trình duyệt.

Out of scope:

- Giao diện Timer, Landing (Người 1); API recall, Gemini, V9 (Người 3).
- Sửa `requirements.md`: chờ PO duyệt các mặc định v1 (25 phút, đơn vị giây, giới hạn tài liệu).

## Approach

Backend trước (V8 và API phiên học) để Người 1 nối Timer, rồi giao diện Recall làm trên fixture và mock theo hợp đồng, bỏ mock khi API của Người 3 sẵn sàng. Không tự thêm hành vi ngoài hợp đồng; điểm hợp đồng còn mơ hồ ghi vào tài liệu sản phẩm.

## Risks And Recovery

- Hợp đồng v1 chưa được PO và nhóm duyệt chính thức: đổi route, enum hoặc nullability phải sửa tài liệu, fixture và test cùng lúc.
- `App.jsx` thuộc Người 1; thay đổi `#recall` (một route, một nút) có thể xung đột khi gộp. Giữ nhỏ và gộp theo hướng của Người 1.
- V8 chỉ thêm bảng mới. Hoàn tác: chưa có bản triển khai thật; trên DB thử, bỏ nhánh và dựng lại DB thử. Không sửa V1 đến V7, không bật `outOfOrder`, không reset DB cá nhân.
- Mock dễ bị hiểu nhầm là chức năng thật: chỉ bật bằng `?recallMock=1` trong `npm run dev`, có banner, không vào bản build.

## Progress

- [x] Chốt khung code từ `main`, mẫu lớp và quy tắc API
- [x] V8 `study_sessions` và `study_session_receipts`
- [x] `StudySessionPolicy`, `StudySessionService`, `StudySessionRepository`, `StudySessionController`, handler lỗi
- [x] Test đơn vị, MockMvc, PostgreSQL (commit `fa0db0b`)
- [x] `lib/recall.js`, mock, test (22 ca) và trang Ôn tập (commit `a0bab8b`)
- [x] Tài liệu sản phẩm và mục Harness
- [ ] Người 1 nối Timer UI với session API và kiểm tra hai tab, tải lại, mất mạng bằng trình duyệt
- [ ] Người 3 giao API recall; chạy lại luồng Recall với API thật, kiểm tải lại trang còn dữ liệu
- [ ] Test đồng thời cho hai tab thao tác (khác key, cùng `expectedVersion`) trên phiên học
- [ ] Gộp `App.jsx` với Người 1, bỏ mock khỏi đường demo (D5)
- [ ] PO duyệt hợp đồng v1, rồi cập nhật `requirements.md`
- [ ] Review độc lập và CI trên GitHub

## Decisions

- 2026-11-12: Key `Idempotency-Key` dùng một không gian cho cả bắt đầu và thao tác của một tài khoản; dấu vân tay gồm thao tác, đích và nội dung, nên dùng lại key cho việc khác là 409 `IDEMPOTENCY_CONFLICT`.
- 2026-11-12: Thời gian lưu theo giây nguyên, làm tròn xuống; `GET` không bao giờ ghi (kể cả khi hết 25 phút), giao diện gửi `FINISH`.
- 2026-11-12: Mock Recall chỉ để phát triển, bật bằng tham số trên URL và không có trong bản build production; khi API thiếu, trang báo lỗi thật.

## Validation

- Focused proof: `StudySessionPolicyTest` 11/11; `StudySessionControllerTest` 11/11; `StudySessionApiPostgresIntegrationTest` 14/14 (`-Dtest=... -Dgoal.integration=true`, PostgreSQL 17 dùng một lần, DB `study_planner_test`, cổng 55434).
- Full backend sau V8: `mvn -f backend/pom.xml clean test -Dgoal.integration=true -Dauth.integration=true`: **332 test, 0 lỗi, 25 bỏ qua** (cờ calendar, planning và PDF riêng không bật). Là `test`, không phải `verify`.
- Frontend: `npm run lint` sạch, `npm test` **120/120**, `npm run build` đạt; gói đầu ra không chứa mock.
- Trình duyệt (Vite dev, backend và PostgreSQL thật cho đăng nhập và mục tiêu, Recall bằng mock): kiểm form, tạo, sửa, xác nhận, ôn tập, ôn lại, lỗi tạo rồi thử lại, lỗi đánh giá rồi thử lại, 360 px không tràn ngang; không mock thì báo lỗi thật, không hiện thẻ mẫu.
- Chưa có: Timer UI, API recall thật, kiểm bàn phím và trình đọc màn hình, tải tệp PDF thật, review độc lập, CI. Các lần chạy trên không cộng gộp với số liệu của nhánh khác.
