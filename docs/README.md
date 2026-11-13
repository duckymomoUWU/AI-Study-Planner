# Tài liệu AI Study Planner

Bắt đầu theo nhu cầu của bạn:

| Cần làm gì? | Đọc tài liệu nào? |
| --- | --- |
| Hiểu sản phẩm và giới hạn MVP | [Yêu cầu](../requirements.md) |
| Cài đặt và chạy trên máy | [Hướng dẫn khởi động](../setup.md) |
| Biết chức năng nào đã có, kiểm chứng đến đâu | [Trạng thái hiện tại](status.md) |
| Làm frontend hoặc nối API | [Frontend](../frontend/README.md), [hợp đồng chức năng](product/README.md) |
| Làm backend | [Quy tắc API](backend-api-rules.md), [hợp đồng chức năng](product/README.md) |
| Chạy test và kiểm tra trước bàn giao | [Verification harness](../.agents/harness/README.md) |
| Nhận việc, review và bàn giao | [Quy trình nhóm](WORKFLOW.md), [phân công và mẫu](team-handoff/README.md) |

## Cấu trúc và trách nhiệm

- `product/`: hợp đồng hiện hành của từng chức năng; không dùng để ghi nhật ký kiểm thử.
- `plans/`: kế hoạch và bằng chứng bàn giao có ngày; không thay thế trạng thái hiện tại.
- `decisions/`: các quyết định thiết kế cần giữ lâu dài.
- `team-handoff/`: phân công, tiêu chí nghiệm thu, design system và mẫu dùng chung.
- `reviews/`, `archive/`: lịch sử rà soát và tài liệu đã thay thế.
- `templates/`, `patterns/`: tài liệu tham khảo cho người cần lập kế hoạch hoặc bổ sung kiểm tra.

## Cập nhật tài liệu

Quy tắc sản phẩm nằm ở `requirements.md`; chi tiết giao tiếp nằm trong hợp đồng của
chức năng; lệnh chạy nằm ở `setup.md`; lệnh test nằm ở harness. Khi thay đổi, sửa đúng
nơi thay vì thêm một đoạn đính chính vào nhiều file. Kết quả một lần chạy phải có ngày,
nhánh/commit và giới hạn; không biến số test hoặc môi trường của một người thành mặc định chung.
