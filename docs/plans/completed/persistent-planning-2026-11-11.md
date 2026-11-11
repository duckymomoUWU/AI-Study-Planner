# Execution Plan: Persistent planning settings across the core lifecycle

Date: 2026-11-11

## Status

Completed

## Outcome

Keep selected topics, priority/source metadata and per-day minute overrides with the goal. Reopening, regenerating, confirmation and manual movement use the same budget, including a zero-minute day. Preserve completed tasks and reject edits that invalidate saved work.

## Context

PO accepted the core audit's fix for lost/ignored daily settings. See requirements.md, docs/product/syllabus-revision-plan.md and the prior core handoff. Existing changes in the integration worktree must be preserved; no Git push is requested.

## Scope

In scope: V5 migration/backfill, persisted planningScope on Goal API, shared budget rules, form hydration/regeneration, regression tests and local restart.

Out of scope: automatic adaptive replanning, replacing/deleting completed tasks, changing Google events after task edits, timer/recall/payments, and production deployment.

## Approach

Add goal-owned JSON settings without replacing tasks. Backfill from the latest available scoped draft. Save user settings before the provider call, increment goal version when they change, and use them for generation/confirmation/manual CRUD. Keep AI mock tests separate from live provider calls.

## Risks And Recovery

- Run integration suites only against study_planner_test, never the demo database.
- V5 is additive and tested from a fresh V4 schema with old scoped data and completed history. Do not edit already applied V1–V4.
- Preserve .env/OAuth credentials and existing Calendar exports. Keep the prior runnable JAR locally before restart; no destructive DB rollback.

## Progress

- [x] Persist settings and share budget rules across services.
- [x] Restore settings in edit/regeneration UI and retain zero-day overrides.
- [x] API/DB scenario validates reload, movement, regeneration, confirm replay and completed history.
- [x] Rehearse V4→V5 with synthetic legacy data in an isolated schema.
- [x] Finish all backend regression/integration checks and package.
- [x] Apply/restart local runtime and record migration/preservation-test evidence.

## Decisions

- Settings are durable goal configuration; draft scope remains a frozen proposal snapshot.
- Persist only per-day values different from the default. Preserve explicit zero values; other days follow the current default.
- Do not decrease a day's budget below already stored work or move the goal start past stored tasks. Reject instead of changing completed history.
- A provider error leaves the goal/settings saved and does not create tasks. Confirmation stays explicit and idempotent.

## Validation

- Focused proof: GoalPlanningTest and PersistentPlanningPostgresTest: 6/6 passed in the initial focused run.
- Frontend: lint, 84/84 tests and build passed.
- Full backend run initially found an old assertion assuming draft creation never advances the goal version. The new settings save intentionally does; the assertion now compares against the draft's saved version to retain the rollback invariant. Rerun: 268 tests, zero failures/errors, three private-PDF opt-ins skipped (265 passed). All PostgreSQL integration flags enabled against study_planner_test.
- No private document sent to Gemini; no real Google events created or changed during these tests.

## Result

Package succeeded; demo restarted on 18083 and V5 applied. Direct health and frontend proxy health on 5173 both UP. Previous JAR retained in ignored .tools/backend-before-planning-v5.jar (not a DB backup). Preservation is verified with synthetic V4 migration/completed-task tests; no live before/after row fingerprint captured. App ready at http://127.0.0.1:5173. Existing .env unchanged. No push, browser E2E, live provider verification or full production readiness claim.
