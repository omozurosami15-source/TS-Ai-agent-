<?php
// Public feed: the posts you publish in Admin > Posts (like a channel feed)
require __DIR__ . '/inc/db.php';
require __DIR__ . '/inc/ui.php';
$posts = q('SELECT * FROM posts WHERE active=1 ORDER BY id DESC LIMIT 50')->fetchAll();
ui_head(site() . ' - Feed'); ?>
<style>.post-media{width:100%;max-height:70vh;object-fit:contain;border-radius:4px;display:block;margin-bottom:10px;background:#000}.post-body{white-space:pre-wrap}.post-date{font-size:11px}</style>
<div class="wrap">
<div class="row" style="justify-content:space-between;padding:6px 0">
  <a href="index.php">&larr; <?= e(site()) ?></a>
  <a href="<?= e(tg_url()) ?>" target="_blank" rel="noopener">./telegram</a>
</div>
<h2 class="glitch" data-t="LATEST">LATEST</h2>
<?php if (!$posts): ?><p class="mut">No posts yet. Check back soon.</p><?php endif; ?>
<?php foreach ($posts as $p): ?>
<article class="card post-card">
  <?php if ($p['media']): $src = 'uploads/posts/' . rawurlencode($p['media']); ?>
    <?php if ($p['media_type'] === 'video'): ?>
      <video src="<?= e($src) ?>" controls playsinline preload="metadata" class="post-media"></video>
    <?php else: ?>
      <img src="<?= e($src) ?>" alt="" loading="lazy" class="post-media">
    <?php endif; ?>
  <?php endif; ?>
  <?php if ($p['title'] !== ''): ?><h3><?= e($p['title']) ?></h3><?php endif; ?>
  <?php if ($p['body'] !== ''): ?><p class="post-body"><?= e($p['body']) ?></p><?php endif; ?>
  <?php if ($p['link'] !== ''): ?><a class="btn sm" href="<?= e($p['link']) ?>" target="_blank" rel="noopener">Open link</a><?php endif; ?>
  <p class="mut post-date"><?= e($p['created_at']) ?></p>
</article>
<?php endforeach; ?>
</div>
<?php ui_foot(); ?>
