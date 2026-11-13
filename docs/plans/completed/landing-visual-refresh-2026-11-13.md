# Landing visual refresh — 2026-11-13

Branch: `feature/dev1-landing-timer`. Reference: https://kairostudy.app/ .
Read public HTML, typography references and layout declarations; no reference images
or source code were copied. Browser connection was unavailable. Reference CSS fetch
returned 403, so no claim of observing its exact motion or rendered mobile layout.

## Delivered

- New editorial hero, local Lora headings and existing Be Vietnam Pro body/theme.
- Original generated study still-life, floating accents and three hero scenes
  (still-life, schedule, weekly progress). Rotation every 5 seconds, previous/next,
  individual scene buttons and pause/play. Manual selection resets the interval and keeps rotation enabled;
  keyboard focus pauses it, hidden tabs do not advance, reduced-motion disables rotation
  and floating/entrance animations.
- Three onboarding steps, alternating schedule/shared-budget/progress previews,
  six native expandable FAQs, sticky navigation, mobile menu and final signup CTA.
- Product previews explicitly marked as illustrations, not account data. No Timer,
  Recall or adaptive-plan completion claims, fabricated testimonials or pricing.
- Removed the repetitive “AI gợi ý. Bạn điều chỉnh và quyết định.” hero line and
  “AI hỗ trợ. Bạn quyết định.” footer copy. Functional AI explanations remain in FAQs.
- All landing anchors, including skip link/features/FAQ, stay on landing for guests
  and signed-in users. Existing registration/login callbacks remain intact.

## Verification

Frontend lint clean, 109/109 tests and build passed. Navigation regression assertions
cover added anchors. SSR smoke: one H1, six FAQs, three labeled feature previews,
carousel controls and removed copy. Direct Vite requests: page/CSS/image/font all 200.
Read the downloaded TTF cmap independently: checked Vietnamese characters are present.
Inspected generated asset visually. Actual rendered desktop/mobile, keyboard clicks
and animation behavior remain unverified because no browser connection was available.
Backend, DB, credentials and Timer implementation unchanged. No GitHub push.

## Assets

- `frontend/src/assets/landing-study-still-life.png` (original PNG, 2,066,084 bytes).
  Generated with the built-in imagegen tool; saved in project and used by LandingPage.
- `frontend/src/assets/fonts/Lora-Variable.ttf` and `Lora-OFL.txt` downloaded from
  https://github.com/google/fonts/tree/main/ofl/lora . Font is local, scoped to landing.

Final image-generation prompt:

> Create an original premium editorial website hero illustration for a Vietnamese student study planner. Square composition, no text, no letters, no logos, no numbers. A quiet sculptural still life: one closed muted forest-green linen textbook, one warm ivory notebook partly open with blank pages, an elegant curved eucalyptus branch laid diagonally nearby, a small pale sage ceramic cup. Warm cream seamless background exactly close to #f6f3eb, soft natural shadows, calm tactile paper and linen textures, high-end art-directed 3D render with a gentle watercolor softness. Objects grouped in the lower central two thirds with generous breathing room. Palette forest/moss green #285b53, sage #b8c8ae, ivory and warm sand. Sophisticated, minimal, welcoming academic aesthetic. Do not copy any existing brand illustration. No embedded UI, no floating panels, no decoration beyond the still life.

## Preview

http://127.0.0.1:5173/#landing — hard refresh. Check rotation/pause, manual selection,
reduced-motion, menu at 360px, FAQ expansion and signup/login for a guest, then test
anchors and Go to Overview with an authenticated account.
## Scroll reveal follow-up

Landing sections now fade in and rise 22px on first viewport entry (700ms), with 100ms stagger for cards. Content is visible by default without IntersectionObserver or with reduced-motion; keyboard focus reveals its section immediately. Observer/listeners clean up on unmount. Final lint, build and SSR render smoke passed; actual scrolling in a browser remains unverified.
