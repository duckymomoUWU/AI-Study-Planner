# Landing and Timer frontend delivery

Branch: `feature/dev1-landing-timer`, based on Recall integration `f7d623d`.
Scope: Person 1 in `docs/team-handoff/02-GIAO-VIEC-VA-AC.md`.
No backend, database, secret, Calendar or Jira modifications.

## Implemented

- Public LandingPage without workspace sidebar, cream/moss theme, local Vietnamese
  font, responsive hero/process illustration/three steps and registration/login CTAs.
  No invented users/testimonials/prices or advertisement of unavailable Timer/Recall.
- Guest root resolves to landing after session lookup. Authenticated root keeps
  Overview. Explicit #landing is available to either audience; #overview remains
  a separate workspace route. Landing start opens #register for guests and goals
  for signed-in users. Existing registration automatically logs in and opens goals.
  #landing-steps stays on landing. Browser hash back/forward is retained.
- TimerPage at #timer; task links from weekly and daily schedule preserve a real task
  UUID in #timer?task=... across reload. Direct entry loads the current account's
  tasks for a chosen date, including completed tasks for revision.
- Session API client follows the agreed versioned contract and uses session cookies,
  CSRF, optimistic versions and stable idempotency keys for ambiguous write retries.
- UI includes start/pause/resume/finish/cancel confirmation, active-session recovery,
  recent history, a separate five-minute UI break, completion and cancellation text.
  No client-only success or localStorage-based study-time accounting.
- Countdown uses received server remainingSeconds plus monotonic elapsed time.
  Paused snapshots freeze. Focus/tab visibility syncs backend state. At zero the UI
  submits FINISH once; ambiguous errors retain the operation/key, and conflicts
  require sync rather than repeatedly issuing new finish requests.
- In-flight write guard prevents double submission; read generations prevent a late
  pre-write snapshot from replacing a newer write result. State announcements do not
  announce every timer tick. History read failure cannot invalidate a confirmed write.
- Timer does not mark tasks completed or change estimates/budgets/Calendar.

## Verification and limits

Frontend lint passed without warnings; 109/109 client/logic tests passed; production
build passed. New tests cover routes/task links, elapsed/paused countdown, clamp,
API paths/CSRF/stable retries, unavailable API and malformed successful responses.
Vite SSR smoke renders public landing structure, guest Timer login prompt and
signed-in Timer initial loading structure. This does not execute browser effects.

Live Vite modules returned 200 and backend proxy health was UP. An authenticated
GET /api/v1/study-sessions/active returned 404; anonymous request returned 401.
No session start/action was attempted against the missing backend.
Browser discovery returned no connected browser; actual clicks, reload/race/offline
UI behavior, screenshots and 360/768/1440 layout remain unverified. Responsive CSS
and accessibility affordances are implemented but not a substitute for those checks.

## Remaining dependency / acceptance

**Timer backend is absent from the reviewed branches.** The frontend calls the agreed
API and displays a clear unavailable state with Start disabled, rather than faking a
saved session. Timer UI implementation is available, but TMR-UI-01 is not accepted
end to end and its integration DoD remains pending Person 2's real backend.
The session contract remains in `docs/team-handoff/03-API-CONTRACT.md`; this change
does not claim that the endpoints described there have been implemented.

After backend delivery, verify active/paused reload, two-tab conflict, early finish,
automatic 25-minute finish, offline replay, cancellation, history and goal deletion
against the real endpoints. Break time must not count toward completed study time.

## Manual preview

- http://127.0.0.1:5173/#landing - public introduction, including when signed in.
- Open root in a guest/private window; Start must open registration, Login must open
  login, and successful authentication must open goals. Existing authenticated root
  must remain Overview.
- http://127.0.0.1:5173/#timer - choose a real task/date; current backend shows the
  unavailable service message and does not pretend to start/save.
- Weekly/daily schedule: use the Tập trung button to carry the selected task ID.
- Check keyboard focus, mobile 360px and desktop widths directly before UI sign-off.
