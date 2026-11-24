# MISS SARA Premium UI implementation notes

- The existing TS Agent home dashboard remains in place.
- A horizontally scrollable, touch-friendly navigation bar has been added above it.
- Chat, Memory, Soul, Voice, Vision, Tasks, and Control open as separate views.
- The 17 PLANS catalogue opens 17 distinct module detail pages.
- The design uses a dark emerald / cyan glassmorphism palette, responsive grids, large touch targets, and reduced-motion support.
- Existing chat, memory editor, personality editor, task editor, microphone, settings, and coupon redemption flows are reused where they exist.

## Payment and entitlement limitation
The v10 codebase has only `free`, `p1`, `p2`, and `owner` entitlement identifiers. The 17 catalogue entries are independent UI pages, but they are not independently purchasable/activatable yet. The current coupon redeem flow only activates the existing tiers. Do not mark a plan as paid based on browser storage. Independent plans require verified server-side payment callbacks, a server-owned plan ID mapping, API entitlement checks, and matching Android-side entitlements.

## Validation performed
- `node --check docs/app/app.js`
- Node module smoke test for existing command parser and Plan API
- Static check that all 17 module IDs and 9 navigation buttons exist

No Android APK build or live payment test was performed.


## v12 usability and multimodal update

- Branding and primary navigation are bilingual (Bangla + English) for easier mobile use.
- Vision Lab now has explicit camera capture and gallery selection, image preview, image removal, task presets for diagrams/charts, screenshots/OCR, documents, objects and scenes, and a separate Analyze action.
- Images are resized in the browser before analysis and sent only to the AI provider configured by the user. The image is not sent until the user presses Analyze.
- Image analysis adapters are implemented for Anthropic Messages, Gemini generateContent, and OpenAI-compatible providers (OpenAI, Grok and compatible custom endpoints). The current Cloud endpoint does not support image input and reports that limitation clearly.
- Voice Studio now provides a microphone action, spoken sample, stop control, and adjustable browser speech rate.
- Added animated voice orb, image preview/result states, clearer controls, larger touch targets and reduced-motion support.
- Vision responses are instructed to explain diagrams step by step, distinguish visible facts from assumptions, and disclose unreadable/uncertain details.

## Privacy and capability notes

- Camera capture is user-initiated through the browser/device picker; no background camera access is added to the web app.
- The selected image is processed locally for resizing, then sent to the selected AI provider only after the user presses Analyze. Provider terms, retention, API costs and vision-model support apply.
- A model/provider without image-input support may reject the request. Select a vision-capable model in Settings.
- Android's separate Sara Watch feature remains opt-in, owner-gated and visibly notified; this update does not enable it automatically.

## v12 validation

- `node --check docs/app/app.js` passed.
- Node module smoke test loaded the application module and Plan API.
- Static checks confirmed camera/gallery inputs, image analysis action, provider adapters and bilingual navigation.
- No live API request, camera permission test, Android APK build or device-level accessibility test was performed.
