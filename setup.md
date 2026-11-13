# Application Runbook: AI Study Planner local

## Scope

Chạy frontend/backend và database local để kiểm tra luồng đề cương → mục tiêu → AI draft → xác nhận → lịch học → Google Calendar. Cấu hình và khởi động thuộc tài liệu này; [verification harness](.agents/harness/README.md) sở hữu lệnh test và giới hạn bằng chứng.

## Prerequisites

- JDK 21, Maven 3.9+, Node.js 22.12+ và npm; Docker Compose nếu dùng database từ `compose.yml`.
- Chọn đúng checkout/branch. Phiên PO hiện làm việc trong `C:\project\AI-Study-Planner\.worktrees\core-syllabus`, nhánh `integration/sprint2-sprint3-all8-test`; mở checkout chính không tự chuyển các thay đổi chưa commit ở worktree này sang đó.
- PostgreSQL, user và database đã tạo; DB_URL trỏ đúng nơi. Compose dùng PostgreSQL 17; máy PO hiện có PostgreSQL portable 16.4 với dữ liệu cũ. Không chạy hai database trên cùng cổng hoặc thay database hiện có bằng database rỗng.
- Gemini key/model để sinh đề xuất AI. Google OAuth Web client, tài khoản test đã được cho phép và khóa mã hóa token để dùng Calendar. Thiếu provider không đồng nghĩa được tạo dữ liệu giả.

### File môi trường

Từ root dự án, chỉ tạo bản mới nếu chưa có:

```powershell
if (-not (Test-Path -LiteralPath .env)) { Copy-Item .env.example .env }
```

Điền cấu hình trong `.env` bằng trình soạn thảo, không in secret ra terminal. Backend đã khai báo import `.env` khi chạy từ root hoặc `backend/`; shell environment vẫn ưu tiên hơn file. Cú pháp `KEY=value`, không thêm `export`/dấu nháy. Restart backend sau khi đổi cấu hình.

| Biến | Vai trò |
| --- | --- |
| DB_URL / DB_USER / DB_PASSWORD | JDBC và database; mẫu trỏ database `study_planner` trên 5432 của Compose |
| PORT | Cổng backend, mặc định 8080 |
| SESSION_COOKIE_SECURE | false cho HTTP local; HTTPS production cần cấu hình phù hợp |
| GEMINI_API_KEY / GEMINI_MODEL | Chỉ backend gọi Gemini; model mặc định trong mẫu là `gemini-flash-lite-latest`, cần model khả dụng cho key |
| GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET | OAuth Web client, không phải Gemini key |
| GOOGLE_REDIRECT_URI / APP_URL | Callback và địa chỉ frontend |
| CALENDAR_TOKEN_KEY | Base64 của 32 bytes ngẫu nhiên, AES-256-GCM cho token; giữ ổn định và backup cùng DB |

`.env` bị Git ignore; chỉ `.env.example` không có key thật được commit. Không đưa key, client secret hoặc token vào `frontend/`, `VITE_*`, log, screenshot hoặc tài liệu. Tệp JSON OAuth tải về cũng chứa secret, không thêm vào repo.

Nếu chưa có CALENDAR_TOKEN_KEY, xem lệnh tạo trong `.env.example`. Không tạo lại nếu đã có token kết nối cần giữ. Có thể phải kết nối Google lại nếu mất/đổi khóa.

## Start

Các lệnh sau chạy từ root checkout cần sử dụng.

### Database

Nếu là máy mới dùng Compose, sau khi điền mật khẩu trong `.env`:

```powershell
docker compose up -d --wait
```

Compose mặc định PostgreSQL 17, database/user `study_planner`, port 5432 và volume riêng. Nếu máy đã có database cũ/portable, dùng instance đó và điền DB_URL tương ứng; không tạo instance thứ hai để thay dữ liệu cũ.

### Backend

```powershell
mvn -f backend/pom.xml spring-boot:run
```

Hoặc `scripts/start-backend.bat`. Backend dùng `.env`; không cần đặt lại API key mỗi lần mở terminal. Nếu môi trường shell còn DB_URL/PORT/key cũ, chúng vẫn override `.env`.

V1–V4 trong `backend/src/main/resources/db/migration/` được Flyway áp khi cần; không sửa migration đã áp hoặc drop bảng để xử lý lỗi. Output Maven vẫn nằm ở `backend/target/`.

### Frontend

Terminal riêng từ root:

```powershell
Set-Location frontend
npm.cmd ci
npm.cmd run dev
```

Bash dùng `npm` thay `npm.cmd`. Khi dependencies đã có và không đổi thì không cần cài lại. Mở `http://127.0.0.1:5173`. Vite proxy `/api` và `/actuator/health`; không mở CORS rộng để thay cho proxy.

Nếu backend không dùng 8080, điền `BACKEND_URL=http://127.0.0.1:<PORT>` trong `frontend/.env.local`, restart Vite. File frontend này chỉ chứa địa chỉ proxy, không chứa secret. Vite preview 4173 không có dev proxy; production cần reverse proxy riêng.

### Cấu hình Google OAuth

1. Trong Google Cloud project, bật Google Calendar API.
2. Google Auth Platform → Branding/Audience: cấu hình tên/email app. Với nhóm dùng Gmail hoặc tài khoản bên ngoài tổ chức, chọn External, giữ Testing khi đang phát triển và thêm đúng tài khoản Google của từng người vào Test users.
3. Data Access: thêm `https://www.googleapis.com/auth/calendar.readonly` và `https://www.googleapis.com/auth/calendar.events` theo hợp đồng hiện tại.
4. Clients → Create client → Web application. Đăng ký chính xác Authorized redirect URI:

