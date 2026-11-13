# Dev 3 Recall integration - 2026-11-13

Branch: `integration/dev3-recall-test`, base `1f59ff9` (weekly progress plus soft
subject preferences/shared hard capacity). Merged `origin/sprint3/AIfeat` at `98a2b80`.

## Delivered and reviewed

Backend materials (text/PDF), AI draft decks and source quotes, card edits, activation,
remembered/forgot reviews, progress, deletion, owner checks and request receipts.
This matches Person 3 backend/AI scope. No Recall product UI or Timer is included.

## Integration corrections

- Renumber new unapplied Recall migration V2 to V8; preserve V1-V7. Remove duplicated
  PDFBox 3.0.4 dependency and retain existing 3.0.7.
- Original same-object call bypassed Spring transaction interception. Use explicit
  transaction for deck/cards/receipt, with Gemini outside the transaction.
- Receipt failures roll back. Owner locks serialize Recall mutations and receipt
  rechecks prevent concurrent same-key generation from persisting duplicate decks.
- Keep versioned routes only, reject invalid pagination/unknown fields/coerced numeric
  inputs, limit encoded PDF size and filename length.
- Use existing Gemini configuration/default; header credentials, no raw provider/source
  logging. Missing credentials and provider errors never produce heuristic success.
- Require requested count of verified AI cards; reject incorrect page citations.

## Evidence

Initial focused branch tests: 33 passed (predominantly mocks).
Final full backend test with goal/auth/planning/calendar integration flags, explicit
`127.0.0.1:55433/study_planner_test`: 367 executions, zero failures/errors,
3 optional private-PDF skips (364 passed). New real PostgreSQL cases cover lifecycle,
ownership, stale edits, review replay, cascade, failed-write rollback, concurrent
same-key generation, provider-error preservation and strict input. AI is mocked.
Frontend lint, 104/104 tests and build passed. Separate verify with tests skipped
packaged successfully after full tests.

Restarted isolated preview backend port 18084, DB `study_planner_preview` on 55433.
V8 applied successfully; existing user preview data retained. HTTP through Vite proxy:
material 201, absent key 503 AI_UNAVAILABLE, no fabricated deck, anonymous 401,
goal deletion 204 with material cascade. An initial smoke helper incorrectly parsed
empty 204 as JSON; corrected helper passed. Local HTML served 200 and its script
passed node --check. No browser interaction/layout or actual Gemini success claimed.
No .env/secret changes, Google writes, GitHub push or main merge.

## Manual test and remaining work

Sign in at http://127.0.0.1:5173 first. Open
http://127.0.0.1:5173/.tools/recall-test.html . Source artifact:
[Recall API test](../../testing/recall-backend-test.html).
This local API harness is copied into ignored frontend/.tools, outside shipped UI.
Load goals, choose one, save text/PDF, reload materials, request five cards.
Without a Gemini key expect an error with material retained. With valid backend
configuration: inspect/edit draft, activate and rate Remembered/Forgot.

The separate Recall frontend still needs integration with
`docs/team-handoff/03-API-CONTRACT.md`. Real Gemini quality and browser/mobile
acceptance remain required; mock success is not live-provider evidence.
