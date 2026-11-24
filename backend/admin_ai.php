<?php
require __DIR__ . '/inc/db.php';
require __DIR__ . '/inc/ui.php';
require __DIR__ . '/inc/ai.php';
if (empty($_SESSION['admin'])) { header('Location: admin.php'); exit; }
$ok = ''; $err = ''; $list = ''; $listFor = '';
$PROVS = ['anthropic', 'openai', 'gemini', 'grok', 'openrouter', 'elevenlabs', 'meta', 'custom'];
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    if (!csrf_ok()) die('CSRF error, refresh the page');
    $a = $_POST['a'] ?? '';
    if ($a === 'plan') { q('UPDATE plans SET daily_coins=?, max_tier=? WHERE id=?', [max(0, (int)$_POST['daily_coins']), min(3, max(1, (int)$_POST['max_tier'])), (int)$_POST['id']]); $ok = 'Plan saved'; }
    elseif ($a === 'provider' && in_array($_POST['name'] ?? '', $PROVS, true)) {
        q('UPDATE providers SET enabled=? WHERE name=?', [empty($_POST['enabled']) ? 0 : 1, $_POST['name']]);
        if ($_POST['name'] === 'elevenlabs') set_setting('elevenlabs_voice_id', trim((string)($_POST['voice_id'] ?? '')));
        if ($_POST['name'] === 'anthropic') set_setting('anthropic_workspace', trim((string)($_POST['workspace_id'] ?? '')));
        if (trim($_POST['api_key'] ?? '') !== '') q('UPDATE providers SET api_key=?, fail_count=0 WHERE name=?', [trim($_POST['api_key']), $_POST['name']]);
        $ok = 'Provider saved';
    }
    elseif ($a === 'model_add') {
        if (!in_array($_POST['provider'] ?? '', $PROVS, true) || trim($_POST['model_id'] ?? '') === '') $err = 'Choose a provider and enter the model id';
        else { q('INSERT INTO models(tier,provider,model_id,label,priority) VALUES(?,?,?,?,?)', [min(3, max(1, (int)$_POST['tier'])), $_POST['provider'], trim($_POST['model_id']), trim($_POST['label'] ?? ''), (int)($_POST['priority'] ?? 10)]); $ok = 'Model added'; }
    }
    elseif ($a === 'model_save') { q('UPDATE models SET tier=?, priority=?, active=?, label=?, is_free=? WHERE id=?', [min(3, max(1, (int)$_POST['tier'])), (int)$_POST['priority'], empty($_POST['active']) ? 0 : 1, trim($_POST['label'] ?? ''), empty($_POST['is_free']) ? 0 : 1, (int)$_POST['id']]); $ok = 'Model saved'; }
    elseif ($a === 'model_del') { q('DELETE FROM models WHERE id=?', [(int)$_POST['id']]); $ok = 'Model removed'; }
    elseif ($a === 'settings') {
        foreach (['alert_chat_id', 'alert_cooldown_min', 'coin_cost_1', 'coin_cost_2', 'coin_cost_3', 'cors_origin', 'signup_bonus'] as $k) if (isset($_POST[$k])) set_setting($k, trim($_POST[$k]));
        if (trim($_POST['alert_bot_token'] ?? '') !== '') set_setting('alert_bot_token', trim($_POST['alert_bot_token']));
        $ok = 'Saved';
    }
    elseif ($a === 'test_key' && in_array($_POST['name'] ?? '', $PROVS, true)) { [$good, $msg] = ai_test_key($_POST['name']); $ok = ($good ? 'Key test OK: ' : 'Key test FAILED: ') . $_POST['name'] . ' - ' . $msg; if (!$good) $err = $ok; }
    elseif ($a === 'test_alert') { $ok = alert_send('Test alert from TS Agent admin. If you can read this, API alerts work.') ? 'Test alert sent to Telegram' : 'Could not send. Check the bot token and chat id (and press Start in the bot chat first).'; }
    elseif ($a === 'grant') {
        $u = q('SELECT id FROM users WHERE email=?', [strtolower(trim($_POST['email'] ?? ''))])->fetch();
        if (!$u) $err = 'No user with that email'; else { q('UPDATE users SET bonus_coins=GREATEST(0,bonus_coins+?) WHERE id=?', [(int)$_POST['coins'], $u['id']]); $ok = 'Bonus coins updated'; }
    }
    elseif ($a === 'import_free' && in_array($_POST['name'] ?? '', $PROVS, true)) { $n = ai_import_free($_POST['name']); $ok = $n . ' free model(s) added for ' . $_POST['name']; }
    elseif ($a === 'list' && in_array($_POST['name'] ?? '', $PROVS, true)) { $listFor = $_POST['name']; $list = ai_list_models($listFor); }
}
$provs = q('SELECT * FROM providers')->fetchAll(); $byName = []; foreach ($provs as $p) $byName[$p['name']] = $p;
$models = q('SELECT * FROM models ORDER BY tier, priority, id')->fetchAll();
$plans = q('SELECT * FROM plans ORDER BY id')->fetchAll();
$today = q('SELECT COUNT(*) n, COALESCE(SUM(coins),0) c FROM usage_log WHERE created_at>=CURDATE() AND coins>0')->fetch();
ui_head('Admin - AI and coins'); ?>
<div class="wrap">
<div class="row" style="justify-content:space-between"><h2>AI &amp; coins</h2><div class="row"><a class="btn sm ghost" href="admin.php">Back to admin</a></div></div>
<?php if ($err): ?><div class="err"><?= e($err) ?></div><?php endif; ?><?php if ($ok): ?><div class="ok"><?= e($ok) ?></div><?php endif; ?>
<div class="card">Today: <b><?= (int)$today['n'] ?></b> hosted AI requests, <b><?= (int)$today['c'] ?></b> daily coins spent.</div>

