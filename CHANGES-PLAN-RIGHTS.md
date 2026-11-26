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

## Reply cut off by the microphone (v18)
- Cause: while the phone spoke, the mic kept listening. The phone's own voice came back into the mic, and the echo check often failed to match it, so the app treated it as the user interrupting and stopped the reply after the first line. Partial results and the start of speech also stopped the reply.
- Fix: the mic is off while a reply is spoken (listen() refuses to start; the recognizer is cancelled when a reply starts). Results that arrive during a reply are ignored. Listening restarts when the reply ends. Only the ABORT button stops a reply.
- Applies to the main screen and the floating assistant.
- Trade-off: the user cannot interrupt by voice during a reply; use ABORT. Voice interruption can be added back later with headset-only detection.

## Plan 1 page only (v19)
- Removed the previous premium designs (animated code designs and the picture pages for plans 2-17) and their pictures. Plans 3 and 4 no longer show pictures on the plan screen.
- REVIEW opens the new Plan 1 page (AI Core) only for Plan 1 phones with the premium design.
- Plan 1 page: header with title and status, the core picture in a frame, ask the plan's AI, and buttons to Chat, Voice, Vision Lab, Diagram and Plans.
- Not verified visually. Needs a phone screenshot to compare with the reference picture.

## Restore default home (v20)
- Appearance → "Restore default home" resets: home shortcuts, dashboard plan, reduce motion (off), floating bubble (on).
- Chat, memory, tasks, plan and owner data are not touched. The defaults are built into the app.

## Plan 2 page (v21)
- PlanTwoActivity: Voice Studio page after the AI VOICE ASSISTANT reference picture: title, glass panel with animated cyan and magenta waveform (WaveView), real status (mic, plan, reply language, voice), ask the plan's AI, and buttons.
- REVIEW opens it for Plan 2 phones with the premium design; Plan 1 keeps its page.
- Not pixel-identical: the waveform is drawn in code, the hologram hexagon and the background scene are not included.

## Plans button in Profile (v22)
- Profile page: a "Plans" row opens the plan screen (pgPlans), placed under Personal Information.

## Side button text stays inside the box (v23)
- MEMORY, CHAT and SOUL labels on the home screen shrink to fit before the arrow, so they no longer run past the box edge.

## Side button text clipped to the box (v24)
- The MEMORY, CHAT and SOUL labels are clipped to their box, so a letter cannot be drawn outside it. The text room is also wider on the right.

## Plan 3 page (v25)
- PlanThreeActivity: Memory Vault after the holographic microphone reference picture: title, a stage with the glowing capsule, arc rings, ripples on the platform (MicView), real status (saved notes for the owner, plan, mic), ask the plan's AI, and buttons.
- REVIEW opens it for Plan 3 phones with the premium design.
- Not pixel-identical: the scene is drawn in code. The room and side panels of the picture are not included.

## Settings reverted (v27)
- Settings owner and Sara Watch controls are back as before (v26 change reverted).
- Face matching for owner unlock is dropped at the owner's request.

## Chat path (v28, from video 1)
- Talk and questions (no phone action words) go straight to the AI as a normal conversation: plain text answer, full answer, shown and spoken in full.
- The system prompt asks the AI to understand mixed Bangla and English and to reply in the user's style; reply language setting applies.
- A "Thinking..." status shows while the answer is being made.
- Phone actions (open, call, send, upload, alarm, reminder, timer, and similar) keep the agent loop.
- Limit: routing is by keywords. A question that contains an action word (for example "how do I open a bank account") goes to the agent path.

## Plan 4 page (v29)
- PlanFourActivity: Soul Studio after the holographic brain reference picture: title, a stage with a glowing core, a light column to a platform with ripples, two orbit rings that turn (BrainView), real status (plan, mode), ask the plan's AI, and buttons.
- REVIEW opens it for Plan 4 phones with the premium design.
- Not pixel-identical: the brain is a glowing core, not the 3D brain of the picture; the side dashboards are not included.

## Stronger understanding (v30)
- Phone agent prompt: a UNDERSTANDING section (mixed Bangla/English, Latin-letter Bangla, speech mistakes, examples), ask one question when a command is missing a key detail, never guess a recipient, retry a failed step once then say what went wrong.
- Chat prompt: infer meaning from context when speech looks garbled.
- Existing app-name matching (Bangla aliases and fuzzy names) is unchanged.
- Limit: the real strength depends on the model. Prompt changes alone cannot make a weak model strong.

