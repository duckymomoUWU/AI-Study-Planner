# Recall update integration ? 2026-11-13

Source: `origin/dev2-sprint4` at `5d31347`, three commits after previously integrated `10051f8`.
Target: `integration/dev3-recall-test`, based on `5ec2765`.

## Delivered changes

- Source detail endpoint: `GET /api/v1/recall/materials/{materialId}`, owned by the signed-in user.
  Returns at most 1,200 preview characters and an explicit `previewTruncated` flag.
- Source preview in Recall, PDF source labels, normalized Vietnamese text, actionable PDF errors.
- AI output keeps verified, distinct questions; rejects oversized questions/answers instead of cutting them.
  Between five and the requested number of valid cards may be saved, with a shortfall notice after generation.
  Fewer than five verified cards is an error, without fabricated fallback cards.
- Duplicate in-flight generation on the same user/key returns `409 GENERATION_IN_PROGRESS`;
  retry after completion replays the same persisted deck.
- Activation checks actual cards rather than trusting stored card counts.

## Merge decisions

- Preserve owner locks and receipt recheck inside the transaction, strict request validation,
  invalid pagination rejection, pre-decode base64 size guard and configured Gemini model fallback.
- Preserve original integration safety tests in `RecallSafetyPostgresIntegrationTest`;
  update concurrent generation assertions for the explicit in-flight conflict.
- Keep V8 Recall / V9 Timer migrations unchanged. No database migration or user-data reset.
- The documentation cleanup stays on `docs/documentation-cleanup`, outside this merge.

## Verification

Results are recorded after execution below. PostgreSQL suites use only disposable
`study_planner_test` on port 55433. The manual preview uses separate `study_planner_preview`.
Browser discovery was unavailable; no repeated browser click or visual checks are claimed.

- Frontend: lint passed, 150/150 tests passed, production build passed.
- Backend full enabled PostgreSQL suites: 440 executions, zero failures/errors, three skipped
  private-PDF fixtures (437 passed). `verify -DskipTests` packaged the already tested source successfully.
- Live frontend proxy at `http://127.0.0.1:5173`: short preview, 1,200-character truncation,
  anonymous 401 and another owner 404 passed. Real Gemini returned 201 with five cards;
  persisted source quotes and identical-key replay were verified. Synthetic test goals were deleted.
- Preview restarted on backend port 18084 using existing ignored local configuration;
  Calendar background synchronization remains disabled for this isolated preview.
- The first Recall-only run exposed an obsolete mock receipt sequence after the additional
  receipt recheck; the test was updated to exercise the database-conflict path, then the full suite passed.

Manual demo: open `http://127.0.0.1:5173/#recall`, select a goal, save a text/PDF source,
choose the source preview, generate cards, inspect source labels and activate the deck.
No repeated browser visual or keyboard acceptance is claimed for this integration.
