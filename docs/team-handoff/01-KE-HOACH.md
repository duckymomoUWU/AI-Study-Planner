# Kế hoạch triển khai và phối hợp

## 1. Mục tiêu demo cuối đợt

Khách mở landing → đăng ký/đăng nhập → chọn công việc đã có → học bằng Pomodoro → lưu thời gian thực tế. Người dùng chọn môn → tải PDF văn bản hoặc dán nội dung → AI tạo bản nháp flashcard → xem/sửa/xác nhận → lật đáp án và tự đánh giá. Đóng/mở app vẫn thấy phiên học, thẻ và tiến độ đã lưu.

Không làm lại mục tiêu, lịch AI, ngân sách, auth hoặc Google Calendar. Phiên học không tự thay đổi các phần này.

## 2. Công việc và ước lượng

| Người | Đầu ra chính | Ước lượng tham khảo | Việc bàn giao sớm |
| --- | --- | --- | --- |
| 1 | Landing, StudyTimerPage, studySessions client, nút mở Timer từ công việc, điều hướng và tính đồng nhất/mobile | 4–5 ngày công | Shell route/callback và mock Timer ở D2 |
| 2 | StudySession API/DB/tests; ActiveRecallPage và recall client | 5–6 ngày công | Schema/session API skeleton và fixture ở D2 |
| 3 | Material/deck/card/review API/DB, Gemini adapter, nguồn dẫn và tests | 5–7 ngày công | Payload flashcard thành công/lỗi và stub API ở D2 |

Đây **chưa phải chia đều tuyệt đối**: Người 3 có rủi ro AI/tài liệu cao nhất; Người 2 có hai phần độc lập. Khi Người 1 xong giao diện sớm, ưu tiên hỗ trợ regression/mobile cho Active Recall, không tự nhận thêm feature. Nếu nhóm chỉ làm vài giờ mỗi ngày thì kéo dài lịch; không hiểu 7 ngày lịch là đủ 7 ngày công.

## 3. Lịch tương đối D1–D7

| Mốc | Người 1 | Người 2 | Người 3 | Cổng nghiệm thu |
| --- | --- | --- | --- | --- |
| D1 | Chốt layout, props, điều hướng | Chốt state machine Timer, DB và lỗi | Chốt material/deck/review, nguồn dẫn và lỗi AI | PO duyệt phạm vi; cả nhóm duyệt API v1 và fixture |
| D2 | Landing shell, Timer mock; PR route nhỏ | PR nền session + migration V8; tạo API tối thiểu | Upload/parser mock + fixture flashcard; chờ V8 trước V9 | FE có thể code theo mock; migration nền V8 merge trước |
| D3–D4 | Landing hoàn thiện, Timer nối API thật | Phiên học, idempotency/ownership; UI recall theo fixture | Sinh bản nháp AI, DB recall V9 và CRUD/activate/review | Demo từng luồng nhỏ, cập nhật PR hằng ngày |
| D5 | Kiểm tra refresh/two-tab/mobile Timer | Recall UI nối API thật | Sửa lỗi nội dung, nguồn và API | Không còn mock trong luồng demo; chưa mở rộng scope |
| D6 | Chủ trì ghép shell và kiểm tra theme | Kiểm tra tích hợp Timer + Recall | Kiểm tra AI lỗi, timeout và dữ liệu | Smoke test xuyên suốt trên database test |
| D7 | Cùng sửa lỗi P0/P1 | Cùng sửa lỗi P0/P1 | Cùng sửa lỗi P0/P1 | PO demo và tick checklist DoD; còn gì chưa đạt phải ghi rõ |

Nếu D1 đổi contract, phải sửa cả fixture và tài liệu trước khi merge; không để hai phía tự hiểu khác nhau.

## 4. Quyền sở hữu file để tránh đụng nhau

