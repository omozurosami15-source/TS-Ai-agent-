# Android Plan-aware Home implementation

This change is for the native Android app only. It does not modify `docs/index.html`, `docs/demo.html`, or the web app.

- The existing MISS SARA MainActivity layout and its controls remain in place.
- A PLANS shortcut opens the native 17-plan dashboard; Settings also contains the same entry.
- The purchased plan's matching `app/src/main/assets/plan_designs/plan_XX.png` artwork is applied behind the existing MainActivity HUD stage.
- Free users continue to see the original Matrix/HUD Home without a plan artwork overlay.
- `p1` and `p2` coupon entitlements map to designs 01 and 02. The 17 module coupon products map to designs 01–17 (neural, voice, memory, soul, vision, web, code, files, tasks, notes, briefing, translate, research, calendar, reminders, security, analytics).
- The 17-plan dashboard is declared in the Android manifest and can be opened from MainActivity.

Build verification note: this environment did not have an Android SDK / Gradle executable available, so an APK build could not be run here. ZIP integrity and source/resource presence were checked.
