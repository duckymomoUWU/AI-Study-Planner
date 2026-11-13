# Story và Acceptance Criteria

**Cách đọc:** AC = điều kiện người dùng quan sát được; DoD = điều kiện kỹ thuật để bàn giao. Tiêu chí dùng để review chức năng đã tích hợp và các cải tiến tiếp theo. Trạng thái từng phần tra ở [status](../status.md); có code không mặc nhiên nghĩa mọi AC đã đạt. Mã story trong tài liệu không thay thế Jira issue.

## Người 1: Landing + UI Timer

### LND-01 — Trang giới thiệu riêng

**Người dùng/vấn đề:** khách mới chưa hiểu sản phẩm và chưa biết bắt đầu ở đâu. **Demo:** mở cửa sổ chưa đăng nhập → landing → đăng ký/đăng nhập → tạo mục tiêu.

Phải có: hero nói rõ giá trị, CTA bắt đầu/đăng nhập, ba bước tải đề cương → duyệt kế hoạch → học mỗi ngày, giới thiệu ngắn Timer/Recall theo đúng trạng thái đã thực hiện. Không có số liệu người dùng/thành tích, đánh giá khách hàng hoặc giá gói tự bịa.

| AC | Điều kiện nghiệm thu |
| --- | --- |
| LND-01-A | Khách vào `/` thấy landing, không bị buộc đăng nhập trước khi đọc giới thiệu. Không hiện sidebar làm việc như một dashboard trống. |
| LND-01-B | “Bắt đầu lập kế hoạch” dẫn khách tới đăng ký; sau auth thành công mở mục tiêu, không mất phiên. Người đã đăng nhập đi thẳng tới mục tiêu. |
| LND-01-C | “Đăng nhập” mở đúng tab đăng nhập; vào Tổng quan sau khi đã có phiên vẫn hoạt động như cũ. |
| LND-01-D | CTA không gọi tạo mục tiêu/API ghi trước khi người dùng nhập và xác nhận. Không chuyển tới route không tồn tại. |
| LND-01-E | Màn hình 360px và 1440px không tràn ngang; dùng bàn phím truy cập được CTA, không lỗi dấu tiếng Việt. |

**Đầu ra:** LandingPage + CSS, shell navigation PR, ảnh mobile/desktop, test điều hướng hoặc bước tái hiện thật. **Ngoài scope:** landing builder, SEO SSR, thanh toán, làm lại auth.

### TMR-UI-01 — Giao diện phiên tập trung

**Demo:** chọn task → bắt đầu → tạm dừng → tải lại → tiếp tục → kết thúc → thấy thời gian vừa lưu.

Giao diện: tên môn/công việc, đồng hồ, trạng thái rõ, Bắt đầu/Tạm dừng/Tiếp tục/Kết thúc/Hủy, thông báo lỗi và lịch sử gần đây. Nghỉ 5 phút là đồng hồ riêng trên UI, không cộng thời gian học.

| AC | Điều kiện nghiệm thu |
| --- | --- |
| TUI-01 | Mở Timer từ lịch mang đúng task ID. Truy cập Timer trực tiếp cho chọn task thật của tài khoản; không gọi start với ID giả. |
| TUI-02 | Bắt đầu mới chỉ hiện thành công sau khi backend trả session. Đang gửi phải chặn bấm trùng. |
| TUI-03 | Tải lại/tab ẩn không làm reset phiên. Lấy active session từ backend; PAUSED vẫn paused. Không dựa hoàn toàn vào số tick setInterval. |
| TUI-04 | Khi snapshot báo đã đủ 25 phút, đồng hồ dừng ở 00:00 và gửi FINISH idempotent; nếu mất mạng báo chưa lưu, cho thử lại cùng yêu cầu. Không tự ghi “đã lưu” từ UI. |
| TUI-05 | Hủy hỏi xác nhận rõ; phiên hủy không được tính vào tổng học đã hoàn tất. Kết thúc sớm lưu đúng giây backend tính. |
| TUI-06 | Hết phiên học không tự đánh dấu task hoàn thành, không dời lịch, không đổi Google Calendar. Có thể chuyển sang nghỉ 5 phút hoặc quay lại lịch. |
| TUI-07 | Có phiên đang chạy/tạm dừng ở tab khác thì mở lại nó hoặc báo xung đột, không vẽ hai phiên riêng. Session API 401/404/409 có trạng thái dễ hiểu. |

