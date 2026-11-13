# Development and verification harness

## Landing and Timer frontend (2026-11-13)

[Delivery and pending integration](../../docs/plans/completed/dev1-landing-timer-ui-2026-11-13.md).
Branch `feature/dev1-landing-timer`: frontend lint without warnings, 109/109 tests and
build passed. Vite SSR smoke covers rendered page structure only, not effects or UI
interaction. Live proxy health UP; authenticated session/active API returns 404 and
anonymous request 401. The Timer backend is absent, so real persistence/reload/offline
and two-tab acceptance remain pending; no successful session writes are claimed.
Browser discovery returned no connection. No backend/DB/secret changes for this work.

This is the maintained owner of verification commands and known gaps. Environment/startup instructions belong to [setup.md](../../setup.md); product rules belong to [requirements.md](../../requirements.md) and contracts linked in [docs/README.md](../../docs/README.md).

Current implementation includes auth, Goal CRUD, tasks, syllabus extraction, scoped AI drafts/confirmation and Google Calendar. Starter-era statements that only `/actuator/health` exists are obsolete. [Consolidated delivery evidence](../../docs/plans/completed/core-study-calendar-2026-11-11.md) records dated results and remaining limits.

## Working directory and prerequisites

- Java 21, Maven 3.9+, Node.js 22.12+ and npm. Frontend CI uses Node 24.
- Commands below start at repository root unless otherwise stated. The PO runtime currently uses the integration worktree `C:\project\AI-Study-Planner\.worktrees\core-syllabus`, branch `integration/sprint2-sprint3-all8-test`; check `git status`/branch before operating.
- Backend explicitly imports root `.env` from root or `backend/`. Shell environment overrides file values. Keep credentials private; never dump `.env`, token rows or provider response bodies into output.
- Maven output stays in `backend/target`; frontend output stays in `frontend/dist`.

## Frontend

```powershell
Set-Location frontend
npm.cmd ci
npm.cmd run lint
npm.cmd test
npm.cmd run build
```

On Bash use `npm` instead of `npm.cmd`. Install when dependencies are absent/changed; do not rerun install just to report an existing test result.

Tests in `src/lib/*.test.js` exercise logic and HTTP clients using mocks: auth/CSRF, goals, task/progress operations, syllabus, scoped planning, week navigation/aggregation, overview and Calendar selection/export. They do not prove browser layout, OAuth clicks, mobile sync or real provider behavior.

## Backend default checks

```powershell
mvn -f backend/pom.xml verify -B -ntp
```

This compiles/tests/packages. Several PostgreSQL and personal-PDF cases are opt-in; report skipped counts. Unit/MockMvc checks do not by themselves prove real PostgreSQL, Gemini, Google OAuth or Calendar export.

If Maven cache access is restricted on the local agent, the observed runtime used `-Dmaven.repo.local=C:\Users\ACER\.m2\repository`. This is machine-specific, not a requirement for the team. A retry after a permissions failure is not proof the failing invocation passed.

## PostgreSQL integration boundaries

Use a disposable test database, never the demo/user database. Some auth/goal integration suites clear fixture data; confirm the resolved datasource before running them. Do not use `docker compose down -v` to make tests pass.

The auth/goal suites read test datasource settings from the environment:

```powershell
$env:DB_URL='jdbc:postgresql://127.0.0.1:5432/study_planner_test'
$env:DB_USER='study_planner'
# Set DB_PASSWORD privately to the disposable test DB password.
mvn -f backend/pom.xml -Dgoal.integration=true -Dauth.integration=true verify -B -ntp
```

Calendar integration is separately opt-in:

```powershell
mvn -f backend/pom.xml '-Dtest=Calendar*Test' '-Dcalendar.integration=true' '-Dcalendar.test-db-url=jdbc:postgresql://127.0.0.1:5432/study_planner_test' test -B -ntp
```

`CalendarStoreIntegrationTest` currently uses fixed local fixture username/password in its `@SpringBootTest` properties; the test URL is configurable. This command was verified against that isolated local fixture, not arbitrary team/CI credentials. Inspect the test before enabling it elsewhere. It creates/deletes only its own random users; Google is mocked, so these tests create no real calendar events.

