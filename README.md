# AI Study Planner

Repo khởi tạo tối giản: requirements, môi trường chạy backend và database.

- **Java 21 + Spring Boot 3.5.16 + Maven 3.9+**.
- **PostgreSQL 17**, chạy bằng Docker Compose.
- **Flyway** tự tạo/cập nhật schema khi ứng dụng khởi động.
- Kiến trúc ban đầu: một backend; chia controller/service/repository khi phát triển tính năng.

## Nội dung

- [requirements.md](requirements.md): chức năng, quy tắc nghiệp vụ và phạm vi.
- [setup.md](setup.md): cài đặt, chạy, kiểm tra và dừng môi trường.
- [Migration DB](src/main/resources/db/migration/V1__initial_schema.sql): users, goals, tasks, drafts.

Hiện chỉ có ứng dụng Spring Boot khởi động, kết nối DB, migration và health endpoint. Chưa có đăng nhập, API nghiệp vụ, frontend hoặc tích hợp AI. Không dùng cho dữ liệu thật trước khi triển khai auth và kiểm tra ownership.

Tài liệu/Epic/User Stories và demo Next.js trước đó đã được đóng gói riêng trong file ZIP bàn giao; vẫn có thể xem lịch sử Git ở commit `ef2afef`.
