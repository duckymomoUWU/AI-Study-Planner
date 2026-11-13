# Planned and actual time per task

Branch: `integration/dev3-recall-test`.

## Behavior

Weekly/day schedules and a known selected task in Timer show estimated minutes next
to actual time from all COMPLETED study sessions linked to that task. Seconds are
preserved, not rounded up. RUNNING, PAUSED, CANCELLED and UI breaks are excluded.
Several sessions accumulate; completion or reopening a task does not reset time.
Moving the task keeps its total because aggregation uses task ID, not session date.
No remaining-time estimate is inferred by subtracting actual time from the plan.

Each display offers an explicit estimate editor (1-480 integer minutes), using the
existing PATCH task API. The server checks the shared daily budget, including other
tasks and completed tasks. Failure leaves the editor/input open and saved estimate
unchanged. Successful edits refresh the schedule but never alter recorded sessions.
Task completion remains a separate explicit choice after a saved Timer session.

## API

GET `/api/v1/study-sessions/task-totals?taskIds=<uuid>,<uuid>` requires login and
1-100 valid UUIDs per request. Returns `{data:[{taskId,completedSessionCount,
totalFocusSeconds}]}`. Every requested task must belong to the principal; missing or
foreign tasks give 404 for the batch. No sessions yields an explicit zero row.
The query joins owned tasks and goals to completed owner sessions in PostgreSQL;
does not load every history page or run one query for every task.
The client deduplicates IDs and batches over 100; missing/failed data is shown as
unavailable, never fabricated zero. Focus refreshes totals; Timer version changes
refresh after session completion. No schema migration.

## Verification

- Frontend lint, 145/145 tests and build passed; existing SSR smoke passed.
- Tests cover time formatting, 205-task batching, and unavailable/incomplete data.
- Full backend test with goal/auth/planning/calendar flags and explicit disposable
  study_planner_test at 127.0.0.1:55433: 411 executions, zero failures/errors,
  three optional private-PDF skips (408 passed).
- Added PostgreSQL cases: two 20-minute sessions total 40 minutes; cancelled/running
  sessions excluded; owner/anonymous/invalid/oversized batches; estimates remain
  independent; changing estimate checks shared budget and preserves actual time.
- Separate skipTests verify packaged. Preview restarted on 18084 against separate
  study_planner_preview; ignored .env preserved, Calendar remote processing disabled.
- Live synthetic-account API smoke: explicit zeros, finish total, cancel excluded,
  edit beyond 60-minute shared budget rejected without changing 40-minute estimate,
  edit to 35 accepted and actual time retained. No existing user data changed.
- No real browser clicks/layout validation claimed. No provider calls needed for
  this feature, no key or schema changes, no GitHub push.

## Demo

Open #daily, inspect a task's Dự kiến / Đã học. Run and finish a short Timer session,
then return to schedule. The actual time increases while estimated minutes stay
unchanged. Use Điều chỉnh thời lượng and enter a value that exceeds the shared
budget: see the error and retained original estimate. Lower it within capacity and
save; actual time should remain the same.
