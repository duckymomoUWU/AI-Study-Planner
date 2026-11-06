# ASP-22 / ASP-9 — Goal API contract (proposed)

**Status:** Draft API contract for implementing Jira [ASP-9](https://ai-study-planner-nhom.atlassian.net/browse/ASP-9). **These endpoints do not yet exist in the current Spring Boot backend.** This is the design baseline for ASP-23/24/25/26/27, not proof of a running feature.

**ASP-23 implementation progress (stacked PR):** `GoalJdbcRepository` now contains owner-scoped JDBC primitives for list/find/lock/insert/versioned update/delete and task reads. These methods are **not wired to a business service, current-user provider or controller**. Their SQL shape is unit-tested with mocks, not yet validated against a live PostgreSQL instance.

**ASP-23/24 application-service progress (stacked PR #12):** `GoalService` now composes the policy and repository in Spring transactions for list/get/create/update/delete. It trims subject/topics, uses `Asia/Ho_Chi_Minh` by default for date validation, locks the owned goal before validating existing tasks, checks optimistic version, and refuses invalid writes. The trusted owner UUID is an explicit server-side method argument. No controller or authentication integration is included; ASP-12 must supply this value from a verified principal. Service tests use mocked repository behavior; live PostgreSQL transaction tests remain outstanding.

## Current architecture and ownership

- `backend/`: Spring Boot 3.5, Java 21, JDBC, PostgreSQL 17, Flyway.
- `frontend/`: React + Vite; development proxy forwards `/api` to Spring Boot.
- Schema `V1__initial_schema.sql` already has `app_users`, `study_goals`, `study_tasks`, `plan_drafts`. `study_tasks.goal_id` and `plan_drafts.goal_id` reference `study_goals` with `ON DELETE CASCADE`.
- `study_goals` fields include `subject`, `topics`, `start_date`, `exam_date`, `minutes_per_day`, `timezone` (default `Asia/Ho_Chi_Minh`), and `version` (default 0).
- **ASP-12 prerequisite:** authenticate requests and obtain a trusted user ID on the server. No endpoint may accept or trust a client-supplied `userId`. The login/session/token mechanism is deliberately not specified by ASP-9.
- The repository's existing `/actuator/health` endpoint is not an authenticated business API.

## Proposed endpoints

| Method | Path | Outcome / status |
| --- | --- | --- |
| POST | `/api/goals` | Create current user's goal, **201** |
| GET | `/api/goals` | List goals owned by current user, **200** |
| GET | `/api/goals/{goalId}` | Read owned goal, **200** |
| PUT | `/api/goals/{goalId}` | Replace editable fields of owned goal, **200** |
| DELETE | `/api/goals/{goalId}` | Delete goal and related tasks/drafts after UI confirmation, **204** |

All routes require authenticated access. Missing authentication returns **401**. For an unknown ID or another user's ID, return the same **404** to avoid leaking ownership.

### Input: POST /api/goals

```json
{
  "subject": "Giải tích 2",
  "topics": "Tích phân; phương trình vi phân",
  "startDate": "2026-11-09",
  "examDate": "2026-12-05",
  "minutesPerDay": 90
}
```

- `subject`: non-blank, trim whitespace; database max 100 characters.
- `topics`: non-blank, trim whitespace.
- `startDate`, `examDate`: ISO calendar date (`YYYY-MM-DD`) with no time component.
- `examDate` cannot be in the past **in the goal's effective timezone**; it must be strictly later than `startDate`; date difference may be at most 90 calendar days (inclusive).
- `minutesPerDay`: JSON integer between 15 and 480 inclusive; strings, decimals and null are invalid.
- Do not accept `userId`, `version`, `timezone` or stored task data from the client on create. Server sets owner; use server-side effective timezone (initial default `Asia/Ho_Chi_Minh`).
- The accepted requirements do not forbid a `startDate` in the past; do **not** invent that rule.

### Response (201 Create, 200 Detail/Update)

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

The UUID is **illustrative**, not an existing record. `GET /api/goals` returns `{"items": [ ... ]}` and `{"items": []}` for no goals. Never include `password_hash`, other users' data or internal secrets in responses.

### Input: PUT /api/goals/{goalId}

Use all five editable fields shown in POST **plus the record's current integer `version`**. A mismatched version returns **409** (`stale_goal_version`) and must not overwrite a concurrent edit. On success, increment version.

Before replacing the goal, check all existing scheduled tasks belonging to it:
- no task falls on or after the proposed `examDate`;
- sum of estimated minutes for each scheduled calendar day is at most the new `minutesPerDay`;
- validate the same subject/topic/date/minute rules as creation.

Reject the **entire** update when any rule fails. Do not partially persist the changed goal or mutate task dates automatically.

The current data model has no independently accepted rule for pre-existing tasks scheduled *before* a newly changed `startDate`. Record this for PO review rather than silently adding a new behavior.

### DELETE /api/goals/{goalId}

The frontend must ask for explicit confirmation before sending DELETE; cancelling must not call the endpoint. A successful delete must be atomic and remove associated `study_tasks` and `plan_drafts` (Flyway V1 already defines cascading foreign keys). No other goal is affected. Require a trusted owner context for this action.

### Errors

Use JSON and consistent machine-readable codes:

```json
{
  "code": "validation_failed",
  "message": "Dữ liệu mục tiêu không hợp lệ.",
  "fieldErrors": [
    { "field": "examDate", "code": "not_after_start", "message": "Ngày thi phải sau ngày bắt đầu." }
  ]
}
```

| HTTP | Code examples | Meaning |
| --- | --- | --- |
| 400 | `validation_failed`, field codes `required`, `past_date`, `not_after_start`, `over_90_days`, `out_of_range` | Invalid input, missing required values |
| 401 | `unauthenticated` | No trusted logged-in user |
| 404 | `goal_not_found` | Unknown goal OR other owner's goal |
| 409 | `stale_goal_version`, `goal_tasks_conflict` | Concurrent update or existing tasks violate proposed goal limits |
| 500 | `internal_error` | Unexpected server error; never expose secrets or stack traces to clients |

The above are the proposed **API mappings**, not currently implemented HTTP responses. The pure Java `GoalPolicy` is intentionally independent of HTTP, so future controllers/services can map violations safely.

## Acceptance criteria and implementation ownership

| Jira AC | Owner of implementation | Minimum proof |
| --- | --- | --- |
| Required fields, 90 days, 15–480 minutes, exam date | ASP-23 and shared `GoalPolicy` foundation | Policy unit tests + API/server validation |
| Create, list/detail and persistence | ASP-23 | Authenticated API/DB test and reload demo |
| Edit while preserving schedules | ASP-24 | Transactional API/DB test with tasks before/after |
| Form, errors, list/edit UI | ASP-25 | Browser + real API test |
| Confirm delete and cascade | ASP-26 | Cancel/confirm UI + relational DB test |
| Ownership, edge cases and demo | ASP-27 | Cross-account integration + end-to-end evidence |
| Trusted current user identity | ASP-12 | Authentication and authorization tests |

## Safety, concurrency and implementation notes
- All goal queries must be scoped to authenticated `user_id`; don't do find-by-id then rely on frontend filtering.
- Daily minutes and deadline checks must run in the same database transaction as relevant writes. Coordinate ASP-10 task writes to prevent races. The `version` column supports optimistic locking of goal updates; additional locking may be needed across goal/task modifications.
- Validate using `LocalDate.now(ZoneId.of(goalTimezone))`; pass that date into `GoalPolicy` so tests are deterministic.
- A future API must enforce this policy at the server boundary, and trim/normalize input before storing it; the pure policy only validates.
- No controller or public CRUD endpoint is created by ASP-22/24 foundation while ASP-12 authentication is absent.
- Build/unit tests are not integration tests: backend auth/DB/API, UI and delete cascade still need independent proof.

## Open business decision (does not block contract/policy foundation)
When a user moves `startDate` **later** and existing tasks now fall **before** that start date, should the update be blocked or should the tasks remain? ASP-9 currently only explicitly blocks tasks at/after the exam date and daily over-budget tasks. PO decision required before implementing this extra behavior.
