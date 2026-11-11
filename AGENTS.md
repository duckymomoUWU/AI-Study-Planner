# Project agent conventions

- Frontend: `frontend/` (React + Vite). Backend: `backend/` (Spring Boot, Java 21, Maven 3.9+).
- Preserve existing Java packages and Maven layout. Do not move generated `target/` output.
- Business APIs live under `/api/v1`: auth, goals, tasks, syllabus, AI drafts and Google Calendar. Follow contracts linked in `docs/README.md`; `frontend/src/lib/api.js` is only the health client.
- From repository root: `mvn -f backend/pom.xml verify` and `mvn -f backend/pom.xml spring-boot:run`.
- In `frontend/`: `npm ci`, `npm run lint`, `npm test`, `npm run build`; use `npm run dev` locally.
- Run Compose from root. Backend explicitly imports root `.env` when launched from root or `backend/`; shell variables override file values. Keep secrets in ignored `.env`, with blank credentials in `.env.example`.
- Keep secrets out of Git and frontend variables. Preserve unrelated working changes.
- Validation instructions and known gaps: [.agents/harness/README.md](.agents/harness/README.md).
- Project skills belong at `.agents/skills/<skill-name>/SKILL.md`. Do not install external skills without a request.
- Repository Harness navigation: [docs/README.md](docs/README.md); project requirements and verification remain in their existing documents.
- Current handoff and evidence: [core and Calendar delivery](docs/plans/completed/core-study-calendar-2026-11-11.md). Verify cwd/branch before running commands. Integration tests use a disposable database, never the demo/user database.
- Backend code must follow [docs/backend-api-rules.md](docs/backend-api-rules.md) (paths, envelope, error codes, ownership, concurrency).
