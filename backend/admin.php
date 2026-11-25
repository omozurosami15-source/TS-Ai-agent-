<?php
require __DIR__ . '/inc/db.php';
ensure_schema();
require __DIR__ . '/inc/ui.php';
// Logout is a POST with a CSRF token, so another site cannot log the admin out with a plain link.
if ($_SERVER['REQUEST_METHOD'] === 'POST' && ($_POST['a'] ?? '') === 'admin_logout' && csrf_ok()) { unset($_SESSION['admin']); header('Location: admin.php'); exit; }
$err = ''; $ok = '';

if (empty($_SESSION['admin'])) {
    if ($_SERVER['REQUEST_METHOD'] === 'POST' && csrf_ok()) {
        if (login_blocked('admin')) { $err = 'Too many wrong attempts. Try again in 15 minutes.'; }
        else {
            $a = q('SELECT * FROM admins WHERE username=?', [trim($_POST['username'] ?? '')])->fetch();
            if ($a && password_verify($_POST['password'] ?? '', $a['pass_hash'])) {
                login_succeeded('admin');
                session_regenerate_id(true); $_SESSION['admin'] = (int)$a['id']; header('Location: admin.php'); exit;
            }
            login_failed('admin');
            usleep(1000000); $err = 'Wrong username or password';
        }
    }
    ui_head('Admin'); ?>
<div class="wrap" style="max-width:420px"><div class="card"><h2>Admin login</h2>
<?php if ($err): ?><div class="err"><?= e($err) ?></div><?php endif; ?>
<form method="post"><?= csrf_field() ?>
<label>Username</label><input name="username" required><label>Password</label><input name="password" type="password" required>
<p><button class="btn">Login</button></p></form></div></div>
<?php ui_foot(); exit;
}

