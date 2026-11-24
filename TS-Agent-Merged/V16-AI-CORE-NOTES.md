# TS Agent v16 — AI Core upgrade

Based on the uploaded TS-Agent-full-v15.zip.

Implemented:
- More complete responses rather than forcing all answers to be very short.
- Better uncertainty handling: do not guess age, dates, event times, names or amounts; ask one focused clarification when needed.
- Agent task ceiling raised from 20 to 30 actions and timeout from 3 to 5 minutes.
- More recent action and conversation context retained.
- Requested output token budgets are now passed to Gemini and OpenAI-compatible providers; v15 accepted maxTokens but did not send it for those providers.
- Network read timeout increased to 90 seconds.
- Floating voice assistant retries recoverable recognition errors with bounded backoff.
- Main speech input allows longer natural pauses.

Limitations:
- This is an AI-core/reliability upgrade, not a new foundation model. Intelligence still depends on the chosen provider/model, API key, quota and network.
- True token-by-token streaming, long-term searchable memory, structured function calling, Bengali date/time parser, live camera streaming and broad app integrations are not implemented here.
- Speech recognition quality depends on Android's speech service, language pack, device and network.
- Calendar/SMS/email may still open confirmation screens requiring user action.
- This ZIP has not been compiled or tested on a physical phone here. Run GitHub Actions before installing.
- It is based on the uploaded v15 ZIP, not a fresh clone of live GitHub main. Merge against current main rather than blindly overwriting newer files.

Next engineering phase:
1. Provider-native streaming with cancellation.
2. Persistent searchable conversation memory with user-controlled save/forget.
3. Structured tool calling and strict action validation.
4. Bengali date/time parser with timezone confirmation and tests.
5. Plan-execute-inspect-retry task state machine.
6. Camera vision capability detection and explicit capture UI.
7. Automated tests for Bengali names, dates, ages, multi-turn chat and Android action flows.
