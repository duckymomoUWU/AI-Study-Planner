# Review và tích hợp 8 branch — 2026-11-08

Nhánh đích: `integration/sprint2-sprint3-all8-test`. Nguồn đúng/sai là `requirements.md`, rồi hợp đồng API đã chấp nhận trong `docs/backend-api-rules.md`.
Mỗi branch làm một phần user story; không đánh giá một branch thiếu chức năng do branch khác phụ trách.
Tài liệu mẫu dự án khác không được xem là yêu cầu. Review dựa trên commit đã fetch từ GitHub; không suy kết quả từ màu CI trong ảnh.

## Mỗi branch đã làm gì

| Branch | Commit đã đọc | Chức năng trong code | Đối chiếu / quyết định tích hợp |
| --- | --- | --- | --- |
| `dev1` | `a5737fc3` | Nền auth (session/CSRF) và goal policy/repository/service; có cả các thành phần kế thừa từ lịch sử chung. | Auth đáp ứng phần tài khoản; hợp đồng endpoint/envelope cũ cần thống nhất với nhánh goal. |
| `dev4-sprint2` | `a5737fc3` | Cùng chính xác commit với dev1, gồm tài liệu API sprint 2. | Không phải một chức năng độc lập mới; merge không tạo thêm hai bản auth. |
| `dev2/sprint2` | `6ff74939` | API goal CRUD, pagination, validate, ownership, optimistic lock, error envelope và UI/client tương ứng. | Phù hợp US goal; dùng làm hợp đồng chung /api/v1. Không yêu cầu nhánh này phải làm task/AI. |
| `dev4-sprint2-UI` | `ff637891` | Giao diện auth/goal/calendar, responsive/layout; nhiều thao tác lịch dùng dữ liệu mô phỏng/localStorage. | Đã có UI; lưu mock không tính là hoàn thành nghiệp vụ server. Giữ preview riêng, nối trang thật vào API. |
| `dev2-sprint3` | `48a76dd0` | Đọc task theo ngày/goal, đánh dấu hoàn thành/mở lại, kiểm tra owner, version/no-op; UI trạng thái và lỗi. | Phù hợp phần theo dõi task. Trong phạm vi nhánh này, create/edit/delete để US2 triển khai. |
| `dev4-sprint3` | `1f0744ea` | API và UI tiến độ goal: total/completed/remaining/rate; 0/0; cập nhật theo version. | Phù hợp requirements: đo số task hoàn thành, không đánh giá hiểu bài; đọc không tăng version. |
| `US2/sprint3` | `6a08a12b` | CRUD task, ràng buộc ngày/ngân sách, overdue và direct PATCH; bản mới sửa font. | Logic chính phù hợp. API/DTO/error khác nhánh task-completion; frontend có nhánh giả dữ liệu khi request lỗi. Đã chuyển phần nghiệp vụ vào hợp đồng chung. |
| `sprint3/AIfeat` | `3c79a014` | Gemini adapter, plan_drafts, preview/confirm UI, syllabus/PDF và prototype lịch có drag-drop/best-fit. | Có luồng AI nhưng confirm gốc thiếu kiểm tra version/expiry/budget và receipt; frontend có thành công giả. Giờ/recurrence chưa có schema; PDF ngoài MVP. Đã tích hợp phần draft/confirm theo requirements, ghi rõ phần prototype chưa nhận. |

## Cách merge và chỗ đã sửa

- Tiếp nối nhánh test 6 branch sẵn có; merge cập nhật mới nhất của US2 và AIfeat bằng merge commit, giữ lịch sử nguồn.
- Giữ auth/session/CSRF, goal CRUD, task-completion và progress đã có; xử lý các conflict hợp đồng bằng adapter thật.
- Tích hợp US2 tạo/sửa/xóa/overdue, direct PATCH, kiểm tra ngân sách với khóa goal, tăng version và số phút vượt.
- Tích hợp Gemini và lưu draft. Thay confirm không kiểm tra bằng xác nhận nguyên tử, validate toàn bộ, version/expiry và receipt PostgreSQL chống gửi trùng.
- Đồng bộ frontend `/api/v1`, envelope và CSRF; thêm màn hình chỉnh bản nháp rồi xác nhận; task editor và dời task dùng PATCH.
- Không dùng frontend fallback/localStorage để giả một thao tác backend đã lưu. Giữ màn hình calendar-preview có nhãn mô phỏng.
- Thêm migration V2; giữ nguyên V1. Không gộp thẳng các phiên bản shared controller cũ vì sẽ làm lệch auth/goal/task/progress của các branch khác.
- Các thay đổi kiểu lịch giờ/lặp tuần/best-fit liên môn/PDF/tự dọn cache theo tài khoản không được bật trong luồng lưu thật: ngoài MVP hoặc chưa có schema.
  Vì vậy đây là tích hợp có xử lý xung đột theo requirements, không cam kết giữ nguyên mọi dòng code prototype của cả 8 branch.
