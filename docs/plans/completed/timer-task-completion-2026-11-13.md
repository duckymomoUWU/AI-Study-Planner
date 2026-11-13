# Timer finish and explicit task completion

Branch: `integration/dev3-recall-test`.

After a confirmed COMPLETED session the Timer reads its linked task from the server.
If it exists and is incomplete, it asks whether the student finished the task.
Confirm calls the existing owner-scoped PATCH with `{completed:true}`; defer writes
nothing. RUNNING/PAUSED/CANCELLED, deleted links and already completed tasks do not
offer the question. A completed session read during recovery is also eligible.

Task completion errors do not undo the saved study session or falsely show success.
The user can retry; repeated PATCH uses true, never a toggle. Missing task responses
hide the choice; 401 offers login. Concurrent button presses are guarded in the UI.
The question's state survives the five-minute break; a new session gets a new choice.
After successful completion the Timer task picker updates its task state.
No backend/schema/env/Calendar change and no automatic marking on FINISH.

Verification: frontend lint, 142/142 tests and build passed. New client/behavior tests
cover eligible sessions, no write on read, explicit completion/CSRF, repeat requests,
already completed tasks, 404 and write failures. Existing SSR smoke passed.
Live isolated preview APIs with a temporary synthetic account: FINISH leaves the task
open; explicit and repeated completion stay true; session time/status retained;
deleted task returns 404. No real user tasks changed. No browser click/layout or
network-outage UI validation claimed; backend code unchanged, not retested in full.

Demo: open a real task in #timer, start and finish early. Choose either completion
or defer, then check #daily. Try an already completed task and a cancelled session:
neither should show the question. The saved study time is independent of the choice.
