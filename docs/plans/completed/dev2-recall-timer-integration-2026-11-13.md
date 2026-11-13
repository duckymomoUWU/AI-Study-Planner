# Updated dev2-sprint4 integration - 2026-11-13

Target: integration/dev3-recall-test, previous head 1e8ee83.
Source: origin/dev2-sprint4 at 10051f8.

## Reviewed and integrated

- Active Recall UI with source upload/pasted text, stored materials, draft generation,
  source quotes, draft edits/activation, answer reveal, remembered/forgot ratings,
  review progress, deletion and paged long lists. Default uses real versioned API;
  explicit dev-only recallMock=1 remains a separate development tool.
- Real Timer backend: server Clock, 1500-second cap, RUNNING/PAUSED/COMPLETED/CANCELLED,
  active/read/history/goal totals, versions, receipts and unique one-active guard.
  Owner lock plus DB constraints protect concurrent writes. Cancelled sessions do
  not count as completed study time; pause/break time is excluded.
- Existing Timer client/UI connects to the matching backend contract without mocks.
  Existing landing, carousel, scroll reveal, weekly progress, shared budget/preferences
  and previous Recall transaction/validation/provider safety fixes retained.
- New source changes also validate overlong goal subjects as 400, preserve multiline
  Recall text and improve keyboard handling and reduced-motion behavior.

## Integration corrections

- Resolved App navigation conflicts; added #recall to the shared router and reset
  Recall page state when the authenticated account changes. Existing #timer and
  landing routes remain intact.
- Preserved existing applied V8__active_recall_schema.sql byte-for-byte. Source had
  identical Recall schema renamed to V9 and Timer as V8. Drop that duplicate Recall
  migration and append previously unapplied Timer as V9__study_sessions.sql.
  No applied migration edited, no Flyway repair/clean/out-of-order override.
  This test branch's numbering differs from source; review migration histories before
  sharing with an environment that has already applied the source branch's V8/V9.
- Added a disposable-database-name guard before the incoming Timer integration suite
  deletes app_users. Its test database is never the preview/user database.

## Verification

- Frontend lint clean, 139/139 tests and build passed.
- Full backend test with goal/auth/planning/calendar integration flags and explicit
  study_planner_test at 127.0.0.1:55433: 409 executions, zero failures/errors,
  three optional private-PDF skips (406 passed). Includes real PostgreSQL Timer
  concurrency/action/replay tests and existing Recall persistence/security checks.
- Separate verify -DskipTests packaged successfully after full tests.
- Restarted preview backend on 18084 with separate study_planner_preview on 55433;
  V9 applied successfully. Calendar remote sync remains disabled; no .env/key changes.
- Live HTTP via Vite with a new synthetic account: start/replay/second-start conflict,
  active read, pause and reload without accumulating paused time, resume, finish,
  repeat finish without duplicate accounting, goal totals, no automatic task completion,
  and history survives goal/task deletion with nullable links and retained titles.
- Recall live HTTP: source persisted, missing provider key produces 503 AI_UNAVAILABLE,
  no fabricated deck, delete goal cascades Recall materials. Existing user preview
  account was not mutated by these smoke scenarios.
- An optional pre-migration count script could not find psql; no count/fingerprint
  comparison is claimed. The migration is additive; no existing app rows are rewritten.
- No browser UI/click/mobile/offline or real Gemini generation verification claimed.
  Browser availability was absent earlier in this session. No GitHub push/main merge.

## Manual preview

- http://127.0.0.1:5173/#recall - select goal, save source, generate/review/activate cards.
  Generation requires private Gemini configuration; this preview currently has none.
- http://127.0.0.1:5173/#timer - choose a real task/date, start, pause, refresh, resume
  and finish; history should persist and task completion remain unchanged.
- Existing preview login: dev2.preview@example.test (previously shared test account).
- Refresh the frontend after integration. Success of API/client tests is not visual QA.
