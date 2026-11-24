<?php
require __DIR__ . '/inc/db.php';
require __DIR__ . '/inc/ui.php';
$plans = q('SELECT * FROM plans WHERE active=1 ORDER BY price_usd, price')->fetchAll();
$apk = basename(setting('apk_file'));
$has = $apk !== '' && is_file(__DIR__ . '/uploads/' . $apk);
$notice = setting('notice');
$headline = 'Say it. Your phone does it.';
ui_head(site() . ' - AI assistant for your phone'); ?>
<div class="wrap">
<div class="row" style="justify-content:space-between;padding:6px 0">
  <b class="glitch" data-t="<?= e(strtoupper(site())) ?>" style="letter-spacing:.18em;font-size:19px;color:var(--cy)"><?= e(strtoupper(site())) ?></b>
  <div class="row"><a href="#features">./features</a><a href="feed.php">./feed</a><a href="<?= e(tg_url()) ?>" target="_blank" rel="noopener">./telegram</a><?php if (!free_mode()): ?><a href="#pricing">./pricing</a><?php endif; ?><a class="btn sm ghost" href="account.php">login</a></div>
</div>
<div class="ticker"><span>ACCESS: GRANTED &nbsp;//&nbsp; ENCRYPTION: ACTIVE &nbsp;//&nbsp; VOICE ENGINE ONLINE &nbsp;//&nbsp; ACCESSIBILITY BRIDGE: READY &nbsp;//&nbsp; USER KEY: LOCAL ONLY &nbsp;//&nbsp; NO DATA LEAVES YOUR PHONE EXCEPT YOUR OWN AI CALLS &nbsp;//&nbsp;</span></div>
<?php if ($notice): ?><div class="ok">[NOTICE] <?= e($notice) ?></div><?php endif; ?>

<div class="card" style="text-align:center;background:radial-gradient(ellipse at top,rgba(0,255,156,.12),rgba(2,14,9,.9) 70%)">
  <canvas id="orb" width="440" height="330" style="max-width:100%"></canvas>
  <h1 style="font-size:clamp(26px,6vw,40px)"><span class="glitch" data-t="<?= e($headline) ?>"><?= e($headline) ?></span></h1>
  <p class="mut">Speak your command; <?= e(site()) ?> reads the screen and taps for you.</p>
  <div class="card term" id="term" style="margin:14px auto;max-width:560px"></div>
  <div class="row" style="justify-content:center">
  <?php if ($has): ?>
    <a class="btn" href="download.php">⬇ Download APK <?= e(setting('apk_version')) ?></a>
  <?php else: ?>
    <span class="btn ghost">APK coming soon</span>
  <?php endif; ?>
    <a class="btn alt" href="account.php">Free account</a>
    <a class="btn ghost" href="<?= e(tg_url()) ?>" target="_blank" rel="noopener">✈ Join Telegram</a>
  </div>
  <p class="mut" style="font-size:12px">Android only. When installing, allow installs from "unknown sources" in your phone settings.</p>
</div>

<h2 id="features">Features</h2>
<div class="grid">
  <div class="card"><h3>🎙 Your language</h3><p class="mut">Tap the mic and talk in your own language. It answers in the same language.</p></div>
  <div class="card"><h3>📱 App control</h3><p class="mut">Open apps, search, type, scroll, tap.</p></div>
  <div class="card"><h3>🌐 Your social apps</h3><p class="mut">YouTube, Facebook, TikTok, Telegram, Instagram, X and GitHub. Say what to do and it does it. If it needs a permission, it asks you to grant it.</p></div>
  <div class="card"><h3>📤 Post and upload</h3><p class="mut">Post to Telegram channels, more platforms coming step by step.</p></div>
  <div class="card"><h3>🧠 Memory and soul</h3><p class="mut">It remembers your preferences, and you can set its personality yourself.</p></div>
  <div class="card"><h3>✋ Always in your control</h3><p class="mut">While a task runs, the screen shows a neon frame and an "ABORT" button. It asks permission before sending money or deleting a post.</p></div>
  <div class="card"><h3>🔑 Your own AI key</h3>
    <p class="mut">AI usage is billed to your own Anthropic account, with no extra charge from us. The key stays only on your phone and never goes to our server.</p></div>
</div>

