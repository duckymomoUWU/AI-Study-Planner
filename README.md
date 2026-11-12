# AI Study Planner

Ứng dụng lập kế hoạch ôn tập từ đề cương: chọn chủ đề, đặt ngày thi và thời gian học, xem kế hoạch AI đề xuất, xác nhận rồi theo dõi việc học mỗi ngày.

## Chức năng hiện có

- Đăng ký/đăng nhập với session, CSRF và quyền sở hữu dữ liệu.
- Tạo/sửa/xóa mục tiêu; mục tiêu mới không bắt đầu trong quá khứ.
- Đọc PDF/DOCX/văn bản, rà chủ đề và ngày thi có nguồn trước khi áp dụng.
- AI tạo bản nháp theo chủ đề và quỹ phút; chỉ xác nhận mới lưu công việc.
- Tổng quan lấy dữ liệu thật; xem lịch tuần/ngày, lọc mục tiêu, theo dõi hoàn thành.
- Xuất lịch một chiều sang Google Calendar: OAuth, né giờ bận/ăn/ngủ/nghỉ, xem trước, xác nhận và chống bản sao khi thử lại.
- Giao diện kem–xanh rêu, phông Be Vietnam Pro hỗ trợ tiếng Việt. Trang lịch mẫu đã được gỡ.

## Công nghệ và cấu hình

Backend: Java 21, Spring Boot, Spring Security, Maven 3.9+, PostgreSQL và Flyway. Frontend: React, Vite, JavaScript/CSS. Compose dùng PostgreSQL 17; runtime portable local đã kiểm tra dùng PostgreSQL 16.4.

Backend đọc `.env` tại root khi chạy từ root hoặc `backend/`. Chỉ `.env.example` chứa cấu trúc cấu hình được chia sẻ qua Git; Gemini key, OAuth secret và khóa mã hóa token không nằm trong frontend/Git.

## Bắt đầu và tài liệu

- [Cài đặt, chạy app và cấu hình Google OAuth](setup.md).
- [Bản đồ tài liệu](docs/README.md) và [quy trình Harness](docs/WORKFLOW.md).
- [Yêu cầu sản phẩm](requirements.md).
- [Lệnh kiểm thử và giới hạn xác minh](.agents/harness/README.md).
- [Bàn giao core và Calendar đến 2026-11-11](docs/plans/completed/core-study-calendar-2026-11-11.md).
- [Frontend](frontend/README.md), [hợp đồng AI/scoped planning](docs/product/syllabus-revision-plan.md), [Google Calendar](docs/product/google-calendar.md).

## Repository

```text
frontend/                 React + Vite
backend/                  Spring Boot, migrations, test suites
.agents/harness/README.md  Canonical verification commands and gaps
docs/                     Product contracts, workflow and delivery records
setup.md                  Canonical environment and startup instructions
compose.yml               PostgreSQL service
.env.example              Configuration template without real credentials
```

Trạng thái code local, test tự động, kết quả người dùng quan sát và trạng thái GitHub được ghi riêng trong bản bàn giao. Không suy ra đã push, CI đã chạy hoặc production đã sẵn sàng từ việc app chạy được trên máy local.