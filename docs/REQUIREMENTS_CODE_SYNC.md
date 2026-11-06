# Requirements ↔ Code — Quy trình thay đổi hai chiều

> **Trạng thái: quy trình đề xuất, chờ Scrum Team xác nhận và cấu hình tích hợp.** Jira ASP là nguồn chính cho Product Backlog và các Acceptance Criteria đã được PO duyệt. GitHub quản lý source code và PR. Tài liệu `requirements.md` trong repository là bản tham chiếu chi tiết cho Developers, không phải backlog độc lập.

## Nguồn dữ liệu và quyền quyết định
- **Jira ASP**: nguồn chính cho Epic, User Story, AC, sub-task, priority, Sprint và trạng thái; PO duyệt thay đổi hành vi sản phẩm tại đây.
- **`requirements.md`**: tài liệu tham chiếu phiên bản hóa trong repository, phản ánh các quy tắc sản phẩm đã được PO chấp thuận trên Jira. Cập nhật bằng PR sau khi Jira thay đổi; không dùng file này để tạo một Product Backlog thứ hai.
- **GitHub**: source code, branch, commit, Pull Request, CI và test. GitHub Issues cũ chỉ giữ làm lịch sử/tham khảo; không dùng làm backlog song song.
- **Code và test**: bằng chứng về hành vi đã được hiện thực và kiểm chứng, không tự trở thành quyết định sản phẩm.
- **PO**: quyết định yêu cầu/nghiệp vụ. **Developers**: quyết định cách triển khai và kiểm chứng. **Scrum Master**: hỗ trợ nhóm tuân thủ Working Agreement.

## A. Jira / requirements thay đổi → đề xuất cập nhật code
1. PO cập nhật Jira Story: lý do, AC cũ/mới, phạm vi và ảnh hưởng tới Sprint. Jira là nơi ghi nhận quyết định Product Backlog.
2. Đối chiếu `requirements.md`; xác định quy tắc nào cần thêm/sửa/bỏ để tài liệu tham chiếu khớp với Jira đã duyệt.
3. AI/Codex đọc ticket và repository hiện tại, tạo impact analysis: frontend, backend, DB migration, API contract, tests, rủi ro và thứ tự sub-task. Không giả định endpoint hoặc chức năng đã tồn tại.
4. Developers refinement, xác nhận cách chia sub-task/estimate và tạo branch/PR gắn mã Jira thật.
5. Cập nhật code và test trong PR; nếu cần, cập nhật `requirements.md` cùng PR hoặc PR tài liệu liên quan.
6. Developer khác review; chạy kiểm tra liên quan. PO review hành vi demo so với AC đã duyệt trước khi chấp thuận sản phẩm.

## B. Code thay đổi → đề xuất cập nhật requirements
1. PR mô tả hành vi người dùng trước/sau và Jira Story liên quan, không chỉ liệt kê file/hàm thay đổi.
2. Tác giả PR phân loại:
   - Không ảnh hưởng hành vi sản phẩm: nêu lý do, không cần đổi AC.
   - Có ảnh hưởng hành vi/contract: đề xuất thay đổi Jira AC và `requirements.md`.
   - Chưa rõ hoặc mâu thuẫn: gắn **Needs PO decision**, không tự chốt quy tắc.
3. PO chấp thuận hoặc từ chối thay đổi trên Jira. Nếu chấp thuận, cập nhật tài liệu tham chiếu trong PR.
4. Bổ sung test để chứng minh AC mới; review và merge theo Working Agreement. Jira hiển thị thông tin development khi tích hợp Jira–GitHub được cấu hình.

## C. Trạng thái truy vết Jira hiện tại

Các story dưới đây được đọc từ Jira ASP. Tại thời điểm kiểm tra, chúng đang ở trạng thái **To Do**. Đây là danh sách truy vết; chưa khẳng định code đã triển khai.

