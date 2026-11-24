<?php
// Start crypto payment: creates an invoice on the gateway and sends the user to the payment page
require __DIR__ . '/inc/db.php';
require __DIR__ . '/inc/paypal.php'; // site_url()
require __DIR__ . '/inc/tspay.php';
if (free_mode()) { header('Location: account.php'); exit; }
if (empty($_SESSION['uid']) || $_SERVER['REQUEST_METHOD'] !== 'POST' || !csrf_ok()) { header('Location: account.php'); exit; }
$pl = q('SELECT * FROM plans WHERE id=? AND active=1 AND id<>1 AND price_usd>0', [(int)($_POST['plan_id'] ?? 0)])->fetch();
$u = q('SELECT * FROM users WHERE id=? AND banned=0', [$_SESSION['uid']])->fetch();
if (!$pl || !$u || !tspay_ready()) { header('Location: account.php?paid=err'); exit; }
$base = site_url();
[$c, $r] = tspay_call('crypto-checkout', [
    'amount' => number_format((float)$pl['price_usd'], 2, '.', ''),
    'currency' => 'USD',
    'full_name' => $u['name'],
    'email' => $u['email'],
    'ref' => 'u' . $u['id'] . 'p' . $pl['id'],
    'metadata' => ['uid' => (int)$u['id'], 'plan_id' => (int)$pl['id']],
    'redirect_url' => $base . '/tspay_return.php',
    'cancel_url' => $base . '/account.php?paid=cancel',
    'webhook_url' => $base . '/tspay_webhook.php',
], setting('tspay_crypto_key'));
$url = (string)($r['payment_url'] ?? '');
$gwHost = parse_url(setting('tspay_base_url'), PHP_URL_HOST);
parse_str((string)parse_url($url, PHP_URL_QUERY), $qs);
$invoice = preg_replace('/[^A-Za-z0-9_-]/', '', (string)($qs['id'] ?? ''));
if ($c !== 200 || !($r['status'] ?? false) || $invoice === '' || strcasecmp((string)parse_url($url, PHP_URL_HOST), (string)$gwHost) !== 0) {
    error_log('tspay create ' . $c . ' ' . substr(json_encode($r), 0, 300));
    header('Location: account.php?paid=err'); exit;
}
q('INSERT INTO payments(user_id,plan_id,method,trx_id,amount,currency) VALUES(?,?,?,?,?,?)',
    [$u['id'], $pl['id'], 'crypto', $invoice, $pl['price_usd'], 'USD']);
header('Location: ' . $url);
