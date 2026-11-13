# Kế hoạch phối hợp

Landing, Timer và Recall đã được tích hợp. Với công việc tiếp theo, dùng quy trình
[WORKFLOW](../WORKFLOW.md) và phân công trách nhiệm trong [README](README.md).

## Thứ tự thực hiện khi có thay đổi giao nhau

1. Chốt kết quả người dùng, JSON, lỗi và dữ liệu cần giữ; cập nhật hợp đồng của chức năng.
2. Backend triển khai/thay đổi contract và test; frontend có thể dùng fixture trong lúc chờ.
3. Ghép client với API thật; bỏ đường mock khỏi buổi demo tích hợp.
4. Kiểm tra luồng lưu/mở lại, lỗi, quyền sở hữu và tương tác màn hình.
5. Bàn giao bằng PR, bằng chứng và giới hạn; cập nhật trạng thái, không tự đánh dấu mọi AC đạt.

## Các điểm phải phối hợp

| Phần giao nhau | Người liên quan | Điều cần giữ |
| --- | --- | --- |
| Timer UI ↔ sessions API | Dev 1 + Dev 2 | Trạng thái, giây server tính, version và retry key |
| Recall UI ↔ backend/AI | Dev 2 + Dev 3 | Nguồn, draft/active, trích dẫn, tự đánh giá và lỗi provider |
| Task ↔ Timer/thời lượng | Chủ module task + sessions | FINISH không tự hoàn thành; estimate và thực học độc lập |
| Migration | Tất cả backend | Phiên bản chưa trùng và không sửa migration đã áp |

Mốc sprint, người thực hiện và hạn bàn giao quản lý trên board của nhóm; không dùng
lịch D1–D7 cố định của tài liệu thiết kế cũ làm thời hạn hiện tại.
