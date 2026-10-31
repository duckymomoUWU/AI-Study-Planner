# AI agent trong quy trình phát triển

Một coding agent như Codex đã đủ cho nhóm giai đoạn đầu. Chọn **vai trò và kiểm chứng** theo bước; không cần mua một sản phẩm khác cho từng việc. ChatGPT có thể hỗ trợ PO, Codex hỗ trợ repo; GitHub Copilot là lựa chọn thay thế trong IDE, không bắt buộc dùng cả hai. Playwright, Zod và test runner là công cụ kiểm chứng, không phải AI agent.

|Bước|Agent/công cụ đề xuất|Đầu vào → đầu ra|Người chịu trách nhiệm / kiểm chứng|
|---|---|---|---|
|Discovery/business|ChatGPT vai BA|Ghi chú phỏng vấn đã ẩn danh → giả thuyết, câu hỏi tiếp theo|PO đối chiếu bằng chứng, không chấp nhận số liệu bịa|
|Refinement|ChatGPT hoặc Codex vai BA|Vision + business rules → story, Given/When/Then, edge case|PO làm rõ giá trị; Dev thảo luận feasibility/estimate|
|UX|ChatGPT/Codex hỗ trợ flow và prototype|Story + ràng buộc → form, empty/error/loading states|Dev + 5 người dùng kiểm tra; AI không thay usability test|
|Architecture|Codex vai reviewer kiến trúc|ADR + constraints → tradeoff, schema/API, threat model|Dev chọn thiết kế, review auth/ownership|
|Implementation|Codex trong repo hoặc Copilot IDE|Một story + AC + file scope → patch nhỏ và test|Người viết hiểu diff, chạy test; không cho tự merge|
|Testing|Codex vai test engineer + node:test/Playwright|AC + failure modes → integration/E2E tests|Reviewer xác nhận test fail đúng khi hành vi hỏng, không sửa assertion để xanh|
|AI evaluation|Codex soạn fixture + PO đánh giá nội dung|Schema + rubric → malformed/overload/injection cases|Test xác định chặn lịch sai, người thật chấm tính hữu ích|
|Code review|Codex review diff + một Dev khác|Diff + spec → lỗi theo mức độ và test tái hiện|Human review bắt buộc; AI không tự chấp thuận output của mình|
|Release/ops|Codex đọc CI/log đã loại secret|Runbook + log → chẩn đoán, bước rollback|Dev xác minh môi trường/backup, chỉ triển khai theo quyền được giao|
|Review/Retro|ChatGPT tổng hợp ghi chú|Kết quả thực + phản hồi → một action đo được|Cả Scrum Team quyết định; không tạo biên bản giả|

Prompt mẫu coding: “Thực hiện USxx theo docs/backlog.md và business.md. Trước sửa, đọc code/AGENTS.md. Nêu ràng buộc và test sẽ chứng minh AC. Chỉ sửa trong phạm vi story; không đổi test để che lỗi; không dùng secret hoặc dữ liệu thật. Báo files, checks đã chạy, lỗi và phần chưa làm.”

Prompt mẫu testing: “Từ AC USxx, xác định lỗi có tác động người dùng: truy cập chéo user, ngày thi, ngân sách, retry lưu. Viết test có thể fail vì lỗi đó. Chạy và báo số pass/fail/skipped; không khẳng định test chưa chạy.”

Prompt mẫu review: “Review diff so với AC, ưu tiên mất dữ liệu, quyền truy cập, lịch sai, retry. Mỗi finding có file/line, trigger và hậu quả; phân biệt lỗi chứng minh được với giả thuyết.”

Không gửi passwords, tokens, tài liệu riêng của sinh viên hoặc toàn DB cho agent. Người phụ trách story chịu trách nhiệm code AI tạo. Dùng nhánh/PR nhỏ; không giao 4 agent sửa cùng file. Agent trong sản phẩm (provider AI đề xuất lịch) là thành phần khác với agent hỗ trợ nhóm viết phần mềm.
