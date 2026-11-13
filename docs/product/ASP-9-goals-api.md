# ASP-9 — Goal API contract (v1)

**Status (2026-11-07):** all five goal endpoints below are implemented on the development branch (`dev2`) and checked by MockMvc and PostgreSQL tests; they are not on `main` until the branches merge. The contract follows [Backend API rules](../backend-api-rules.md), which govern paths, the response envelope, error codes, ownership and concurrency. [requirements.md](../../requirements.md) owns the product rules; this file turns them into an API and data contract. Task, AI-draft and progress endpoints are not implemented (ASP-10, ASP-11, ASP-13, ASP-14, ASP-16/17).

## 1. Data model (`V1__initial_schema.sql`, unchanged)

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
| `version` | INTEGER | NOT NULL, default 0 (optimistic lock) |
| `created_at` | TIMESTAMPTZ | NOT NULL |

`study_tasks.goal_id` and `plan_drafts.goal_id` reference `study_goals(id)` with `ON DELETE CASCADE`. **No migration is needed**; V1 is applied and must not be edited.

The database does not enforce: exam date not in the past, tasks not on or after the exam date, per-day task totals within `minutes_per_day`, ownership. The service does.

## 2. Endpoints

All routes need a session (401 `UNAUTHORIZED` otherwise) and, for writes, the `X-CSRF-TOKEN` header (403 `CSRF_INVALID`). The owner is the logged-in user; another user's goal and an unknown or malformed ID all answer the same 404 `NOT_FOUND`.

| Method | Path | Success |
| --- | --- | --- |
| POST | `/api/v1/goals` | 201 `{data: Goal}` |
| GET | `/api/v1/goals?page=1&limit=20` | 200 `{data: [Goal], meta}` |
| GET | `/api/v1/goals/{goalId}` | 200 `{data: Goal}` |
| PATCH | `/api/v1/goals/{goalId}` | 200 `{data: Goal}` |
| DELETE | `/api/v1/goals/{goalId}` | 204, no body |

### Goal

```json
{
  "id": "3f3ac7a4-13a9-4763-b271-0e1c9f0e641d",
  "subject": "Giải tích 2",
  "topics": "Tích phân; phương trình vi phân",
  "startDate": "2026-11-09",
  "examDate": "2026-12-05",
  "minutesPerDay": 90,
  "timezone": "Asia/Ho_Chi_Minh",
  "version": 0,
  "createdAt": "2026-11-07T03:00:00Z"
}
```

(The ID is illustrative.) Responses never contain the owner ID or any password data.

### Create — `POST /api/v1/goals`

```json
{ "subject": "Giải tích 2", "topics": "Tích phân; phương trình vi phân",
  "startDate": "2026-11-09", "examDate": "2026-12-05", "minutesPerDay": 90 }
```

