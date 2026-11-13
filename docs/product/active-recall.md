# Ôn tập chủ động (flashcard): giao diện và client

**Trạng thái (2026-11-12):** giao diện `#recall`, client `lib/recall.js` và mock phát triển đã hiện thực trên nhánh `feat/study-sessions-recall-ui`. **API `/api/v1/recall` chưa có** (việc của Người 3), nên chưa có kiểm tra nào với máy chủ thật. Chưa gộp vào `main`. Hợp đồng API là mục C của bộ giao việc nhóm (`03-API-CONTRACT.md`, ngoài repo), đang là đề xuất chờ nhóm và PO duyệt.

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

| Tiêu chí | Hiện thực | Đã chạy | Chưa kiểm chứng |
| --- | --- | --- | --- |
| RUI-01 chọn mục tiêu thật, PDF hoặc dán, kiểm đầu vào, hiện tên nguồn | `SourceForm`, `validate*` | Test giới hạn; trình duyệt với mock: dán văn bản, báo lỗi trống, hiện nguồn đã lưu | Chọn tệp PDF thật trong trình duyệt; tải lên API thật |
| RUI-02 lưu nguồn và tạo thẻ là hai trạng thái, lỗi AI không mất nguồn, thử lại không tải thêm nguồn | `GenerateForm`, key giữ theo lần thử | Test mock (lỗi rồi thử lại cùng key chỉ 1 bộ thẻ); trình duyệt: lỗi 503 giữ nguồn, "Thử tạo lại" thành công | Timeout và 502 thật của Gemini |
| RUI-03 bản nháp có hỏi, đáp, nguồn; sửa; xác nhận; không báo đang dùng trước khi kích hoạt thành công | `DeckEditor` | Trình duyệt với mock: sửa thẻ, nhãn đã sửa, xác nhận chuyển sang "Đang dùng" | Phiên bản thật khi sửa đồng thời |
| RUI-04 hỏi trước, "Xem đáp án" mới được đánh giá, gọi là tự đánh giá | `StudyDeck` | Trình duyệt: nút Nhớ/Chưa nhớ chỉ có sau khi lật | Kiểm bằng bàn phím và trình đọc màn hình |
| RUI-05 chặn bấm trùng, mất mạng thử lại cùng key, tải lại còn bộ thẻ và tiến độ | cờ `saving`, `pending` có key | Trình duyệt: lỗi mạng giữ thẻ, "Thử lưu lại" ghi một lần; test mock đánh giá một lần | **Tải lại trang còn dữ liệu** (mock mất khi tải lại, cần API thật) |
| RUI-06 trạng thái trống, đang tải, lỗi, hết phiên có nội dung và hành động; mock chỉ để phát triển | từng màn hình | Trình duyệt: đang tải, chưa có mục tiêu/bộ thẻ, lỗi khi API thiếu; bundle không chứa mock | Hết phiên đăng nhập giữa chừng (đường 401) |
| RUI-07 thống kê theo đánh giá cuối, không vượt tổng | `deckProgress` | Test; trình duyệt: ôn lại thẻ chưa nhớ giữ 5/5 đã đánh giá | Số liệu từ máy chủ thật |

## 5. Giới hạn

- Chưa chạy với API recall thật nên mọi kết quả ở trên là với mock; mock tuân theo hợp đồng nhưng không chứng minh máy chủ làm đúng.
- Chưa có test thành phần React (repo chưa có công cụ), phần giao diện kiểm bằng test logic thuần và thao tác thủ công trong trình duyệt ở 1024 và 360 px.
- Trang đã thêm route `#recall` và một nút sidebar vào `App.jsx`; Người 1 sở hữu `App.jsx` nên cần gộp thống nhất.
- Lúc App đang kiểm tra đăng nhập, trang có thể hiện màn "Đăng nhập" thoáng qua vì App chưa truyền trạng thái đang kiểm tra.