<div class="card scroll"><h3>Providers (keys stay on this server, never in the app or repo)</h3><p class="mut">Use "Test key" after saving. A failed test or a failing call sends a Telegram alert to you.</p><table>
<tr><th>Provider</th><th>On</th><th>Voice ID / Workspace ID</th><th>API key</th><th>Health</th><th></th></tr>
<?php foreach ($PROVS as $n): $p = $byName[$n] ?? ['enabled' => 0, 'base_url' => '', 'api_key' => '', 'fail_count' => 0, 'last_error' => '', 'last_error_at' => null, 'last_ok_at' => null]; ?>
<tr><form method="post"><?= csrf_field() ?><input type="hidden" name="a" value="provider"><input type="hidden" name="name" value="<?= e($n) ?>">
<td><b><?= e($n) ?></b></td><td><input type="checkbox" name="enabled" value="1" <?= $p['enabled'] ? 'checked' : '' ?> style="width:auto"></td>
<td><?php if ($n === 'elevenlabs'): ?><input name="voice_id" value="<?= e(setting('elevenlabs_voice_id')) ?>" placeholder="voice ID"><?php elseif ($n === 'anthropic'): ?><input name="workspace_id" value="<?= e(setting('anthropic_workspace')) ?>" placeholder="wrkspc_..."><?php endif; ?></td>
<td><input name="api_key" type="password" placeholder="<?= $p['api_key'] ? '(saved) type to replace' : 'paste key' ?>"></td>
<td><?php if ($p['fail_count'] > 0): ?><span class="tag" style="border-color:var(--rd);color:var(--rd)"><?= (int)$p['fail_count'] ?> fails</span> <?= e($p['last_error']) ?><?php elseif ($p['last_ok_at']): ?><span class="tag">OK</span> <?= e($p['last_ok_at']) ?><?php else: ?><span class="mut">no calls yet</span><?php endif; ?></td>
<td class="row"><button class="btn sm">Save</button></form>
<form method="post"><?= csrf_field() ?><input type="hidden" name="a" value="test_key"><input type="hidden" name="name" value="<?= e($n) ?>"><button class="btn sm alt">Test key</button></form>
<form method="post"><?= csrf_field() ?><input type="hidden" name="a" value="list"><input type="hidden" name="name" value="<?= e($n) ?>"><button class="btn sm ghost">List models</button></form></td></tr>
<?php endforeach; ?></table>
<?php if ($list !== ''): ?><h4>Models offered by <?= e($listFor) ?></h4><pre style="max-height:240px;overflow:auto"><?= e($list) ?></pre><form method="post"><?= csrf_field() ?><input type="hidden" name="a" value="import_free"><input type="hidden" name="name" value="<?= e($listFor) ?>"><button class="btn sm">Add all FREE models of <?= e($listFor) ?></button></form><?php endif; ?></div>

