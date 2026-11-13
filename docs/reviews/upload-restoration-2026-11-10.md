# Khôi phục upload từ nhánh AI — 2026-11-10

Theo yêu cầu người dùng, đưa lại chức năng upload/đọc đề cương đã có ở sprint3/AIfeat vào nhánh integration/sprint2-sprint3-all8-test.
Nhánh nguồn đã là ancestor; commit này khôi phục phần trước đó chưa nhận, với adapter cho API chung và kiểm tra kiểu tệp.

Đã nhận: chọn PDF/DOCX/TXT/MD, dán văn bản, trích xuất môn/chủ đề/ngày/phút, xem kết quả và điền biểu mẫu.
Endpoint `/api/v1/goals/parse-syllabus` dùng envelope/session/CSRF hiện có. Không dùng endpoint /api/goals hoặc fallback localStorage của prototype.
Sửa DOCX đọc như text trong nguồn thành giải nén Word XML ở server. Giới hạn file 5 MB, text 30.000 ký tự; file hỏng/sai định dạng bị từ chối.
Chế độ local có nhãn rõ và chỉ áp dụng đầu vào văn bản đọc được; không giả đã đọc PDF khi thiếu key/model. Lỗi Gemini không đổi thành kết quả giả.
Không lưu tài liệu gốc; trích xuất không tự tạo goal/task. Người dùng xem trước, điền form và bấm lưu riêng.
`requirements.md` đã ghi nhận phạm vi upload được người dùng chấp nhận cho nhánh test, không tiếp tục loại PDF theo yêu cầu cũ.

Kết quả: backend 190/190 (PostgreSQL, không skip), frontend 59/59, lint/build pass; 7 kiểm tra client JS thật với 12 request qua Vite pass.
Provider thật chưa kiểm chứng khi chưa có key/model. Chưa tự động hóa browser thao tác upload.
Chi tiết: [hợp đồng upload](../product/syllabus-upload.md); mẫu thử: [syllabus-demo.txt](../../examples/syllabus-demo.txt).
