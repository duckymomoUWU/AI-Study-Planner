# Product Backlog & Epic planning

Đây là backlog đề xuất để refinement tuần 02–08/11/2026; chưa phải toàn bộ chức năng đã làm. PO sắp thứ tự; Developers ước lượng và chọn lượng việc phù hợp. Các Sprint dưới đây là dự báo, chưa gắn ngày bắt đầu.

## Product Goal
Sinh viên có thể biến nội dung ôn thành lịch khả thi, tự điều chỉnh và thực hiện từng ngày; kiểm chứng giảm công sức lập kế hoạch qua pilot.

## Epics
|Epic|Kết quả|Giá trị|Bắt đầu dự kiến|
|---|---|---|---|
|E1|Tài khoản và dữ liệu cá nhân|Sinh viên quản lý kế hoạch riêng, không lộ dữ liệu cho người khác.|Sprint 1|
|E2|Mục tiêu và kế hoạch thủ công|Không cần AI vẫn lập và thực hiện được lịch ôn.|Sprint 1|
|E3|Đề xuất AI có kiểm soát|Giảm công sức chia nhỏ nội dung nhưng người học quyết định.|Sprint 2|
|E4|Công việc hôm nay và tiến độ|Biết việc cần làm, cập nhật tiến độ và xử lý việc trễ.|Sprint 3|
|E5|Chất lượng và vận hành|Có bằng chứng kiểm thử, triển khai và khả năng phục hồi.|Sprint 1|
|E6|Kiểm chứng giá trị|Kiểm tra sản phẩm có giải quyết đúng khó khăn trước khi mở rộng.|Sprint 1|

## Sprint forecast
|Sprint|Sprint Goal dự kiến|Story ứng viên|Demo giá trị|
|---|---|---|---|
|1|Sinh viên lập và thực hiện lịch ôn thủ công, dữ liệu riêng tư|US01–07, US15, US19|Đăng nhập, tạo mục tiêu, thêm việc, xem hôm nay, đánh dấu xong và refresh|
|2|Sinh viên chủ động biến đề xuất AI thành kế hoạch khả thi|US08–12, US16, US20|Xem/sửa/xác nhận nháp; quá tải và AI lỗi vẫn có cách tiếp tục|
|3|Sinh viên điều chỉnh lịch trong thực tế; nhóm đủ bằng chứng để chạy pilot|US13–14, US17–18, US21|Việc trễ, thống kê, quota, restart/restore, phản hồi pilot|

Mỗi Sprint có kiểm thử, bảo mật và cải tiến ngay trong Sprint; không dồn chúng đến Sprint 3. Sprint 1 chỉ cần số hoàn thành cơ bản; Sprint 3 nâng cách trình bày. Chưa biết giờ rảnh của từng người, nên không gán story points hoặc cam kết hoàn thành mọi story. Nếu quá tải, PO và Developers cắt scope quanh Sprint Goal, giữ chất lượng.

## US01 — Đăng ký và đăng nhập
Là sinh viên, tôi muốn tạo tài khoản và đăng nhập để dùng kế hoạch riêng.
- Epic: E1 · Must · dự kiến Sprint 1 · trạng thái Backlog
- Phụ thuộc: Không · Ước lượng: Developers xác định

Acceptance criteria:
1. Given email hợp lệ chưa tồn tại, When đăng ký đúng quy tắc mật khẩu, Then tạo tài khoản và đăng nhập được.
2. Given mật khẩu sai, When đăng nhập, Then trả thông báo chung và không tạo phiên; giới hạn lần thử.
3. When đăng xuất, Then phiên bị vô hiệu; mật khẩu được hash bằng thư viện đã được kiểm chứng, cookie HttpOnly/Secure khi triển khai.

