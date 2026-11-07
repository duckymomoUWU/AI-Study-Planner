# AI Study Planner documentation map

## Project authority

- [Project conventions](../AGENTS.md): repository layout, scope and agent rules.
- [Requirements](../requirements.md): accepted product scope and business rules; these describe planned features, not proof of implementation.
- [Setup](../setup.md): Windows/PowerShell prerequisites, PostgreSQL lifecycle and application startup.
- [Verification harness](../.agents/harness/README.md): maintained build/test commands, live checks and known verification gaps.
- [Frontend guide](../frontend/README.md): frontend operation and proxy behavior.
- [Initial schema](../backend/src/main/resources/db/migration/V1__initial_schema.sql): current database structure; not an implemented business API.
- [Backend API rules](backend-api-rules.md): mandatory rules for backend code (`/api/v1`, response envelope, error codes, ownership, concurrency).
- [ASP-9 Goal API contract](product/ASP-9-goals-api.md): Goal CRUD contract, errors, timezone rule and AC mapping; the endpoints are implemented on the development branch.
- [Goal progress](product/goal-progress.md): view completed and remaining tasks of a goal (API, UI, 0/0 rule).
- [ASP-9 verification evidence](product/ASP-9-verification.md): AC-to-test traceability with the results that were actually run and the limits not verified.

## Repository Harness

- [Workflow](WORKFLOW.md): work shape, authority and evidence guidance.
- [Invariant encoding](patterns/encoding-invariants.md): optional native validation guidance.
- [Decisions](decisions/README.md) and [plans](plans/README.md): optional durable records for accepted decisions and complex work.
- `templates/`: optional decision, execution-plan, application-runbook and Harness-improvement templates.
- [Product document guidance](product/README.md): generic guidance; requirements remain authoritative at the existing root document. Do not duplicate requirements or verification commands here.

Harness status and doctor validate the Harness installation, not application behavior.