// ---------- actions ----------
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    if (!csrf_ok()) die('CSRF error, refresh the page');
    $a = $_POST['a'] ?? '';
    $uid = (int)($_POST['id'] ?? 0);
    if ($a === 'user_plan') {
        $pid = (int)($_POST['plan_id'] ?? 1); $days = max(0, (int)($_POST['days'] ?? 0));
        if ($pid === 1) q('UPDATE users SET plan_id=1, plan_expires=NULL WHERE id=?', [$uid]);
        else q('UPDATE users SET plan_id=?, plan_expires=? WHERE id=?', [$pid, date('Y-m-d H:i:s', time() + $days * 86400), $uid]);
        $ok = 'User plan updated';
    } elseif ($a === 'user_ban') {
        q('UPDATE users SET banned=?, token_hash=NULL WHERE id=?', [(int)($_POST['banned'] ?? 0), $uid]); $ok = 'Updated';
    } elseif ($a === 'user_logout') {
        q('UPDATE users SET token_hash=NULL WHERE id=?', [$uid]); $ok = 'Device logged out';
    } elseif ($a === 'pay_ok') {
        $ok = activate_payment($uid) ? 'Payment approved, premium activated' : 'Could not approve (it may already be approved)';
    } elseif ($a === 'pay_no') {
        q("UPDATE payments SET status='rejected' WHERE id=? AND status IN ('pending','failed')", [$uid]); $ok = 'Rejected';
    } elseif ($a === 'plan_save' || $a === 'plan_add') {
        $f = [trim($_POST['name'] ?? ''), (int)ceil(max(0, (float)($_POST['price_usd'] ?? 0))), max(0, (float)($_POST['price_usd'] ?? 0)),
              max(0, (int)($_POST['days'] ?? 0)), max(0, (int)($_POST['daily_limit'] ?? 0)), empty($_POST['active']) ? 0 : 1,
              max(0, (int)($_POST['buy_coins'] ?? 0))];
        if ($f[0] === '') $err = 'Enter a plan name';
        elseif ($a === 'plan_add') { q('INSERT INTO plans(name,price,price_usd,days,daily_limit,active,buy_coins) VALUES(?,?,?,?,?,?,?)', $f); $ok = 'Plan added'; }
        else { if ($uid === 1) $f[5] = 1; q('UPDATE plans SET name=?,price=?,price_usd=?,days=?,daily_limit=?,active=?,buy_coins=? WHERE id=?', array_merge($f, [$uid])); $ok = 'Plan saved'; }
    } elseif ($a === 'post_add') {
        $title = mb_substr(trim($_POST['title'] ?? ''), 0, 160);
        $body = mb_substr(trim($_POST['body'] ?? ''), 0, 4000);
        $link = trim($_POST['link'] ?? '');
        if ($link !== '' && !preg_match('#^https://#i', $link)) { $err = 'The link must start with https://'; $link = ''; }
        $media = null; $mtype = null; $f = $_FILES['media'] ?? null;
        if ($f && $f['error'] === UPLOAD_ERR_OK) {
            $head = (string)file_get_contents($f['tmp_name'], false, null, 0, 16);
            $ext = null;
            if (substr($head, 0, 3) === "\xFF\xD8\xFF") { $ext = 'jpg'; $mtype = 'image'; }
            elseif (substr($head, 0, 8) === "\x89PNG\r\n\x1a\n") { $ext = 'png'; $mtype = 'image'; }
            elseif (substr($head, 0, 4) === 'RIFF' && substr($head, 8, 4) === 'WEBP') { $ext = 'webp'; $mtype = 'image'; }
            elseif (substr($head, 4, 4) === 'ftyp') { $ext = 'mp4'; $mtype = 'video'; }
            elseif (substr($head, 0, 4) === "\x1A\x45\xDF\xA3") { $ext = 'webm'; $mtype = 'video'; }
            if (!$ext) $err = 'Only JPG, PNG, WEBP, MP4 or WEBM files are allowed';
            else {
                $dir = __DIR__ . '/uploads/posts/';
                if (!is_dir($dir)) @mkdir($dir, 0755, true);
                $name = bin2hex(random_bytes(8)) . '.' . $ext;
                if (move_uploaded_file($f['tmp_name'], $dir . $name)) $media = $name;
                else { $err = 'Could not save the file, check the uploads folder permissions'; $mtype = null; }
            }
        } elseif ($f && $f['error'] !== UPLOAD_ERR_NO_FILE) $err = 'Upload failed (code ' . $f['error'] . '). The file may be larger than your hosting limit.';
        if (!$err) {
            if ($title === '' && $body === '' && !$media) $err = 'Write a title or text, or add a photo or video';
            else { q('INSERT INTO posts(title,body,link,media,media_type) VALUES(?,?,?,?,?)', [$title, $body, $link, $media, $mtype]); $ok = 'Post published'; }
        }
    } elseif ($a === 'post_toggle') {
        q('UPDATE posts SET active=1-active WHERE id=?', [(int)($_POST['id'] ?? 0)]); $ok = 'Post updated';
    } elseif ($a === 'post_del') {
        $pid = (int)($_POST['id'] ?? 0);
        $old = q('SELECT media FROM posts WHERE id=?', [$pid])->fetch();
        if ($old && $old['media']) { $fp = __DIR__ . '/uploads/posts/' . basename($old['media']); if (is_file($fp)) @unlink($fp); }
        q('DELETE FROM posts WHERE id=?', [$pid]); $ok = 'Post deleted';
    } elseif ($a === 'bug_clear') {
        q('DELETE FROM bug_reports'); $ok = 'Reports cleared';
    } elseif ($a === 'settings') {
        foreach (['site_name', 'site_url', 'notice', 'maintenance', 'support_link', 'paypal_mode', 'tspay_base_url', 'free_mode', 'elevenlabs_voice_id', 'app_aliases', 'anthropic_workspace', 'app_version', 'app_download_url'] as $k) {
            if (isset($_POST[$k])) set_setting($k, trim((string)$_POST[$k]));
        }
        foreach (['paypal_client_id', 'paypal_secret', 'tspay_crypto_key', 'tspay_main_key'] as $k) { if (trim((string)($_POST[$k] ?? '')) !== '') set_setting($k, trim($_POST[$k])); }
        $tb = rtrim(setting('tspay_base_url'), '/');
        if ($tb !== '' && !preg_match('#^https://[A-Za-z0-9.-]+(:\d+)?(/[A-Za-z0-9._~/-]*)?$#', $tb)) { set_setting('tspay_base_url', ''); $err = 'Gateway URL must start with https://'; }
        else set_setting('tspay_base_url', $tb);
        if ($err === '') $ok = 'Settings saved';
    } elseif ($a === 'pass') {
        $np = $_POST['newpass'] ?? '';
        $adm = q('SELECT * FROM admins WHERE id=?', [$_SESSION['admin']])->fetch();
        if (!password_verify($_POST['oldpass'] ?? '', $adm['pass_hash'])) $err = 'Old password is wrong';
        elseif (strlen($np) < 8) $err = 'New password must be at least 8 characters';
        else { q('UPDATE admins SET pass_hash=? WHERE id=?', [password_hash($np, PASSWORD_DEFAULT), $adm['id']]); $ok = 'Password changed'; }
    } elseif ($a === 'apk') {
        $ver = preg_replace('/[^0-9A-Za-z._-]/', '', trim($_POST['version'] ?? ''));
        $manual = basename(trim($_POST['manual'] ?? ''));
        $dir = __DIR__ . '/uploads/';
        if ($ver === '') $err = 'Enter a version (for example 1.0)';
        elseif (!empty($_FILES['apk']['name'])) {
            $f = $_FILES['apk'];
            if ($f['error'] !== UPLOAD_ERR_OK) $err = 'Upload failed (code ' . $f['error'] . '), the hosting upload limit may be too small. Use the manual method below.';
            elseif (strtolower(pathinfo($f['name'], PATHINFO_EXTENSION)) !== 'apk' || file_get_contents($f['tmp_name'], false, null, 0, 2) !== 'PK') $err = 'This is not a valid .apk file';
            else {
                $name = 'ts-agent-' . $ver . '-' . bin2hex(random_bytes(4)) . '.apk';
                if (move_uploaded_file($f['tmp_name'], $dir . $name)) {
                    $old = basename(setting('apk_file')); if ($old && $old !== $name && is_file($dir . $old)) @unlink($dir . $old);
                    set_setting('apk_file', $name); set_setting('apk_version', $ver); $ok = 'APK uploaded';
                } else $err = 'Could not save the file, check the uploads folder permissions';
            }
        } elseif ($manual !== '' && preg_match('/\.apk$/i', $manual) && is_file($dir . $manual)) {
            set_setting('apk_file', $manual); set_setting('apk_version', $ver); $ok = 'Manual APK set';
        } else $err = 'Choose a file, or enter the name of a file in the uploads folder';
    }
}

