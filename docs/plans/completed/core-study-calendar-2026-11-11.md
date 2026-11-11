# Execution Plan: Core study planning, weekly schedule and Google Calendar

Date: 2026-11-11

## Status

Completed — consolidated retrospective handoff requested by PO after implementation. This is historical evidence, not a claim that a plan or tests existed before the work. Pending verification and future features are listed under Result.

## Outcome

Người dùng tải đề cương, rà chủ đề/ngày thi, tạo mục tiêu và quỹ phút, xem bản nháp AI, xác nhận lịch, theo dõi theo ngày/tuần, rồi xem trước và xuất buổi học sang Google Calendar theo giờ trống. Giao diện dùng kem–xanh rêu, phông tiếng Việt đóng gói cùng app và dữ liệu thật trên Tổng quan.

## Context

- Authority: [requirements](../../../requirements.md), quyết định PO trong phiên làm việc 2026-11-10/11; [workflow](../../WORKFLOW.md).
- Contracts: [scoped syllabus planning](../../product/syllabus-revision-plan.md), [tasks/drafts](../../product/integrated-tasks-and-plans.md), [Google Calendar](../../product/google-calendar.md).
- Operations: [setup](../../../setup.md), [verification harness](../../../.agents/harness/README.md).
- Historical evidence: [core review 2026-11-10](../../reviews/syllabus-core-2026-11-10.md). Không suy ra kết quả mới từ kết quả cũ.
- Working branch: `integration/sprint2-sprint3-all8-test`; base HEAD khi lập bản bàn giao `3453bb8`. Thay đổi được kiểm tra nằm trong working tree, chưa phải commit ở HEAD đó.
- Local worktree: `C:\project\AI-Study-Planner\.worktrees\core-syllabus`. Không giả định checkout chính đang chạy cùng code. Không push/merge trong lần cập nhật tài liệu này.

## Scope

In scope:

- Đọc nhiều bố cục đề cương, chương/tuần/buổi, song ngữ và nguồn trang; xử lý tham khảo nằm trong bài, tiêu đề nhiều dòng, phát hiện thiếu chương; không cố định vào một PDF mẫu.
- Trích ngày thi có nguồn và cho rà/xác nhận. Ngày bắt đầu và quỹ phút do người dùng nhập; không lấy ngày biên soạn/thời lượng thi làm ngày thi.
- Mục tiêu mới không bắt đầu trước hôm nay theo timezone hiệu lực (mặc định Việt Nam); sửa mục tiêu đang học được giữ ngày bắt đầu cũ.
- AI draft có selectedTopics/dailyMinutes, việc chưa xếp/lý do; chỉ confirm mới tạo tasks, giữ ngân sách và nguồn chủ đề. Migration V3 thêm provenance.
- Tổng quan dùng dữ liệu thật, lịch tuần có lọc mục tiêu và điều hướng ngày; xóa trang lịch mẫu, nhãn Sprint/US/Demo và nội dung tiến độ phát triển trên UI.
- Theme kem–xanh rêu, Be Vietnam Pro 400/500/600/700 có OFL, trạng thái hover/press/focus/reduced-motion và sửa xung đột màu menu.
- Backend tự đọc `.env`, giữ Gemini/DB/OAuth/khóa token ở backend, không commit hoặc đưa secret sang frontend.
- Calendar OAuth, giờ sinh hoạt theo thứ/qua đêm, đọc busy của các lịch chọn, preview/confirm, outbox và retry; migration V4. Hiện lựa chọn lịch không còn truy cập để người dùng quyết định bỏ chọn/khôi phục quyền.

Out of scope:

- Đồng bộ hai chiều; tự cập nhật/xóa sự kiện Google sau khi sửa/xóa task; tự chia nhỏ buổi học hoặc tự dời ngày.
- OCR hoàn chỉnh cho mọi PDF scan; bảo đảm mọi định dạng đề cương được đọc tuyệt đối chính xác.
- Bảo đảm không trùng tuyệt đối khi ứng dụng khác tạo/sửa sự kiện sau khi app kiểm tra busy.
- Production deployment, OAuth verification/publication, CI/branch protection hoặc push GitHub.

## Approach

