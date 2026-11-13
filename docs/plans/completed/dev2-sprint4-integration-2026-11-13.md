# Dev2 sprint 4 — integration test delivery

## Scope

Integration branch: `integration/dev2-sprint4-test`, based on `origin/main` at `29dbf6d`.
Merged source: `origin/dev2-sprint4` at `fe88648`.

The delivered branch implements weekly progress (ASP-33/99–102/112):
`GET /api/v1/progress/weekly`, owner-scoped counts and a weekly progress screen.
It does not contain study-session/Pomodoro backend or Active Recall UI.
Those remain separate work despite the newer three-developer allocation.

## Integration changes

- Preserve authentication on syllabus parsing; remove the branch's unrelated anonymous access change.
- Remove the US-17 badge and technical route title from the product interface.
- Apply existing cream/moss theme tokens to the new screen and progress indicators.
- Reload the current week when its navigation button is selected again; previously loading could remain pending because the selected date had not changed.
- Remount weekly data when the authenticated account changes.
- Check the disposable database name before the new PostgreSQL suite deletes test users.
- Add a security regression proving anonymous syllabus parsing still returns 401.

## Verification

- Frontend: lint passed; 102/102 client/logic tests passed; production build passed.
- Full backend verify with goal/auth/planning/calendar integration enabled against
  `127.0.0.1:55433/study_planner_test`: 323 executions, zero failures/errors,
  three optional private-PDF skips. This run preceded the final two test-only changes.
- After the database guard and authentication regression: `WeeklyProgress*Test`
  with goal integration enabled passed 28/28, and verify packaged successfully.
  These runs are distinct; counts are not combined into a full-suite claim.
- Real HTTP through Vite proxy with synthetic preview data: weekly 1/2;
  empty week 0/0; invalid date 400; anonymous progress 401; anonymous syllabus 401;
  proxy health UP.
- Browser runtime returned no available browser. No click/layout/mobile browser
  validation is claimed, including the current-week navigation fix.

## Local test environment

- Frontend: `http://127.0.0.1:5173/#weekly`; backend port 18084.
- Preview database: `study_planner_preview` on the isolated PostgreSQL instance
  at port 55433. Integration suites use the separate `study_planner_test` database.
- Synthetic preview account has one goal and two tasks, one completed.
- No existing demo database, user credentials, Gemini keys or Google tokens copied.
  Automatic Calendar processing is disabled in this preview process.
- Gemini and Google OAuth are not configured in this isolated preview. Weekly
  progress does not need either service. Other provider-dependent workflows require
  a separately authorized local configuration.
- No application schema migration added; the preview applied existing V1–V7.
- Local logs and temporary smoke tools are ignored under `.tools/`.

The automatic approval reviewer rejected copying the existing `.env` and starting
the old demo database. The test environment therefore uses new synthetic data;
the original environment and data were left unchanged.
