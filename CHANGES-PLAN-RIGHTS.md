# Plan rights change

## Free plan
- Uses the user's own API key, no usage limit.
- No paid features: phone actions, floating assistant, autoplay, web search are blocked (Entitlements.premium).
- Basic chat and inbox messages only.

## Plan 1 (p1)
- Companion persona. Note: the companion still starts only after owner login (Persona.kt), as set in the owner-mode requirement. The sales text says so.

## Plan 2 (p2)
- Everything in Plan 1, plus phone actions, floating assistant, autoplay, web search.

## Coins (placeholder)
- Monthly coins: free 0, Plan 1 3,000, Plan 2 10,000, owner unlimited.
- PlanRights.monthlyCoins holds these values. They are not enforced yet.

## Not done yet (needs the server)
- Sign-up bonus (1000 coins) and daily login bonus (200 coins).
- Coin counting and spending for the platform API.
- Admin panel to change plans, coins and prices.
- Purchase/payment flow. Plans are still unlocked by code.
- Paid plans calling the platform API instead of the user's key.
- Sign-up, login and remove buttons in Settings to be removed.

## Architecture
- PlanRights.kt is the single rights list. Entitlements.can() checks it.

## Owner fixes (v3)
- OwnerAuth.login: the owner passphrase can be created only on a phone that already holds the owner plan (owner code redeemed). Before, any phone could create it without the code.
- Settings: LOGIN, SIGN UP and "LOG OUT OF WEBSITE" buttons removed.

## Known limit (needs the server)
- Owner and plan codes are local. Redeeming the same code on another phone still works, because the app cannot track codes across phones. Fix: server-side single-use or device-bound codes.

## Feature switches (v4)
- FeatureSwitches.kt: companion, sara_watch, owner_mode. Off = ordinary users do not see the control or run the feature. Owner plan always sees them.
- Read from docs/feature_switches.json in the repo (set Config.GITHUB_REPO first). No file = all ON.
- Settings hides the controls via FeatureSwitches.apply(); Persona and Reminders check the companion switch.
- Later: admin panel writes these values to the server instead of the GitHub file.

## Build fix (v5)
- Glyphs.kt: BitmapDrawable import corrected to android.graphics.drawable.BitmapDrawable. This fixed the compileDebugKotlin errors (unresolved reference, ImageSpan overload ambiguity).

## Logo fixes (v6)
- ProfilePhoto (Profile screen) loads ms_sara_logo.png first, so the MS Sara logo shows instead of the masked avatar.
- Home logo (HudKit) and profile photo get a lit purple backdrop and a brightness boost, so the dark logo is visible.

## Same design for Plan 1 and Plan 2 (v7)
- Plan 2 is drawn with Plan 1's 3D scene, model (plan_01.glb), accent colour and motion label. Names, descriptions and AI roles stay separate.
- Plan3DView.lookOf(n) decides which design a plan uses; PlanDashboardActivity.motionIndex(n) decides the motion label.

## Plan 3 design (v8)
- The uploaded reference picture is saved as assets/plan_designs/plan_03_scene.png.
- Plan 3 shows that picture with a slow zoom animation and the existing overlay effects. Other plans keep the 3D view.
- Limit: a picture is not a 3D model. For a live 3D version, a GLB model for Plan 3 is needed.

## Plan 4 design (v9)
- Reference picture saved as assets/plan_designs/plan_04_scene.png (holographic brain).
- Plan 4 uses the same picture mode as Plan 3 (slow zoom, overlay effects). The picture for each plan is set in planArtFiles in PlanDashboardActivity.
- Plans 1, 2 and 5-17 keep the 3D view.

## Premium design screen (v10)
- New PremiumActivity: MISS SARA premium screen (orb animation, tiles for Chat, Voice, Vision Lab, Diagram, Premium Plans, Payment Panel, Settings, Admin Panel for owner).
- PlanRights.Feature.PREMIUM_DESIGN, included in Plan 2 and owner.
- The main REVIEW button opens PremiumActivity for entitled phones; others see the old review unchanged. The classic home is not changed.
- Manifest registers PremiumActivity.
- Not done: other reference images (1-4) are not used. Tiles open the existing screens; they are not separate new features.

