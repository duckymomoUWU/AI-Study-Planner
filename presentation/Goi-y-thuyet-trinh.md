# Gợi ý thuyết trình — AI Study Planner

12 slide; thời lượng gợi ý 8–10 phút. Nội dung dưới đây cũng có trong Speaker Notes của PowerPoint.

## Slide 1: Giới thiệu

Hôm nay nhóm trình bày AI Study Planner, ứng dụng hỗ trợ sinh viên lập kế hoạch ôn tập. Nội dung tập trung vào requirements, user stories và tiêu chí chấp nhận để nhóm biết sẽ xây dựng điều gì. Hình bên phải chỉ minh họa một lịch ba ngày, không phải ảnh sản phẩm đã hoàn thiện. Sản phẩm theo hướng AI-assisted: AI đưa ra gợi ý nhưng người học quyết định chỉnh sửa và lưu. Nhóm gồm một Product Owner, một Scrum Master và bốn Developers. Bản hiện tại mới có nền tảng Spring Boot và database PostgreSQL.

## Slide 2: Vấn đề và giá trị

Chúng ta bắt đầu từ vấn đề của sinh viên, không bắt đầu từ việc muốn thêm AI. Sinh viên thường biết ngày thi nhưng việc chia nội dung và sắp lịch vẫn mất công. Giá trị nhóm kỳ vọng là giúp họ biết hôm nay cần học gì và tự điều chỉnh khi thực tế thay đổi. Đây hiện là giả thuyết, chưa có dữ liệu khảo sát để khẳng định. PO sẽ phỏng vấn sinh viên có kỳ thi sắp tới, quan sát cách họ lên lịch hiện nay, rồi thử nghiệm sản phẩm. Chỉ số có thể theo dõi là thời gian từ nhập thông tin đến xác nhận kế hoạch và mức độ cần trợ giúp; không suy diễn thành cải thiện điểm thi.

## Slide 3: Requirements chức năng

MVP có sáu nhóm chức năng. Tài khoản đảm bảo dữ liệu riêng tư. Mục tiêu lưu môn học, nội dung, thời hạn và thời gian có thể học. Người dùng có thể tạo công việc thủ công ngay cả khi chưa dùng AI. AI gợi ý công việc và kế hoạch nhưng phải đi qua bước xem và xác nhận. Tiến độ thể hiện các công việc đã làm. Các yêu cầu được đánh mã FR để liên kết với user stories ở phần sau. Để giữ phạm vi phù hợp nhóm sáu người, bản đầu chưa đọc PDF, kết nối LMS, đồng bộ lịch hoặc đánh giá tự động mức độ hiểu bài.

## Slide 4: Quy tắc nghiệp vụ

Nhóm cần thống nhất business rules trước khi viết code. Trong MVP, ngày thi không được tính là ngày ôn. Ngân sách tính theo từng mục tiêu, nên chưa tuyên bố tối ưu tổng thời gian của tất cả các môn. Nếu tổng thời lượng vượt khả năng học, ứng dụng phải nói rõ thiếu bao nhiêu phút, không âm thầm bỏ công việc hoặc giảm ước lượng cho vừa. Thời lượng AI chỉ mang tính tham khảo. Người dùng luôn xác nhận trước khi lưu. Tiến độ tính theo số công việc, vì vậy không được diễn giải tỷ lệ hoàn thành thành tỷ lệ hiểu bài. Nguồn: requirements.md ở commit 452ff4c.

## Slide 5: Luồng AI-assisted

Sinh viên nhập thông tin và yêu cầu đề xuất. AI có nhiệm vụ chia nhỏ nội dung và ước lượng, còn việc kiểm tra ràng buộc và phân bổ lịch do logic server thực hiện. Bản nháp được đưa ra để sinh viên sửa. Chỉ sau khi xác nhận, server mới kiểm tra lại và lưu kế hoạch. Điều này phân biệt rõ AI-assisted với việc để AI tự quyết định và thay đổi dữ liệu. Nếu dịch vụ AI lỗi hoặc trả dữ liệu không hợp lệ, người dùng không bị chặn: đầu vào được giữ và có thể tự tạo công việc. Provider AI chưa được chọn hoặc kết nối trong bộ khung hiện tại.

## Slide 6: Backlog user stories

Một requirement có thể tách thành nhiều user stories. Ví dụ yêu cầu tài khoản bao gồm đăng nhập và kiểm tra cách ly dữ liệu. Mỗi story phải đem lại giá trị cho sinh viên, không chỉ đặt tên theo màn hình hoặc bảng database. Các mã US giữ nguyên từ bộ backlog đã bàn giao; bảng này chỉ trích những story cốt lõi, không phải toàn bộ 21 story. Tất cả đều là ưu tiên đề xuất để PO và nhóm refinement. Cột Sprint giúp nhìn hướng phát triển nhưng Developers sẽ ước lượng và chọn lượng việc dựa trên công suất thật, không coi đây là cam kết hoàn thành toàn bộ.

## Slide 7: US03 — Tạo mục tiêu

