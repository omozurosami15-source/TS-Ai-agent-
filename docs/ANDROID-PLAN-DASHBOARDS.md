# Android Plan Dashboards – 17 Plans

The native Android selector includes all 17 supplied plan artworks and keeps the source artwork intact. Each plan has its own title, description, feature summary and translucent motion overlay.

## Plan activation mapping

The native selector maps Plan 1–17 to the existing module coupon identifiers in `docs/coupons.json`:

| Plan | Dashboard | Coupon entitlement |
|---:|---|---|
| 1 | Neural Core | `module:neural` |
| 2 | Voice Studio | `module:voice` |
| 3 | Memory Vault | `module:memory` |
| 4 | Soul Studio | `module:soul` |
| 5 | Vision Lab | `module:vision` |
| 6 | Web Intelligence | `module:web` |
| 7 | Coding Studio | `module:code` |
| 8 | File Reader | `module:files` |
| 9 | Task Automation | `module:tasks` |
| 10 | Smart Notes | `module:notes` |
| 11 | Daily Briefing | `module:briefing` |
| 12 | Translator | `module:translate` |
| 13 | Research Desk | `module:research` |
| 14 | Calendar Hub | `module:calendar` |
| 15 | Reminder Center | `module:reminders` |
| 16 | Security Center | `module:security` |
| 17 | System Analytics | `module:analytics` |

The dashboard now recognizes these module entitlements when displaying unlocked state and enabling **Set Active Default**. The **Unlock** control accepts a valid plan-specific code, checks the local coupon catalogue plus the existing remote GitHub coupon list, and refreshes the dashboard state after redemption. A code for one module does not unlock another module. The selected default remains saved on the device.

To issue codes, add the SHA-256 hash of a unique code to both `docs/coupons.json` and `app/src/main/assets/coupons.json`, using the exact `module:<slug>` entitlement above. Keep the two files synchronized. Never publish the raw activation code in either JSON file.

## Payment and admin integration boundary

The included PHP backend already provides account registration/login, an Admin > Plans editor, payment records, PayPal/card checkout and crypto checkout with signed webhook plus gateway-side verification. Those existing payments activate the backend's account subscription tiers (`Free`, `Starter`, `Plus`, `Pro`, `Max`) and their AI coin/model limits.

They are **not yet individual checkout products for the 17 dashboard modules**. The Android module unlock flow currently uses server-published coupon hashes; it does not claim that a successful Starter/Plus/etc. subscription automatically purchases a particular dashboard module. To sell each module directly, the backend needs a separate module-product/entitlement ledger and a verified payment-to-module mapping. Do not change the current subscription tier IDs to `p1`–`p17`; that would break the existing AI tier logic.

## Design and animation

- Reference artwork: `app/src/main/assets/plan_designs/plan_XX.png`.
- Portrait uses a phone-friendly stacked layout; landscape expands the visual stage.
- Native Canvas overlays provide plan-specific scan, pulse, orbit, waveform, workflow, calendar, shield and analytics motion.
- Overlays are translucent and do not replace or recolor the supplied artwork. The poster images are flattened still images, so individual objects inside the artwork are not independently animated.

## Validation and build

Run `node --check docs/app/app.js` and PHP lint on the backend files after changes. Build the Android app in Android Studio or CI with the Android Gradle plugin. This source archive does not include a Gradle wrapper or Gradle executable, so an APK build cannot be confirmed from this environment.
