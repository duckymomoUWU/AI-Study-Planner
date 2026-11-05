# Development and verification harness

Run commands from repository root unless another directory is specified.
See [setup.md](../../setup.md) for environment setup and database lifecycle.

## Frontend

Requires Node.js 22.12+ and npm. Existing CI uses Node 24:
[frontend workflow](../../.github/workflows/frontend.yml).

```powershell
Set-Location frontend
npm ci
npm run lint
npm test
npm run build
npm run dev
```

`src/lib/api.test.js` tests health request handling using mocked fetch.
It does not test a live backend or browser interactions. No browser or end-to-end tests exist yet.
CI already runs install, lint, tests and build in `frontend/`; its paths remain valid.

## Backend

Requires JDK 21 and Maven 3.9+. No Maven Wrapper exists.

```powershell
mvn -f backend/pom.xml verify
```

Output is `backend/target/`. There is currently no `src/test/`, backend test suite,
or backend CI workflow. Maven verification alone does not demonstrate database connectivity.
To start with the shared PostgreSQL service, from root:

```powershell
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'DB password' -AsSecureString)).Password
docker compose up -d --wait
mvn -f backend/pom.xml spring-boot:run
```

Spring reads `DB_URL`, `DB_USER`, `DB_PASSWORD`, and `PORT` from its process environment.
Compose can read root `.env`; Java does not load that file automatically.
Flyway migrations live in `backend/src/main/resources/db/migration/`.
Do not change applied migrations or delete database volumes to make checks pass.

## Changes affecting both sides

1. Review backend handlers and configuration together with `frontend/src/lib/api.js`
   and `frontend/src/lib/api.test.js`. Currently only `/actuator/health` exists;
   `/api` is reserved for future business endpoints, and no OpenAPI specification exists.
2. Run both sets of checks above. Extend relevant tests for changed behavior.
3. Start database, backend, and Vite (`npm run dev` from `frontend/`) in separate terminals.
4. From another PowerShell terminal, compare:

   ```powershell
   Invoke-RestMethod http://localhost:8080/actuator/health
   Invoke-RestMethod http://localhost:5173/actuator/health
   ```

   Both should report `status: UP`. Confirm the frontend connection card and retry behavior in a browser.
5. Compare method, path, status codes, payload, error handling, and authentication for any new API.

Vite proxies `/api` and `/actuator/health` to `BACKEND_URL` (default `http://localhost:8080`).
Moving Java source does not change these URLs. If `PORT` changes, update
`frontend/.env.local` using `frontend/.env.example` and restart Vite.
Vite preview has no backend proxy; production requires a separate reverse proxy.

## Verification status

The repository has frontend checks and mocked API tests, but no backend tests or end-to-end suite.
Builds, live database checks, and browser smoke checks must be recorded separately;
documentation of a command is not evidence that it passed.
Verified on Windows / PowerShell during restructuring (2026-11-05):

- `npm ci`, `npm run lint`, `npm test` (2 passed), and `npm run build` succeeded in `frontend/`.
- `mvn -f backend/pom.xml verify` succeeded and produced the repackaged JAR;
  Maven reported no test sources, so this is a build check rather than backend test coverage.
- `git diff --check` passed. All four moved backend files matched their original
  contents after normalizing line endings.
- CI working-directory and cache paths remain correct. Compose only defines PostgreSQL,
  with no backend build context to change. No tracked Dockerfile, custom script, or VS Code configuration exists.
- Live database startup, direct/proxied health requests, and browser smoke checks were not run.
  These remain unverified; frontend API tests use mocks.
