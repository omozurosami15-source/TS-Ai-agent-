<?php
// When the user returns from the gateway: verify on the server and activate premium
require __DIR__ . '/inc/db.php';
require __DIR__ . '/inc/tspay.php';
$inv = preg_replace('/[^A-Za-z0-9_-]/', '', (string)($_GET['invoice_id'] ?? ''));
if (empty($_SESSION['uid']) || $inv === '') { header('Location: account.php'); exit; }
$p = q("SELECT * FROM payments WHERE trx_id=? AND user_id=? AND method='crypto'", [$inv, $_SESSION['uid']])->fetch();
if (!$p) { header('Location: account.php?paid=err'); exit; }
header('Location: account.php?paid=' . (tspay_settle($p) ? 'ok' : 'wait'));
