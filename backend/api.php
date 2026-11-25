<?php
// API for the app: register, login, me, run
require __DIR__ . '/inc/db.php';
require __DIR__ . '/inc/ai.php';
header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: ' . (setting('cors_origin', '*') ?: '*'));
header('Access-Control-Allow-Headers: Content-Type, X-Token');
header('Access-Control-Allow-Methods: POST, OPTIONS');
if (($_SERVER['REQUEST_METHOD'] ?? '') === 'OPTIONS') { http_response_code(204); exit; }
function out($d, int $code = 200): void { http_response_code($code); echo json_encode($d, JSON_UNESCAPED_UNICODE); exit; }
function body(): array { $j = json_decode(file_get_contents('php://input'), true); return is_array($j) ? $j : []; }
function token_user(): array {
    $t = $_SERVER['HTTP_X_TOKEN'] ?? '';
    if (!preg_match('/^[a-f0-9]{64}$/', $t)) out(['error' => 'Log in first'], 401);
    $u = q('SELECT * FROM users WHERE token_hash=?', [hash('sha256', $t)])->fetch();
    if (!$u) out(['error' => 'Session expired, log in again'], 401);
    if ($u['banned']) out(['error' => 'Your account is disabled'], 403);
    return $u;
}
function status(array $u): array {
    $p = effective_plan($u); $c = coin_state($u, $p);
<<<<<<< HEAD
    return ['plan' => $p['name'], 'coins_cap' => $c['cap'], 'coins_left' => $c['daily_left'], 'bonus' => $c['bonus'], 'max_tier' => (int)$p['max_tier'],
=======
    return ['plan' => $p['name'], 'plan_id' => (int)$p['id'], 'plan_expires' => $u['plan_expires'], 'coins_cap' => $c['cap'], 'coins_left' => $c['daily_left'], 'bonus' => $c['bonus'], 'max_tier' => (int)$p['max_tier'],
>>>>>>> 26712bef9165d438e019f5218df1752f7d2450eb
        'cost' => [tier_cost(1), tier_cost(2), tier_cost(3)], 'expires' => $u['plan_expires'], 'notice' => setting('notice'), 'apk_version' => setting('apk_version'),
        'limit' => 0, 'used' => 0, 'free_mode' => false];
}
if ($_SERVER['REQUEST_METHOD'] !== 'POST') out(['error' => 'Use POST'], 405);
$a = $_GET['action'] ?? '';
$b = body();

if ($a === 'register') {
    [$id, $err] = create_user(trim((string)($b['name'] ?? '')), trim((string)($b['email'] ?? '')), (string)($b['password'] ?? ''));
    if (!$id) out(['error' => $err], 422);
    out(['token' => issue_token($id)]);
}
if ($a === 'login') {
    if (login_blocked('api')) out(['error' => 'Too many wrong attempts. Try again in 15 minutes.'], 429);
    $u = check_login(trim((string)($b['email'] ?? '')), (string)($b['password'] ?? ''));
    if (!$u) { login_failed('api'); out(['error' => 'Wrong email or password'], 401); }
    login_succeeded('api');
    if ($u['banned']) out(['error' => 'Your account is disabled'], 403);
    out(['token' => issue_token((int)$u['id'])]);
}
if ($a === 'me') {
    out(status(token_user()));
}
if ($a === 'run') {
    // The app calls this before every agent task: checks the plan and daily limit (the AI key belongs to the user)
    $u = token_user();
    if (setting('maintenance') === '1') out(['error' => 'Under maintenance, try again later'], 503);
    $limit = free_mode() ? 0 : (int)effective_plan($u)['daily_limit'];
    if ($limit > 0 && used_today((int)$u['id']) >= $limit) out(['error' => 'The free command limit for today is reached. Premium gives unlimited commands.'], 429);
    q('INSERT INTO usage_log(user_id) VALUES(?)', [$u['id']]);
    out(status($u));
}
if ($a === 'chat') {
    // Hosted AI: the user spends coins, the server holds the provider keys and fails over between providers.
    $u = token_user();
    if (setting('maintenance') === '1') out(['error' => 'Maintenance in progress, try again later'], 503);
    $plan = effective_plan($u);
    $tier = max(1, min((int)($b['tier'] ?? 1), (int)$plan['max_tier']));
    $cost = tier_cost($tier); $cs = coin_state($u, $plan);
    if ($cs['total'] < $cost) out(['error' => 'No coins left for today. They refill tomorrow, or upgrade your plan.', 'code' => 'no_coins', 'coins' => ['daily_left' => $cs['daily_left'], 'cap' => $cs['cap'], 'bonus' => $cs['bonus']]], 402);
    $system = (string)($b['system'] ?? ''); $prompt = (string)($b['prompt'] ?? '');
    if ($prompt === '' || mb_strlen($system) > 12000 || mb_strlen($prompt) > 16000) out(['error' => 'Bad request'], 422);
    if ((int)q('SELECT COUNT(*) c FROM usage_log WHERE user_id=? AND created_at>=DATE_SUB(NOW(), INTERVAL 1 MINUTE)', [$u['id']])->fetch()['c'] >= 30) out(['error' => 'Too many requests, slow down'], 429);
    $models = pick_models($tier);
    if (!$models) { alert_admin('nomodels_' . $tier, "TS Agent: no active model for tier $tier. Add one in Admin > AI and coins."); out(['error' => 'AI is not set up on the server yet'], 503); }
    // Reserve the coins under a row lock BEFORE calling the provider, so parallel requests cannot all pass the check.
    $pdo = db(); $pdo->beginTransaction();
    try {
        $fresh = q('SELECT * FROM users WHERE id=? FOR UPDATE', [$u['id']])->fetch();
        $cs = coin_state($fresh, $plan);
        if ($cs['total'] < $cost) { $pdo->rollBack(); out(['error' => 'No coins left for today. They refill tomorrow, or upgrade your plan.', 'code' => 'no_coins', 'coins' => ['daily_left' => $cs['daily_left'], 'cap' => $cs['cap'], 'bonus' => $cs['bonus']]], 402); }
        $fromDaily = min($cost, $cs['daily_left']); $fromBonus = $cost - $fromDaily;
        if ($fromBonus > 0) q('UPDATE users SET bonus_coins=bonus_coins-? WHERE id=?', [$fromBonus, $u['id']]);
        q("INSERT INTO usage_log(user_id,tokens_in,tokens_out,coins,model_id) VALUES(?,0,0,?,'pending')", [$u['id'], $fromDaily]);
        $resId = (int)$pdo->lastInsertId();
        $pdo->commit();
    } catch (Throwable $e) { if ($pdo->inTransaction()) $pdo->rollBack(); throw $e; }

    foreach ($models as $m) {
        $r = ai_call(['name' => $m['pname'], 'api_key' => $m['api_key'], 'base_url' => $m['base_url']], $m['model_id'], $system, $prompt, 700);
        note_provider($m['pname'], $r);
        if (!$r['ok']) continue;
        q('UPDATE usage_log SET tokens_in=?, tokens_out=?, model_id=? WHERE id=?', [$r['tin'], $r['tout'], $m['model_id'], $resId]);
        $left = coin_state(q('SELECT * FROM users WHERE id=?', [$u['id']])->fetch(), $plan);
        out(['text' => $r['text'], 'coins' => ['daily_left' => $left['daily_left'], 'cap' => $left['cap'], 'bonus' => $left['bonus']]]);
    }
    // Every model failed: give the reserved coins back.
    q('DELETE FROM usage_log WHERE id=?', [$resId]);
    if ($fromBonus > 0) q('UPDATE users SET bonus_coins=bonus_coins+? WHERE id=?', [$fromBonus, $u['id']]);
    alert_admin('allfail_' . $tier, "TS Agent: ALL models failed for tier $tier. Users are getting errors. Check Admin > AI and coins.");
    out(['error' => 'AI is temporarily unavailable. Please try again soon.'], 502);
}
if ($a === 'config') {
    // Public and secret-free: what the app needs to know from the admin panel.
    ensure_schema();
    $aiReady = (int)q("SELECT COUNT(*) c FROM models m JOIN providers p ON p.name=m.provider WHERE m.active=1 AND m.is_free=1 AND p.enabled=1 AND p.api_key IS NOT NULL AND p.api_key<>''")->fetch()['c'] > 0;
    $el = q("SELECT api_key FROM providers WHERE name='elevenlabs' AND enabled=1")->fetch();
    $voiceReady = $el && trim((string)$el['api_key']) !== '' && trim(setting('elevenlabs_voice_id', '')) !== '';
    out(['site_name' => setting('site_name', 'TS Agent'), 'support_link' => setting('support_link', ''),
         'notice' => setting('notice', ''), 'maintenance' => setting('maintenance') === '1',
         'ai_ready' => $aiReady, 'voice_ready' => $voiceReady, 'aliases' => setting('app_aliases', ''), 'app_version' => setting('app_version', ''), 'app_url' => setting('app_download_url', '')]);
}
if ($a === 'report') {
    // The app sends crashes and errors here; they show up in Admin > Bug reports.
    ensure_schema();
    $b = body();
    $kind = mb_substr(trim((string)($b['kind'] ?? 'other')), 0, 20);
    $msg = mb_substr(trim((string)($b['message'] ?? '')), 0, 2000);
    $ctx = mb_substr(trim((string)($b['context'] ?? '')), 0, 4000);
    if ($msg !== '') q('INSERT INTO bug_reports(kind,message,context) VALUES(?,?,?)', [$kind, $msg, $ctx]);
    out(['ok' => true]);
}
if ($a === 'speak') {
    // Text to speech with the admin's ElevenLabs key and voice ID. The key never leaves the server.
    $u = token_user();
    $text = mb_substr(trim((string)(body()['text'] ?? '')), 0, 1000);
    if ($text === '') out(['error' => 'No text'], 400);
    $voice = trim(setting('elevenlabs_voice_id', ''));
    $pr = q("SELECT api_key FROM providers WHERE name='elevenlabs' AND enabled=1")->fetch();
    if (!$pr || trim((string)$pr['api_key']) === '') out(['error' => 'ElevenLabs key is not set in Admin > AI and coins'], 503);
    if ($voice === '') out(['error' => 'Voice ID is not set in Admin > Settings'], 503);
    $c = 0; $r = '';
    foreach (ai_keys((string)$pr['api_key']) as $k) {
        [$c, $r, $e] = http_req('POST', 'https://api.elevenlabs.io/v1/text-to-speech/' . rawurlencode($voice),
            ['xi-api-key: ' . $k, 'Content-Type: application/json', 'Accept: audio/mpeg'],
            json_encode(['text' => $text, 'model_id' => 'eleven_multilingual_v2']), 60);
        if ($c === 200 || !in_array($c, [401, 402, 403, 429], true)) break;
    }
    if ($c !== 200 || !is_string($r) || $r === '') out(['error' => 'Voice service error (' . $c . ')'], 502);
    header('Content-Type: audio/mpeg');
    header('Cache-Control: no-store');
    echo $r; exit;
}
if ($a === 'feed') {
    // Public: the latest posts for the app and the website. Media URLs are relative to the server unless site_url is set.
    $rows = q('SELECT id,title,body,link,media,media_type,created_at FROM posts WHERE active=1 ORDER BY id DESC LIMIT 50')->fetchAll();
    $base = rtrim(setting('site_url', ''), '/');
    out(['posts' => array_map(fn($r) => [
        'id' => (int)$r['id'], 'title' => (string)$r['title'], 'body' => (string)$r['body'], 'link' => (string)$r['link'],
        'media_type' => (string)$r['media_type'],
        'media_url' => $r['media'] ? (($base !== '' ? $base . '/' : '') . 'uploads/posts/' . $r['media']) : '',
        'created_at' => (string)$r['created_at']], $rows)]);
}
out(['error' => 'Unknown action'], 404);
