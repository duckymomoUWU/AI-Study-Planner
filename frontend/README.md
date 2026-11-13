# Frontend

React + Vite, JavaScript/JSX và CSS. [Setup](../setup.md) chứa cách chạy/proxy;
[harness](../.agents/harness/README.md) chứa lệnh lint/test/build.

## Màn hình

| Route | Thành phần | Nội dung |
| --- | --- | --- |
| `/`, `#landing` | `LandingPage` | Landing cho khách; `/` với session hợp lệ vào Tổng quan |
| `#overview` | `OverviewPage` | Việc hôm nay, mục tiêu và dữ liệu tổng quan |
| `#login`, `#register` | `AuthPage` | Đăng nhập/đăng ký; đăng ký xong tự đăng nhập ở UI |
| `#goals` | `GoalsPage` | Mục tiêu, đề cương, quỹ chung và bản nháp AI |
| `#daily` | `StudyPlanPage`, `DailyTasksPage` | Tuần/ngày, công việc, dự kiến/thực học và Calendar |
| `#timer` | `TimerPage` | Phiên tập trung, nghỉ UI, lịch sử, lựa chọn hoàn thành công việc |
| `#recall` | `ActiveRecallPage` | Nguồn tài liệu, draft/active deck, ôn và tự đánh giá |
| `#weekly` | `WeeklyProgressPage` | Số việc hoàn thành theo tuần/ngày |

## Nơi sửa code

- `App.jsx` và `lib/navigation.js`: session, shell và điều hướng hash.
- `lib/`: client API và logic thuần; `lib/api.js` chỉ là health client.
- `components/`: các phần dùng lại như bản nháp, Calendar, thời lượng và xác nhận sau Timer.
- `theme.css`: token kem–xanh rêu, Be Vietnam Pro và trạng thái tương tác chung.
- Landing dùng thêm Lora cho tiêu đề; font/hình và giấy phép nằm trong `src/assets/`.
- CSS của chức năng phải có prefix riêng, không reset toàn bộ button/sidebar của app.

## Dữ liệu và phát triển

API luôn là đường dẫn tương đối `/api/v1/...`, cookie cùng origin và CSRF khi ghi.
Key/provider chỉ ở backend. Countdown Timer dựa snapshot server, không lấy localStorage
làm sổ thời gian. Câu hỏi hoàn thành sau FINISH chỉ ghi khi người dùng chọn.

Recall mặc định gọi API thật. `?recallMock=1#recall` là chế độ phát triển được yêu cầu rõ,
không phải fallback khi backend lỗi và không dùng làm bằng chứng tích hợp. Client test
mock không chứng minh backend hoạt động.

Vite dev proxy tới `BACKEND_URL` ở `frontend/.env.local`, mặc định backend 8080.
`dist/` là output build; Vite preview 4173 không có dev proxy. Triển khai cần reverse proxy,
không mở CORS rộng hoặc đưa secret vào VITE_* để làm đường gọi hoạt động.

Thiết kế/bàn giao chung: [frontend guide](../docs/team-handoff/04-FRONTEND-GUIDE.md).
Trạng thái và giới hạn kiểm chứng: [status](../docs/status.md).