## US02 — Cách ly dữ liệu theo người dùng
Là sinh viên, tôi muốn chỉ mình truy cập dữ liệu học của mình để giữ riêng tư.
- Epic: E1 · Must · dự kiến Sprint 1 · trạng thái Backlog
- Phụ thuộc: US01 · Ước lượng: Developers xác định

Acceptance criteria:
1. Given A và B có mục tiêu riêng, When B đọc/sửa/xóa ID của A qua API, Then bị từ chối và dữ liệu không đổi.
2. Given chưa đăng nhập, When gọi API dữ liệu cá nhân, Then trả 401.
3. Mọi truy vấn mục tiêu, công việc, đề xuất và tiến độ đều kiểm tra chủ sở hữu phía server; có integration tests hai tài khoản.

## US03 — Tạo mục tiêu ôn tập
Là sinh viên, tôi muốn nhập môn, chủ đề, ngày thi và thời gian để bắt đầu kế hoạch.
- Epic: E2 · Must · dự kiến Sprint 1 · trạng thái Backlog
- Phụ thuộc: US01, US02 · Ước lượng: Developers xác định

Acceptance criteria:
1. Given môn 1–100 ký tự và 1–30 chủ đề hợp lệ, When lưu, Then mục tiêu thuộc tài khoản hiện tại.
2. Ngày bắt đầu mặc định hôm nay theo múi giờ người dùng; ngày thi phải sau ngày bắt đầu, thời gian 15–480 phút/ngày, khoảng ôn tối đa 90 ngày.
3. Given dữ liệu sai hoặc thiếu, When lưu, Then báo đúng trường, không mất phần đã nhập và server từ chối lưu.

## US04 — Sửa và xóa mục tiêu
Là sinh viên, tôi muốn sửa hoặc xóa mục tiêu để phản ánh kỳ thi thực tế.
- Epic: E2 · Must · dự kiến Sprint 1 · trạng thái Backlog
- Phụ thuộc: US03 · Ước lượng: Developers xác định

Acceptance criteria:
1. When xóa mục tiêu và xác nhận, Then xóa mục tiêu cùng kế hoạch/con việc liên quan trong transaction; hủy xác nhận không thay đổi gì.
2. When thay ngày thi hoặc quỹ giờ làm lịch hiện có không hợp lệ, Then hiển thị xung đột và yêu cầu chỉnh lịch, không âm thầm bỏ công việc.
3. Given phiên bản mục tiêu đã thay đổi ở tab khác, When lưu bản cũ, Then báo xung đột và cho tải lại thay vì ghi đè.

## US05 — Tạo và sửa công việc thủ công
Là sinh viên, tôi muốn tự thêm công việc để lập kế hoạch khi không dùng AI.
- Epic: E2 · Must · dự kiến Sprint 1 · trạng thái Backlog
- Phụ thuộc: US03 · Ước lượng: Developers xác định

Acceptance criteria:
1. When nhập tiêu đề, ngày và số phút nguyên dương trong giới hạn, Then tạo công việc ở trạng thái chưa hoàn thành.
2. When ngày nằm ngoài khoảng ôn hoặc tổng phút trong ngày vượt ngân sách của mục tiêu, Then từ chối kèm số phút vượt.
3. When sửa/xóa việc, Then cập nhật danh sách và tiến độ; đường thủ công không gọi dịch vụ AI.

## US06 — Xem công việc theo ngày
Là sinh viên, tôi muốn xem việc hôm nay để bắt đầu học ngay.
- Epic: E2 · Must · dự kiến Sprint 1 · trạng thái Backlog
- Phụ thuộc: US05 · Ước lượng: Developers xác định

Acceptance criteria:
1. When mở trang hôm nay, Then hiển thị ngày theo timezone đã chọn, việc của tất cả mục tiêu và tổng thời lượng.
2. Ngày không có việc hiển thị trạng thái trống và hành động thêm việc.
3. When chọn ngày khác, Then chỉ thấy việc thuộc ngày đó; việc trễ hiện riêng, không tự đổi ngày.

