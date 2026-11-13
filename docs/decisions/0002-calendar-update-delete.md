# 0002 App edits propagate to the original Google Calendar event

Date: 2026-11-11. Status: Accepted, based on explicit PO request to implement one-way update/delete after its behavior and limitations were discussed.

Initial export stays explicit and previewed. Later edits/deletions propagate asynchronously to the event originally exported, with local save and durable sync intent in one transaction. Never roll back local work because Google is offline. Preserve tombstones for task/goal deletion. Keep an old event and show blocked status if its new date has no valid slot; do not silently change the study date.

Intent is captured by V7 PostgreSQL triggers so all existing mutation paths (manual CRUD, goal cascade deletion and subject rename) participate without remote calls in domain transactions. Worker claims a per-event lease and revision; conditional ETag writes and marker checks guard remote identity and concurrent changes. Success/failure receipts are conditional on the claimed revision, so old workers cannot consume later edits.

Queue retains pending work across restarts. Target event listing excludes only its own event; subtraction from merged FreeBusy is prohibited because it can hide an overlapping unrelated event. All selected calendars fail closed if unreadable. Original target calendar remains the mapping destination after settings change. Calendar export confirmation now locks owner before goal using NO KEY UPDATE, compatible with independent export receipt FK checks; shared-budget serialization uses the same owner lock mode.

Tradeoffs: eventual synchronization, Google request latency/quota, pending events may temporarily show old times, and no atomic reservation against unrelated Google apps. No backfill of historical divergence, no implicit re-creation of missing events, no reverse sync. User-account deletion and removing calendars are separate lifecycle concerns, not task/goal deletion.

Contract: [Google Calendar](../product/google-calendar.md). Evidence: [completed delivery](../plans/completed/calendar-sync-2026-11-11.md). Automated tests mock Google transport and disable scheduling; real OAuth/provider write verification is not implied.
