<?php
// After returning from PayPal: capture, verify and activate premium
require __DIR__ . '/inc/db.php';
require __DIR__ . '/inc/paypal.php';
$id = preg_replace('/[^A-Z0-9]/', '', strtoupper((string)($_GET['token'] ?? '')));
if (empty($_SESSION['uid']) || $id === '') { header('Location: account.php'); exit; }
$p = q("SELECT * FROM payments WHERE trx_id=? AND user_id=? AND method='paypal'", [$id, $_SESSION['uid']])->fetch();
if (!$p) { header('Location: account.php?paid=err'); exit; }
if ($p['status'] === 'approved') { header('Location: account.php?paid=ok'); exit; }
[$c, $r] = pp_call('POST', '/v2/checkout/orders/' . $id . '/capture', null, ['PayPal-Request-Id: cap-' . $id]);
if ($c === 422) { [$c, $r] = pp_call('GET', '/v2/checkout/orders/' . $id); } // already captured, check the order status
$cap = $r['purchase_units'][0]['payments']['captures'][0] ?? [];
$capStatus = $cap['status'] ?? '';
$good = ($r['status'] ?? '') === 'COMPLETED' && $capStatus === 'COMPLETED'
    && ($cap['amount']['currency_code'] ?? '') === $p['currency']
    && number_format((float)($cap['amount']['value'] ?? 0), 2, '.', '') === number_format((float)$p['amount'], 2, '.', '');
if ($good) {
    activate_payment((int)$p['id']);
    $st = q('SELECT status FROM payments WHERE id=?', [$p['id']])->fetch()['status'];
    header('Location: account.php?paid=' . ($st === 'approved' ? 'ok' : 'err')); exit;
}
if ($capStatus === 'PENDING') { header('Location: account.php?paid=wait'); exit; }
q("UPDATE payments SET status='failed' WHERE id=? AND status='pending'", [$p['id']]);
header('Location: account.php?paid=err');
