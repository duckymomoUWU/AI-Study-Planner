---
name: study-planner-test-behavior
description: Thiết kế test, tái hiện lỗi hoặc xác minh hành vi AI Study Planner cho dev và QA từ tiêu chí chấp nhận; không triển khai story hay tự thay đổi yêu cầu sản phẩm.
---

# Kiểm tra hành vi

Đọc [AGENTS.md](../../../AGENTS.md), [requirements.md](../../../requirements.md) và [harness](../../harness/README.md); xem [setup.md](../../../setup.md) khi cần môi trường chạy thật. Chạy git status và giữ thay đổi đang làm dở. Dùng skill này độc lập, không tự gọi skill khác.

- Bắt đầu từ tiêu chí chấp nhận: gắn mỗi ca với tiêu chí, dữ liệu, thao tác và kết quả mong đợi. Nếu thiếu quyết định ảnh hưởng kết quả đúng/sai, hỏi PO và ghi phần chưa chốt, không tự đặt quy tắc mới.
- Khi tái hiện lỗi, ghi điều kiện môi trường, dữ liệu tối thiểu, bước tái hiện, kết quả mong đợi và thực tế trước khi kết luận.
- Chọn ca đúng, ca lỗi và biên liên quan. Với lịch học, cân nhắc 14/15/480/481 phút, khoảng 89/90/91 ngày, ngày thi bằng/trước ngày bắt đầu, việc rơi vào ngày thi, ngân sách vừa đủ/thiếu và số phút thiếu. Chỉ lấy ca thuộc tiêu chí đang kiểm tra.
- Khi liên quan, kiểm tra quyền sở hữu giữa hai tài khoản, ngày/giờ và múi giờ, bản nháp chưa được lưu trước xác nhận, gửi lại không tạo trùng, AI lỗi vẫn tạo việc thủ công, tiến độ rỗng 0/0. Lấy kỳ vọng từ requirements, không suy diễn từ schema.
- Chọn lớp nhỏ nhất đủ chứng minh hành vi: logic thuần cho tính toán; test API/service/DB khi cần chứng minh validate, ownership, giao dịch hoặc ràng buộc; trình duyệt khi tiêu chí nằm ở tương tác hiển thị. Mock fetch không chứng minh backend hay DB hoạt động.
- Với AI lập lịch, dùng phản hồi mẫu cố định (hợp lệ, sai cấu trúc, vượt ngân sách hoặc lỗi phù hợp tiêu chí) để test logic xử lý và kiểm tra quy tắc. Tách lần thử model thật thành kiểm tra riêng, ghi model/cấu hình và kết quả quan sát; không dùng tính bất định của model làm kỳ vọng chính xác cho test logic. Không log bí mật hay nội dung học thật.
- Hiện `frontend/src/lib/api.test.js` dùng `node:test` và mock fetch cho `/actuator/health`; chưa có test backend, browser/E2E hoặc API nghiệp vụ. Kiểm tra hiện trạng trước khi chọn công cụ; không giả định có sẵn test runner/backend fixtures. Script npm test hiện chỉ chạy file API test này; nếu thêm file test phải bảo đảm nó thực sự được chạy.
- Dùng lệnh và điều kiện môi trường trong harness. Phân biệt build, test dùng mock, test tích hợp với dịch vụ thật và demo thủ công. Maven verify hiện không có test backend nên không chứng minh nghiệp vụ hay kết nối DB; health UP cũng không chứng minh API nghiệp vụ.

## Đầu ra

| Tiêu chí | Cách kiểm tra | Kết quả |
| --- | --- | --- |
| Mã tiêu chí và hành vi mong đợi | Loại kiểm tra, lớp, dữ liệu, bước/lệnh từ harness | Đạt / Không đạt / Chưa chạy, bằng chứng hoặc lý do |

Ghi riêng từng loại kiểm tra nếu cùng phủ một tiêu chí. Nêu rõ kiểm tra chỉ mới thiết kế, kiểm tra chưa chạy, tiêu chí chưa được chứng minh và lần thử model thật (nếu có). Không ghi đạt chỉ vì đã thêm test hoặc vì tài liệu từng ghi lệnh chạy thành công.
