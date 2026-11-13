# Điều kiện nhận việc và hoàn tất

## Ready — đủ rõ để bắt đầu

- Có mục tiêu người dùng, phạm vi trong/ngoài và tiêu chí nghiệm thu kiểm tra được.
- Xác định hợp đồng API, dữ liệu, owner, lỗi và phần phụ thuộc frontend/backend.
- Có quyết định cho các lựa chọn làm thay đổi nghiệp vụ; không dùng giá trị mặc định để tự suy ra chính sách.
- Biết cách dựng môi trường và dữ liệu kiểm thử an toàn.

## Done — đủ điều kiện bàn giao

- Luồng người dùng chạy qua API thật và dữ liệu được giữ khi mở lại nếu tiêu chí yêu cầu.
- Server kiểm tra đầu vào, quyền sở hữu, trạng thái/quỹ/version; không chỉ validate ở UI.
- Có test phù hợp cho hành vi mới và kết quả đã chạy; ghi rõ mock/DB/HTTP/browser/provider.
- Loading, empty, error, retry, keyboard, tiếng Việt và màn hình hẹp được kiểm tra theo phạm vi thay đổi.
- Nếu đổi schema: migration mới, không đụng migration đã áp và đã kiểm tra nâng cấp.
- API/client/tài liệu khớp nhau; không còn placeholder/mock trên đường demo thật.
- Không commit secret hoặc dữ liệu cá nhân. PR được review theo quy trình nhóm.
- Phần chưa xác minh được ghi là chưa xác minh, không đánh dấu đạt để đóng ticket.

## Phần bàn giao trong PR

1. Vấn đề và hành vi sau thay đổi; liên kết mã công việc.
2. Phạm vi frontend/backend/migration và thay đổi contract nếu có.
3. Cách khởi động hoặc cấu hình mới; không chép key thật vào mô tả.
4. Bước demo, dữ liệu mẫu an toàn và kết quả mong đợi.
5. Lệnh/test đã chạy, kết quả và bằng chứng; ảnh/video khi cần kiểm tra giao diện.
6. Giới hạn, lỗi còn mở và việc phụ thuộc người khác.

Lệnh kiểm tra chuẩn ở [harness](../../.agents/harness/README.md), không chép lại một
bộ lệnh khác vào từng issue. Không tự kết luận đã deploy hoặc CI xanh từ test local.
