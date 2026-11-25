# TS Agent V17 - full fixed build

Home and plans
- Active plan replaces the middle of Home with its real 3D animation (Plan3DView). Header and bottom bar stay.
- REVIEW button (top-left of the plan area) opens the same plan animation full screen with CLOSE.
- Plan 1-17 use plan_models/plan_NN.glb when present (see assets/plan_models/README.txt).
- Old photo covers and drawn scenes were removed.

Header, pages and icons
- SETTINGS/SETUP buttons stacked, custom rocket icon, name fits the space, DEV label fits.
- PROFILE, HISTORY, AI TOOLS, VOICE MODE pages with the shared bottom bar.
- Emoji replaced with code-drawn icons (Glyphs.kt). Task marker check mark kept as data.
- OWNER status shows TAP TO SET / ON / LOCKED.

Voice and chat
- Agent chat rule: full answer goes in the done message (no one-word replies).
- Mic no longer drops speech while the phone talks; a real new sentence interrupts the reply.
- TTS stop callback resets the speaking state (no stuck mic).
- SpeechGate: one voice at a time across main screen, background assistant and watch.
- Echo filter: the phone's own reply heard by the mic is not sent back as a question.
- Duplicate speech fixed (no truncated second copy of the same reply).

First-run setup
- SETUP screen opens on first launch and from the SETUP button.
- Shows microphone, notifications, overlay, accessibility and media permissions with done/waiting state.
- Each ALLOW opens the Android permission screen; the app cannot switch permissions on by itself.
