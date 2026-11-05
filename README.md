# AI Study Planner

Bộ khởi tạo dự án: requirements, backend Spring Boot, database và frontend React.

- **Java 21 + Spring Boot 3.5.16 + Maven 3.9+**.
- **PostgreSQL 17**, chạy bằng Docker Compose.
- **Flyway** tự tạo/cập nhật schema khi ứng dụng khởi động.
- **React + Vite + JavaScript + CSS**, frontend trong `frontend/`.
- Kiến trúc ban đầu: một backend; chia controller/service/repository khi phát triển tính năng.

## Nội dung

- [requirements.md](requirements.md): chức năng, quy tắc nghiệp vụ và phạm vi.
- [setup.md](setup.md): cài đặt, chạy, kiểm tra và dừng môi trường.
- [frontend/README.md](frontend/README.md): chạy frontend và kết nối backend.
- [Migration DB](backend/src/main/resources/db/migration/V1__initial_schema.sql): users, goals, tasks, drafts.

Hiện có bộ khung Spring Boot, cấu hình kết nối DB, migration, health endpoint và giao diện React khởi đầu có kiểm tra kết nối backend. Chưa có đăng nhập, API nghiệp vụ hoặc tích hợp AI. Không dùng cho dữ liệu thật trước khi triển khai auth và kiểm tra ownership.

Tài liệu/Epic/User Stories và demo Next.js trước đó đã được đóng gói riêng trong file ZIP bàn giao; vẫn có thể xem lịch sử Git ở commit `ef2afef`.

## Chạy frontend

Cài Node.js 22.12+ (khuyến nghị Node 24 LTS), mở terminal tại root repository:

```bash
cd frontend
npm ci
npm run dev
```

Mở http://localhost:5173. Chạy backend/DB theo `setup.md` để thẻ kết nối kiểm tra được trạng thái thật.

## Cấu trúc repository

```text
frontend/                 React + Vite
backend/                  Spring Boot: pom.xml, src/main/ (chưa có src/test/)
AGENTS.md                 Quy ước chung cho coding agent
.agents/skills/           Skill dự án: <ten-skill>/SKILL.md
.agents/harness/README.md Hướng dẫn chạy và kiểm chứng
.github/workflows/        CI hiện có cho frontend
compose.yml               PostgreSQL dùng chung, chạy từ root
.env.example              Mẫu cấu hình local, không chứa mật khẩu thật
```

Backend dùng Maven cài trên máy (chưa có Maven Wrapper). Từ root, chạy
`mvn -f backend/pom.xml verify` để build và `mvn -f backend/pom.xml spring-boot:run`
để khởi động với môi trường DB theo [setup.md](setup.md).
JAR được tạo trong `backend/target/`.
Quy ước agent: [AGENTS.md](AGENTS.md); kiểm chứng: [harness](.agents/harness/README.md).