| Jira Story | Nội dung | Epic | Priority | Trạng thái |
| --- | --- | --- | --- | --- |
| [ASP-9](https://ai-study-planner-nhom.atlassian.net/browse/ASP-9) | US-01: Tạo mục tiêu ôn tập | ASP-5 | High | To Do |
| [ASP-10](https://ai-study-planner-nhom.atlassian.net/browse/ASP-10) | US-02: Tự thêm, sửa, xóa công việc trong mục tiêu | ASP-5 | Medium | To Do |
| [ASP-11](https://ai-study-planner-nhom.atlassian.net/browse/ASP-11) | US-03: Xem công việc theo ngày | ASP-5 | Medium | To Do |
| [ASP-12](https://ai-study-planner-nhom.atlassian.net/browse/ASP-12) | US-04: Đăng ký, đăng nhập | ASP-6 | Medium | To Do |
| [ASP-13](https://ai-study-planner-nhom.atlassian.net/browse/ASP-13) | US-05: AI đề xuất kế hoạch | ASP-7 | High | To Do |
| [ASP-14](https://ai-study-planner-nhom.atlassian.net/browse/ASP-14) | US-06: Duyệt, sửa và lưu kế hoạch AI | ASP-7 | High | To Do |
| [ASP-15](https://ai-study-planner-nhom.atlassian.net/browse/ASP-15) | US-07: Cảnh báo kế hoạch quá tải hoặc quá hạn | ASP-7 | Medium | To Do |
| [ASP-16](https://ai-study-planner-nhom.atlassian.net/browse/ASP-16) | US-08: Đánh dấu hoàn thành | ASP-8 | High | To Do |
| [ASP-17](https://ai-study-planner-nhom.atlassian.net/browse/ASP-17) | US-09: Xem tiến độ | ASP-8 | High | To Do |
| [ASP-18](https://ai-study-planner-nhom.atlassian.net/browse/ASP-18) | US-10: Dùng được khi AI lỗi | ASP-7 | Medium | To Do |
| [ASP-19](https://ai-study-planner-nhom.atlassian.net/browse/ASP-19) | US-11: Việc bị trễ và dời ngày | ASP-8 | Medium | To Do |
| [ASP-20](https://ai-study-planner-nhom.atlassian.net/browse/ASP-20) | US-12: AI xếp lại lịch khi bị trễ (mở rộng) | ASP-7 | Medium | To Do |

**Điểm cần PO xử lý trước khi chia sub-task cho ASP-9:** Jira hiện ghi AC gồm môn, ngày thi, thời gian mỗi ngày; ngày thi không ở quá khứ; mục tiêu lưu và hiển thị lại. `requirements.md` trong repo mô tả chi tiết hơn (chủ đề, ngày bắt đầu, khoảng ngày tối đa, giới hạn phút mỗi ngày và quy tắc sửa mục tiêu). PO cần quyết định phần nào là AC bắt buộc của US-01, phần nào thuộc story khác hoặc ngoài phạm vi. Không tự thêm các rule còn thiếu vào Jira.

## D. Tự động hóa: thực tế và mức độ an toàn

**Mức 1 — liên kết phát triển Jira–GitHub:**
- Cài/cấu hình GitHub for Atlassian trong Jira và chọn repository `duckymomoUWU/AI-Study-Planner`.
- Dùng Jira key thật (ví dụ `ASP-9`) trong branch, commit, PR title.
- Kiểm tra Jira hiển thị branch/commit/PR. Có thể cấu hình Automation chuyển trạng thái khi PR mở/merge tùy workflow và quyền.
- Đây là liên kết và cập nhật development status, **không** tự chuyển requirements thành code.

**Mức 2 — AI-assisted, cần cấu hình riêng:**
- Trigger khi Jira Story/AC thay đổi hoặc PR có dấu hiệu đổi hành vi.
- Agent có quyền đọc Jira + repo so sánh yêu cầu và code, tạo comment/impact analysis/draft PR với test đề xuất.
- Không tự merge, không tự thay AC, không tự đổi quy tắc sản phẩm; PO và Developers review.

**Mức 3 — tự sửa main/auto-merge: không đề xuất.** AI có thể hiểu sai ngữ nghĩa; PO và Developers cần xác nhận thay đổi ảnh hưởng tới sinh viên, dữ liệu và quyền truy cập.

## Checklist trước khi merge thay đổi ảnh hưởng sản phẩm
- [ ] Có Jira issue thật và AC đã được PO xác nhận.
- [ ] Đã xét tác động tới `requirements.md` (cập nhật hoặc ghi rõ không cần).
- [ ] Đã xét frontend, backend, schema, API contract và tương thích.
- [ ] Có test/đầu ra demo tương ứng AC; phân biệt pass thực tế và chưa chạy.
- [ ] PR được review theo Working Agreement; không có secret/dữ liệu người dùng thật.

**Giới hạn hiện tại:** Đã đọc được Jira ASP và repository GitHub. Chưa xác nhận GitHub for Atlassian đã cài/cấu hình; chưa có webhook hay agent tự chạy khi Jira/PR thay đổi. Không tuyên bố đồng bộ tự động đang hoạt động.