Calendar checks cover ownership/expiry, token encryption, overnight rest and disabled days, exact/subsecond boundaries, unavailable busy calendars, stale tasks/settings, durable outbox recovery after timeout, and simultaneous confirmations without duplicate insertions.

## Optional real PDFs

```powershell
mvn -f backend/pom.xml '-Dtest=SyllabusOutlineTest,SyllabusLayoutTest,SyllabusAdhocTest,SyllabusServiceTest' '-Dsyllabus.pdf=<absolute-game-pdf>' '-Dsyllabus.network-pdf=<absolute-network-pdf>' '-Dsyllabus.testing-pdf=<absolute-testing-pdf>' test -B -ntp
```

Replace placeholders with files you own locally. These private files are not in Git and their absence is not an automatic parser failure. Synthetic fixtures remain available without the files. Do not send private syllabus contents to Gemini merely to validate a key; a minimal synthetic prompt can test connectivity separately.

## Live readiness and manual journeys

Start resources using setup.md. Default backend port is 8080; the observed PO runtime uses 18083, frontend 5173, PostgreSQL 5432. Substitute the actual configured port:

```powershell
Invoke-RestMethod http://127.0.0.1:18083/actuator/health
Invoke-RestMethod http://127.0.0.1:5173/actuator/health
```

Both should return `status: UP`. Health does not prove business behavior. Calendar status without a logged-in session should return 401. Do not bypass ownership by replaying another user's browser cookies.

Manual journeys to verify after relevant changes:

1. Sign in; upload syllabus, inspect topics/source/exam date; enter start date/minutes. Yesterday must be rejected for a new goal; an ongoing goal can preserve its original start.
2. Generate a scoped draft; tasks must not exist before confirmation. Confirm and verify the week/day view; repeated confirmation must not duplicate tasks.
3. Connect Google as an allowed OAuth test user, choose writable target and busy calendars, set living hours, preview and explicitly confirm. Verify actual event times in the selected Google calendar.
4. If a selected calendar disappears, it must remain visibly selected/unavailable. User explicitly removes it or restores permission; app must not treat missing busy data as free time.
5. Check active/hover/keyboard-focus sidebar contrast, Vietnamese diacritics, reduced-motion behavior and narrow-screen layout. Confirm phone visibility in the same Google account/calendar; distinguish this from the local app being reachable on a phone.

Only perform real provider writes with explicit user authorization and a reviewable scenario. Record whether evidence came from HTTP, a connected browser, or the user's screenshot/report.

## CI and enforcement

- `.github/workflows/frontend.yml` declares install/lint/test/build on Node 24 for relevant changes.
- `.github/workflows/backend.yml` declares Maven verify with `goal.integration` and `auth.integration`, using a PostgreSQL 17 service.
- Calendar integration is not currently enabled by that workflow; local Calendar proof is not CI proof.
- Workflow source does not prove an actual successful Actions run or enforced branch protection. Neither was verified or changed by the documentation update.
- Harness status/doctor validates Harness installation, not application behavior. No Harness upgrade, hook installation or CI policy change is part of this update.

## Evidence ledger and remaining gaps

See the completed handoff for exact dated runs: a full backend verify of 260 executions/55 skipped before the final Calendar selection fix; then 34/34 targeted Calendar tests and 82/82 frontend tests after the fix. Do not aggregate separate runs into a new full-suite result.

Actual Google web/mobile visibility was confirmed by the PO, not automated browser/mobile testing. No connected browser E2E, production OAuth verification/deployment, backup restoration rehearsal, or full-suite rerun after the final selection fix is claimed. Logs/reports in ignored `.tools/` and generated `target/` are local artifacts and can be overwritten; documentation should retain the invocation, scope and limitation of each result without secrets.
## Partial planning verification (2026-11-11)

