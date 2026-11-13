# Ôn tập chủ động (flashcard): giao diện và client

**Trạng thái (2026-11-13):** giao diện `#recall`, client `lib/recall.js` và mock phát triển đã hiện thực trên nhánh `dev2` (nhánh remote `dev2-sprint4`). API `/api/v1/recall` do Người 3 làm đã được gộp vào `dev2` (migration V9) và giao diện đã được chạy với API thật ở mục 4, **trừ bước gọi Gemini** (xem mục 5). Chưa gộp vào `main`. Hợp đồng API là mục C của bộ giao việc nhóm (`docs/team-handoff/03-API-CONTRACT.md`), đang là đề xuất chờ nhóm và PO duyệt.

Phạm vi MVP: thẻ hỏi–đáp, người học **tự đánh giá** nhớ hay chưa nhớ. Không AI chấm bài, không lặp ngắt quãng, không trắc nghiệm.

## 1. Luồng trên giao diện

1. Chọn mục tiêu thật của tài khoản (lấy từ `GET /api/v1/goals`).
2. **Thêm nguồn**: dán văn bản (tối đa 30.000 ký tự) hoặc tải PDF có chữ (tối đa 5 MiB; số trang và việc có chữ do máy chủ kiểm). Lưu nguồn là một bước riêng.
3. **Tạo bộ thẻ** từ một nguồn đã lưu: tên bộ thẻ và số thẻ 5 đến 20 (mặc định 10). Bộ thẻ mới luôn là bản nháp.
4. **Xem bản nháp**: mỗi thẻ có câu hỏi, đáp án và đoạn trích nguồn (kèm trang với PDF). Sửa câu hỏi hoặc đáp án; thẻ đã sửa có nhãn "Bạn đã sửa" và ghi chú nội dung sửa chưa được đối chiếu với nguồn. Chỉ khi bấm **Xác nhận dùng bộ thẻ này** thành công, giao diện mới coi bộ thẻ là đang dùng.
5. **Ôn tập** bộ thẻ đang dùng: hiện câu hỏi trước; "Xem đáp án" mới hiện đáp án và hai nút "Chưa nhớ"/"Nhớ". Mở lại thì tiếp tục từ thẻ chưa đánh giá đầu tiên. Cuối lượt có tổng kết, ôn lại thẻ chưa nhớ hoặc tất cả.
6. **Xóa bộ thẻ** có bước xác nhận nói rõ mất thẻ và kết quả ôn, còn nguồn và mục tiêu không đổi.

## 2. Quy tắc của client

- Mọi gọi API dùng URL tương đối `/api/v1/recall/...`, cookie phiên và `X-CSRF-TOKEN` (qua `getCsrfToken`). Không có khóa Gemini hay gọi AI trực tiếp ở frontend.
- `Idempotency-Key` có cho tạo bộ thẻ và chấm thẻ. Một lần thử giữ nguyên key khi thử lại **cùng** đầu vào (kể cả sau timeout); đổi nội dung hoặc thành công thì lần sau dùng key mới. Không dùng chung key cho hai việc.
- Tiến độ lấy **đánh giá cuối của mỗi thẻ**: `reviewedCards = rememberedCards + forgottenCards ≤ totalCards`; thẻ chưa ôn không bị tính là quên.
- Lỗi xử lý theo `status` và `code`, không theo câu chữ message. Lỗi AI (`AI_INVALID_RESPONSE`, `AI_UNAVAILABLE`, `AI_TIMEOUT`) báo nguồn vẫn được giữ và cho thử lại. Lỗi `*_VERSION_CONFLICT`, `DECK_STATE_CONFLICT`, 404 tải lại dữ liệu. 401 hiện màn đăng nhập.
- Đánh giá chưa lưu được (mất mạng) giữ trạng thái chờ cùng key và có nút "Thử lưu lại"; không tự ghi "đã lưu".
- Giao diện: lớp CSS `.recall-*`, dùng các thành phần `components/ui` hiện có và token `theme.css`. Câu hỏi, nút lật và nút đánh giá dùng được bằng bàn phím (phần tử HTML chuẩn); chỉ thông báo kết quả lưu qua vùng `aria-live`.

