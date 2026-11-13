## Vấn đề và kết quả

Story: ___ / Người làm: ___ / Reviewer: ___

Khi người dùng ___, trước đây ___; sau PR ___.

## Phạm vi và contract

- AC đã làm: ___
- Route/API/migration mới: ___
- Contract/fixture đã cập nhật: ___
- AC ngoài scope hoặc chưa đạt: ___ (lý do và ảnh hưởng)

## Demo

Tài khoản/nguồn **giả lập**, không ghi password thật: ___

1. Mở ___
2. Thao tác ___
3. Quan sát ___

Ảnh desktop/mobile hoặc video: ___

## Xác minh thực tế

| Lệnh/cách thử | Pass/fail/skip hoặc kết quả | Loại bằng chứng |
| --- | --- | --- |
| ___ | ___ | Unit / mock client / MockMvc / PostgreSQL / browser / provider thật |

- [ ] Ownership + session + CSRF đã kiểm tra.
- [ ] Reload/retry/duplicate request đã kiểm tra khi có state lưu.
- [ ] Không secret, fixture cá nhân, mock production hoặc migration bị sửa lịch sử.
- [ ] CI liên quan xanh; không coi test skipped là passed.
- [ ] Theme/mobile/keyboard đạt.

## Rủi ro và khôi phục

Chưa xác minh: ___

Dữ liệu ảnh hưởng và cách triển khai an toàn: ___

Không đề xuất xóa DB hoặc reset dữ liệu người dùng để khắc phục lỗi migration.