[Delivery evidence](../../docs/plans/completed/partial-planning-2026-11-11.md) supersedes the older counts for this change. Full backend test with goal/auth/calendar/planning integration flags: 269 executions, zero failures/errors, three private-PDF skips. After final reason preservation, focused PersistentPlanningPostgresTest and PlanSchedulingTest: 11/11 passed. Frontend: 86/86 tests, lint/build passed; final adjustment-link change received lint/build. Runtime direct/proxy health UP; new pending endpoint through proxy returns 401 without auth. No browser E2E or live provider verification.

Enable planning HTTP/DB and V4-to-V5 rehearsal tests with `-Dplanning.integration=true`; use only disposable study_planner_test. Full-run auth/goal suites also require explicit DB_URL/DB_USER/DB_PASSWORD for that disposable DB. Planning tests use `planning.test-db-url` and the local fixture credentials declared in test source. Do not point any suite at demo/user data. No schema migration is required for partial planning; remainder is stored in existing plan_drafts JSON.
## Shared daily budget verification (2026-11-11)

Contract: [shared study budget](../../docs/product/shared-study-budget.md). Delivery: [shared-budget evidence](../../docs/plans/completed/shared-budget-2026-11-11.md). V6 requires explicit user setup; no guessed availability for legacy accounts.

After restarting the existing PostgreSQL portable data directory, focused shared/persistent/task-service tests passed 16/16. Full backend invocation with explicit disposable study_planner_test datasource and goal/auth/calendar/planning integration flags: 274 executions, 271 passed, three private-PDF opt-ins skipped, zero failures/errors. Package then succeeded. Frontend: lint, 89/89 tests and build passed. Concurrent manual/confirm and settings/task writes are exercised with real PostgreSQL; provider is mocked.

SharedBudgetPostgresTest is enabled by -Dplanning.integration=true, using the same isolated planning.test-db-url convention as persistent planning tests. Do not use demo/user DBs. V6 preservation is covered by the isolated migration rehearsal. Live demo migration also retained identical read-only fingerprints for goals/tasks/drafts/Calendar exports. Direct/proxy health UP and anonymous budget endpoint through proxy returns 401. No browser E2E, mobile rendering, real provider calls or backup restore claimed. Existing .env unchanged.
## Draft recovery verification (2026-11-11)

[Delivery record](../../docs/plans/completed/draft-recovery-2026-11-11.md): red loop initially reproduced duplicate-existing-work rejection and confirmed latest read returning 409. Full regression after fixes and DB restart: 277 backend executions, zero failures/errors, three private-PDF skips; goal/auth/calendar/planning flags and explicit disposable study_planner_test datasource. Frontend lint, 90/90 tests and build passed. Package and local restart succeeded. Direct/proxy health UP; anonymous latest API via proxy returns 401.

History reads now return status while confirm/reschedule enforce permissions and usable state separately. Provider validation repair is bounded to one retry and tested with mocked responses; no actual raw failed response from the user's screenshot was captured. No live Gemini/Google call, browser E2E, schema migration, secret edit or production verification claimed.
## Partial-plan warning UX follow-up (2026-11-11)

Product behavior: [scoped plan contract](../../docs/product/syllabus-revision-plan.md). Focused backend SharedBudgetPostgresTest: 5/5 passed with sharedBudgetLimitedDays metadata assertion. Frontend: 90/90 client tests; final lint and build passed without warnings. Local Vite SSR rendering checked 12/32 partial work and all three actions, total-shortfall versus fragmented-capacity wording, and complete-plan rendering. SSR is not browser click/layout/focus validation. Backend packaged/restarted; direct/proxy health UP. No schema/.env/provider changes or push. The prior full-backend count is historical, not a new full-suite result for this follow-up.
## Calendar update/delete synchronization (2026-11-11)

Contract: [Google Calendar sync](../../docs/product/google-calendar.md). Decision: [0002](../../docs/decisions/0002-calendar-update-delete.md). Delivery: [V7 sync evidence](../../docs/plans/completed/calendar-sync-2026-11-11.md).

