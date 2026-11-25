# TS Agent - merged full build (all features)

Base: TS-Ai-agent-v16-fixed (Android app + backend + AI core upgrade).

Merged in from the premium package:
- 17 premium plan pages, each unlocked by its own code (docs/app/app.js).
- Plan codes: docs/coupons.json and app/src/main/assets/coupons.json (hashes only).
- Code generator: tools/make-coupons.py (module:<id> codes).
- English-only web navigation and SIGNAL SCAN radar on the web home.

Kept from v16 (all Android features): voice, mic, agent, Termux jobs (animate, gitlog, date,
organize plan/apply, storage, runbg/stopbg, runpy, edit), WhatsApp/Telegram/imo messaging,
inbox, channel posts and reads, notifications, scam alerts, prayer/Ramadan, Bangladesh info,
weather, cPanel, evidence, code writing/running, camera photos, screen explain, daily jobs,
local Qwen provider, owner lock, Sara Watch (off by default), batch 1/2/3 helpers.

Not included here: live backend setup (backend/ is in v16; configure it on a server),
native device testing, and the Android build itself (run GitHub Actions first).