**Phụ thuộc:** contract + backend Người 2. **DoD riêng:** kết nối API thật, không có countdown chỉ chạy đẹp trong mock; ảnh/video có thao tác reload và mất mạng. Đồng hồ không đọc màn hình mỗi giây qua aria-live; chỉ thông báo chuyển trạng thái.

## Người 2: Backend phiên học + UI Active Recall

### TMR-BE-01 — Phiên học bền vững, thời gian do server tính

**Demo:** hai request start đồng thời chỉ có một phiên active; pause/finish rồi mở lại thấy đúng tổng thời gian.

| AC | Điều kiện nghiệm thu |
| --- | --- |
| TBE-01 | Start chỉ nhận task của owner từ principal; task lạ và task tài khoản khác đều 404. Task đã hoàn thành vẫn được ôn lại. |
| TBE-02 | Mỗi người tối đa một RUNNING hoặc PAUSED. Có unique guard ở DB và xử lý race thành 409; không chỉ kiểm tra if trước insert. |
| TBE-03 | Focus = thời gian RUNNING do Clock server tính, không tính PAUSED/BREAK; tổng mỗi phiên không vượt 1500 giây. Body không được nhận elapsedSeconds tùy ý từ client. |
| TBE-04 | Pause/resume/finish/cancel kiểm tra expectedVersion. Thay đổi bị từ chối không đổi thời gian/status/version. |
| TBE-05 | Retry cùng Idempotency-Key và payload trả cùng kết quả, không thêm session hoặc cộng thời gian hai lần; key cũ cho payload khác trả 409. |
| TBE-06 | FINISH trước 25 phút được lưu đúng số giây; hết hạn focus mà client đóng thì GET vẫn tính tối đa 1500, báo focusComplete để UI hoàn tất, không tự tính qua đêm. |
| TBE-07 | Chỉ COMPLETED tính vào tổng đã học. CANCELLED vẫn có trong lịch sử nhưng không cộng vào tổng; thời gian nghỉ không phải session học. |
| TBE-08 | Xóa task/goal không làm mất lịch sử học; giữ owner và snapshot tên, liên kết task/goal có thể null. Phiên đã mở vẫn kết thúc được, không khôi phục task đã xóa. |
| TBE-09 | Không cập nhật estimatedMinutes, completed hoặc lịch AI/Google khi lưu session; quỹ chung vẫn là ràng buộc lập lịch, không phải bộ đếm thời gian thực tế. |

**Đầu ra:** sessions package, migration sessions V9, API action/active/history/summary, test Clock cố định + PostgreSQL ownership/race/replay. **Ngoài scope:** chống gian lận, đo sự tập trung thực sự, tracking chạy nền qua extension, notification/service worker.

### AR-UI-01 — Tạo và học flashcard từ nội dung của mình

**Demo:** chọn môn → gửi tài liệu/text → sinh bản nháp → sửa một thẻ → xác nhận → lật đáp án → nhớ/chưa nhớ → reload thấy kết quả.

| AC | Điều kiện nghiệm thu |
| --- | --- |
| RUI-01 | Chọn mục tiêu thật, tải PDF văn bản hoặc dán text; kiểm tra dung lượng/đầu vào, hiện tên nguồn. Client không chứa Gemini key và không gọi Gemini trực tiếp. |
| RUI-02 | Upload và sinh thẻ là hai trạng thái riêng. AI lỗi không làm mất nguồn đã lưu; bấm thử lại không upload thêm nguồn ngoài ý muốn. |
| RUI-03 | Bản nháp có câu hỏi, đáp án, dẫn nguồn/đoạn trích; cho sửa question/answer, sau đó xác nhận dùng bộ thẻ. Không báo bộ thẻ đang dùng khi chưa activate thành công. |
| RUI-04 | Khi ôn: hiện câu hỏi trước; nút Xem đáp án → mới cho chọn Nhớ/Chưa nhớ. Gọi đó là tự đánh giá, không gọi “AI chấm đúng/sai”. |
| RUI-05 | Chặn bấm trùng khi gửi review; mất mạng cho retry cùng key. Reload còn bộ thẻ và tiến độ; không đòi giữ đúng animation/vị trí thẻ trước reload. |
| RUI-06 | Không có thẻ/đang tải/AI lỗi/hết phiên đăng nhập đều có nội dung và hành động rõ; mock chỉ dùng phát triển, không fallback giả thành công. |
| RUI-07 | Thống kê Nhớ/Chưa nhớ tính đánh giá cuối của mỗi thẻ. Cùng một thẻ đánh giá nhiều lần không khiến reviewedCards vượt totalCards. |

