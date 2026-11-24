# TS Agent website and admin panel (PHP + MySQL)

## Setup
1. Upload all files to public_html (including the `uploads` folder and the `.htaccess` files).
2. Create a MySQL database and user in cPanel.
3. Put the database details in `inc/config.php`.
4. Open `yourdomain.com/install.php`, choose an admin username and password, then delete `install.php`.
5. Open `yourdomain.com/admin.php`, go to Settings: site URL, payment settings and the notice.
6. In the App (APK) tab, upload the APK. The download button appears on the landing page automatically.
7. In the Android project, set `SERVER` in `Config.kt` to your domain and rebuild the app.

## How it works
- The user's AI key stays on their phone. Before each task the app calls `api.php?action=run`, which checks the plan and the daily limit.
- Pages: landing page, account page (sign up, login, payments, download), admin panel (dashboard, users, payments, plans, APK, settings).

## Payments
- PayPal / card: the account page creates an order, the user pays on PayPal, and the server captures it, checks amount and currency, then activates premium.
- Own gateway (TS Pay) Crypto API: in the gateway panel unlock the Crypto API, enable crypto, add a crypto wallet address under payment numbers and generate the Crypto API key. In Admin > Settings enter the gateway URL, the Crypto API key (creates payment links) and the main API key (verification and webhook signature; the gateway signs webhooks with the main key).
  - `tspay.php` calls `crypto-checkout` (USD, metadata with user and plan ids).
  - `tspay_webhook.php` (signature and 5 minute timestamp check) and `tspay_return.php` both call `verify-payment`, compare status, currency, amount and user, then call `activate_payment()`. The plan is activated only once.
  - The gateway accepts only public https webhook URLs, so the site needs SSL. Keep both keys on the server only.
- To add another gateway: when a payment succeeds, insert a row in `payments` and call `activate_payment($id)`.

## Prices and Telegram
All prices are in US dollars. The official Telegram channel (https://t.me/ts_official_top) is linked on the landing page, the account page and in the app; change the link in Admin > Settings.

## Free mode
The site starts in free mode: every user is unlimited, pricing and payment buttons are hidden, and the payment endpoints refuse requests. When you want paid plans, open Admin > Settings, set Free mode to Off and save. Plans, limits and all three payment methods are already built and become active immediately.

## Design
Hacker theme on every page: matrix rain, scanlines, glitch title, terminal demo. Animations are disabled for users who enable "reduce motion".

## TS Cloud backend (coins and hosted AI)
Run `install.php` on a fresh database (it creates the coin, provider and model tables). Then Admin > "AI & coins": add provider keys, add models per tier (use "List models"), set the alert bot token and chat id, press "Send a test alert". Default plans: Free 20 coins/day; Starter, Plus, Pro, Max with more coins and higher tiers. Endpoints used by the app: `api.php?action=register|login|me|chat` (header `X-Token`). CORS is open by default; restrict it to your web app address in Admin > AI and coins. Needs PHP with cURL and MySQL, and HTTPS. This code was written without being run, so test register, login, a chat request and a test alert first.


## Signup bonus coins
Admin > AI and coins > "Signup bonus coins" (default 30, 0 = off). Given once when someone registers; only the first 2 signups per IP address per day get it (a hashed IP is kept in the table `signup_log`, created automatically).

## Posts feed (like a channel: photos, videos, text, links)
- Admin > **Posts**: write a title and text, add an optional https link, and attach a photo (JPG, PNG, WEBP) or a short video (MP4, WEBM). Publish. Hide, show or delete any post later.
- Public page: `feed.php` (linked as "./feed" on the landing page).
- App and API: `api.php?action=feed` returns the newest 50 visible posts. In the Android app and the web app, SETTINGS > "LATEST FROM TS (feed)" shows them. Tapping a post opens its link or photo/video.
- Videos are limited by your hosting upload size (php.ini `upload_max_filesize` and `post_max_size`). Keep clips short.
- Only publish content you own or have permission to share.
