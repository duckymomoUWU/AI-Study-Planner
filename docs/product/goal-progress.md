# Tiến độ mục tiêu

GET `/api/v1/goals/{goalId}/progress`, cần đăng nhập và sở hữu goal:

```json
{"data":{"goalId":"<uuid>","totalTasks":3,"completedTasks":1,"remainingTasks":2,"completionRate":0.3333333333333333}}
```

Đếm trên toàn bộ task đã lưu, không phải trang hiện tại. remaining = total − completed;
completionRate = completed/total, bằng 0 khi chưa có task. Draft và việc chưa xếp chưa
vào lịch không được đếm; chỉ số không khẳng định đã bao phủ nội dung hoặc hiểu bài.

Một truy vấn có lọc owner lấy các số đếm; đọc không ghi hoặc tăng goal version.
Goal thiếu/ngoại tài khoản trả 404; thiếu session trả 401. Tiến độ thay đổi khi task
được hoàn thành/mở lại hoặc danh sách công việc thay đổi.

Code: ProgressController/Service/JdbcRepository. Test tương ứng gồm MockMvc,
PostgreSQL và client progress. [Bằng chứng cũ](../archive/ASP-9-verification.md),
[status](../status.md), [tiến độ tuần](weekly-progress.md).