$tab = $_GET['tab'] ?? 'dash';
$plans = q('SELECT * FROM plans ORDER BY id')->fetchAll();
$pending = (int)q("SELECT COUNT(*) c FROM payments WHERE status='pending'")->fetch()['c'];
ui_head('Admin - ' . site()); ?>
<div class="wrap">
<div class="row" style="justify-content:space-between">
  <h2>🛠 <?= e(site()) ?> Admin</h2>
  <div class="row">
  <?php foreach (['dash' => 'Dashboard', 'users' => 'Users', 'pay' => 'Payments' . ($pending ? " ($pending)" : ''), 'plans' => 'Plan', 'bugs' => 'Bug reports', 'posts' => 'Posts', 'apk' => 'App (APK)', 'set' => 'Settings'] as $k => $l): ?>
    <a class="btn sm <?= $tab === $k ? '' : 'ghost' ?>" href="?tab=<?= $k ?>"><?= e($l) ?></a>
  <?php endforeach; ?>
    <a class="btn sm alt" href="admin_ai.php">AI &amp; coins</a>
    <form method="post" style="display:inline"><?= csrf_field() ?><input type="hidden" name="a" value="admin_logout"><button class="btn sm red">Logout</button></form>
  </div>
</div>
<?php if ($err): ?><div class="err"><?= e($err) ?></div><?php endif; ?>
<?php if ($ok): ?><div class="ok"><?= e($ok) ?></div><?php endif; ?>

