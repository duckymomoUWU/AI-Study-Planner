# API dùng chung

Đây là điểm tra cứu cho frontend/backend, không duy trì một bản JSON thứ hai khác
với tài liệu sản phẩm.

| Module | Hợp đồng hiện hành |
| --- | --- |
| Quy tắc chung: route, envelope, session/CSRF, lỗi, ownership | [Backend API rules](../backend-api-rules.md) |
| Timer: active/start/actions/history, tổng thời gian theo goal/task | [Study sessions](../product/study-sessions.md) |
| Recall: materials, draft/active deck, cards/reviews/progress | [Active Recall](../product/active-recall.md) |
| Task CRUD và xác nhận bản nháp | [Tasks](../product/integrated-tasks-and-plans.md) |
| Quỹ chung và mức mong muốn | [Budget](../product/shared-study-budget.md), [preferences](../product/study-time-preferences.md) |

Khi thay contract: sửa đúng tài liệu module, backend, client và test tương ứng trong
cùng PR hoặc một kế hoạch tích hợp rõ ràng. Không suy payload từ ảnh giao diện.

## Fixtures

- [session-active.json](fixtures/session-active.json): snapshot Timer tổng hợp.
- [recall-deck.json](fixtures/recall-deck.json): bộ thẻ mẫu, không phải kết quả AI thật.
- [material-text.json](fixtures/material-text.json): nguồn text minh họa.
- [api-error.json](fixtures/api-error.json): envelope lỗi.

Fixtures chỉ phục vụ phát triển/test. Cần đối chiếu nullability, enum, đơn vị giây/phút,
version và HTTP status với API thật trước khi bàn giao. Client không nhận owner ID
hoặc số giây thực học do người dùng tự khai để ghi vào server.
