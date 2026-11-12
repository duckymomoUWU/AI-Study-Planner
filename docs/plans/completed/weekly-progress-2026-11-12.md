# Xem tiến độ theo tuần (ASP-99 → ASP-102, ASP-112) — kế hoạch và bằng chứng

Date: 2026-11-12

## Status

Completed trên nhánh `dev2` (chưa gộp vào `main`, chưa có review độc lập, CI chưa chạy).

## Outcome

Sinh viên xem số việc hoàn thành và tổng số việc theo tuần Thứ Hai–Chủ Nhật, chọn tuần trước/sau, tuần trống hiện 0/0. Hợp đồng: [weekly-progress.md](../../product/weekly-progress.md).

## Progress

- [x] ASP-99 trang tiến độ tuần (frontend)
- [x] ASP-100 `GET /api/v1/progress/weekly` (backend)
- [x] ASP-101 test chấp nhận trên PostgreSQL thật và mock
- [x] ASP-102 tài liệu và `requirements.md`
- [x] ASP-112 nghiệm thu DoD
- [x] 2026-11-12: gộp `main`, bỏ trang checklist trùng, chuyển controller sang `ApiResponse`/`ApiExceptionHandler`

## Decisions

- 2026-11-12: bỏ `TaskChecklistPage` vì trang Lịch học (`DailyTasksPage`) đã có đủ đánh dấu, thêm, sửa, dời, xóa và dời việc quá hạn bằng một `PATCH`.

## Validation


Ngày chạy 2026-11-08, Windows 11, nhánh `sprint4/weekly-progress` (tách từ nhánh sprint 3 của dev1). Mô tả tính năng: [weekly-progress.md](../../product/weekly-progress.md). Chỉ ghi "Đạt" cho những kiểm tra đã chạy thật.

## 1. Lệnh và kết quả

| Kiểm tra | Lệnh | Kết quả |
| --- | --- | --- |
| Backend, gồm test PostgreSQL | `mvn -f backend/pom.xml -Dgoal.integration=true -Dauth.integration=true clean verify` (container `postgres:17` dùng một lần, cổng 55433) | **Đạt**: 92 test, 0 lỗi, 0 bỏ qua (có `WeeklyProgressApiPostgresIntegrationTest` 7 ca, `WeeklyProgressControllerTest` 8, `WeeklyProgressServiceTest` 9, `WeeklyProgressJdbcRepositoryTest` 3) |
| Frontend test | `npm test` | **Đạt**: 37 test (có `weekly-progress.test.js` 10 ca) |
| Frontend lint, build | `npm run lint`, `npm run build` | **Đạt** |
| Trình duyệt trên stack thật (React + Spring Boot + PostgreSQL) | Vite dev, `spring-boot:run`, tài khoản thử mới | **Đạt**, các bước ở mục 3 |
| CI trên GitHub | Actions | **Chưa chạy** |

Ghi chú: cần `clean` khi chạy Maven sau khi đổi nhánh, vì thư mục `backend/target` còn lớp test biên dịch từ nhánh khác. Máy Windows có múi giờ `Asia/Saigon` bị PostgreSQL 17 từ chối nên đã đặt `JAVA_TOOL_OPTIONS=-Duser.timezone=Asia/Ho_Chi_Minh`.

## 2. Đối chiếu tiêu chí chấp nhận và Definition of Done

