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
- [x] Người 3 giao API recall; luồng Recall chạy với API thật, tải lại trang còn dữ liệu (trừ bước Gemini, chưa có khóa)
- [x] Backend recall: bỏ thẻ dự phòng, lưu nguyên tử, một bộ thẻ cho mỗi Idempotency-Key, không kích hoạt bộ thẻ rỗng, NFC, số trang cho PDF, lỗi PDF rõ ràng, xem trước nội dung đã đọc (xem active-recall.md mục 8)
- [x] Kiểm toàn bộ với dữ liệu lớn (130 mục tiêu, 130 bộ thẻ, 130 phiên học): sửa phân trang và chữ dài của trang Ôn tập, lỗi 500 khi tên mục tiêu dài 101 ký tự, `reduced-motion` và nhãn `US-17` (xem harness, mục 2026-11-13)
- [x] Test đồng thời cho hai tab thao tác (khác key, cùng `expectedVersion`) và thử lại cùng key (commit `cced8c6`)
- [x] Fixture phiên học dùng chung trong `backend/src/test/resources/fixtures/`, MockMvc so khớp từng trường
- [x] Kiểm bàn phím, cây truy cập, tương phản, 768 px và tải PDF thật; sửa mất focus và tương phản (commit `72f4de5`)
- [x] Gộp API recall của Người 3 vào `dev2` (V9, bỏ `out-of-order`) và chạy giao diện với API thật
- [ ] Gộp `App.jsx` với Người 1, bỏ mock khỏi đường demo (D5)
- [ ] PO duyệt hợp đồng v1, rồi cập nhật `requirements.md`
- [ ] Review độc lập và CI trên GitHub

## Decisions

- 2026-11-12: Key `Idempotency-Key` dùng một không gian cho cả bắt đầu và thao tác của một tài khoản; dấu vân tay gồm thao tác, đích và nội dung, nên dùng lại key cho việc khác là 409 `IDEMPOTENCY_CONFLICT`.
- 2026-11-12: Thời gian lưu theo giây nguyên, làm tròn xuống; `GET` không bao giờ ghi (kể cả khi hết 25 phút), giao diện gửi `FINISH`.
- 2026-11-12: Mock Recall chỉ để phát triển, bật bằng tham số trên URL và không có trong bản build production; khi API thiếu, trang báo lỗi thật.

## Validation

- Focused proof: `StudySessionPolicyTest` 11/11; `StudySessionControllerTest` 11/11; `StudySessionApiPostgresIntegrationTest` 14/14 (`-Dtest=... -Dgoal.integration=true`, PostgreSQL 17 dùng một lần, DB `study_planner_test`, cổng 55434).
- Full backend trên `dev2` đã gộp API recall (V8 và V9): `mvn -f backend/pom.xml clean test -Dgoal.integration=true -Dauth.integration=true`: **396 test, 0 lỗi, 25 bỏ qua** (cờ calendar, planning và PDF riêng không bật). Là `test`, không phải `verify`. Gồm 40 test phiên học (11 policy, 13 MockMvc, 16 PostgreSQL) và 33 test recall của Người 3.
- Frontend: `npm run lint` sạch, `npm test` **130/130**, `npm run build` đạt; gói đầu ra không chứa mock.
- Trình duyệt: (a) mock, 1024 và 360 px (luồng đầy đủ và các đường thử lại); (b) **API thật** (Vite, Spring Boot, PostgreSQL): lưu nguồn văn bản và PDF thật, sửa, kích hoạt, ôn tập, tải lại còn tiến độ, PDF không chữ bị từ chối; (c) bàn phím, cây truy cập, tương phản và 768 px (chi tiết trong `docs/product/active-recall.md` mục 4 và 6).
- Chưa có: Timer UI, bước gọi Gemini thật (không có khóa), trình đọc màn hình thật, đường 401 giữa chừng, review độc lập, CI. Các lần chạy trên không cộng gộp với số liệu của nhánh khác.
