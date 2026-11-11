# Shared daily study budget

Status: Completed. Branch integration/sprint2-sprint3-all8-test; existing worktree changes preserved. No commit/push requested or performed.

PO request: constrain all goals to the student's total available minutes each day. Keep per-goal limits as additional caps.

Implementation: additive V6 nullable account budget (no guessed availability), default minutes plus dated overrides including zero; owned GET/PUT settings with version; reject reducing future caps below saved work; include completed tasks. Missing setup blocks new reservations with actionable 422; completion/deletion remain possible. AI date allocator uses shared remaining capacity; confirmation rechecks under an account lock before goal lock; manual writes use the same lock. UI settings and unfiltered daily usage on Goals and Study Plan.

Acceptance: two 90-minute goals cannot exceed a common 120-minute day; AI retains unscheduled estimates; move/confirmation enforce aggregate capacity; rest days, user isolation, stale settings, replay and concurrent writes are safe. Existing tasks are not moved/deleted. Test only disposable study_planner_test; verify frontend, backend, migration and runtime. No live Google/Gemini writes or .env changes.

Progress: V6, owned GET/PUT settings, shared allocator constraints, locked aggregate validation, UI on Goals/Study Plan and regression tests implemented. Existing per-goal integration fixtures explicitly configure 1440 minutes so those suites continue testing their original per-goal constraints; dedicated shared-budget cases use realistic 120/60/0 values.

Validation 2026-11-11:

- Initial integration invocation failed because local PostgreSQL was stopped. Recovered the existing portable cluster from its existing data directory; no initdb/reset. Then 16/16 focused tests passed (SharedBudgetPostgresTest, PersistentPlanningPostgresTest, TaskServiceTest).
- Full backend: explicit study_planner_test datasource and goal/auth/calendar/planning flags, `mvn ... test -B -ntp`: 274 executions, 271 passed, three private-PDF opt-ins skipped, zero failures/errors. Package succeeded separately with tests already verified.
- Five new HTTP/PostgreSQL cases cover required setup/auth/CSRF/invalid dates; per-user totals and completed work; zero days and move capacity release; settings reductions/version conflicts; AI keeping honest unscheduled estimates and detecting another goal's later reservation; concurrent manual/AI confirmation; and concurrent settings reduction/task creation. Existing migration rehearsal now also proves V6 leaves legacy availability null while retaining goal scope/history.
- Frontend lint, 89/89 client/logic tests and production build passed. Final StudyPlan adjustment callback is navigation-only; no browser E2E claim.
- Runtime: V6 applied to study_planner_demo. Backend 18083 and Vite 5173 running; direct/proxy health both UP; unauthenticated study-budget API through proxy returns 401. Before/after read-only fingerprints match for all 4 goals, 60 tasks, 3 drafts and 13 Calendar exports; schema advanced 5 to 6. This is data-preservation evidence, not a restore rehearsal or database backup.
- Existing .env unchanged. No real Gemini/Google calls or user-data edits made by tests; no browser/mobile rendering validation.

Demo: log in at http://127.0.0.1:5173; open Goals or Study Plan and save the actual shared daily minutes. Defaults are intentionally blank for old accounts. If existing reservations exceed the intended cap, first set a cap that accommodates them, move/delete pending work, then lower it. Add work for two separate 90-minute goals on a 120-minute day: only 120 total may be reserved. AI retains work that cannot fit. Set an empty future day to zero; it remains a rest day after reload.

Recovery/limits: additive V6 only; do not edit applied migrations or drop data to undo. No .env change required. No automatic cross-goal reprioritization, task movement, global clock slots or Google synchronization added. All capacity uses task date/minutes; completed work still consumes the day's allocation. Global date convention is Asia/Ho_Chi_Minh. Requirements and the linked product contract own accepted behavior.
