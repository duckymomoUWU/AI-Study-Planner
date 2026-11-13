# Phân công và tài liệu dùng chung

Bộ giao việc cho Landing, Timer và Active Recall. Các phần này đã được tích hợp;
đọc [trạng thái](../status.md) để biết phần đã có và việc cần kiểm chứng thêm.

| Vai trò | Trách nhiệm |
| --- | --- |
| Dev 1 | Landing, giao diện Timer và điều hướng |
| Dev 2 | Backend phiên học, giao diện/client Active Recall |
| Dev 3 | Backend/AI nguồn tài liệu, bộ thẻ và tự đánh giá |

Phân công là trách nhiệm phối hợp, không phải bằng chứng một người đã hoàn tất phần việc.
Khi nhận cải tiến mới, chốt tiêu chí và phần giao nhau trên code hiện tại.

## Tài liệu cần dùng

1. [Kế hoạch phối hợp](01-KE-HOACH.md): thứ tự và điểm giao nhau.
2. [Tiêu chí nghiệm thu](02-GIAO-VIEC-VA-AC.md): checklist theo chức năng.
3. [API chung](03-API-CONTRACT.md): đường dẫn tới hợp đồng hiện hành và fixtures.
4. [Frontend](04-FRONTEND-GUIDE.md): theme và quy ước code; [bảng minh họa](FRONTEND-THEME.html).
5. [Backend](05-BACKEND-GUIDE.md): lớp, dữ liệu và migration.
6. [Ready/Done và bàn giao](06-DOD-VA-KIEM-THU.md).

`templates/` và `fixtures/` là mẫu để phát triển/test, không phải API đang chạy hoặc
dữ liệu production. Không sao chép skeleton đè lên service/controller đã có.
Hợp đồng chính ở [product](../product/README.md); bộ giao việc không tạo một spec khác.