## 3. Mock phát triển (không phải chức năng)

Chỉ khi chạy `npm run dev` và mở `/?recallMock=1#recall`, trang dùng `lib/recallMock.js`: bộ nhớ trong, theo cùng quy tắc hợp đồng (chỉ sửa bản nháp, phiên bản, kích hoạt, chỉ chấm thẻ khi đang dùng, key thử lại), dữ liệu tổng hợp từ fixture (`lib/recallFixtures.js`), mất khi tải lại, có banner nêu rõ. `recallFail=generate,review` làm một lần lỗi để thử đường thử lại. Bản build production **không chứa** mock (đã kiểm tra gói đầu ra) và nếu API thiếu thì trang báo lỗi thật, không hiện thẻ mẫu.

## 4. Đối chiếu tiêu chí (AR-UI-01) và bằng chứng

Hai loại chạy: **mock** (`?recallMock=1`) và **API thật** (backend Spring Boot, PostgreSQL 17 dùng một lần, không mock, đăng nhập thật).

| Tiêu chí | Hiện thực | Đã chạy | Chưa kiểm chứng |
| --- | --- | --- | --- |
| RUI-01 chọn mục tiêu thật, PDF hoặc dán, kiểm đầu vào, hiện tên nguồn | `SourceForm`, `validate*` | Test giới hạn. API thật: dán văn bản và **tải PDF có chữ (2 trang, 260 ký tự, trích đúng)** lưu vào PostgreSQL và hiện lại; ảnh `.png` và PDF trên 5 MiB bị chặn ở giao diện, không gửi yêu cầu; PDF không có chữ bị máy chủ từ chối với thông báo rõ và danh sách không đổi | PDF quét ảnh thật, PDF lớn gần 5 MiB hoặc gần 100 trang |
| RUI-02 lưu nguồn và tạo thẻ là hai trạng thái, lỗi AI không mất nguồn, thử lại không tải thêm nguồn | `GenerateForm`, key giữ theo lần thử | Test mock (lỗi rồi thử lại cùng key chỉ 1 bộ thẻ); trình duyệt với mock: lỗi 503 giữ nguồn, "Thử tạo lại" thành công | Timeout 60 giây và HTTP 429 của Gemini (chưa chạy với nhà cung cấp thật; HTTP 503 thật đã được báo đúng trước khi sửa, xem mục 8) |
| RUI-03 bản nháp có hỏi, đáp, nguồn; sửa; xác nhận; không báo đang dùng trước khi kích hoạt thành công | `DeckEditor` | API thật: mở bản nháp 10 thẻ, sửa một thẻ (có nhãn đã sửa), xác nhận, chuyển sang "Đang dùng"; dữ liệu đúng trong DB | Sửa đồng thời hai tab |
| RUI-04 hỏi trước, "Xem đáp án" mới được đánh giá, gọi là tự đánh giá; dùng được bằng bàn phím | `StudyDeck` | Trình duyệt: nút Nhớ/Chưa nhớ chỉ có sau khi lật. **Bàn phím** (mục 6): lưu nguồn, tạo, sửa, xác nhận, lật, chấm chỉ bằng bàn phím, thứ tự Tab hợp lý, focus hiện rõ và không bị mất | Trình đọc màn hình thật (NVDA, VoiceOver); mới kiểm cây truy cập và vùng `status` |
| RUI-05 chặn bấm trùng, mất mạng thử lại cùng key, tải lại còn bộ thẻ và tiến độ | cờ `saving`, `pending` có key | Mock: lỗi mạng giữ thẻ, "Thử lưu lại" ghi một lần. **API thật: chấm 3 thẻ, tải lại trang, mở lại thì tiếp tục đúng thẻ 4 với 3/10 đã đánh giá** (3 lượt chấm trong DB) | Mất mạng thật trên API thật |
| RUI-06 trạng thái trống, đang tải, lỗi, hết phiên có nội dung và hành động; mock chỉ để phát triển | từng màn hình | Trình duyệt: đang tải, chưa có mục tiêu/bộ thẻ, lỗi khi API thiếu; bundle không chứa mock | Hết phiên đăng nhập giữa chừng (đường 401) |
| RUI-07 thống kê theo đánh giá cuối, không vượt tổng | `deckProgress` | Test; mock: ôn lại thẻ chưa nhớ giữ 5/5; API thật: số liệu tiến độ đúng sau tải lại | Số liệu khi hai tab chấm cùng một thẻ |