| File/module | Người phụ trách | Cách phối hợp |
| --- | --- | --- |
| `frontend/src/App.jsx`, điều hướng/sidebar, điểm mở Timer từ lịch | 1 | 2 gửi props/route cần dùng; không cùng sửa shell |
| `frontend/src/pages/LandingPage.*`, `StudyTimerPage.*`, `lib/studySessions.*` | 1 | 2 review JSON/session logic, không sửa layout song song |
| `frontend/src/pages/ActiveRecallPage.*`, `components/recall/*`, `lib/recall.*` | 2 | 1 review theme/mobile, 3 review mapping field |
| `frontend/src/theme.css`, `components/ui/*` | 1 | Chỉ chỉnh khi thật sự cần; người khác đề xuất qua PR |
| `backend/.../sessions/*`, schema phiên học, test session | 2 | Session backend là dependency của Timer UI |
| `backend/.../recall/*`, Gemini recall adapter, schema recall, test recall | 3 | Có thể tái sử dụng public parser sẵn có, không sửa planner prompt cũ |
| `backend/.../api/ApiExceptionHandler.java` nếu cần thêm handler | 2 | 3 gửi exception/error code cần đăng ký; không sửa file chung cùng lúc |
| Contract và fixture trong bộ giao việc | 2 điều phối | 1 và 3 cùng review; PO chốt thay đổi hành vi |
| `application.yml`, `pom.xml`, workflow, migrations | Chủ PR liên quan + 1 reviewer | Không tự đổi cấu hình chung; không sửa migration đã áp dụng |

## 5. Branch, PR và thứ tự phụ thuộc

Branch gợi ý: `feat/landing-timer-ui`, `feat/study-sessions-recall-ui`, `feat/recall-api`. Tên không gắn người thật để dễ chuyển giao. Mỗi người tách PR nhỏ; không chờ D7 mới mở một PR khổng lồ.

Thứ tự merge:

1. PR contract/fixture/route shell nhỏ, được cả ba review.
2. PR nền backend phiên học của Người 2, **V8** (nếu main lúc đó vẫn mới nhất là V7).
3. Người 3 cập nhật branch có V8 rồi mới chốt **V9** cho recall. Không áp V9 vào DB đang thiếu V8 rồi mới bổ sung V8 sau.
4. Landing + Timer UI nối session API; recall API và UI ghép sau khi schema/base API đã có.
5. PR tích hợp cuối và PO demo. Merge khi check liên quan xanh, không dùng force-push main.

V8/V9 là lịch phân bổ trên baseline hiện tại, không phải tên cố định bất chấp main. Trước khi thêm migration phải kiểm tra thư mục và đồng ý lại với người còn lại. Không bật `outOfOrder`, không xóa lịch sử Flyway hoặc reset DB cá nhân để che lỗi.

## 6. Hợp đồng component giữa các thành viên

```jsx
<LandingPage currentUser={user} onStart={openGoalsOrRegister} onLogin={openLogin} />
<StudyTimerPage currentUser={user} initialTaskId={taskId}
  onNavigateAuth={openAuth} onNavigateGoals={openGoals} />
<ActiveRecallPage currentUser={user} initialGoalId={goalId}
  onNavigateAuth={openAuth} />
```

Route hash đề xuất: `#landing` công khai, `#timer` và `#recall` cần đăng nhập. Chưa đăng nhập ở `/` thấy landing; đã đăng nhập ở `/` thấy Tổng quan. Truy cập rõ `#landing` vẫn xem được landing. Giữ `#goals`, `#daily`, `#login`, `#register` đang có. Người 1 quản lý callback/return destination bằng allowlist route nội bộ, không nhận URL ngoài từ query.

CTA landing: khách → đăng ký; sau auth → mục tiêu/tạo kế hoạch. Nút “Bắt đầu học” từ một công việc → Timer với đúng task ID. Người 2/3 không thêm router thứ hai, session giả hay sidebar riêng.

## 7. Rủi ro cần báo sớm

- API chưa sẵn sàng: dùng fixture được chốt, hiển thị rõ chế độ phát triển; không merge mock thành chức năng thật.
- AI chậm/hết quota: Người 3 giữ tài liệu đã lưu, trả lỗi có thể thử lại; Người 2 không tạo thẻ giả để làm UI trông thành công.
- Timer refresh/two-tab: cần backend state machine, không chỉ setInterval/localStorage.
- Theme lệch: dùng token và component hiện có, không copy toàn bộ theme của website khác.
- Scope tăng: giọng nói, OCR, AI chấm đáp án, spaced repetition, thống kê nâng cao chuyển sang đợt sau.
