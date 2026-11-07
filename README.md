# AI Study Planner

Ứng dụng lập kế hoạch học tập hỗ trợ bởi AI: biến nội dung ôn tập thành kế hoạch học theo từng ngày, phù hợp với ngày thi và thời gian học mỗi ngày.

- **Java 21 + Spring Boot 3.5.16 + Spring Security 6 + Maven 3.9+**.
- **PostgreSQL 17**, chạy bằng Docker Compose.
- **Flyway** tự động quản lý schema cơ sở dữ liệu (`V1__initial_schema.sql`).
- **React + Vite + JavaScript + CSS**, giao diện tại `frontend/`.
- **shadcn/ui** components cho màn hình Đăng nhập / Tạo tài khoản.
- Quản lý phiên bảo mật: Session cookie `JSESSIONID` (`HttpOnly`, `SameSite=Lax`), `BCryptPasswordEncoder(12)`, và CSRF protection (`X-CSRF-TOKEN`).

## Tài liệu dự án

- [requirements.md](requirements.md): Chức năng, quy tắc nghiệp vụ và phạm vi MVP.
- [setup.md](setup.md): Cài đặt môi trường, chạy PostgreSQL, backend và frontend.
- [docs/ASP-12-auth-api-guide.md](docs/ASP-12-auth-api-guide.md): Đặc tả API xác thực (JSON contract), hướng dẫn tích hợp cho backend (Dev 2) và frontend (Dev 3/4).
- [docs/product/ASP-9-goals-api.md](docs/product/ASP-9-goals-api.md): Hợp đồng API quản lý mục tiêu (Goal CRUD).
- [frontend/README.md](frontend/README.md): Hướng dẫn chạy frontend, cấu hình proxy và thư viện UI shadcn/ui.

## Chạy dự án

### 1. Khởi động cơ sở dữ liệu (PostgreSQL)
```bash
docker compose up -d --wait
```

### 2. Khởi động Backend (Spring Boot)
Thiết lập mật khẩu DB trong PowerShell và khởi chạy:
```powershell
$env:DB_PASSWORD = "mat_khau_db_cua_ban"
mvn -f backend/pom.xml spring-boot:run
```

### 3. Khởi động Frontend (React + Vite)
```bash
cd frontend
npm ci
npm run dev
```
Mở trình duyệt tại **http://localhost:5173**.

## Cấu trúc repository

```text
frontend/                 React + Vite, giao diện shadcn/ui trong src/components/ui/
backend/                  Spring Boot: pom.xml, src/main/ và test suites trong src/test/
docs/                     Tài liệu đặc tả API, quyết định kiến trúc và hướng dẫn tích hợp
AGENTS.md                 Quy ước chung cho coding agent
.agents/harness/README.md Hướng dẫn chạy và kiểm chứng
.github/workflows/        CI tự động cho frontend và backend (PostgreSQL integration test)
compose.yml               PostgreSQL dùng chung, chạy từ root
```

## Trạng thái triển khai

- **Backend Auth (ASP-12 - Dev 1):** Hoàn thành bộ API JSON xác thực (`/api/v1/auth/csrf`, `/register`, `/login`, `/logout` và `/api/v1/me`), BCrypt băm mật khẩu, session cookie `HttpOnly`, phòng vệ CSRF và nhận diện user từ máy chủ. Đạt 100% qua bộ test 32 ca kiểm thử tự động.
- **Frontend Auth (shadcn/ui - Dev 3):** Hoàn thành màn hình Đăng nhập / Tạo tài khoản (`AuthPage.jsx`) sử dụng bộ component shadcn/ui composable (`Card`, `Button`, `Input`, `Label`, `Alert`, `Tabs`). Đồng bộ trạng thái đăng nhập, xử lý 401 và hiển thị email người dùng.
- **Goal API Foundation (ASP-9/ASP-23/24 - Dev 2):** Đã có `GoalPolicy`, `GoalJdbcRepository`, `GoalService` và test suite. Sẵn sàng kết nối `GoalController` với Auth Principal.