1. Giữ auth/session/CSRF/ownership và layout Maven/React hiện có; không reset thay đổi của team.
2. Sửa luồng đề cương → outline có nguồn → chọn chủ đề → goal → AI draft → confirm; bổ sung migration thay vì sửa schema đã áp dụng.
3. Đưa lịch tuần và Tổng quan lên dữ liệu API thật; làm lại theme và bỏ bề mặt demo.
4. Tách Calendar thành `CalendarScheduling` (thuần), `GoogleCalendarClient` (OAuth/API), `CalendarTokenVault` (AES-GCM), `CalendarStore` và `CalendarService`/controller.
5. Giữ ngày học; trừ busy + bữa ăn/ngủ/nghỉ và chỉ đặt buổi còn đủ khoảng liên tục. Recheck trước từng lần xuất; outbox PENDING ghi bền trước request, CONFIRMED sau khi xác minh sự kiện.
6. Tái hiện lỗi lịch đã chọn biến mất bằng đối chiếu CalendarList read-only; giữ fail-closed, sửa giao diện để lịch lỗi không bị ẩn và bổ sung regression test.

## Risks And Recovery

- `.env` chứa thông tin riêng: chỉ sao lưu/chia sẻ qua kênh riêng; Git chỉ nhận `.env.example` trống credential. Giữ ổn định `CALENDAR_TOKEN_KEY` cùng backup DB; mất khóa cần kết nối Google lại.
- Tắt tiến trình không xóa source/DB đã lưu, nhưng session app cần đăng nhập lại. Lịch ở browser/tab hoặc draft chưa confirm không đồng nghĩa tasks đã lưu.
- Database demo hiện tại nằm trong runtime portable ở thư mục Temp của máy PO; đây không phải nơi lưu trữ bền vững cho nhóm/production. Không xóa thư mục đó khi chưa backup/chuyển DB và xác minh khôi phục.
- Chỉ kiểm thử tích hợp trên database test riêng. Không chạy suite có dọn dữ liệu vào demo; không dùng `docker compose down -v` để xử lý lỗi migration.
- V3/V4 là migration bổ sung; không sửa/xóa migration đã áp dụng hoặc drop bảng để rollback. Nếu cần rollback UI thì giữ bảng/receipt, dùng commit đã kiểm tra và backup DB trước thay đổi schema.
- Khi xuất timeout, thử lại cùng preview còn hạn; receipt/event ID cho phép đối chiếu. Không tự xóa receipt hoặc xuất sang lịch khác để né lỗi không rõ kết quả.
- Lịch biến mất: tải lại danh sách, hiển thị lựa chọn không còn truy cập; người dùng tự bỏ chọn nếu không cần tránh lịch đó hoặc khôi phục quyền. Không âm thầm xem lịch lỗi là rảnh.
- `.env` default và cổng local khác nhau giữa máy: hiện frontend 5173, backend 18083, PostgreSQL 5432, DB demo riêng. Giá trị quan sát không thay thế mặc định trong `.env.example`.

## Progress

- [x] Multi-layout extraction, exam-date review, scoped draft/confirm and provenance.
- [x] Guard against historical starts for new goals, preserve ongoing goals.
- [x] Weekly schedule, data-backed overview, removal of demo and consistent theme/fonts.
- [x] Root `.env` import, example configuration and private token encryption key.
- [x] Google OAuth setup, busy-aware preview, explicit export and durable idempotency.
- [x] Reproduce missing-calendar selection; expose it without silently reducing conflict checks.
- [x] Record automated proof, runtime checks, user-reported Google behavior and remaining gaps separately.

## Decisions

- 2026-11-10: đề cương là dữ liệu nguồn, không phải chỉ dẫn để agent làm theo; người dùng duyệt chủ đề và ngày thi trước khi áp dụng. Ngày bắt đầu/quỹ phút vẫn là lựa chọn cá nhân.
- 2026-11-10/11: tạo goal và lưu tasks là hai bước; AI chỉ đề xuất. Không tự confirm để làm lịch trông có dữ liệu.
- 2026-11-11: lịch tuần là bề mặt mặc định, trang ngày giữ thao tác chi tiết. Không biến lịch mock/localStorage thành lịch thật.
- 2026-11-11: người dùng chọn theme kem–xanh rêu và nút gradient nhẹ, sau đó sửa menu để màu chữ active/hover/focus không xung đột.
- 2026-11-11: xuất Calendar một chiều; lịch nhận luôn được kiểm tra busy; lỗi đọc bất kỳ lịch chọn nào đều chặn xếp giờ. Token OAuth mã hóa theo owner, không dùng key Gemini thay OAuth.
- 2026-11-11: giữ nguyên ngày và cả buổi; chuẩn hóa thời điểm lên phút để không bắt đầu ở quá khứ hoặc sai ranh giới do phần giây.