| Mục | Trạng thái | Ghi chú |
| --- | --- | --- |
| AC1: Hiển thị số việc hoàn thành và tổng số việc của từng tuần | Đạt | Test API+DB; demo: tuần có 3 việc hiện "2/3 việc", 67% |
| AC2: Chọn được tuần trước hoặc tuần sau | Đạt | Demo: tuần trước "1/1", tuần sau "0/1"; test PostgreSQL cho tuần trước và sau |
| AC3: Tuần không có việc hiện 0/0, không báo lỗi | Đạt | Test backend và client; demo: "0/0 việc", dòng "Chưa có công việc nào trong tuần này." |
| Ranh giới tuần (thứ Hai, Chủ Nhật, tuần kề, năm, năm nhuận) | Đạt | `countsTheWeekAcrossGoalsWithInclusiveBoundaries`, `yearBoundaryWeek`, `WeeklyProgressServiceTest` |
| Kiểm tra quyền sở hữu ở server | Đạt | Công việc của tài khoản khác không được đếm; chưa đăng nhập 401 |
| Validate ở server | Đạt | Ngày sai định dạng và ngoài 2000–2100 là 400 |
| Bản nháp không được đếm; đọc không có tác dụng phụ | Đạt | Test PostgreSQL |
| Không đánh giá mức độ hiểu bài | Đạt | Chỉ có số đếm và phần trăm; có ghi chú trên giao diện |
| Lỗi rõ ràng, không báo giả | Đạt | Giao diện: tải, lỗi (có "Thử lại"), chưa đăng nhập |
| Không ghi mật khẩu, API key, nội dung học vào log | Chưa kiểm | Chỉ rà mã: endpoint chỉ đọc, bộ xử lý lỗi chỉ ghi tên lớp lỗi; chưa kiểm log của server đang chạy |
| Review bởi Developer khác | **Chưa làm** | Mới có tự rà soát ở mục 4 |
| Merge vào `main`, CI | **Chưa làm** | Chưa tạo PR; CI chưa chạy |
| Có thể demo | Đạt | Mục 3 |

Không khai báo story là Done: thiếu review độc lập, merge và CI, và 3 điểm cần PO xác nhận ở [weekly-progress.md](../../product/weekly-progress.md) mục 6 chưa có trả lời.

## 3. Demo trình duyệt (đã chạy)

Tài khoản thử, công việc tạo bằng SQL (chưa có API tạo hoặc đánh dấu hoàn thành trên nhánh này). Hôm nay là Chủ Nhật 2026-11-08, tuần hiện tại là 02/11 – 08/11/2026.

1. Đăng ký, tạo mục tiêu; chèn 5 công việc: thứ Hai (xong), thứ Tư (xong), Chủ Nhật (chưa), thứ Sáu tuần trước (xong), thứ Ba tuần sau (chưa).
2. Mở "Tiến độ" (`#weekly`): "02/11 – 08/11/2026", nhãn "Tuần hiện tại", "2/3 việc", thanh 67%, "2 hoàn thành · 1 còn lại"; bảng ngày: T2 1/1, T4 1/1, CN 0/1, còn lại 0/0.
3. "← Tuần trước": "26/10 – 01/11/2026", "1/1 việc", 100%, công việc nằm ở T6.
4. "Tuần sau →" hai lần: "09/11 – 15/11/2026", "0/1 việc", 0%, công việc nằm ở T3.
5. "Tuần sau →" tiếp: "16/11 – 22/11/2026", "0/0 việc", "Chưa có công việc nào trong tuần này.", không có thông báo lỗi.
6. "Tuần này": quay về "02/11 – 08/11/2026".
7. Các nút của hai hàng thẳng hàng, cùng chiều cao 40px.

## 4. Tự rà soát mã (chưa thay cho review của người khác)

- Truy vấn nối `study_goals` và lọc `user_id` trong cùng một câu lệnh, tham số hóa; không ghép chuỗi người dùng vào SQL.
- Endpoint chỉ đọc; không khóa, không đổi dữ liệu (có test kiểm version giữ nguyên).
- Ngày được chặn trong khoảng 2000–2100 trước khi tính `plusDays`, tránh tràn ngày (`LocalDate.MAX`).
- Đã sửa trong lúc làm: bài test PostgreSQL của auth (`AuthPostgresIntegrationTest`) vẫn kiểm cấu trúc phản hồi cũ trong khi controller đã trả `{data: ...}` và `{error: ...}`, nên fail khi bật `-Dauth.integration=true` (CI backend cũng bật cờ này). Chỉ sửa test, không đổi mã chạy thật.
- Cập nhật 2026-11-12: controller đã chuyển sang `ApiResponse` và `ApiExceptionHandler` chung (cùng mã lỗi và khuôn phản hồi, 27 test tuần vẫn đạt).

