# Hướng dẫn backend dùng chung

Java 21, Spring Boot, Spring Security, JDBC, PostgreSQL và Flyway. Giữ package
`com.studyplanner.*`; không thêm ORM/JWT/framework khác để làm một feature chạy.

## Cấu trúc

- Controller: parse HTTP, gọi service, envelope đúng contract.
- Service: owner, điều phối và transaction. Policy: logic thời gian/trạng thái/validation.
- Repository: SQL và lọc owner; không suy quyền từ khóa ngoại hoặc ID do client gửi.
- API và lỗi theo [backend rules](../backend-api-rules.md); contract từng module ở
  [product](../product/README.md), không dựa skeleton trong templates làm implementation.

## Dữ liệu và đồng thời

Main hiện dùng V8 Recall, V9 Timer. Số migration ở bản thiết kế ban đầu của nhóm đã
khác; xem [setup](../../setup.md) trước khi ghép DB từng chạy branch cũ. Không sửa
migration đã áp, không tự clean/repair database để giải quyết xung đột.

Quỹ chung khóa owner trước goal. Timer/Recall dùng owner lock và receipt theo contract;
AI remote call ở ngoài transaction, recheck trước khi commit. Dữ liệu và receipt phải
nguyên tử, không nuốt lỗi persistence rồi trả thành công.

Task/goal xóa không được làm mất lịch sử Timer; Recall theo goal cascade. Thời lượng
lập lịch khác thời gian thực học. Bất kỳ thay đổi nào cũng phải giữ nguyên ranh giới này.

## Kiểm tra và bàn giao

Dùng [harness](../../.agents/harness/README.md): unit/MockMvc cho contract và PostgreSQL
cho owner, persistence, lock, version, replay. Chỉ chạy trên DB test riêng; một số suite
xóa users. Không gọi provider thật để thay thế test xác định.

Khi đổi API/schema, cập nhật contract, fixture, client và bằng chứng tương ứng. Kiểm tra
máy thiếu key, lỗi provider và retry; không sinh dữ liệu giả để làm demo trông thành công.
Các file `.java.example` trong templates chỉ để tham khảo, không chép đè sessions/recall đã có.