Cấu trúc user story gồm người dùng, mong muốn và giá trị. Với US03, sinh viên cần nhập thông tin để bắt đầu kế hoạch. Acceptance criteria làm yêu cầu kiểm thử được. Trường hợp hợp lệ tạo mục tiêu thuộc đúng người dùng, không lấy user ID tùy ý từ client. Trường hợp ngày thi không hợp lệ phải bị từ chối. Với dữ liệu thiếu, giao diện cần chỉ rõ trường sai và giữ phần người dùng đã nhập. Ví dụ minh họa có ba ngày ôn trước ngày thi. Mục tiêu của phần này là để PO, Developer và người kiểm thử hiểu cùng một hành vi trước khi code.

## Slide 8: US09 — Ngân sách thời gian

Ví dụ đầu vào đề xuất có tổng 180 phút công việc và ba ngày để ôn. Nếu mỗi ngày 60 phút thì đủ 180 phút. Nếu mỗi ngày chỉ có 45 phút, tổng khả năng là 135 phút, thiếu 45 phút. Kết quả đúng trong trường hợp hai là báo thiếu và yêu cầu người dùng tăng thời gian hoặc giảm phạm vi. Không được tự làm biến mất công việc để tạo cảm giác kế hoạch hợp lệ. Acceptance criteria gồm không vượt deadline, không vượt ngân sách ngày, giữ đầy đủ tổng công việc hoặc báo không khả thi. Đây là ví dụ dữ liệu để kiểm thử, không phải kết quả nghiên cứu.

## Slide 9: US11 — Xác nhận lưu kế hoạch

Điểm kiểm soát quan trọng của sản phẩm là xác nhận. Bản nháp chưa được tính vào kế hoạch đang thực hiện. Sau khi người dùng sửa, server cần kiểm tra lại toàn bộ ràng buộc trước khi lưu. Một lần xác nhận phải lưu nguyên tử: không tạo nửa kế hoạch nếu có lỗi. Khi người dùng bấm hai lần hoặc mạng gửi lại yêu cầu, cơ chế chống trùng chỉ tạo một kế hoạch. Nếu ngày thi hoặc quỹ giờ của mục tiêu đã thay đổi từ lúc tạo nháp, cần báo xung đột. US12 bổ sung lối đi thủ công khi AI lỗi. Các tiêu chí này còn cần hiện thực trong backend Spring.

## Slide 10: Yêu cầu chất lượng và Definition of Done

Một chức năng chỉ đạt chất lượng khi có bằng chứng phù hợp. Kiểm tra quyền riêng tư cần thử truy cập chéo hai tài khoản, không chỉ xem giao diện có ẩn dữ liệu hay không. Validation phải ở server. Trường hợp AI lỗi cần vẫn tiếp tục bằng cách thủ công. Với tiến độ, hai trên năm công việc là 40 phần trăm nhưng không nói sinh viên đã hiểu 40 phần trăm kiến thức. DoD là tiêu chuẩn chung của nhóm, còn acceptance criteria mô tả riêng cho mỗi story. Bản Spring hiện mới kiểm tra build, DB và health; chưa có các kiểm thử nghiệp vụ ở bảng này. Không đưa secrets hoặc nội dung học vào log.

## Slide 11: Tổ chức Scrum và dự báo Sprint

Nhóm có sáu thành viên: PO chịu trách nhiệm về giá trị và thứ tự backlog, Scrum Master hỗ trợ hiệu quả Scrum Team, bốn Developers cùng chịu trách nhiệm tạo Increment dùng được. Sprint 1 hướng tới luồng thủ công độc lập với AI, Sprint 2 thêm đề xuất có kiểm soát, Sprint 3 hoàn thiện và học từ phản hồi thực tế. Đây là dự báo chứ chưa cam kết ngày hoặc khối lượng. Nhóm thực hiện Planning, Daily Scrum 15 phút cho Developers, Review với người dùng và Retrospective. Mỗi Sprint chọn một vấn đề thực tế và một hành động cải thiện đo được. Chất lượng phải có trong từng Sprint, không để hết đến Sprint 3. Cơ sở: Scrum Guide 2020 tiếng Việt, phần Scrum Team, Events và Artifacts.

## Slide 12: Trạng thái hiện tại và bước tiếp theo

Công nghệ đã chọn là Java 21 và Spring Boot cho backend, PostgreSQL 17 cho database, Flyway cho migration, Maven và Docker Compose cho môi trường làm việc. Bộ khung đã build thành công, kết nối DB và health trả về UP sau khởi động lại. Schema ban đầu có app_users, study_goals, study_tasks và plan_drafts. Chưa có đăng nhập, API nghiệp vụ, frontend hoặc AI thật; không trình bày chúng như chức năng đã xong. Trước mốc bàn giao tuần tới, nhóm cần review requirements và acceptance criteria, Developers ước lượng và cùng PO chọn phạm vi Sprint đầu. Nguồn chính: https://github.com/duckymomoUWU/AI-Study-Planner/blob/452ff4cec54e99ccaef725ed1a6ccabcf6111250/requirements.md ; setup.md cùng commit. User stories tham chiếu docs/backlog.md ở commit ef2afefa805e2f86fcfb7cafb30f1e7b0d2ed4af. Các mã FR trong deck là mã tham chiếu trình bày cho sáu nhóm requirements.