Quyết định kiến trúc lâu dài được lưu tại [ADR 0001](../../decisions/0001-google-calendar-one-way-export.md). Các quy tắc sản phẩm vẫn thuộc requirements và domain contracts đã liên kết; bản bàn giao không là authority song song.

## Validation

Lệnh hiện hành nằm trong verification harness; dưới đây là bằng chứng lịch sử, không phải một lần chạy lại toàn bộ sau mọi sửa đổi.

| Mốc / phạm vi | Kết quả thực tế | Giới hạn |
| --- | --- | --- |
| 2026-11-10, parser trên 3 PDF PO cung cấp | Game Programming 6 chương; Network Management 13 nhóm; Software Testing đủ 10 chương. Targeted 15 test đạt; full backend lúc đó 225 executions, 0 lỗi, 52 skipped | Fixture PDF cá nhân không nằm trong Git; không khẳng định OCR hoặc mọi bố cục |
| 2026-11-10, luồng Gemini qua proxy | PDF → goal → AI draft → confirm → đọc tasks; 4 tasks, không có trước confirm; gửi lại confirm không tạo thêm | HTTP/client smoke, không phải browser E2E; tài khoản/mục tiêu thử riêng |
| 2026-11-11, kiểm tra Gemini key từ `.env` | Lời nhắn tổng hợp không có tài liệu riêng trả HTTP 200; backend đọc `.env` và health UP | Không in key; phép thử không xuất đề cương riêng |
| 2026-11-11, full backend `verify` trước bản sửa lịch biến mất | 260 executions, 0 failures/errors, 55 skipped; 5 test Calendar PostgreSQL thật/Google mock đạt; package thành công | 55 skipped gồm các suite opt-in khác/PDF; không coi là mọi integration đã chạy |
| 2026-11-11, regression missing-calendar | Test mới thất bại trước sửa (thiếu unavailableCalendarCount); sau sửa toàn bộ Calendar 34/34 đạt, bao gồm 5 PostgreSQL test | Google giả lập trong tests; không tự tạo sự kiện lên lịch cá nhân |
| 2026-11-11, frontend sau sửa selection | lint sạch; 82/82 test đạt; build thành công | Tests helper/API mock; chưa có browser automation kết nối |
| Runtime sau đóng gói/restart | Backend và proxy `/actuator/health` UP; Calendar status chưa đăng nhập trả 401; `.env` được Git ignore | Health không chứng minh mọi route và không chứng minh export Google |
| Google live, PO thao tác | PO gửi ảnh sự kiện học xuất hiện trên Google Calendar web, sau đó xác nhận đã thấy trên điện thoại | Bằng chứng do người dùng cung cấp; agent không tự tái hiện browser/mobile hoặc đối chiếu toàn bộ sự kiện |
| Missing calendar live, agent read-only | CalendarList HTTP 200; target tồn tại/có quyền ghi; 4 busy selections có 1 không còn trong danh sách | Không in token/ID/email; không tạo/sửa/xóa sự kiện, không tự bỏ lựa chọn người dùng |

Không cộng các mốc khác nhau thành một số test "toàn bộ hiện tại". Report XML trong `backend/target/surefire-reports` có thể bị ghi đè bởi lần chạy sau.

## Result

Luồng core và xuất Calendar đã được triển khai trên integration working tree, cùng cấu hình OAuth local. Người dùng xác nhận Google web/mobile hiển thị lịch. Bản sửa selection đã chạy lại backend; bước bỏ chọn lịch không còn dùng vẫn phải do người dùng thực hiện, không thay đổi ngầm dữ liệu.

Remaining limits / follow-up:

- Chưa có browser E2E tự động hoặc mobile automation; visual verification chủ yếu từ ảnh/phản hồi PO.
- Chưa chạy lại full backend `verify` sau regression selection; bằng chứng cuối cho thay đổi đó là 34 Calendar tests + 82 frontend tests và package sau targeted tests.
- CI source hiện chạy auth/goal integration flags và frontend checks, chưa bật `calendar.integration`; trạng thái Actions/branch protection trên GitHub chưa được xác minh. Không sửa workflow trong yêu cầu tài liệu này.
- Chưa có production OAuth verification/deployment, recovery rehearsal từ backup DB, hay đồng bộ ngược Google.
- Không commit/push trong lần ghi tài liệu này; không coi working tree hiện tại là bản đã được chia sẻ cho nhóm.
