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
7. Google Calendar (PO chốt 2026-11-11): kết nối OAuth, chọn các lịch kiểm tra giờ bận và lịch nhận, cấu hình giờ học theo thứ/giờ sinh hoạt, xem trước rồi xác nhận xuất một chiều. Giữ nguyên ngày, không tách buổi, không xuất khi còn buổi chưa xếp. Hợp đồng và giới hạn: [Google Calendar](docs/product/google-calendar.md).

## Quy tắc nghiệp vụ

Google Calendar mở rộng PO chấp thuận 2026-11-11: sau khi chủ động xuất một buổi học, sửa tên/ngày/phút trong app hoặc tên môn sẽ cập nhật cùng sự kiện; xóa việc/mục tiêu sẽ xóa sự kiện tương ứng. Lưu thay đổi và yêu cầu đồng bộ nguyên tử; retry không tạo bản sao. Đổi ngày/phút phải tránh lịch bận, giờ sinh hoạt và khoảng nghỉ; không có chỗ thì giữ sự kiện cũ và báo cần điều chỉnh. Chỉ một chiều app → Google, không tự tạo lại sự kiện bị xóa trên Google, không tự quét sửa/xóa lịch cũ. Hợp đồng: [Calendar synchronization](docs/product/google-calendar.md#đồng-bộ-sửa-xóa-từ-app).

PO chấp nhận 2026-11-11: có quỹ phút chung theo ngày cho tất cả mục tiêu, bên cạnh giới hạn riêng từng mục tiêu. Người dùng tự đặt mức mặc định và ghi đè ngày (kể cả 0 phút). AI và mọi thao tác tăng/di chuyển thời lượng phải kiểm tra tổng liên môn, gồm cả việc đã hoàn thành; kiểm tra dưới khóa owner để không vượt khi lưu đồng thời. Không tự đoán quỹ hoặc thay đổi lịch cũ. Cấu hình chưa có thì yêu cầu thiết lập trước khi đặt thêm thời gian. Hợp đồng: [quỹ học chung](docs/product/shared-study-budget.md).
- Luồng đề cương được PO chốt ngày 2026-11-10: tải PDF → rà/xác nhận chủ đề có nguồn → tự nhập ngày thi và quỹ phút → sinh bản nháp → chỉnh → xác nhận lưu. Hợp đồng và giới hạn: [tạo lịch ôn từ đề cương](docs/product/syllabus-revision-plan.md). Ngày có thể override về 0 phút; việc chưa xếp phải giữ trong draft và ghi lý do. Chưa cho lưu một phần nếu còn việc chưa được xếp; quyền lưu một phần cần PO chốt.
- Ngày thi phải sau ngày bắt đầu; không xếp việc vào ngày thi hoặc sau ngày thi.
- Khi tạo mục tiêu mới, ngày bắt đầu không được trước hôm nay theo múi giờ mục tiêu (mặc định Việt Nam). Khi sửa mục tiêu đang học, được giữ ngày bắt đầu cũ; lịch tạo lại chỉ xếp từ hôm nay trở đi.
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

Theo yêu cầu bổ sung của người dùng ngày 2026-11-10, nhánh test tích hợp hỗ trợ upload PDF/DOCX/TXT/MD để rà và xác nhận chủ đề. PDF có lớp văn bản đọc cục bộ bằng PDFBox; TXT/MD/DOCX đọc văn bản cục bộ. Kết quả không tự lưu goal/task; tự điền ngày thi nếu tài liệu ghi rõ và người dùng xác nhận, không tự suy diễn ngày thi hoặc quỹ phút cá nhân. Gemini cần key/model ở backend để đề xuất công việc từ chủ đề đã xác nhận.

## Trạng thái

Cập nhật 2026-11-11: PO chấp nhận lưu phần kế hoạch đã xếp và giữ việc chưa xếp để xử lý tiếp. Hiển thị phút cần/quỹ còn lại, lý do chưa xếp; không tự giảm ước lượng. Người dùng có thể tăng phút/ngày hoặc giảm phạm vi chủ đề; phần chưa xếp được lưu bền, có cảnh báo chưa bao phủ hết nội dung và có thể xếp lại theo cấu hình mới mà không gọi AI. Hợp đồng và giới hạn: [syllabus revision plan](docs/product/syllabus-revision-plan.md); [kiểm chứng](docs/plans/completed/partial-planning-2026-11-11.md).
Cập nhật 2026-11-10 (nhánh kiểm thử tích hợp `integration/sprint2-sprint3-all8-test`; chưa gộp vào `main`). Chi tiết hợp đồng API: [docs/backend-api-rules.md](docs/backend-api-rules.md).

| Chức năng | Tình trạng |
| --- | --- |
| 1. Đăng ký, đăng nhập, đăng xuất, chỉ truy cập dữ liệu của mình | Đã hiện thực (session, CSRF, ownership) |
| 2. Tạo, sửa, xóa mục tiêu | Đã hiện thực, có giao diện |
| 3. Tạo, sửa, xóa công việc thủ công; xem công việc theo ngày | Đã có API và UI tạo/sửa/xóa, xem theo ngày/mục tiêu; kiểm tra ngày/ngân sách/ownership ở server |
| 4. AI đề xuất bản nháp | Đã có chọn chủ đề và nguồn, scope theo ID, ngày có 0 phút, việc chưa xếp kèm lý do. Cần GEMINI_API_KEY/GEMINI_MODEL ở backend; đã smoke với Gemini thật, tách riêng test AI mock |
| 5. Chỉ lưu kế hoạch sau khi xác nhận | Có confirm nguyên tử, kiểm tra version/expiry/ngân sách, receipt chống lưu trùng; đã test PostgreSQL với AI mock |
| 6. Đánh dấu hoàn thành/mở lại; số công việc hoàn thành và còn lại | Đã có API và giao diện đánh dấu hoàn thành/mở lại; có số lượng theo ngày, tiến độ theo từng mục tiêu và **theo từng tuần Thứ Hai–Chủ Nhật** (kể cả 0/0) |

Mô tả tính năng: [đánh dấu hoàn thành](docs/product/task-completion.md) [tiến độ mục tiêu](docs/product/goal-progress.md) và [tiến độ theo tuần](docs/product/weekly-progress.md) (bằng chứng ở [weekly-progress-2026-11-12.md](docs/plans/completed/weekly-progress-2026-11-12.md)).

Chi tiết tích hợp: [review 8 branch](docs/reviews/branch-integration-2026-11-08.md) và [hợp đồng task/draft](docs/product/integrated-tasks-and-plans.md).


Luồng tạo mục tiêu trên nhánh test (2026-11-10): tạo goal xong tự sinh/mở bản nháp AI; giữ bước người dùng xác nhận trước khi lưu task. AI lỗi vẫn giữ goal và cho thử lại sinh draft, không tạo thêm goal.
