# ASP-27 — ASP-9 verification evidence

Date of runs: 2026-11-07, Windows 11, branch `dev2`. Re-run on the same day after the move to the `/api/v1` contract ([Backend API rules](../backend-api-rules.md)): paths, envelope and error codes below use the new names (`PATCH` replaced `PUT`, `NOT_FOUND`, `GOAL_UPDATE_CONFLICT`, `GOAL_VERSION_CONFLICT`). Contract: [ASP-9-goals-api.md](../product/ASP-9-goals-api.md). Results are what was actually executed; anything not run is marked **Chưa chạy**.

## 1. Commands and results

| Check | Command | Result |
| --- | --- | --- |
| Backend unit + PostgreSQL integration | `mvn -f backend/pom.xml -Dgoal.integration=true -Dauth.integration=true verify` against a disposable `postgres:17` container (port 55432, not the project database) | **Đạt**: 111 tests, 0 failures, 0 errors, 0 skipped |
| Frontend tests | `npm test` in `frontend/` | **Đạt**: 32 tests, 0 failures |
| Frontend lint | `npm run lint` | **Đạt** (no findings) |
| Frontend build | `npm run build` | **Đạt** |
| Browser demo (React + Spring Boot + PostgreSQL) | Vite dev server, `spring-boot:run`, real registration and session in the in-app browser | **Đạt** for the steps in section 4; limits in section 5 |
| Backend CI workflow | GitHub Actions | **Chưa chạy** (not triggered from this machine) |

Notes: the local JVM reports time zone `Asia/Saigon`, which PostgreSQL 17 rejects, so the PostgreSQL tests were run with `JAVA_TOOL_OPTIONS=-Duser.timezone=Asia/Ho_Chi_Minh`. Without the PostgreSQL system properties, those 11 tests are skipped, so a plain `mvn verify` proves less.

## 2. Acceptance criteria → test → result

Test names are classes under `backend/src/test/java/com/studyplanner/goals/` (and `frontend/src/lib/`). "API+DB" = `GoalApiPostgresIntegrationTest` (HTTP, session auth, real PostgreSQL).

