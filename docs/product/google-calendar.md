# Google Calendar — xuất lịch một chiều

PO chấp thuận 2026-11-11. Entry: Lịch học → Đưa lên Google Calendar.

## Hành vi

- Kết nối Google riêng cho từng tài khoản app qua authorization code + PKCE; state một lần, ràng buộc user/session, hết hạn sau 10 phút. Không dùng key Gemini để truy cập Google Calendar.
- Chọn lịch nhận có quyền ghi và các lịch cá nhân cần tránh. Lịch nhận luôn được thêm vào danh sách kiểm tra, tối đa 50 lịch. Lỗi đọc bất kỳ lịch nào chặn xếp/xuất; không giả định lịch đó rảnh.
- Giờ học cấu hình theo từng thứ; giờ ăn/ngủ/nghỉ có thể lặp theo thứ và qua đêm. Mặc định 08:00–22:00, ngủ 23:00–07:00; bữa ăn 07:30–08:00, 12:00–13:00, 18:00–19:00; nghỉ 10 phút giữa các sự kiện. Người dùng chỉnh trước khi dùng.
- Xem trước chỉ đọc Google. Áp dụng cho công việc chưa hoàn thành của tuần đang xem (có thể lọc mục tiêu), từ hôm nay trở đi, trước ngày thi. Giữ nguyên ngày và thời lượng, không tự chia nhỏ. Tối đa 100 công việc/lần, tuần trong 90 ngày tới.
- Bản xem trước lưu 15 phút, thuộc riêng chủ tài khoản. Không xuất khi có buổi thiếu giờ. Khi xác nhận, kiểm tra lại cấu hình, công việc và giờ bận ngay trước mỗi lần ghi. Nếu công việc hoàn thành/thay đổi/xóa thì không ghi nó.
- Thực tế Google không cung cấp thao tác free/busy + insert nguyên tử: ứng dụng khác vẫn có thể tạo sự kiện sau lần kiểm tra. Không hứa không trùng tuyệt đối trong trường hợp thay đổi đồng thời ngoài app.
- Mỗi buổi có event ID tất định theo owner/task. Outbox PENDING ghi bền trước lệnh gửi Google và CONFIRMED ghi riêng sau khi xác minh ID, owner/task marker và giờ. Retry sau timeout tìm lại sự kiện này, không tự tạo bản sao hoặc chuyển sang lịch khác. Lưu từng kết quả thành công khi lô có lỗi; UI hiển thị từng kết quả, cho thử lại cùng bản xem trước.
- Receipt và giờ đã giữ tồn tại trong khi chờ xóa; được giải phóng sau khi xác nhận Google đã xóa sự kiện. PO đã duyệt cập nhật/xóa một chiều từ app như mục bên dưới. Không nhập thay đổi ngược từ Google.
- Ngắt kết nối luôn xóa token cục bộ và cố gắng thu hồi grant ở Google; nếu Google không truy cập được, có thể thu hồi quyền thủ công trong tài khoản Google. Không xóa sự kiện đã xuất.

## API

Busy-read feedback follow-up (2026-11-11): CALENDAR_BUSY_UNAVAILABLE includes owner-scoped error.context.failedCalendarIds. Client preserves context, expands the selected-calendar list and names/highlights failed calendars. Read failure is not proof OAuth failed; the connected state may remain true. User must restore access or explicitly deselect unneeded calendars. If the failed calendar is the export target, deselecting it from the busy list does not bypass the always-checked target; select a usable target or reconnect. Do not silently omit any failed calendar or infer free time. Specific provider failure behind the user's screenshot was not captured; this change improves diagnosis without guessing which calendar is at fault.

Tất cả endpoint yêu cầu session app, owner lấy từ principal; thao tác ghi cần CSRF.

| Route | Kết quả |
| --- | --- |
| GET `/api/v1/calendar/status` | 200 `{data:{configured,connected,settings}}`, không trả token |
| GET `/api/v1/calendar/calendars` | 200 `{data:[{id,name,writable,primary}]}`, có xử lý phân trang Google |
| POST `/api/v1/calendar/connection` | 200 `{data:{url}}`, bắt đầu OAuth |
| GET `/api/v1/calendar/oauth/callback` | Xác minh state + PKCE, đổi code ở backend rồi 303 về APP_URL |
| DELETE `/api/v1/calendar/connection` | 204, ngắt kết nối; không xóa sự kiện |
| PATCH `/api/v1/calendar/settings` | 200 settings; gửi đủ snapshot, lock theo owner |
| POST `/api/v1/calendar/previews` | 201 `{data:Preview}`; body `{from:"YYYY-MM-DD",goalId?:UUID}` |
| POST `/api/v1/calendar/previews/{id}/confirm` | 200 `{data:{results,confirmed,failed}}`; retry cùng id |
| GET `/api/v1/calendar/sync` | 200 `{data:{pending,blocked,synced,deleted,items}}`; tối đa 100 việc gần đây, ưu tiên chưa xử lý, chỉ owner |
| POST `/api/v1/calendar/sync/retry` | 200 summary; đưa RETRY/BLOCKED của owner về hàng chờ, không tuyên bố đã ghi Google |

Preview có `id/from/to/settings/slots/alreadyExported/expiresAt`. Mỗi slot có snapshot task và start/end (UTC Instant); thiếu chỗ là start/end null kèm reason. Giờ hiển thị theo timezone. Settings có `timezone`, đủ 7 `days[{weekday,enabled,start,end}]`, `blocks[{label,start,end,weekdays}]`, `breakMinutes`, `busyCalendarIds`, `targetCalendarId`.

