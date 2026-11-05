---
name: study-planner-implement-story
description: Triển khai story AI Study Planner đã rõ phạm vi và tiêu chí chấp nhận cho dev; dùng để sửa code theo một luồng có thể demo, không tự quyết định yêu cầu còn mơ hồ.
---

# Triển khai story

Đọc [AGENTS.md](../../../AGENTS.md), [requirements.md](../../../requirements.md), [setup.md](../../../setup.md) và [harness](../../harness/README.md). Chạy git status, giữ thay đổi đang làm dở. Dùng skill này độc lập, không tự gọi skill khác.

- Đối chiếu tiêu chí chấp nhận với hành vi hiện có; nếu còn lựa chọn đổi hành vi/phạm vi, hỏi PO trước phần phụ thuộc lựa chọn đó. Chỉ sửa các lớp story cần, không nhân tiện tái cấu trúc.
- Tìm mẫu thật và giải thích điểm vào cùng luồng dữ liệu cho người chưa quen. Mẫu hiện có là `frontend/src/components/BackendStatus.jsx` → `frontend/src/hooks/useBackendHealth.js` → `frontend/src/lib/api.js`, với mock fetch trong `frontend/src/lib/api.test.js`. Đây chỉ là health check.
- Backend bắt đầu ở `backend/src/main/java/com/studyplanner/StudyPlannerApplication.java`; cấu hình ở `backend/src/main/resources/application.yml`, schema ở `backend/src/main/resources/db/migration/`. Chưa có controller nghiệp vụ, service mẫu hay `backend/src/test/`; kiểm tra lại trước khi làm, không viện dẫn mẫu chưa tồn tại.
- Chia triển khai thành lát cắt nhỏ từ đầu vào đến kết quả quan sát được, có dữ liệu và bước demo cụ thể. Giữ package Java và layout Maven. Khi cần đổi DB, thêm migration thay vì sửa migration đã áp dụng.
- Khi đổi API, đối chiếu backend và nơi frontend thực sự gọi API: method, path, status, payload, lỗi và xác thực/quyền sở hữu. Kiểm tra `frontend/vite.config.js` cùng code gọi API; `/api` hiện chỉ là proxy dành cho tương lai, chưa có OpenAPI. Không mặc định mẫu health xử lý được API nghiệp vụ.
- Với nghiệp vụ trong story, giữ quy tắc requirements ở server: quyền sở hữu, validate, ngân sách/ngày và lưu sau xác nhận. Không đưa bí mật vào frontend hay log.
- Chọn và chạy kiểm tra tương ứng theo harness; đổi cả hai phía thì kiểm tra cả frontend lẫn backend và đường gọi qua proxy. Repo hiện chưa có bộ test backend: Maven verify chỉ chứng minh build khi không có test; nếu story cần test mới, nêu rõ phần bổ sung và giới hạn xác minh.

## Đầu ra

Báo thay đổi và lý do, các điểm vào chính; cách demo với dữ liệu, bước làm và kết quả mong đợi; kiểm tra đã chạy và kết quả thực tế, tách build/mock/tích hợp/thủ công; tiêu chí chưa xác minh kèm lý do. Kiểm tra diff để tránh sửa ngoài phạm vi và giữ nguyên thay đổi của team.
