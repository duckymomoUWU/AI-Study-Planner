# Kiểm tra core tạo lịch từ đề cương — 2026-11-10

## Bổ sung sau phản hồi PO: giữ tự điền ngày thi

Đã sửa việc bỏ tự điền quá rộng: ngày thi ghi rõ được trích riêng, giữ nguồn, cho người dùng xem/sửa/xác nhận rồi điền form. Một ngày duy nhất (kể cả lặp song ngữ) được tự điền; nhiều ngày khác nhau yêu cầu chọn; không có ngày thì không đoán và không xóa ngày đã nhập. Ngày biên soạn, số tiết và phút thi không được dùng. Hỗ trợ ngày có năm đầy đủ dạng ISO, d/m/yyyy, d-m-yyyy, d.m.yyyy, ngày/tháng/năm tiếng Việt và tháng đầy đủ tiếng Anh. PDF CO3045 đính kèm chỉ có thời lượng thi và ngày biên soạn, không có ngày thi cụ thể nên vẫn để trống đúng dữ liệu.

File bổ sung: `SyllabusExamDate.java`, `SyllabusExamDateTest.java`; sửa DTO của `SyllabusService`, preview `SyllabusUpload`, áp dụng form `GoalsPage`/`planningScope.js`, test helper và tài liệu. Giữ toàn bộ thay đổi core trước đó, không commit/push.

Kiểm tra sau bổ sung: Maven test với `-Dtest=SyllabusExamDateTest,SyllabusServiceTest,SyllabusOutlineTest` và `-Dsyllabus.pdf=...`: 12/12 đạt. Frontend 70/70, lint/build đạt. Maven package `-DskipTests` thành công sau lượt test riêng. Smoke qua Vite proxy thật đạt: tự điền 2026-12-25 từ nhãn final exam, ngày mơ hồ giữ hai lựa chọn, không đổi phút cá nhân, CO3045 không lấy ngày biên soạn/phút thi. Backend đã khởi động lại với JAR mới. Đây là test liên quan, không phải chạy lại toàn bộ bộ test PostgreSQL 210 ca của mốc trước.

## Hiện trạng ban đầu

- Đúng: session/CSRF/ownership, bản nháp không tạo task, confirm có transaction/goal lock/version/receipt, ngân sách từng goal, không xếp vào ngày thi, AI lỗi không xóa việc cũ.
- Sai trong luồng đề cương: trích xuất đang gợi ý ngày thi và phút cá nhân từ tài liệu; chỉ trả chuỗi topics; fallback có thể tạo chủ đề tổng quát dù không tìm thấy nội dung. Luồng parse mới không gọi fallback đó.
- Thiếu: duyệt từng chủ đề và nguồn, gộp song ngữ/chương tiếp, ID chủ đề trên task, 0 phút ở từng ngày, giữ việc chưa xếp và lý do, phân biệt tổng quỹ thiếu với một việc không vừa ngày, chính sách confirm lịch một phần, bảo vệ phản hồi cũ ở frontend.

## Điểm vào và thay đổi

- UI: `SyllabusUpload.jsx` → `syllabus.js` → `SyllabusService` → `SyllabusOutline` (đọc PDF/DOCX/văn bản, tạo outline để duyệt).
- Sau xác nhận chủ đề: `GoalsPage.jsx` → `goalPlanning.js`/`ai-planner.js` → `PlanDraftController`/`PlanDraftService` → Gemini (việc gắn ID) → `PlanScheduling` (kiểm tra và xếp theo phút/ngày) → `plan_drafts.proposal` JSONB.
- Duyệt/sửa ở `PlanDraftPanel.jsx` → confirm kiểm tra lại dưới khóa → `study_tasks` + chủ đề + receipt. Migration mới V3 chỉ thêm cặp trường topic.
- Sửa form sau khi goal được tạo sẽ cập nhật cùng goal rồi sinh draft, không tạo thêm goal khi thử AI lại. Nhập liệu được giữ khi AI lỗi. Kết quả từ yêu cầu cũ bị bỏ khi form đổi hoặc component đóng.
- Hợp đồng và giới hạn: [syllabus-revision-plan.md](../product/syllabus-revision-plan.md).