Full backend test with explicit disposable study_planner_test datasource and goal/auth/calendar/planning flags: 295 executions, 292 passed, three private-PDF opt-ins skipped, zero failures/errors. Then a separate CalendarSyncPostgresTest run passed 6/6 after adding the independent-receipt lock proof; do not aggregate these runs. Frontend lint, 91/91 tests and build passed. Google HTTP calls are mocked; test application.properties disables scheduling and the PostgreSQL test asserts the worker bean is absent. Existing default tests do not silently run scheduled provider writes.

Production worker is enabled by default, starts after 30 seconds and polls every 15 seconds, up to 3 jobs/tick. Disable via calendar.sync.enabled=false only when deliberately pausing remote processing; queue remains durable. Test update/delete/lease/revision behavior with -Dcalendar.integration=true and -Dtest=CalendarSync*Test on the disposable database. Never point it at demo/user data.

Local demo schema migrated 6 to 7, package/restart succeeded, direct/proxy health UP and unauthenticated sync endpoint returns 401. Before/after fingerprints match all 3 goals, 58 tasks, 5 drafts and 13 export receipts. No .env change, historical queue backfill, real-provider update/delete verification, browser E2E or Git push. First export still requires explicit preview/confirmation; future user-approved app mutations enqueue synchronization automatically.
## Failed busy-calendar feedback (2026-11-11)

CalendarBusyTest + CalendarServiceTest: 12/12 passed after adding failedCalendarIds context while retaining fail-closed behavior. Frontend lint, 92/92 client tests and build passed; regression checks error context reaches the UI client. Package/restart succeeded and proxy health UP. UI now names/highlights failed calendars and explains target calendars cannot be bypassed by deselection. No schema/.env changes, actual provider failure capture or browser E2E claimed. The screenshot alone does not establish which selected calendar failed; exact name is shown on the next preview request.

## Weekly progress (2026-11-12)

Integration follow-up: [dev2-sprint4 test delivery](../../docs/plans/completed/dev2-sprint4-integration-2026-11-13.md).
On 2026-11-13 the integrated branch passed full backend verify with all four integration flags: 323 executions, zero failures/errors, three private-PDF skips on the isolated test database. After two test-only safeguards, WeeklyProgress tests passed 28/28 and verify packaged successfully. Frontend lint, 102/102 tests and build passed. Live proxy smoke used synthetic data in a separate preview database; browser interaction was unavailable. No existing demo secrets or data were used.

Contract: [weekly progress](../../docs/product/weekly-progress.md). Evidence: [weekly-progress-2026-11-12.md](../../docs/plans/completed/weekly-progress-2026-11-12.md).

After merging main into dev2: full backend `clean test` with explicit disposable study_planner_test datasource and goal/auth flags: 323 executions, 0 failures/errors, 25 opt-in skips (calendar, planning, private PDFs). The 27 WeeklyProgress tests were re-run alone after the controller moved to the shared `ApiResponse`/`ApiExceptionHandler`. Frontend lint, 102/102 tests and build passed. This was `test`, not `verify`; no browser E2E after the merge, no CI run, no independent review claimed. Read-only endpoint, no schema change.

## Dev 3 Recall integration (2026-11-13)

[Delivery and demo](../../docs/plans/completed/dev3-recall-integration-2026-11-13.md).
Branch integration/dev3-recall-test: backend full test with goal/auth/planning/calendar
flags on disposable study_planner_test port 55433: 367 executions, 0 failures/errors,
3 optional PDF skips. Separate verify -DskipTests packaged after that run. Frontend
lint, 104/104 tests and build passed. RecallPostgresIntegrationTest uses
-Dplanning.integration=true and -Dplanning.test-db-url, checks test database name,
and cleans only its own users. Live preview smoke checks persistence, missing-key
503, anonymous 401 and goal cascade. AI success is mocked; browser/real Gemini
remain unverified. Local API harness is not shipped Recall UI. No secret edits/push.

## Landing visual refresh (2026-11-13)