## US07 — Hoàn thành và mở lại công việc
Là sinh viên, tôi muốn đánh dấu hoàn thành để biết phần còn lại.
- Epic: E4 · Must · dự kiến Sprint 1 · trạng thái Backlog
- Phụ thuộc: US06 · Ước lượng: Developers xác định

Acceptance criteria:
1. When đánh dấu xong hoặc mở lại, Then trạng thái lưu bền và còn đúng sau refresh.
2. Given gửi lặp cùng một yêu cầu, Then kết quả không tăng đếm hai lần.
3. Tiến độ = số công việc hoàn thành / tổng công việc hiện có; 0 việc hiển thị 0/0 và Chưa có công việc, không báo đã học xong.

## US08 — Yêu cầu AI chia nhỏ nội dung
Là sinh viên, tôi muốn AI gợi ý công việc và thời lượng để giảm công sức lập kế hoạch.
- Epic: E3 · Must · dự kiến Sprint 2 · trạng thái Backlog
- Phụ thuộc: US03, US05 · Ước lượng: Developers xác định

Acceptance criteria:
1. When bấm đề xuất, Then gửi môn, chủ đề và ràng buộc cần thiết; không gửi email hoặc mật khẩu.
2. Kết quả phải có title, topic, estimatedMinutes theo JSON schema; server từ chối phần tử sai kiểu/ngoài giới hạn, tối đa 100 việc.
3. Nội dung người dùng chỉ là dữ liệu; chỉ dẫn chèn trong chủ đề không được quyền thay đổi schema, thực thi mã hay gọi công cụ.

## US09 — Phân bổ lịch và báo thiếu thời gian
Là sinh viên, tôi muốn lịch vừa thời gian để nhận ra phạm vi không khả thi.
- Epic: E3 · Must · dự kiến Sprint 2 · trạng thái Backlog
- Phụ thuộc: US08 · Ước lượng: Developers xác định

Acceptance criteria:
1. Given 180 phút công việc, 3 ngày trước thi, 60 phút/ngày, Then mỗi ngày tối đa 60 phút và không có việc ngày thi hoặc sau đó.
2. Given 180 phút công việc nhưng chỉ có 135 phút, Then báo thiếu 45 phút và yêu cầu giảm phạm vi/tăng thời gian; không tự giảm ước lượng cho vừa.
3. Công việc dài được chia thành phiên giữ tổng số phút; bộ kiểm tra xác định xử lý lịch, không tin ngày AI tự đề xuất.

## US10 — Xem và chỉnh bản đề xuất
Là sinh viên, tôi muốn sửa đề xuất trước khi lưu để phù hợp cách học của mình.
- Epic: E3 · Must · dự kiến Sprint 2 · trạng thái Backlog
- Phụ thuộc: US09 · Ước lượng: Developers xác định

Acceptance criteria:
1. When nhận đề xuất, Then hiển thị nhãn Bản nháp và Thời lượng tham khảo; kế hoạch hiện hành chưa thay đổi.
2. When sửa tiêu đề, thời lượng, ngày hoặc bỏ việc, Then kiểm tra lại lịch và báo mọi vi phạm ngân sách.
3. When hủy nháp, Then không thay đổi việc đã lưu hoặc đã hoàn thành.

## US11 — Xác nhận lưu kế hoạch
Là sinh viên, tôi muốn chủ động xác nhận để chỉ lưu kế hoạch tôi đồng ý.
- Epic: E3 · Must · dự kiến Sprint 2 · trạng thái Backlog
- Phụ thuộc: US10 · Ước lượng: Developers xác định

Acceptance criteria:
1. When xác nhận nháp hợp lệ, Then lưu nguyên tử mọi công việc; lỗi giữa chừng không tạo kế hoạch dở dang.
2. Given bấm lưu hai lần hoặc retry mạng, Then cùng idempotency key chỉ sinh một kế hoạch.
3. Given mục tiêu đã đổi từ lúc tạo nháp, Then từ chối nháp cũ bằng version check; khi thay lịch giữ nguyên việc đã hoàn thành, preview rõ phần thay thế.

