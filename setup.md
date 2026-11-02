# Setup working environment

## Yêu cầu
Java **JDK 21** (có javac), Maven **3.9+**, Docker Engine/Desktop và Docker Compose v2. Git dùng để clone repo. Kiểm tra `java -version`, `javac -version`, `mvn -version`, `docker compose version`.

## Chạy local (Bash / macOS / Linux / WSL)

```bash
git clone https://github.com/duckymomoUWU/AI-Study-Planner.git
cd AI-Study-Planner
# Chọn mật khẩu local, không ghi trực tiếp vào lệnh lưu trong history:
read -rsp 'DB password: ' DB_PASSWORD; echo
export DB_PASSWORD
# Docker và Java dùng cùng biến môi trường này.
docker compose up -d --wait
mvn verify
mvn spring-boot:run
```

Mở terminal khác và kiểm tra:

```bash
curl --fail http://localhost:8080/actuator/health
# Kỳ vọng {"status":"UP"}; trạng thái có kiểm tra kết nối DB.
```

PowerShell: đặt `$env:DB_PASSWORD` trong phiên terminal trước khi chạy Compose và Maven. Không đưa giá trị thật lên Git. Có thể dùng `.env` cho Compose, nhưng Spring Boot không tự đọc file đó; phải truyền DB_PASSWORD vào môi trường tiến trình Java hoặc cấu hình Run của IDE.

## Database

- Database/user: `study_planner`, cổng local `5432`.
- Biến cấu hình: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `PORT` (backend mặc định 8080).
- Flyway chạy `V1__initial_schema.sql` khi ứng dụng kết nối lần đầu. Các lần sau chỉ áp migration mới; không sửa migration đã áp vào DB dùng chung.
- Bảng: `app_users` → `study_goals` → `study_tasks` / `plan_drafts`. Dữ liệu là rỗng ban đầu, không có tài khoản mẫu.
- Các CHECK/FK bảo vệ dữ liệu cơ bản. Ownership, tổng phút mỗi ngày, ngày task trong phạm vi và schema bản nháp vẫn cần code ở service.

```bash
docker compose exec db psql -U study_planner -d study_planner -c '\dt'
```

## Build và dừng

`mvn verify` tạo JAR trong `target/`. Chạy `java -jar target/ai-study-planner-0.0.1-SNAPSHOT.jar` với cùng biến DB_PASSWORD. Repo chưa có bộ test nghiệp vụ; build thành công không chứng minh chức năng tương lai.

Ctrl+C dừng backend. `docker compose stop` dừng DB và giữ dữ liệu. Dữ liệu nằm trong named volume; không dùng `down -v` nếu cần giữ dữ liệu. Đổi DB_PASSWORD không tự đổi mật khẩu của DB đã tạo: dùng mật khẩu ban đầu hoặc đổi bằng PostgreSQL.

Nếu cổng 5432/8080 đang bận, kiểm tra dịch vụ đang dùng rồi cấu hình cổng khác và cập nhật DB_URL/PORT tương ứng. Nếu Flyway lỗi, đọc lỗi và sửa cấu hình hoặc thêm migration; không xóa DB để che lỗi.


## Frontend React + Vite

Cài Node.js 22.12+ (khuyến nghị Node 24 LTS) và npm. Trong terminal mới tại root repository:

```bash
cd frontend
npm ci
npm run dev
```

Mở http://localhost:5173. Frontend gọi `/actuator/health` qua Vite proxy đến backend cổng 8080; `/api` cũng được proxy để tích hợp tính năng sau này. Frontend mở được khi backend tắt và hiển thị trạng thái chưa kết nối. Không cần thêm CORS rộng vào backend.

Nếu backend dùng cổng khác, sao chép `frontend/.env.example` thành `frontend/.env.local`, đổi BACKEND_URL và khởi động lại Vite. Không chuyển mật khẩu DB hoặc AI API key sang frontend.

Chạy `npm run lint`, `npm test`, `npm run build` trong `frontend/`. `npm run preview` mở bản build ở cổng 4173 nhưng không có dev proxy; production cần reverse proxy riêng cho `/api` và `/actuator/health`. Chi tiết ở [frontend/README.md](frontend/README.md).
