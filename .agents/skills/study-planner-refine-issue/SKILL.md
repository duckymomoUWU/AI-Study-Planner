---
name: study-planner-refine-issue
description: Làm rõ story AI Study Planner còn mơ hồ cho PO, BA hoặc dev; soạn nội dung để review, không triển khai hay đăng issue.
---

# Làm rõ story

Đọc [AGENTS.md](../../../AGENTS.md), [requirements.md](../../../requirements.md) và [harness](../../harness/README.md); đối chiếu issue với code liên quan. Giữ thay đổi đang làm dở. Dùng skill này độc lập, không tự gọi skill khác.

- Nêu người dùng, vấn đề và kết quả cần đạt. Tách yêu cầu đã chốt (kèm nguồn) khỏi đề xuất, giả định và hành vi đang có. Khi tài liệu và code khác nhau, ghi rõ khác biệt; không tự coi code là quyết định sản phẩm.
- Đọc docs/status.md và code thật: dự án đã có API nghiệp vụ, Timer và Recall. lib/api.js chỉ phục vụ health; không dùng mô tả starter làm hiện trạng.
- Chọn một luồng nhỏ có thể demo; chỉ rõ trong/ngoài phạm vi và phụ thuộc còn thiếu. Không biến toàn bộ requirements thành phạm vi của một story.
- Viết tiêu chí theo dữ liệu đầu vào, thao tác và kết quả quan sát được, gồm ca thành công, lỗi và ngoại lệ liên quan. Với lịch học, đối chiếu giới hạn ngày/phút, quỹ chung và mức mong muốn từng môn, bản nháp cần xác nhận và đường tạo việc thủ công khi AI lỗi trong requirements.
- Hỏi PO trước khi chốt lựa chọn làm thay đổi hành vi hoặc phạm vi, kể cả khi issue mâu thuẫn requirements. Trong khi chờ, đánh dấu câu hỏi mở; không tự đưa đề xuất vào tiêu chí đã chốt.

## Đầu ra để review

Mục tiêu; phạm vi trong/ngoài; quyết định đã chốt và nguồn; đề xuất riêng; tiêu chí chấp nhận có mã để kiểm tra; trường hợp ngoại lệ; câu hỏi còn mở và ảnh hưởng của chúng. Trỏ tới harness cho cách xác minh khả thi, không coi lệnh được ghi trong tài liệu là bằng chứng đã chạy.

Chỉ soạn nội dung trong phản hồi để team review; không sửa ứng dụng, tự cập nhật yêu cầu hoặc đăng/sửa issue trên hệ thống bên ngoài.