## Ca bắt buộc

1. PDF CO3045 thật được truyền bằng `-Dsyllabus.pdf=...`: đọc 9 trang; 6 chương; Vector/Matrix/đụng độ mỗi chủ đề một lần; chương 6 gộp trang 7–8 giữ 6 nội dung; không sinh chương từ chỉ dẫn đọc sách. Fixture nhỏ song ngữ chạy không cần file ngoài repo. Không đưa PDF cá nhân lên Git.
2. Chỉ chọn Collision/Path finding: frontend chỉ gửi hai chủ đề; backend từ chối ID khác, ghi topicId/topicTitle sau confirm.
3. 180 phút việc/120 phút quỹ: giữ việc chưa xếp, thiếu 60, không vượt ngân sách.
4. 3 ngày x 20, việc45 +15: giữ nguyên việc45, `NO_DAY_FITS`, shortfall0.
5. Hai việc40 chuyển cùng ngày60: confirm422, dữ liệu cũ/version/receipt giữ nguyên trên PostgreSQL.
6. Estimate âm, ID lạ, trùng, ngày thi/sau thi: không tạo draft hợp lệ/task.
7. Ngày hết hạn hoặc toàn bộ quỹ0: mock xác nhận provider không được gọi.
8. AI lỗi giữ draft/task cũ; thay ngày thi trong khi mock AI bị giữ chờ: kết quả409. Frontend kiểm tra deferred response không ghi đè input/draft mới.

## Lệnh kiểm tra

Chỉ dùng database disposable `study_planner_test` cho các test tích hợp: bộ test có xóa users, tuyệt đối không dùng demo DB.

```powershell
$env:DB_URL='jdbc:postgresql://127.0.0.1:55432/study_planner_test'
$env:DB_USER='study_planner'
$env:DB_PASSWORD='unused-local'
mvn -f backend/pom.xml '-Dmaven.repo.local=C:\Users\ACER\.m2\repository' '-Dgoal.integration=true' '-Dauth.integration=true' '-Dsyllabus.pdf=C:\Users\ACER\Downloads\DCMH.CO3045_Game Programming.pdf' verify -B -ntp
cd frontend
npm.cmd ci
npm.cmd run lint
npm.cmd test
npm.cmd run build
```

## Kết quả cuối

- `npm.cmd ci`: thành công, 0 lỗ hổng được npm audit báo tại thời điểm chạy.
- `mvn ... verify` như lệnh trên: **210 test, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**, tạo JAR. Bao gồm policy/unit, MockMvc, PostgreSQL thật (DB test riêng), fixture song ngữ, PDF CO3045 thật và schema/phản hồi Gemini nhiều phần. AI trong bộ kiểm thử tự động là mock, không phụ thuộc key/provider.
- `npm.cmd run lint`, `npm.cmd test`, `npm.cmd run build`: thành công; **69/69 test**. Các test JS kiểm tra client/API và cơ chế bỏ phản hồi cũ, không phải thao tác UI bằng trình duyệt.
- Smoke riêng qua Vite proxy và backend/PostgreSQL demo bằng client JS thật, Gemini `gemini-flash-lite-latest`: PDF thật 9 trang → 6 chương → chọn 2 chủ đề (đụng độ và Path finding) → 1 goal POST → 1 draft POST → 2 công việc đúng scope → trước confirm có 0 task → sau confirm có 2 task với topicId/topicTitle → gửi lại cùng key/payload không tạo thêm → đọc được việc theo ngày. Ngày 0 phút được bỏ qua; lịch đầu tiên là 2026-11-12. Đã xóa mục tiêu smoke và logout, không ghi key/tài liệu vào app log.
- Lượt provider đầu tiên chưa đúng định dạng trả 502 và không thay lịch; đã bổ sung `responseJsonSchema` và xử lý multipart/thought, thêm test cố định rồi chạy lại toàn bộ test và smoke thật thành công. Không có fallback lịch giả.
- Direct và proxied `/actuator/health`: UP. App đang chạy từ working tree `C:\project\AI-Study-Planner\.worktrees\core-syllabus`, frontend 127.0.0.1:5173, backend 18080, PostgreSQL 55432. Source chính `main` và file spec chưa theo dõi được giữ nguyên.
- Không có trình duyệt nào kết nối với công cụ kiểm thử (`browsers.list()` rỗng sau kiểm tra kết nối). Vì vậy **chưa xác minh thao tác chọn file/click/layout trong browser**; không coi smoke HTTP là browser E2E. Cách thử lại: mở http://127.0.0.1:5173, đăng nhập, vào Mục tiêu, tải PDF, rà/chọn chủ đề, nhập ngày/phút, sinh draft, sửa, xác nhận và xem lịch. Có thể cần đăng nhập lại sau khi backend khởi động lại.
- `git diff --check`: đạt; source diff chỉ trong phạm vi luồng đề cương/lập lịch, test, migration và tài liệu liên quan. Công cụ kiểm tra tạm nằm ở `.tools/` được loại khỏi Git bằng local exclude. **Không commit, push hoặc sửa Jira.**

