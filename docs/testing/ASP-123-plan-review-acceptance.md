# ASP-123: nghiệm thu duyệt, chỉnh sửa và xác nhận kế hoạch AI

Ngày: 2026-11-14. Nhánh kiểm tra: `fix/plan-draft-review` (từ `dev2`). Dựa trên [báo cáo kiểm thử ASP-57](ASP-57-plan-review-confirm-test-report.md); không lặp lại các ca ở đó.

## Kết luận

**Chưa đủ điều kiện nghiệm thu, chưa nên đóng story.** Cả 4 AC đạt về chức năng ở mức đã kiểm tra. Điều kiện còn thiếu nằm ở quy trình của Definition of Done: chưa có PR, review hay CI, chưa lưu ảnh/video demo, và một lựa chọn sản phẩm của AC-01 chưa được PO xác nhận.

## 1. Checklist AC

| AC | Kết quả | Bằng chứng |
| --- | --- | --- |
| AC-01 sửa tên, phút, ngày; thêm hoặc bỏ việc mà chưa đổi lịch đã lưu | **Đạt, chờ PO xác nhận cách hiểu "bỏ"** | Sửa và dữ liệu sai: 15 ca API/DB. Thêm: `addedTasks` (3 test PostgreSQL mới, thử phá mã để chắc test bắt lỗi). Bỏ: việc chuyển sang phần chưa xếp, không bị xóa. Giao diện thật với Gemini thật: lịch đã lưu vẫn 0 việc trước khi xác nhận; lưu xong có 2 việc AI và 1 việc `manual` đúng chủ đề |
| AC-02 kiểm tra lại ngày và ngân sách trước khi xác nhận | Đạt | 409 khi mục tiêu đổi, 422 khi hạ quỹ chung hoặc môn khác chiếm quỹ, 410 khi hết hạn, ngày ngoài khoảng bị 400; mọi ca không lưu gì. Việc người dùng thêm cũng bị kiểm quỹ (test mới) |
| AC-03 lưu phần đã xếp, giữ phần chưa xếp; nút nêu số lưu/giữ và cảnh báo chưa bao phủ | Đạt | Lưu một phần có và không có `deferredTasks`, lặp cùng key, id lạ bị từ chối. Nút ghi "Lưu N việc vào lịch (gồm M việc bạn thêm). K việc còn lại được giữ" và "kế hoạch chưa bao phủ hết nội dung" |
| AC-04 reload vẫn thấy lịch và phần chưa xếp; xếp phần còn lại không bắt buộc gọi AI | Đạt | Đăng nhập lại và tải lại trang vẫn còn; xếp lại chỉ gọi `POST …/reschedule`, không gọi sinh bản nháp; tăng quỹ chung khi bản nháp đang mở nay tự làm mới (đã kiểm trên giao diện) |

## 2. Checklist DoD ([06-DOD-VA-KIEM-THU.md](../team-handoff/06-DOD-VA-KIEM-THU.md))

| Điều kiện | Kết quả | Ghi chú |
| --- | --- | --- |
| Luồng chạy qua API thật, dữ liệu giữ khi mở lại | Đạt | 49 ca API + PostgreSQL + Gemini thật; giao diện thật |
| Server kiểm tra đầu vào, quyền sở hữu, trạng thái, quỹ, version | Đạt | Người khác nhận 404 ở 6 thao tác, chưa đăng nhập 401, thiếu CSRF 403; `addedTasks` đi qua cùng kiểm tra |
| Có test cho hành vi mới, ghi rõ loại | Đạt | Backend 443 chạy, 0 lỗi, 3 bỏ qua; frontend 152 đạt; lint và build đạt. Mock, DB, HTTP, trình duyệt và Gemini thật được phân biệt trong báo cáo ASP-57 |
| Loading, empty, error, retry, bàn phím, tiếng Việt, màn hình hẹp | **Đạt một phần** | Ở 375 px: không tràn ngang kể cả tiêu đề 200 ký tự trong việc thêm; Tab và Enter dùng được; phát hiện và đã sửa lỗi mất focus khi bấm "Bỏ khỏi lần lưu này" và "Xóa việc đã thêm". Chưa thử đường lỗi "AI chưa sẵn sàng" (503) và thử lại trên giao diện |
| Đổi schema thì có migration mới | Đạt (không áp dụng) | Không đổi schema |
| API, client và tài liệu khớp nhau | Đạt | Cập nhật [syllabus-revision-plan.md](../product/syllabus-revision-plan.md) và [status.md](../status.md) |
| Không commit secret hay dữ liệu cá nhân | Đạt | Quét repo không thấy khóa hay bí mật; tài khoản kiểm thử chỉ nằm ngoài repo |
| PR được review theo quy trình nhóm | **Chưa đạt** | Chưa có PR hay review |
| Phần chưa xác minh được ghi rõ | Đạt | Mục 4 |

## 3. Regression và quyền dữ liệu

- Regression: chạy lại toàn bộ backend (443) và frontend (152) trên cùng nhánh; các suite Timer, Recall, Calendar, quỹ chung, tiến độ cùng đạt.
- Quyền dữ liệu: xem báo cáo ASP-57, nhóm "Quyền dữ liệu" và "Xác thực".

## 4. Điểm chưa đạt và cần theo dõi

| # | Điểm | Ảnh hưởng | Việc cần làm |
| --- | --- | --- | --- |
| 1 | Chưa có PR, review, CI | Không đóng được DoD | Mở PR có mã ASP-57/123, gắn các tài liệu này, chờ review; không suy ra CI xanh từ chạy local |
| 2 | Cách hiểu "bỏ việc" (AC-01): hiện là bỏ khỏi lần lưu này, việc được giữ ở phần chưa xếp, không xóa vĩnh viễn | Nếu PO muốn xóa hẳn khỏi kế hoạch thì cần thêm thao tác và đổi hợp đồng | PO xác nhận |
| 3 | Chưa lưu ảnh hoặc video demo | Thiếu bằng chứng giao diện | Chụp: bản nháp thiếu thời gian, bỏ/thêm việc, lưu một phần, mở lại phần còn lại, xếp lại |
| 4 | Chưa thử: đường lỗi 503 và thử lại trên giao diện, hai tab xác nhận đồng thời, Gemini 429 hoặc hết thời gian, điện thoại thật, trình đọc màn hình | Có thể còn lỗi ở các đường này | Chạy trước khi đóng, hoặc ghi chấp nhận rủi ro |
| 5 | Bản nháp cũ không có phạm vi chủ đề: không có "Việc bạn thêm" (server từ chối `addedTasks` với draft loại này); nhánh từ chối chưa có test riêng | Thấp, draft loại này không còn được sinh ở luồng mặc định | Thêm test nếu giữ tương thích lâu dài |

Trạng thái story nên giữ ở "đang chờ review/nghiệm thu", không chuyển Done cho tới khi mục 1 và 2 xong.