## 5. Giới hạn chưa kiểm chứng

- Công việc đều tạo bằng SQL; chưa kiểm luồng "tạo hoặc xác nhận bản nháp, đánh dấu hoàn thành rồi xem tiến độ tuần" vì API tạo và đánh dấu hoàn thành chưa có trên nhánh này.
- Chưa kiểm bằng trình duyệt thứ hai cho tài khoản khác; truy cập chéo chỉ kiểm bằng test API+DB.
- Chưa có test component React; giao diện kiểm bằng test logic thuần và demo thủ công.
- Chưa kiểm hiệu năng với số lượng công việc lớn (truy vấn gom theo ngày trong một tuần, chưa có chỉ mục riêng cho `scheduled_date`; đã có chỉ mục `(goal_id, scheduled_date)`).
- Chưa kiểm hiển thị trên màn hình điện thoại.

## 6. Kiểm chứng sau khi gộp vào `dev2` (2026-11-08)

`sprint4/weekly-progress` được gộp vào `dev2` (đã gồm `dev2-sprint3`); xung đột giữa phần dev1 và phần hợp đồng `/api/v1` được giải quyết trong commit gộp.

| Kiểm tra | Kết quả |
| --- | --- |
| Backend `mvn ... -Dgoal.integration=true -Dauth.integration=true test` trên `postgres:17` dùng một lần | **Đạt**: 179 test, 0 lỗi, 0 bỏ qua (gồm 6 test mới `PlanDraftControllerTest` cho các endpoint AI chuyển sang `/api/v1`) |
| Frontend `npm test`, `npm run lint`, `npm run build` | **Đạt**: 54 test, lint và build sạch |
| Trình duyệt, luồng mới có thể chạy nhờ gộp: mục tiêu có 3 việc chưa xong → "Tiến độ" hiện 0/3 → trang Lịch học đánh dấu 2 việc xong (DB `completed = true`) → "Tiến độ" hiện 2/3, 67%, Chủ Nhật 2/3 | **Đạt** |
| Các trang `#goals`, `#daily`, `#weekly` mở được, không có yêu cầu API nào thất bại ở lần chạy cuối | **Đạt** |

Giới hạn còn lại: chưa kiểm tay các tính năng AI của dev1 (đọc đề cương, sinh lịch bằng Gemini) sau khi gộp, vì cần khóa API; chỉ có test cho hợp đồng (phiên, quyền sở hữu, khuôn lỗi) của các endpoint đó.

## 7. Kiểm chứng sau khi gộp `main` vào `dev2` (2026-11-12)

Gộp `main` (29dbf6d) vào `dev2`, giải quyết 15 xung đột bằng cách lấy bản của `main` rồi thêm lại phần tuần; bỏ `TaskChecklistPage` trùng; controller dùng `ApiResponse`.

| Kiểm tra | Lệnh | Kết quả |
| --- | --- | --- |
| Backend | `mvn -f backend/pom.xml clean test -Dgoal.integration=true -Dauth.integration=true` (postgres:17 dùng một lần, cổng 55434, DB `study_planner_test`) | **Đạt**: 323 test, 0 lỗi, 25 bỏ qua (cờ calendar, planning, PDF riêng không bật) |
| Test tuần sau khi đổi controller | `-Dtest='WeeklyProgress*Test'` cùng cờ | **Đạt**: 27/27 |
| Frontend | `npm run lint`, `npm test`, `npm run build` | **Đạt**: 102 test, lint và build sạch |

Chưa làm: kiểm bằng trình duyệt sau khi gộp, chạy lệnh `verify` đầy đủ, CI, review độc lập. Các lần chạy trên không cộng gộp với số liệu 2026-11-08 ở trên.
