# Business — từ giả thuyết đến bằng chứng

## Phân khúc và giá trị

MVP chọn sinh viên đại học có kỳ thi trong 2–4 tuần, ôn từ nhiều chủ đề bằng văn bản, đang dùng ghi chú/to-do nhưng khó chia việc và giờ học. Người dùng và người quyết định sử dụng là sinh viên; người trả tiền chưa được xác thực. Chưa nhắm trường học hoặc LMS.

Job to be done: “Khi biết ngày thi nhưng chưa biết học phần nào trước, tôi muốn một lịch có thể thực hiện và điều chỉnh để bắt đầu học hôm nay.” Khác biệt cần kiểm chứng là lịch tôn trọng quỹ giờ, người học kiểm soát bản nháp, và vẫn tự lập việc khi AI lỗi. Chưa được tuyên bố độc đáo so với mọi đối thủ hoặc giúp tăng điểm.

Lựa chọn thay thế: giấy, Google Calendar, Notion/to-do, hỏi chatbot rồi tự chép lịch. Phỏng vấn hỏi thời gian và khó khăn của cách hiện tại; không hỏi dẫn dắt “bạn có thích AI không?”. Chi phí chuyển đổi là nhập chủ đề và duy trì cập nhật; giảm form và giữ lối thủ công là ưu tiên.

## Business rules cần PO xác nhận với nhóm tuần tới

1. Mỗi mục tiêu có môn, danh sách chủ đề, ngày bắt đầu, ngày thi, timezone IANA và ngân sách phút/ngày. Mặc định timezone Asia/Ho_Chi_Minh, cho đổi rõ ràng. Ngày là date-only; timestamp lưu UTC.
2. Ngày thi bị loại khỏi ngày ôn. Kỳ thi cùng ngày hiện không được tạo lịch mới; hiển thị hướng dẫn. Ngày bắt đầu không trước hôm nay khi tạo mới; lịch cũ vẫn đọc được.
3. MVP ngân sách theo **từng mục tiêu**. Trang hôm nay cộng tất cả mục tiêu, cảnh báo nguy cơ chồng tải. Không được quảng cáo tối ưu quỹ thời gian toàn bộ môn. Nếu phỏng vấn cho thấy đây là nhu cầu chính, ưu tiên shared daily capacity trước tính năng phụ.
4. Giới hạn khởi điểm: 1–30 chủ đề, mỗi chủ đề tối đa 200 ký tự; 15–480 phút/ngày, tối đa 90 ngày; 100 công việc/đề xuất. Giới hạn là lựa chọn sản phẩm, có thể đổi sau thử nghiệm.
5. AI ước lượng thời lượng, không đảm bảo khả năng hiểu hoặc kết quả thi. Bộ lập lịch kiểm tra tổng phút và ngày. Thiếu giờ thì hiển thị chênh lệch và lựa chọn, không giảm ước lượng để che lỗi.
6. Nháp AI không phải kế hoạch đã lưu. Người dùng được sửa/xóa/đổi ngày, server kiểm tra lại khi xác nhận. Việc chia thành phiên phải giữ tổng thời lượng.
7. Không tự động thay đổi lịch hoặc việc đã xong. Tạo lại lịch chỉ thay phần chưa xong được preview, sau xác nhận và version check. Lưu phải có transaction và idempotency.
8. Đánh dấu xong có thể mở lại. Tiến độ tính số việc đã lưu và hoàn thành; xóa việc đổi mẫu số. Việc chia nhỏ tạo nhiều phiên làm thay đổi mẫu số và phải hiển thị rõ. Không gọi tỷ lệ này là độ thành thạo.
9. Mọi dữ liệu có chủ sở hữu; kiểm tra ở server trên từng thao tác, không chỉ ẩn giao diện. Mục tiêu xóa sẽ xóa phụ thuộc sau xác nhận.
10. Provider không giữ vai trò quyết định quyền hoặc gọi DB. Không gửi thông tin định danh không cần thiết. Thông báo dữ liệu gửi tới AI và chính sách nhà cung cấp trước pilot; không dùng dữ liệu nhạy cảm để demo.

Bộ khung hiện mới minh họa rule 2, giới hạn lịch, bảo toàn phút và thiếu thời gian. Các rule auth, bản nháp/lưu và multi-goal còn nằm trong backlog; không coi đã hoàn tất.