| AC | What is proven | Evidence | Result |
| --- | --- | --- | --- |
| 1. Create with 5 fields (logged-in user) | 201, row saved, owner from session, `userId` from client ignored | API+DB `createListAndReadPersist`, `ownerComesFromSession`; browser demo step 3 | Đạt |
| 1. Subject/topics not blank after trim | Whitespace-only rejected (400 `required`); stored value trimmed | API+DB `validationBoundaries`, `createListAndReadPersist`; `GoalPolicyTest`; demo steps 1, 3 | Đạt |
| 1. Exam date not in the past, after start | Past exam and exam equal to start rejected | API+DB `validationBoundaries`; `GoalPolicyTest` (including Vietnam time-zone boundary in `GoalServiceTest`) | Đạt |
| 1. At most 90 days | 90 accepted, 91 rejected | API+DB `validationBoundaries`; `GoalPolicyTest`; DB CHECK in V1 | Đạt |
| 1. 15–480 integer minutes | 14 and 481 rejected; 15 and 480 accepted; 90.5 and `"90"` rejected | API+DB `validationBoundaries`; `GoalControllerTest` (`decimalMinutesAreRejected`, wrong types); demo step 2 (14) | Đạt |
| 1. Server validates; UI shows errors at the field | Field errors rendered under the matching input | `GoalControllerTest`; `goalErrors.test.js`; demo steps 1, 2 (screenshots in session) | Đạt (UI logic checked in browser; no React component test) |
| 1. Saved and shown after reload | Goal still listed after browser reload | Demo step 4 (F5 with session) | Đạt |
| 2. See own list and detail only | Own goal listed/readable; other account sees empty list and 404 | API+DB `crossAccountAccessIsRejected`; `GoalServicePostgresIntegrationTest` | Đạt |
| 2. No access by changing ID / direct request | A's goal ID with B's session → 404 `NOT_FOUND`; no session → 401 | API+DB `crossAccountAccessIsRejected` | Đạt |
| 3. Edit and see it again | PUT saves, `version` +1, GET shows new values; stale version → 409 | API+DB `updatePersistsAndChecksVersion`; demo step 6 (v0 → v1) | Đạt |
| 3. Edit checks existing tasks | Task on/after new exam date, or day total over new budget → 409 `GOAL_UPDATE_CONFLICT` naming task, date and minutes over | API+DB `updateRejectedByExistingTasksIsAtomic`; `GoalServicePostgresIntegrationTest.updateBoundariesWithRealTasks`; `GoalPolicyTest`; demo step 5 | Đạt |
| 3. Rejected edit keeps data | Row unchanged after 409 (values and version) | Same API+DB test; demo step 5 (checked in the database) | Đạt |
| 3. Invalid edit input rejected | 400 with field errors, even when tasks also conflict | API+DB `updateCrossAccountAndInvalidInput`; `GoalControllerTest` | Đạt |
| 3. Account A cannot edit B's goal | 404, goal unchanged | API+DB `updateCrossAccountAndInvalidInput`; `GoalServicePostgresIntegrationTest.updateByAnotherAccountIsRejected...` | Đạt |
| 4. Confirm before delete; cancel keeps data | Cancel sends no request; goal and tasks remain | `goalDelete.test.js`; demo step 7 (cancel: 1 goal / 3 tasks remain) | Đạt (see limit L1) |
| 4. Confirm deletes goal, tasks, drafts | 204; goal, tasks and plan drafts gone; other goal untouched; repeat → 404 | API+DB `deleteRemovesGoalTasksAndDraftsOnly`; demo step 8 (1/3/1 → 0/0/0) | Đạt |
| 4. Cannot delete another user's goal | 404 and rows still present | API+DB `deleteCrossAccountAndUnauthenticated` | Đạt |
| 5. No task on/after exam date; daily total ≤ budget | Enforced when the goal is edited | Rows for AC 3 | Đạt for goal edits; task create/edit is ASP-10 (**Chưa chạy**, not implemented here) |
| 5. Report missing minutes when a plan does not fit | — | Belongs to ASP-13/14 | Chưa chạy (out of ASP-9 scope) |
| 5. Budget per goal | A heavy day in goal B does not block editing goal A | `GoalServicePostgresIntegrationTest.updateOnlyChecksTasksOfTheSameGoal` | Đạt |
| DoD: ownership/auth on every operation | 401 without session; 404 for other owner on get/update/delete; CSRF required | Rows above; `GoalControllerTest` | Đạt |
| DoD: server-side validation, not only frontend | All rules re-checked by the server | API+DB tests send raw requests | Đạt |
| DoD: no passwords, keys or study content in logs | Error handler logs only the exception class | Code review of `GoalExceptionHandler`; no log audit of a running server | Chưa chạy (not audited) |
| DoD: reviewed by another developer; checks recorded | — | No review has happened | Chưa chạy |

## 3. Cases found while verifying

- An empty date field was reported as "wrong format" instead of "required". Fixed (`GoalRequest`), covered by `GoalControllerTest.blankDatesAreReportedAsRequiredNotAsBadFormat`.
- Conflict messages appear both in the page alert and under the related field, so they are shown twice. Cosmetic; not changed.

## 4. Browser demo performed (real stack)

Local only; test account `demo-a@example.test`, no real data.

1. Opened the app, created an account, landed on the goals page with an empty list (from the API).
2. Submitted an empty form: server errors under subject, topics and both dates. Entered 14 minutes: error under minutes per day.
3. Entered valid data with spaces around the subject and saved: goal appears; database row has the trimmed subject.
4. Reloaded the page: goal still listed.
5. Inserted 3 tasks for the goal by SQL (ASP-10 UI does not exist). Edited the goal to an earlier exam date and 60 minutes: the page listed the three tasks after the new exam date and the day with 90 minutes (30 over); the database row was unchanged (version 0).
6. Edited to a valid budget: saved, version 1.
7. Clicked Delete with the confirmation rejected: no request, goal and 3 tasks still in the database.
8. Added a plan draft by SQL, clicked Delete with the confirmation accepted: database went from 1 goal / 3 tasks / 1 draft to 0 / 0 / 0.

## 5. Limits that were not verified

