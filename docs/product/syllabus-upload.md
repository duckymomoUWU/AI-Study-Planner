# Tải đề cương và rà nguồn

Luồng Mục tiêu cho tải tài liệu hoặc dán văn bản, xem kết quả và điền vào biểu mẫu
để rà lại. Chưa lưu goal/task chỉ vì trích tài liệu thành công.

POST `/api/v1/goals/parse-syllabus`, session + CSRF, body JSON có đúng một trong
`content` hoặc `base64`, cùng `fileName,mimeType` phù hợp. Hỗ trợ text/TXT/MD, PDF có
lớp văn bản và DOCX; tối đa 5 MiB, nội dung trích tối đa 30.000 ký tự.

PDF đọc cục bộ bằng PDFBox, DOCX đọc word/document.xml an toàn. Không có OCR hoặc
đường dẫn tải file ngoài do client tùy đặt. Trước hết parser lấy chương/chủ đề cục bộ;
chỉ khi thiếu/không hoàn chỉnh và có cấu hình Gemini mới thử trích outline có kiểm
nguồn. Không có key không đồng nghĩa PDF văn bản không đọc được.

Kết quả gồm `subject,topics,chapters,examDate,examDateCandidates,extractionMethod`;
startDate và minutesPerDay null. Ngày thi chỉ tự điền khi có nguồn rõ; người dùng tự
nhập ngày bắt đầu/quỹ và xác nhận nội dung. Không lấy số tiết, giờ tự học hay phút thi
làm quỹ cá nhân. Không lưu file gốc vào DB ở bước parse.

Field/type/file sai, quá lớn, hỏng, không đọc được chủ đề hoặc provider lỗi phải có
thông báo; cho nhập tay, không tự bịa nội dung. Các field sources/page và code lỗi chi
tiết nằm ở [hợp đồng lập kế hoạch từ đề cương](syllabus-revision-plan.md).

Đây là bước lấy đề cương cho mục tiêu, khác với nguồn tài liệu Recall lưu bền tại
`/recall/materials`. Không giả định file từng parse đã là tài liệu Recall.
Test: SyllabusService/Outline/Layout/Evidence và frontend syllabus tests.
