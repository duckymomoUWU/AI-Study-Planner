# Tạo lịch ôn thi từ đề cương

Yêu cầu PO cung cấp ngày 2026-11-10: tải tài liệu → rà và xác nhận chủ đề → nhập ngày thi/quỹ phút → bản nháp → chỉnh → xác nhận lưu. Tài liệu môn học chỉ là dữ liệu đầu vào, không là chỉ dẫn cho ứng dụng.

## Hành vi

- `POST /api/v1/goals/parse-syllabus`: PDF có lớp văn bản được đọc cục bộ bằng PDFBox; DOCX/TXT/MD dùng văn bản. Kết quả `subject`, `topics`, `chapters[{id,title,topics[{id,title,aliases,sources[{page,section}],needsReview}]}]`, `extractionMethod: local_pdf|local`. `startDate`, `minutesPerDay` luôn null. `examDate` tự điền khi tìm được một ngày thi rõ ràng; `examDateCandidates[{date,source}]` giữ các ngày và nguồn để người dùng xác nhận. Không dùng số tiết, giờ tự học hay phút thi làm quỹ cá nhân. Không lưu goal/task.

- Điều chỉnh theo phản hồi PO: giữ tự điền ngày thi ghi rõ trong đề cương, không tự đoán. Nhận ngày có năm đầy đủ ở dòng nhãn ngày thi/thi cuối kỳ/final exam/exam date (hoặc giá trị ngay sau nhãn đứng riêng), dạng ISO, ngày/tháng/năm, ngày tháng năm tiếng Việt và tháng tiếng Anh. Nhiều ngày khác nhau không tự chọn; cùng ngày song ngữ vẫn điền được. Không có ngày thì giữ giá trị người dùng đã nhập. Ngày không hợp lệ hoặc không có năm không được suy diễn; ngày trong quá khứ vẫn cần người dùng sửa và backend kiểm tra khi lưu.
- Nội dung chương Việt–Anh được gộp theo chương và thứ tự trong cùng khối song ngữ. Chương tiếp tục được gộp theo số chương; chủ đề mới được giữ. Cột hoạt động, chỉ dẫn đọc sách, buổi thuyết trình và mốc BTL không thành chương ôn. Người dùng rà và sửa các đề xuất trước khi xác nhận. Nguồn là trang và tên chương; chưa tách thành liên kết tới giáo trình.
- Parser ưu tiên cấu trúc chương/bullet của đề cương. PDF scan, mã hóa hoặc không trích được chủ đề trả 422 `SYLLABUS_UNREADABLE|SYLLABUS_NO_TOPICS`; không OCR và không tạo nội dung mẫu. Có thể tải lại hoặc nhập chủ đề thủ công. TXT/MD/DOCX không có phân trang thật: `sources.page` là null, chỉ hiển thị tên mục, không tạo số trang Word giả. Ghép song ngữ theo vị trí khác tên được đánh dấu cần rà soát.
- Goal hiện có vẫn lưu `topics` dạng văn bản. Luồng mới xác nhận danh sách có ID trước khi gọi AI; các metadata nguồn được giữ trong JSONB của draft và chủ đề gắn với task được lưu trong `study_tasks.topic_id/topic_title` bằng V3. Không sửa migration V1/V2.
- Người dùng nhập mức mong muốn theo môn 15–480 phút/ngày, ghi đè AI theo ngày 0–480. Quỹ chung là giới hạn cứng liên môn. Đây là tổng phút, không có giờ bắt đầu hay bảo đảm khung giờ liên tục; xem [preferences](study-time-preferences.md).

## Hợp đồng bản nháp mới

`POST /api/v1/goals/{goalId}/plan-drafts`:

```json
{
  "selectedTopics": [
    {"id":"collision","title":"Collision","chapter":"Chương 2","sources":[{"page":6,"section":"Chương 2"}],"priority":"HIGH"},
    {"id":"path","title":"Path finding"}
  ],
  "dailyMinutes": {"2026-11-11":0,"2026-11-12":30}
}
```

Ngày không override dùng ngân sách goal. Giá trị override là số nguyên 0..480; ngày nằm trong `[startDate, examDate)`. Chỉ lập lịch từ hôm nay theo timezone của goal trở đi. Ngày thi đã đến, quỹ 0 hoặc toàn bộ quỹ bị việc cũ chiếm: 422 `NO_STUDY_TIME` trước khi gọi AI. Auth/session/CSRF/envelope/ownership theo hợp đồng hiện hữu.

