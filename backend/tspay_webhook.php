<?php
// Signed gateway webhook: confirms the payment even if the user closes the browser
require __DIR__ . '/inc/db.php';
require __DIR__ . '/inc/tspay.php';
$raw = file_get_contents('php://input');
$ts = $_SERVER['HTTP_X_TSPAY_TIMESTAMP'] ?? '';
$sig = $_SERVER['HTTP_X_TSPAY_SIGNATURE'] ?? '';
$key = setting('tspay_main_key');
if ($key === '' || !ctype_digit($ts) || abs(time() - (int)$ts) > 300) { http_response_code(400); exit('bad request'); }
if (!hash_equals('sha256=' . hash_hmac('sha256', $ts . '.' . $raw, $key), $sig)) { http_response_code(401); exit('bad signature'); }
$j = json_decode($raw, true);
$inv = preg_replace('/[^A-Za-z0-9_-]/', '', (string)($j['invoice_id'] ?? ''));
if ($inv !== '') {
    $p = q("SELECT * FROM payments WHERE trx_id=? AND method='crypto'", [$inv])->fetch();
    if ($p) tspay_settle($p);   // verify again with the gateway itself
}
echo 'OK';
