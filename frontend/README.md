# AI Study Planner — Frontend

React + Vite + JavaScript + CSS. Đây là bộ khởi tạo frontend, chưa triển khai các chức năng nghiệp vụ MVP.

## Chạy local

Cài Node.js **22.12+** (khuyến nghị Node 24 LTS) và npm. Từ root repository:

```bash
cd frontend
npm ci
npm run dev
```

Mở **http://localhost:5173**. Frontend vẫn mở được khi backend tắt; thẻ kết nối hiển thị chưa kết nối, có nút thử lại và timeout 5 giây.

Để kiểm tra kết nối thật, chạy PostgreSQL và Spring Boot theo [setup.md](../setup.md) ở terminal khác. Vite proxy `/actuator/health` và `/api` đến `http://localhost:8080`, vì vậy không cần bật CORS rộng ở backend để chạy local.

Backend hiện chỉ có health endpoint; `/api` được chuẩn bị cho API tương lai, chưa có API mục tiêu, đăng nhập hoặc AI.

### Đổi cổng backend

Sao chép `.env.example` thành `.env.local`, đặt `BACKEND_URL` (ví dụ `http://localhost:8081`), rồi khởi động lại Vite. Cấu hình PORT của backend tương ứng. Trên PowerShell có thể dùng `Copy-Item .env.example .env.local`; Bash dùng `cp .env.example .env.local`.

Vite đọc biến này ở môi trường Node để cấu hình proxy. Không đưa DB_PASSWORD hay AI API key vào frontend hoặc biến `VITE_*`; các biến `VITE_*` có thể được đóng gói và lộ trong trình duyệt.

## Cấu trúc

```text
frontend/
  src/
    components/BackendStatus.jsx  # Thẻ kiểm tra kết nối thật
    hooks/useBackendHealth.js    # Loading, timeout, cleanup và thử lại
    lib/api.js                   # Fetch đến backend qua đường dẫn tương đối
    lib/api.test.js              # Kiểm tra phản hồi health và lỗi
    App.jsx                      # Giao diện khởi đầu, liên kết Wiki/Issues
    App.css                      # Layout responsive
    index.css                    # Style nền tảng
    main.jsx                     # React entry point
  vite.config.js                 # Cổng local và proxy backend
```

Hình kế hoạch trên trang chủ là minh họa trang trí, không phải dữ liệu học tập hoặc kết quả AI. Các thẻ Sprint là lộ trình dự kiến. Giao diện chưa đăng nhập, tạo/lưu mục tiêu hay công việc, và không gọi AI.

## Kiểm tra và build

```bash
npm run lint
npm test
npm run build
npm run preview
```

`npm run build` tạo `dist/`. Preview chạy ở **http://localhost:4173** để xem bản build, **không có proxy backend** như dev server. Khi preview độc lập, thẻ kết nối sẽ báo chưa kết nối.

Khi triển khai, cấu hình máy chủ/reverse proxy cùng origin: `/api/*` và `/actuator/health` chuyển tới Spring Boot; các đường dẫn giao diện phục vụ `dist/`. Dev proxy không tự đi kèm bản build. Không dùng Vite dev server làm máy chủ production.

## Bước phát triển tiếp

1. Chốt hợp đồng API, cơ chế xác thực/CSRF và thiết kế màn hình với nhóm backend.
2. Làm luồng đăng nhập, mục tiêu và công việc thủ công theo Issues #1–#3.
3. Tích hợp bản nháp AI và xác nhận sau khi có API tương ứng.
4. Bổ sung kiểm thử hành vi giao diện cho từng chức năng; bộ test health hiện tại chưa chứng minh nghiệp vụ MVP.
