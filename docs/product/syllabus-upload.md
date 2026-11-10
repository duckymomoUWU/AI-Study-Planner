# Upload tài liệu để điền nhanh mục tiêu

Khôi phục từ sprint3/AIfeat theo yêu cầu người dùng ngày 2026-11-10; phạm vi mở rộng đã được người dùng chấp nhận cho nhánh test.

Trong Mục tiêu có nút **Tải tài liệu / đề cương để điền nhanh**. Người dùng chọn tệp hoặc dán văn bản, đọc kết quả và bấm **Điền vào biểu mẫu để kiểm tra**, sau đó tự bấm tạo/lưu mục tiêu.
Không tự tạo goal, task hoặc draft, không lưu tài liệu gốc vào database.

`POST /api/v1/goals/parse-syllabus` yêu cầu đăng nhập và X-CSRF-TOKEN, trả `{data:{subject,topics,startDate,examDate,minutesPerDay,extractionMethod}}`.
Body JSON nhận `content` hoặc `base64` (chính xác một đầu vào), `fileName`, `mimeType`.
Text UTF-8 nhận TXT/MD/TEXT hoặc nội dung dán; PDF và DOCX truyền base64.
File tối đa 5 MB, nội dung trích xuất tối đa 30.000 ký tự. Field lạ/sai loại, file hỏng, input mơ hồ hoặc quá lớn trả 400 VALIDATION_ERROR.
DOCX được giải nén word/document.xml và đọc XML ở server; không đọc file ZIP bằng file.text(). Không mở external entity hoặc ghi file lên ổ đĩa.

Có GEMINI_API_KEY và GEMINI_MODEL: gọi Gemini để trích xuất, validate kết quả theo goal policy, `extractionMethod=gemini`.
Không có cấu hình: TXT/MD/DOCX dùng bộ trích xuất văn bản cục bộ có nhãn `local`; PDF trả 503 AI_UNAVAILABLE vì không thể giả đã đọc PDF.
Lỗi provider không bị đổi thành thông tin học giả: trả 502/503/504 phù hợp. Không log key hoặc nội dung tài liệu.
UI cho nhập thủ công ngay cả khi trích xuất lỗi. Ngày/ngân sách và quyền sở hữu tiếp tục được kiểm tra khi thực sự lưu mục tiêu.

Kiểm thử: SyllabusServiceTest (text/DOCX/binary, mode local/PDF 503, validation và XML), CombinedFeaturesPostgresIntegrationTest (auth, CSRF, envelope và không ghi goal/task), syllabus.test.js (đọc/encode đúng loại, giới hạn, API thật thay vì placeholder).
Chưa xác minh provider Gemini thật khi không có key/model; build/mock/HTTP phải được báo riêng với browser/E2E.


## Kết quả ngày 2026-11-10

- Backend Maven verify với PostgreSQL riêng, goal.integration/auth.integration=true: 190 test, 0 lỗi, 0 bỏ qua; BUILD SUCCESS.
- Frontend: 59/59 test, lint và build pass.
- 7 kiểm tra chạy thật (12 request từ client JS frontend qua Vite): chặn chưa đăng nhập, TXT, DOCX, PDF thiếu cấu hình trả 503, không tự lưu, tạo goal sau xác nhận, cleanup/logout.
- Chưa kiểm chứng provider Gemini thật và thao tác chọn tệp trực tiếp bằng browser tự động.

Có thể thử ngay bằng `examples/syllabus-demo.txt`: Mục tiêu → Tải tài liệu / đề cương để điền nhanh → chọn file → xem kết quả → Điền vào biểu mẫu → tạo mục tiêu.
Cấu hình GEMINI_API_KEY và GEMINI_MODEL ở môi trường tiến trình backend để thử Gemini (ví dụ model gemini-3.8-flash); không đưa key vào frontend hoặc Git.