```text
http://127.0.0.1:5173/api/v1/calendar/oauth/callback
```

5. Điền Client ID/Secret vào `.env`; đặt GOOGLE_REDIRECT_URI đúng URI trên và APP_URL=`http://127.0.0.1:5173`. Giữ nguyên CALENDAR_TOKEN_KEY hiện có, restart backend.
6. Đăng nhập app, vào Lịch học → Đưa lên Google Calendar → Kết nối Google Calendar và cấp quyền. Luôn dùng cùng host 127.0.0.1 cho phiên local; localhost và 127.0.0.1 không thay thế lẫn nhau trong callback/cookie.

OAuth client dùng chung cho app; mỗi người tự cấp quyền lịch Google riêng. Không chia sẻ token người dùng. `redirect_uri_mismatch`: kiểm tra URI khớp chính xác; `403 access_denied` ở chế độ Testing: kiểm tra email đã nằm trong Test users. Đây không phải lỗi cần đổi Gemini key.

## Readiness

Health trực tiếp backend và qua proxy phải trả `status: UP`; lấy lệnh từ verification harness và thay cổng đúng `.env`. API Calendar chưa đăng nhập phải trả 401. Health không chứng minh provider có quyền hoặc lịch đã được xuất.

Observed PO runtime ngày 2026-11-11: frontend 5173, backend 18083, PostgreSQL 5432/database demo riêng. Đây là giá trị local quan sát được, không phải mặc định của `.env.example` cho mọi thành viên.

## Deterministic State

Test tự động dùng database disposable riêng; đọc harness trước khi bật integration flags. Không reset app demo hoặc xóa users/tasks của người khác. Với thử thủ công, tạo một mục tiêu nhận diện được và ngày thi ở tương lai, chỉ dùng tài liệu được phép. Không gọi AI thật hoặc xuất Calendar thật chỉ để chạy một test không cần provider.

## Interface

- Mục tiêu: tải đề cương → kiểm tra chủ đề/nguồn và ngày thi → nhập ngày bắt đầu/phút → sinh draft → rà/xác nhận. Chưa confirm thì chưa có task để xuất.
- Lịch học: xem tuần/ngày, lọc mục tiêu, hoàn thành công việc và mở Calendar.
- Calendar: chọn lịch nhận có quyền ghi và các lịch cần kiểm tra busy, chỉnh giờ sinh hoạt, xem trước rồi xác nhận. Chỉ confirm mới ghi lên Google.
- Nếu lịch đã chọn không còn trong danh sách Google, app phải hiển thị cảnh báo và giữ lựa chọn. Người dùng bỏ chọn nếu không dùng hoặc khôi phục quyền; không âm thầm xem nó là rảnh.
- Nếu Google web đã thấy mà điện thoại chưa có, kiểm tra đúng tài khoản, đúng lịch được bật và đồng bộ/refresh trên điện thoại. Không xuất lại để xử lý vấn đề hiển thị mobile.

## Runtime Evidence

Đọc stdout/stderr của tiến trình mình khởi động và report Maven/npm tương ứng. Runtime local do agent chạy có log trong `.tools/*.log` (Git ignore); tên file phụ thuộc lần chạy, không là API ổn định. Không in `.env`, cookie, authorization code, token hay nội dung response chứa secret. Phân biệt HTTP smoke, browser thao tác thật và ảnh/người dùng xác nhận.

## Ownership And Cleanup

Dừng bằng Ctrl+C ở terminal của tiến trình mình chạy; nếu chạy ngầm, kiểm tra PID/port/cwd trước khi dừng. Không kill tất cả java/node/postgres hoặc xóa volume để đóng app. Tắt app không xóa code và DB đã lưu nhưng session cần đăng nhập lại; giữ `.env` để provider được nạp ở lần mở sau.

Database portable PO nằm trong thư mục Temp của máy, không được coi là lưu trữ bền vững. Trước khi dọn Temp/chuyển máy, cần backup DB và `.env`, rồi kiểm chứng restore. Tài liệu hiện chưa có recovery rehearsal hoặc script backup/restore đã được xác minh.

## Validation

Calendar update/delete sync runs automatically while backend is running: first tick after 30 seconds, up to 3 jobs per tick, then 15-second delay. It uses existing Google credentials/scopes and CALENDAR_TOKEN_KEY; no new .env entry is required. Stop automatic processing temporarily with the shell JVM property `-Dcalendar.sync.enabled=false` before `-jar` (or equivalent Spring property). Pending work remains in PostgreSQL and resumes when enabled. Tests disable it via backend/src/test/resources/application.properties and use mocked Google calls. Status and retry are visible on Goals/Study Plan; no-slot keeps the old remote event and requires user adjustment. Do not clear the outbox or export receipts to hide a sync problem.

Theo [verification harness](.agents/harness/README.md) cho lệnh và giới hạn từng loại test; [bản bàn giao](docs/plans/completed/core-study-calendar-2026-11-11.md) giữ các lần đã thực sự chạy. Không coi hướng dẫn ở đây là bằng chứng đã thực hiện thành công trên một máy mới.

## Unknowns

Production domains/HTTPS, OAuth verification/publication, quản lý secret cho deployment, backup/restore bền vững, CI Calendar và browser/mobile automation chưa được kiểm chứng trong phiên này. Không tự suy ra các mục đó đã sẵn sàng từ kết quả local.