## Đồng bộ sửa/xóa từ app

PO duyệt 2026-11-11. V7 tạo calendar_sync_jobs và trigger transactional trên task update/delete, goal rename. Chỉ việc đã có calendar_exports mới phát sinh intent; không tự xuất việc mới. Xóa goal cascade giữ tombstone với owner/task/calendar/event ID sau khi task không còn tồn tại. Đánh dấu hoàn thành/mở lại không đổi Google. Thông báo xác nhận xóa trong UI nói rõ sự kiện đã xuất cũng sẽ bị xóa khi đồng bộ.

Worker tự chạy sau khi backend khởi động 30 giây, mỗi lượt tối đa 3 việc, cách lượt 15 giây; Google lỗi thì backoff tối đa 15 phút. Cần backend đang chạy và kết nối còn hiệu lực. Quá trình ghi Google không giữ transaction/khóa task; lease 5 phút và revision giữ yêu cầu mới hơn khi người dùng sửa/xóa liên tiếp. Kết quả cũ không xóa hàng chờ mới. Thử lại trong Lịch học/Mục tiêu chỉ đưa vào hàng chờ; trạng thái tự tải lại mỗi 15 giây.

Đổi tên chỉ PATCH summary; đổi ngày/phút cố giữ giờ cũ trên ngày mới nếu hợp lệ, nếu không tìm khoảng trống khác trong đúng ngày đó. Không tách hoặc đổi ngày thay người dùng. Dùng events.list cho lịch nhận để loại đúng event ID đang sửa; không trừ khoảng FreeBusy gộp vì có thể che sự kiện khác trùng giờ. Các lịch khác vẫn dùng FreeBusy; vẫn kiểm tra cả all-day, lịch bận, giờ ăn/ngủ/nghỉ và break. Đọc lại giờ bận ngay trước PATCH. Không còn chỗ/mất lịch/quyền thì BLOCKED và giữ sự kiện cũ; người dùng chỉnh rồi thử lại. Google không có thao tác check-and-reserve nguyên tử nên thay đổi từ ứng dụng khác ngay sau kiểm tra vẫn là giới hạn.

Giữ lịch đích gốc theo receipt, kể cả khi đổi lịch mặc định cho lần xuất sau. Mỗi PATCH/DELETE xác minh marker owner/task và event ID, dùng If-Match với ETag. 412/timeout được đọc lại và thử lại, không INSERT sự kiện mới. DELETE 204/404/410 được phục hồi an toàn khi lịch đích vẫn truy cập/ghi được; nếu mất quyền thì không báo xóa thành công. Nếu sự kiện UPDATE không còn trên Google thì báo cần kiểm tra, không tự tạo bản sao. Việc đã sửa trực tiếp trên Google không được nhập về app; lần sửa trong app sau có thể áp lại trường do app quản lý.

Trạng thái: PENDING/RETRY (đang chờ), BLOCKED (cần điều chỉnh), IN_SYNC (đã cập nhật), DELETED (đã xóa Google). Trạng thái app lưu thành công khác với trạng thái Google. Không tự xóa sự kiện khi ngắt kết nối; yêu cầu chưa hoàn tất được giữ, có thể tiếp tục sau kết nối lại. Không hồi tố các sửa/xóa trước V7, không backfill intent cho các receipt cũ.

Tài liệu API: [patch](https://developers.google.com/workspace/calendar/api/v3/reference/events/patch), [delete](https://developers.google.com/workspace/calendar/api/v3/reference/events/delete), [list](https://developers.google.com/workspace/calendar/api/v3/reference/events/list), [ETag/If-Match](https://developers.google.com/workspace/calendar/api/guides/version-resources). Bằng chứng: [delivery](../plans/completed/calendar-sync-2026-11-11.md).

## Cấu hình nhóm và token

Các bước Google Cloud, Test users, redirect URI, `.env` và khởi động lại được duy trì tại [setup — cấu hình Google OAuth](../../setup.md#cấu-hình-google-oauth). Không sao chép credential thật vào tài liệu hoặc Git.

Scopes: `calendar.readonly` để liệt kê lịch/đọc freeBusy; `calendar.events` để tạo và đối chiếu sự kiện. Token access/refresh lưu ở backend dưới AES-256-GCM có owner làm AAD; giữ ổn định khóa mã hóa và sao lưu cùng DB. Mất khóa phải kết nối lại.

Ngày 2026-11-11, PO đã kết nối OAuth và cung cấp ảnh/xác nhận sự kiện xuất hiện trên Google web, sau đó thấy trên điện thoại. Đây là xác minh thủ công do người dùng cung cấp, không phải agent browser E2E. Phép đối chiếu CalendarList read-only phát hiện một busy calendar đã chọn không còn trong danh sách; UI/API đã được sửa để báo rõ và giữ fail-closed. Không tự xóa lựa chọn hoặc tự tạo sự kiện thử. Bằng chứng và giới hạn đầy đủ: [handoff](../plans/completed/core-study-calendar-2026-11-11.md#validation).

Tài liệu: [OAuth](https://developers.google.com/identity/protocols/oauth2/web-server), [FreeBusy](https://developers.google.com/workspace/calendar/api/v3/reference/freebusy/query), [Events insert](https://developers.google.com/workspace/calendar/api/v3/reference/events/insert).