## Quyết định PO còn cần chốt

- Có cho lưu lịch một phần hay không, cách lưu/trình bày việc chưa xếp và hành động người dùng xác nhận bỏ phạm vi. Hiện giữ việc trong draft và chỉ cho confirm đủ danh sách.
- Giới hạn hiện tại: ngân sách theo ngày và theo từng goal, không có khung giờ, không bảo đảm buổi liên tục, không OCR PDF scan; không tự tái lập lịch sau khi bỏ lỡ. Không tự mở rộng các phần này.

Test/provider smoke chỉ chứng minh các trường hợp đã chạy, không bảo đảm parser hiểu mọi dạng đề cương hay AI luôn đề xuất đúng nội dung thi. Người dùng vẫn rà và xác nhận phạm vi, công việc và ước lượng.

## Danh sách file thay đổi

- `.agents/harness/README.md`
- `backend/pom.xml`
- `backend/src/main/java/com/studyplanner/goals/GeminiPlanService.java`
- `backend/src/main/java/com/studyplanner/goals/PlanDraftController.java`
- `backend/src/main/java/com/studyplanner/goals/PlanDraftService.java`
- `backend/src/main/java/com/studyplanner/goals/PlanScheduling.java`
- `backend/src/main/java/com/studyplanner/goals/SyllabusOutline.java`
- `backend/src/main/java/com/studyplanner/goals/SyllabusService.java`
- `backend/src/main/java/com/studyplanner/tasks/TaskJdbcRepository.java`
- `backend/src/main/java/com/studyplanner/tasks/TaskResponse.java`
- `backend/src/main/resources/db/migration/V3__task_topic_provenance.sql`
- `backend/src/test/java/com/studyplanner/goals/GeminiStructuredOutputTest.java`
- `backend/src/test/java/com/studyplanner/goals/PlanDraftControllerTest.java`
- `backend/src/test/java/com/studyplanner/goals/PlanSchedulingTest.java`
- `backend/src/test/java/com/studyplanner/goals/SyllabusOutlineTest.java`
- `backend/src/test/java/com/studyplanner/goals/SyllabusServiceTest.java`
- `backend/src/test/java/com/studyplanner/integration/CombinedFeaturesPostgresIntegrationTest.java`
- `backend/src/test/java/com/studyplanner/integration/SyllabusPlanPostgresIntegrationTest.java`
- `docs/backend-api-rules.md`
- `docs/product/syllabus-revision-plan.md`
- `docs/product/syllabus-upload.md`
- `docs/reviews/syllabus-core-2026-11-10.md`
- `frontend/src/components/PlanDraftPanel.jsx`
- `frontend/src/components/SyllabusUpload.css`
- `frontend/src/components/SyllabusUpload.jsx`
- `frontend/src/lib/ai-planner.js`
- `frontend/src/lib/goalPlanning.js`
- `frontend/src/lib/goalPlanning.test.js`
- `frontend/src/lib/planningScope.js`
- `frontend/src/lib/planningScope.test.js`
- `frontend/src/lib/syllabus.js`
- `frontend/src/pages/DailyTasksPage.jsx`
- `frontend/src/pages/GoalsPage.jsx`
- `requirements.md`