<?php if ($tab === 'dash'):
    $n = fn($sql) => (int)q($sql)->fetch()['c'];
    $usd = (float)q("SELECT COALESCE(SUM(amount),0) s FROM payments WHERE status='approved' AND currency='USD'")->fetch()['s'];
    $cards = [
        'Total users' => $n('SELECT COUNT(*) c FROM users'),
        'New today' => $n('SELECT COUNT(*) c FROM users WHERE created_at>=CURDATE()'),
        'Active premium' => $n('SELECT COUNT(*) c FROM users WHERE plan_id<>1 AND plan_expires>NOW()'),
        'Pending payments' => $pending,
        'Revenue (USD)' => '$' . number_format($usd, 2),
        'Downloads' => (int)setting('downloads', '0'),
        'Agent runs today' => $n('SELECT COUNT(*) c FROM usage_log WHERE created_at>=CURDATE()'),
    ]; ?>
<div class="grid"><?php foreach ($cards as $l => $v): ?><div class="card"><div class="mut"><?= e($l) ?></div><div style="font-size:28px;font-weight:700"><?= e($v) ?></div></div><?php endforeach; ?></div>

<?php elseif ($tab === 'users'):
    $s = trim($_GET['q'] ?? '');
    $sql = 'SELECT u.*, p.name pname, (SELECT COUNT(*) FROM usage_log l WHERE l.user_id=u.id AND l.created_at>=CURDATE()) used FROM users u JOIN plans p ON p.id=u.plan_id';
    $rows = $s !== '' ? q($sql . ' WHERE u.email LIKE ? OR u.name LIKE ? ORDER BY u.id DESC LIMIT 100', ["%$s%", "%$s%"])->fetchAll() : q($sql . ' ORDER BY u.id DESC LIMIT 100')->fetchAll(); ?>
<form class="row"><input type="hidden" name="tab" value="users"><input name="q" value="<?= e($s) ?>" placeholder="Name or email" style="max-width:320px"><button class="btn sm">Search</button></form>
<div class="card scroll"><table>
<tr><th>#</th><th>User</th><th>Plan</th><th>Today</th><th>Change plan</th><th>Action</th></tr>
<?php foreach ($rows as $u): ?>
<tr><td><?= (int)$u['id'] ?></td>
<td><?= e($u['name']) ?><br><span class="mut"><?= e($u['email']) ?></span><?= $u['banned'] ? ' <span class="tag">Ban</span>' : '' ?></td>
<td><?= e($u['pname']) ?><br><span class="mut"><?= $u['plan_expires'] ? e(date('d M Y', strtotime($u['plan_expires']))) : '-' ?></span></td>
<td><?= (int)$u['used'] ?></td>
<td><form method="post" class="row"><?= csrf_field() ?><input type="hidden" name="a" value="user_plan"><input type="hidden" name="id" value="<?= (int)$u['id'] ?>">
  <select name="plan_id" style="width:auto"><?php foreach ($plans as $p): ?><option value="<?= (int)$p['id'] ?>" <?= $p['id'] == $u['plan_id'] ? 'selected' : '' ?>><?= e($p['name']) ?></option><?php endforeach; ?></select>
  <input name="days" type="number" min="0" placeholder="Days" style="width:80px"><button class="btn sm">Set</button></form></td>
<td class="row">
  <form method="post"><?= csrf_field() ?><input type="hidden" name="a" value="user_ban"><input type="hidden" name="id" value="<?= (int)$u['id'] ?>"><input type="hidden" name="banned" value="<?= $u['banned'] ? 0 : 1 ?>"><button class="btn sm <?= $u['banned'] ? '' : 'red' ?>"><?= $u['banned'] ? 'Unban' : 'Ban' ?></button></form>
  <form method="post"><?= csrf_field() ?><input type="hidden" name="a" value="user_logout"><input type="hidden" name="id" value="<?= (int)$u['id'] ?>"><button class="btn sm ghost">Logout</button></form></td></tr>
<?php endforeach; ?></table></div>

<?php elseif ($tab === 'pay'):
    $st = in_array($_GET['s'] ?? 'pending', ['pending', 'approved', 'rejected', 'failed', 'all'], true) ? ($_GET['s'] ?? 'pending') : 'pending';
    $sql = 'SELECT p.*, u.email, pl.name pname FROM payments p JOIN users u ON u.id=p.user_id JOIN plans pl ON pl.id=p.plan_id';
    $rows = $st === 'all' ? q($sql . ' ORDER BY p.id DESC LIMIT 100')->fetchAll() : q($sql . ' WHERE p.status=? ORDER BY p.id DESC LIMIT 100', [$st])->fetchAll(); ?>