## 5. Giới hạn

- **Gemini đã được thử bằng khóa trong `.env` với văn bản tổng hợp** (mục 8). Chưa thử lại sau khi đổi prompt: câu lệnh độc hại ẩn trong tài liệu và tài liệu sát 30.000 ký tự (cả hai đạt trước khi sửa). Chưa thử: PDF dùng font tiếng Việt cũ (VNI, TCVN3), PDF hai cột hoặc có bảng, PDF scan, quota hết (429) và chạy nhiều máy chủ cùng lúc.
- Chưa có test thành phần React (repo chưa có công cụ); giao diện kiểm bằng test logic thuần và thao tác thủ công trong trình duyệt ở 1024, 768 và 360 px.
- Trang đã thêm route `#recall` và một nút sidebar vào `App.jsx`; Người 1 sở hữu `App.jsx` nên cần gộp thống nhất.
- Lúc App đang kiểm tra đăng nhập, trang có thể hiện màn "Đăng nhập" thoáng qua vì App chưa truyền trạng thái đang kiểm tra.

## 6. Trợ năng và bố cục (kiểm 2026-11-13)

- **Bàn phím:** thứ tự Tab: mục tiêu, lựa chọn nguồn, tên, nội dung, nút lưu. Sau mỗi bước focus đi đến phần kế tiếp thay vì rơi về đầu trang: ô đầu của form tạo bộ thẻ, tên bộ thẻ, ô câu hỏi khi sửa, nút "Sửa thẻ" khi hủy hoặc lưu, nút "Giữ lại" khi hỏi xóa, câu hỏi mới, phần đáp án vừa lật, bản tổng kết, nút thử lại sau lỗi, ô lỗi đầu tiên khi nhập sai. Lỗi này đã được phát hiện khi kiểm và đã sửa.
- **Cây truy cập:** có landmark, tiêu đề, nhãn gắn đúng với ô nhập, thanh tiến độ có tên và giá trị, kết quả lưu đọc qua vùng `status`. Các hộp thông tin (mock, ghi chú bản nháp) dùng `role="status"`, chỉ lỗi mới dùng `alert`.
- **Tương phản:** chữ thường, chữ phụ, nhãn, nút chính và nút phụ đạt AA (từ 4.57:1). Nhãn "Đang dùng" dùng biến thể `success` dùng chung chỉ đạt 2.86:1, đã tối lại thành 6.19:1 **chỉ trên trang này**; biến thể dùng chung ở các trang khác chưa được sửa.
- **Bố cục:** không tràn ngang ở 1024, 768 và 360 px (study, danh sách, bản nháp, form sửa, form tạo bộ thẻ).

## 7. Dữ liệu lớn, chữ dài và nhiều dòng (kiểm 2026-11-13)

Chạy trên ứng dụng thật (Vite, Spring Boot, PostgreSQL 17 dùng một lần) với một tài khoản có 130 mục tiêu, 130 nguồn, 130 bộ thẻ (650 thẻ) và 130 phiên học, trong đó có tên 100 ký tự không dấu cách, câu hỏi 300 ký tự liền nhau, đáp án 1.900 ký tự liền nhau, trích dẫn 484 ký tự liền nhau và đáp án nhiều dòng.

Lỗi tìm thấy rồi sửa (đều trong phần Ôn tập):

