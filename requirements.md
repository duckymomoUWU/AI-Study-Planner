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
Kết nối LMS, đồng bộ lịch, thanh toán và tự động đánh giá kiến thức.

Theo yêu cầu bổ sung của người dùng ngày 2026-11-10, nhánh test tích hợp khôi phục chức năng upload PDF/DOCX/TXT/MD từ AIfeat để trích xuất đề cương và điền nhanh mục tiêu. Kết quả phải được xem lại, không tự lưu goal/task. TXT/MD/DOCX có chế độ trích xuất cục bộ ghi nhãn rõ; PDF dùng Gemini và cần cấu hình key/model.

## Trạng thái
Cập nhật 2026-11-10 (nhánh kiểm thử tích hợp `integration/sprint2-sprint3-all8-test`; chưa gộp vào `main`). Chi tiết hợp đồng API: [docs/backend-api-rules.md](docs/backend-api-rules.md).

| Chức năng | Tình trạng |
| --- | --- |
| 1. Đăng ký, đăng nhập, đăng xuất, chỉ truy cập dữ liệu của mình | Đã hiện thực (session, CSRF, ownership) |
| 2. Tạo, sửa, xóa mục tiêu | Đã hiện thực, có giao diện |
| 3. Tạo, sửa, xóa công việc thủ công; xem công việc theo ngày | Đã có API và UI tạo/sửa/xóa, xem theo ngày/mục tiêu; kiểm tra ngày/ngân sách/ownership ở server |
| 4. AI đề xuất bản nháp | Đã tích hợp API/UI trên nhánh test; cần GEMINI_API_KEY và GEMINI_MODEL ở backend, chưa kiểm chứng provider thật |
| 5. Chỉ lưu kế hoạch sau khi xác nhận | Có confirm nguyên tử, kiểm tra version/expiry/ngân sách, receipt chống lưu trùng; đã test PostgreSQL với AI mock |
| 6. Đánh dấu hoàn thành/mở lại; số công việc hoàn thành và còn lại | Đã có API và giao diện đánh dấu hoàn thành/mở lại; có số lượng theo ngày và tiến độ theo từng mục tiêu (kể cả 0/0) |

Mô tả tính năng: [đánh dấu hoàn thành](docs/product/task-completion.md) và [tiến độ mục tiêu](docs/product/goal-progress.md).

Chi tiết tích hợp: [review 8 branch](docs/reviews/branch-integration-2026-11-08.md) và [hợp đồng task/draft](docs/product/integrated-tasks-and-plans.md).


Luồng tạo mục tiêu trên nhánh test (2026-11-10): tạo goal xong tự sinh/mở bản nháp AI; giữ bước người dùng xác nhận trước khi lưu task. AI lỗi vẫn giữ goal và cho thử lại sinh draft, không tạo thêm goal.
