---
name: study-planner-generate-subtasks
description: Từ một user story AI Study Planner đã có AC, đề xuất sub-task có phụ thuộc và bằng chứng hoàn thành; không tự tạo issue Jira.
---

# Generate sub-tasks

Đọc [AGENTS.md](../../../AGENTS.md), [requirements.md](../../../requirements.md), [Working Agreement](../../../docs/WORKING_AGREEMENT.md), [hướng dẫn Generate Sub-tasks](../../../docs/GENERATE_SUBTASKS.md) và [harness](../../harness/README.md). Đọc user story Jira được **người dùng cung cấp hoặc connector cho phép truy cập**, so với code hiện có. Dùng skill này độc lập, không tự gọi skill khác.

1. Tách dữ liệu đã xác nhận (story/AC) khỏi giả định, câu hỏi mở và kỹ thuật đề xuất.
2. Dừng tại điểm cần PO quyết định nếu thiếu quy tắc nghiệp vụ; nêu chính xác câu hỏi cần chốt, không bịa.
3. Tạo tối thiểu các lát cắt cần để demo AC; xét giao diện, API, DB, validation, test khi thực sự liên quan. Không buộc mỗi story có đủ 4 lớp.
4. Mỗi sub-task ghi Title, outcome, parent Jira key **thật**, AC được bao phủ, phụ thuộc, phạm vi repo dự kiến và cách kiểm chứng cụ thể.
5. Developers chọn cách chia, ước lượng và nhận việc. AI không tự phân công, tự chuyển trạng thái hay tự đăng Jira khi chưa được yêu cầu/quyền xác nhận.
6. Không coi code stub/health endpoint là API nghiệp vụ; không giả định có backend tests hoặc end-to-end hiện hữu.

## Đầu ra
- Bảng sub-task theo thứ tự phụ thuộc, độ phủ AC, kết quả kiểm chứng.
- Danh sách câu hỏi cần PO trả lời và rủi ro.
- Kết luận rõ **draft để Developers/PO review**, chưa tạo Jira ticket nếu chưa thao tác thật.
