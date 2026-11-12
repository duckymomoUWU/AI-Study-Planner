# AI Study Planner — Frontend

React + Vite + JavaScript/CSS. Hướng dẫn môi trường/khởi động nằm ở [setup](../setup.md); lệnh kiểm thử và các giới hạn nằm ở [verification harness](../.agents/harness/README.md).

## Các trang và luồng dữ liệu

| Trang / thành phần | Nguồn dữ liệu và hành vi |
| --- | --- |
| `OverviewPage.jsx` | Goals và tasks hôm nay; số việc/phút còn lại, tiến độ, kỳ thi sắp tới. Loading/lỗi/empty riêng, không giả lập số liệu. |
| `GoalsPage.jsx` | Goal CRUD, syllabus upload/review, ngày thi có nguồn, ngày bắt đầu nhập tay, quỹ phút, AI draft. |
| `StudyPlanPage.jsx` | Mặc định lịch tuần; tải 7 ngày qua API thật, lọc mục tiêu, hoàn thành công việc, mở luồng Calendar. |
| `DailyTasksPage.jsx` | Chi tiết ngày, công việc thủ công, sửa/xóa/hoàn thành và quá hạn. |
| `SyllabusUpload.jsx` / `PlanDraftPanel.jsx` | Duyệt chủ đề/nguồn và bản nháp; chỉ confirm mới tạo tasks. |
| `CalendarExportPanel.jsx` | OAuth, chọn lịch và giờ sinh hoạt, preview và explicit export; lựa chọn lịch mất quyền vẫn hiển thị để người dùng xử lý. |
| `AuthPage.jsx` | Đăng nhập/đăng ký với UI primitives ở `components/ui/`. |

Trang `LocalCalendarPreview` và menu lịch mẫu đã được xóa; không dùng localStorage mock làm lịch thật. `BackendStatus.jsx` còn là thành phần health tham khảo, không còn là nội dung Tổng quan.

## Cấu trúc chính

- `App.jsx`: session, điều hướng hash và layout.
- `lib/auth.js`, `goals.js`, `tasks.js`, `syllabus.js`, `ai-planner.js`, `googleCalendar.js`: API clients cùng origin, giữ session và CSRF cho thao tác ghi.
- `lib/planningScope.js`, `goalPlanning.js`, `studyWeek.js`, `overview.js`: logic thuần, có unit tests tương ứng.
- `theme.css`: token kem–xanh rêu, typography và trạng thái tương tác chung. `App.css` giữ layout; CSS từng trang giữ bố cục. Tránh thêm màu `!important` cạnh tranh giữa active/hover/focus.
- `assets/fonts/`: Be Vietnam Pro 400/500/600/700 và giấy phép OFL. Phông được đóng gói cùng build, không cần tải từ Google Fonts lúc chạy.

## Proxy và cấu hình

Vite dev lắng nghe `127.0.0.1:5173`, proxy `/api` và `/actuator/health` tới `BACKEND_URL` trong `frontend/.env.local` (mặc định backend 8080). Bản local PO đang dùng backend 18083; không coi đây là mặc định mọi máy.

Không đưa DB password, Gemini key, Google Client Secret hoặc token vào `frontend/.env.local` hay `VITE_*`. Các giá trị đó thuộc `.env` backend ở root. Dùng nhất quán host 127.0.0.1 với OAuth callback đã đăng ký; không đổi lẫn localhost và 127.0.0.1 giữa phiên.

`npm run build` tạo `dist/`; Vite preview cổng 4173 không có dev proxy. Production cần reverse proxy cùng origin, không dùng Vite dev làm production server.

## Kiểm chứng

Các test `src/lib/*.test.js` chủ yếu kiểm tra logic và HTTP client bằng mock; lint/build không chứng minh layout hay tương tác browser. Chưa có browser E2E tự động kết nối trong phiên này. Xem [bằng chứng bàn giao](../docs/plans/completed/core-study-calendar-2026-11-11.md#validation) cho các mốc thực sự đã chạy.