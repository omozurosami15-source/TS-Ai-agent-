<?php
// Delete this file after running it once
require __DIR__ . '/inc/db.php';
require __DIR__ . '/inc/ui.php';
if (is_file(__DIR__ . '/inc/install.lock')) { http_response_code(403); exit('Already installed. Delete install.php.'); }
$done = false; $err = '';
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $u = trim($_POST['username'] ?? ''); $p = $_POST['password'] ?? '';
    if (!preg_match('/^[A-Za-z0-9_]{3,30}$/', $u) || strlen($p) < 8) $err = 'Username must be 3-30 letters, digits or underscores, and the password at least 8 characters';
    else {
        $t = 'ENGINE=InnoDB DEFAULT CHARSET=utf8mb4';
        db()->exec("CREATE TABLE IF NOT EXISTS settings(k VARCHAR(60) PRIMARY KEY, v TEXT) $t");
        db()->exec("CREATE TABLE IF NOT EXISTS plans(id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(60) NOT NULL, price INT NOT NULL DEFAULT 0, price_usd DECIMAL(8,2) NOT NULL DEFAULT 0, days INT NOT NULL DEFAULT 0, daily_limit INT NOT NULL DEFAULT 0, daily_coins INT NOT NULL DEFAULT 0, max_tier TINYINT NOT NULL DEFAULT 1, active TINYINT NOT NULL DEFAULT 1) $t");
        db()->exec("CREATE TABLE IF NOT EXISTS users(id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(60) NOT NULL, email VARCHAR(120) NOT NULL UNIQUE, pass_hash VARCHAR(255) NOT NULL, plan_id INT NOT NULL DEFAULT 1, plan_expires DATETIME NULL, token_hash CHAR(64) NULL, banned TINYINT NOT NULL DEFAULT 0, bonus_coins INT NOT NULL DEFAULT 0, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, INDEX(token_hash)) $t");
        db()->exec("CREATE TABLE IF NOT EXISTS payments(id INT AUTO_INCREMENT PRIMARY KEY, user_id INT NOT NULL, plan_id INT NOT NULL, method VARCHAR(10) NOT NULL, trx_id VARCHAR(30) NOT NULL UNIQUE, amount DECIMAL(10,2) NOT NULL, currency VARCHAR(3) NOT NULL DEFAULT 'USD', status VARCHAR(10) NOT NULL DEFAULT 'pending', created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, INDEX(status)) $t");
        db()->exec("CREATE TABLE IF NOT EXISTS usage_log(id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id INT NOT NULL, tokens_in INT NOT NULL DEFAULT 0, tokens_out INT NOT NULL DEFAULT 0, coins INT NOT NULL DEFAULT 0, model_id VARCHAR(100) NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, INDEX(user_id, created_at)) $t");
        db()->exec("CREATE TABLE IF NOT EXISTS providers(name VARCHAR(20) PRIMARY KEY, api_key TEXT NULL, base_url VARCHAR(200) NOT NULL DEFAULT '', enabled TINYINT NOT NULL DEFAULT 0, fail_count INT NOT NULL DEFAULT 0, last_error VARCHAR(255) NULL, last_error_at DATETIME NULL, last_ok_at DATETIME NULL) $t");
        db()->exec("CREATE TABLE IF NOT EXISTS models(id INT AUTO_INCREMENT PRIMARY KEY, tier TINYINT NOT NULL, provider VARCHAR(20) NOT NULL, model_id VARCHAR(100) NOT NULL, label VARCHAR(60) NOT NULL DEFAULT '', priority INT NOT NULL DEFAULT 10, active TINYINT NOT NULL DEFAULT 1) $t");
        db()->exec("CREATE TABLE IF NOT EXISTS admins(id INT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(40) NOT NULL UNIQUE, pass_hash VARCHAR(255) NOT NULL) $t");
        if (!q('SELECT id FROM plans LIMIT 1')->fetch()) {
            q("INSERT INTO plans(id,name,price,price_usd,days,daily_limit,daily_coins,max_tier) VALUES (1,'Free',0,0,0,0,20,1),(2,'Starter',3,2.99,30,0,100,2),(3,'Plus',7,6.99,30,0,300,3),(4,'Pro',15,14.99,30,0,800,3),(5,'Max',30,29.99,30,0,2000,3)");
        }
        foreach (['anthropic', 'openai', 'gemini', 'grok', 'openrouter', 'elevenlabs', 'meta', 'custom'] as $pn) q('INSERT IGNORE INTO providers(name) VALUES(?)', [$pn]);
        foreach (['site_name' => SITE_NAME, 'notice' => '', 'maintenance' => '0', 'support_link' => TELEGRAM_URL, 'apk_version' => '', 'apk_file' => '', 'downloads' => '0', 'site_url' => '', 'paypal_mode' => 'sandbox', 'paypal_client_id' => '', 'paypal_secret' => '', 'tspay_base_url' => '', 'tspay_crypto_key' => '', 'tspay_main_key' => '', 'free_mode' => '0', 'alert_bot_token' => '', 'alert_chat_id' => '', 'alert_cooldown_min' => '30', 'coin_cost_1' => '1', 'coin_cost_2' => '12', 'coin_cost_3' => '40', 'cors_origin' => '*'] as $k => $v) {
            q('INSERT IGNORE INTO settings(k,v) VALUES(?,?)', [$k, $v]);
        }
        q('INSERT INTO admins(username,pass_hash) VALUES(?,?) ON DUPLICATE KEY UPDATE pass_hash=VALUES(pass_hash)', [$u, password_hash($p, PASSWORD_DEFAULT)]);
        file_put_contents(__DIR__ . '/inc/install.lock', date('c'));
        $done = true;
    }
}
ui_head('Install'); ?>
<div class="wrap" style="max-width:480px"><div class="card">
<h2>TS Agent Install</h2>
<?php if ($done): ?>
<div class="ok">Install complete! Now <b>delete the install.php file</b>, then log in at <a href="admin.php">admin.php</a>.</div>
<?php else: ?>
<?php if ($err): ?><div class="err"><?= e($err) ?></div><?php endif; ?>
<form method="post">
<label>Admin username</label><input name="username" required>
<label>Admin password (at least 8)</label><input name="password" type="password" required>
<p><button class="btn">Install</button></p>
</form>
<?php endif; ?>
</div></div>
<?php ui_foot();
