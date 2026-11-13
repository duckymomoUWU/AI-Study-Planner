# Definition of Ready, Done và mẫu bàn giao

## Definition of Ready — trước khi bắt đầu story

- [ ] Chỉ định người làm/reviewer; story có luồng demo và AC trong file 02.
- [ ] PO/team duyệt các mặc định v1: Pomodoro 25/5, seconds cho phiên học, tự đánh giá thẻ, giới hạn tài liệu/count và hành vi xóa.
- [ ] FE/BE đồng ý route, JSON, nullability, trạng thái, lỗi và idempotency trong file 03; fixture phản ánh đúng.
- [ ] Branch từ main mới nhất; chạy được frontend, backend và DB test riêng. `.env` thật không vào Git.
- [ ] Đã thống nhất người sửa shell/error handler và thứ tự V8 → V9. Không có migration trùng tên.

## Definition of Done — áp dụng từng PR

- [ ] Đạt AC được gán; AC chưa đạt phải ghi rõ, không đánh dấu toàn bộ story Done.
- [ ] UI đã nối API thật; mock không còn trong đường chạy production, dữ liệu không hardcode/fallback thành công giả.
- [ ] Kiểm tra loading, empty, error, retry, session hết hạn, chống submit trùng; bàn phím dùng được.
- [ ] Dùng theme/font hiện có, không lỗi tiếng Việt, không tràn ngang ở 360px/768px/1440px.
- [ ] Mọi API đọc/ghi lọc owner, nested resource kiểm tra parent; thiếu session 401, thiếu CSRF khi ghi 403, ID người khác 404.
- [ ] Validation ở backend; body số sai kiểu, field lạ, version cũ, thao tác sai trạng thái được từ chối mà không đổi DB.
- [ ] Migration mới áp được trên DB test từ schema main; không sửa V1–V7 đã có, không yêu cầu xóa data app.
- [ ] Có test hành vi quan trọng: Timer clock/race/replay; Recall nguồn/AI lỗi/owner/review. Không chỉ test getter hay mock luôn thành công.
- [ ] Lint/test/build hoặc Maven verify phù hợp đã chạy, ghi **lệnh + số pass/fail/skip thực tế**. Không biến skipped thành passed.
- [ ] Tài liệu API/fixture đã cập nhật khi payload đổi; không phá goal/task/planner/auth/Calendar đang chạy.
- [ ] PR có ảnh/video demo, hướng dẫn tái hiện, giới hạn và reviewer duyệt; CI liên quan xanh.
- [ ] Không có `.env`, key/token, PDF riêng tư, dữ liệu PostgreSQL, `target`, `dist`, `node_modules` hoặc log thật trong commit.

**Done không đồng nghĩa production-ready:** cần deployment/OAuth/browser/mobile thật riêng. Đây là đợt MVP local của nhóm.

## Checklist demo chung cuối D6/D7

| Ca | Cách thử | Kết quả cần thấy |
| --- | --- | --- |
| E2E-01 | Khách vào `/` → bắt đầu → auth → mục tiêu | Landing dẫn đúng luồng, không lẫn dashboard |
| E2E-02 | Task thật → start → pause → reload → resume → finish | Thời gian đúng, không cộng lúc pause, phiên lưu được |
| E2E-03 | Hai tab start cùng lúc | Chỉ một session active; không tạo hai phiên |
| E2E-04 | Học xong rồi bấm finish/retry lần nữa | Không cộng gấp đôi; task không tự completed |
| E2E-05 | Đóng tab Pomodoro quá 25 phút rồi mở lại | Không tính thêm nhiều giờ; focusComplete và finish an toàn |
| E2E-06 | Xóa task đang có lịch sử/phiên học | Lịch sử không mất; không khôi phục task; Calendar behavior cũ vẫn đúng |
| E2E-07 | PDF/text → draft thẻ → sửa → activate → lật đáp án → tự đánh giá | Nguồn rõ, lưu bền, không AI chấm bài |
| E2E-08 | AI timeout/sai quote/sai JSON | Lỗi rõ, giữ material và deck đã có, không tạo thẻ giả |
| E2E-09 | Review cùng thẻ hai lần khác key, rồi retry cùng key | Progress dùng đánh giá cuối, review không trùng do retry |
| E2E-10 | Account B đoán ID session/deck/card account A | 404, không lộ nội dung hoặc thay đổi A |
| E2E-11 | Xóa goal có deck và phiên đã học | Recall cascade; history Timer còn snapshot; không phá dữ liệu khác |
| E2E-12 | Tạo mục tiêu, AI plan, quỹ chung, xuất/sync Calendar như trước | Smoke regression không đổi hành vi core cũ |

## Lệnh kiểm tra và an toàn dữ liệu

Các lệnh chuẩn và giới hạn ở [Harness](https://github.com/duckymomoUWU/AI-Study-Planner/blob/29dbf6d1c7e4f0cacdf9524aa905f1789bfb2b62/.agents/harness/README.md) là nguồn duy trì chính. Ví dụ cho PR:

```powershell
# Frontend
Set-Location frontend
npm.cmd run lint
npm.cmd test
npm.cmd run build

# Backend: chạy từ root sau khi trỏ DB_URL/DB_USER/DB_PASSWORD tới DB test riêng
mvn -B -ntp -f backend/pom.xml '-Dgoal.integration=true' '-Dauth.integration=true' verify
```

CI hiện dùng PostgreSQL 17, DB **study_planner_test**. Không chạy integration suite dọn fixture trên database app. Không đưa mật khẩu thật vào PR hay log. Flag kích hoạt test sessions/recall mới phải được người viết test ghi rõ; đừng mặc định chúng đã chạy chỉ vì Maven xanh. Backend test đã tắt worker Calendar; không bật Google/Gemini thật trong CI.

## Mẫu PR và báo cáo hằng ngày

Copy [templates/PR-TEMPLATE.md](templates/PR-TEMPLATE.md) vào mô tả PR. Mỗi ngày chỉ cần báo:

> Đã làm: … / Demo hoặc PR: … / Test đã chạy: … / Còn vướng: … / Cần người nào bàn giao gì: …

PO điền trước khi gửi: Người 1 = ___; Người 2 = ___; Người 3 = ___; Reviewer mỗi PR = ___; D1 = ___; ngày demo = ___. Nếu bỏ qua một test, ghi lý do và rủi ro cụ thể.