<div class="card scroll"><h3>Models by tier (1 lite, 2 standard, 3 pro). If one fails, the next by priority is tried.</h3><table>
<tr><th>Tier</th><th>Provider</th><th>Model id</th><th>Label</th><th>Priority</th><th>Active</th><th>Free</th><th></th></tr>
<?php foreach ($models as $m): ?>
<tr><form method="post"><?= csrf_field() ?><input type="hidden" name="a" value="model_save"><input type="hidden" name="id" value="<?= (int)$m['id'] ?>">
<td><input name="tier" type="number" min="1" max="3" value="<?= (int)$m['tier'] ?>" style="width:60px"></td><td><?= e($m['provider']) ?></td><td><code><?= e($m['model_id']) ?></code></td>
<td><input name="label" value="<?= e($m['label']) ?>"></td><td><input name="priority" type="number" value="<?= (int)$m['priority'] ?>" style="width:70px"></td>
<td><input type="checkbox" name="active" value="1" <?= $m['active'] ? 'checked' : '' ?> style="width:auto"></td>
<td><label><input type="checkbox" name="is_free" value="1" <?= ($m['is_free'] ?? 1) ? 'checked' : '' ?> style="width:auto"> free</label></td>
<td class="row"><button class="btn sm">Save</button></form><form method="post"><?= csrf_field() ?><input type="hidden" name="a" value="model_del"><input type="hidden" name="id" value="<?= (int)$m['id'] ?>"><button class="btn sm red">Remove</button></form></td></tr>
<?php endforeach; ?></table>
<form method="post" class="row" style="margin-top:10px"><?= csrf_field() ?><input type="hidden" name="a" value="model_add">
<input name="tier" type="number" min="1" max="3" value="1" style="width:70px"><select name="provider" style="width:auto"><?php foreach ($PROVS as $n): ?><option><?= e($n) ?></option><?php endforeach; ?></select>
<input name="model_id" placeholder="model id (use List models above)" style="width:260px"><input name="label" placeholder="label" style="width:140px"><input name="priority" type="number" value="10" style="width:80px"><button class="btn sm">Add model</button></form></div>

<div class="card scroll"><h3>Plans: daily coins and highest tier</h3><table>
<tr><th>#</th><th>Plan</th><th>Price $</th><th>Daily coins</th><th>Max tier</th><th></th></tr>
<?php foreach ($plans as $p): ?>
<tr><form method="post"><?= csrf_field() ?><input type="hidden" name="a" value="plan"><input type="hidden" name="id" value="<?= (int)$p['id'] ?>">
<td><?= (int)$p['id'] ?></td><td><?= e($p['name']) ?></td><td><?= e($p['price_usd']) ?></td>
<td><input name="daily_coins" type="number" min="0" value="<?= (int)$p['daily_coins'] ?>" style="width:110px"></td><td><input name="max_tier" type="number" min="1" max="3" value="<?= (int)$p['max_tier'] ?>" style="width:70px"></td>
<td><button class="btn sm">Save</button></form></td></tr>
<?php endforeach; ?></table><p class="mut">Names, prices and durations are edited in Admin &gt; Plans. Free users get the coins of plan 1 every day; unused coins do not carry over.</p></div>

<form method="post" class="card"><?= csrf_field() ?><input type="hidden" name="a" value="settings"><h3>Coin cost per request, alerts, CORS</h3>
<div class="grid">
<div><label>Signup bonus coins (given once when someone registers; 0 = none)</label><input name="signup_bonus" type="number" min="0" value="<?= e(setting('signup_bonus', '30')) ?>"></div>
<div><label>Coins per tier 1 request</label><input name="coin_cost_1" type="number" min="1" value="<?= e(setting('coin_cost_1', '1')) ?>"></div>
<div><label>Coins per tier 2 request</label><input name="coin_cost_2" type="number" min="1" value="<?= e(setting('coin_cost_2', '12')) ?>"></div>
<div><label>Coins per tier 3 request</label><input name="coin_cost_3" type="number" min="1" value="<?= e(setting('coin_cost_3', '40')) ?>"></div>
<div><label>Alert bot token (Telegram) <?= setting('alert_bot_token') !== '' ? '(saved)' : '' ?></label><input name="alert_bot_token" type="password" placeholder="type to replace"></div>
<div><label>Alert chat id (your Telegram chat)</label><input name="alert_chat_id" value="<?= e(setting('alert_chat_id')) ?>"></div>
<div><label>Alert cooldown (minutes, per problem)</label><input name="alert_cooldown_min" type="number" min="1" value="<?= e(setting('alert_cooldown_min', '30')) ?>"></div>
<div><label>Allowed web app origin (* = any, or https://you.github.io)</label><input name="cors_origin" value="<?= e(setting('cors_origin', '*')) ?>"></div>
</div><p class="row"><button class="btn">Save</button></p></form>
<form method="post" class="card"><?= csrf_field() ?><input type="hidden" name="a" value="test_alert"><button class="btn alt">Send a test alert to Telegram</button></form>

<form method="post" class="card row"><?= csrf_field() ?><input type="hidden" name="a" value="grant"><h3 style="width:100%">Give bonus coins to a user</h3>
<input name="email" type="email" placeholder="user email" style="max-width:280px"><input name="coins" type="number" value="100" style="max-width:120px"><button class="btn sm">Add (negative removes)</button></form>
</div>
<?php ui_foot();