**Đầu ra:** ActiveRecallPage, recall components/client, ảnh mobile/desktop và demo API thật. **Phụ thuộc:** Người 3. **DoD riêng:** bỏ mock trước khi nghiệm thu, không sửa prompt/model planner để làm UI chạy.

## Người 3: Backend/AI Flashcard

### AR-BE-01 — Nguồn tài liệu và bản nháp câu hỏi

| AC | Điều kiện nghiệm thu |
| --- | --- |
| RBE-01 | Nhận text ≤30.000 ký tự hoặc PDF văn bản ≤5 MiB, ≤100 trang; không nhận hai kiểu cùng lúc. PDF scan/rỗng, file giả định dạng, quá dài đều báo lỗi rõ, không tự bịa nội dung. Không OCR trong MVP. |
| RBE-02 | Lưu text trích và metadata/page ở PostgreSQL theo owner/mục tiêu. Không giả định upload đề cương hiện tại đã lưu file gốc hoặc có module Notes để gọi lại. |
| RBE-03 | Input tài liệu là dữ liệu không tin cậy, không phải chỉ thị hệ thống. Gemini chỉ tạo theo nguồn đã chọn; output có schema rõ và dẫn đoạn trích có thật trong nguồn. |
| RBE-04 | Tạo 5–20 thẻ theo count yêu cầu; mặc định đề xuất 10. Question/answer không trống, ID hợp lệ, không trùng, quote/page kiểm tra được. Kết quả sai trả lỗi, không chèn bộ thẻ rỗng hoặc tự sinh nội dung lấp chỗ. |
| RBE-05 | Deck mới là DRAFT. Người dùng xem/sửa rồi activate; chỉ ACTIVE mới ghi review. AI không sửa lịch học hoặc task hiện có. |
| RBE-06 | Không giữ transaction/row lock khi đợi Gemini. Timeout/lỗi provider không xóa material hay deck cũ; kiểm tra lại ownership/material còn tồn tại trước khi commit. |
| RBE-07 | Sinh lại cùng request key không tạo trùng deck; key khác là đề xuất mới, không ghi đè bộ cũ. Model/key lấy cấu hình backend, không hardcode vào source/fixture. |

### AR-BE-02 — Lưu thẻ và tự đánh giá

| AC | Điều kiện nghiệm thu |
| --- | --- |
| RBE-08 | CRUD đọc/sửa/xóa deck, sửa thẻ đều kiểm tra owner và parent. Deck xóa thì cards/reviews liên quan bị xóa nguyên tử; không đụng task/mục tiêu. |
| RBE-09 | Chỉ cho sửa question/answer trong DRAFT. Thẻ sửa bởi người dùng có editedByUser; không khẳng định đáp án sửa đã được AI/nguồn xác minh. |
| RBE-10 | Activate với version đúng; bản nháp rỗng hoặc thẻ không hợp lệ không activate. Mọi lỗi giữ trạng thái cũ. |
| RBE-11 | Review chỉ nhận REMEMBERED/FORGOT; không nhận điểm/đáp án để AI chấm. Timestamp và owner do server lấy. |
| RBE-12 | Retry review cùng key chỉ ghi một lần; nhiều lần ôn khác key được lưu lịch sử, progress tính trạng thái cuối mỗi thẻ. |
| RBE-13 | Người khác không đọc được material/deck/card/review, kể cả đoán UUID; response/log không lộ nội dung hoặc token. Xóa goal cascade dữ liệu recall theo contract, không xóa lịch sử học Timer. |

**Đầu ra:** recall package + V9 sau V8, Gemini adapter tách biệt, schema/validation, endpoint và integration tests. **Ngoài scope:** AI grading, OCR, tự xuất câu hỏi ra Google, chat tài liệu, spaced repetition, lặp vô hạn đến khi AI trả đẹp.
