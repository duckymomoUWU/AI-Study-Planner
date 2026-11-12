# API contract v1 — cần duyệt D1, sau đó dùng chung

**Tất cả endpoint trong tài liệu này là MỚI, chưa có trong app.** Phân công đã chốt; chi tiết dưới đây là baseline đề xuất để team/PO duyệt trước triển khai. Không để FE tự đổi tên field khi BE chưa đồng ý.

## A. Quy ước chung

- Base `/api/v1`, JSON camelCase, UUID cho ID. Session cookie hiện có, không thêm JWT. Owner lấy từ `AppUserPrincipal`, không nhận `userId`/email owner từ client.
- GET không có CSRF; POST/PATCH/DELETE phải có `X-CSRF-TOKEN` lấy từ `/api/v1/auth/csrf`; `credentials: 'same-origin'`. URL local `http://127.0.0.1:5173`, qua Vite proxy `/api`.
- Một resource `{data:{...}}`; list phân trang `{data:[...],meta:{page,limit,totalItems,totalPages}}`. page mặc định 1, limit 20, tối đa 100. Không có active session dùng `{data:{session:null}}`, không dùng `data:null` bị wrapper bỏ qua.
- Lỗi `{error:{code,message,details?:[{field,issue}],context?:{...}}}`. FE xử lý theo code/status; message dành người đọc. Không lộ SQL/stack/token/nội dung tài liệu trong lỗi/log.
- Timestamp ISO UTC; ngày học giữ `YYYY-MM-DD`. **Ngoại lệ thời lượng cho Timer:** đo bằng integer **seconds**, để không làm tròn/đếm sai phiên ngắn. Quỹ lập lịch/task vẫn là integer minutes như hiện tại. FE hiển thị mm:ss, không tự quy đổi giây sang phút rồi gửi lại.
- Body strict, field lạ/sai kiểu/null không cho phép → 400. Tài nguyên người khác hoặc không tồn tại → cùng 404. Thiếu session 401; CSRF sai 403.
- POST tạo resource: 201 lần đầu; replay hợp lệ 200. Action/PATCH trả 200. DELETE trả 204 không body. Chỉ các endpoint ghi rõ yêu cầu Idempotency-Key mới áp receipt.
- `Idempotency-Key: <UUID>`: cùng owner/key/payload trả kết quả cũ; payload khác trả 409 IDEMPOTENCY_CONFLICT. FE giữ key khi retry cùng lần gửi; đổi thao tác/nội dung thì tạo key mới. Không dùng một key cho cả start và finish.
- Ghi version dùng expectedVersion; 409 thì tải lại trước khi gửi mới. Validation thất bại không tăng version hoặc thay đổi dữ liệu.

## B. Phiên học — Người 2 backend, Người 1 client/UI

### B1. Trạng thái và quy tắc thời gian

`RUNNING → PAUSED → RUNNING`; RUNNING/PAUSED có thể FINISH → COMPLETED hoặc CANCEL → CANCELLED. Trạng thái kết thúc không quay lại running. Tối đa một RUNNING/PAUSED mỗi tài khoản.

Pomodoro đề xuất 25 phút = 1500 giây. Nghỉ 5 phút là đồng hồ UI độc lập, không lưu thành focus session. Chưa làm stopwatch/tùy chỉnh chu kỳ.

Backend lưu accumulatedFocusSeconds khi pause/action; khi đang chạy, focusedSeconds hiệu dụng = min(1500, accumulated + thời gian từ lastResumedAt đến serverNow). remainingSeconds = max(0,1500 − focusedSeconds). PAUSED không cộng theo giờ thực. Server không nhận elapsedSeconds/clientStartedAt để tính công.

GET tính snapshot nhưng không tự cộng nhiều record hoặc ghi FINISH. Khi hết focus, trả focusComplete=true, remainingSeconds=0; UI gửi FINISH idempotent. Sau đóng tab, số giây vẫn tối đa 1500, không cần cron để ghi công. Kết thúc sớm lưu số giây thực tế server tính. CANCELLED không tính vào tổng, dù có thể giữ số giây đã chạy trong history để giải thích.

Session DTO thống nhất:

```json
{
  "id":"11111111-1111-4111-8111-111111111111",
  "taskId":"22222222-2222-4222-8222-222222222222",
  "goalId":"33333333-3333-4333-8333-333333333333",
  "taskTitle":"Ôn kiểm thử hộp đen",
  "subject":"Kiểm tra phần mềm",
  "status":"RUNNING",
  "plannedFocusSeconds":1500,
  "focusedSeconds":120,
  "remainingSeconds":1380,
  "focusComplete":false,
  "startedAt":"2026-11-11T02:00:00Z",
  "lastResumedAt":"2026-11-11T02:00:00Z",
  "endedAt":null,
  "serverNow":"2026-11-11T02:02:00Z",
  "version":0
}
```

