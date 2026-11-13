# Yêu cầu sản phẩm

## Mục tiêu

Giúp sinh viên biết cần ôn gì trước ngày thi và có một kế hoạch phù hợp với thời gian
của mình. AI đưa ra đề xuất; người học xem, điều chỉnh và xác nhận trước khi lưu.

## Phạm vi hiện hành

1. Landing công khai dẫn tới đăng ký/đăng nhập và tạo mục tiêu.
2. Tài khoản dùng session; mỗi người chỉ truy cập dữ liệu của mình.
3. Quản lý mục tiêu: môn/chủ đề, ngày bắt đầu, ngày thi, mức phút/ngày mong muốn.
4. Tải đề cương PDF/DOCX/TXT/MD hoặc nhập tay; rà và xác nhận chủ đề có nguồn.
5. AI đề xuất công việc và ước lượng; xem/chỉnh bản nháp, xác nhận toàn bộ hoặc lưu phần đã xếp.
6. Công việc thủ công, lịch tuần/ngày, quá hạn, hoàn thành/mở lại và tiến độ cơ bản.
7. Quỹ thời gian chung/ngày cho mọi mục tiêu; các ngày có thể có quỹ khác nhau.
8. Timer 25 phút, nghỉ UI 5 phút; phiên học bền vững và thời gian do server tính.
9. Active Recall: flashcard hỏi–đáp từ tài liệu, duyệt trước khi dùng và tự đánh giá Nhớ/Chưa nhớ.
10. Google Calendar: kết nối, chọn lịch, tránh giờ bận/sinh hoạt, xem trước rồi xuất;
    cập nhật/xóa sự kiện đã liên kết khi người dùng sửa/xóa trong app.

## Quy tắc lập kế hoạch

- Ngày thi sau ngày bắt đầu; khoảng kế hoạch tối đa 90 ngày. Không xếp việc vào ngày thi hoặc sau ngày thi.
- Mục tiêu mới không bắt đầu trước hôm nay theo múi giờ mục tiêu, mặc định Việt Nam.
  Mục tiêu đang học có thể giữ ngày bắt đầu cũ; lịch tạo lại chỉ xếp từ hôm nay trở đi.
- Tự điền ngày thi chỉ khi tài liệu có ngày rõ ràng và có nguồn để xác nhận; không có thì yêu cầu người học nhập.
  Ngày bắt đầu và quỹ thời gian do người học chọn, không suy từ số tiết hay thời lượng kỳ thi.
- Quỹ chung 0–1440 phút/ngày là giới hạn cứng của tổng việc đã xếp, kể cả việc hoàn thành.
  Không tự đoán quỹ hoặc thay đổi lịch cũ khi thiết lập cấu hình.
- Mức mong muốn từng môn 15–480 phút/ngày là ưu tiên của AI, không phải giới hạn cứng khi thêm việc thủ công.
  Ghi đè cho AI theo ngày là 0–480 phút; 0 là ngày AI không xếp việc cho môn đó.
- AI ưu tiên mức mong muốn; chỉ đề xuất vượt khi còn quỹ chung và phải yêu cầu người dùng đồng ý trước khi lưu.
  Người dùng có thể yêu cầu xếp lại theo mức mong muốn mà không gọi AI lần nữa.
- Không rút ngắn ước lượng, xóa việc hay thay lịch môn khác để làm kế hoạch trông đủ.
  Công việc chưa đủ chỗ có lý do và được giữ khi người dùng chọn lưu một phần.
- Tạo bản nháp không tạo công việc. Xác nhận lưu nguyên tử; gửi lại cùng yêu cầu không tạo trùng.
- AI lỗi vẫn giữ dữ liệu đã lưu và cho dùng công việc thủ công; không thay bằng kết quả giả.

## Học và theo dõi

- Tiến độ là số việc hoàn thành/tổng số việc; rỗng là 0/0. Không coi đó là mức độ hiểu bài.
- Mỗi tài khoản tối đa một phiên Timer RUNNING hoặc PAUSED; một phiên tối đa 1500 giây tập trung.
  Chỉ phiên COMPLETED cộng vào thực học, không tính PAUSED, CANCELLED hoặc giờ nghỉ.
- FINISH lưu phiên học, không tự hoàn thành công việc. Sau khi lưu, người học có thể chọn
  đánh dấu hoàn thành hoặc học tiếp sau; việc đã hoàn thành/bị xóa không bị hỏi lại.
- Dự kiến và thực học là hai số độc lập. Không tự sửa dự kiến hoặc lấy phép trừ để kết luận
  còn bao nhiêu thời gian. Sửa dự kiến là thao tác chủ động, được kiểm tra quỹ chung.
- Recall lưu nguồn trước khi gọi AI; bộ thẻ mới là DRAFT, được xem/sửa rồi mới ACTIVE.
  Chỉ ACTIVE mới nhận đánh giá. Số thẻ đã ôn tính đánh giá cuối mỗi thẻ, không đếm trùng khi gửi lại.
- Xóa mục tiêu xóa công việc, bản nháp và dữ liệu Recall liên quan. Lịch sử Timer giữ tên snapshot,
  các liên kết tới mục tiêu/công việc đã xóa có thể null.

## Google Calendar

- Chỉ đồng bộ một chiều từ app, giữ ngày học và không tách buổi. Xuất lần đầu phải xem trước và xác nhận.
- Kiểm tra đủ giờ bận của lịch đã chọn, giờ sinh hoạt và khoảng nghỉ; thiếu quyền/dữ liệu thì không coi là rảnh.
- Sửa/xóa dữ liệu đã xuất tạo yêu cầu đồng bộ bền vững. Retry không tạo sự kiện trùng.
  Khi ngày mới không còn chỗ, giữ sự kiện cũ và báo cần điều chỉnh.
- Không nhập thay đổi từ Google về app hoặc tự tạo lại sự kiện đã bị xóa trên Google.

## Chất lượng và ngoài phạm vi

Server kiểm tra dữ liệu, quyền sở hữu, phiên bản và giới hạn thời gian. Không ghi key,
mật khẩu, token hay nội dung học vào log. Kiểm thử ngày/giờ, quỹ thiếu, ghi đồng thời,
truy cập chéo tài khoản và lỗi provider.

Chưa thuộc MVP: AI chấm đáp án, spaced repetition, tự cân lại lịch theo mức nhớ,
gamification, thanh toán, tích hợp LMS và đồng bộ Calendar hai chiều.

Chi tiết API: [hợp đồng chức năng](docs/product/README.md).
Trạng thái và bằng chứng: [docs/status.md](docs/status.md).