Branch feature/dev1-landing-timer: frontend lint, 109/109 tests and build passed. SSR structure and live asset serving passed. Vietnamese font cmap checked; generated hero image inspected. Carousel and responsive interactions implemented but no connected browser for rendered/click/motion verification. [Evidence](../../docs/plans/completed/landing-visual-refresh-2026-11-13.md). No backend or secret changes.

## Study sessions and Active Recall UI (2026-11-12)

Contracts: [study sessions](../../docs/product/study-sessions.md) and [Active Recall](../../docs/product/active-recall.md). Plan and evidence: [active plan](../../docs/plans/active/study-sessions-recall-2026-11-12.md). Migration V8 adds `study_sessions` and `study_session_receipts` only.

Backend: focused `StudySessionPolicyTest` 11/11, `StudySessionControllerTest` 11/11 and `StudySessionApiPostgresIntegrationTest` 14/14. The PostgreSQL suite is enabled by `-Dgoal.integration=true` with explicit disposable DB_URL/DB_USER/DB_PASSWORD (study_planner_test); it replaces the Clock with a hand-moved clock, so it never sleeps, and it clears `app_users` first. Full backend `clean test` with goal/auth flags: 332 executions, zero failures/errors, 25 opt-in skips (calendar, planning, private PDFs). This was `test`, not `verify`. `backend.yml` declares `goal.integration`, so CI would run the new suite, but no Actions run was observed.

Frontend: lint, 120/120 tests and build passed; the production bundle contains no development mock. Run the Recall page against sample data with `npm run dev` and open `/?recallMock=1#recall` (`&recallFail=generate,review` forces one failure each for the retry paths). The mock is not evidence about the server. Browser checks used Vite dev with the real backend for sign-in and goals, the Recall API mocked, at desktop and 360 px width.

Not claimed: any run against a real `/api/v1/recall` API (not implemented), the Timer UI, two-tab or offline browser behavior for sessions, a concurrency test for two simultaneous actions on one session, keyboard or screen-reader checks, real PDF upload, independent review or CI. The sessions contract values (25 minutes, seconds as the unit) await PO approval; `requirements.md` was not changed.

### Update 2026-11-13: concurrency, shared fixtures, accessibility and real API

On `dev2` with the recall API (V9) merged, full backend `clean test` with goal/auth flags and the disposable study_planner_test datasource: 396 executions, zero failures/errors, 25 opt-in skips (calendar, planning, private PDFs); `test`, not `verify`. This replaces the 332 above for the merged state only; do not add the runs together. Sessions now have 40 tests: 11 policy, 13 MockMvc (two compare the response field for field with the shared `session-active.json` and `api-error.json` fixtures in `backend/src/test/resources/fixtures/`) and 16 PostgreSQL, including six simultaneous actions on one version (exactly one applies, the rest 409) and six simultaneous retries with one Idempotency-Key (one change, one receipt). Removing the owner lock was tried to confirm the same-key case fails; the source was restored. Frontend lint, 130/130 tests and build passed; no development mock in the bundle.

Browser checks, desktop pane plus 768 px and 360 px, Vite dev with the real backend and PostgreSQL: with the mock, full keyboard-only flow, focus order and focus retention (a loss to the page body was found and fixed), accessibility tree, contrast measurement (the shared success badge is 2.86:1 and is darkened on the Recall page only), no horizontal overflow; against the real recall API, text and a generated two-page PDF upload (pages and text extracted and stored), rejection of an image and of a file over 5 MiB in the client without a request, rejection of a text-less PDF by the server, edit, confirm, three reviews persisted and resumed after reload.

