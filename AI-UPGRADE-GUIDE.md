# TS Agent AI Upgrade — File Center + Plan Mode

This package uses the clean `TS-Agent-Merged` project as its source baseline. The unresolved Git merge markers from the outer project copy are not carried into this package.

## Added in this upgrade
- **＋ FILE** opens Android's system document picker for source files, ZIP archives, photos and documents.
- Selected files are copied into the app's private `filesDir/assistant_uploads` folder.
- **Text/code files** (`.kt`, `.java`, `.py`, `.js`, `.ts`, `.xml`, `.json`, `.md`, `.txt`, `.yml`, `.yaml`, `.gradle`, `.kts`, `.sh`, `.sql`, `.php`, `.properties`) are added to the next AI prompt as a preview capped at 96 KB.
- **ZIP files** are inspected locally and up to 250 non-directory entries are listed in the next AI prompt. The archive is not extracted.
- **PLAN** toggles a simple plan-first instruction for the next request; it asks the existing configured AI provider to make an understandable step-by-step plan before doing safe work.
- Attachment selection and plan mode are one-shot: both clear after the next submitted task.

## Important current limitations
- The existing LLM layer is text-prompt based. This upgrade does **not** transmit image pixels or PDF pages to a vision model. Photo/PDF selection is supported, and the app tells the AI that the file exists, but image/PDF understanding requires a separate multimodal upload implementation.
- ZIP handling currently provides an inventory only; it does not recursively unpack or compile projects.
- Large source files are previewed only up to 96 KB.
- User-selected files stay in private app storage. No server upload is performed by this feature.

## Build
Open this project in Android Studio and run the `assembleDebug` task, or push it to GitHub and run the included Build APK workflow. A successful source edit is not proof of a successful APK build; validate on CI/Android Studio and test file selection on a real device.