`lastResumedAt` null khi paused/terminal; endedAt chỉ có khi terminal. taskId/goalId có thể null sau khi đối tượng bị xóa; snapshot tên vẫn còn. Không trả owner ID. focusedSeconds/remainingSeconds là snapshot tại serverNow, không phải con số client được phép ghi.

| Method + route mới | Request | Data thành công |
| --- | --- | --- |
| GET `/study-sessions/active` | Không body | `{session: Session hoặc null}` |
| POST `/study-sessions` | `{taskId}` + Idempotency-Key | Session; plannedFocusSeconds server đặt 1500 |
| GET `/study-sessions/{sessionId}` | Không body | Session |
| POST `/study-sessions/{sessionId}/actions` | `{action,expectedVersion}` + Idempotency-Key | Session sau chuyển trạng thái |
| GET `/study-sessions?taskId=&page=1&limit=20` | taskId tùy chọn | List Session, `startedAt DESC,id ASC`; chỉ owner |
| GET `/goals/{goalId}/study-time` | Goal phải thuộc owner | `{completedSessionCount,totalFocusSeconds}` |

Action chỉ là PAUSE/RESUME/FINISH/CANCEL. PAUSE chỉ khi RUNNING; RESUME chỉ khi PAUSED và còn giây; FINISH/CANCEL chỉ khi active. Request key được kiểm tra/replay trước version để retry sau timeout không bị báo stale giả. Hai tab có key khác vẫn phải kiểm tra version; tab thua nhận 409 rồi tải lại.

Lỗi riêng: 409 SESSION_ALREADY_ACTIVE (`context.activeSessionId` của chính owner), SESSION_VERSION_CONFLICT, SESSION_STATE_CONFLICT, FOCUS_FINISH_REQUIRED. FINISH/CANCEL không tạo task mới, không đổi task.completed, không tác động daily budget/Google Calendar.

## C. Tài liệu và flashcard — Người 3 backend, Người 2 client/UI

### C1. Nguồn tài liệu

Chọn đúng một dạng:

```json
{"goalId":"33333333-3333-4333-8333-333333333333","title":"Chương kiểm thử","content":"Nội dung do người học cung cấp…"}
```

```json
{"goalId":"33333333-3333-4333-8333-333333333333","title":"Chương kiểm thử","fileName":"chuong-1.pdf","mimeType":"application/pdf","base64":"<base64 file được chọn>"}
```

Giới hạn đề xuất: title 1..120 ký tự; text 1..30.000; PDF ≤5 MiB và ≤100 trang. PDF chỉ xử lý text, không OCR. Extract vượt 30.000 ký tự thì 422 SOURCE_TOO_LONG, yêu cầu chọn phần ngắn/dán đoạn cần ôn; **không âm thầm cắt đầu tài liệu**. Không nhận file URL bên ngoài, không dùng filename làm đường dẫn lưu file.

Material DTO: `{id,goalId,title,fileName,kind:"TEXT"|"PDF",pageCount,characterCount,createdAt}`. Không trả toàn bộ text ở list. Với text, fileName và pageCount null. Backend lưu text/page để làm nguồn dẫn; không giả định syllabus upload hiện tại đã lưu file để tái sử dụng. Không cần lưu file PDF gốc trong MVP.

### C2. Deck, card và dẫn nguồn

Deck DTO: `{id,goalId,materialId,title,status:"DRAFT"|"ACTIVE",cardCount,version,createdAt}`. Title 1..120. Generation count integer 5..20, mặc định 10. Key replay không tạo thêm deck.

Card DTO:

```json
{
  "id":"44444444-4444-4444-8444-444444444444",
  "deckId":"55555555-5555-4555-8555-555555555555",
  "question":"Mục đích của kiểm thử hộp đen là gì?",
  "answer":"Kiểm tra hành vi đầu vào–đầu ra mà không dựa vào cấu trúc bên trong.",
  "source":{"materialId":"66666666-6666-4666-8666-666666666666","page":2,"quote":"Kiểm thử hộp đen tập trung vào đầu vào và đầu ra của hệ thống."},
  "editedByUser":false,
  "version":0,
  "lastRating":null,
  "lastReviewedAt":null,
  "reviewCount":0
}
```

Question 1..300 ký tự, answer 1..2000, quote 1..500. PDF page 1-based trong khoảng tài liệu, text page=null. Server kiểm tra quote thật sự tồn tại trong page/text gốc theo cách chuẩn hóa whitespace có tài liệu rõ; không chấp nhận AI tự ghi “Nguồn: trang 2” mà không đối chiếu. Quote có thật **không bảo đảm đáp án đúng về ngữ nghĩa**; vẫn bắt người học xem/sửa/xác nhận.

Sửa question/answer chỉ ở DRAFT; editedByUser=true và tăng card version **và deck version** để activation cũ không chấp nhận nội dung vừa đổi. Không sửa source metadata từ UI. Activate khóa deck và kiểm tra lại tất cả thẻ. Deck ACTIVE là snapshot để ôn, không sửa âm thầm; muốn nội dung khác tạo deck mới. Chỉ ACTIVE mới review.

