# AI Study Planner documentation map

## Project authority

- [Project conventions](../AGENTS.md): repository layout, scope and agent rules.
- [Requirements](../requirements.md): accepted product scope and business rules; these describe planned features, not proof of implementation.
- [Setup](../setup.md): Windows/PowerShell prerequisites, PostgreSQL lifecycle and application startup.
- [Verification harness](../.agents/harness/README.md): maintained build/test commands, live checks and known verification gaps.
- [Frontend guide](../frontend/README.md): frontend operation and proxy behavior.
- [Database migrations](../backend/src/main/resources/db/migration/): schema history; V3 adds topic provenance and V4 adds Google Calendar connection/preferences/preview/export receipts.
- [Backend API rules](backend-api-rules.md): mandatory rules for backend code (`/api/v1`, response envelope, error codes, ownership, concurrency).
- [ASP-9 Goal API contract](product/ASP-9-goals-api.md): Goal CRUD contract, errors, timezone rule and AC mapping; the endpoints are implemented on the development branch.
- [Task completion](product/task-completion.md): mark a task done or re-open it (API, UI, version effect, open decisions).
- [Goal progress](product/goal-progress.md): view completed and remaining tasks of a goal (API, UI, 0/0 rule).
- [Weekly progress (US-17)](product/weekly-progress.md): completed and total tasks per Monday-to-Sunday week (API `GET /api/v1/progress/weekly`, UI, 0/0 rule, open PO points); evidence in [weekly-progress-2026-11-12.md](plans/completed/weekly-progress-2026-11-12.md).
- [ASP-9 verification evidence](product/ASP-9-verification.md): AC-to-test traceability with the results that were actually run and the limits not verified.
- [Syllabus and scoped planning](product/syllabus-revision-plan.md): extracted topics, source review, per-day minutes, AI draft and confirmation.
- [Google Calendar](product/google-calendar.md): OAuth, busy calendars, living hours, preview/confirm and retry behavior.
- [Current handoff and verification](plans/completed/core-study-calendar-2026-11-11.md): consolidated work through 2026-11-11; historical test runs, user observations and remaining limits.
- [Core verification history](reviews/syllabus-core-2026-11-10.md): earlier evidence; current commands remain in the verification harness.

## Repository Harness

- [Workflow](WORKFLOW.md): work shape, authority and evidence guidance.
- [Invariant encoding](patterns/encoding-invariants.md): optional native validation guidance.
- [Decisions](decisions/README.md) and [plans](plans/README.md): optional durable records for accepted decisions and complex work.
- `templates/`: optional decision, execution-plan, application-runbook and Harness-improvement templates.
- [Product document guidance](product/README.md): generic guidance; requirements remain authoritative at the existing root document. Do not duplicate requirements or verification commands here.

Harness status and doctor validate the Harness installation, not application behavior.

- [Task và bản nháp trên nhánh tích hợp](product/integrated-tasks-and-plans.md)