<div class="row"><?php foreach (['pending' => 'Pending', 'approved' => 'Approved', 'failed' => 'Failed', 'rejected' => 'Rejected', 'all' => 'All'] as $k => $l): ?><a class="btn sm <?= $st === $k ? '' : 'ghost' ?>" href="?tab=pay&s=<?= $k ?>"><?= e($l) ?></a><?php endforeach; ?></div>
<div class="card scroll"><table>
<tr><th>Time</th><th>User</th><th>Plan</th><th>Method</th><th>Amount</th><th>TrxID</th><th>Status</th><th></th></tr>
<?php foreach ($rows as $p): ?>
<tr><td><?= e(date('d M H:i', strtotime($p['created_at']))) ?></td><td><?= e($p['email']) ?></td><td><?= e($p['pname']) ?></td><td><?= e($p['method']) ?></td>
<td><?= '$' . number_format((float)$p['amount'], 2) ?></td><td><code><?= e($p['trx_id']) ?></code></td><td><?= e($p['status']) ?></td>
<td class="row"><?php if (in_array($p['status'], ['pending', 'failed'], true)): ?>
  <form method="post"><?= csrf_field() ?><input type="hidden" name="a" value="pay_ok"><input type="hidden" name="id" value="<?= (int)$p['id'] ?>"><button class="btn sm">Approve</button></form>
  <form method="post"><?= csrf_field() ?><input type="hidden" name="a" value="pay_no"><input type="hidden" name="id" value="<?= (int)$p['id'] ?>"><button class="btn sm red">Reject</button></form>
<?php endif; ?></td></tr>
<?php endforeach; ?></table></div>
<p class="mut">PayPal payments are approved automatically. Only stuck payments need to be approved here by hand. "Failed" payments: approve only after you confirm in your PayPal dashboard that the money really arrived.</p>

