# Product documentation

[requirements.md](../../requirements.md) owns accepted product scope and business rules. Contracts below refine those rules; code, tests and runtime evidence establish implementation. Keep startup/environment instructions in [setup.md](../../setup.md), validation commands in [the verification harness](../../.agents/harness/README.md), and historical delivery evidence in [completed plans](../plans/completed/README.md).

| Domain | Contract |
| --- | --- |
| Authentication | [ASP-12 guide](../ASP-12-auth-api-guide.md) |
| Goal CRUD | [ASP-9 goals API](ASP-9-goals-api.md) |
| Task CRUD and AI draft confirmation | [Integrated tasks and plans](integrated-tasks-and-plans.md) |
| Task completion and goal progress | [Completion](task-completion.md), [progress](goal-progress.md) |
| Upload and scoped syllabus planning | [Upload](syllabus-upload.md), [source-backed planning](syllabus-revision-plan.md) |
| Google Calendar | [OAuth, free time and export](google-calendar.md) |
| Shared daily study budget | [All-goal capacity and concurrency](shared-study-budget.md) |

Older verification documents describe their dated runs. Use the current Harness for commands and the [2026-11-11 handoff](../plans/completed/core-study-calendar-2026-11-11.md) for the consolidated implementation and proof limits. Do not duplicate requirements or infer product policy from defaults alone.
