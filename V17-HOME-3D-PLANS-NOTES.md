# TS Agent v17 — Home redesign, real 3D plans, 17 plan AI systems

Based on TS-Agent-AI-ANIMATED-PLANS-FIXED.zip.

## What changed
**Home screen** now follows the supplied design: photo header with crown, MISS SARA + "Your AI Assistant", SETTINGS / SETUP,
status strip (UPTIME, ACCESSIBILITY, OWNER, PLAN, COINS, COMPANION), terminal box with AI CORE badge, big MEMORY / CHAT / SOUL buttons,
dev Tarek4x pill, globe with platform and curved name, LISTENING + equalizer, SIGNAL SCAN (Status: OK), TODAY.TASKS (percent + bars),
bottom bar HOME / VOICE MODE / AI TOOLS / HISTORY / PROFILE and the footer line.
All earlier features and animations are kept: digital rain, CRT scan overlay, boot sequence, glitch title, rotating radar, MicView (now inside VOICE MODE),
ABORT (chip on the stage), chat row (CHAT button: FILE / PLAN / EXEC), long-press on the mic switches voice language, tasks editor, news refresh, floating assistant.
- AI TOOLS opens the plan screen. HISTORY shows the session transcript (and the latest TS posts). PROFILE shows plan, coins, access. Tap the PLAN cell to open plans.
- Avatar: `app/src/main/assets/miss_sara_avatar.png` was cut from the supplied screenshot (small). **Replace it with your original HD file (same name, square, 512 px or more).**

**17 plans = 17 real 3D scenes** (`Plan3DView.kt`, OpenGL ES 2.0). No photos: every scene is built from lit 3D meshes with its own animation
(Neural Core, Voice Studio equalizer ring, Memory Vault, Soul lotus, Vision eye, Web globe, Code </>, File Reader, Task gears, Smart Notes, a clock showing real time,
Translator cards, Research Desk, Calendar, Reminder bell, Security shield, Analytics bars) in front of an animated shader background. Drag to turn the object.
The scene reacts to the AI: ready / listening / speaking / thinking change its speed and glow, and every AI reply gives it a pulse.
The old `plan_designs/*.png` posters are no longer used by the app (they can be deleted).

**Home follows the plan:** free users keep the globe. When a plan is bought (code redeemed) it becomes the default and its 3D object + background replace the globe.
Several plans can be owned; "SET ACTIVE DEFAULT" in the plan screen chooses which one Home shows (the owner can pick any).

**17 plan AI systems** (`PlanCatalog.kt`, `PlanAi.kt`, hooked into `Llm.kt`): the active plan adds its own specialist instructions, answer length budget and (plans 6 and 13) web search,
and uses its own model: TS Cloud uses tier 1/2/3 by plan; with your own key you can set a different model per plan and provider (plan screen > PLAN AI MODEL, empty = SETTINGS model).
TEST PLAN AI asks that plan's engine a question.

## Honest limits
- A plan does not add a new foundation model. Quality still depends on the provider, key and model.
- Not compiled or run here (no Android SDK). Run the GitHub Actions build first and test on a phone. If the 3D shader fails on a device the log tag is `Plan3D`.
- Each plan is bought with a coupon code as before; there is no per-plan payment backend yet (see ANDROID-PLAN-DASHBOARDS.md).
- The built-in 3D is procedural geometry. For artist-quality models put `plan_01.glb` ... `plan_17.glb` in `app/src/main/assets/plan_models/` (see README.txt there): the app loads them automatically and falls back to the built-in scene if a file is missing or cannot be read. Textures, rigging, glTF animations and Draco/meshopt compression are not supported yet.