- Required: `subject`, `topics`, `startDate`, `examDate`, `minutesPerDay`. Optional: `timezone` (IANA name, at most 64 characters; default `Asia/Ho_Chi_Minh`).
- `subject` and `topics` are trimmed first; they must not be empty afterwards; `subject` has at most 100 characters.
- `examDate` is not in the past (in the goal's time zone), is after `startDate`, and at most 90 calendar days after it. `startDate` may be in the past.
- `minutesPerDay` is a JSON integer from 15 to 480 (strings, decimals and `null` are invalid).
- Any other field (`id`, `userId`, `createdAt`, `version`, ...) is a 400 with issue `unknown_field`.

### List — `GET /api/v1/goals`

`page` defaults to 1, `limit` to 20 (max 100); anything else is a 400. Sorted `createdAt DESC, id ASC`. `meta` is `{page, limit, totalItems, totalPages}`; an empty list is `{"data": [], "meta": {..., "totalItems": 0, "totalPages": 0}}`.

### Update — `PATCH /api/v1/goals/{goalId}`

```json
{ "minutesPerDay": 120, "expectedVersion": 0 }
```

- Send at least one of `subject`, `topics`, `startDate`, `examDate`, `minutesPerDay`, `timezone`, plus the required integer `expectedVersion`. A required field cannot be `null`.
- In one transaction the server locks the goal row, compares the version, merges the patch into the stored goal, validates the resulting goal with the create rules, and checks the goal's existing tasks: none on or after the new `examDate`, and no day whose total `estimated_minutes` exceeds the new `minutesPerDay`.
- Any failure rejects the whole update; nothing is saved. A success increments `version` by 1.
- Tasks are only checked as they exist when the goal is edited. Checking when a task is created or edited belongs to ASP-10, which must lock the same goal row first.
- Changing `timezone` keeps the calendar dates as they are.
- Open decision for the PO: if `startDate` moves later and existing tasks fall before it, should the update be blocked or the tasks kept? ASP-9 only blocks tasks on or after the exam date and over-budget days; this extra behaviour is not implemented.

### Delete — `DELETE /api/v1/goals/{goalId}`

The UI asks for confirmation first; cancelling sends nothing. The delete is one transaction and removes the goal with its `study_tasks` and `plan_drafts` through the cascading foreign keys. Other goals are unaffected.

## 3. Errors

```json
{ "error": { "code": "VALIDATION_ERROR", "message": "Dữ liệu mục tiêu không hợp lệ.",
  "details": [ { "field": "examDate", "issue": "must_be_after_start_date", "message": "Ngày thi phải sau ngày bắt đầu." } ] } }
```

Clients use `code`, `details[].field` and `details[].issue`; `message` is only for people.

| HTTP | `code` | When | `details` |
| --- | --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Bad input, unknown field, malformed JSON, bad `page`/`limit` | one item per problem |
| 401 | `UNAUTHORIZED` | No session | — |
| 403 | `CSRF_INVALID` | Missing or wrong CSRF token | — |
| 404 | `NOT_FOUND` | Unknown, foreign or malformed ID | — |
| 405 | `METHOD_NOT_ALLOWED` | For example `PUT` on a goal | — |
| 409 | `GOAL_VERSION_CONFLICT` | `expectedVersion` differs from the stored version | — |
| 409 | `GOAL_UPDATE_CONFLICT` | Edit breaks existing tasks | one item per task or day, `message` names the task, date and minutes over |
| 500 | `INTERNAL_SERVER_ERROR` | Unexpected | — |

`issue` values: `required`, `invalid_type`, `invalid_date`, `invalid_timezone`, `unknown_field`, `empty_patch`, `too_long`, `must_not_be_in_past`, `must_be_after_start_date`, `exceeds_90_days`, `out_of_range`, `task_on_or_after_exam_date`, `daily_budget_exceeded`, `invalid_existing_task`. When input errors and task conflicts occur together, the answer is 400.

Errors and logs never contain stack traces, SQL, passwords or study content.

## 4. Current date and time zone

The goal's `timezone` decides "today" for the past-date rule: `LocalDate.now(clock.withZone(zone))`. The date is passed into `GoalPolicy` and tests inject a fixed `Clock`. Spans are counted in calendar days, never as a UTC difference divided by 24 hours. The study days are `[startDate, examDate)`.

## 5. Mapping: acceptance criteria → API / service / database

| ASP-9 AC | API | Service rule | Database |
| --- | --- | --- | --- |
| 1. Create with all five fields | `POST /api/v1/goals` → 201 | Owner from session; trim; validate | Insert into `study_goals` (`user_id`, `timezone`, `version = 0`) |
| 1. Subject/topics not blank after trim | 400 `required` | Trim, then check | `CHECK length(trim(...)) > 0` |
| 1. Subject at most 100 characters after trim | 400 `too_long` (not a 500) | Trim, then count Unicode characters (an emoji is one); `GoalRequest` on create and patch | `VARCHAR(100)` |
| 1. Exam date not past, after start | 400 `must_not_be_in_past`, `must_be_after_start_date` | Compare with today in goal time zone | `CHECK exam_date > start_date` (past-date is service only) |
| 1. At most 90 days | 400 `exceeds_90_days` | Day difference ≤ 90 | `CHECK exam_date - start_date <= 90` |
| 1. 15–480 integer minutes | 400 `out_of_range` | Integer in range | `CHECK BETWEEN 15 AND 480` |
| 1. Saved and shown after reload | `GET` list and detail | Owner-scoped read | Persisted row |
| 2. See only own goals | `GET` → 200 or 404 | `WHERE id AND user_id` together | Index on `user_id` |
| 3. Edit | `PATCH` | Lock row, check version, merge, validate | `UPDATE ... WHERE id AND user_id AND version`, then `version + 1` |
| 3. Edit checks existing tasks | 409 `GOAL_UPDATE_CONFLICT` | Tasks on/after exam date; per-day total vs budget | Read `study_tasks` in the same transaction |
| 4. Confirm, then delete with tasks and drafts | `DELETE` → 204 | Owner-scoped delete; confirmation is a UI step | `ON DELETE CASCADE` on both child tables |
| 4. Cannot delete another user's goal | 404 | Owner-scoped delete | — |
| 5. No task on/after exam date; day total within budget | Enforced by the task and AI stories | Same rules, same transaction as task writes | `study_tasks` (service only) |
| 5. Report missing minutes when a plan does not fit | ASP-13/14 | Out of scope for goal CRUD | — |
| DoD: server validates, owner checked everywhere | 400 / 401 / 404 | No client-supplied user ID | `user_id` FK |

## 6. Responsibility

| Area | Owner |
| --- | --- |
| Goal CRUD, validation, owner-scoped queries, edit checks against tasks, cascade delete, goal UI | ASP-9 |
| Registration, login, session, logout, CSRF, trusted user ID | ASP-12 |
| Task CRUD and daily view | ASP-10, ASP-11 |
| AI proposals and plan approval (AI only suggests) | ASP-13, ASP-14 |
| Progress display | ASP-16, ASP-17 |

## 7. Concurrency

- Every goal query includes `user_id`; never fetch by ID and filter afterwards.
- The deadline and daily-minutes checks run in the same transaction as the write, after `SELECT ... FOR UPDATE` on the goal row. ASP-10 task writes must take the same lock.
- `version` is the optimistic lock for concurrent goal edits (`GOAL_VERSION_CONFLICT`).

## 8. Verification

Test evidence and its limits: [ASP-9 verification](ASP-9-verification.md). Names of the covering tests: `GoalControllerTest` (contract: status, envelope, error codes), `GoalApiPostgresIntegrationTest` (HTTP + session + PostgreSQL), `GoalServicePostgresIntegrationTest`, `GoalServiceTest`, `GoalPolicyTest`, `GoalJdbcRepositoryTest`; frontend `goals.test.js`, `goalErrors.test.js`, `goalDelete.test.js`.