## Kế hoạch kiểm chứng

Tuần tới: 5 phỏng vấn 15–20 phút (kỳ thi thật sắp tới). Xin đồng ý ghi chú, dùng mã P01…P05. Hỏi: lần gần nhất lên kế hoạch thế nào, có bỏ cuộc không, thời gian mất, xử lý khi trễ, có dùng chatbot không. Thu ví dụ hành vi, không bịa dữ liệu khảo sát.

Sprint 2: 5 usability sessions, bài tập “Agile/Scrum/XP, 3 ngày, 60 phút/ngày”, so sánh thời gian tạo lịch bằng cách hiện tại và sản phẩm. Đảo thứ tự thử giữa người dùng để giảm ảnh hưởng học trước. Ghi cả cần trợ giúp, hủy và sửa nháp.

Pilot Sprint 3: 10–15 sinh viên, 7 ngày, tuyển qua nhóm lớp khi được phép; chưa tự động gửi lời mời. Mục tiêu thăm dò, không đại diện toàn bộ sinh viên.

|Chỉ số|Định nghĩa|Ngưỡng giả thuyết để thảo luận|
|---|---|---|
|Activation|Số người lưu kế hoạch đầu và hoàn thành ít nhất 1 việc trong 24h / người tham gia bắt đầu onboarding|≥60%|
|Thời gian đến giá trị|Median từ bắt đầu form đến xác nhận kế hoạch, báo cả các lượt bỏ cuộc riêng|≤5 phút; giảm ≥30% so với cách cũ|
|D7 quay lại|Số người có hành động học vào ngày thứ 7 theo timezone / người activated đã đủ 7 ngày theo dõi|≥40%|
|Tuân thủ lịch|Số kế hoạch lưu vi phạm deadline/ngân sách / số kế hoạch lưu|0|
|AI hiệu quả|Chi phí AI thực / số kế hoạch AI được xác nhận; theo dõi cả retry và nháp bỏ|PO đặt trần trước pilot|

Báo cả tử số/mẫu số và giới hạn mẫu nhỏ, không chỉ %. Nếu chưa đạt: phân tích hành vi/định tính, thử sửa một điểm gây cản trở; không vội thêm PDF/chat. Không đo điểm thi để suy diễn tác động khi chưa có thiết kế nghiên cứu.

## Chi phí và mô hình kinh doanh

Giai đoạn đầu là pilot miễn phí có quota. Chưa tích hợp thanh toán trong ba Sprint. Giả thuyết về sau: miễn phí lập thủ công + số lượt AI giới hạn; gói trả phí có thêm lượt/lịch sử nếu người dùng sẵn sàng trả. Không khóa dữ liệu người dùng đã tạo khi hết quota.

Công thức chi phí AI tháng = số request × [(input tokens × giá input/1M) + (output tokens × giá output/1M)] / 1.000.000. Cộng retry, hosting, backup và vận hành. Lấy giá hiện hành từ provider khi chọn model; tài liệu này không giả định giá.

Ví dụ tính công suất, không phải dự báo doanh thu: 15 người × 2 lượt/ngày × 7 ngày = tối đa 210 lượt nếu đặt quota đó. Đo tokens thực tế rồi PO chốt ngân sách. Giới hạn input/output, timeout, rate limit và kill switch AI là điều kiện pilot. Chỉ thử mức giá sau khi có giá trị và chi phí đo được.

## Mapping tiêu chí môn học

|Tiêu chí ảnh cung cấp|Bằng chứng dự kiến|
|---|---|
|Product Vision|Phân khúc/JTBD/Product Goal, phỏng vấn ẩn danh|
|AI Flow|Input → AI nháp → validator/scheduler → người dùng sửa → xác nhận → lưu|
|AI Feature|Chia nhỏ và ước lượng; có fixture, trường hợp thiếu giờ và lỗi provider|
|Technical Understanding & Quality|Architecture, ADR, CI, auth tests, demo scheduler và restore|
|Scrum Implementation|Một PO, một SM, bốn Dev; artifacts, commitments, biên bản sự kiện và Increment|
|Improvement Each Sprint|Một vấn đề quan sát được + action/owner/hạn + số đo trước/sau; chưa bịa kết quả|
