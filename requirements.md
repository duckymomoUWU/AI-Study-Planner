# AI Study Planner — Requirements

## Mục tiêu
Giúp sinh viên tạo kế hoạch ôn tập theo môn học, nội dung, ngày thi và thời gian học mỗi ngày. AI đưa ra đề xuất; người dùng chỉnh sửa và xác nhận trước khi lưu.

## Quản lý mục tiêu — Jira ASP-9
Người dùng có thể tạo, xem, sửa và xóa mục tiêu ôn tập. Mỗi mục tiêu gồm:
- Môn học.
- Chủ đề.
- Ngày bắt đầu.
- Ngày thi.
- Số phút học mỗi ngày.

Quy tắc:
- Môn học và chủ đề không được rỗng sau khi loại bỏ khoảng trắng ở đầu/cuối.
- Ngày thi không được ở trong quá khứ và phải sau ngày bắt đầu.
- Khoảng thời gian từ ngày bắt đầu đến ngày thi không vượt quá 90 ngày.
- Số phút học mỗi ngày là số nguyên từ 15 đến 480, bao gồm hai giá trị biên.
- Mục tiêu đã tạo phải được lưu và hiển thị lại sau khi tải lại trang.
- Khi sửa ngày thi hoặc ngân sách học mỗi ngày, kiểm tra các công việc đã có. Nếu công việc nằm vào ngày thi/sau ngày thi hoặc tổng thời lượng trong một ngày vượt ngân sách mới, từ chối lưu và thông báo phần cần điều chỉnh.
- Khi xóa, yêu cầu người dùng xác nhận; sau xác nhận, xóa mục tiêu cùng các công việc và bản nháp kế hoạch liên quan.
- Server kiểm tra quyền sở hữu đối với mọi thao tác đọc/ghi mục tiêu và dữ liệu liên quan.

## Chức năng phiên bản đầu
1. Đăng ký, đăng nhập, đăng xuất; chỉ truy cập dữ liệu của mình.
2. Tạo, xem, sửa, xóa mục tiêu ôn tập theo quy tắc ở mục **Quản lý mục tiêu — Jira ASP-9**.
3. Tạo, sửa, xóa công việc thủ công; xem công việc theo ngày.
4. AI đề xuất các công việc nhỏ và thời lượng; hiển thị bản nháp để chỉnh sửa.
5. Chỉ lưu kế hoạch sau khi người dùng xác nhận.
6. Đánh dấu hoàn thành/mở lại; hiển thị số công việc hoàn thành và còn lại.

## Quy tắc nghiệp vụ
- Không xếp công việc vào ngày thi hoặc bất kỳ ngày nào sau ngày thi.
- Tổng thời lượng công việc trong một ngày không vượt ngân sách của mục tiêu đó. Nếu kế hoạch không thể xếp vừa, báo số phút còn thiếu để người dùng đổi phạm vi hoặc ngân sách; không âm thầm tạo lịch quá tải.
- Bản MVP tính ngân sách theo từng mục tiêu; chưa tối ưu đồng thời nhiều môn/mục tiêu.
- AI chỉ gợi ý, không tự lưu hoặc thay đổi kế hoạch đã xác nhận. Ước lượng thời lượng mang tính tham khảo.
- AI lỗi thì người dùng vẫn tạo công việc thủ công được.
- Tiến độ = số công việc hoàn thành / tổng số công việc; không đánh giá mức độ hiểu bài. Chưa có việc thì hiển thị 0/0.

## Chất lượng cần đạt khi triển khai tính năng
- Kiểm tra quyền sở hữu ở server trên mọi thao tác dữ liệu cá nhân.
- Validate dữ liệu ở server; lưu kế hoạch nguyên tử, tránh tạo trùng khi gửi lại yêu cầu.
- Không đưa mật khẩu, API key hoặc nội dung học vào log.
- Kiểm thử ngày/giờ, giới hạn 90 ngày, giới hạn 15–480 phút, thiếu ngân sách, sửa mục tiêu khi đã có công việc, xóa dữ liệu liên quan, truy cập chéo tài khoản và AI lỗi.

## Ngoài phạm vi ban đầu
Đọc PDF, kết nối LMS, đồng bộ lịch, thanh toán và tự động đánh giá kiến thức.

## Trạng thái
Đây là yêu cầu cần phát triển. Repo tối giản chỉ khởi tạo môi trường và cấu trúc DB; chưa hiện thực các chức năng trên.