| Lỗi | Trước | Sau |
| --- | --- | --- |
| Danh sách nguồn và bộ thẻ chỉ lấy trang đầu của API | 100 trên 130 mục, 30 mục mất im lặng; trang cao 18.078 px | Client đi qua mọi trang (`meta.totalPages`); danh sách hiện 10 mục, "Xem thêm 10 bộ thẻ" mở thêm, có dòng "Đang hiển thị x / n", focus vào mục mới đầu tiên; trang cao 3.149 px; ra đủ 131 nguồn và 129 bộ thẻ |
| Chữ dài liền nhau làm vỡ bố cục | Trang rộng 20.098 px (bản nháp) và 22.586 px (ôn tập) | `overflow-wrap: anywhere` cho cả trang; không tràn ngang ở 1024, 768 và 360 px ở danh sách, bản nháp, ôn tập, đáp án, hộp xác nhận xóa |
| Xuống dòng bị mất khi hiển thị | Đáp án 3 dòng và một đoạn hiện thành một dòng (dữ liệu trong DB vẫn đúng) | `white-space: pre-line` cho câu hỏi, đáp án và trích dẫn |

Thao tác dữ liệu trên giao diện và đối chiếu DB (API thật):

| Thao tác | Kết quả trong DB |
| --- | --- |
| Lưu nguồn văn bản dài 30.000 ký tự nhiều đoạn, tên 120 ký tự | Một dòng `recall_materials` (lưu 29.999 vì máy chủ cắt dòng trống cuối), còn xuống dòng. 30.001 ký tự bị chặn ở giao diện |
| Tải PDF thật 2 trang | `kind=PDF`, 2 trang, 260 ký tự, chữ trích đúng; ảnh và tệp trên 5 MiB bị chặn không gửi yêu cầu; PDF không chữ bị máy chủ từ chối |
| Sửa đáp án nhiều dòng | Lưu đúng ký tự xuống dòng, `edited_by_user = true`, `version = 1` |
| Kích hoạt bộ thẻ | `status` chuyển sang `ACTIVE` |
| Chấm Nhớ/Chưa nhớ | Một dòng `recall_reviews` mỗi lượt, thẻ giữ đánh giá cuối |
| Xóa bộ thẻ | Dòng bộ thẻ và các thẻ của nó bị xóa, nguồn vẫn còn (đúng như thông báo xác nhận) |

Giới hạn: danh sách vẫn tải toàn bộ các mục về trình duyệt rồi mới chia 10 mục mỗi lần hiển thị (đủ cho quy mô hiện tại; nếu lên hàng nghìn nguồn cần phân trang phía máy chủ). Thẻ đáp án 1.900 ký tự liền nhau cao khoảng 1.220 px ở 1024 px, dài nhưng nằm trong thẻ.

## 8. Sửa luồng tải tài liệu lên Recall (2026-11-13)

Một thành viên báo luồng "tải tài liệu lên Recall" chưa ổn. Kiểm tra trên ứng dụng thật, PostgreSQL thật và Gemini thật (văn bản và PDF tiếng Việt tổng hợp, font Arial nhúng, nhờ PDFBox tạo) tìm ra các lỗi sau, đã sửa trong `backend/.../recall` và giao diện. Mỗi lỗi có test; hai lỗi quan trọng nhất đã được phá thử để chắc test bắt được.

