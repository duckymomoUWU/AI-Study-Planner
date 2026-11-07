# ASP-22 / ASP-9 — Goal API contract (proposed)

**Status:** Design contract for Jira [ASP-9](https://ai-study-planner-nhom.atlassian.net/browse/ASP-9). **Implementation status on this branch (ASP-23, ASP-24):** `POST`, `GET` (list, detail) and `PUT /api/goals/{goalId}` are implemented (`GoalController`) and covered by `GoalControllerTest` and the PostgreSQL tests (`GoalApiPostgresIntegrationTest`, `GoalServicePostgresIntegrationTest`). **`DELETE` is not exposed over HTTP yet** (ASP-26); the service method exists but has no controller. This document is the baseline for the remaining ASP-9 tasks; the endpoints are not on `main` until the stacked PRs merge. [requirements.md](../../requirements.md) remains the owner of product rules; this file only turns them into an API and data contract.

## 1. Current state

State when the contract was written (`main`): no controllers, services, repositories, tests or authentication; `/actuator/health` was the only endpoint. State on this branch:

| Area | Finding |
| --- | --- |
| Backend | Spring Boot 3.5, Java 21, JDBC, Flyway, PostgreSQL. `GoalPolicy`, `GoalJdbcRepository`, `GoalService` and `GoalController` (create, list, detail) with tests. |
| Business API | `POST`/`GET /api/goals` and `GET /api/goals/{goalId}`. `PUT`/`DELETE` are not routed yet. |
| Authentication | Session login, CSRF and `AppUserPrincipal` from ASP-12 (merged from `dev1`). |
| Frontend | React + Vite with auth screens and a goals page/client from other branches; Vite proxies `/api` to the backend. |
| Schema | `V1__initial_schema.sql` already defines `app_users`, `study_goals`, `study_tasks`, `plan_drafts`. |

## 2. Data model (from `V1__initial_schema.sql`)

`study_goals`

| Column | Type | Constraint |
| --- | --- | --- |
| `id` | UUID | PK, default `gen_random_uuid()` |
| `user_id` | UUID | NOT NULL, FK `app_users(id)` ON DELETE CASCADE; indexed |
| `subject` | VARCHAR(100) | NOT NULL, `length(trim(subject)) > 0` |
| `topics` | TEXT | NOT NULL, `length(trim(topics)) > 0` |
| `start_date`, `exam_date` | DATE | NOT NULL; `exam_date > start_date` and `exam_date - start_date <= 90` |
| `minutes_per_day` | INTEGER | NOT NULL, `BETWEEN 15 AND 480` |
| `timezone` | VARCHAR(64) | NOT NULL, default `Asia/Ho_Chi_Minh` |
| `version` | INTEGER | NOT NULL, default 0 (optimistic locking) |
| `created_at` | TIMESTAMPTZ | NOT NULL |

Related tables: `study_tasks.goal_id` and `plan_drafts.goal_id` reference `study_goals(id)` with `ON DELETE CASCADE`. Tasks carry `scheduled_date` and `estimated_minutes` (1–480).

**Migration impact: none required.** V1 has every field and constraint this contract needs. V1 is applied and must not be edited; any later change goes in a new `V<n>__*.sql`.

Not enforced by the database (the service must enforce them): exam date not in the past, tasks not on or after the exam date, per-day task total within `minutes_per_day`, and ownership.

## 3. Endpoints

All routes require an authenticated user (ASP-12). Missing authentication returns **401**. An unknown ID and another user's ID both return the same **404**, so ownership is not leaked. The owner is always taken from the server-side session; `userId` in a body, query or path is never accepted.

| Method | Path | Success | Purpose |
| --- | --- | --- | --- |
| POST | `/api/goals` | 201 | Create a goal for the current user |
| GET | `/api/goals` | 200 | List the current user's goals |
| GET | `/api/goals/{goalId}` | 200 | Read one owned goal |
| PUT | `/api/goals/{goalId}` | 200 | Replace editable fields of an owned goal |
| DELETE | `/api/goals/{goalId}` | 204 | Delete an owned goal and its tasks and drafts |

### Create — `POST /api/goals`

```json
{
  "subject": "Giải tích 2",
  "topics": "Tích phân; phương trình vi phân",
  "startDate": "2026-11-09",
  "examDate": "2026-12-05",
  "minutesPerDay": 90
}
```

- `subject`, `topics`: required; trimmed before validation and storage; non-empty after trim; `subject` at most 100 characters.
- `startDate`, `examDate`: ISO `YYYY-MM-DD`, no time part.
- `examDate`: not before today (in the goal's timezone), strictly after `startDate`, and at most 90 calendar days after it.
- `minutesPerDay`: JSON integer 15–480 inclusive. Strings, decimals and null are invalid.
- `startDate` may be in the past; requirements do not forbid it.
- `timezone`, `version`, `userId` and task data are not accepted on create; extra fields are ignored. The server sets the owner and timezone.

Response (201; the same shape for GET and PUT with 200). The ID is illustrative.

```json
{
  "id": "3f3ac7a4-13a9-4763-b271-0e1c9f0e641d",
  "subject": "Giải tích 2",
  "topics": "Tích phân; phương trình vi phân",
  "startDate": "2026-11-09",
  "examDate": "2026-12-05",
  "minutesPerDay": 90,
  "timezone": "Asia/Ho_Chi_Minh",
  "version": 0
}
```

`GET /api/goals` returns `{"items": [ ...goals ]}`, and `{"items": []}` when there are none. Responses never include the owner ID, password hash or other users' data.

### Update — `PUT /api/goals/{goalId}`

Body: the five editable fields above **plus the current integer `version`** (required). Same validation as create.

Before saving, in one transaction with the goal row locked, the server loads the goal's existing tasks and rejects the whole update if:
- a task is scheduled on or after the new `examDate`; or
- for any calendar day, the sum of `estimated_minutes` exceeds the new `minutesPerDay`.

A stale `version` returns 409 and nothing is written. On success `version` increases by 1. The server never moves or edits tasks automatically.

### Delete — `DELETE /api/goals/{goalId}`

The UI must ask for confirmation first; cancelling sends no request. The delete is one transaction and removes the goal plus its `study_tasks` and `plan_drafts` through the existing cascading foreign keys. Other goals are unaffected.

## 4. Errors

Every error is JSON:

```json
{
  "code": "validation_failed",
  "message": "Dữ liệu mục tiêu không hợp lệ.",
  "fieldErrors": [
    { "field": "examDate", "code": "not_after_start", "message": "Ngày thi phải sau ngày bắt đầu." }
  ]
}
```

`fieldErrors` is present for 400 and for task conflicts, and lets the UI mark the right field. `field` is one of `subject`, `topics`, `startDate`, `examDate`, `minutesPerDay`, `version`.

| HTTP | `code` | When | Field codes |
| --- | --- | --- | --- |
| 400 | `validation_failed` | Invalid or missing input, malformed JSON | `required`, `invalid_type`, `invalid_date`, `past_date`, `not_after_start`, `over_90_days`, `out_of_range` |
| 401 | `unauthenticated` | No valid session | — |
| 404 | `goal_not_found` | Unknown ID, malformed ID, or another user's goal | — |
| 409 | `stale_goal_version` | `version` does not match the stored goal | — |
| 409 | `goal_tasks_conflict` | Existing tasks violate the proposed exam date or budget | `task_after_deadline` (on `examDate`), `daily_budget_exceeded` (on `minutesPerDay`, message names the day) |
| 500 | `internal_error` | Unexpected failure | — |

Errors and logs must not contain stack traces, SQL, passwords, API keys or the user's study content.

## 5. Current date and timezone

- The server decides `timezone`; the client cannot send it. The initial value is the column default `Asia/Ho_Chi_Minh`.
- "Today" for the past-date rule is `LocalDate.now(clock.withZone(ZoneId.of(goalTimezone)))`. This date is passed into the validation logic and tests inject a fixed `Clock`, so results do not depend on the machine date.
- Ranges use calendar dates: `examDate - startDate` must be 1 to 90 days.

## 6. Mapping: acceptance criteria → API / service / database

| ASP-9 AC | API | Service rule | Database |
| --- | --- | --- | --- |
| 1. Create with all five fields | `POST /api/goals` → 201 | Owner from session; trim; validate | Insert into `study_goals` (`user_id`, `timezone`, `version = 0`) |
| 1. Subject/topics not blank after trim | 400 `required` | Trim then check | `CHECK length(trim(...)) > 0` |
| 1. Exam date not past, after start | 400 `past_date`, `not_after_start` | Compare with today in goal timezone | `CHECK exam_date > start_date` (past-date is service only) |
| 1. At most 90 days | 400 `over_90_days` | Day difference ≤ 90 | `CHECK exam_date - start_date <= 90` |
| 1. 15–480 integer minutes | 400 `out_of_range` | Integer in range | `CHECK BETWEEN 15 AND 480` |
| 1. Saved and shown after reload | `GET /api/goals` | Owner-scoped list | Persisted row |
| 2. See only own goals | `GET` list/detail → 200 or 404 | Query by `id` and `user_id` together | Index on `user_id` |
| 3. Edit | `PUT /api/goals/{id}` | Lock row, check `version`, validate | Update `WHERE id AND user_id AND version`, then `version + 1` |
| 3. Edit checks existing tasks | 409 `goal_tasks_conflict` | Tasks on/after new exam date; per-day total vs new budget | Read `study_tasks` in the same transaction |
| 4. Confirm, then delete with tasks and drafts | `DELETE` → 204 | Owner-scoped delete; confirmation is a UI step | `ON DELETE CASCADE` on both child tables |
| 4. Cannot delete another user's goal | 404 | Owner-scoped delete | — |
| 5. No task on/after exam date; day total within budget | Enforced by task and AI stories | Reuse the same rules in the same transaction as task writes | `study_tasks` (service-level only) |
| 5. Report missing minutes when a plan cannot fit | Reported by ASP-13/14 | Out of scope for goal CRUD | — |
| DoD: server validates and checks ownership everywhere | 400 / 401 / 404 | No client-supplied user ID | `user_id` FK |

## 7. Responsibility

| Area | Owner |
| --- | --- |
| Goal CRUD, validation, owner-scoped queries, update checks against tasks, cascade delete, API errors, goal UI | ASP-9 |
| Registration, login, session/token, logout, CSRF, mapping a request to a trusted user ID | ASP-12 |
| Task CRUD and daily view | ASP-10, ASP-11 |
| AI proposals and plan approval (AI only suggests; never saves on its own) | ASP-13, ASP-14 |
| Progress display | ASP-16, ASP-17 |

ASP-9 only consumes a trusted current-user ID from ASP-12. Goal endpoints must not be implemented as working, nor described as working, until that identity exists.

## 8. Concurrency notes

- Every goal query includes `user_id`; never fetch by ID and filter afterwards.
- The daily-minutes and deadline checks run in the same transaction as the write. Task writes (ASP-10) must take the same lock on the parent goal row to avoid races with goal edits.
- `version` gives optimistic locking for concurrent goal edits.
- Update flow (ASP-24): lock the owned goal row (`SELECT ... FOR UPDATE`), compare `version`, read the goal's tasks, validate, then `UPDATE ... WHERE version = ?`, all in one transaction. A rejected update rolls back, so nothing is saved halfway.
- ASP-9 vs ASP-10: goal update only checks the tasks that exist when the goal is edited. ASP-10 is responsible for checking exam date and daily budget when a task is created or edited, and its task writes must first lock the parent goal row (`GoalJdbcRepository.lockOwned`) so a task insert and a goal edit cannot both pass their checks at the same time.
- Update conflicts: 409 `goal_tasks_conflict` lists, in `fieldErrors`, every task on or after the new exam date (task title and date) and every day whose total exceeds the new budget (day, total, minutes over). If the input itself is invalid (400 `validation_failed`), input errors are reported and the status is 400 even when tasks also conflict.

## 9. Verification checklist

"Covered" means a test that passed against the ASP-23/24 code (including real PostgreSQL for create/list/detail). DELETE rows stay Pending until ASP-26 exposes it.

| Check | Expected | Status |
| --- | --- | --- |
| Blank or whitespace-only `subject`/`topics` | 400 `required` | Covered (ASP-23) |
| Exam date in the past (goal timezone) | 400 `past_date` | Covered (ASP-23) |
| Exam date equal to or before start | 400 `not_after_start` | Covered (ASP-23) |
| 90 days accepted, 91 days rejected | 201 / 400 `over_90_days` | Covered (ASP-23) |
| 14 and 481 minutes rejected; 15 and 480 accepted | 400 / 201 | Covered (ASP-23) |
| Decimal, string or null minutes | 400 | Covered (ASP-23) |
| Another account's goal: get, update, delete | 404, nothing leaked | get, update Covered (ASP-23/24); delete Pending |
| No session | 401 | Covered (ASP-23) |
| Edit exam date to a day that has a task | 409 `goal_tasks_conflict`, nothing saved | Covered (ASP-24) |
| Edit budget below a day's task total | 409 `goal_tasks_conflict`, nothing saved | Covered (ASP-24) |
| Stale `version` on PUT | 409 `stale_goal_version` | Covered (ASP-24) |
| Delete removes tasks and drafts; other goals untouched | 204, rows gone | Pending |
| Cancel in the confirmation dialog | No DELETE request | Pending |
| Logs contain no passwords, keys or study content | None present | Pending |

## 10. Open business decision

If a user moves `startDate` later and existing tasks now fall before it, should the update be blocked or should the tasks stay? ASP-9 only blocks tasks on or after the exam date and over-budget days. The PO must decide before this extra behavior is implemented.
