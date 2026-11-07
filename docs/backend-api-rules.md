# Backend API rules

Mandatory rules for code under `backend/`. They come from the *Backend API Spec — AI Study Planner* (draft of 2026-11-07) and are the standard that reviews and tests check against. [requirements.md](../requirements.md) still owns product scope; if a rule here conflicts with it, requirements win and this file is fixed.

Rules marked **(future)** describe features that are not implemented yet (tasks, AI drafts, confirm). New code for those features must follow them; there is nothing to retrofit today.

## 1. Paths and methods

1. Every business endpoint lives under `/api/v1`. Only `GET /actuator/health` is outside it, and it keeps the Actuator format.
2. Current routes:

   | Method | Path | Success |
   | --- | --- | --- |
   | GET | `/api/v1/auth/csrf` | 200 |
   | POST | `/api/v1/auth/register` | 201 |
   | POST | `/api/v1/auth/login` | 200 |
   | POST | `/api/v1/auth/logout` | 204 |
   | GET | `/api/v1/me` | 200 |
   | POST / GET | `/api/v1/goals` | 201 / 200 (paginated) |
   | GET / PATCH / DELETE | `/api/v1/goals/{goalId}` | 200 / 200 / 204 |

3. Partial update uses `PATCH`, never `PUT`. Create returns 201, read and update 200, delete and logout 204 with no body.
4. Nested resources must check that the child belongs to the `goalId` in the URL.
5. `GET /api/v1/goals/{goalId}/progress` is implemented (see [goal progress](product/goal-progress.md)). **(future)** Task, draft and confirm routes follow spec section 7 (`/goals/{goalId}/tasks`, `/tasks?date=`, `/goals/{goalId}/plan-drafts`, `.../confirm`). Completing a task is `PATCH {"completed": true|false}`, never a toggle.

## 2. JSON conventions

- `application/json`, field names in `camelCase`, IDs are UUID strings.
- Dates are `YYYY-MM-DD` local calendar dates of the goal; never convert them to UTC. Timestamps are ISO 8601 UTC (`2026-11-07T03:00:00Z`).
- `timezone` is an IANA name supported by the JVM, at most 64 characters, default `Asia/Ho_Chi_Minh`.
- Durations are integer minutes. JSON strings, decimals and `null` are not integers.
- Lists use `page` (default 1) and `limit` (default 20, max 100); a value outside the range is a 400. Goals are sorted `createdAt DESC, id ASC`.
- Request bodies are strict: an unknown field, or a field the client may not set (`id`, `userId`, `createdAt`, `version`, `source`...), is a 400 `VALIDATION_ERROR` with issue `unknown_field`. A required field cannot be set to `null`.

## 3. Response envelope

- One resource: `{ "data": { ... } }`.
- List: `{ "data": [ ... ], "meta": { "page": 1, "limit": 20, "totalItems": 0, "totalPages": 0 } }`. `meta` appears only on paginated lists.
- DTOs never expose `password_hash`, the owner ID of a goal, or any internal column.

## 4. Errors

```json
{ "error": { "code": "VALIDATION_ERROR", "message": "...", "details": [ { "field": "examDate", "issue": "must_be_after_start_date", "message": "..." } ] } }
```

- Clients branch on `code` and `details[].field` / `issue`, never on `message`. `details[].message` is optional human text.
- Never put passwords, stack traces, SQL or the user's study content into an error or a log line. Log the exception class only.
- Codes in use:

  | HTTP | `code` | When |
  | --- | --- | --- |
  | 400 | `VALIDATION_ERROR` | Bad type, missing field, bad date, unknown field, malformed JSON, bad `page`/`limit` |
  | 401 | `UNAUTHORIZED` | No session or session expired |
  | 401 | `INVALID_CREDENTIALS` | Login failed; never say whether the email or the password was wrong |
  | 403 | `CSRF_INVALID` | Missing or wrong CSRF token on a write |
  | 404 | `NOT_FOUND` | Unknown resource, another user's resource, or a malformed ID |
  | 405 | `METHOD_NOT_ALLOWED` | A real path called with a method it does not support (for example `PUT` on a goal) |
  | 409 | `EMAIL_ALREADY_EXISTS` | Registration with a used email |
  | 409 | `GOAL_VERSION_CONFLICT` | `expectedVersion` does not match the stored goal |
  | 409 | `GOAL_UPDATE_CONFLICT` | Editing a goal would break existing tasks (task on/after exam date, or a day over the new budget); `details` name each task and day |
  | 500 | `INTERNAL_SERVER_ERROR` | Anything unexpected |
  | **(future)** 409 | `DRAFT_ALREADY_CONFIRMED`, `IDEMPOTENCY_CONFLICT` | Confirm rules, section 10 |
  | **(future)** 410 | `DRAFT_EXPIRED` | `now >= expiresAt` |
  | **(future)** 422 | `INSUFFICIENT_TIME`, `DAILY_BUDGET_EXCEEDED` | With `requiredMinutes`, `availableMinutes`, `shortfallMinutes`, or per-day `budgetMinutes`, `scheduledMinutes`, `excessMinutes` |
  | **(future)** 502/503/504 | `AI_INVALID_RESPONSE`, `AI_UNAVAILABLE`, `AI_TIMEOUT` | AI adapter |

- `issue` values for goals: `required`, `invalid_type`, `invalid_date`, `invalid_timezone`, `unknown_field`, `must_not_be_in_past`, `must_be_after_start_date`, `exceeds_90_days`, `out_of_range`, `empty_patch`, `malformed_json`, `invalid_email`, `invalid_length`, `task_on_or_after_exam_date`, `daily_budget_exceeded`, `invalid_existing_task`.
- If an input is invalid and tasks also conflict, answer 400 (input errors win); use 409 only when every problem comes from existing tasks.

