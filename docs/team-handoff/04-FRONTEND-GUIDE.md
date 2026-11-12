# Frontend design system và template chung

## 1. Nguồn chuẩn đang có

Đây là React + Vite, JavaScript/JSX + CSS thuần. Thư mục có tên `components/ui` và `shadcn.css` là component CSS hiện có, **không đồng nghĩa dự án đang dùng Tailwind/shadcn CLI**. Không cài lại UI framework cho ba feature này.

Đọc các file thật trước khi làm:

- [theme.css](https://github.com/duckymomoUWU/AI-Study-Planner/blob/29dbf6d1c7e4f0cacdf9524aa905f1789bfb2b62/frontend/src/theme.css): nguồn chuẩn token, font và hiệu ứng.
- [App.jsx](https://github.com/duckymomoUWU/AI-Study-Planner/blob/29dbf6d1c7e4f0cacdf9524aa905f1789bfb2b62/frontend/src/App.jsx): shell/route hash, auth state và callback điều hướng.
- [OverviewPage.jsx](https://github.com/duckymomoUWU/AI-Study-Planner/blob/29dbf6d1c7e4f0cacdf9524aa905f1789bfb2b62/frontend/src/pages/OverviewPage.jsx): bố cục dashboard, empty/error và copy tiếng Việt.
- [StudyPlanPage.jsx](https://github.com/duckymomoUWU/AI-Study-Planner/blob/29dbf6d1c7e4f0cacdf9524aa905f1789bfb2b62/frontend/src/pages/StudyPlanPage.jsx): gọi API, màn hình đăng nhập, refresh trạng thái.
- [Button](https://github.com/duckymomoUWU/AI-Study-Planner/blob/29dbf6d1c7e4f0cacdf9524aa905f1789bfb2b62/frontend/src/components/ui/button.jsx), [card](https://github.com/duckymomoUWU/AI-Study-Planner/blob/29dbf6d1c7e4f0cacdf9524aa905f1789bfb2b62/frontend/src/components/ui/card.jsx), [input](https://github.com/duckymomoUWU/AI-Study-Planner/blob/29dbf6d1c7e4f0cacdf9524aa905f1789bfb2b62/frontend/src/components/ui/input.jsx): tái sử dụng khi phù hợp.
- [googleCalendar.js](https://github.com/duckymomoUWU/AI-Study-Planner/blob/29dbf6d1c7e4f0cacdf9524aa905f1789bfb2b62/frontend/src/lib/googleCalendar.js), [tasks.js](https://github.com/duckymomoUWU/AI-Study-Planner/blob/29dbf6d1c7e4f0cacdf9524aa905f1789bfb2b62/frontend/src/lib/tasks.js), [auth.js](https://github.com/duckymomoUWU/AI-Study-Planner/blob/29dbf6d1c7e4f0cacdf9524aa905f1789bfb2b62/frontend/src/lib/auth.js): mẫu client thật có cookie, CSRF và lỗi.

## 2. Token bắt buộc dùng lại

| Vai trò | CSS token hiện có | Giá trị |
| --- | --- | --- |
| Nền trang kem | `--canvas` | `#f6f3eb` |
| Card/input | `--surface` | `#fffefa` |
| Màu chính xanh rêu | `--primary` | `#285b53` |
| Hover chính | `--primary-hover` | `#204a43` |
| Chữ chính | `--ink` | `#293c35` |
| Chữ phụ | `--muted` | `#647267` |
| Viền | `--line` | `#e2dfd3` |
| Nền nhẹ/selected | `--soft` | `#e9ede2` |
| Font | `--font-ui` | Be Vietnam Pro → Segoe UI → sans-serif |

Font đã lưu local ở `frontend/src/assets/fonts/`, đủ 400/500/600/700, có OFL. Không đổi sang font serif, không load Google Fonts qua CDN chỉ cho feature mới. `index.css` có khai báo cũ, nhưng **theme.css của app là lớp theme cuối**, không lấy màu/font cũ làm baseline.

Kích thước đề xuất cho feature mới: body 14–16px, chữ phụ tối thiểu 12px, H1 32–38px, H2 22–26px. Spacing 8/12/16/24/32px, card radius 16px, input/button 10–14px, vùng chạm nút khoảng 44px. Đây là quy ước thiết kế mới để thống nhất, không phải tất cả màn hình cũ đã đạt từng con số.

## 3. Bố cục và trạng thái

- Landing có header công khai, nội dung rộng vừa phải, hero/ba bước/CTA. Timer và Recall dùng shell ứng dụng; không thêm sidebar/topbar thứ hai.
- CSS chỉ theo module: `.landing-*`, `.timer-*`, `.recall-*`. Không reset toàn cục `button`, `body`, `.sidebar` trong CSS của feature.
- Nút chính chỉ 1 hành động nổi bật trong cùng nhóm. Nút phụ nền nhẹ; hủy/xóa phải nói đúng hậu quả. Giữ hover/focus/disabled/pressed và `prefers-reduced-motion` theo theme.
- Màu không được là dấu hiệu duy nhất: phải có chữ RUNNING/PAUSED hoặc “Cần thử lại”. Giữ focus ring khi dùng bàn phím.
- Loading: chặn thao tác lặp; không ẩn dữ liệu đã có bằng màn hình trắng. Empty: giải thích và có hành động bắt đầu. Error: giữ input, nói rõ đã lưu/chưa lưu, retry đúng API.
- Label gắn input; radio/checkbox có text; câu hỏi flashcard và nút lật/đánh giá dùng bàn phím được. Chỉ thông báo chuyển trạng thái của Timer bằng aria-live, không đọc mỗi giây.
- Copy tiếng Việt rõ, không có “Sprint”, “US-xx”, “placeholder”, “UI demo”, raw status/SQL hoặc text AI viết về quá trình lập trình trong màn hình sản phẩm.
- Không khoe Timer/Recall hoạt động ở landing trước khi API thật của feature được ghép. Nội dung quảng bá phải đúng hiện trạng release.

## 4. Quy ước client và quản lý state

- `lib/studySessions.js` Người 1; `lib/recall.js` Người 2. URL tương đối `/api/v1/...`; không hardcode cổng backend vào component.
- Dùng `getCsrfToken()` hiện có cho ghi. Không tạo auth context/JWT mới, không cho người dùng nhập owner ID.
- Đọc `error.code`, `error.details` và `error.context`; không phân nhánh bằng cách so sánh câu message tiếng Việt.
- `useEffect` có cleanup/AbortController, chặn kết quả request cũ ghi đè khi đổi task/goal. React StrictMode có thể chạy effect nhiều lần: effect đọc được lặp, start/generate/review chỉ chạy từ intent rõ hoặc retry cùng key.
- Countdown dựa snapshot `serverNow/remainingSeconds` và elapsed thời gian monotonic từ lúc nhận; refresh/focus tab phải đồng bộ backend. Không gửi một request cho mỗi giây.
- LocalStorage chỉ lưu sở thích/điểm UI nếu cần, không làm nguồn thật cho số phút đã học hay đánh giá đã lưu.
- Không đánh giá card bằng side effect mount, không tự sinh lại deck khi người dùng chỉ mở trang.

## 5. Template và kiểm tra

- [FeaturePage.jsx.example](templates/FeaturePage.jsx.example) và [feature.css.example](templates/feature.css.example): khung page có loading/empty/error; đổi tên theo module, không chép đè page cũ.
- [featureApi.js.example](templates/featureApi.js.example): skeleton client với session/CSRF/envelope; URL trong mẫu chưa phải API đang chạy.
- [FRONTEND-THEME.html](FRONTEND-THEME.html): bảng màu và minh họa card/nút/Timer/flashcard, mở offline. Đây là style guide, không phải màn hình feature đã triển khai.
- Check 360px, 768px, 1440px, tiếng Việt có dấu và bàn phím. `npm.cmd run lint`, `npm.cmd test`, `npm.cmd run build` phải ghi kết quả thật trong PR.