- **L1:** The native `window.confirm` dialog was replaced by a script stub that returned false/true; the real dialog was not clicked. The code path is the same, the dialog rendering is not proven.
- **L2:** Tasks and drafts were created by SQL because task UI/API (ASP-10, ASP-13) do not exist yet. Nothing was proven about creating tasks through the UI.
- **L3:** Cross-account access was proven through API/DB tests, not by a second browser session.
- **L4:** No screenshots or screencast were saved to the repository; screenshots were only seen during the session.
- **L5:** Concurrent edits (two requests racing) were not load-tested; only sequential stale-version rejection and the row lock in code.
- **L6:** No React component tests exist; UI behaviour is covered by pure-logic tests and the manual demo above.
- **L7:** CI and an independent code review are still outstanding.

## 6. Status of ASP-9

AC 1–4 are met for the Goal CRUD and ownership scope, with real PostgreSQL evidence. **ASP-9 is not declared Done:** AC 5 depends on ASP-10/13/14 (task creation limits and shortfall reporting), the log audit and the required review are not done, and limits L1–L7 remain.

## 7. Re-run after the contract change (2026-11-07)

- `mvn clean verify -Dgoal.integration=true -Dauth.integration=true` (disposable `postgres:17`): 111 tests, 0 failures, 0 skipped. `npm test` 32 tests, `npm run lint` and `npm run build` clean.
- Live HTTP against the running app (curl, throwaway account, deleted afterwards): `/api/v1/auth/csrf`, register (201), duplicate register (409 `EMAIL_ALREADY_EXISTS`), wrong password (401 `INVALID_CREDENTIALS`), login, `/api/v1/me`, `POST` (201, trimmed subject), `POST` with `userId` and 481 minutes (400, `unknown_field` and `out_of_range`), paged list with `meta`, `PATCH` (200, version 1), stale `PATCH` (409 `GOAL_VERSION_CONFLICT`), `DELETE` (204), logout (204), `/api/v1/me` afterwards (401 `UNAUTHORIZED`); the old path `/api/goals` no longer serves data.
- Browser (new frontend, new throwaway account): register with automatic login, server error under minutes (481), create (subject trimmed, listed), edit rejected by existing tasks with the task/day message, valid edit to version 1, detail panel, delete with the confirmation stubbed to accept (goal and tasks gone from the database). Limits L1–L7 above still apply.

## 8. Kiểm chứng ASP-63 → ASP-66 (đánh dấu hoàn thành) và đối chiếu Definition of Done (ASP-120)

Ngày chạy 2026-11-07, nhánh `dev2-sprint3`. Mô tả tính năng: [task-completion.md](../product/task-completion.md).

### 8.1 Lệnh và kết quả

| Kiểm tra | Lệnh | Kết quả |
| --- | --- | --- |
| Backend, gồm test PostgreSQL | `mvn -f backend/pom.xml -Dgoal.integration=true -Dauth.integration=true clean verify` (container `postgres:17` dùng một lần, cổng 55433) | **Đạt**: 143 test, 0 lỗi, 0 bỏ qua (có `TaskApiPostgresIntegrationTest` 8 ca, `TaskControllerTest` 11, `TaskServiceTest` 7, `TaskJdbcRepositoryTest` 5) |
| Frontend test | `npm test` | **Đạt**: 37 test |
| Frontend lint, build | `npm run lint`, `npm run build` | **Đạt** |
| Trình duyệt trên stack thật (React + Spring Boot + PostgreSQL) | Vite dev, `spring-boot:run`, tài khoản thử mới | **Đạt**, các bước ở 8.3 |
| CI trên GitHub | Actions | **Chưa chạy** |

### 8.2 Đối chiếu Definition of Done