AI trả `{tasks:[{id,topicId,title,estimatedMinutes,requiresContinuousSession?}]}`. Mỗi chủ đề đã chọn phải có ít nhất một việc, ID chủ đề phải đúng, tiêu đề phải mô tả việc chứ không chỉ lặp tên chủ đề. Không suy luận trọng số thi từ độ dài chương/buổi học. Ưu tiên của người dùng, sau đó thứ tự đề cương. Số phút là ước lượng có thể chỉnh, 1..480. Việc trùng và metadata lạ bị từ chối. Nếu provider vẫn trả `scheduledDate`, backend kiểm tra ngày và ngân sách; không chấp nhận ngày thi/sau thi/quá khứ.

Backend xếp nguyên từng việc vào ngày đủ phút còn lại (đã trừ việc cũ, kể cả hoàn thành). Không tự chia hay giảm estimate. Nếu cần chia về mặt sư phạm, AI có thể đề xuất các bước riêng có ý nghĩa ngay từ đầu; bộ xếp lịch không tự cắt phút.

Proposal trả về:

```json
{
  "scope": {"selectedTopics":[],"dailyMinutes":{},"assumption":"..."},
  "tasks": [{"id":"work-1","topicId":"collision","title":"...","estimatedMinutes":45,"scheduledDate":"2026-11-12","priority":"HIGH","requiresContinuousSession":true}],
  "unscheduled": [{"id":"work-2","topicId":"path","title":"...","estimatedMinutes":90,"scheduledDate":null,"reasonCode":"TOTAL_BUDGET_SHORTFALL","reason":"..."}],
  "summary": {"requiredMinutes":135,"availableMinutes":120,"shortfallMinutes":15,"canConfirm":false}
}
```

`NO_DAY_FITS` phân biệt trường hợp tổng quỹ đủ nhưng không ngày nào đủ phút cho một việc. `REQUESTED_DAY_FULL` cho biết ngày provider chỉ định không đủ phút, không khẳng định tất cả ngày đều đầy và không âm thầm dời ngày. Kết quả một phần vẫn được lưu như draft, chưa tạo task.