## 17 premium pages (v11)
- PremiumDesigns.kt: 17 designs (style, accent colour, hero name, tagline), one per plan.
- PremiumBgView.kt: animated hero drawn in code (waves, node network, perspective grid, glowing orb, bar panels, rotating rings, radar scan).
- PremiumActivity.kt: one page per plan, opened by REVIEW for entitled phones. Each page has its hero, its own plan AI (Llm.complete with the plan's role) and quick access to the existing screens.
- Limit: the designs are code approximations of the reference pictures, not copies. Each design is distinct by style, colour and name.

## Pictures, Vision Lab, Diagram (v12)
- Reference pictures added to assets/plan_designs/rich_XX.png and mapped in PremiumPictures (plans 1, 2, 5-9, 13; plans 3 and 4 keep their pictures).
- PREMIUM_DESIGN now for Plan 1 and up (all 17 pages, animated code design). PREMIUM_RICH for Plan 2 and owner (picture designs).
- VisionLabActivity: pick a picture, ask the AI (Llm.completeVision with the Vision Lab role).
- DiagramActivity: describe a process, the AI writes Mermaid code, drawn in a WebView (Mermaid from jsdelivr CDN).
- Both registered in the manifest and opened from the premium page tiles.

## Paid AI tools, Security, Help (v13)
- AI tools (translator, text writer, code helper, summarizer, web search, file analyzer, image maker) are paid: Feature.AI_TOOLS for Plan 1 and up, owner has all. AI Chat and Voice Mode are free. Free users see a dialog with a link to the plans.
- Security (profile): shows owner mode, accessibility and overlay status; lock owner mode; owner login; opens the permission settings.
- Help (profile): Terms, Privacy, Copyright and Contact pages on the website (Config.SERVER), Telegram channel and the setup guide.
- Not done: Image Maker needs an image-generation API (the text AI cannot create images). Notifications, Appearance and Language still use the existing screens.

## Chat answers cut short (v14)
- Causes found: (1) every reply had to be the phone-agent JSON format; a plain-text answer was discarded and the loop retried silently, so the user saw one short line or nothing; (2) the prompt asked for concise voice replies, which shortened answers; (3) Voice read-out was capped at 1,500 characters.
- Fixes: plain-text answers are shown and spoken in full; the prompt asks for the full answer; the read-out limit is 4,000 characters.
- Note: the free model (gemma free) follows the JSON format poorly. A stronger model in SETTINGS gives better answers.

## Profile pages with real settings (v15)
- Security: owner mode status, lock, owner login, change passphrase, owner login length (1, 7 or 30 days), clear chat history, clear saved memory (owner), accessibility and overlay settings.
- Appearance: reduce motion (stops the animated pages), floating assistant on/off.
- Language: voice language (English, Bangla, Hindi), reply language (auto, English, Bangla; added to every AI request).
- Notifications: reminder alerts on/off, test notification, system notification settings.

## Owner code (v16)
- Only one owner code unlocks owner mode: the code given by the owner. Stored as a SHA-256 hash (id c-owner-1) in app/assets/coupons.json and docs/coupons.json.
- The old owner entry (c001) is revoked in both files, so an older copy of the file cannot bring it back.

## Full audit fixes (v17)
- Plan 2 now includes AI_TOOLS (it was missing; Plan 2 buyers would have been locked out of the AI tools).
- Config.GITHUB_REPO set to omozurosami15-source/TS-Ai-agent-, so feature switches, notices and coupon updates load from GitHub.
- Static checks passed: manifest XML, coupon and switch JSON, no conflict markers, brace balance in every app file.
- Not verified here: a real Kotlin compile and the APK run. Those happen in GitHub Actions and on the phone.
