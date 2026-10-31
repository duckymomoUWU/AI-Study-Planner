# Gói bàn giao tuần tới: User Stories + khởi tạo công nghệ

Cửa sổ lập kế hoạch: 02–08/11/2026 (giờ Việt Nam); chưa biết ngày nghiệm thu cụ thể. Không hứa hoàn thành toàn bộ MVP trong tuần này.

|Mốc tương đối|Việc|Người phối hợp|Bằng chứng hoàn tất|
|---|---|---|---|
|Ngày 1|Kickoff, đọc vision, thống nhất phạm vi và business rules|PO + cả nhóm|Product Goal, phạm vi ngoài MVP, 6 người và quyền repo|
|Ngày 1–2|Phỏng vấn 5 sinh viên, review 21 story|PO; Dev cùng dự khi phù hợp|Ghi chú ẩn danh, sửa AC và ưu tiên dựa trên kết quả|
|Ngày 2|Tạo GitHub Projects/Issues từ bộ import|SM + PO|URL board thật, view backlog/Sprint, quyền nhóm|
|Ngày 2–3|Mỗi Dev clone, npm ci, check và chạy app|4 Dev|Ảnh/log cài chạy trên máy từng người, xử lý chênh Node|
|Ngày 3–4|Refinement ưu tiên US01–07, spike auth, chốt ADR|Dev + PO|Stories đủ rõ, dependency, ước lượng do Dev, chọn auth đã kiểm tra|
|Ngày 4–5|Demo bộ lập lịch và thống nhất forecast Sprint 1|Cả nhóm|Kịch bản đủ giờ/thiếu giờ, CI, một Sprint Goal dự thảo|

Definition of ready cho gói bàn giao tuần tới: board hoặc bộ import được chạy bởi người có quyền; ≥6 epic và 21 story đã được nhóm review; có AC/dữ liệu mẫu/priority/dependencies; công nghệ cài được từ lockfile; build/test và server smoke pass; liệt kê rõ backlog chưa hiện thực và credential còn thiếu.

Các story trong file hiện là **bản nháp tốt để refinement**, không thay thế sự đồng thuận của nhóm. Tên người, capacity theo giờ mỗi tuần, ngày Sprint và ước lượng là phần nhóm cần chốt. Các bài test hiện kiểm chứng bộ khung, không chứng nhận toàn bộ sản phẩm.

Bản template biên bản sự kiện:
- Planning: Sprint Goal; capacity; stories chọn; cách thực hiện; rủi ro/phụ thuộc.
- Review: Increment đạt DoD; ai phản hồi; quan sát; thay đổi Product Backlog.
- Retro: vấn đề/bằng chứng; action; owner; ngày kiểm tra; metric trước/sau.
