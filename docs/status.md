# Trạng thái sản phẩm

Đối chiếu ngày 13/11/2026 với bản tích hợp Recall `2f23abe` và bộ tài liệu đã hợp nhất.
Bảng này phân biệt phần đã hiện thực với mức kiểm chứng; không phải chứng nhận production.

| Chức năng | Hiện trạng | Bằng chứng và giới hạn |
| --- | --- | --- |
| Landing/auth | Landing riêng, đăng ký/đăng nhập, session/CSRF/ownership | Test client và API; giao diện cần kiểm tra đầy đủ ở các kích thước |
| Mục tiêu/đề cương | CRUD, đọc tài liệu, rà chủ đề/nguồn/ngày thi, nhập tay | Test parser/HTTP/PostgreSQL; không bảo đảm mọi định dạng đề cương đều trích đúng |
| Lập kế hoạch AI | Draft, chỉnh/xác nhận, lưu một phần, phần chưa xếp và xếp lại | Test AI mock + DB; provider thật là lần thử riêng, cần key/model của môi trường |
| Quỹ chung | Giới hạn cứng liên môn, mức mong muốn theo môn và đồng ý phần vượt | Test biên/ngân sách/đồng thời; không phải bộ xếp giờ thực tế |
| Lịch/tiến độ | Tuần/ngày, thao tác task, quá hạn, tiến độ theo mục tiêu/tuần | API/PostgreSQL/client; không đo mức hiểu bài |
| Timer | Start/pause/resume/finish/cancel, lịch sử, chọn hoàn thành sau lưu | HTTP thật và Clock/test concurrency; browser hai tab/offline chưa được xác minh đầy đủ |
| Dự kiến/thực học | Tổng giây COMPLETED theo task, sửa dự kiến có kiểm quỹ | Test DB và HTTP; không tự suy ra thời gian còn lại |
| Recall | Nguồn → draft → sửa/activate → lật đáp án → tự đánh giá, ôn lại | API/client/DB; đã thử Gemini thật tạo 5 thẻ, activate và lưu một đánh giá trong preview |
| Google Calendar | OAuth, busy/living hours, preview/confirm, outbox sửa/xóa một chiều | Có bằng chứng mock/DB và quan sát xuất lịch; update/delete provider thật và mobile sync chưa được kiểm chứng đầy đủ |

## Kết quả kiểm tra gần nhất

- Backend local: 440 lượt chạy, 437 đạt, 3 ca PDF cá nhân tùy chọn bỏ qua; không lỗi.
- Frontend local: lint/build đạt, 150/150 test đạt.
- [Cập nhật Recall](plans/completed/dev2-recall-update-2026-11-13.md): preview nguồn và quyền sở hữu
  qua proxy đạt; Gemini thật tạo 5 thẻ có trích dẫn, retry cùng key trả đúng bộ thẻ đã lưu.
- Các suite backend trên dùng database `study_planner_test` riêng, không dùng preview/user DB.
- Bằng chứng [dự kiến/thực học](plans/completed/planned-actual-study-time-2026-11-13.md),
  [Timer hoàn thành task](plans/completed/timer-task-completion-2026-11-13.md) và
  [tích hợp Recall/Timer](plans/completed/dev2-recall-timer-integration-2026-11-13.md).
- Kết quả này không khẳng định mọi suite được bật trong CI; xem [harness](../.agents/harness/README.md).
  Một run CI phải đối chiếu đúng commit, không cộng số liệu của các lần chạy khác nhau.

## Còn cần kiểm chứng hoặc phát triển

- Luồng trình duyệt thật trên điện thoại, bàn phím, mất mạng và hai tab Timer.
- Chất lượng câu hỏi Recall với nhiều tài liệu; nguồn trích đúng chưa bảo đảm đáp án đúng nghĩa.
- Chọn phần tài liệu dài và mở Recall trực tiếp từ công việc là hướng cải tiến, chưa được coi là đã có.
- Backup/restore DB và cấu hình, triển khai production, tải lớn và branch protection chưa được xác minh.
- Spaced repetition, AI chấm đáp án, lịch tự thích ứng theo mức nhớ, thanh toán và gamification chưa thuộc MVP.

Cấu hình môi trường/provider là riêng từng máy. `.env` local có thể đã được thiết lập
nhưng không được commit; không dùng trạng thái key của một máy để mô tả toàn bộ sản phẩm.
