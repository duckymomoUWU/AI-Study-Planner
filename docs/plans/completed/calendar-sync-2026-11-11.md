# App-to-Google Calendar update/delete synchronization

Status: Completed. Branch integration/sprint2-sprint3-all8-test; unrelated worktree changes preserved. No commit or Git push.

Authority: PO explicitly approved implementing one-way update/delete after discussing the limits. This supersedes ADR 0001's earlier follow-up restriction for this scope; two-way sync remains excluded.

Outcome: edits to exported task title/date/minutes or goal subject update the same marked Google event; deleting a task/goal removes only that app-owned event. Local mutations and durable outbox intent commit together. Google failure cannot roll back user changes. Keep old event if new date has no valid slot, display pending/blocked status, provide retries. No auto migration of existing events or live provider mutation during verification.

Design: additive V7 trigger-backed outbox, revision plus lease to serialize work per event without holding database locks across Google calls. Worker uses conditional ETag patch/delete, verifies owner/task markers and stored event ID, treats missing delete as success. Date/minute changes recheck all selected busy calendars, living hours and breaks; event listing excludes only its own target event (never subtract a FreeBusy interval). Preserve original export calendar when default target changes. Existing export receipts remain the event mapping.

Validation plan: deterministic unit tests for update/delete, self-busy exclusion, no-slot, stale revision and retries; real PostgreSQL trigger/tombstone/rollback tests on study_planner_test only; frontend sync status/client checks, lint/build. Test scheduler disabled to prevent accidental live writes. Apply additive migration to demo only after tests, preserve data and .env, update Harness evidence, restart for PO testing. No Git push requested.

## Results

- Implemented V7 transactional outbox triggers, leased/revisioned worker, conditional PATCH/DELETE, busy reallocation, owned status/retry API and UI on Goals/Study Plan (including daily view). Delete confirmations explain the remote consequence. Original calendar/event mapping remains stable. No backfill or real Google write performed during verification.
- Initial focused run found a test expecting TaskNotFound while a foreign goal correctly raises GoalNotFound (same public 404); assertion corrected. Rerun 28/28 targeted tests passed.
- Full backend against explicit study_planner_test with goal/auth/calendar/planning integration flags: 295 executions, 292 passed, three private-PDF opt-ins skipped, zero failures/errors. New tests: 10 service/parser, 3 mocked HTTP transport, 5 PostgreSQL/MockMvc. Tests prove transactional rollback, goal-delete tombstone, owner isolation, lease protection, later-delete survival, owner deletion cleanup, ETag/204/missing delete, no-slot preservation, reconnect/retry and no duplicate INSERT. Test resource disables the scheduler; PostgreSQL test asserts it is absent.
- Frontend lint, 91/91 client/logic tests and build passed. No browser/mobile click or rendering proof.
- Additional focused lock proof after the full run: CalendarSyncPostgresTest 6/6 passed, including NO KEY UPDATE allowing a real REQUIRES_NEW receipt FK insert under the outer owner lock (5-second bound). This is a separate focused run, not an aggregated full-suite count; production source remained unchanged.
- Package succeeded; demo schema advanced 6→7, backend 18083 restarted, Vite 5173 available. Direct and proxy health UP; anonymous sync API via proxy returned 401. Pre/post read-only fingerprints match for 3 goals, 58 tasks, 5 drafts and 13 Calendar exports. This is migration preservation, not backup/restore verification. Existing .env and encryption key unchanged.
- Primary API references checked: official Google events.patch/delete/list and resource ETag documentation, linked in contract. Queue starts empty and old exported events are not touched by installation. Later authorized task/goal edits will trigger the new worker.

## PO demo and limits

At http://127.0.0.1:5173 log in if needed. Choose an already-exported test task and rename it; observe pending then synced status and the same Google event. Change to an available day/duration; check busy/living-hour avoidance. Try a full or disabled day: local app change stays saved, event stays at its old slot, status needs adjustment. Restore a valid day/settings and retry. Delete an exported test task/goal after reviewing the confirmation; its event should disappear after worker processing. These real-provider steps are for PO testing, not claimed automated proof.

Backend must remain running. Initial wait up to 30 seconds, subsequent worker ticks every 15 seconds plus retry backoff. Missing remote events are not auto recreated; lost calendar permissions require repair. No reverse sync or retroactive cleanup of edits/deletions before V7. User-account deletion is not a remote cleanup flow. Remote slots cannot be reserved atomically against concurrent edits by other Google apps.