- Phát hiện framework DEBUG đang ghi request body trong môi trường thử; thêm cấu hình console INFO để tránh lộ mật khẩu/nội dung học.

Hợp đồng mới: [task và plan draft](../product/integrated-tasks-and-plans.md).

## Giới hạn và phần cần quyết định

AI thật cần key và tên model backend. Chưa xác minh chất lượng/kết nối model thật; test dùng proposal cố định qua mock.
Không dùng mặc định `gemini-3.8-flash` trong branch để khẳng định provider có model đó.
Không có browser kết nối trong phiên làm việc: chưa chứng minh tương tác hiển thị bằng browser/E2E.
Receipt chưa có TTL dọn định kỳ; được giữ để chống trùng và xóa theo cascade khi xóa dữ liệu liên quan.
`error.context` là mở rộng envelope cho số phút; validation cũ vẫn là `error.details[]`.

## Cách tự test

Nhánh được import vào repository gốc để bạn chuyển sang thử mà không thay main:

```powershell
git switch integration/sprint2-sprint3-all8-test
```

Trong phiên này frontend được chạy tại `http://127.0.0.1:5173`, backend tại `http://127.0.0.1:18080`.
PostgreSQL portable riêng ở cổng 55432: `study_planner_test` dùng cho test tự động, `study_planner_demo` cho chạy app.
Không dùng dữ liệu dự án hiện hữu. Database và tiến trình này là local/tạm, có thể dừng khi đóng máy.

Để chạy lại trên môi trường thông thường, thiết lập DB theo `setup.md` và truyền biến môi trường vào Java.
Từ root: `mvn -f backend/pom.xml verify`, sau đó `mvn -f backend/pom.xml spring-boot:run`.
Trong frontend: `npm ci`, `npm run dev`. BACKEND_URL trỏ vào port backend; không dùng biến VITE cho secret.
Chạy integration test chỉ với DB dùng riêng vì các test xóa dữ liệu:
`mvn -f backend/pom.xml -Dgoal.integration=true -Dauth.integration=true verify`.

Luồng thử: đăng ký → đăng nhập → tạo mục tiêu hợp lệ → tạo/sửa/hoàn thành task trong Lịch học → xem tiến độ ở Chi tiết mục tiêu.
Thử thêm task vượt phút và task vào ngày thi; server phải từ chối, dữ liệu cũ còn nguyên.
Khi có cấu hình AI: Chi tiết mục tiêu → Tạo bản nháp AI → chỉnh task → Xác nhận; trước xác nhận lịch không đổi.
Khi chưa cấu hình AI: nhận lỗi AI chưa sẵn sàng; task thủ công vẫn dùng được.


## Kết quả kiểm chứng trên nhánh tích hợp

| Tiêu chí / kiểm tra | Loại bằng chứng | Kết quả |
| --- | --- | --- |
| Backend và toàn bộ test có PostgreSQL | `mvn -f backend/pom.xml -Dgoal.integration=true -Dauth.integration=true clean verify`, Java 21/Maven 3.9.9/PostgreSQL 16.4 portable, database test riêng | 182 test; 0 fail, 0 error, 0 skip; BUILD SUCCESS |
| Frontend client, error handling và test draft mới | `npm test` (`node --test src/lib/*.test.js`) | 47/47 pass |
| Frontend lint/build | `npm run lint`, `npm run build` | Đạt |
| Auth, task CRUD, ngày/ngân sách, progress và AI unavailable qua server thật | 18 bước HTTP qua `127.0.0.1:5173` (Vite proxy → backend → PostgreSQL demo) | Đạt; có register/login/CSRF mới, reject overload/source, edit, complete, progress, delete, logout và 401 sau logout |
| Health trực tiếp và qua proxy | `/actuator/health` trên port 18080 và 5173 | 200, UP |
| Không ghi credential trong console | Gửi credential ngẫu nhiên vào register trên build cuối; kiểm tra log không chứa credential đó hoặc framework DEBUG | Đạt |
| Draft trước confirm, confirm nguyên tử, retry/key conflict/expiry/stale version, đồng thời và cross-owner | 6 test chấp nhận mới với PostgreSQL và AI mock cố định; thêm 4 test MockMvc draft controller | Đạt trong bộ backend ở trên |
| UI/Browser/E2E | Browser runtime không có browser kết nối | Chưa chạy; HTTP không thay thế kiểm tra giao diện |
| Kết nối/chất lượng provider Gemini thật | Chưa có cấu hình key/model dùng để xác minh | Chưa chạy; không suy từ mock hoặc heuristic |
| Kiểm tra diff/merge | `git diff --check`; kiểm tra ancestry của cả 8 tip | Diff sạch; 8/8 tip là ancestor của merge commit `37cd459`, đã fetch lại để đối chiếu |

Log chạy local được giữ ngoài Git: `backend-integration.log`, `frontend/frontend-test.log`, `live-smoke.log`.
Không chạy lại từng branch độc lập và không khẳng định các check GitHub gốc đã xanh; kết quả trên áp dụng bản tích hợp.
