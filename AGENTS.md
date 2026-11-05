# Project agent conventions

- Frontend: `frontend/` (React + Vite). Backend: `backend/` (Spring Boot, Java 21, Maven 3.9+).
- Preserve existing Java packages and Maven layout. Do not move generated `target/` output.
- API example: `frontend/src/lib/api.js`, tested by `frontend/src/lib/api.test.js`.
  The only current endpoint is Spring Actuator `/actuator/health`; business API controllers do not exist yet.
- From repository root: `mvn -f backend/pom.xml verify` and `mvn -f backend/pom.xml spring-boot:run`.
- In `frontend/`: `npm ci`, `npm run lint`, `npm test`, `npm run build`; use `npm run dev` locally.
- Run Compose from root. Supply DB environment variables to Java explicitly; Spring does not automatically load root `.env`.
- Keep secrets out of Git and frontend variables. Preserve unrelated working changes.
- Validation instructions and known gaps: [.agents/harness/README.md](.agents/harness/README.md).
- Project skills belong at `.agents/skills/<skill-name>/SKILL.md`. Do not install external skills without a request.
- Repository Harness navigation: [docs/README.md](docs/README.md); project requirements and verification remain in their existing documents.
