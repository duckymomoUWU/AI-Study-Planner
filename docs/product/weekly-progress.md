# Tiến độ theo tuần

`#weekly` (alias `#progress`) hiển thị số việc hoàn thành/tổng việc của tuần và từng ngày.
Đây là tiến độ công việc đã xếp, không phải giờ thực học hoặc mức hiểu bài.

## Quy tắc và API

GET `/api/v1/progress/weekly?date=YYYY-MM-DD`, cần đăng nhập. Date là ngày bất kỳ trong
tuần; bỏ trống dùng ngày hiện tại theo Clock/timezone mặc định của app. Tuần từ thứ Hai
đến Chủ Nhật, gồm cả hai đầu. Task thuộc tuần chứa scheduled_date của nó, không phải ngày đánh dấu xong.

```json
{"data":{"weekStart":"2026-11-09","weekEnd":"2026-11-15","totalTasks":0,"completedTasks":0,"remainingTasks":0,"completionRate":0,"days":[]}}
```

Ví dụ rút gọn: response thật luôn có đủ 7 phần tử days `{date,totalTasks,completedTasks}`,
kể cả ngày không có việc. completionRate = completed/total, bằng 0 khi total=0;
UI hiển thị phần trăm nguyên. Tính trên tất cả mục tiêu của principal; không đếm draft.
Không ghi dữ liệu hoặc tăng goal version khi đọc.

400 VALIDATION_ERROR nếu date sai hoặc ngoài năm 2000–2100; 401 nếu chưa đăng nhập.
Không trả dữ liệu người khác và không để lỗi bất ngờ lộ chi tiết SQL.

## Giao diện và phạm vi

Chọn tuần trước/sau/hiện tại; loading, lỗi/retry, login và tuần trống 0/0 riêng.
Hoàn thành/mở lại ở Lịch học làm số đếm mới thay đổi khi tải lại trang tiến độ.
Mục tiêu có [progress riêng](goal-progress.md); quỹ chung và thời gian Timer là các số khác,
không lấy phần trăm task để suy ra ngân sách hoặc số giờ đã học.

Lọc tiến độ tuần theo môn, thống kê dài hạn và đánh giá kiến thức chưa thuộc trang này.
Code: WeeklyProgressController/Service/JdbcRepository và WeeklyProgressPage.
[Bằng chứng](../plans/completed/weekly-progress-2026-11-12.md), [status](../status.md).