Still not claimed: a real Gemini call (no key on the test machine; the recall backend falls back to a heuristic generator on a missing key or a generic Gemini failure and returns 201, which differs from the contract's AI error codes), a real screen reader, the Timer UI, a 401 mid-flow, independent review or CI.

## Large-data audit of dev2 (2026-11-13)

Environment as the harness requires: a disposable PostgreSQL 17 container, database `study_planner_test` on port 55440 (the shared 5432 instance was not touched), role `study_planner` with the fixture password that the Calendar and planning tests declare in their source; `JAVA_TOOL_OPTIONS=-Duser.timezone=Asia/Ho_Chi_Minh`. Full backend, all four opt-in flags, packaging included:

```powershell
mvn -f backend/pom.xml -Dgoal.integration=true -Dauth.integration=true -Dplanning.integration=true -Dcalendar.integration=true -Dplanning.test-db-url=jdbc:postgresql://127.0.0.1:55440/study_planner_test -Dcalendar.test-db-url=jdbc:postgresql://127.0.0.1:55440/study_planner_test clean verify -B -ntp
```

Result after the fix below: 398 executions, zero failures/errors, 3 skipped (the private-PDF opt-ins: SyllabusAdhocTest, SyllabusLayoutTest, SyllabusOutlineTest). The same command before the fix gave 396 executions, zero failures, 3 skipped. This is a new full run of one tree; it is not added to the earlier 332 or 396 counts. Frontend `npm run lint`, `npm test` (132/132) and `npm run build` passed; no development mock and no `US-17` label in the bundle. Live readiness: direct and proxy `/actuator/health` UP; Calendar status without a session returned 401.

Evidence source for the manual part: the connected in-app browser against Vite, Spring Boot and that PostgreSQL, with one account holding 130 goals, 171 tasks (later 1,671), 130 recall sources, 130 decks (650 cards) and 130 study sessions, including 100 to 1,900 character unbroken strings and multi-line text, plus SQL and HTTP checks of every change. Not a user screenshot.

Defects found in code authored by tyn7624 and fixed: recall lists silently stopped at 100 items and had no paging; long unbroken text made the Recall page up to 22,586 px wide; line breaks in cards were collapsed; `POST/PATCH /api/v1/goals` with a 101-character subject answered 500 instead of 400 `too_long` (red/green: the new PostgreSQL case failed with a 500 without the fix); the checkbox and progress bar ignored `prefers-reduced-motion`; the sidebar and topbar showed an `US-17` label.

Defects seen in code that is not tyn7624's, reported and left unchanged: the Goals list renders all goals on one page (33,600 px for 130; `GoalsPage.jsx`, Đức); the weekly schedule overflows to 1,147 px at 1,024 px with 101 sessions (`StudyPlanPage.jsx`, duckymomoUWU); the daily task list overflows to 2,272 px with a 200-character title (`.task-body`/`.task-title` in `DailyTasksPage.css`, tvgb05); the shared success badge is 2.86:1 (`shadcn.css`, tvgb05); recall generation falls back to heuristic cards when the Gemini key is missing (recall backend, tvgb05).

Harness journey 5 (what was checked): sidebar item contrast 6.25:1 idle, 7.31:1 active, badge 5.61:1; Vietnamese diacritics rendering on the Recall and weekly pages (keyboard focus was checked on the Recall page in the earlier 2026-11-13 run, not repeated on the weekly page); narrow screen 360 px and 768 px without horizontal overflow on the weekly and Recall pages with the data above; reduced-motion rules are loaded for the Recall page, checkbox and progress bar (the media query itself was not toggled in the browser).

Still not claimed: journeys 1 to 4 (syllabus upload, scoped draft, Google connection and export; not part of this audit and mostly other authors' work), a real Gemini or Google call, a real screen reader, independent review or CI.


## Updated dev2 Recall and Timer integration (2026-11-13)

[Current evidence](../../docs/plans/completed/dev2-recall-timer-integration-2026-11-13.md).
Branch integration/dev3-recall-test merges dev2-sprint4 10051f8: frontend lint,
139/139 tests and build passed; full backend test with all four integration flags
on disposable study_planner_test port 55433: 409 executions, zero failures/errors,
three optional PDF skips. Separate skipTests verify packaged. Live isolated preview
Timer lifecycle/persistence/replay/cascade passed. Recall uses real API; actual AI
success remains unverified without a key. Migration numbering is Recall V8, Timer V9
on this existing test environment; see delivery note before sharing database histories.
No browser/real Gemini claim, no secret changes or push.
