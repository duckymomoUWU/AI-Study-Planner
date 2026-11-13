# Bộ giao việc: Landing, Pomodoro và Active Recall

**Dành cho:** PO và 3 thành viên AI Study Planner. **Ngày:** 11/11/2026. **Source tham chiếu:** `main`, commit `29dbf6d`.

## Chốt phạm vi và cách dùng

Phân công và phạm vi MVP đã được PO đồng ý trong trao đổi:

| Thành viên | Phần chịu trách nhiệm |
| --- | --- |
| Người 1 — Frontend | Landing riêng + giao diện Timer + tích hợp điều hướng và kiểm tra theme |
| Người 2 — Fullstack | Backend phiên học/lưu thời gian + giao diện và client API Active Recall |
| Người 3 — Backend/AI | Nhận tài liệu, sinh/lưu bộ câu hỏi, API sửa/xác nhận thẻ và tự đánh giá |

MVP Active Recall là **flashcard hỏi–đáp, tự đánh giá nhớ/chưa nhớ**. Không AI chấm bài, không spaced repetition, không trắc nghiệm phức tạp. Timer chỉ làm Pomodoro tập trung 25 phút/nghỉ 5 phút; chưa thêm stopwatch tùy chỉnh. Thời lượng, giới hạn upload và chi tiết API bên dưới là **đề xuất triển khai v1** để PO/team duyệt ở D1, không phải chức năng đã tồn tại. Sau D1, đóng băng contract trước khi code song song.

Bộ này là **tài liệu giao việc và mẫu tham chiếu**, chưa triển khai ba chức năng, chưa thêm API hay migration vào app. Các đoạn mẫu không phải code đã kiểm thử trong production. Không sửa requirements cũ bằng cách suy diễn từ tài liệu này.

## Gửi cho thành viên những gì?

1. [Kế hoạch, phân công, thứ tự merge](01-KE-HOACH.md).
2. [Story và tiêu chí nghiệm thu cho từng người](02-GIAO-VIEC-VA-AC.md).
3. [API chung v1 và dữ liệu mẫu](03-API-CONTRACT.md) — cả ba phải đọc trước khi làm.
4. [Design system + quy ước frontend](04-FRONTEND-GUIDE.md).
5. [Quy ước backend + dữ liệu](05-BACKEND-GUIDE.md).
6. [Definition of Ready/Done + checklist bàn giao](06-DOD-VA-KIEM-THU.md).
7. [Mở bảng mẫu giao diện bằng trình duyệt](FRONTEND-THEME.html).
8. Thư mục [templates](templates/) chứa mẫu file để tham khảo; [fixtures](fixtures/) chứa JSON để FE mock theo contract.

**“Theme/template” trong bộ này gọi là:** design system ở frontend; API contract, coding conventions và cấu trúc lớp ở backend. Không có framework/backend theme mới cần cài.

## Mặc định phối hợp

- D1–D7 là ngày làm việc tương đối, **chưa phải ngày hẹn lịch**. PO điền tên người, ngày bắt đầu và mức độ tham gia trước khi gửi.
- Lấy code từ `main` mới nhất. Không dùng nhánh tích hợp cũ làm gốc nếu nó chưa chứa sửa CI `29dbf6d`.
- Giữ React + Vite + CSS hiện có; Java 21 + Spring Boot + JDBC + PostgreSQL + Flyway. Không tự đổi sang Tailwind, TypeScript, JPA hay JWT.
- Chạy local thống nhất `http://127.0.0.1:5173`, nhất là khi kiểm tra Google OAuth. Không gửi `.env` thật vào PR/ZIP/Git; mỗi người tự cấu hình từ `.env.example` và nhận secret qua kênh riêng.
- Mọi route/API ghi trong phần **mới** ở bộ này là việc cần xây. API đang có được dẫn bằng file thật trong repo.

## Tin nhắn có thể copy gửi nhóm

> Nhóm triển khai 3 phần MVP: Người 1 làm Landing + UI Pomodoro; Người 2 làm backend phiên học + UI Active Recall; Người 3 làm backend/AI flashcard từ tài liệu. Đọc README, API contract và phần giao việc của mình. D1 chốt JSON và callback trước, sau đó code trên branch riêng từ main. Giữ theme kem–xanh rêu, font Be Vietnam Pro và cơ chế session/CSRF. Mỗi PR cần AC đã kiểm tra, ảnh/video demo, test thật và migration nếu có. Không đưa secret lên Git và không đổi luồng tạo mục tiêu/lịch học hiện tại. D6 tích hợp, D7 PO nghiệm thu; lịch này là đề xuất và sẽ điều chỉnh theo năng lực/thời gian của nhóm.
