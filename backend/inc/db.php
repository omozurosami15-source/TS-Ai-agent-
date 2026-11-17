<?php
require __DIR__ . '/config.php';
if (session_status() === PHP_SESSION_NONE) {
    session_set_cookie_params(['httponly' => true, 'samesite' => 'Lax', 'secure' => !empty($_SERVER['HTTPS'])]);
    session_start();
}
function db(): PDO {
    static $p;
    if (!$p) {
        try {
            $p = new PDO('mysql:host=' . DB_HOST . ';dbname=' . DB_NAME . ';charset=utf8mb4', DB_USER, DB_PASS,
                [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION, PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC]);
        } catch (PDOException $e) {
            // Friendly setup message instead of a blank 500. No credentials are shown.
            http_response_code(503);
            header('Content-Type: text/html; charset=utf-8');
            echo '<!doctype html><meta charset="utf-8"><title>Setup needed</title>'
               . '<body style="font-family:monospace;background:#020604;color:#d4ffe8;padding:24px;line-height:1.6">'
               . '<h2>Database is not connected</h2>'
               . '<p>1. Open <b>inc/config.php</b> on the server and replace DB_NAME, DB_USER and DB_PASS with your real MySQL details (cPanel &gt; MySQL Databases).</p>'
               . '<p>2. Make sure DB_HOST is correct (usually <b>localhost</b>).</p>'
               . '<p>3. If this is a new install, open <b>install.php</b> once, then delete it.</p>'
               . '</body>';
            exit;
        }
        $p->exec("SET time_zone='+06:00'");
    }
    return $p;
}
function q(string $sql, array $a = []): PDOStatement { $s = db()->prepare($sql); $s->execute($a); return $s; }
function setting(string $k, string $d = ''): string { $r = q('SELECT v FROM settings WHERE k=?', [$k])->fetch(); return $r ? (string)$r['v'] : $d; }
function set_setting(string $k, string $v): void { q('INSERT INTO settings(k,v) VALUES(?,?) ON DUPLICATE KEY UPDATE v=VALUES(v)', [$k, $v]); }
/** Free mode (default ON): everyone is unlimited, payments are hidden and disabled. Switch off in Admin > Settings when you want paid plans. */
function free_mode(): bool { return setting('free_mode', '0') === '1'; }
function tg_url(): string { return setting('support_link', TELEGRAM_URL) ?: TELEGRAM_URL; }
function site(): string { return setting('site_name', SITE_NAME) ?: SITE_NAME; }
function e($s): string { return htmlspecialchars((string)$s, ENT_QUOTES, 'UTF-8'); }
function csrf(): string { if (empty($_SESSION['csrf'])) $_SESSION['csrf'] = bin2hex(random_bytes(16)); return $_SESSION['csrf']; }
function csrf_field(): string { return '<input type="hidden" name="csrf" value="' . csrf() . '">'; }
function csrf_ok(): bool { return isset($_POST['csrf'], $_SESSION['csrf']) && hash_equals($_SESSION['csrf'], (string)$_POST['csrf']); }

function effective_plan(array $u): array {
    $free = q('SELECT * FROM plans WHERE id=1')->fetch();
    if ((int)$u['plan_id'] !== 1 && $u['plan_expires'] && strtotime($u['plan_expires']) > time()) {
        $p = q('SELECT * FROM plans WHERE id=?', [$u['plan_id']])->fetch();
        if ($p) return $p;
    }
    return $free;
}
function used_today(int $uid): int {
    return (int)q('SELECT COUNT(*) c FROM usage_log WHERE user_id=? AND created_at>=CURDATE()', [$uid])->fetch()['c'];
}
function create_user(string $name, string $email, string $pass): array {
    if (mb_strlen($name) < 2 || mb_strlen($name) > 60) return [0, 'Name must be 2-60 characters'];
    if (!filter_var($email, FILTER_VALIDATE_EMAIL)) return [0, 'Enter a valid email'];
    if (strlen($pass) < 6) return [0, 'Password must be at least 6 characters'];
    $email = strtolower($email);
    if (q('SELECT id FROM users WHERE email=?', [$email])->fetch()) return [0, 'This email already exists'];
    // Signup bonus, but only for the first 2 signups per IP address per day (stops bonus farming with many emails)
    db()->exec("CREATE TABLE IF NOT EXISTS signup_log(ip_hash CHAR(64) NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, INDEX(ip_hash))");
    $ih = hash('sha256', (string)($_SERVER['REMOTE_ADDR'] ?? ''));
    $recent = (int)q('SELECT COUNT(*) c FROM signup_log WHERE ip_hash=? AND created_at >= (NOW() - INTERVAL 1 DAY)', [$ih])->fetch()['c'];
    $bonus = $recent < 2 ? max(0, (int)setting('signup_bonus', '30')) : 0;
    q('INSERT INTO signup_log(ip_hash) VALUES(?)', [$ih]);
    q('INSERT INTO users(name,email,pass_hash,bonus_coins) VALUES(?,?,?,?)', [$name, $email, password_hash($pass, PASSWORD_DEFAULT), $bonus]);
    return [(int)db()->lastInsertId(), ''];
}
function check_login(string $email, string $pass): ?array {
    $u = q('SELECT * FROM users WHERE email=?', [strtolower($email)])->fetch();
    if ($u && password_verify($pass, $u['pass_hash'])) return $u;
    usleep(800000);
    return null;
}
function issue_token(int $uid): string {
    $t = bin2hex(random_bytes(32));
    q('UPDATE users SET token_hash=? WHERE id=?', [hash('sha256', $t), $uid]);
    return $t;
}

