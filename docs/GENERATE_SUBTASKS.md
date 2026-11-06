# Generate Sub-tasks — Hướng dẫn PO và Developers

> Quy trình đề xuất. **AI chỉ tạo bản nháp; Developers review và tự lập kế hoạch thực hiện.** Không tự tạo issue Jira nếu chưa được yêu cầu.

## Trước khi sinh sub-task
1. Mở User Story trên **Jira ASP** và lấy parent key thật.
2. Đọc AC trên Jira và đối chiếu `requirements.md` (tài liệu tham chiếu trong repo); Jira là nguồn chính cho Product Backlog và AC đã duyệt.
3. Kiểm tra code hiện tại trước khi đề xuất phạm vi kỹ thuật.
4. Nếu Jira và `requirements.md` có mâu thuẫn mới hoặc thiếu quyết định nghiệp vụ, dừng ở phần bị ảnh hưởng và hỏi PO; không tạo sub-task dựa trên giả định.
5. AI đề xuất lát cắt công việc và cách kiểm chứng; Developers review, điều chỉnh phụ thuộc/estimate và tự thống nhất kế hoạch. Không bắt buộc số lượng sub-task cố định.

## Prompt để dùng với Codex / AI

```text
Bạn đang hỗ trợ Developers của AI Study Planner.
Đọc AGENTS.md, requirements.md, docs/WORKING_AGREEMENT.md,
docs/GENERATE_SUBTASKS.md và Jira Story [MÃ JIRA THẬT] được cung cấp.
Jira là nguồn chính cho User Story và Acceptance Criteria đã được duyệt.
Kiểm tra code repo hiện tại trước khi chia việc.
Hãy đề xuất các sub-task triển khai có thứ tự phụ thuộc, không tự đăng Jira.

Với mỗi sub-task, trả về:
- Title: ngắn, bắt đầu bằng kết quả cần có;
- Description: mục đích và việc cụ thể;
- Parent: mã story Jira thật;
- Phạm vi tệp / module dự kiến (nếu đã kiểm chứng);
- Phụ thuộc;
- AC nào được chứng minh;
- Kiểm chứng / cách demo có thể quan sát;
- Câu hỏi chờ PO (nếu có).

Không bịa mã Jira, người thực hiện, ước lượng, test đã chạy, endpoint đã tồn tại,
hoặc quyết định sản phẩm. Tách phần đã chốt khỏi đề xuất và câu hỏi mở.
Nếu Jira và requirements.md có mâu thuẫn mới, nêu rõ phần lệch và chờ PO quyết định.
```

## Ví dụ đang có trên Jira — ASP-9: Quản lý mục tiêu ôn tập

[ASP-9 — US-01: Tạo, xem, sửa và xóa mục tiêu ôn tập](https://ai-study-planner-nhom.atlassian.net/browse/ASP-9) thuộc Epic ASP-5, Priority High, trạng thái To Do. PO đã chốt đầy đủ quy tắc nghiệp vụ trên Jira; story hiện chưa có sub-task.

**Phạm vi đã chốt trên Jira:**
- Tạo/xem/sửa/xóa mục tiêu với môn học, chủ đề, ngày bắt đầu, ngày thi và số phút học mỗi ngày.
- Môn học/chủ đề không rỗng sau trim; ngày thi không ở quá khứ và phải sau ngày bắt đầu; khoảng ngày tối đa 90 ngày; ngân sách 15–480 phút/ngày.
- Server validate và kiểm tra ownership; dữ liệu được lưu và hiển thị lại sau khi tải trang.
- Khi sửa mục tiêu, kiểm tra các công việc đã có để không vượt ngày thi/ngân sách mới; từ chối lưu và thông báo nếu vi phạm.
- Xóa cần xác nhận; sau đó xóa mục tiêu cùng công việc và bản nháp kế hoạch liên quan.
- Không xếp công việc vào ngày thi/sau ngày thi; tổng thời lượng trong ngày không vượt ngân sách của mục tiêu; báo số phút thiếu nếu không thể xếp vừa.
- MVP tính ngân sách riêng theo từng mục tiêu, chưa tối ưu đồng thời nhiều môn.

**Các sub-task đã được tạo trên Jira ngày 06/11/2026 dưới parent ASP-9.** Chúng là kế hoạch triển khai (trạng thái To Do), **không phải chức năng đã làm xong**. PO chưa gán người thực hiện, estimate hoặc tuyên bố đã kiểm thử.

| Thứ tự | Jira sub-task | Kết quả / kiểm chứng |
| --- | --- | --- |
| 1 | [ASP-22 — Rà soát schema Goal và API contract](https://ai-study-planner-nhom.atlassian.net/browse/ASP-22) | Đối chiếu `study_goals`, `study_tasks`, `plan_drafts`, xác định API và các tình huống lỗi |
| 2 | [ASP-23 — Backend: tạo và xem mục tiêu theo tài khoản](https://ai-study-planner-nhom.atlassian.net/browse/ASP-23) | API tạo/list/detail, validate trường bắt buộc và ownership |
| 3 | [ASP-24 — Backend: sửa mục tiêu và bảo vệ giới hạn lịch](https://ai-study-planner-nhom.atlassian.net/browse/ASP-24) | Không cho sửa khiến công việc hiện có vượt ngày thi hoặc ngân sách |
| 4 | [ASP-25 — Frontend: giao diện quản lý mục tiêu](https://ai-study-planner-nhom.atlassian.net/browse/ASP-25) | Form tạo/xem/sửa, hiển thị validation và lỗi server |
| 5 | [ASP-26 — Xóa mục tiêu có xác nhận](https://ai-study-planner-nhom.atlassian.net/browse/ASP-26) | Kiểm tra xác nhận và xóa liên đới task/draft, giữ quyền sở hữu |
| 6 | [ASP-27 — Kiểm thử tích hợp, bảo mật và demo](https://ai-study-planner-nhom.atlassian.net/browse/ASP-27) | Truy vết AC → test → bằng chứng; test biên, ownership, DB và UI |

**Phụ thuộc:** đăng nhập/ownership ở ASP-12. ASP-10, ASP-11, ASP-13 và ASP-14 phải tôn trọng giới hạn ngày thi/ngân sách. Schema trong `V1__initial_schema.sql` đã có các bảng Goal/Task/Draft và cascade; business API và auth chưa được xác nhận là đã triển khai, vì vậy các sub-task yêu cầu kiểm tra bằng test thật.

## Theo dõi sau khi đã tạo trên Jira
- Dùng mã thật ASP-22…ASP-27 trong branch/commit/PR để liên kết với đúng sub-task, sau khi cấu hình GitHub for Atlassian.
- Chỉ đánh dấu In Progress khi có triển khai thực tế; chỉ đánh dấu Done khi có bằng chứng đạt AC và DoD.
- Developers có thể điều chỉnh cách triển khai, estimate và nhận việc mà không cần tạo trùng các sub-task.
- Không tạo bản duplicate trên GitHub Issues; Jira là nguồn chính cho Product Backlog.

## Không nên làm
- Không sinh task chỉ theo từng file hoặc từng thành viên mà bỏ qua kết quả sử dụng được.
- Không coi “đã tạo sub-task” là “đã triển khai”.
- Không tự giả định React/Spring Boot đã có auth, CRUD hay AI endpoint. Kiểm chứng repo trước.
