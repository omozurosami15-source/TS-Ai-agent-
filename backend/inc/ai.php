<?php
// TS Cloud: server-held AI keys, coins, provider failover and admin alerts
function prov(string $name): ?array { $r = q('SELECT * FROM providers WHERE name=?', [$name])->fetch(); return $r ?: null; }

function http_req(string $method, string $url, array $headers, ?string $body = null, int $timeout = 60): array {
    $ch = curl_init($url);
    $o = [CURLOPT_CUSTOMREQUEST => $method, CURLOPT_RETURNTRANSFER => 1, CURLOPT_TIMEOUT => $timeout, CURLOPT_CONNECTTIMEOUT => 10, CURLOPT_HTTPHEADER => $headers];
    if ($body !== null) $o[CURLOPT_POSTFIELDS] = $body;
    curl_setopt_array($ch, $o);
    $res = curl_exec($ch); $code = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE); $err = curl_error($ch); curl_close($ch);
    return [$code, (string)$res, $err];
}

/** One request to one provider. Returns ['ok'=>bool, 'text'=>..., 'tin'=>, 'tout'=>] or ['ok'=>false,'code'=>,'err'=>]. */
/** Anthropic headers. Keys that belong to a workspace need the workspace ID (set in Admin > Settings). */
function anthropic_headers(string $key, array $extra = []): array {
    $h = array_merge($extra, ['x-api-key: ' . $key, 'anthropic-version: 2023-06-01']);
    $ws = trim(setting('anthropic_workspace', ''));
    if ($ws !== '') $h[] = 'anthropic-workspace-id: ' . $ws;
    return $h;
}
/** Several keys for one provider, separated by commas or new lines. Used one by one: the next key is tried only when a key has run out of quota or is rejected. */
function ai_keys(string $raw): array {
    $keys = array_values(array_filter(array_map('trim', preg_split('/[\r\n,]+/', $raw)), fn($k) => $k !== ''));
    return $keys ?: [''];
}
function ai_call(array $p, string $model, string $system, string $prompt, int $maxTokens): array {
    $last = ['ok' => false, 'code' => 0, 'err' => 'no key'];
    foreach (ai_keys((string)$p['api_key']) as $k) {
        $p2 = $p; $p2['api_key'] = $k;
        $r = ai_call_one($p2, $model, $system, $prompt, $maxTokens);
        if (!empty($r['ok'])) return $r;
        $last = $r;
        $code = (int)($r['code'] ?? 0);
        $msg = strtolower((string)($r['err'] ?? ''));
        // only "out of quota", "rate limited" or "key rejected" moves on to the next key
        if (!in_array($code, [401, 402, 403, 429], true) && !preg_match('/quota|rate limit|exhaust|insufficient|billing|credit/', $msg)) break;
    }
    return $last;
}
function ai_call_one(array $p, string $model, string $system, string $prompt, int $maxTokens): array {
    $key = (string)$p['api_key']; $name = $p['name'];
    if ($name === 'anthropic') {
        [$c, $r, $e] = http_req('POST', 'https://api.anthropic.com/v1/messages', anthropic_headers($key, ['content-type: application/json']),
            json_encode(['model' => $model, 'max_tokens' => $maxTokens, 'system' => $system, 'messages' => [['role' => 'user', 'content' => $prompt]]], JSON_UNESCAPED_UNICODE));
        $j = json_decode($r, true);
        if ($c === 200 && isset($j['content'])) { $t = ''; foreach ($j['content'] as $b) if (($b['type'] ?? '') === 'text') $t .= $b['text']; return ['ok' => true, 'text' => $t, 'tin' => (int)($j['usage']['input_tokens'] ?? 0), 'tout' => (int)($j['usage']['output_tokens'] ?? 0)]; }
        return ['ok' => false, 'code' => $c, 'err' => $e ?: substr($r, 0, 200)];
    }
    if ($name === 'gemini') {
        [$c, $r, $e] = http_req('POST', 'https://generativelanguage.googleapis.com/v1beta/models/' . rawurlencode(preg_replace('#^models/#', '', $model)) . ':generateContent', ['Content-Type: application/json', 'x-goog-api-key: ' . $key],
            json_encode(['systemInstruction' => ['parts' => [['text' => $system]]], 'contents' => [['role' => 'user', 'parts' => [['text' => $prompt]]]], 'generationConfig' => ['maxOutputTokens' => $maxTokens]], JSON_UNESCAPED_UNICODE));
        $j = json_decode($r, true);
        if ($c === 200 && isset($j['candidates'][0]['content']['parts'])) { $t = ''; foreach ($j['candidates'][0]['content']['parts'] as $b) $t .= $b['text'] ?? ''; return ['ok' => true, 'text' => $t, 'tin' => (int)($j['usageMetadata']['promptTokenCount'] ?? 0), 'tout' => (int)($j['usageMetadata']['candidatesTokenCount'] ?? 0)]; }
        return ['ok' => false, 'code' => $c, 'err' => $e ?: substr($r, 0, 200)];
    }
    $base = ai_openai_base($name, $p['base_url']);
    if (strpos($base, 'https://') !== 0) return ['ok' => false, 'code' => 0, 'err' => 'bad base url'];
    $body = ['model' => $model, 'messages' => [['role' => 'system', 'content' => $system], ['role' => 'user', 'content' => $prompt]]];
    $body[$name === 'openai' ? 'max_completion_tokens' : 'max_tokens'] = $maxTokens;
    [$c, $r, $e] = http_req('POST', $base . '/chat/completions', ['Content-Type: application/json', 'Authorization: Bearer ' . $key], json_encode($body, JSON_UNESCAPED_UNICODE));
    $j = json_decode($r, true);
    if ($c === 200 && isset($j['choices'][0]['message']['content'])) {
        $m = $j['choices'][0]['message']['content']; $t = is_array($m) ? implode('', array_map(fn($x) => $x['text'] ?? '', $m)) : (string)$m;
        return ['ok' => true, 'text' => $t, 'tin' => (int)($j['usage']['prompt_tokens'] ?? 0), 'tout' => (int)($j['usage']['completion_tokens'] ?? 0)];
    }
    return ['ok' => false, 'code' => $c, 'err' => $e ?: substr($r, 0, 200)];
}

