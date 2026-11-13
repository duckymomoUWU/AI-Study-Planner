# Kiểm tra và xác minh

Tài liệu này sở hữu lệnh kiểm tra. [setup](../../setup.md) sở hữu cách chạy app;
[status](../../docs/status.md) tóm tắt tình trạng; kết quả theo từng lần bàn giao nằm
ở [completed plans](../../docs/plans/completed/README.md).

## Frontend

Trong `frontend/`:

```powershell
npm.cmd ci
npm.cmd run lint
npm.cmd test
npm.cmd run build
```

Bash dùng `npm`. Không cần cài lại nếu dependencies đã có và không đổi. `npm test`
chạy `src/lib/*.test.js` bằng node:test. Đây chủ yếu là test logic/client dùng mock,
không chứng minh layout, thao tác trình duyệt, provider hay database hoạt động.

## Backend mặc định

Từ root:

```powershell
mvn -f backend/pom.xml verify -B -ntp
```

Lệnh compile, test và package. Một số suite PostgreSQL và PDF cá nhân chỉ chạy khi
bật cờ; phải báo số bị bỏ qua. Không gọi một lần build là kiểm chứng toàn bộ nghiệp vụ.

## PostgreSQL tích hợp

Chỉ dùng database dùng riêng cho test, tên `study_planner_test`. Một số suite xóa
`app_users`; không chạy trên database demo/preview/người dùng. Đối chiếu datasource
đã resolve, không chỉ đọc tên biến; `.env` và shell có thể trỏ tới môi trường khác nhau.

| Cờ | Phạm vi | Nguồn kết nối |
| --- | --- | --- |
| `goal.integration=true` | Goals, progress, study sessions và HTTP/session | `DB_URL/DB_USER/DB_PASSWORD` |
| `auth.integration=true` | Đăng ký/đăng nhập, ownership | `DB_URL/DB_USER/DB_PASSWORD` |
| `planning.integration=true` | Draft, quỹ chung, Recall PostgreSQL | `planning.test-db-url` |
| `calendar.integration=true` | Calendar và outbox | `calendar.test-db-url` |

Một số suite planning/Calendar khai báo fixture `study_planner` / `unused-local`
trong test properties. Đây là thông tin database test, không phải mật khẩu production.
Muốn chạy đủ suite, dùng instance bỏ được có cấu hình tương ứng hoặc chủ động sửa
cấu hình test trong một công việc riêng; không đổi mật khẩu instance dùng chung.

Ví dụ trên instance test đã chuẩn bị sẵn (thay cổng đúng môi trường):

```powershell
$testDbUrl='jdbc:postgresql://127.0.0.1:55433/study_planner_test'
$env:DB_URL=$testDbUrl
$env:DB_USER='study_planner'
$env:DB_PASSWORD='unused-local'
mvn -f backend/pom.xml '-Dgoal.integration=true' '-Dauth.integration=true' '-Dplanning.integration=true' "-Dplanning.test-db-url=$testDbUrl" '-Dcalendar.integration=true' "-Dcalendar.test-db-url=$testDbUrl" test -B -ntp
```

Chỉ dùng thông tin ví dụ cho database test riêng. Cờ Maven không tự tạo PostgreSQL.
Google được mock và worker tắt trong test resources; không cần xuất lịch thật để chạy suite.
Khi chạy test liên quan Gemini, dùng mock/config test, không dùng key thật làm cơ sở
cho kỳ vọng chính xác. Không in `.env` hoặc response provider để chẩn đoán.

## PDF cá nhân tùy chọn

Một số ca nhận `-Dsyllabus.pdf=...`, `-Dsyllabus.network-pdf=...`,
`-Dsyllabus.testing-pdf=...`. Chỉ trỏ tới file được phép dùng; không đưa tài liệu cá nhân
vào Git. Thiếu file thì báo skip, không tự kết luận parser lỗi hoặc mọi PDF đều đọc đúng.

## Kiểm tra thủ công

Theo luồng bị tác động, không cần chạy provider thật cho mọi thay đổi:

1. Đăng nhập và thử truy cập chéo tài khoản; dữ liệu phải được bảo vệ ở server.
2. Tạo mục tiêu từ đề cương/nhập tay → duyệt chủ đề/ngày thi/quỹ → AI draft → chỉnh → xác nhận.
3. Thiếu quỹ → thấy việc chưa xếp → lưu phần đã xếp/xếp lại; retry không tạo trùng.
4. Lịch học → Timer → pause/reload/resume/finish → chọn hoàn thành hoặc học tiếp sau.
5. Đối chiếu dự kiến/thực học; sửa dự kiến quá quỹ phải bị chặn và giữ dữ liệu cũ.
6. Recall → lưu nguồn → sinh nháp → sửa/xác nhận → lật đáp án → Nhớ/Chưa nhớ → reload.
7. Khi có phạm vi thử Calendar thật: chọn lịch/giờ sinh hoạt → xem trước → xác nhận → kiểm tra sự kiện và sửa/xóa.
8. Kiểm tra tiếng Việt, bàn phím, reduced-motion, màn hình hẹp và dữ liệu dài.

Ghi rõ thao tác qua HTTP, browser thật hay quan sát của người dùng. Test mock, SSR
render và health không thay thế bước tương tác; không khẳng định đã kiểm thử mobile
chỉ vì có media query.

## CI và giới hạn

- [Frontend workflow](../../.github/workflows/frontend.yml): Node 24, install/lint/test/build khi thay đổi frontend.
- [Backend workflow](../../.github/workflows/backend.yml): Java 21, PostgreSQL 17, verify với goal/auth integration.
- Planning/Calendar opt-in chưa được bật trong workflow; kết quả local không đồng nghĩa được bảo vệ bởi CI.
- Workflow có trong repo không chứng minh branch protection hoặc một run cụ thể đã đạt.
- Bằng chứng cũ đã chuyển sang [verification history](../../docs/archive/verification-history-2026-11-13.md).
- Thay đổi chỉ tài liệu: kiểm tra liên kết nội bộ, code fence, stale references và diff;
  không cần chạy lại toàn bộ suite ứng dụng nếu không đổi hành vi/code.
