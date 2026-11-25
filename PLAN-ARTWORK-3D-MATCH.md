# TS Agent — Reference Artwork to 3D Models

The 17 reference artworks in `app/src/main/assets/plan_designs/plan_01.png` through `plan_17.png` were used as visual references. The new matching low-poly/stylized 3D models are in `app/src/main/assets/plan_models/plan_01.glb` through `plan_17.glb`.

Each GLB is texture-free, uses dark structural materials plus the plan accent emission, is centered and normalized to a maximum 1.82-unit extent (inside the requested 2×2×2 box), and remains below 60,000 vertices. The Android `Plan3DView` already loads the matching GLB by selected plan number; the GLBs now override the prior procedural fallback scenes.

## Per-plan visual mapping and motion

All motion is a repeating 5-second loop: one slow 360° rotation around the vertical axis plus a subtle 2.5% scale pulse. There is no translation or deformation. User drag remains available. AI response bursts can temporarily add a small pulse.

| Plan | Reference-matched 3D composition | Motion |
|---|---|---|
| 01 Neural Core | Central cyan neural orb, three orbit rings, linked nodes, compact command-console base and side UI panels | 5 s rotation + pulse |
| 02 Voice Studio | Neural/voice network sphere, linked nodes, orbital rings and paired holographic interface panels | 5 s rotation + pulse |
| 03 Memory Vault | Dark AI dashboard slab, grouped data cards, status core and compact bar chart | 5 s rotation + pulse |
| 04 Soul Studio | Control-room panel array, central floating hologram, node links and circular platform | 5 s rotation + pulse |
| 05 Vision Lab | Analytics/control board with chart tiles, bars and rising trend line | 5 s rotation + pulse |
| 06 Web Intelligence | Audio console, microphone ring and sculpted waveform bars | 5 s rotation + pulse |
| 07 Coding Studio | Laptop, code lines/cursor and rising code-bar columns | 5 s rotation + pulse |
| 08 File Reader | Fanned document sheets with line details and scanning magnifier | 5 s rotation + pulse |
| 09 Task Automation | Interlocking gear-like rings, teeth, linked workflow path and nodes | 5 s rotation + pulse |
| 10 Smart Notes | Notebook, paper lines, highlighted strip and pen | 5 s rotation + pulse |
| 11 Daily Briefing | Timeline clock, hands, hour markers and lower timeline | 5 s rotation + pulse |
| 12 Translator | Blue/red language panels, abstract glyphs, center ring and transfer arrow | 5 s rotation + pulse |
| 13 Research Desk | Stacked books and magnifier lens/handle | 5 s rotation + pulse |
| 14 Calendar Hub | Calendar slab, binding loops, date tiles and selected day | 5 s rotation + pulse |
| 15 Reminder Center | Bell, clapper, rim and floating notification cards | 5 s rotation + pulse |
| 16 Security Center | Extruded shield, central lock and side circuit traces | 5 s rotation + pulse |
| 17 System Analytics | Bar chart, base, rising trend line and data nodes | 5 s rotation + pulse |

## Matching limitation

The supplied references include both detailed scene renders and promotional cards with text, prices and unlock controls. The 3D models reproduce the central visual subject and composition rather than text, UI copy, or photographic background detail. Plan 02's reference is a brain/network console rather than a literal microphone; its 3D composition follows that supplied reference. Plan 06's reference is a voice waveform console, so its 3D model follows that visual instead of the short scene description in the original brief.