### C3. Endpoint mới

| Method + route | Request | Data thành công |
| --- | --- | --- |
| POST `/recall/materials` | Một input C1 | Material, 201 |
| GET `/recall/materials?goalId=&page=1&limit=20` | goalId tùy chọn, nếu có phải thuộc owner | List Material |
| POST `/recall/decks` | `{materialId,title,count}` + Idempotency-Key | Deck DRAFT, 201 hoặc replay 200 |
| GET `/recall/decks?goalId=&page=1&limit=20` | goalId tùy chọn | List Deck, createdAt DESC/id ASC |
| GET `/recall/decks/{deckId}` | Không body | `{deck, cards:[Card...]}`, tối đa 20 thẻ nên không phân trang cards |
| PATCH `/recall/cards/{cardId}` | `{question?,answer?,expectedVersion}` | Card mới; không nhận editedByUser/source/deckId |
| POST `/recall/decks/{deckId}/activate` | `{expectedVersion}` | Deck ACTIVE; lặp activate cùng version đã activate có thể trả 200 trạng thái hiện tại, không tạo thẻ mới |
| DELETE `/recall/decks/{deckId}` | Không body | 204, xóa deck/cards/reviews nguyên tử |
| POST `/recall/cards/{cardId}/reviews` | `{rating:"REMEMBERED"|"FORGOT"}` + Idempotency-Key | `{reviewId,cardId,rating,reviewedAt,progress}` |
| GET `/recall/decks/{deckId}/progress` | Không body | `{totalCards,reviewedCards,rememberedCards,forgottenCards}` |

GET deck chứa đáp án để phục vụ lật thẻ; đây là tự học, **không phải bài thi chống xem đáp án bằng devtools**. UI ẩn đáp án trước khi lật và khóa nút đánh giá đến lúc lật. Server chỉ lưu self-rating, không thể khẳng định người dùng thật sự đã nhớ.

Progress dùng **đánh giá cuối của mỗi card**: reviewedCards = rememberedCards + forgottenCards ≤ totalCards. Thẻ chưa ôn không bị tính là quên. Retry cùng review key không tăng reviewCount; đánh giá lại có key mới tăng lịch sử nhưng chỉ thay lastRating.

Xóa goal sẽ cascade material/deck/card/review thuộc goal đó sau xác nhận hiện có. Timer history độc lập, không cascade mất theo goal. Deck xóa không xóa goal/task/phiên học. MVP chưa có endpoint xóa material riêng hoặc chỉnh nguồn đang được deck dùng.

### C4. Lỗi và dữ liệu khi AI thất bại

| HTTP/code đề xuất | Hành vi UI |
| --- | --- |
| 400 VALIDATION_ERROR | Gắn lỗi đúng field, giữ input |
| 404 NOT_FOUND | Không tìm thấy/không có quyền, không lộ owner |
| 409 DECK_VERSION_CONFLICT / CARD_VERSION_CONFLICT | Tải bản mới, không tự ghi đè |
| 409 DECK_STATE_CONFLICT / MATERIAL_CHANGED | Không review/sửa sai trạng thái; xem lại nguồn/deck |
| 422 SOURCE_NO_TEXT / SOURCE_TOO_LONG | Yêu cầu PDF văn bản hoặc đoạn text ngắn hơn |
| 502 AI_INVALID_RESPONSE | Chưa lưu deck mới; giữ nguồn và các deck cũ |
| 503 AI_UNAVAILABLE | Quota/key/provider không sẵn sàng; cho thử lại, không fake thẻ |
| 504 AI_TIMEOUT | Giữ input và retry cùng key; chống request chồng tạo nhiều deck |

Generate không giữ DB transaction khi gọi Gemini. Trước remote call có thể nhận/kiểm tra receipt đang xử lý theo request key để request lặp không gọi AI vô hạn; sau remote call commit deck/cards/receipt nguyên tử. Request đồng thời cùng key có thể nhận 409 GENERATION_IN_PROGRESS để UI chờ rồi retry. Không tự retry provider nhiều lần trong background.

## D. Fixture và kiểm tra contract

- [session-active.json](fixtures/session-active.json): envelope active session để Người 1 mock.
- [recall-deck.json](fixtures/recall-deck.json): envelope deck để Người 2 mock. Chứa dữ liệu tổng hợp minh họa, không phải trích từ đề cương thật.
- [api-error.json](fixtures/api-error.json): lỗi có structure thống nhất.
- Cùng một fixture phải dùng được trong client test và MockMvc assert. Nếu đổi enum/route/nullability, sửa document + fixture + cả hai test trong cùng PR tích hợp.
- Không coi file contract là OpenAPI đã triển khai. Trong D1, Người 2/3 có thể chuyển sang OpenAPI nhưng nội dung phải khớp bản đã được cả nhóm duyệt; không tạo một bản spec thứ hai khác field.
