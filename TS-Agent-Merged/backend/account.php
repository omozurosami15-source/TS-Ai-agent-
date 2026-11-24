<?php
require __DIR__ . '/inc/db.php';
ensure_schema();
require __DIR__ . '/inc/ui.php';
require __DIR__ . '/inc/paypal.php';
require __DIR__ . '/inc/tspay.php';
$err = ''; $ok = '';
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    if (!csrf_ok()) $err = 'Session expired, refresh the page';
    else {
        $a = $_POST['a'] ?? '';
        if ($a === 'register') {
            [$uid, $e] = create_user(trim($_POST['name'] ?? ''), trim($_POST['email'] ?? ''), $_POST['password'] ?? '');
            if ($uid) { session_regenerate_id(true); $_SESSION['uid'] = $uid; header('Location: account.php'); exit; }
            $err = $e;
        } elseif ($a === 'login') {
            if (login_blocked('web')) { $err = 'Too many wrong attempts. Try again in 15 minutes.'; }
            else {
                $u = check_login(trim($_POST['email'] ?? ''), $_POST['password'] ?? '');
                if ($u) { login_succeeded('web'); session_regenerate_id(true); $_SESSION['uid'] = (int)$u['id']; header('Location: account.php'); exit; }
                login_failed('web');
                $err = 'Wrong email or password';
            }
        } elseif ($a === 'logout') {
            unset($_SESSION['uid']); header('Location: account.php'); exit;
        }
    }
}
$paid = $_GET['paid'] ?? '';
if ($paid === 'ok') $ok = '✅ Payment successful! Your premium is active.';
elseif ($paid === 'wait') $ok = 'Verifying payment. Premium turns on automatically once confirmed.';
elseif ($paid === 'cancel') $err = 'Payment was cancelled.';
elseif ($paid === 'err') $err = 'Payment did not complete. If money was deducted, tell support.';

$user = !empty($_SESSION['uid']) ? q('SELECT * FROM users WHERE id=?', [$_SESSION['uid']])->fetch() : null;
if ($user && $user['banned']) { unset($_SESSION['uid']); $user = null; $err = 'Account is disabled'; }

ui_head('Account - ' . site()); ?>
<div class="wrap" style="max-width:720px">
<p><a href="index.php">← Home</a></p>
<?php if ($err): ?><div class="err"><?= e($err) ?></div><?php endif; ?>
<?php if ($ok): ?><div class="ok"><?= e($ok) ?></div><?php endif; ?>

<?php if (!$user): ?>
<?php $startTab = (($_POST['a'] ?? '') === 'register') ? 'signup' : 'login'; ?>
<div class="card auth-card" style="max-width:460px;margin:20px auto">
  <div class="row auth-tabs" style="margin-bottom:14px">
    <button type="button" class="btn sm" data-tab="login">Log in</button>
    <button type="button" class="btn sm ghost" data-tab="signup">Sign up</button>
  </div>
  <form method="post" data-pane="login"><?= csrf_field() ?><input type="hidden" name="a" value="login">
    <label>Email</label><input name="email" type="email" autocomplete="email" required>
    <label>Password</label><input name="password" type="password" autocomplete="current-password" required>
    <p><button class="btn" style="width:100%">Log in</button></p>
    <p class="mut" style="font-size:12px;text-align:center">No account? <a href="#signup" data-switch="signup">Sign up</a></p>
  </form>
  <form method="post" data-pane="signup" style="display:none"><?= csrf_field() ?><input type="hidden" name="a" value="register">
    <label>Name</label><input name="name" autocomplete="name" required>
    <label>Email</label><input name="email" type="email" autocomplete="email" required>
    <label>Password (at least 6 characters)</label><input name="password" type="password" autocomplete="new-password" required>
    <p><button class="btn alt" style="width:100%">Create account</button></p>
    <p class="mut" style="font-size:12px;text-align:center">Already have an account? <a href="#login" data-switch="login">Log in</a></p>
  </form>