## Plan 5 page: AI companion (v31)
- PlanFiveActivity: a personal AI companion the user names. Name shows at the top. ON/OFF with the 18+ confirmation (same comp_on key as the Settings companion).
- It says it is an AI, is warm and supportive, is never possessive, no sexual content, points to help if the user is in danger.
- Memory: the user saves what the companion may remember; view or clear all.
- REVIEW opens it for Plan 5 phones. Companion access comes from Entitlements.companion (Plan 1 and up).
- Note: the Settings companion on the main screen still needs owner unlock (Persona.kt). The Plan 5 page does not.

## Check of plans 1 to 5, and plans 6 and 7 (v32)
- Check (code only): Plan 1 and Plan 5 had no animation; added a breathing animation to each. Plans 2, 3, 4 already animate. None of these were checked on a phone.
- Plan 6 (Web Intelligence, green control room): flowing green waves and a turning network globe, real status (web search on/off, plan), ask with the plan AI and search when enabled.
- Plan 7 (Coding Studio, robot desk): a glowing robot head that blinks, a tools row, a code box and RUN button.
- REVIEW opens them for Plan 6 and 7 phones. Not pixel-identical to the pictures.

## Plans 8 and 9 (v33)
- Plan 8 File Reader: hero is the sci-fi bridge picture (plan_08_scene.png) with a slow zoom, real status, ask the plan AI.
- Plan 9 Task Automation: hero is the control-room picture (plan_09_scene.png), same page structure.
- Shared base: PlanPicturePage. REVIEW opens them for Plans 8 and 9.
- Not pixel-identical checks; the hero is the picture itself, the status lines are real.

## Plans 10 to 17 (v34)
- Each plan has its page on PlanPicturePage with its reference picture as the hero: 10 Smart Notes (AI control dashboard), 11 Daily Briefing (voice analytics), 12 Translator (MISS SARA banner), 13 Research Desk (atom), 14 Calendar Hub (voice hologram), 15 Reminder Center (robot desk), 16 Security Center (control room), 17 System Analytics (AI core brain).
- Some pictures are reused across plans, because only a few distinct reference pictures were available. The uploads of app screenshots were not used as designs.
- REVIEW routes 10 to 17 to their pages.

## One photo per plan (v35)
- Plans 8 to 17 now each use a different photo: 8 sci-fi bridge, 9 control room, 10 AI control dashboard, 11 green sphere chat, 12 MISS SARA banner, 13 atom, 14 lime dashboard, 15 MISS SARA poster, 16 Lumi chat, 17 voice analytics.
- Plans 1 to 7 keep their own photo or drawn design (see notes above).
- Limit: each page still shows the photo as the hero with the same layout. A bespoke layout that matches each photo's panels and layers is not built yet.

## Plan 1 rebuilt in layers (v36)
- Layer 1: the photo, sized to its own aspect ratio.
- Layer 2: title and real status placed inside the curved top panel.
- Layer 3: a tap area over the brain that opens the question box.
- Layer 4: a control bar placed on the platform (CHAT, VISION, DIAGRAM, PLANS).
- Layers use fractions of the photo, so they stay on the same spots at any width.
- Limit: positions were set from the photo, not measured on a phone. Needs a screenshot check.

## Build fix (v37)
- MicView was defined twice (the project already has MicView.kt). The Plan 3 view is renamed PlanThreeMicView, and the Plan 6 view is renamed PlanSixWebView, so no class name clashes.

## AI Builder (v38)
- BuilderActivity: describe a website or app, the AI writes the files (JSON), and they are saved in the app's own folder (filesDir/projects/<name>/). Paths are checked so files stay inside the project folder; limits: 40 files, 300 KB each.
- Builder model: an optional stronger model, saved in Builder, is used only for builds (the normal model is put back after).
- Plan access: Feature.BUILDER, Plan 2 and up. Opened from the Plan 17 page.
- Limits: the AI writes the files, the app does not run them. Zipping, previews and publishing are not done. Quality depends on the model.

## Builder: zip, cPanel deploy, database, self-fix (v39)
- ZIP: packs the project folder into <name>.zip in the app folder.
- DEPLOY: uploads each project file to the first cPanel site in Settings (CpanelClient.write), checks the site URL (CpanelClient.check). If the check is not 200, the AI reads the result and the files, fixes them, and redeploys; at most two fix rounds.
- DATABASE: creates a database on the cPanel site (CpanelClient.createDb) named after the project.
- Not done: running PHP on the phone; reading PHP error logs; choosing a site; checking that the fix works beyond the HTTP check. The cPanel host, user and token must be set in Settings.

