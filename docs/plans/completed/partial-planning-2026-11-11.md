# Partial study plan confirmation

Follow-up UX accepted and delivered 2026-11-11: replace the long partial-save button with a short Lưu phần đã xếp label and an incomplete-plan warning with scheduled/total counts, estimate shortfall and three distinct actions. Adjust-time targets shared settings when actual reduced-capacity metadata is present; topic selection targets the existing goal editor, including from Study Plan. Keep saved work and all pending items. Old scopes without binding metadata use neutral guidance and offer a shared-budget link; never infer binding from sharedBudgetApplied alone.

Follow-up validation: SharedBudgetPostgresTest 5/5 passed, including the new reduced-capacity metadata assertion. Frontend 90/90 client/logic tests passed; final lint/build passed without warnings. A local Vite SSR check renders 12/32 partial work, total-shortfall/shared-limit guidance, fragmented daily capacity with no false total shortage, and complete plans with the normal confirm button. SSR does not prove browser clicks, layout or editor focus. Backend package/restart and health checks performed; no schema migration, .env change, live provider call or Git push. This focused run is not a new full-backend-suite claim.

Status: Completed. Branch: integration/sprint2-sprint3-all8-test.

PO accepted: honest estimates before allocation; report insufficient time; offer more minutes, fewer topics or saving scheduled work while retaining all deferred work.

Implementation: explicit allowPartial confirmation, atomic durable remainder draft, pending-list ownership API, deterministic reschedule using saved goal configuration, UI warnings/actions. No automatic deletion of work, shrinking estimates or provider writes. Existing worktree changes and .env preserved. No schema migration required.

Acceptance: 180 minutes against 120 available retains all work; explicit partial save creates only scheduled tasks; remainder survives reload/expiry and is discoverable even after a newer AI draft; retry creates no duplicates; invalid/foreign requests cannot mutate data; increasing minutes allows rescheduling without an AI call.

Validation (2026-11-11): full backend run with goal/auth/calendar/planning integration flags: 269 executions, 266 passed, three private-PDF opt-ins skipped, zero failures/errors. Datasource explicitly study_planner_test. Subsequent focused regression after preserving remainder reasons: PersistentPlanningPostgresTest + PlanSchedulingTest, 11/11 passed. The partial scenario also verifies rejection without explicit permission, forged deferred topic rollback, same-key replay/conflict, edited deferred estimates, expiry-independent reload, pending discovery after newer drafts, foreign access rejection, budget increase and no extra AI call on reschedule.

Frontend: npm.cmd run lint, npm.cmd test (86/86) and npm.cmd run build passed; lint/build repeated after latest adjustment-link update. Client tests exercise review totals and partial/deferred payloads, retry stability and API paths; they are not browser rendering tests.

Runtime: package succeeded; existing backend on port 18083 replaced with new JAR. Prior JAR copied to ignored .tools/backend-before-partial-planning.jar. Direct health and frontend proxy health both UP; unauthenticated pending API through 5173 returns 401. No schema changes or .env edits. No demo rows intentionally created/edited by tests. No real Gemini/Google calls, browser E2E or live data fingerprint claimed. No Git push.

Demo: open a goal with a draft exceeding the remaining budget (or a task too long for any individual day). Observe required/available minutes and per-item reason. Save scheduled tasks; reopen goal to see the incomplete-coverage warning and pending list. Edit goal minutes, reopen pending part and reschedule, then confirm. To reduce topics, edit goal scope and generate a fresh AI draft; old deferred parts remain visible and are not silently deleted.

Limits: reschedule uses the stored draft's work/estimates, not unsaved form edits. Removing a pending topic from goal settings yields conflict on reschedule; regenerate for the new scope. Existing draft TTL remains for ordinary unconfirmed proposals; the retained partial backlog is exempt from expiry but confirmation still checks goal version, date and budget. No automatic plan adjustment, global cross-goal budget or Google event synchronization added.