/** When a payment is confirmed, activates the user plan (safe to call multiple times) */
/** Adds columns introduced after the first install (safe to run every time). */
function ensure_schema(): void {
    static $done = false; if ($done) return; $done = true;
    $hasFree = (int)q("SELECT COUNT(*) c FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='models' AND COLUMN_NAME='is_free'")->fetch()['c'];
    if (!$hasFree) q('ALTER TABLE models ADD COLUMN is_free TINYINT NOT NULL DEFAULT 1');
    q('CREATE TABLE IF NOT EXISTS posts(id INT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(160) NOT NULL DEFAULT \'\', body TEXT NULL, link VARCHAR(300) NOT NULL DEFAULT \'\', media VARCHAR(120) NULL, media_type VARCHAR(10) NULL, active TINYINT NOT NULL DEFAULT 1, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, INDEX(active, id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4');
    q('CREATE TABLE IF NOT EXISTS bug_reports(id INT AUTO_INCREMENT PRIMARY KEY, kind VARCHAR(20) NOT NULL DEFAULT \'\', message TEXT NULL, context TEXT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, INDEX(id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4');
    q('CREATE TABLE IF NOT EXISTS login_fails(k VARCHAR(100) PRIMARY KEY, fails INT NOT NULL DEFAULT 0, locked_until DATETIME NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4');
    $has = (int)q("SELECT COUNT(*) c FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='plans' AND COLUMN_NAME='buy_coins'")->fetch()['c'];
    if (!$has) q('ALTER TABLE plans ADD COLUMN buy_coins INT NOT NULL DEFAULT 0');
}
/* Login throttle: after 5 wrong passwords from one IP, that IP is blocked for 15 minutes. */
function login_key(string $scope): string { return $scope . ':' . hash('sha256', $_SERVER['REMOTE_ADDR'] ?? 'x'); }
function login_blocked(string $scope): bool {
    ensure_schema();
    return (int)q('SELECT COUNT(*) c FROM login_fails WHERE k=? AND locked_until > NOW()', [login_key($scope)])->fetch()['c'] > 0;
}
function login_failed(string $scope): void {
    ensure_schema(); $k = login_key($scope);
    q('INSERT INTO login_fails(k,fails) VALUES(?,1) ON DUPLICATE KEY UPDATE fails=fails+1', [$k]);
    if ((int)q('SELECT fails FROM login_fails WHERE k=?', [$k])->fetch()['fails'] >= 5)
        q('UPDATE login_fails SET fails=0, locked_until=DATE_ADD(NOW(), INTERVAL 15 MINUTE) WHERE k=?', [$k]);
}
function login_succeeded(string $scope): void { q('DELETE FROM login_fails WHERE k=?', [login_key($scope)]); }

function activate_payment(int $id): bool {
    ensure_schema();
    $pdo = db();
    $pdo->beginTransaction();
    try {
        $p = q('SELECT * FROM payments WHERE id=? FOR UPDATE', [$id])->fetch();
        if (!$p || $p['status'] === 'approved') { $pdo->rollBack(); return false; }
        $pl = q('SELECT * FROM plans WHERE id=?', [$p['plan_id']])->fetch();
        $u = q('SELECT * FROM users WHERE id=? FOR UPDATE', [$p['user_id']])->fetch();
        if (!$pl || !$u) { $pdo->rollBack(); return false; }
        $base = time();
        if ((int)$u['plan_id'] === (int)$pl['id'] && $u['plan_expires'] && strtotime($u['plan_expires']) > $base) $base = strtotime($u['plan_expires']);
        q('UPDATE users SET plan_id=?, plan_expires=? WHERE id=?', [$pl['id'], date('Y-m-d H:i:s', $base + (int)$pl['days'] * 86400), $u['id']]);
        // one-time coins this plan gives on purchase (set per plan in Admin > Plans)
        $bc = (int)($pl['buy_coins'] ?? 0);
        if ($bc > 0) q('UPDATE users SET bonus_coins=bonus_coins+? WHERE id=?', [$bc, $u['id']]);
        q("UPDATE payments SET status='approved' WHERE id=?", [$id]);
        $pdo->commit();
        return true;
    } catch (Throwable $e) {
        if ($pdo->inTransaction()) $pdo->rollBack();
        error_log($e->getMessage());
        return false;
    }
}