## Builder: approval and real site check (v40)
- Upload and database creation need a confirmation tap first (the user's permission for each action).
- Site check reads the page: fails on HTTP 400 and above, and on PHP or database error text (Fatal error, Parse error, Warning, Notice, undefined variable, MySQL error). The error text is given to the AI for the fix.
- Still not done: server error logs, testing the site in a real cPanel account, and a verified end-to-end run.

## Plans 16 and 17 on full-width photos with moving layers (v41)
- Plan 17 (System Analytics): the brain picture (1000232249) full width, with a pulsing light and two turning orbit rings over it, a real status box, a tap area over the brain, and a control bar under it.
- Plan 16 (Security Center): the atom picture (1000232234), with a pulsing light and two turning orbit rings, a status box, a tap area, and a control bar.
- A shared base class PlanLayeredPage and a GlowLayer view draw the moving layer. The photo breathes with a slow zoom.
- Limit: the atom picture has a white background, which stays white. The moving layer and the object are the picture's own.

## Owner unlock no longer puts Plan 1 on Home (v42)
- Cause: for the owner, dashboardPlanNumber fell back to 1 when no plan was chosen, so Home switched to the Plan 1 3D view right after owner unlock.
- Fix: the owner now sees the original Home until a plan is chosen on purpose in the plan screen.

## Chat text and identity (v43)
- Replies show clean text: markdown **bold**, __bold__ and # headings are removed before they reach the chat (plainText in Agent).
- Identity: the assistant is MISS SARA from TS Agent, made by Tarek, and does not name another company as its maker. The screenshot showed "Particle AI" as the maker, which came from the model underneath.

## Markdown symbols removed everywhere the AI text is shown (v44)
- Md.plain(): one shared cleaner for **bold**, __bold__, # headings and * lists.
- Used for chat replies (Agent) and every plan, tool and premium page that shows an AI answer.
- Not used on code (Diagram, Builder), on the raw JSON the agent reads (Llm), or on the agent's own parser, so code and JSON are not changed.

## Full chat page (v45)
- ChatPageActivity: message list (user right, MISS SARA left), bottom bar with + (photo, web search on/off, clear), text box, MIC (speech to text) and SEND.
- Under each AI reply: COPY, SHARE, SPEAK, AGAIN (re-ask the last question).
- Photos go through the vision AI (Llm.completeVision). Replies are cleaned with Md.plain.
- Every CHAT button opens this page: the home CHAT node, and the CHAT/Chat buttons on the plan pages.
- Limits: messages are kept only while the app runs (not saved between launches); camera capture is not included (photos only).

## AI CHAT tool routed to the chat page (v46)
- The AI TOOLS page's "AI CHAT" tile opens ChatPageActivity (it used to send the text to the old main chat).

## Animations cleared, one photo per plan (v47)
- Removed every drawn or animated plan design: the wave, brain, microphone, robot, web-globe and layered-glow pages, and the photo zoom. Old plan pages 1 to 17 are replaced by one base page (PlanBasePage) with a per-plan photo, status, controls and question box, and no animation.
- Each plan has its own photo: 1 AI core, 2 voice hologram, 3 microphone, 4 voice analytics, 5 MISS SARA AI card, 6 green control room, 7 robot desk, 8 sci-fi bridge, 9 control room, 10 AI dashboard, 11 green sphere chat, 12 MISS SARA banner, 13 lime dashboard, 14 Lumi, 15 MISS SARA poster, 16 atom, 17 brain.
- Plan 5 keeps its companion page (name, on/off, memory) without the breathing animation.
- Next: design each plan from its photo, one at a time.

## Plan 1 designed from its photo (v48)
- Photo full width, at its own proportions.
- Status box inside the curved top panel (plan, mic, reply language), real values only.
- Tap area over the brain: a tap opens the question box.
- Control bar on the platform under the brain: CHAT, VISION, PLANS, BACK.
- No animation yet (animation is added after the layout is approved).
- The base page takes the layer positions from each plan, so plans 2 to 17 can be placed the same way from their photos.

## Plan 1 animation (v49)
- The brain has a pulsing light and two orbit rings that turn in opposite directions (GlowView).
- The photo has a slow zoom (5 seconds in, out).
- Other plans have no animation yet.
