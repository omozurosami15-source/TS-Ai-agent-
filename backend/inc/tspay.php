<?php
// Your own gateway (TS Pay) Crypto API integration
// crypto key  -> crypto-checkout (creates the payment link)
// main key    -> verify-payment and webhook signature (the gateway signs webhooks with the main API key)
function tspay_ready(): bool {
    return setting('tspay_base_url') !== '' && setting('tspay_crypto_key') !== '' && setting('tspay_main_key') !== '';
}
function tspay_call(string $route, array $data, string $key): array {
    $ch = curl_init(rtrim(setting('tspay_base_url'), '/') . '/api/' . $route);
    curl_setopt_array($ch, [CURLOPT_POST => 1, CURLOPT_POSTFIELDS => json_encode($data, JSON_UNESCAPED_UNICODE),
        CURLOPT_RETURNTRANSFER => 1, CURLOPT_TIMEOUT => 25,
        CURLOPT_HTTPHEADER => ['TSPAY-API-KEY: ' . $key, 'Content-Type: application/json']]);
    $r = curl_exec($ch); $c = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE); curl_close($ch);
    $j = json_decode((string)$r, true);
    return [$c, is_array($j) ? $j : []];
}
/** Verifies with the gateway server itself (not trusting the redirect or webhook), then activates the plan */
function tspay_settle(array $p): bool {
    if ($p['status'] === 'approved') return true;
    [$c, $v] = tspay_call('verify-payment', ['invoice_id' => $p['trx_id']], setting('tspay_main_key'));
    if ($c !== 200 || ($v['status'] ?? '') !== 'COMPLETED' || ($v['invoice_id'] ?? '') !== $p['trx_id']) return false;
    if (strtoupper((string)($v['currency'] ?? '')) !== $p['currency']) return false;
    if (round((float)($v['amount'] ?? 0), 2) < round((float)$p['amount'], 2)) return false;
    $meta = is_array($v['metadata'] ?? null) ? $v['metadata'] : [];
    if ((int)($meta['uid'] ?? 0) !== (int)$p['user_id'] || (int)($meta['plan_id'] ?? 0) !== (int)$p['plan_id']) return false;
    activate_payment((int)$p['id']);
    return q('SELECT status FROM payments WHERE id=?', [$p['id']])->fetch()['status'] === 'approved';
}
