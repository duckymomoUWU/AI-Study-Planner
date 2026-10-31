# Team board — 6 người

GitHub Projects là nguồn sự thật cho backlog và Sprint; Issues chứa yêu cầu, PR chứa thay đổi, docs chứa quyết định. Không cần chạy song song Jira/Trello. Tên thật và GitHub handle do nhóm điền khi kickoff; chưa mời hoặc phân quyền cho ai.

|Người|Trách nhiệm|Đóng góp thực tế|
|---|---|---|
|Bạn — Product Owner|Tối đa hóa giá trị, Product Goal và thứ tự Product Backlog|Phỏng vấn, làm rõ business rule, mời người dùng Review, quyết định phạm vi|
|1 Scrum Master|Hiệu quả Scrum Team, hỗ trợ tự quản và tháo trở ngại|Facilitate sự kiện, theo dõi hành động cải tiến; không làm người giao việc cho Developers|
|Dev 1|Developer; đầu mối UI/UX ban đầu|Form mục tiêu, trang hôm nay, accessibility|
|Dev 2|Developer; đầu mối dữ liệu/auth ban đầu|Session, ownership, schema, API|
|Dev 3|Developer; đầu mối kế hoạch/AI ban đầu|Scheduler, AI adapter, schema output|
|Dev 4|Developer; đầu mối chất lượng/vận hành ban đầu|CI, integration/E2E, deploy; vẫn làm tính năng|

Đây là điểm bắt đầu để phối hợp, không phải 4 nhóm con hoặc một QA nhận toàn bộ việc test. Mỗi story có một người phối hợp và người review khác; Developers tự chọn và đổi cặp, cùng chịu trách nhiệm Increment. PO không phải cổng ký duyệt bắt buộc để một việc đạt Definition of Done.

## Cấu hình Projects

Tên: **AI Study Planner — Product & Delivery**. Liên kết repo duckymomoUWU/AI-Study-Planner. Quyền Project: PO và SM quản trị; 4 Dev có quyền ghi; repo theo nguyên tắc tối thiểu cần thiết. Chọn private nếu chứa thông tin người dùng thử.

Fields:
- Status: Backlog → Ready → In Progress → In Review → Done.
- Priority: Must / Should / Could.
- Epic: E1 / E2 / E3 / E4 / E5 / E6.
- Target Sprint: Sprint 1 / Sprint 2 / Sprint 3 / Unscheduled (dự báo).
- Iteration: cấu hình khi nhóm thống nhất ngày và độ dài Sprint; không đồng nhất với Target Sprint.
- Assignees; Estimate (number, Developers quyết định); Blocked (Yes/No); PR liên kết bằng `Closes #...`.

Views cần tạo trong UI:
1. **Product Backlog**: table, sort Priority rồi kéo thứ tự theo PO; hiện Epic, Target Sprint, Estimate, Assignee.
2. **Current Sprint**: board group Status, filter Iteration hiện tại; không ẩn việc bị blocked.
3. **Epic roadmap**: table group Epic, hiện Target Sprint; roadmap theo Iteration khi có ngày.
4. **Bugs & impediments**: filter label bug hoặc impediment, hiện assignee.
5. **Improvements**: filter label improvement, xem hành động Retro và bằng chứng.

WIP khởi điểm: In Progress tối đa 4 story cho 4 Dev; In Review tối đa 2. Blocked là cờ, vẫn tính WIP. Nếu review đầy, hỗ trợ review trước khi lấy việc mới. Đây là quy ước thử của nhóm, không phải quy định Scrum.

Ready (quy ước refinement): có giá trị, AC kiểm được, dependencies/rủi ro rõ, đủ nhỏ để xong trong Sprint. Không dùng checklist để cản trao đổi hoặc biến thành cổng duyệt cứng.

Done: đáp ứng AC; code được người khác review; test phù hợp và CI xanh; kiểm tra ownership/validation cho luồng liên quan; không secret trong repo/log; tài liệu/cấu hình cập nhật; chạy được trên môi trường tích hợp. Việc không đạt DoD quay lại Product Backlog để xem xét, không tính là Increment. Review không phải cổng cho phép release.

Automation gợi ý: item mới → Backlog; PR merge chỉ chuyển Done khi DoD đã đạt; bật linked PR status và auto-add Issues nếu quyền gói GitHub hỗ trợ. Không tự đóng issue chỉ vì mở PR.

## Nhịp làm việc

Giả định để nhóm thảo luận: Sprint 2 tuần, tối đa một tháng theo Scrum Guide. Planning 90–120 phút; Daily Scrum 15 phút mỗi ngày làm việc cho Developers; Review 60 phút với sinh viên dùng thử; Retro 45 phút. Refinement 30–45 phút hai lần/tuần là hoạt động bổ sung, không phải sự kiện Scrum bắt buộc.

Planning trả lời Why/What/How, tạo một Sprint Goal. Developers chọn story theo công suất thực tế và chia task tối đa khoảng một ngày. Daily tập trung khả năng đạt Sprint Goal, không báo cáo cho PO/SM. Review dùng sản phẩm chạy được và phản hồi để cập nhật backlog. Retro tạo một action có owner, hạn và thước đo; không cần giả vờ tìm ra vấn đề trước khi Sprint diễn ra.

Ví dụ action Retro (chưa phải kết quả thực tế): PR chờ review lâu → thống nhất hai khung review/ngày → owner Dev luân phiên → đo median thời gian chờ trước/sau.

## Đưa backlog lên GitHub

`scripts/setup_github.py` mặc định chỉ hiển thị kế hoạch, không tạo dữ liệu:

```bash
python scripts/setup_github.py
# Trên máy đã có quyền Issues và Projects:
gh auth status
python scripts/setup_github.py --apply
```

Script tạo/reuse Project, các trường Priority/Epic/Target Sprint và 6 epic issues + 21 story issues có AC, labels, liên kết epic và dependencies; thêm vào Project và điền ba trường. Không gán người chưa biết tên và không tạo ngày Sprint giả. Script nhận diện issue bằng marker, chạy lại không chủ động ghi đè nội dung đã sửa. Chạy tuần tự, tránh hai người import đồng thời.

Sau import, đổi các lựa chọn Status mặc định thành các cột trên, thêm Iteration/Estimate/Blocked, tạo Views và quyền thành viên trong UI. Script không thiết lập các phần UI hoặc branch protection. GitHub không có tính năng import CSV Projects trực tiếp được bảo đảm; CSV là bản bàn giao để đọc/chuyển đổi, script là đường import chính.

Bảo vệ main sau khi push: require PR, một approval, required check `quality`, chặn force-push; không tự miễn review cho AI. Kiểm tra khả năng ruleset theo gói tài khoản.

Hiện tại API GitHub bị Forbidden từ môi trường, chưa có Project/Issues online và chưa push mã nguồn. Cần cho phép api.github.com và có quyền GitHub Projects/Issues qua kết nối được hỗ trợ hoặc chạy script trên máy đã đăng nhập. Không gửi token vào chat.
