<?php
function pp_base(): string { return setting('paypal_mode', 'sandbox') === 'live' ? 'https://api-m.paypal.com' : 'https://api-m.sandbox.paypal.com'; }
function pp_ready(): bool { return setting('paypal_client_id') !== '' && setting('paypal_secret') !== ''; }
function pp_token(): ?string {
    $ch = curl_init(pp_base() . '/v1/oauth2/token');
    curl_setopt_array($ch, [CURLOPT_POST => 1, CURLOPT_POSTFIELDS => 'grant_type=client_credentials',
        CURLOPT_USERPWD => setting('paypal_client_id') . ':' . setting('paypal_secret'),
        CURLOPT_RETURNTRANSFER => 1, CURLOPT_TIMEOUT => 30, CURLOPT_HTTPHEADER => ['Accept: application/json']]);
    $r = curl_exec($ch); curl_close($ch);
    $j = json_decode((string)$r, true);
    return $j['access_token'] ?? null;
}
function pp_call(string $method, string $path, ?array $body = null, array $h = []): array {
    $t = pp_token();
    if (!$t) return [0, []];
    $ch = curl_init(pp_base() . $path);
    $o = [CURLOPT_CUSTOMREQUEST => $method, CURLOPT_RETURNTRANSFER => 1, CURLOPT_TIMEOUT => 40,
        CURLOPT_HTTPHEADER => array_merge(['Content-Type: application/json', 'Authorization: Bearer ' . $t], $h)];
    if ($body !== null) $o[CURLOPT_POSTFIELDS] = json_encode($body, JSON_UNESCAPED_UNICODE);
    elseif ($method === 'POST') $o[CURLOPT_POSTFIELDS] = '{}';
    curl_setopt_array($ch, $o);
    $r = curl_exec($ch); $c = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE); curl_close($ch);
    $j = json_decode((string)$r, true);
    return [$c, is_array($j) ? $j : []];
}
function site_url(): string {
    $u = rtrim(setting('site_url'), '/');
    if ($u !== '') return $u;
    $s = !empty($_SERVER['HTTPS']) ? 'https' : 'http';
    return $s . '://' . ($_SERVER['HTTP_HOST'] ?? 'localhost') . rtrim(dirname($_SERVER['SCRIPT_NAME']), '/\\');
}
