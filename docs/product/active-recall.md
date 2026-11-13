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
| RUI-02 lưu nguồn và tạo thẻ là hai trạng thái, lỗi AI không mất nguồn, thử lại không tải thêm nguồn | `GenerateForm`, key giữ theo lần thử | Test mock (lỗi rồi thử lại cùng key chỉ 1 bộ thẻ); trình duyệt với mock: lỗi 503 giữ nguồn, "Thử tạo lại" thành công | Lỗi AI thật: máy chủ hiện tại tự tạo thẻ dự phòng khi thiếu khóa hoặc Gemini lỗi, nên đường báo lỗi chưa gặp được với API thật |
| RUI-03 bản nháp có hỏi, đáp, nguồn; sửa; xác nhận; không báo đang dùng trước khi kích hoạt thành công | `DeckEditor` | API thật: mở bản nháp 10 thẻ, sửa một thẻ (có nhãn đã sửa), xác nhận, chuyển sang "Đang dùng"; dữ liệu đúng trong DB | Sửa đồng thời hai tab |
| RUI-04 hỏi trước, "Xem đáp án" mới được đánh giá, gọi là tự đánh giá; dùng được bằng bàn phím | `StudyDeck` | Trình duyệt: nút Nhớ/Chưa nhớ chỉ có sau khi lật. **Bàn phím** (mục 6): lưu nguồn, tạo, sửa, xác nhận, lật, chấm chỉ bằng bàn phím, thứ tự Tab hợp lý, focus hiện rõ và không bị mất | Trình đọc màn hình thật (NVDA, VoiceOver); mới kiểm cây truy cập và vùng `status` |
| RUI-05 chặn bấm trùng, mất mạng thử lại cùng key, tải lại còn bộ thẻ và tiến độ | cờ `saving`, `pending` có key | Mock: lỗi mạng giữ thẻ, "Thử lưu lại" ghi một lần. **API thật: chấm 3 thẻ, tải lại trang, mở lại thì tiếp tục đúng thẻ 4 với 3/10 đã đánh giá** (3 lượt chấm trong DB) | Mất mạng thật trên API thật |
| RUI-06 trạng thái trống, đang tải, lỗi, hết phiên có nội dung và hành động; mock chỉ để phát triển | từng màn hình | Trình duyệt: đang tải, chưa có mục tiêu/bộ thẻ, lỗi khi API thiếu; bundle không chứa mock | Hết phiên đăng nhập giữa chừng (đường 401) |
| RUI-07 thống kê theo đánh giá cuối, không vượt tổng | `deckProgress` | Test; mock: ôn lại thẻ chưa nhớ giữ 5/5; API thật: số liệu tiến độ đúng sau tải lại | Số liệu khi hai tab chấm cùng một thẻ |

## 5. Giới hạn

- **Gemini chưa được thử:** máy kiểm thử không có khóa. Khi không có khóa hoặc Gemini lỗi chung chung, mã backend (của Người 3) chuyển sang bộ tạo thẻ dự phòng theo heuristic và vẫn trả 201, thay vì báo `AI_UNAVAILABLE` như hợp đồng (RBE-04, E2E-08). Thẻ dự phòng chất lượng thấp, có thẻ cắt giữa chữ. Giao diện chưa gặp được đường báo lỗi AI từ máy chủ thật vì điều này. Đây là việc ở phía backend recall, chưa sửa.
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