| Lỗi (trước) | Sửa | Bằng chứng |
| --- | --- | --- |
| Lưu bộ thẻ không nguyên tử: `@Transactional` gọi từ chính class nên bị bỏ qua; lỗi khi lưu thẻ để lại bộ thẻ rỗng "5 thẻ" trong danh sách | Dùng `TransactionTemplate`; biên nhận, bộ thẻ và thẻ cùng một giao dịch | PostgreSQL: gây lỗi lưu thẻ thì không còn bộ thẻ hay biên nhận, thử lại cùng key thành công; bỏ giao dịch thì test đỏ |
| Kích hoạt được bộ thẻ rỗng (dựa vào cột `card_count` lưu sẵn) | Đếm thẻ thật trong DB | `409 DECK_STATE_CONFLICT`; dùng lại cách cũ thì test đỏ (200) |
| Hai yêu cầu cùng `Idempotency-Key` cùng lúc ra hai bộ thẻ | Khóa theo (người dùng, key) trong tiến trình cho lúc đang gọi AI, thêm biên nhận lưu trước tiên với khóa duy nhất ở DB; bên thua trả lại bộ thẻ của bên thắng | Hai yêu cầu: một `201`, một `409 GENERATION_IN_PROGRESS`, đúng 1 bộ thẻ; thử lại sau đó nhận lại cùng bộ thẻ |
| Thiếu khóa Gemini hoặc lỗi mạng vẫn trả `201` với thẻ giả theo heuristic; AI trả dưới 5 thẻ thì trộn thẻ giả | Bỏ hẳn bộ tạo thẻ dự phòng: thiếu khóa `503 AI_UNAVAILABLE`, lỗi mạng `503`, dưới 5 thẻ hợp lệ `502 AI_INVALID_RESPONSE` | Test đơn vị và PostgreSQL (lỗi AI không lưu gì); trước khi sửa, HTTP 503 thật của Gemini đã được báo đúng `AI_UNAVAILABLE`, đường mã đó được giữ |
| Trích dẫn so khớp từng ký tự: PDF dấu tách rời (NFD) mất thẻ (7/10) | Lưu mọi chữ ở dạng NFC và `\n`; so khớp bỏ qua dạng Unicode, dấu nháy và gạch cong, khoảng trắng đặc biệt | Với Gemini thật: PDF NFD ra 10/10 thẻ (trước 7); ký tự đếm vào giới hạn 30.000 giảm từ 1.321 xuống 991 |
| PDF không có số trang (cả 10 thẻ `page = null`), giao diện ghi "Đoạn dán vào" | Prompt có dấu `[Trang N]`; máy chủ tự tìm trang chứa trích dẫn; giao diện ghi "Trang N" hoặc "Tài liệu PDF" | Gemini thật: thẻ ghi Trang 1, 2, 3 đúng; trích dẫn nằm đúng trang nêu |
| Thông báo lỗi PDF lộ chữ nội bộ tiếng Anh; PDF có mật khẩu bị gọi là "hỏng" | `password_protected` và `corrupted_pdf` có thông báo cố định; giao diện không bao giờ hiện chữ của máy chủ cho lỗi 400 và 422 | Tải qua giao diện: "Tệp PDF có mật khẩu. Hãy bỏ mật khẩu rồi tải lại." |
| Tên tệp 300 ký tự gây `500` | `400 fileName too_long` | PostgreSQL |
| Không biết máy chủ đã đọc chữ gì từ PDF | `GET /api/v1/recall/materials/{id}` và nút "Xem nội dung đã đọc" | Giao diện: "PDF · 3 trang · 1.078 ký tự" và chữ đúng dấu |
| Nhận 7 thẻ thay vì 10 mà không có lời giải thích | Thông báo "AI chỉ tạo được x trên y thẻ, vì chỉ giữ lại những thẻ có trích dẫn đúng nguyên văn và không trùng câu hỏi" | Test và giao diện (chế độ mẫu) |
| Câu hỏi trùng nhau; câu trả lời dài bị cắt giữa câu | Loại câu hỏi trùng (không phân biệt hoa thường và khoảng trắng); loại thẻ có câu hỏi trên 300 hoặc đáp án trên 2.000 ký tự thay vì cắt (chỉ trích dẫn được rút gọn, vẫn nguyên văn) | Test đơn vị |
| Log ghi nội dung trích dẫn và thân phản hồi của Gemini; khóa nằm trong URL | Log chỉ ghi số lượng và mã trạng thái; khóa gửi qua header `x-goog-api-key` | Đọc log: không có nội dung tài liệu, không có chuỗi `key=` |
| Đường dẫn cũ `/api/recall/...` không có `v1` | Gỡ | `404` |

Chưa đổi: việc tải cùng một tệp nhiều lần vẫn tạo nhiều nguồn (không chống trùng, vì đổi sang trả nguồn cũ là thay đổi hợp đồng `201`); phép đếm ký tự của PDF vẫn do máy chủ tính trên chữ đã chuẩn hóa.

