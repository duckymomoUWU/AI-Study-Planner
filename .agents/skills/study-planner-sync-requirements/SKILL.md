---
name: study-planner-sync-requirements
description: Phân tích thay đổi yêu cầu ↔ code AI Study Planner, phát hiện lệch và đề xuất PR/test có PO review; không tự đổi quy tắc sản phẩm.
---

# Requirements ↔ code impact

Đọc [AGENTS.md](../../../AGENTS.md), [requirements.md](../../../requirements.md), [Requirements Code Sync](../../../docs/REQUIREMENTS_CODE_SYNC.md), [Working Agreement](../../../docs/WORKING_AGREEMENT.md) và [harness](../../harness/README.md). Xác định đầu vào là diff yêu cầu (Jira hoặc requirements) hay diff code/PR; chỉ đọc sự kiện từ nguồn có truy cập thật.

- **Requirements → code:** đối chiếu AC cũ/mới; tìm code, test, database và API bị ảnh hưởng. Phân biệt chưa triển khai, đã triển khai, chưa kiểm chứng. Đưa phương án triển khai, cần PO xác nhận nếu vẫn mơ hồ.
- **Code → requirements:** so diff PR với rules hiện tại, mô tả hành vi người dùng trước/sau, đề xuất tài liệu/Jira AC cần thay đổi. Không coi code là lý do đủ để tự chốt scope mới.
- Nhớ giới hạn kỹ thuật repo: frontend health sample; backend chưa có business endpoints/test; không tuyên bố E2E đã kiểm tra. Giữ các thay đổi người khác đang làm.
- Khi được giao thực hiện thay đổi **đã được PO duyệt**, làm trong branch/PR và bổ sung test tương ứng; không tự sửa main, merge, thay Jira hoặc CI nếu chưa có quyền/yêu cầu riêng.
- Nếu Jira và requirements mâu thuẫn, nêu rõ hai phía và trả lại PO quyết định.

## Đầu ra
1. Requirement/AC và nguồn có thể truy vết (Jira key thật, file/version/PR).
2. Impact matrix: rule → code path → test → hành động (đổi/giữ/đang thiếu).
3. Phần cần PO quyết định; PR/diff đề xuất chỉ khi đã có đủ authority.
4. Kiểm tra đã chạy và kết quả thực tế; giới hạn chưa xác minh.
