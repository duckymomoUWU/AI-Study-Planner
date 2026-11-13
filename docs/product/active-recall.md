# Active Recall

## Phạm vi và luồng

Flashcard hỏi–đáp từ tài liệu của người học, tự đánh giá Nhớ/Chưa nhớ. Không AI chấm
câu trả lời, spaced repetition hoặc tự cân lịch theo mức nhớ.

Giao diện `#recall` gọi API thật mặc định:

1. Chọn mục tiêu, dán text hoặc tải PDF có chữ và lưu nguồn.
2. Chọn nguồn đã lưu, đặt tên/số thẻ và yêu cầu sinh bản nháp.
3. Xem câu hỏi, đáp án và đoạn trích; sửa question/answer nếu cần.
4. Xác nhận dùng bộ thẻ: DRAFT → ACTIVE.
5. Khi ôn, xem câu hỏi trước, mở đáp án rồi tự đánh giá. Cuối lượt có ôn lại thẻ chưa nhớ hoặc tất cả.
6. Mở lại còn bộ thẻ và đánh giá cuối; xóa deck có xác nhận, không xóa nguồn/goal/task.

## Nguồn và thẻ

- Nguồn phải gắn goal của owner. Title 1–120 ký tự; đúng một trong text hoặc PDF Base64.
- Text tối đa 30.000 ký tự. PDF tối đa 5 MiB/100 trang, có lớp văn bản; không OCR.
  Trích vượt giới hạn/rỗng thì báo lỗi, không âm thầm cắt hoặc tạo nội dung thay thế.
- Lưu text/page và metadata, không cần lưu PDF gốc. Danh sách không trả toàn bộ text.
- Sinh 5–20 thẻ, mặc định 10. Question 1–300, answer 1–2000, quote 1–500 ký tự.
- Quote phải có trong trang/text nguồn sau chuẩn hóa whitespace. PDF page 1-based;
  không nhận trích đúng nội dung nhưng ghi sai trang. Text dùng page=null.
- Trích dẫn có thật chưa bảo đảm đáp án đúng nghĩa; người học vẫn cần duyệt.
- Chỉ sửa thẻ DRAFT; tăng card và deck version, đánh dấu editedByUser. ACTIVE là bản để ôn.

## API

Các đường nối sau `/api/v1`, dùng session/CSRF khi ghi.

| Method + route | Request | Data |
| --- | --- | --- |
| POST `/recall/materials` | `{goalId,title,content}` hoặc `{goalId,title,fileName,mimeType,base64}` | Material, 201 |
| GET `/recall/materials?goalId=&page=1&limit=20` | Lọc goal tùy chọn | Material[] + meta |
| POST `/recall/decks` | `{materialId,title,count}` + Idempotency-Key UUID | Deck DRAFT |
| GET `/recall/decks?goalId=&page=1&limit=20` | Lọc goal tùy chọn | Deck[] + meta |
| GET `/recall/decks/{id}` | Không body | `{deck,cards}` |
| PATCH `/recall/cards/{id}` | `{question?,answer?,expectedVersion}` | Card |
| POST `/recall/decks/{id}/activate` | `{expectedVersion}` | Deck ACTIVE |
| DELETE `/recall/decks/{id}` | Không body | 204 |
| POST `/recall/cards/{id}/reviews` | `{rating:REMEMBERED hoặc FORGOT}` + Idempotency-Key | Review + progress |
| GET `/recall/decks/{id}/progress` | Không body | `{totalCards,reviewedCards,rememberedCards,forgottenCards}` |

Material DTO: `id,goalId,title,fileName,kind,pageCount,characterCount,createdAt`.
Deck DTO: `id,goalId,materialId,title,status,cardCount,version,createdAt`.
Card DTO: `id,deckId,question,answer,source:{materialId,page,quote},editedByUser,version,
lastRating,lastReviewedAt,reviewCount`.

Create deck hiện trả 201 cả khi replay; không dùng HTTP status để suy ra deck mới.
Key/payload giống nhau trả cùng deck, không chèn bản sao; payload khác trả 409.
Chỉ ACTIVE mới review. Progress dùng đánh giá cuối mỗi thẻ: reviewed = remembered +
forgotten ≤ total. Retry review không cộng lần nữa; đánh giá mới giữ lịch sử và thay lastRating.

## Lỗi và an toàn dữ liệu

- 400 đầu vào/phân trang sai; 401 thiếu session; 404 tài nguyên không có/không thuộc owner.
- 409 DECK_VERSION_CONFLICT, CARD_VERSION_CONFLICT, DECK_STATE_CONFLICT hoặc IDEMPOTENCY_CONFLICT.
- 422 SOURCE_NO_TEXT/SOURCE_TOO_LONG; 502 AI_INVALID_RESPONSE; 503 AI_UNAVAILABLE; 504 AI_TIMEOUT.
- AI lỗi vẫn giữ nguồn/deck cũ. Không tự sinh thẻ heuristic khi thiếu key hoặc provider lỗi.
- Không giữ DB transaction khi chờ Gemini. Deck/cards/receipt lưu nguyên tử, lỗi receipt phải rollback;
  owner lock và recheck receipt ngăn lưu trùng khi request đồng thời.
- Xóa goal cascade tài liệu/deck/card/review; không xóa lịch sử Timer.

## Code và kiểm chứng

Backend: `com.studyplanner.recall`, migration V8. Frontend: `ActiveRecallPage`,
`components/recall`, `lib/recall.js`. Gemini lấy cấu hình từ `.env` backend.
`?recallMock=1#recall` chỉ là opt-in phát triển, không dùng cho nghiệm thu API thật.

[Tích hợp và test DB](../plans/completed/dev3-recall-integration-2026-11-13.md),
[ghép UI](../plans/completed/dev2-recall-timer-integration-2026-11-13.md).
Lần kiểm tra local ngày 13/11 đã tạo 5 thẻ bằng Gemini thật, activate và lưu đánh giá.
Chưa coi đó là đánh giá chất lượng mọi tài liệu hoặc kiểm chứng đầy đủ mobile/offline.
