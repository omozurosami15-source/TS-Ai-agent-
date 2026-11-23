<?php
require __DIR__ . '/inc/db.php';
$f = basename(setting('apk_file'));
$path = __DIR__ . '/uploads/' . $f;
if ($f === '' || !is_file($path)) { http_response_code(404); exit('APK has not been uploaded yet'); }
set_setting('downloads', (string)((int)setting('downloads', '0') + 1));
$v = preg_replace('/[^0-9A-Za-z._-]/', '', setting('apk_version', '1.0'));
header('Content-Type: application/vnd.android.package-archive');
header('Content-Disposition: attachment; filename="TS-Agent-' . $v . '.apk"');
header('Content-Length: ' . filesize($path));
readfile($path);
