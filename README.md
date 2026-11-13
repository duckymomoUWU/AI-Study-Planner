# AI Study Planner

Ứng dụng giúp sinh viên chuyển đề cương, ngày thi và quỹ thời gian thành kế hoạch ôn tập,
học theo từng công việc và theo dõi tiến độ.

## Chức năng

- Landing riêng; đăng ký/đăng nhập, session và dữ liệu theo tài khoản.
- Đọc đề cương PDF/DOCX/TXT/MD, rà chủ đề và ngày thi; hỗ trợ nhập tay.
- AI tạo bản nháp để người dùng chỉnh và xác nhận; lưu phần đã xếp, giữ việc chưa xếp.
- Quỹ thời gian chung/ngày, mức mong muốn theo môn; lịch tuần/ngày và thao tác công việc.
- Timer/Pomodoro, lịch sử phiên học và thời gian thực học; xác nhận hoàn thành riêng.
- Active Recall: tài liệu → bộ thẻ nháp → chỉnh/xác nhận → tự đánh giá Nhớ/Chưa nhớ.
- Tiến độ theo công việc, mục tiêu và tuần.
- Google Calendar: xem trước và xuất lịch; cập nhật/xóa một chiều sau khi đã liên kết sự kiện.

Gemini và Google Calendar cần cấu hình riêng. Chạy được giao diện không đồng nghĩa đã
cấu hình provider hoặc đã kiểm tra toàn bộ trải nghiệm; xem [trạng thái](docs/status.md).

## Bắt đầu

1. Đọc [setup.md](setup.md), tạo `.env` riêng từ `.env.example` và chuẩn bị PostgreSQL.
2. Chạy backend: `mvn -f backend/pom.xml spring-boot:run`.
3. Trong `frontend/`: `npm ci`, sau đó `npm run dev`.
4. Mở `http://127.0.0.1:5173`; nếu backend dùng cổng khác 8080, đặt `BACKEND_URL`
   trong `frontend/.env.local` theo setup.

## Công nghệ

React + Vite, JavaScript/CSS; Java 21, Spring Boot, Spring Security, JDBC, Maven;
PostgreSQL và Flyway. Theme kem–xanh rêu; font được lưu trong frontend.

## Làm việc nhóm

- [Mục lục tài liệu](docs/README.md)
- [Yêu cầu sản phẩm](requirements.md)
- [Quy trình nhận việc và bàn giao](docs/WORKFLOW.md)
- [Hợp đồng chức năng](docs/product/README.md) và [quy tắc backend](docs/backend-api-rules.md)
- [Chạy test](.agents/harness/README.md)
- [Quy ước cho công cụ AI](AGENTS.md)

Không commit `.env`, token, mật khẩu hoặc tài liệu học cá nhân. Test tích hợp chỉ chạy
trên database kiểm thử riêng, không chạy trên database demo hay dữ liệu người dùng.
