# 0001 Google Calendar export is one-way, previewed and recoverable

Date: 2026-11-11

## Status

Accepted — records the PO's approval in the implementation session, not a new decision introduced by this documentation update. Product authority remains [requirements](../../requirements.md) and the [Calendar contract](../product/google-calendar.md).

## Context

The app stores study dates and durations; a Google event needs an actual time interval that avoids the user's existing calendars and living hours. Network timeouts can leave an event created remotely while the app has not received confirmation. A calendar that cannot be read must not silently count as free time.

## Decision

- First delivery exports app tasks to Google only, after a server-generated preview and explicit user confirmation. It preserves the study day and whole task duration; insufficient contiguous time stays unplaced.
- Use OAuth per app owner, not a shared Gemini key. Keep provider credentials on the backend and encrypt access/refresh tokens with AES-256-GCM bound to that owner.
- Check the selected calendars plus target calendar; block placement/export if required busy data or permissions are unavailable. Keep stale selections visible for an explicit user choice.
- Use deterministic event identity and durable PENDING/CONFIRMED receipts to recover ambiguous remote success and serialize confirmations per owner. Verify remote event markers and times rather than overwrite an event that changed externally.
- Existing Google events are not automatically updated/deleted after app tasks change. Calendar checks and inserts are not atomic against edits made by external applications.

## Alternatives Considered

1. Two-way synchronization is outside the accepted first delivery and has not been authorized as implemented behavior.
2. Automatic export without confirmation conflicts with the existing product boundary that proposals require user confirmation.
3. Treating an unreadable calendar as available would weaken the accepted conflict-avoidance requirement; the implementation fails closed instead.

## Consequences

Positive:

- The user reviews actual times before an external write; lost responses/repeated confirmation can be recovered without generating another event.
- Private Google grants are isolated by app owner; disappearing calendars do not silently reduce conflict checks.

Tradeoffs:

- Configuration requires a Google OAuth client, allowed test users while in Testing, and a stable encryption key backed up with the DB.
- Existing exported events may diverge from edited app tasks. Users must currently manage such changes on Google manually.
- No absolute no-overlap guarantee is possible if another application changes the calendar after the final busy check.

## Follow-Up

- PO approved remote update/delete from app on 2026-11-11; see [0002](0002-calendar-update-delete.md). Separate approval is still required for two-way synchronization and adaptive replanning.
- Add connected-browser/mobile automation, production OAuth/deployment verification and backup restoration rehearsal before claiming those are verified.
- See [completed handoff](../plans/completed/core-study-calendar-2026-11-11.md) and [verification harness](../../.agents/harness/README.md) for dated proof and gaps. No CI policy or branch protection is established by this decision record.
