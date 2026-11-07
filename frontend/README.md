# AI Study Planner — Frontend

React + Vite + JavaScript + CSS. Giao diện người dùng cho ứng dụng lập kế hoạch học tập AI Study Planner.

## Chạy local

Cài Node.js **22.12+** (khuyến nghị Node 24 LTS) và npm. Từ root repository:

```bash
cd frontend
npm ci
npm run dev
```

Mở **http://localhost:5173**. Frontend vẫn mở được khi backend tắt; thẻ kết nối hiển thị chưa kết nối, có nút thử lại và timeout 5 giây.

Để kiểm tra kết nối thật, chạy PostgreSQL và Spring Boot theo [setup.md](../setup.md) ở terminal khác. Vite proxy `/actuator/health` và `/api` đến `http://localhost:8080`, vì vậy không cần bật CORS rộng ở backend để chạy local.

### Đổi cổng backend

Sao chép `.env.example` thành `.env.local`, đặt `BACKEND_URL` (ví dụ `http://localhost:8081`), rồi khởi động lại Vite. Cấu hình PORT của backend tương ứng. Trên PowerShell có thể dùng `Copy-Item .env.example .env.local`; Bash dùng `cp .env.example .env.local`.

Vite đọc biến này ở môi trường Node để cấu hình proxy. Không đưa DB_PASSWORD hay AI API key vào frontend hoặc biến `VITE_*`; các biến `VITE_*` có thể được đóng gói và lộ trong trình duyệt.

## Giao diện Xác thực (shadcn/ui — ASP-12)

Màn hình Đăng nhập / Tạo tài khoản (`src/pages/AuthPage.jsx`) được xây dựng theo phong cách và triết lý thành phần của **[shadcn/ui](https://github.com/shadcn-ui/ui)**:
- **Composable, accessible components:** Các component UI được tách biệt rõ ràng, tự do tùy biến và sở hữu mã nguồn trong `src/components/ui/`:
  - `Button` (`src/components/ui/button.jsx`): Đầy đủ các variants (`default`, `secondary`, `outline`, `ghost`, `link`), hỗ trợ trạng thái `isLoading` với spinner SVG.
  - `Card` (`src/components/ui/card.jsx`): Cấu trúc thẻ phân lớp (`CardHeader`, `CardTitle`, `CardDescription`, `CardContent`, `CardFooter`).
  - `Input` (`src/components/ui/input.jsx`): Ô nhập liệu tiêu chuẩn có focus-ring và viền đỏ báo lỗi validation (`hasError`).
  - `Label` (`src/components/ui/label.jsx`): Nhãn truy cập hỗ trợ liên kết `htmlFor`.
  - `Alert` (`src/components/ui/alert.jsx`): Thông báo lỗi (`destructive`) hoặc thành công (`success`) kèm icon trực quan.
  - `Tabs` (`src/components/ui/tabs.jsx`): Tabs chuyển đổi mượt mà giữa Đăng nhập và Tạo tài khoản với `role="tablist"` chuẩn ARIA.
- **Tích hợp API Client (`src/lib/auth.js`):**
  - Quản lý phiên qua cookie `JSESSIONID` (`credentials: 'same-origin'`).
  - Tự động lấy và đính kèm CSRF token qua header `X-CSRF-TOKEN` cho mọi request ghi.
  - Đồng bộ trạng thái người dùng đăng nhập trong `App.jsx`, hiển thị email và nút đăng xuất trên thanh điều hướng.

## Cấu trúc

```text
frontend/
  src/
    components/
      ui/                        # Thư viện component shadcn/ui
        button.jsx               # Button variants & loading spinner
        card.jsx                 # Card layout primitives
        input.jsx                # Accessible input
        label.jsx                # Form labels
        alert.jsx                # Alert notification banners
        tabs.jsx                 # Accessible tabs
        shadcn.css               # Design tokens & component styles
      BackendStatus.jsx          # Thẻ kiểm tra kết nối backend
    hooks/
      useBackendHealth.js        # Loading, timeout, cleanup và thử lại
    lib/
      api.js                     # Health check fetch helper
      api.test.js                # Test health check
      auth.js                    # API client xác thực (login, register, /me, logout, CSRF)
      auth.test.js               # Test suite cho API client xác thực
      goals.js                   # API client quản lý mục tiêu
      goals.test.js              # Test suite cho Goal API
    pages/
      AuthPage.jsx               # Màn hình Đăng nhập / Tạo tài khoản (shadcn/ui)
      AuthPage.css               # Style responsive cho trang Auth
      GoalsPage.jsx              # Giao diện quản lý mục tiêu
      GoalsPage.css              # Style trang quản lý mục tiêu
    App.jsx                      # Điều hướng chính, quản lý session và layout
    App.css                      # Layout responsive
    index.css                    # Style nền tảng
    main.jsx                     # React entry point
  vite.config.js                 # Cổng local và proxy backend
```

## Kiểm tra và build

```bash
npm run lint
npm test
npm run build
npm run preview
```

`npm run build` tạo `dist/`. Preview chạy ở **http://localhost:4173** để xem bản build, **không có proxy backend** như dev server. Khi preview độc lập, thẻ kết nối sẽ báo chưa kết nối.

Khi triển khai, cấu hình máy chủ/reverse proxy cùng origin: `/api/*` và `/actuator/health` chuyển tới Spring Boot; các đường dẫn giao diện phục vụ `dist/`. Dev proxy không tự đi kèm bản build. Không dùng Vite dev server làm máy chủ production.
