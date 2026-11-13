# Quỹ chung và mức mong muốn theo môn

## Quy tắc sản phẩm

Quỹ thời gian chung là giới hạn cứng của tổng công việc trong ngày, bao gồm mọi
mục tiêu và cả việc đã hoàn thành. Phút/ngày của mục tiêu là mức mong muốn để AI
ưu tiên phân bổ, không phải giới hạn cho thao tác thủ công.

- Thêm, tăng phút hoặc dời việc thủ công được vượt mức mong muốn nếu quỹ chung còn đủ.
- Giảm mức mong muốn không xóa, dời hoặc làm mất công việc đã lưu.
- AI thử bố trí trong mức mong muốn trước; phần chưa phù hợp có thể được đề xuất
  vào quỹ chung còn dư. Ước lượng không bị giảm để ép vừa lịch; không tự chia nhỏ việc.
- Ngày có mức AI riêng bằng 0 vẫn được bỏ qua khi AI xếp lịch. Thao tác thủ công
  chỉ chịu quỹ chung và quy tắc ngày; quỹ chung 0 là ngày không thể thêm thời lượng.
- Bản nháp nêu ngày, tổng phút môn (kể cả việc đã có), mức mong muốn, số phút vượt
  và tổng phút các môn/quỹ chung theo snapshot.
- Người dùng tích chọn đồng ý phần vượt trước khi xác nhận. Đổi việc/ngày/phút,
  sinh hoặc xếp lại bản nháp yêu cầu chọn lại; không tự thay mức mong muốn.
- Nếu không đồng ý, chọn **Giữ mức mong muốn và xếp lại**. Hệ thống dùng công việc
  và ước lượng đã lưu trong bản nháp, xếp sang ngày khác trước ngày thi. Phần không
  có chỗ được giữ nguyên trong việc chưa xếp và có thể lưu phần đã xếp như hiện tại.
- Không thay lịch đã xác nhận hoặc tự tối ưu lại công việc của các môn khác.

Ví dụ: quỹ chung 200, mức Toán 120, việc Toán 180. Thêm tay được nếu tổng ngày còn
đủ. Với bản nháp AI, phải báo vượt 60 và có đồng ý; nếu ngày khác không còn đủ
120 để chứa nguyên việc 180 thì giữ việc chưa xếp khi người dùng chọn giữ mức.

## Hợp đồng API

Không đổi tên `minutesPerDay` để tương thích dữ liệu cũ. Nó lưu mức mong muốn
15–480; `planningScope.dailyMinutes` lưu mức riêng ngày 0–480. Không có migration.

Trong snapshot draft scope:

- `preferredDailyMinutes`: mức mong muốn theo ngày.
- `dailyMinutes`: khả dụng cứng cho môn sau khi trừ việc các môn khác; còn phải trừ việc cũ của chính môn.
- `existingGoalMinutes`, `otherGoalMinutes`, `sharedDailyMinutes`: snapshot để giải thích bản nháp.
- `respectPreferences`: khi true, giới hạn đề xuất theo mức người dùng muốn giữ.
- `summary.preferenceExcessDays`: `{date,preferredMinutes,plannedMinutes,excessMinutes}`.

POST confirm nhận thêm `allowPreferenceExceedance` boolean, mặc định false.
Server tính lại mức vượt từ công việc thực tế và cấu hình hiện tại dưới khóa owner/goal.
Thiếu đồng ý trả 422 `PREFERENCE_EXCEEDANCE_REQUIRES_CONFIRMATION` cùng danh sách
ngày vượt; không ghi task/receipt hoặc tăng version. Đồng ý không bỏ qua quỹ chung,
ngày thi, quyền sở hữu, version, expiry hay các kiểm tra khác. Quyết định đồng ý
nằm trong fingerprint idempotency; đổi quyết định bằng cùng key bị từ chối.

POST reschedule nhận body tùy chọn `{respectPreferences:true}` để xếp lại theo
mức mong muốn; không body giữ chế độ hiện có. Không gọi AI lại. Mode true được
lưu trong draft và kiểm tra lại khi confirm, không thể vượt nó bằng cờ đồng ý.

Quỹ chung vẫn được kiểm tra lại tại thời điểm lưu. Bản nháp là snapshot, không phải
đặt chỗ; dữ liệu thay đổi ở môn khác hoặc quỹ chung có thể khiến xác nhận bị từ chối.
Quy tắc này thay thế các mô tả cũ coi phút/ngày từng môn là giới hạn cứng.