| Mục | Trạng thái | Ghi chú |
| --- | --- | --- |
| Đánh dấu hoàn thành và mở lại qua giao diện và API, dữ liệu lưu và đọc lại đúng | Đạt | Test API+DB; demo: tải lại trang vẫn giữ trạng thái |
| Kiểm tra quyền sở hữu ở server | Đạt | Tài khoản B bị 404, công việc không thuộc goal trên URL bị 404, không ghi gì |
| Validate ở server, không chỉ ở frontend | Đạt | Body sai kiểu, thiếu, trường lạ đều 400 |
| Gửi lặp không đảo trạng thái, đồng thời không ghi đôi | Đạt | Test gửi lặp; hai yêu cầu song song chỉ tăng version một lần |
| Lỗi rõ ràng, không báo thành công giả | Đạt | 401, 403, 404 có thông báo; không còn dữ liệu demo giả cho danh sách và đổi trạng thái |
| Hiển thị số hoàn thành và còn lại, 0/0 khi chưa có việc | Đạt cho ngày đang xem | Tiến độ theo mục tiêu thuộc ASP-16/17 |
| Không ghi mật khẩu, API key, nội dung học vào log | Chưa kiểm | Chỉ rà mã: bộ xử lý lỗi chỉ ghi tên lớp lỗi; chưa kiểm log của server đang chạy |
| Review bởi Developer khác | **Chưa làm** | Mới có tự rà soát ở 8.4 |
| Merge vào `main`, CI | **Chưa làm** | Chưa tạo PR; CI chưa chạy |
| Có thể demo | Đạt | 8.3 |

Không khai báo story là Done: thiếu review độc lập, merge và CI.

### 8.3 Demo trình duyệt (đã chạy)

1. Đăng ký tài khoản thử; tạo mục tiêu; chèn 3 công việc hôm nay bằng SQL (chưa có API tạo công việc).
2. Trang lịch học hiện đúng 3 công việc từ DB (0 / 3, "0 hoàn thành · 3 còn lại").
3. Bấm checkbox một công việc: DB `completed = true`, version mục tiêu 0 → 1; giao diện 1 / 3, checkbox thành "Mở lại: …".
4. Tải lại trang: vẫn 1 / 3.
5. Bấm lại để mở: DB `completed = false`, version 2; giao diện 0 / 3.
6. Xóa một công việc bằng SQL rồi bấm checkbox của nó: giao diện báo "Không tìm thấy công việc… danh sách đã được tải lại" và danh sách còn 2 việc.

### 8.4 Tự rà soát mã (chưa thay cho review của người khác)

- Tìm thấy và sửa 1 lỗi: sau lỗi 404, danh sách được tải lại nhưng `refreshData` xóa luôn thông báo lỗi; nay tải lại trước rồi mới hiện thông báo (đã xác nhận lại trên trình duyệt).
- Đã kiểm: mọi truy vấn công việc nối `study_goals` và lọc `user_id`; thao tác ghi khóa dòng mục tiêu rồi mới đọc và ghi; không chuỗi nào do người dùng nhập được ghép vào SQL (phần ghép chỉ gồm đoạn lọc cố định).
- Đã biết, chưa xử lý: đổi trạng thái hoàn thành làm tăng version mục tiêu theo đặc tả, nên sửa mục tiêu bằng `expectedVersion` cũ trả 409 cho tới khi tải lại; cần PO chấp nhận ([task-completion.md](../product/task-completion.md), mục 4).

### 8.5 Giới hạn chưa kiểm chứng

- Công việc trong DB đều tạo bằng SQL; chưa kiểm luồng tạo công việc qua giao diện (ASP-10).
- Chưa chạy theo trình duyệt thứ hai cho tài khoản khác; truy cập chéo chỉ kiểm bằng test API+DB.
- Không có test component React (chỉ test logic thuần và demo thủ công).
- Hộp xác nhận xóa mục tiêu vẫn là hộp thoại của trình duyệt, chưa bấm trực tiếp trong các lần demo này.

## 9. Kiểm chứng ASP-67 → ASP-70 (xem tiến độ) và đối chiếu Definition of Done (ASP-113)

Ngày chạy 2026-11-07, nhánh `dev4-sprint3`. Mô tả tính năng: [goal-progress.md](../product/goal-progress.md).

### 9.1 Lệnh và kết quả