## 5. Authentication, session and CSRF

- Server-side session in an `HttpOnly` cookie, `SameSite=Lax`; `Secure` is on when the app is served over HTTPS (`SESSION_COOKIE_SECURE=true`), off for local HTTP.
- Every write (including register, login and logout) needs the `X-CSRF-TOKEN` header; the token comes from `GET /api/v1/auth/csrf` as `{ "data": { "token", "headerName" } }` and is fetched again whenever the session changes.
- Login replaces the session ID. Logout invalidates the session and deletes the cookie. Register does not log the user in.
- Email is trimmed and lower-cased before storage and lookup; the password is never trimmed or altered, and only its BCrypt hash is stored.
- A missing session is always a JSON 401, never a redirect to an HTML page.

## 6. Ownership

- The owner always comes from the authenticated principal. No endpoint reads a user ID from the body, query or path.
- Every query filters by owner (`user_id`, or through the parent goal). Do not fetch by ID and filter afterwards. A foreign foreign key is not authorization.
- Another user's resource and an unknown one both answer 404 with the same body.

## 7. Goal rules

- `subject`: not blank after trim, at most 100 characters. `topics`: not blank after trim. Both are trimmed before they are validated and stored.
- `startDate` may be in the past; do not invent a rule that forbids it. `examDate` is not in the past (in the goal's time zone), is after `startDate`, and at most 90 calendar days after it.
- `minutesPerDay` is an integer from 15 to 480.
- Create accepts `subject`, `topics`, `startDate`, `examDate`, `minutesPerDay` and optional `timezone`. Patch accepts the same fields (at least one) plus the required `expectedVersion`. The server validates the goal as it would be after the patch, together with its existing tasks.
- A rejected patch changes nothing (single transaction); the response states what to fix.
- Delete removes the goal and, through `ON DELETE CASCADE`, its tasks and drafts. Never leave a half-deleted goal.

## 8. Scheduling and budget

- Study days are `[startDate, examDate)`: the exam day itself is excluded. A task date must satisfy `startDate <= scheduledDate < examDate`.
- For each goal and day: total `estimatedMinutes` of saved tasks must not exceed `minutesPerDay`. Completed tasks still count.
- Budgets are per goal; two goals may each use their full budget on the same day.
- Count days on the calendar of the goal's time zone, never by dividing a UTC difference by 24 hours.
- Nothing may silently overload a plan or auto-move, shorten or delete tasks. **(future)** A plan that does not fit reports the minutes missing.

## 9. Versioning and concurrency

- `study_goals.version` is the optimistic lock. A patch must send `expectedVersion`; a mismatch is `GOAL_VERSION_CONFLICT`. A successful change increments the version once.
- **(future)** Every real mutation of the goal or of its tasks increments the goal version once in its transaction; setting `completed` to the value it already has does not.
- Every write that depends on the budget locks the goal row (`SELECT ... FOR UPDATE`) in the same transaction as its check. Reading the total and writing in two transactions is not allowed.
- No migration edits: `V1__initial_schema.sql` is applied. Schema changes go into a new `V<n>__*.sql`.

## 10. **(future)** Drafts, confirm and idempotency

- Generating a draft never creates `study_tasks`. Do not hold a DB transaction open during the AI call. Validate everything the AI returns.
- Confirm is atomic, takes `Idempotency-Key` (UUID) and the full final task list, adds to the existing tasks, creates tasks with `source = ai`, and bumps the goal version once. Receipts are stored in the database, checked before expiry and version errors, and never kept after a rollback.
- Same key and payload replays the stored result with 200; same key with another payload is `IDEMPOTENCY_CONFLICT`.

## 11. Security and logging

- No password, API key, cookie or study content in logs. Log request ID, operation, result code and duration only.
- DB credentials and any AI key stay in backend environment variables, never in frontend or `VITE_*` variables.
- Send an AI provider only what scheduling needs; never credentials, cookies or email.

## 12. How the code is organised

- Keep the Java packages `com.studyplanner.*`: `api` (envelope, error DTOs, the single `ApiExceptionHandler`), `auth`, `goals`. Do not move generated `target/` output.
- Controllers translate HTTP only: parse, call a service, wrap in the envelope. Rules live in `GoalPolicy` and `GoalService`; SQL lives in repositories. The service takes the owner ID as an argument.
- All error responses are produced by `ApiExceptionHandler` and the security entry points; controllers do not build error bodies.
- Every new endpoint ships with: a controller (MockMvc) test for status, envelope and error body; a PostgreSQL integration test for persistence and ownership across two accounts; and an update of [ASP-9 contract](product/ASP-9-goals-api.md) or the relevant contract file.
- PostgreSQL integration tests run only against a disposable database (`-Dgoal.integration=true -Dauth.integration=true`); they delete rows.

## 13. Implementation status (2026-11-07)

| Area | State |
| --- | --- |
| Auth, `/me`, CSRF | Implemented under `/api/v1` |
| Goals create, list, detail, patch, delete | Implemented under `/api/v1` |
| Goal progress (`GET /goals/{id}/progress`) | Implemented under `/api/v1` ([goal progress](product/goal-progress.md)) |
| Tasks (manual, by day) | Not implemented (ASP-10, ASP-11) |
| AI drafts, confirm, idempotency | Not implemented (ASP-13, ASP-14); needs a new migration |
| Rate limits, session store and TTL, password policy | Open decisions (spec section 14) |