<?php elseif ($tab === 'plans'): ?>
<div class="card scroll"><table>
<tr><th>#</th><th>Name</th><th>Price (USD)</th><th>Days</th><th>Daily limit (0 = unlimited)</th><th>Coins on purchase</th><th>Active</th><th></th></tr>
<?php foreach ($plans as $p): ?>
<form method="post"><?= csrf_field() ?><input type="hidden" name="a" value="plan_save"><input type="hidden" name="id" value="<?= (int)$p['id'] ?>">
<tr><td><?= (int)$p['id'] ?></td><td><input name="name" value="<?= e($p['name']) ?>"></td>
<td><input name="price_usd" type="number" step="0.01" value="<?= e($p['price_usd']) ?>" style="width:90px"></td><td><input name="days" type="number" value="<?= (int)$p['days'] ?>" style="width:80px"></td>
<td><input name="daily_limit" type="number" value="<?= (int)$p['daily_limit'] ?>" style="width:90px"></td>
<td><input name="buy_coins" type="number" min="0" value="<?= (int)($p['buy_coins'] ?? 0) ?>" style="width:90px"></td>
<td><input type="checkbox" name="active" value="1" <?= $p['active'] ? 'checked' : '' ?> style="width:auto"></td><td><button class="btn sm">Save</button></td></tr></form>
<?php endforeach; ?></table></div>
<div class="card"><h3>New plan</h3><form method="post" class="row"><?= csrf_field() ?><input type="hidden" name="a" value="plan_add"><input type="hidden" name="active" value="1">
<input name="name" placeholder="Name" style="width:180px" required><input name="price_usd" type="number" step="0.01" placeholder="$" style="width:90px">
<input name="days" type="number" placeholder="Days" style="width:90px"><input name="daily_limit" type="number" placeholder="Limit" style="width:90px"><input name="buy_coins" type="number" min="0" placeholder="Coins on purchase" style="width:150px"><button class="btn sm">Add</button></form>
<p class="mut">The Free plan (#1) cannot be disabled. Only plans with a $ price can be bought on PayPal.</p></div>

<?php elseif ($tab === 'bugs'):
  $bugs = q('SELECT * FROM bug_reports ORDER BY id DESC LIMIT 100')->fetchAll(); ?>
<div class="card"><h3>Bug reports from the app</h3>
<form method="post"><?= csrf_field() ?><input type="hidden" name="a" value="bug_clear"><button class="btn sm red">Clear all</button></form>
<?php foreach ($bugs as $b): ?>
<div style="border-bottom:1px solid #123;padding:8px 0"><b><?= e($b['kind']) ?></b> &middot; <?= e($b['created_at']) ?>
<pre style="white-space:pre-wrap;font-size:12px"><?= e($b['message']) ?></pre>
<?php if ($b['context'] !== ''): ?><details><summary>details</summary><pre style="white-space:pre-wrap;font-size:11px"><?= e($b['context']) ?></pre></details><?php endif; ?></div>
<?php endforeach; if (!$bugs): ?><p class="mut">No reports yet.</p><?php endif; ?></div>

<?php elseif ($tab === 'posts'):
  $posts = q('SELECT * FROM posts ORDER BY id DESC LIMIT 100')->fetchAll(); ?>
<div class="card"><h3>New post</h3>
<form method="post" enctype="multipart/form-data"><?= csrf_field() ?><input type="hidden" name="a" value="post_add">
<label>Title</label><input name="title" maxlength="160" placeholder="Short title">
<label>Text</label><textarea name="body" rows="4" maxlength="4000" placeholder="What is new?"></textarea>
<label>Link (https, optional)</label><input name="link" placeholder="https://...">
<label>Photo or video (optional: JPG, PNG, WEBP, MP4, WEBM)</label><input type="file" name="media" accept="image/*,video/mp4,video/webm">
<p class="mut">Large videos need a higher upload limit in your hosting php.ini. Short clips work best. Only publish content you own or have permission to share.</p>
<button class="btn">Publish</button></form></div>
<div class="card scroll"><h3>Posts (newest first)</h3><table>
<tr><th>#</th><th>Title</th><th>Type</th><th>Date</th><th>Shown</th><th></th></tr>
<?php foreach ($posts as $p): ?>
<tr><td><?= (int)$p['id'] ?></td><td><?= e($p['title'] !== '' ? $p['title'] : mb_substr((string)$p['body'], 0, 40)) ?></td>
<td><?= e($p['media_type'] ?: 'text') ?></td><td><?= e($p['created_at']) ?></td>
<td><?= $p['active'] ? 'yes' : 'hidden' ?></td>
<td class="row"><form method="post"><?= csrf_field() ?><input type="hidden" name="a" value="post_toggle"><input type="hidden" name="id" value="<?= (int)$p['id'] ?>"><button class="btn sm ghost"><?= $p['active'] ? 'Hide' : 'Show' ?></button></form>
<form method="post" onsubmit="return confirm('Delete this post?')"><?= csrf_field() ?><input type="hidden" name="a" value="post_del"><input type="hidden" name="id" value="<?= (int)$p['id'] ?>"><button class="btn sm red">Delete</button></form></td></tr>
<?php endforeach; ?></table></div>

<?php elseif ($tab === 'apk'):
    $cur = basename(setting('apk_file')); $dir = __DIR__ . '/uploads/'; ?>
<div class="card"><h3>Current app</h3>
<p><?= $cur && is_file($dir . $cur) ? 'Version <b>' . e(setting('apk_version')) . '</b> · ' . e($cur) . ' · ' . round(filesize($dir . $cur) / 1048576, 1) . ' MB' : 'No APK yet' ?></p>
<p class="mut">Total downloads: <?= (int)setting('downloads', '0') ?></p></div>
<div class="card"><h3>Upload new APK</h3>
<form method="post" enctype="multipart/form-data"><?= csrf_field() ?><input type="hidden" name="a" value="apk">
<label>Version</label><input name="version" placeholder="1.0" required>
<label>APK file</label><input type="file" name="apk" accept=".apk">
<label>Or manual: the name of a file you uploaded to the uploads folder via FTP/file manager</label><input name="manual" placeholder="app-debug.apk">
<p><button class="btn">Save</button></p></form>
<p class="mut">The upload limit is set in the php.ini of your hosting. If it fails, put the file in the uploads folder with the file manager and enter its name in the manual field.</p></div>

<?php else: ?>
<form method="post" class="card"><?= csrf_field() ?><input type="hidden" name="a" value="settings"><h3>Site and payments</h3>
<div class="grid">
<div><label>Site name</label><input name="site_name" value="<?= e(setting('site_name')) ?>"></div>
<div><label>Site URL (https://yourdomain.com)</label><input name="site_url" value="<?= e(setting('site_url')) ?>"></div>
<div><label>Telegram channel link</label><input name="support_link" value="<?= e(tg_url()) ?>"></div>
<div><label>Free mode (everyone unlimited, payments hidden)</label><select name="free_mode"><option value="1">On: free for everyone</option><option value="0" <?= free_mode() ? '' : 'selected' ?>>Off: paid plans active</option></select></div>
<div><label>Maintenance mode</label><select name="maintenance"><option value="0">Off</option><option value="1" <?= setting('maintenance') === '1' ? 'selected' : '' ?>>On</option></select></div>
<div><label>PayPal mode</label><select name="paypal_mode"><option value="sandbox">sandbox (testing)</option><option value="live" <?= setting('paypal_mode') === 'live' ? 'selected' : '' ?>>live</option></select></div>
<div><label>PayPal Client ID <?= setting('paypal_client_id') !== '' ? '(saved)' : '' ?></label><input name="paypal_client_id" placeholder="Enter a new one to change"></div>
<div><label>PayPal Secret <?= setting('paypal_secret') !== '' ? '(saved)' : '' ?></label><input name="paypal_secret" type="password" placeholder="Enter a new one to change"></div>
<div><label>Own gateway URL (https://yourgateway.com)</label><input name="tspay_base_url" value="<?= e(setting('tspay_base_url')) ?>"></div>
<div><label>ElevenLabs voice ID (the voice the app speaks with; key is set under AI providers)</label><input name="elevenlabs_voice_id" value="<?= e(setting('elevenlabs_voice_id')) ?>" placeholder="e.g. 21m00Tcm4TlvDq8ikWAM"></div>
<div><label>App names: spoken=app (one per line, any language). Example: tsbf=ts vibe, ইউটিউব=youtube</label><textarea name="app_aliases" rows="5" style="width:100%"><?= e(setting('app_aliases')) ?></textarea></div>
<div><label>Anthropic workspace ID (only if Claude says the key is not scoped to a workspace; starts with wrkspc_)</label><input name="anthropic_workspace" value="<?= e(setting('anthropic_workspace')) ?>" placeholder="wrkspc_..."></div>
<div><label>App version number (bigger than the app's own number = update notice)</label><input name="app_version" value="<?= e(setting('app_version')) ?>" placeholder="e.g. 21"></div>
<div><label>App download link (APK or page)</label><input name="app_download_url" value="<?= e(setting('app_download_url')) ?>" placeholder="https://..."></div>
<div><label>Gateway Crypto API key <?= setting('tspay_crypto_key') !== '' ? '(saved)' : '' ?></label><input name="tspay_crypto_key" type="password" placeholder="Enter a new one to change"></div>
<div><label>Gateway main API key (for verification and webhooks) <?= setting('tspay_main_key') !== '' ? '(saved)' : '' ?></label><input name="tspay_main_key" type="password" placeholder="Enter a new one to change"></div>
</div>
<label>Notice for everyone (shown in the app and on the site)</label><textarea name="notice" rows="3"><?= e(setting('notice')) ?></textarea>
<p><button class="btn">Save</button></p></form>
<form method="post" class="card"><?= csrf_field() ?><input type="hidden" name="a" value="pass"><h3>Change admin password</h3>
<div class="row"><input name="oldpass" type="password" placeholder="Old" style="max-width:240px"><input name="newpass" type="password" placeholder="New (8+)" style="max-width:240px"><button class="btn sm">Change</button></div></form>
<?php endif; ?>
</div>
<?php ui_foot();