/** Provider names that use the OpenAI-compatible chat format. */
function ai_openai_base(string $name, ?string $custom): string {
    return match ($name) {
        'openai' => 'https://api.openai.com/v1',
        'grok' => 'https://api.x.ai/v1',
        'openrouter' => 'https://openrouter.ai/api/v1',
        default => rtrim((string)$custom, '/'),
    };
}

/** Sends one tiny request to check that a saved key works. Returns [ok, message]. */
function ai_test_key(string $name): array {
    $p = prov($name);
    if (!$p || (string)$p['api_key'] === '') return [false, 'No key saved'];
    if ($name === 'anthropic') [$c, $r, $e] = http_req('GET', 'https://api.anthropic.com/v1/models?limit=1', anthropic_headers($p['api_key']), null, 20);
    elseif ($name === 'gemini') [$c, $r, $e] = http_req('GET', 'https://generativelanguage.googleapis.com/v1beta/models?pageSize=1', ['x-goog-api-key: ' . $p['api_key']], null, 20);
    elseif ($name === 'elevenlabs') [$c, $r, $e] = http_req('GET', 'https://api.elevenlabs.io/v1/user', ['xi-api-key: ' . $p['api_key']], null, 20);
    elseif ($name === 'meta') [$c, $r, $e] = http_req('GET', 'https://graph.facebook.com/v19.0/me?fields=id,name', ['Authorization: Bearer ' . $p['api_key']], null, 20);
    else [$c, $r, $e] = http_req('GET', ai_openai_base($name, $p['base_url']) . '/models', ['Authorization: Bearer ' . $p['api_key']], null, 20);
    if ($c === 200) { q('UPDATE providers SET fail_count=0, last_ok_at=NOW() WHERE name=?', [$name]); return [true, 'Key works']; }
    $msg = 'HTTP ' . $c . ' ' . preg_replace('/\s+/', ' ', substr($e ?: $r, 0, 160));
    note_provider($name, ['ok' => false, 'code' => $c, 'err' => $e ?: $r]);
    return [false, $msg];
}

