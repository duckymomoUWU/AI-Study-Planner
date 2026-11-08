# AI Study Planner — Requirements

## Mục tiêu
Giúp sinh viên tạo kế hoạch ôn tập theo môn học, nội dung, ngày thi và thời gian học mỗi ngày. AI đưa ra đề xuất; người dùng chỉnh sửa và xác nhận trước khi lưu.

## Chức năng phiên bản đầu
1. Đăng ký, đăng nhập, đăng xuất; chỉ truy cập dữ liệu của mình.
2. Tạo, sửa, xóa mục tiêu: môn học, chủ đề, ngày bắt đầu, ngày thi, số phút học mỗi ngày.
3. Tạo, sửa, xóa công việc thủ công; xem công việc theo ngày.
4. AI đề xuất các công việc nhỏ và thời lượng; hiển thị bản nháp để chỉnh sửa.
5. Chỉ lưu kế hoạch sau khi người dùng xác nhận.
6. Đánh dấu hoàn thành/mở lại; hiển thị số công việc hoàn thành và còn lại.

## Quy tắc nghiệp vụ
- Ngày thi phải sau ngày bắt đầu; không xếp việc vào ngày thi hoặc sau ngày thi.
- Thời gian mỗi ngày: 15–480 phút; khoảng lập lịch tối đa 90 ngày.
- Tổng thời lượng trong ngày không vượt ngân sách của mục tiêu đó. Nếu thiếu thời gian, báo số phút thiếu để người dùng đổi phạm vi hoặc ngân sách.
- Bản MVP tính ngân sách theo từng mục tiêu; chưa tối ưu đồng thời nhiều môn.
- AI chỉ gợi ý, không tự lưu hoặc thay đổi kế hoạch đã xác nhận. Ước lượng thời lượng mang tính tham khảo.
- AI lỗi thì người dùng vẫn tạo công việc thủ công được.
- Tiến độ = số công việc hoàn thành / tổng số công việc; không đánh giá mức độ hiểu bài. Chưa có việc thì hiển thị 0/0.

## Chất lượng cần đạt khi triển khai tính năng
- Kiểm tra quyền sở hữu ở server trên mọi thao tác dữ liệu cá nhân.
- Validate dữ liệu ở server; lưu kế hoạch nguyên tử, tránh tạo trùng khi gửi lại yêu cầu.
- Không đưa mật khẩu, API key hoặc nội dung học vào log.
- Kiểm thử ngày/giờ, thiếu ngân sách, truy cập chéo tài khoản và AI lỗi.

## Ngoài phạm vi ban đầu
Đọc PDF, kết nối LMS, đồng bộ lịch, thanh toán và tự động đánh giá kiến thức.

## Trạng thái
Cập nhật 2026-11-08, theo mã trên nhánh `dev2` sau khi gộp sprint 3 của dev2 và sprint 4 (xem tiến độ theo tuần); chưa gộp vào `main`. "Có mã" nghĩa là có hiện thực trong repo; "đã kiểm chứng" nghĩa là có test chạy thật (xem tài liệu trong `docs/product/`). Hợp đồng API: [docs/backend-api-rules.md](docs/backend-api-rules.md).

| Chức năng | Tình trạng |
| --- | --- |
| 1. Đăng ký, đăng nhập, đăng xuất, chỉ truy cập dữ liệu của mình | Đã hiện thực và kiểm chứng (session, CSRF, ownership) |
| 2. Tạo, sửa, xóa mục tiêu | Đã hiện thực và kiểm chứng, có giao diện |
| 3. Tạo, sửa, xóa công việc thủ công; xem công việc theo ngày | Xem công việc theo ngày và theo mục tiêu (chỉ đọc) đã hiện thực; tạo, sửa, xóa chưa có API |
| 4. AI đề xuất các công việc nhỏ và bản nháp | Có mã (API bản nháp qua Gemini, giao diện xem và sửa); chưa có tài liệu kiểm chứng riêng |
| 5. Chỉ lưu kế hoạch sau khi xác nhận | Có mã (API xác nhận bản nháp); chưa có tài liệu kiểm chứng riêng |
| 6. Đánh dấu hoàn thành/mở lại; số công việc hoàn thành và còn lại | Đánh dấu hoàn thành/mở lại đã hiện thực và kiểm chứng (API và giao diện); số hoàn thành/còn lại theo ngày đang xem và **theo từng tuần** đã hiện thực và kiểm chứng; tiến độ theo từng mục tiêu chưa có trên nhánh này |

Các quy tắc nghiệp vụ ở trên không đổi. Tài liệu: [đánh dấu hoàn thành](docs/product/task-completion.md), [xem tiến độ theo tuần](docs/product/weekly-progress.md) (bằng chứng ở [weekly-progress-verification.md](docs/product/weekly-progress-verification.md)).
