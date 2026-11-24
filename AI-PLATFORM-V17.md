# TS Agent / MISS SARA — AI Platform Upgrade v17

This release strengthens the existing Android + PHP AI platform. It is not a new foundation model: intelligence still comes from the configured provider/model and its available quota.

## Platform architecture

- **Android agent runtime:** task classification, multi-step execution, screen inspection, bounded retries, cancellation, and completion verification.
- **Model gateway:** Claude, OpenAI, Gemini, Grok, OpenAI-compatible endpoints, local OpenAI-compatible models, and TS Cloud where configured. Provider/model failover is retained.
- **Multimodal:** image understanding through the device-side vision path; camera/screen capture remains permission-gated and user-visible.
- **Voice:** Android speech recognition/synthesis plus optional ElevenLabs voice.
- **Device tools:** app navigation, accessibility-based screen actions, alarms/timers, calendar drafts, files, media sharing and supported settings shortcuts. Sensitive or irreversible actions require confirmation.
- **Cloud services:** PHP/MySQL accounts, plans, coin metering, model/provider administration, health alerts, payment callbacks and release/feed endpoints.
- **Memory:** local conversation history and user-controlled memory are retained on device. Memory is not a substitute for a secure database and must not contain passwords, API keys, payment credentials or one-time codes.

## v17 runtime changes

1. Expanded agent task ceiling from 30 to 40 action cycles.
2. Expanded maximum task runtime from 5 to 6 minutes. Cancellation and generation replacement still stop stale tasks.
3. Added an explicit internal task workflow: classify → plan → execute one safe action → inspect evidence → revise → verify completion. Hidden reasoning is not exposed to the user.
4. Strengthened tool-result honesty: the agent must not report a send, upload, payment, file write or setting change as successful without result evidence.
5. Increased response budgets for writing, coding, debugging, research, analysis, reports and translation to reduce premature truncation.
6. Preserved provider failover, image support, local history, voice and existing 17-plan/3D dashboard modules.

## Admin operations

- Configure provider keys and model IDs in **Admin → AI and coins**. Keys remain server-side for TS Cloud.
- Test each provider key and check its last error / health state before enabling it for users.
- Set tier priorities and realistic coin costs based on the provider's actual billing.
- Configure Telegram alert token and chat ID, then send a test alert.
- Configure SSL, database credentials and allowed CORS origin before production use.
- Never commit API keys, signing keys, database passwords or production tokens into the ZIP or Git repository.

## Important boundaries / not included in this release

- No new proprietary foundation model is bundled.
- True token-by-token streaming, vector/embedding search, server-synced long-term memory, autonomous background web research, broad third-party OAuth integrations and a general plugin marketplace are not implemented by these changes.
- TS Cloud's existing text chat endpoint does not automatically gain image input; device-side vision uses the configured vision-capable provider.
- Payment success must be confirmed by the configured provider webhook/return flow. A dashboard unlock button or local preference is not proof of payment.
- Android APK compilation and physical-device tests must run in CI or Android Studio; source ZIP validation alone is not an APK test.

## Build and validation

Run the repository's GitHub Actions **Build APK** workflow after pushing the project. Test login, each provider, coin reservation/refund, voice, image analysis, permission denial, action confirmation, cancellation, 17 plan screens and 3D rendering on a real Android device.
