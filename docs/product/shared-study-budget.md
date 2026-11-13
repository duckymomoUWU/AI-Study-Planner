# Quỹ thời gian chung mỗi ngày

Quỹ chung là giới hạn cứng của tổng phút công việc đã xếp trong ngày, gồm tất cả mục
tiêu và cả việc hoàn thành. Đây là tổng phút, không phải khung giờ Calendar. Mức mong
muốn từng môn chỉ để AI ưu tiên, theo [preferences](study-time-preferences.md).

## Cấu hình và API

- `defaultMinutes`: số nguyên 0–1440. `dailyMinutes`: ghi đè theo ngày ISO, tối đa 366 ngày,
  mỗi giá trị 0–1440. Không có ghi đè thì dùng mặc định; 0 là ngày không thêm thời lượng.
- GET `/api/v1/study-budget?from=&to=` mặc định hôm nay và 6 ngày sau, tối đa 90 ngày.
  Data gồm `defaultMinutes,dailyMinutes,version,days[{date,budgetMinutes,usedMinutes,remainingMinutes}]`.
- PUT `/api/v1/study-budget` thay toàn bộ cấu hình, body
  `{defaultMinutes,dailyMinutes,expectedVersion}`. Session/CSRF/ownership như API chung.
- 400 dữ liệu sai; 409 STUDY_BUDGET_VERSION_CONFLICT; 422 SHARED_DAILY_BUDGET_EXCEEDED.
  Context lỗi vượt gồm ngày và số phút cần/quỹ/phần vượt.

V6 thêm cấu hình vào user, không tự đoán quỹ cho tài khoản cũ. Chưa thiết lập thì
thêm thời lượng/sinh lịch nhận STUDY_BUDGET_REQUIRED. Vẫn xem, đổi tên, xóa và đánh dấu
hoàn thành được. Không cho xóa cấu hình để bỏ qua giới hạn.

Khi lưu quỹ mới, không đặt dưới tổng việc đã lưu từ hôm nay trở đi. Ngày đã qua không
chặn giảm mặc định tương lai. Nếu lịch đang dày, chủ động dời/xóa/chỉnh việc trước khi
hạ quỹ; app không tự giảm ước lượng hoặc xóa việc hoàn thành.

## Lập lịch và đồng thời

AI thử mức mong muốn trước; có thể đề xuất dùng thêm phần quỹ chung còn dư và yêu cầu
đồng ý phần vượt. Snapshot draft không thay thế việc kiểm tra quỹ hiện tại khi confirm.
Không trừ hai lần phần của chính môn hoặc bỏ việc chưa xếp để làm quỹ trông đủ.

Create/update/move task, confirm/reschedule và sửa cấu hình đều khóa owner trước goal
khi cần, kiểm tra và ghi cùng transaction. Hai môn ghi đồng thời không thể cùng vượt
quỹ. Dời/xóa việc giải phóng quỹ ngày cũ; hoàn thành/mở lại không giải phóng quỹ.
Mốc ngày chung theo Asia/Ho_Chi_Minh.

Thời gian thực học không tự thay quỹ hay estimate. Google Calendar xử lý giờ thực tế
và đồng bộ sự kiện riêng, xem [Calendar](google-calendar.md).
Bằng chứng gốc V6: [shared-budget delivery](../plans/completed/shared-budget-2026-11-11.md);
quy tắc mới: [study-time preferences](study-time-preferences.md).
