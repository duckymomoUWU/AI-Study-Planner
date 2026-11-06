# Requirements ↔ Code — Quy trình thay đổi hai chiều

> **Trạng thái: quy trình đề xuất, chưa có automation Jira/AI thực thi.** Tích hợp GitHub for Atlassian hiển thị branch, commit, PR trên Jira; **không** tự chuyển một câu requirements thành code đúng hay tự sửa requirements khi code đổi.

## Nguồn dữ liệu và quyền quyết định
- **Jira ASP**: Product Backlog, story, Acceptance Criteria, sub-task, ưu tiên, Sprint.
- **`requirements.md`**: quy tắc sản phẩm đã được PO xác nhận, lưu version trong Git.
- **Code và test**: hành vi được hiện thực và đã kiểm chứng, không tự biến thành quyết định sản phẩm.
- **PO**: quyết định yêu cầu/nghiệp vụ. **Developers**: triển khai/thiết kế/kiểm chứng. Scrum Team chốt Working Agreement và DoD.

## A. Jira / requirements thay đổi → đề xuất cập nhật code
1. PO ghi thay đổi trong Jira: mô tả cũ/mới, lý do, AC bị ảnh hưởng, phạm vi Sprint.
2. Đối chiếu `requirements.md`, xác nhận rule nào được bổ sung, sửa hoặc bỏ. Nếu đã được PO chấp thuận, cập nhật trong PR tài liệu; nếu chưa, để trạng thái **Needs PO decision**.
3. AI/Codex đọc diff requirements và code hiện có, xuất **impact analysis**: frontend, backend, DB migration, tests, rủi ro, thứ tự sub-task. Không tự giả định API đã hiện hữu.
4. Developers tạo branch/PR gắn mã Jira thật; thay đổi code và test khi yêu cầu rõ ràng. Nếu phải đổi contract, cập nhật cả hai đầu frontend/backend.
5. Người khác review và chạy kiểm tra liên quan. PO đối chiếu hành vi demo với AC; chỉ merge khi các điều kiện được đáp ứng.

## B. Code thay đổi → đề xuất cập nhật requirements
1. PR mô tả **hành vi người dùng trước/sau**, không chỉ thay tên hàm/file.
2. Tác giả PR phân loại:
   - Không ảnh hưởng hành vi: nêu lý do, không bắt buộc đổi requirements;
   - Có ảnh hưởng hành vi/contract: chỉ ra rule/AC cần cập nhật;
   - Chưa rõ hoặc mâu thuẫn: gắn **Needs PO decision**, không tự sửa tài liệu để hợp thức hóa code.
3. PO xác nhận hoặc từ chối thay đổi sản phẩm; sửa Jira/requirements trong cùng vòng review (có thể cùng PR tài liệu).
4. Bổ sung test để chứng minh AC mới; merge sau review. Jira hiển thị development status khi liên kết GitHub đã hoạt động.

## Ma trận truy vết (không phải trạng thái triển khai đã xác minh)
| Phạm vi | GitHub Issue tham khảo | Jira issue | Code / test | Trạng thái |
| --- | --- | --- | --- | --- |
| Auth và quyền sở hữu | [#1](https://github.com/duckymomoUWU/AI-Study-Planner/issues/1) | [điền] | [kiểm tra khi triển khai] | Chưa triển khai theo README |
| Mục tiêu ôn tập | [#2](https://github.com/duckymomoUWU/AI-Study-Planner/issues/2) | [điền] | [kiểm tra khi triển khai] | Chưa triển khai theo README |
| Công việc thủ công | [#3](https://github.com/duckymomoUWU/AI-Study-Planner/issues/3) | [điền] | [kiểm tra khi triển khai] | Chưa triển khai theo README |
| AI đề xuất bản nháp | [#4](https://github.com/duckymomoUWU/AI-Study-Planner/issues/4) | [điền] | [kiểm tra khi triển khai] | Chưa triển khai theo README |
| Duyệt và lưu kế hoạch | [#5](https://github.com/duckymomoUWU/AI-Study-Planner/issues/5) | [điền] | [kiểm tra khi triển khai] | Chưa triển khai theo README |
| Hoàn thành / tiến độ | [#6](https://github.com/duckymomoUWU/AI-Study-Planner/issues/6) | [điền] | [kiểm tra khi triển khai] | Chưa triển khai theo README |

## Tự động hóa: thực tế và mức độ an toàn

**Mức 1 — có thể kích hoạt bằng tích hợp chính thức:**
- Cài **GitHub for Atlassian** trong Jira, chọn repo `duckymomoUWU/AI-Study-Planner`.
- Đặt Jira key thật (ví dụ `ASP-123`) trong branch, commit, PR title.
- Jira hiển thị development info; có thể cấu hình Automation chuyển trạng thái khi PR mở/merge tùy workflow và quyền truy cập.
- Đây là **đồng bộ liên kết/trạng thái**, không phải đồng bộ nội dung yêu cầu với code.

**Mức 2 — AI-assisted, cần cài và cấu hình riêng:**
- Trigger khi Jira story/AC thay đổi hoặc khi PR thay đổi hành vi.
- Agent có quyền đọc Jira + repo so sánh yêu cầu và code, tạo **draft PR / comment** với impact analysis, test đề xuất và diff tài liệu/code cần review.
- Không tự merge, không tự thay AC, không đẩy secret ra ngoài. Chỉ chạy sau khi xác nhận quyền, API token, webhook và giới hạn automation.

**Mức 3 — auto merge / tự sửa `main`: không đề xuất.** AI có thể hiểu sai ngữ nghĩa; PO và Developers cần xác nhận thay đổi có ảnh hưởng tới sinh viên, dữ liệu và quyền truy cập.

## Checklist trước khi merge một thay đổi ảnh hưởng sản phẩm
- [ ] Có Jira issue thật và AC đã được PO xác nhận.
- [ ] Đã xét tác động tới `requirements.md` (cập nhật hoặc ghi rõ không cần).
- [ ] Đã xét frontend, backend, schema và tương thích API; không bỏ sót validator server.
- [ ] Có test/đầu ra demo tương ứng AC; phân biệt pass thực tế và chưa chạy.
- [ ] PR được review theo DoD nhóm; không có secret/ dữ liệu người dùng thật.

**Giới hạn hiện tại:** Chưa có quyền đọc Jira ASP trong kết nối này, chưa xác nhận GitHub for Atlassian đã cài, chưa có webhook/token hay agent chạy từ sự kiện Jira. Không tuyên bố tự động đồng bộ đang hoạt động.