## US12 — Tiếp tục khi AI lỗi
Là sinh viên, tôi muốn tự lập việc khi AI không hoạt động để không bị chặn học.
- Epic: E3 · Must · dự kiến Sprint 2 · trạng thái Backlog
- Phụ thuộc: US05, US08 · Ước lượng: Developers xác định

Acceptance criteria:
1. Given AI timeout 20 giây, 429, 5xx hoặc JSON sai, Then báo lỗi dễ hiểu, giữ đầu vào và cung cấp nút tự tạo việc.
2. Không tự retry vô hạn: tối đa một retry cho lỗi tạm thời, không retry sai schema hoặc hết quota.
3. Không ghi key hoặc nội dung học vào log; thông báo có mã yêu cầu để tra lỗi.

## US13 — Xử lý việc quá hạn
Là sinh viên, tôi muốn thấy việc chưa xong để chủ động điều chỉnh.
- Epic: E4 · Should · dự kiến Sprint 3 · trạng thái Backlog
- Phụ thuộc: US07, US09 · Ước lượng: Developers xác định

Acceptance criteria:
1. Việc chưa xong trước hôm nay được ghi Trễ, không đánh dấu xong và không tự chuyển ngày.
2. When chọn dời việc, Then hiện preview ngân sách ngày đích và chỉ lưu sau xác nhận.
3. Given không còn ngày trước thi, Then báo không thể xếp và cho giảm phạm vi; không đẩy việc sau kỳ thi.

## US14 — Xem tiến độ mục tiêu
Là sinh viên, tôi muốn xem tỷ lệ công việc đã hoàn thành để theo dõi thực hiện.
- Epic: E4 · Must · dự kiến Sprint 3 · trạng thái Backlog
- Phụ thuộc: US07 · Ước lượng: Developers xác định

Acceptance criteria:
1. When hoàn thành 2/5 việc, Then hiển thị 40%, số hoàn thành và số còn lại.
2. When xóa/thêm/chia công việc, Then tính lại mẫu số; giao diện giải thích tiến độ đo công việc, không đo kiến thức.
3. Thống kê chỉ lấy việc đã lưu; bản nháp AI không được tính.

## US15 — Khởi tạo công nghệ và CI
Là Developer, tôi muốn clone và chạy kiểm tra bằng lệnh thống nhất để cả nhóm cộng tác.
- Epic: E5 · Must · dự kiến Sprint 1 · trạng thái Backlog
- Phụ thuộc: Không · Ước lượng: Developers xác định

Acceptance criteria:
1. Từ Node 24 và checkout có lockfile, npm ci cài được, npm run check kiểm tra type/test/build thành công.
2. Server khởi động; health xác nhận SQLite đọc/ghi được; request lập lịch hợp lệ và không khả thi đều có kết quả đúng.
3. Có README, .env.example không secret và CI trên pull request; chưa có xác thực thì chỉ dùng dữ liệu mẫu.

## US16 — Kiểm thử tích hợp và E2E luồng chính
Là Developer, tôi muốn kiểm thử các ranh giới để bảo vệ dữ liệu và lịch.
- Epic: E5 · Must · dự kiến Sprint 2 · trạng thái Backlog
- Phụ thuộc: US02, US11, US12 · Ước lượng: Developers xác định

Acceptance criteria:
1. Playwright chạy đăng nhập → mục tiêu → nháp → sửa → xác nhận → hoàn thành → refresh và kiểm tra kết quả.
2. Test API kiểm tra IDOR, phiên hết hạn, lưu trùng và lỗi transaction; scheduler kiểm tra leap day, deadline và vượt ngân sách.
3. CI chặn merge khi check lỗi; fake provider dùng cho CI, live AI smoke test là kiểm tra riêng có quota.

