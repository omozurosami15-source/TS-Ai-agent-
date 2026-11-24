<?php
// Creates a PayPal order and sends the user to the PayPal/card payment page
require __DIR__ . '/inc/db.php';
require __DIR__ . '/inc/paypal.php';
if (free_mode()) { header('Location: account.php'); exit; }
if (empty($_SESSION['uid']) || $_SERVER['REQUEST_METHOD'] !== 'POST' || !csrf_ok()) { header('Location: account.php'); exit; }
$pl = q('SELECT * FROM plans WHERE id=? AND active=1 AND id<>1 AND price_usd>0', [(int)($_POST['plan_id'] ?? 0)])->fetch();
if (!$pl || !pp_ready()) { header('Location: account.php?paid=err'); exit; }
$base = site_url();
[$c, $o] = pp_call('POST', '/v2/checkout/orders', [
    'intent' => 'CAPTURE',
    'purchase_units' => [[
        'reference_id' => 'plan' . $pl['id'],
        'description' => site() . ' - ' . $pl['name'],
        'amount' => ['currency_code' => 'USD', 'value' => number_format((float)$pl['price_usd'], 2, '.', '')],
    ]],
    'application_context' => ['brand_name' => site(), 'user_action' => 'PAY_NOW',
        'return_url' => $base . '/paypal_return.php', 'cancel_url' => $base . '/account.php?paid=cancel'],
]);
$link = '';
foreach (($o['links'] ?? []) as $l) { if (in_array($l['rel'] ?? '', ['approve', 'payer-action'], true)) { $link = $l['href']; break; } }
if ($c < 200 || $c > 299 || !$link || empty($o['id'])) {
    error_log('paypal create ' . $c . ' ' . substr(json_encode($o), 0, 300));
    header('Location: account.php?paid=err'); exit;
}
q('INSERT INTO payments(user_id,plan_id,method,trx_id,amount,currency) VALUES(?,?,?,?,?,?)',
    [$_SESSION['uid'], $pl['id'], 'paypal', $o['id'], $pl['price_usd'], 'USD']);
header('Location: ' . $link);