<?php if (free_mode()): ?>
<h2 id="pricing">Price</h2>
<div class="card" style="text-align:center"><h3 style="font-size:30px;color:var(--cy)">100% FREE</h3>
<p class="mut">Every feature, unlimited commands, no card needed. Create an account, install the app and go.</p></div>
<?php else: ?>
<h2 id="pricing">Plan</h2>
<div class="grid">
<?php foreach ($plans as $p): ?>
  <div class="card">
    <h3><?= e($p['name']) ?></h3>
    <p style="font-size:28px;margin:.2em 0;color:var(--cy)"><?= (float)$p['price_usd'] > 0 ? '$' . e(number_format((float)$p['price_usd'], 2)) : 'Free' ?></p>
    <p class="mut"><?= (int)$p['days'] > 0 ? e($p['days']) . ' Days' : 'Always' ?><br>
    <?= (int)$p['daily_limit'] > 0 ? 'Daily ' . e($p['daily_limit']) . ' commands' : 'Unlimited commands' ?></p>
  </div>
<?php endforeach; ?>
</div>
<p class="mut">Pay with PayPal or card. When done, premium turns on automatically.</p>
<?php endif; ?>

<div class="card">
  <h3>[ Permissions and transparency ]</h3>
  <p class="mut">The app uses the Accessibility service so that, on your command, it can read the screen of other apps and tap. Screen text is sent, only to complete your task, to the AI using your own API key. Text in password fields is not sent. You can turn the service off at any time in the phone Accessibility settings.</p>
</div>
<div class="card" style="text-align:center"><h3>✈ Official Telegram channel</h3><p class="mut">Updates, new versions and support.</p><a class="btn" href="<?= e(tg_url()) ?>" target="_blank" rel="noopener">t.me/ts_official_top</a></div>
<p class="mut" style="text-align:center">© <?= date('Y') ?> <?= e(site()) ?> · <a href="<?= e(tg_url()) ?>" target="_blank" rel="noopener">Telegram</a></p>
</div>
<script>
// rotating dotted orb
const c=document.getElementById('orb'),x=c.getContext('2d');let t=0;const P=[];
for(let i=0;i<320;i++){const y=1-2*(i+.5)/320,r=Math.sqrt(1-y*y),th=i*2.399963;P.push([Math.cos(th)*r,y,Math.sin(th)*r]);}
function f(){t+=.012;const W=c.width,H=c.height,R=H*.3,cx=W/2,cy=H/2,ca=Math.cos(t),sa=Math.sin(t),ct=Math.cos(.4),st=Math.sin(.4);x.clearRect(0,0,W,H);
const g=x.createRadialGradient(cx,cy,R*.2,cx,cy,R*2);g.addColorStop(0,'rgba(0,255,156,.32)');g.addColorStop(1,'rgba(0,255,156,0)');x.fillStyle=g;x.fillRect(0,0,W,H);
for(let k=0;k<3;k++){x.save();x.translate(cx,cy);x.rotate(t*(.3+.15*k)+k*1.05);x.strokeStyle='rgba(0,229,255,.4)';x.lineWidth=1;x.beginPath();x.ellipse(0,0,R*1.35,R*(.42+.07*k),0,0,7);x.stroke();
const a=t*(1.5+.5*k);x.fillStyle='#fff';x.beginPath();x.arc(R*1.35*Math.cos(a),R*(.42+.07*k)*Math.sin(a),2.6,0,7);x.fill();x.restore();}
for(const p of P){const X=p[0]*ca+p[2]*sa,Z1=-p[0]*sa+p[2]*ca,Y=p[1]*ct-Z1*st,Z=p[1]*st+Z1*ct,d=(Z+1)/2,m=(p[1]+1)/2;
x.fillStyle=`rgba(${207*m|0},255,${156+76*m|0},${.2+.8*d})`;x.beginPath();x.arc(cx+X*R,cy+Y*R,.9+2.2*d,0,7);x.fill();}
requestAnimationFrame(f);}f();
// terminal demo
const L=[['$ ','tsagent --voice'],['> ','listening: "open Facebook, like the first video"'],['> ','[open_app] Facebook ........ OK'],['> ','[tap] like ................. OK'],['> ','[confirm] post it? y/n'],['> ','DONE ✓']];
const term=document.getElementById('term');let li=0,ci=0,out='';
function step(){if(li>=L.length){setTimeout(()=>{li=0;ci=0;out='';step()},4200);return}
const [p,s]=L[li];if(ci===0)out+=(li?'\n':'')+'<b>'+p+'</b>';
if(ci<s.length){ci++;term.innerHTML=out+'<i>'+s.slice(0,ci).replace(/</g,'&lt;')+'</i><span class="cursor"></span>';setTimeout(step,26)}
else{out+='<i>'+s.replace(/</g,'&lt;')+'</i>';li++;ci=0;setTimeout(step,420)}}
step();
</script>
<?php ui_foot();