</div>
<script>
(function () {
  var tabs = document.querySelectorAll('[data-tab]');
  var panes = document.querySelectorAll('[data-pane]');
  function show(name) {
    panes.forEach(function (p) { p.style.display = p.dataset.pane === name ? '' : 'none'; });
    tabs.forEach(function (t) { t.classList.toggle('ghost', t.dataset.tab !== name); });
  }
  tabs.forEach(function (t) { t.addEventListener('click', function () { show(t.dataset.tab); }); });
  document.querySelectorAll('[data-switch]').forEach(function (a) {
    a.addEventListener('click', function (ev) { ev.preventDefault(); show(a.dataset.switch); });
  });
  show(location.hash === '#signup' ? 'signup' : (location.hash === '#login' ? 'login' : '<?= $startTab ?>'));
})();
</script>
</div>
<?php else:
    $plan = effective_plan($user);
    $used = used_today((int)$user['id']);
    $pay = q('SELECT p.*, pl.name pname FROM payments p JOIN plans pl ON pl.id=p.plan_id WHERE p.user_id=? ORDER BY p.id DESC LIMIT 10', [$user['id']])->fetchAll();
    $paid_plans = q('SELECT * FROM plans WHERE active=1 AND id<>1 ORDER BY price_usd, price')->fetchAll();
    $apk = basename(setting('apk_file'));
    $has = $apk !== '' && is_file(__DIR__ . '/uploads/' . $apk);
?>
<div class="card">
  <div class="row" style="justify-content:space-between"><h2>Hello, <?= e($user['name']) ?></h2>
    <form method="post"><?= csrf_field() ?><input type="hidden" name="a" value="logout"><button class="btn sm ghost">Logout</button></form></div>
  <p>Plan: <span class="tag"><?= e($plan['name']) ?></span>
  <?php if ((int)$plan['id'] !== 1 && $user['plan_expires']): ?> Expires: <?= e(date('d M Y', strtotime($user['plan_expires']))) ?><?php endif; ?></p>
  <p class="mut">Used today: <?= $used ?> / <?= (!free_mode() && (int)$plan['daily_limit'] > 0) ? (int)$plan['daily_limit'] : '∞' ?></p>
  <p class="mut">Log in to the app with this email and password. If you log in on another device, the previous device is logged out.</p>
  <?php if ($has): ?><a class="btn" href="download.php">⬇ Download app <?= e(setting('apk_version')) ?></a><?php endif; ?>
  <a class="btn ghost" href="<?= e(tg_url()) ?>" target="_blank" rel="noopener">✈ Telegram channel</a>
</div>

<?php if (free_mode()): ?>
<div class="card"><h3>Everything is free right now</h3><p class="mut">Unlimited commands for everyone. Paid plans may come later; you will be told in advance.</p></div>
<?php else: ?>
<div class="card"><h3>Go Premium</h3>
<?php if (pp_ready() || tspay_ready()): ?>
  <div class="grid">
  <?php foreach ($paid_plans as $pl): if ((float)$pl['price_usd'] <= 0) continue; ?>
    <div class="card">
      <b><?= e($pl['name']) ?></b>
      <p style="font-size:24px;margin:.2em 0">$<?= e(number_format((float)$pl['price_usd'], 2)) ?></p>
      <p class="mut"><?= e($pl['days']) ?> days · Unlimited commands<?= (int)($pl['buy_coins'] ?? 0) > 0 ? ' · +' . (int)$pl['buy_coins'] . ' bonus coins' : '' ?></p>
      <?php if (tspay_ready()): ?>
      <form method="post" action="tspay.php" style="margin:6px 0"><?= csrf_field() ?><input type="hidden" name="plan_id" value="<?= (int)$pl['id'] ?>"><button class="btn">₿ Crypto (USDT, TRX...)</button></form>
      <?php endif; ?>
      <?php if (pp_ready()): ?>
      <form method="post" action="paypal.php" style="margin:6px 0"><?= csrf_field() ?><input type="hidden" name="plan_id" value="<?= (int)$pl['id'] ?>"><button class="btn alt">PayPal / Card</button></form>
      <?php endif; ?>
    </div>
  <?php endforeach; ?>
  </div>
  <p class="mut">On the crypto payment page, send exactly the amount shown. Once the blockchain confirms it (usually a few minutes), premium turns on automatically; you do not need to keep the page open.</p>
<?php else: ?>
  <p class="mut">Online payment is coming soon.</p>
<?php endif; ?>
</div>


<?php endif; ?>
<?php if ($pay): ?>
<div class="card"><h3>Payment history</h3><div class="scroll"><table>
<tr><th>Date</th><th>Plan</th><th>Method</th><th>Amount</th><th>Status</th></tr>
<?php foreach ($pay as $p): ?>
<tr><td><?= e(date('d M, H:i', strtotime($p['created_at']))) ?></td><td><?= e($p['pname']) ?></td><td><?= e($p['method']) ?></td>
<td><?= e('$' . number_format((float)$p['amount'], 2)) ?></td><td><?= e($p['status']) ?></td></tr>
<?php endforeach; ?></table></div></div>
<?php endif; ?>
<?php endif; ?>
</div>
<?php ui_foot();