| Kiểm tra | Lệnh | Kết quả |
| --- | --- | --- |
| Backend, gồm test PostgreSQL | `mvn -f backend/pom.xml -Dgoal.integration=true -Dauth.integration=true clean verify` (container `postgres:17` dùng một lần, cổng 55433) | **Đạt**: 130 test, 0 lỗi, 0 bỏ qua (có `ProgressApiPostgresIntegrationTest` 5 ca, `ProgressControllerTest` 6, `ProgressServiceTest` 5, `ProgressJdbcRepositoryTest` 3) |
| Frontend test | `npm test` | **Đạt**: 38 test (có `progress.test.js` 6 ca) |
| Frontend lint, build | `npm run lint`, `npm run build` | **Đạt** |
| Trình duyệt trên stack thật | Vite dev, `spring-boot:run`, tài khoản thử mới | **Đạt**, các bước ở 9.3 |
| CI trên GitHub | Actions | **Chưa chạy** |

### 9.2 Đối chiếu Definition of Done

| Mục | Trạng thái | Ghi chú |
| --- | --- | --- |
| Xem số hoàn thành, còn lại, tổng và tỷ lệ của mục tiêu qua API và giao diện | Đạt | Test API+DB; demo trình duyệt |
| Chưa có việc hiển thị 0/0, không chia cho 0 | Đạt | Test backend, test client, demo |
| Tính trên toàn bộ công việc, không theo trang | Đạt | Test 25 công việc |
| Bản nháp không được đếm | Đạt | Test PostgreSQL và test SQL |
| Kiểm tra quyền sở hữu ở server | Đạt | Tài khoản khác nhận 404; không có phiên 401 |
| Không đánh giá mức độ hiểu bài | Đạt | Chỉ hiển thị số đếm và phần trăm |
| Lỗi rõ ràng, không báo giả | Đạt | 401, 404, lỗi khác có thông báo riêng từng mục tiêu |
| Không ghi mật khẩu, API key, nội dung học vào log | Chưa kiểm | Chỉ rà mã: endpoint chỉ đọc, bộ xử lý lỗi chỉ ghi tên lớp lỗi; chưa kiểm log của server đang chạy |
| Review bởi Developer khác | **Chưa làm** | Mới có tự rà soát ở 9.4 |
| Merge vào `main`, CI | **Chưa làm** | Chưa tạo PR; CI chưa chạy |
| Có thể demo | Đạt | 9.3 |

Không khai báo story là Done: thiếu review độc lập, merge và CI.

### 9.3 Demo trình duyệt (đã chạy)

1. Đăng ký tài khoản thử; tạo hai mục tiêu; chèn 5 công việc cho mục tiêu thứ nhất bằng SQL (2 đã hoàn thành). Chưa có API đổi trạng thái trên nhánh này.
2. Trang Mục tiêu: mục tiêu có việc hiện "2/5 việc", thanh 40%, "2 hoàn thành · 3 còn lại"; mục tiêu chưa có việc hiện "0/0 việc", thanh 0%, "Chưa có công việc."
3. Khung "Chi tiết" hiện cùng số liệu.
4. Đặt cả 5 công việc là hoàn thành trong DB rồi tải lại trang: "5/5 việc", thanh 100%, "0 còn lại".
5. Nút ở đầu trang Mục tiêu từng lệch hàng do `margin-top` toàn cục của `.button--primary`; đã sửa và đo lại cùng vị trí, cùng chiều cao.

### 9.4 Tự rà soát mã (chưa thay cho review của người khác)

- Truy vấn tiến độ nối `study_goals` và lọc `user_id` trong cùng một câu lệnh, không ghép chuỗi người dùng vào SQL.
- Endpoint chỉ đọc; không khóa, không đổi version (có test kiểm version giữ nguyên sau khi đọc).
- Đã biết, chưa xử lý: mỗi mục tiêu gọi một yêu cầu riêng; danh sách rất dài sẽ phát sinh nhiều yêu cầu. Chưa có API gộp; không có trong đặc tả.

### 9.5 Giới hạn chưa kiểm chứng

- Trên nhánh này trạng thái hoàn thành chỉ đặt được bằng SQL; chưa kiểm chuỗi "bấm checkbox → tiến độ đổi" vì tính năng đó nằm ở nhánh `dev2-sprint3`. Cần kiểm lại sau khi gộp hai nhánh.
- Chưa có test component React; giao diện kiểm bằng test logic thuần và demo thủ công.
- Chưa kiểm bằng trình duyệt thứ hai cho tài khoản khác; truy cập chéo chỉ kiểm bằng test API+DB.
