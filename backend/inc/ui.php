<?php
function ui_head(string $title): void { ?>
<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="theme-color" content="#020604"><title><?= e($title) ?></title>
<style>
:root{--line:#0e4a31;--tx:#d4ffe8;--mut:#6fbf9a;--cy:#00ff9c;--pk:#ff2bd6;--vi:#00e5ff;--gr:#00e5ff;--rd:#ff3b5c;--am:#ffb02e}
*{box-sizing:border-box}
html{scroll-behavior:smooth}
body{margin:0;font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,"Courier New",monospace;color:var(--tx);background:#020604;min-height:100vh;line-height:1.65}
body:before{content:"";position:fixed;inset:0;pointer-events:none;z-index:40;background:radial-gradient(ellipse at center,transparent 55%,rgba(0,0,0,.75))}
#rain{position:fixed;inset:0;z-index:-2;opacity:.2}
.scan{position:fixed;inset:0;pointer-events:none;z-index:50;background:repeating-linear-gradient(0deg,rgba(0,0,0,.2) 0 1px,transparent 1px 3px)}
.scan:after{content:"";position:absolute;left:0;right:0;height:150px;background:linear-gradient(transparent,rgba(0,255,156,.08),transparent);animation:sweep 6s linear infinite}
@keyframes sweep{from{top:-150px}to{top:100%}}
a{color:var(--cy);text-decoration:none}a:hover{text-shadow:0 0 8px var(--cy)}
.wrap{max-width:1000px;margin:0 auto;padding:18px}
h1,h2,h3{margin:.3em 0;text-shadow:0 0 10px rgba(0,255,156,.35)}
h2:before{content:"# ";color:var(--pk)}
.mut{color:var(--mut)}
.card{background:rgba(2,14,9,.88);border:1px solid var(--line);border-radius:4px;padding:18px;margin:12px 0;position:relative;transition:.25s}
.card:before,.card:after{content:"";position:absolute;width:12px;height:12px;border:2px solid var(--cy);pointer-events:none}
.card:before{top:-2px;left:-2px;border-right:0;border-bottom:0}
.card:after{bottom:-2px;right:-2px;border-left:0;border-top:0}
.card:hover{border-color:var(--cy);box-shadow:0 0 22px rgba(0,255,156,.22)}
input,select,textarea{width:100%;padding:10px 12px;border-radius:3px;border:1px solid var(--line);background:#02100a;color:var(--cy);font:inherit;outline:0}
input:focus,select:focus,textarea:focus{border-color:var(--cy);box-shadow:0 0 12px rgba(0,255,156,.35)}
::placeholder{color:#3c7a5d}
label{display:block;margin:10px 0 4px;color:var(--mut);font-size:13px}
label:before{content:"> ";color:var(--cy)}
.btn{display:inline-block;padding:10px 18px;border-radius:3px;border:1px solid var(--cy);background:rgba(0,255,156,.06);color:var(--cy);font:inherit;font-weight:700;cursor:pointer;letter-spacing:.06em;text-transform:uppercase;transition:.2s;box-shadow:inset 0 0 12px rgba(0,255,156,.15)}
.btn:hover{background:var(--cy);color:#001a0e;box-shadow:0 0 24px rgba(0,255,156,.6);text-shadow:none}
.btn.alt{border-color:var(--pk);color:var(--pk);background:rgba(255,43,214,.06);box-shadow:inset 0 0 12px rgba(255,43,214,.15)}
.btn.alt:hover{background:var(--pk);color:#fff;box-shadow:0 0 24px rgba(255,43,214,.6)}
.btn.ghost{border-color:var(--line);color:var(--tx);background:transparent;box-shadow:none}
.btn.ghost:hover{border-color:var(--cy);color:var(--cy);background:rgba(0,255,156,.08)}
.btn.red{border-color:var(--rd);color:var(--rd);background:rgba(255,59,92,.06);box-shadow:none}
.btn.red:hover{background:var(--rd);color:#fff;box-shadow:0 0 20px rgba(255,59,92,.6)}
.btn.sm{padding:5px 11px;font-size:12px}
.err{background:rgba(255,59,92,.08);border:1px solid var(--rd);color:#ffc0cb;padding:10px;border-radius:3px;margin:10px 0}
.ok{background:rgba(0,255,156,.07);border:1px solid var(--cy);padding:10px;border-radius:3px;margin:10px 0}
.scroll{overflow-x:auto}table{width:100%;border-collapse:collapse;font-size:13px}
th,td{padding:8px;border-bottom:1px solid var(--line);text-align:left;vertical-align:middle}
th{color:var(--cy);font-weight:400}tr:hover td{background:rgba(0,255,156,.04)}
.grid{display:grid;gap:12px;grid-template-columns:repeat(auto-fit,minmax(210px,1fr))}
.tag{display:inline-block;padding:1px 10px;border-radius:2px;border:1px solid var(--cy);color:var(--cy);font-size:12px}
.row{display:flex;gap:8px;flex-wrap:wrap;align-items:center}
code{color:var(--am)}
.glitch{position:relative;display:inline-block}
.glitch:before,.glitch:after{content:attr(data-t);position:absolute;left:0;top:0;width:100%;overflow:hidden;text-shadow:none}
.glitch:before{color:var(--pk);animation:g1 3s infinite steps(1)}
.glitch:after{color:var(--vi);animation:g2 3s infinite steps(1)}
@keyframes g1{0%,90%,100%{clip-path:inset(0 0 100% 0);transform:none}91%{clip-path:inset(15% 0 60% 0);transform:translate(-4px,0)}94%{clip-path:inset(65% 0 8% 0);transform:translate(3px,0)}97%{clip-path:inset(35% 0 40% 0);transform:translate(-2px,0)}}
@keyframes g2{0%,91%,100%{clip-path:inset(100% 0 0 0);transform:none}92%{clip-path:inset(55% 0 20% 0);transform:translate(4px,0)}95%{clip-path:inset(10% 0 70% 0);transform:translate(-3px,0)}98%{clip-path:inset(75% 0 5% 0);transform:translate(2px,0)}}
.cursor:after{content:"█";margin-left:2px;animation:blink 1s steps(1) infinite}
@keyframes blink{50%{opacity:0}}
.ticker{overflow:hidden;white-space:nowrap;border-top:1px solid var(--line);border-bottom:1px solid var(--line);color:var(--mut);font-size:12px;padding:5px 0;margin:8px 0}
.ticker span{display:inline-block;padding-left:100%;animation:tick 28s linear infinite}
@keyframes tick{to{transform:translateX(-100%)}}
.term{font-size:13px;min-height:168px;white-space:pre-wrap;text-align:left}
.term b{color:var(--cy)}.term i{color:var(--vi);font-style:normal}
.fade{opacity:0;transform:translateY(14px);transition:.7s}.fade.in{opacity:1;transform:none}
@media (prefers-reduced-motion:reduce){.scan:after,.glitch:before,.glitch:after,.ticker span,.cursor:after{animation:none}}
</style></head><body>
<canvas id="rain"></canvas><div class="scan"></div>
<script>
(function(){if(matchMedia('(prefers-reduced-motion:reduce)').matches)return;
const c=document.getElementById('rain'),x=c.getContext('2d');let W,H,cols,drops;const G='01{}<>/\\|=+*#$%&ABCDEF789:;'.split('');
function rs(){W=c.width=innerWidth;H=c.height=innerHeight;cols=Math.ceil(W/16);drops=Array.from({length:cols},()=>Math.random()*-50);}
rs();addEventListener('resize',rs);
setInterval(()=>{x.fillStyle='rgba(2,6,4,.14)';x.fillRect(0,0,W,H);x.font='14px monospace';
for(let i=0;i<cols;i++){x.fillStyle=Math.random()>.975?'#d4ffe8':'#00ff9c';x.fillText(G[Math.random()*G.length|0],i*16,drops[i]*16);
if(drops[i]*16>H&&Math.random()>.975)drops[i]=0;drops[i]+=1;}},60);})();
</script>
<?php }
function ui_foot(): void { ?>
<script>
const io=new IntersectionObserver(es=>es.forEach(e=>{if(e.isIntersecting){e.target.classList.add('in');io.unobserve(e.target)}}),{threshold:.12});
document.querySelectorAll('.card').forEach((el,i)=>{el.classList.add('fade');el.style.transitionDelay=(i%4)*70+'ms';io.observe(el)});
</script></body></html>
<?php }