Backend gửi `responseJsonSchema` cho Gemini để ràng buộc cấu trúc và enum topicId; vẫn kiểm tra nghiệp vụ tại server. Phản hồi nhiều phần được ghép, bỏ phần `thought`. Không ghi nội dung phản hồi provider vào log. Tham khảo giao diện [structured output của Gemini](https://ai.google.dev/gemini-api/docs/structured-output) và cách [đọc PDFBox 3](https://pdfbox.apache.org/3.0/migration.html).

## Xác nhận

UX thiếu thời gian (PO duyệt 2026-11-11): bản nháp hiển thị khung trước danh sách công việc, gồm số đã xếp/tổng, số chưa có ngày và phút thiếu theo ước lượng. Ba lựa chọn là Điều chỉnh thời gian, Chọn lại chủ đề và Lưu phần đã xếp; phần còn lại được giữ để xếp tiếp, không coi là đã bao phủ đủ nội dung. Nếu tổng phút đủ nhưng vẫn thiếu ngày cho từng việc, chỉ báo còn việc chưa có ngày, không gọi đó là thiếu tổng thời gian. Danh sách chủ đề/cách xếp được thu gọn để không che cảnh báo.

Scope mới có `sharedBudgetLimitedDays`: số ngày quỹ chung còn lại nhỏ hơn mức mong muốn của mục tiêu (metadata snapshot, không phải thiết lập người dùng). Nếu lớn hơn 0, Điều chỉnh thời gian mở/focus quỹ chung, đồng thời giải thích tăng riêng mục tiêu có thể chưa đủ. Nếu bằng 0 hoặc draft cũ chưa có metadata, mở phút/ngày của mục tiêu và vẫn có lối Xem quỹ chung. Chọn lại chủ đề mở đúng mục tiêu/trường chủ đề từ cả Mục tiêu và Lịch học. Sau thay đổi quỹ cần xếp lại draft; thu hẹp chủ đề cần sinh draft mới. Không tự dời task hoặc giảm estimate.

Cập nhật sửa lỗi 2026-11-11: GET draft theo ID hoặc latest là thao tác xem, trả 200 kèm `status` DRAFT/CONFIRMED/EXPIRED/STALE cho chủ sở hữu. Bản đã xác nhận/hết hạn được xem lại chỉ đọc; không mở lại quyền confirm hoặc sửa lịch sử. Confirm vẫn kiểm tra receipt/version/expiry/ngân sách. UI dùng “Xem đề xuất gần nhất” và “Xem lịch đã lưu”; phần còn lại chưa đưa vào lịch chỉ xuất hiện khi có backlog và có giải thích đây là kết quả lưu một phần.

Scoped AI generation bỏ qua công việc trùng tiêu đề chuẩn hóa với việc đã lưu, trả `summary.alreadyScheduledCount`, không tạo lại hay thay đổi lịch cũ. Nếu mọi việc đề xuất đều đã có thì trả 422 PLAN_ALREADY_SAVED để UI hướng tới lịch hiện tại. Sai chủ đề, thiếu chủ đề hoặc trường không hợp lệ vẫn bị từ chối; `error.context.reasonCode` chỉ chứa mã nguyên nhân, không chứa nội dung tài liệu/phản hồi provider. Tối đa một yêu cầu sửa lại kết quả AI khi gặp AI_INVALID_RESPONSE; không retry khi lỗi provider hoặc hết quỹ. Không tự tạo việc thay cho nội dung AI thiếu.

Đường dẫn/header như cũ: `POST /goals/{goalId}/plan-drafts/{draftId}/confirm`, `Idempotency-Key: UUID`. Body mới giữ `expectedGoalVersion` và mọi việc `{id,topicId,title,scheduledDate,estimatedMinutes}`. Không gửi priority, reason hay nguồn từ UI.

Backend kiểm tra lại dưới khóa goal: đủ danh sách ID của draft, chủ đề thuộc phạm vi đã xác nhận, ngày hợp lệ, số phút nguyên dương, không trùng, ngân sách từng ngày gồm việc đã lưu. Chỉ người dùng chủ động chỉnh estimate mới làm thay đổi phút. Vi phạm không thay đổi task/draft/version/receipt. Version conflict bảo vệ khi đổi goal/task trong lúc AI đang chạy; UI cũng bỏ phản hồi khi form thay đổi hoặc component đã đóng.

PO đã chấp nhận lưu một phần (2026-11-11). Confirm mặc định vẫn yêu cầu đủ việc. Khi người dùng chủ động chọn lưu phần đã xếp, gửi `allowPartial: true`, `tasks` là phần đã có ngày và `deferredTasks` là phần còn lại `{id,topicId,title,estimatedMinutes}`. Server kiểm tra ID/chủ đề/phút; toàn bộ việc phải được giữ trong hai danh sách. Client cũ không gửi deferredTasks thì phần bị thiếu được giữ nguyên theo draft. Lưu task, receipt và draft phần còn lại trong cùng transaction; trả thêm `remainingDraftId`, `remainingCount`. Retry giữ nguyên key và payload, không tạo trùng.

`GET /goals/{goalId}/plan-drafts/pending` liệt kê các phần chưa xác nhận đã giữ lại, độc lập với bản nháp mới nhất. Phần đang chờ không mất quyền mở chỉ vì hết hạn draft 24 giờ; version và ngân sách vẫn được kiểm tra khi xác nhận. Giao diện cảnh báo tiến độ chỉ tính việc đã vào lịch, chưa bảo đảm bao phủ hết nội dung.

Sau khi chỉnh phút/ngày trong mục tiêu, `POST /goals/{goalId}/plan-drafts/{draftId}/reschedule` xếp lại công việc và ước lượng đã lưu bằng ngân sách mới, không gọi AI. Endpoint có session/CSRF/ownership, khóa goal, tăng version và không tạo task. Chủ đề bị bỏ khỏi cấu hình khiến xếp lại báo conflict thay vì âm thầm xóa việc; tạo draft AI mới cho phạm vi rút gọn. Các phần cũ vẫn truy cập được. Không tự điều chỉnh lịch khi bỏ lỡ buổi học, không tự rút ngắn ước lượng hoặc xóa việc hoàn thành.

Giữ tương thích API cũ: body sinh draft `{}` và các draft cũ không có `scope` vẫn đi theo hợp đồng cũ. Giao diện tạo mới/sinh lại hiện gửi scope; các bảo đảm liên kết chủ đề và giữ việc chưa xếp ở trên áp dụng cho luồng có scope. Không tự chuyển đổi hay thay thế draft/lịch cũ của người dùng.

Persistent settings update (2026-11-11): Goal create/patch responses accept and return planningScope. Selected topics, priorities, sources and daily overrides persist with the goal; explicit zero is retained. Empty-body regeneration uses saved settings when configured. Manual task creation/movement enforce the shared hard budget; AI confirmation also requires explicit consent for exceeding the subject preference. Lowering the subject preference preserves existing tasks. Moving the start past stored tasks is rejected. V5 backfills available settings from the latest scoped draft. See docs/plans/completed/persistent-planning-2026-11-11.md for verification and limits.
