# Cài đặt và chạy local

## Chuẩn bị

- Java 21, Maven 3.9+, Node.js 22.12+ và npm. CI frontend dùng Node 24.
- PostgreSQL; `compose.yml` cung cấp PostgreSQL 17. Không cần Docker nếu đã có instance phù hợp.
- Chọn đúng thư mục và nhánh bằng `git status` và `git branch --show-current`.
  Mỗi worktree có `.env` riêng; mở thư mục gốc không tự chạy code trong worktree khác.

## 1. Cấu hình backend

Từ root checkout, tạo file nếu chưa có:

```powershell
if (-not (Test-Path -LiteralPath .env)) { Copy-Item .env.example .env }
```

Điền bằng trình soạn thảo. Backend import root `.env` khi chạy từ root hoặc `backend/`;
biến môi trường trong shell ưu tiên hơn file. Đổi cấu hình thì khởi động lại backend.

| Biến | Ý nghĩa |
| --- | --- |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Kết nối PostgreSQL của môi trường đang chạy |
| `PORT` | Cổng backend; mặc định 8080 |
| `SESSION_COOKIE_SECURE` | `false` cho HTTP local, `true` khi triển khai HTTPS |
| `GEMINI_API_KEY`, `GEMINI_MODEL` | Gemini dùng cho lập kế hoạch và Recall; model phải khả dụng với key |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | OAuth Web client cho Calendar |
| `GOOGLE_REDIRECT_URI`, `APP_URL` | Callback và địa chỉ frontend |
| `CALENDAR_TOKEN_KEY` | Khóa Base64 32 byte để mã hóa token Calendar; phải giữ ổn định |

Không thay `.env` có sẵn bằng cấu hình mẫu. Không commit file này, JSON OAuth, token
hoặc key thật. `.env.example` chỉ có tên biến và giá trị mẫu không bí mật.
Thiếu Gemini vẫn dùng được mục tiêu/công việc thủ công; thiếu Calendar vẫn dùng được lịch trong app.

## 2. PostgreSQL

Máy mới dùng Compose, sau khi đặt mật khẩu trong `.env`:

```powershell
docker compose up -d --wait
```

Compose dùng database/user `study_planner`, cổng 5432 và volume riêng. Nếu dùng instance
khác, đặt `DB_URL` phù hợp; không chạy instance thứ hai trên cùng cổng hoặc xóa volume để sửa lỗi.

Flyway tự áp migration trong `backend/src/main/resources/db/migration/`:

| Phiên bản | Nội dung |
| --- | --- |
| V1–V3 | Dữ liệu gốc, receipt xác nhận AI, nguồn chủ đề của task |
| V4 | Google Calendar |
| V5–V6 | Cấu hình kế hoạch bền vững, quỹ chung/ngày |
| V7 | Outbox đồng bộ Calendar |
| V8 | Active Recall |
| V9 | Study sessions/Timer |

Một số nhánh phát triển trước đây dùng V8 cho Timer và V9 cho Recall. Trước khi dùng
DB đã chạy nhánh đó, đối chiếu lịch sử Flyway với mã hiện tại; không đổi tên migration
đã áp hoặc tự `repair`/xóa schema. DB local của từng thành viên không mặc nhiên có cùng lịch sử.

## 3. Chạy backend và frontend

Terminal backend, từ root:

```powershell
mvn -f backend/pom.xml spring-boot:run
```

Terminal frontend:

```powershell
Set-Location frontend
npm.cmd ci
npm.cmd run dev
```

Bash dùng `npm`. Khi dependencies không đổi, không cần chạy lại `npm ci` mỗi lần mở app.
Mở `http://127.0.0.1:5173`.

Vite proxy `/api` và `/actuator/health` tới backend 8080. Nếu backend dùng cổng khác,
đặt địa chỉ trong `frontend/.env.local` rồi khởi động lại Vite:

```env
BACKEND_URL=http://127.0.0.1:18084
```

Đây chỉ là ví dụ cổng; dùng đúng `PORT` của backend. File frontend không chứa key hay mật khẩu.
`npm run build` tạo `frontend/dist/`. Preview 4173 không có dev proxy; triển khai cần reverse proxy cùng origin.

## 4. Gemini và Google Calendar

Gemini: điền key/model ở backend, restart, rồi thử một nguồn văn bản ngắn tự tạo trong
Recall hoặc một bản nháp kế hoạch. Lỗi provider không được thay bằng kết quả giả.

Calendar cần Google Calendar API đã bật, OAuth Web client và tài khoản được phép thử
nếu app ở chế độ Testing. Callback local phải khớp chính xác:

```text
http://127.0.0.1:5173/api/v1/calendar/oauth/callback
```

Đặt `APP_URL=http://127.0.0.1:5173`, dùng cùng host khi đăng nhập và callback; không trộn
`localhost` với `127.0.0.1`. Mỗi người cấp quyền lịch Google của mình; không chia sẻ token.
Không tạo lại `CALENDAR_TOKEN_KEY` khi đã có kết nối cần giữ.

Sau khi xuất, worker đồng bộ sửa/xóa chạy mặc định. Có thể tạm dừng bằng Spring property
`calendar.sync.enabled=false`; hàng đợi vẫn được giữ. Chỉ xuất/sửa lịch thật khi chủ động
thử tính năng đó. Không xóa receipt/outbox để làm mất thông báo lỗi.

## 5. Kiểm tra sẵn sàng và dừng app

- Kiểm tra `/actuator/health` ở đúng cổng backend và qua `http://127.0.0.1:5173/actuator/health`.
- Health UP chỉ chứng minh app sẵn sàng nhận kết nối, không chứng minh Gemini/Google đã hoạt động.
- Vào `/` khi chưa đăng nhập để xem landing; `#goals`, `#daily`, `#timer`, `#recall`, `#weekly` là các màn hình chính.
- Dừng bằng Ctrl+C ở terminal đang chạy. Với tiến trình nền, xác định đúng PID/port/checkout;
  không kill toàn bộ Java/Node/PostgreSQL.
- Tắt app không xóa code hoặc DB. Giữ `.env` và khóa mã hóa; đăng nhập lại nếu session mất.
- Backup DB và cấu hình riêng trước khi chuyển máy hoặc dọn thư mục chứa PostgreSQL portable.
  Khả năng restore chưa được diễn tập trong các bằng chứng hiện có.

Lệnh kiểm thử và database test: [harness](.agents/harness/README.md).
