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
