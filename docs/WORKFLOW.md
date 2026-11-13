# Quy trình làm việc nhóm

## 1. Nhận việc

- Chốt người dùng, kết quả cần đạt và tiêu chí nghiệm thu; đối chiếu [requirements](../requirements.md).
- Đọc [hợp đồng chức năng](product/README.md) và kiểm tra code/nhánh thực tế.
- Nếu frontend và backend giao nhau, thống nhất route, JSON, lỗi, trạng thái và người sở hữu trước khi làm.
- Kiểm tra `git status`; không ghi đè thay đổi của người khác. Tạo nhánh từ `main` đã cập nhật.

## 2. Triển khai

- Giữ cấu trúc React/Vite và Spring Boot/JDBC hiện có; tái sử dụng theme và client chung.
- Chỉ sửa phần cần cho công việc. Thiếu quyết định sản phẩm thì làm rõ, không tự chọn chính sách mới.
- Thay đổi schema bằng migration mới; không sửa migration đã áp. Phối hợp số phiên bản giữa các nhánh.
- Với công việc cần nhiều buổi, duy trì một kế hoạch ở `plans/active/`; việc nhỏ không cần thêm nhật ký riêng.

## 3. Kiểm tra

Dùng lệnh trong [harness](../.agents/harness/README.md). Chọn test phù hợp, kiểm tra
thành công, lỗi và quyền sở hữu; chỉ dùng database kiểm thử riêng.

Tách rõ bằng chứng: test mock, PostgreSQL thật, HTTP thật, thao tác trình duyệt và
provider thật. Lint/build hoặc ảnh giao diện không thay thế việc kiểm tra luồng lưu dữ liệu.

## 4. Bàn giao và tích hợp

PR cần có: vấn đề/kết quả, mã công việc, phần thay đổi, cách chạy/demo, test đã chạy và
giới hạn còn lại. Liên kết bằng chứng, không ghi “đạt” chỉ vì có file test.

Giải quyết xung đột theo hợp đồng hiện hành; kiểm tra lại luồng frontend → API → DB.
Khi đã có kết quả bàn giao, chuyển kế hoạch sang `plans/completed/`; việc còn lại được
nêu rõ, không để kế hoạch đã tích hợp tiếp tục mang trạng thái đang triển khai.

## 5. Duy trì tài liệu

| Nội dung thay đổi | Cập nhật tại |
| --- | --- |
| Quy tắc sản phẩm đã được thống nhất | `requirements.md` |
| Route, JSON, lỗi và hành vi chức năng | `docs/product/` và quy tắc API chung |
| Môi trường/khởi động | `setup.md` |
| Lệnh test | `.agents/harness/README.md` |
| Trạng thái hiện tại và giới hạn | `docs/status.md` |
| Kết quả một lần bàn giao hoặc quyết định lâu dài | `docs/plans/`, `docs/decisions/` |

Không thêm liên tục các đoạn “cập nhật thay thế đoạn trước” vào hợp đồng. Sửa nội dung
hiện hành và giữ bằng chứng cũ trong lịch sử Git hoặc khu vực lưu trữ.