## US17 — Giới hạn chi phí và vận hành AI
Là PO, tôi muốn giới hạn lượt AI để vận hành thử trong ngân sách.
- Epic: E5 · Must · dự kiến Sprint 3 · trạng thái Backlog
- Phụ thuộc: US08 · Ước lượng: Developers xác định

Acceptance criteria:
1. Server áp quota theo tài khoản và rate limit, không chỉ disable nút; hết quota vẫn dùng thủ công.
2. Ghi model/version, latency, tokens, status, request ID; không ghi nội dung học; có giới hạn đầu vào và output tokens.
3. PO đặt ngân sách pilot và ngưỡng dừng; kiểm tra khóa AI khẩn cấp vẫn giữ luồng thủ công.

## US18 — Triển khai thử và phục hồi dữ liệu
Là nhóm, tôi muốn demo ổn định để thu phản hồi từ sinh viên.
- Epic: E5 · Must · dự kiến Sprint 3 · trạng thái Backlog
- Phụ thuộc: US16 · Ước lượng: Developers xác định

Acceptance criteria:
1. Triển khai một Node server có TLS và volume SQLite bền; restart không mất kế hoạch.
2. Có backup và diễn tập restore trên database thử, xác nhận số bản ghi/mục tiêu; không chỉ kiểm tra file tồn tại.
3. Có health check, hướng dẫn rollback và lỗi mạng được hiển thị; dữ liệu thử tách dữ liệu thật.

## US19 — Kiểm chứng vấn đề với sinh viên
Là PO, tôi muốn phỏng vấn sinh viên sắp thi để chọn đúng vấn đề.
- Epic: E6 · Must · dự kiến Sprint 1 · trạng thái Backlog
- Phụ thuộc: Không · Ước lượng: Developers xác định

Acceptance criteria:
1. Phỏng vấn ít nhất 5 sinh viên có kỳ thi trong 2–4 tuần, hỏi lần ôn gần nhất và cách chia lịch thực tế.
2. Ghi nhận đồng ý tham gia và dữ liệu ẩn danh: khó khăn, cách hiện tại, thời gian lập lịch; không biến giả định thành kết quả.
3. PO tổng hợp các vấn đề lặp lại, chọn một giả thuyết giá trị và cập nhật thứ tự backlog.

## US20 — Thử khả dụng bản đề xuất
Là PO, tôi muốn quan sát người dùng lập lịch để tìm bước khó.
- Epic: E6 · Should · dự kiến Sprint 2 · trạng thái Backlog
- Phụ thuộc: US11 · Ước lượng: Developers xác định

Acceptance criteria:
1. Ít nhất 5 người thử cùng kịch bản, ghi thời gian đến kế hoạch xác nhận và số lần cần trợ giúp.
2. Ghi tỷ lệ sửa/hủy nháp và lý do, không coi mọi sửa đổi là thất bại AI.
3. Chọn một thay đổi UX dựa trên quan sát, tạo issue có tiêu chí và đo lại ở Sprint sau.

## US21 — Đánh giá pilot và quyết định tiếp tục
Là PO, tôi muốn dữ liệu sử dụng để quyết định đầu tư tiếp.
- Epic: E6 · Should · dự kiến Sprint 3 · trạng thái Backlog
- Phụ thuộc: US14, US20 · Ước lượng: Developers xác định

Acceptance criteria:
1. Mời 10–15 sinh viên thử 7 ngày, đo activation và quay lại ngày 7 theo định nghĩa trong business.md.
2. Báo cáo n/N, mẫu nhỏ và trường hợp bỏ cuộc, không tuyên bố cải thiện điểm thi khi chưa có bằng chứng.
3. Kết thúc bằng quyết định tiếp tục/đổi hướng/dừng và một thử nghiệm tiếp theo; chưa mở thu phí chỉ vì có ý tưởng giá.
