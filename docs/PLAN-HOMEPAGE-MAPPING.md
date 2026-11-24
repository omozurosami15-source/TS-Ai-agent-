# Plan-based Web Home Artwork

The MISS SARA Web home keeps its existing Matrix/HUD layout, navigation and buttons. Once a user signs in to the PHP account backend, the web app requests `api.php?action=me`; the backend returns the effective plan ID/name after checking expiry.

Mapping: Free subscription `plans.id = 1` keeps the original Matrix home. Paid subscription IDs 2–18 select artwork `plan_01.png` through `plan_17.png` respectively. If a subscription expires, `effective_plan()` returns plan 1 and the original Matrix home is restored. This uses the existing verified payment activation (`users.plan_id` / `plan_expires`) and does not trust a browser-only purchase flag.

The 17 Android module coupon entitlements (`module:neural`, `module:voice`, etc.) remain a separate system. They are not silently converted into paid account subscriptions. The existing nav buttons and views are not removed.

The web app defaults `CFG.SERVER` to `../../backend` for a same-host deployment. If the PHP backend is hosted on a different domain, change `CFG.SERVER` in `docs/app/app.js` to that backend base URL and configure its CORS origin. GitHub Pages alone cannot run PHP; deploy the `backend/` folder on a PHP/MySQL host for verified subscription switching.
