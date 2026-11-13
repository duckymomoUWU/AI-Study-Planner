# Hoàn thành và mở lại công việc

Tại lịch tuần/ngày, gửi trạng thái đích bằng PATCH
`/api/v1/goals/{goalId}/tasks/{taskId}` hoặc `/api/v1/tasks/{taskId}`:

```json
{"completed":true}
```

`true` hoàn thành, `false` mở lại; không phải toggle. Request lặp cùng giá trị không
đảo ngược hoặc tăng goal version nếu không thay dữ liệu. Thay đổi thật tăng version
cùng transaction; goal PATCH dùng version cũ phải tải lại.

Cần session và CSRF, task phải thuộc principal và đúng parent goal nếu dùng nested
route. completed phải boolean, không nhận null/chuỗi/số. Task PATCH hiện cũng hỗ trợ
sửa title/ngày/phút theo [task CRUD](integrated-tasks-and-plans.md), không chỉ completed.

Response là Task đã lưu gồm id, goalId, title, scheduledDate, estimatedMinutes,
completed, source, createdAt và metadata chủ đề nếu có. Nguồn do server đặt.

Đánh dấu xong không giải phóng quỹ chung, không tự sửa thời lượng hoặc xóa task khỏi
lịch. Tiến độ tính lại từ task đã lưu, không đếm draft. Thời gian Timer là số độc lập;
hoàn thành/mở lại không xóa hoặc tạo lại phiên học.

Sau Timer FINISH, chỉ ghi completed khi người học xác nhận; xem
[study sessions](study-sessions.md). Xóa task/goal khác với đánh dấu xong và có hậu quả
với Calendar theo [contract](google-calendar.md).

Test TaskController/Service/PostgreSQL và client tasks/sessionCompletion.
[Bằng chứng lịch sử](../archive/ASP-9-verification.md); [kết quả hiện tại](../status.md).
