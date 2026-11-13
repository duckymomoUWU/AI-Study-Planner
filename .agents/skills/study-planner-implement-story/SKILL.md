---
name: study-planner-implement-story
description: Triển khai story AI Study Planner đã rõ phạm vi và tiêu chí chấp nhận cho dev; dùng để sửa code theo một luồng có thể demo, không tự quyết định yêu cầu còn mơ hồ.
---

# Triển khai story

Đọc [AGENTS.md](../../../AGENTS.md), [requirements.md](../../../requirements.md), [setup.md](../../../setup.md) và [harness](../../harness/README.md). Chạy git status, giữ thay đổi đang làm dở. Dùng skill này độc lập, không tự gọi skill khác.

- Đối chiếu tiêu chí chấp nhận với hành vi hiện có; nếu còn lựa chọn đổi hành vi/phạm vi, hỏi PO trước phần phụ thuộc lựa chọn đó. Chỉ sửa các lớp story cần, không nhân tiện tái cấu trúc.
- Tìm luồng thật trong pages/components → lib client → controller/service/repository. lib/api.js chỉ là health client; không dùng nó làm mẫu đầy đủ cho session/CSRF/nghiệp vụ.
- Backend có các package auth, goals, tasks, progress, sessions, recall và calendar. Đọc controller/service/repository cùng migration hiện tại trước khi sửa; không áp giả định của starter.
- Chia triển khai thành lát cắt nhỏ từ đầu vào đến kết quả quan sát được, có dữ liệu và bước demo cụ thể. Giữ package Java và layout Maven. Khi cần đổi DB, thêm migration thay vì sửa migration đã áp dụng.
- Khi đổi API, đối chiếu method/path/status/payload/lỗi/ownership và client thực sự gọi API đó. Vite proxy API cùng origin; hợp đồng tại docs/product/ là nguồn tham chiếu.
- Với nghiệp vụ trong story, giữ quy tắc requirements ở server: quyền sở hữu, validate, ngân sách/ngày và lưu sau xác nhận. Không đưa bí mật vào frontend hay log.
- Chọn kiểm tra theo harness: frontend có node:test, backend có unit/MockMvc và PostgreSQL opt-in. Đổi hai phía thì kiểm tra cả hai; không coi build/mock là bằng chứng tích hợp.

## Đầu ra

Báo thay đổi và lý do, các điểm vào chính; cách demo với dữ liệu, bước làm và kết quả mong đợi; kiểm tra đã chạy và kết quả thực tế, tách build/mock/tích hợp/thủ công; tiêu chí chưa xác minh kèm lý do. Kiểm tra diff để tránh sửa ngoài phạm vi và giữ nguyên thay đổi của team.
