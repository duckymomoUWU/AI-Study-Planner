# Quỹ thời gian chung mỗi ngày

Quy tắc hiện hành: [quỹ chung và mức mong muốn](study-time-preferences.md). Quỹ chung là giới hạn cứng; phút/ngày từng mục tiêu là mức mong muốn cho AI, có xác nhận riêng khi bản nháp vượt mức. Các mô tả giới hạn riêng bên dưới là hợp đồng trước thay đổi này.

Phạm vi PO chấp nhận 2026-11-11: tổng phút của tất cả mục tiêu không vượt thời gian sinh viên dành cho ngày đó. Giới hạn riêng từng mục tiêu vẫn áp dụng. Quỹ chung là tổng phút theo ngày, không phải khung giờ Google Calendar.

## Cấu hình và chuyển tiếp dữ liệu

Người dùng tự nhập `defaultMinutes` từ 0 đến 1440 và `dailyMinutes` ghi đè theo ngày ISO (tối đa 366 ngày, mỗi giá trị 0..1440). Ngày không ghi đè theo mặc định; 0 là ngày nghỉ. UI nằm ở Mục tiêu và Lịch học; hiển thị tổng dùng/còn lại không phụ thuộc bộ lọc môn.

V6 thêm các trường vào app_users, không đoán quỹ của người dùng hiện có và không di chuyển/xóa lịch cũ. Tài khoản chưa cấu hình nhận 422 STUDY_BUDGET_REQUIRED khi thêm việc, đổi ngày/phút của việc hoặc sinh lịch AI. Vẫn xem, hoàn thành/mở lại, đổi tên và xóa việc được. Có thể tạo mục tiêu, nhưng cần cấu hình quỹ để bước AI thành công. Không cho xóa cấu hình để bỏ qua ràng buộc.

Khi lưu cấu hình, không được đặt quỹ dưới tổng việc đã lưu từ hôm nay trở đi, kể cả việc hoàn thành; trả ngày và số phút vượt để người dùng chủ động chỉnh. Ngày đã qua giữ nguyên lịch sử và không chặn giảm mức mặc định cho tương lai. Với lịch cũ dày hơn quỹ mong muốn, trước hết đặt mức đủ chứa lịch hiện có, dời/xóa việc chưa học rồi giảm quỹ. Không tự giảm ước lượng hoặc xóa việc hoàn thành.

## API

Mọi endpoint dùng session, CSRF khi ghi, envelope chuẩn và owner từ principal.

- `GET /api/v1/study-budget?from=2026-11-11&to=2026-11-17`: mặc định hôm nay và 6 ngày sau, tối đa 90 ngày (kể cả hai đầu). Trả `{data:{defaultMinutes,dailyMinutes,version,days:[{date,budgetMinutes,usedMinutes,remainingMinutes}]}}`. Chưa cấu hình: defaultMinutes/budgetMinutes/remainingMinutes null; usedMinutes vẫn là dữ liệu thật.
- `PUT /api/v1/study-budget`: thay toàn bộ cấu hình `{defaultMinutes:120,dailyMinutes:{"2026-11-12":0},expectedVersion:0}`. Không chấp nhận owner/field lạ. Trả cấu hình đã lưu và usage của tuần mặc định. UI tải lại usage của tuần đang xem.
- 400 VALIDATION_ERROR cho giá trị/ngày/phạm vi không hợp lệ; 409 STUDY_BUDGET_VERSION_CONFLICT cho bản cấu hình cũ; 422 SHARED_DAILY_BUDGET_EXCEEDED cho ngày vượt tổng. `error.context` có scheduledDate, requiredMinutes, budgetMinutes, excessMinutes.

## Lập lịch và đồng thời

AI vẫn ước lượng công việc trước. Bộ xếp ngày dùng phần nhỏ hơn giữa quỹ riêng của mục tiêu và quỹ chung trừ các mục tiêu khác; sau đó trừ việc đã có trong chính mục tiêu. Không trừ hai lần hoặc loại bỏ việc chưa xếp. Draft giữ snapshot khả dụng; khi mục tiêu khác/quỹ chung đổi sau khi sinh, xác nhận kiểm tra lại dữ liệu hiện tại và có thể từ chối.

Manual create/update date/minutes, confirm và reschedule khóa hàng app_users trước hàng goal. Cập nhật cấu hình khóa cùng owner trước khi kiểm tra usage. Nhờ đó hai mục tiêu cùng ghi hoặc một thao tác giảm quỹ chạy đồng thời không thể cùng commit vượt quỹ. Confirm vẫn nguyên tử/idempotent; replay receipt không thêm task hoặc trừ phút lần nữa. Dời/xóa việc giải phóng phút của ngày cũ; hoàn thành/mở lại không giải phóng quỹ.

Mốc ngày chung theo Asia/Ho_Chi_Minh như lịch ứng dụng. Chưa bổ sung tự cân lại lịch, phân bổ ưu tiên liên môn hoặc đồng bộ sự kiện Google sau sửa/xóa.

Kiểm chứng và giới hạn: [shared-budget delivery](../plans/completed/shared-budget-2026-11-11.md).
