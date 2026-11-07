# ASP-27 — ASP-9 verification evidence

Date of runs: 2026-11-07, Windows 11, branch `dev2`. Re-run on the same day after the move to the `/api/v1` contract ([Backend API rules](../backend-api-rules.md)): paths, envelope and error codes below use the new names (`PATCH` replaced `PUT`, `NOT_FOUND`, `GOAL_UPDATE_CONFLICT`, `GOAL_VERSION_CONFLICT`). Contract: [ASP-9-goals-api.md](ASP-9-goals-api.md). Results are what was actually executed; anything not run is marked **Chưa chạy**.

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