/** Free models are detected from the provider's own data where it exists, otherwise by a known free-tier rule. */
function ai_is_free(string $name, string $id, array $m): bool {
    if (isset($m['pricing']) && is_array($m['pricing'])) {
        return floatval($m['pricing']['prompt'] ?? 1) == 0.0 && floatval($m['pricing']['completion'] ?? 1) == 0.0;
    }
    if (str_ends_with($id, ':free')) return true;
    if ($name === 'gemini') {
        return str_contains($id, 'flash') && !preg_match('/pro|image|audio|live|tts|embedding|computer|deep-research|exp/', $id);
    }
    return false;
}
/** Tier from the name: lite = 1, pro = 3, everything else = 2. */
function ai_tier_for(string $id): int {
    if (str_contains($id, 'lite')) return 1;
    if (str_contains($id, 'pro')) return 3;
    return 2;
}
/** Returns [id, free] rows, or an error string. */
function ai_model_rows(string $name): array|string {
    $p = prov($name);
    if (!$p || (string)$p['api_key'] === '') return 'Save the key for ' . $name . ' first.';
    if ($name === 'anthropic') [$c, $r] = http_req('GET', 'https://api.anthropic.com/v1/models?limit=100', anthropic_headers($p['api_key']));
    elseif ($name === 'gemini') [$c, $r] = http_req('GET', 'https://generativelanguage.googleapis.com/v1beta/models?pageSize=200', ['x-goog-api-key: ' . $p['api_key']]);
    else {
        $base = ai_openai_base($name, $p['base_url']);
        [$c, $r] = http_req('GET', $base . '/models', ['Authorization: Bearer ' . $p['api_key']]);
    }
    if ($c !== 200) return 'Error ' . $c . ': ' . substr((string)$r, 0, 160);
    $j = json_decode((string)$r, true);
    if (!is_array($j)) return 'Unexpected response';
    $rows = [];
    foreach (($j['data'] ?? $j['models'] ?? []) as $m) {
        $id = preg_replace('#^models/#', '', (string)($m['id'] ?? $m['name'] ?? ''));
        if ($id === '') continue;
        $rows[] = [$id, ai_is_free($name, $id, is_array($m) ? $m : [])];
    }
    usort($rows, fn($a, $b) => $b[1] <=> $a[1] ?: strcmp($b[0], $a[0]));
    return $rows ?: 'No models returned.';
}
/** Text for the admin page: FREE models are marked. */
function ai_list_models(string $name): string {
    $rows = ai_model_rows($name);
    if (is_string($rows)) return $rows;
    return implode("\n", array_map(fn($r) => ($r[1] ? 'FREE  ' : '      ') . $r[0], $rows));
}
/** Adds every FREE model of one provider to the model list. Existing ones are skipped. */
function ai_import_free(string $name): int {
    ensure_schema();
    $rows = ai_model_rows($name);
    if (!is_array($rows)) return 0;
    $added = 0;
    foreach ($rows as [$id, $free]) {
        if (!$free) continue;
        if (q('SELECT id FROM models WHERE provider=? AND model_id=?', [$name, $id])->fetch()) continue;
        q('INSERT INTO models(tier,provider,model_id,label,priority,active,is_free) VALUES(?,?,?,?,10,1,1)', [ai_tier_for($id), $name, $id, $id]);
        $added++;
    }
    return $added;
}

function alert_send(string $msg): bool {
    $tok = setting('alert_bot_token'); $chat = setting('alert_chat_id');
    if ($tok === '' || $chat === '') return false;
    [$c] = http_req('POST', 'https://api.telegram.org/bot' . $tok . '/sendMessage', ['Content-Type: application/x-www-form-urlencoded'], http_build_query(['chat_id' => $chat, 'text' => $msg]), 15);
    return $c === 200;
}
/** Telegram alert to the admin, at most once per cooldown for the same problem key. */
function alert_admin(string $key, string $msg): void {
    $cool = max(1, (int)setting('alert_cooldown_min', '30')) * 60;
    if (time() - (int)setting('alert_at_' . $key, '0') < $cool) return;
    set_setting('alert_at_' . $key, (string)time());
    alert_send($msg);
}
function note_provider(string $name, array $r): void {
    if ($r['ok']) { q('UPDATE providers SET fail_count=0, last_ok_at=NOW() WHERE name=?', [$name]); return; }
    $code = (int)$r['code'];
    $msg = 'HTTP ' . $code . ' ' . preg_replace('/\s+/', ' ', substr((string)$r['err'], 0, 180));
    q('UPDATE providers SET fail_count=fail_count+1, last_error=?, last_error_at=NOW() WHERE name=?', [$msg, $name]);
    $hint = in_array($code, [401, 403], true) ? 'key invalid, expired or no permission' : (in_array($code, [402, 429], true) ? 'quota, billing or rate limit' : ($code >= 500 ? 'provider outage' : ($code === 0 ? 'network error or timeout' : 'request rejected')));
    alert_admin('prov_' . $name, "TS Agent API problem\nProvider: $name\nProblem: $hint\nDetail: $msg");
}

function tier_cost(int $t): int { $d = [1 => '1', 2 => '12', 3 => '40']; return max(1, (int)setting('coin_cost_' . $t, $d[$t] ?? '1')); }
function coin_state(array $u, array $plan): array {
    $cap = (int)$plan['daily_coins'];
    $used = (int)q('SELECT COALESCE(SUM(coins),0) s FROM usage_log WHERE user_id=? AND created_at>=CURDATE()', [$u['id']])->fetch()['s'];
    $dl = max(0, $cap - $used); $bonus = max(0, (int)$u['bonus_coins']);
    return ['cap' => $cap, 'daily_left' => $dl, 'bonus' => $bonus, 'total' => $dl + $bonus];
}
function pick_models(int $tier): array {
    ensure_schema();
    return q("SELECT m.*, p.api_key, p.base_url, p.name AS pname FROM models m JOIN providers p ON p.name=m.provider WHERE m.tier=? AND m.active=1 AND m.is_free=1 AND p.enabled=1 AND p.api_key IS NOT NULL AND p.api_key<>'' ORDER BY m.priority, m.id", [$tier])->fetchAll();
}
