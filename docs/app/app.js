'use strict';
/* TS Agent web app: everything that makes sense in a browser. Phone-screen control, floating orb and background
   services need the Android app. Keys stay in this browser (localStorage). No server of ours. */
const CFG = {
  OWNER_NAME: 'Tarek', OWNER_CONTACT: '+8801622058034', TELEGRAM_URL: 'https://t.me/ts_official_top',
  GITHUB_REPO: 'OWNER/REPO',
  SERVER: '',                     // <- your TS Cloud server, for example 'https://api.yourdomain.com' (no trailing slash)      // <- your repo, for example 'tarek/ts-agent'
  ALL_FREE: false, BUY_URL: 'https://t.me/ts_official_top', DEFAULT_LANG: 'en-US'
};
const HAS_DOM = typeof document !== 'undefined';

/* ---------- storage ---------- */
const store = (() => {
  const mem = {};
  const ok = (() => { try { return typeof localStorage !== 'undefined' && !!localStorage; } catch (e) { return false; } })();
  return {
    get(k, d = '') { try { if (ok) { const v = localStorage.getItem('tsa_' + k); return v === null ? d : v; } } catch (e) {} return k in mem ? mem[k] : d; },
    set(k, v) { try { if (ok) { localStorage.setItem('tsa_' + k, String(v)); return; } } catch (e) {} mem[k] = String(v); },
    del(k) { try { if (ok) localStorage.removeItem('tsa_' + k); } catch (e) {} delete mem[k]; }
  };
})();
const flag = (k, d = false) => store.get(k, d ? '1' : '0') === '1';

/* ---------- crypto helpers ---------- */
const enc = new TextEncoder();
const hex = (buf) => [...new Uint8Array(buf)].map(b => b.toString(16).padStart(2, '0')).join('');
async function sha256hex(s) { return hex(await crypto.subtle.digest('SHA-256', enc.encode(s))); }
const normCode = (s) => String(s).toUpperCase().replace(/[^A-Z0-9]/g, '');
const b64 = (u8) => btoa(String.fromCharCode(...u8));
const unb64 = (s) => Uint8Array.from(atob(s), c => c.charCodeAt(0));
async function pbkdf2(pass, salt) {
  const k = await crypto.subtle.importKey('raw', enc.encode(pass), 'PBKDF2', false, ['deriveBits']);
  return new Uint8Array(await crypto.subtle.deriveBits({ name: 'PBKDF2', salt, iterations: 120000, hash: 'SHA-256' }, k, 256));
}

/* ---------- owner mode (private, local) ---------- */
const Owner = {
  isSet: () => !!store.get('owner_hash'),
  unlocked: () => !Owner.isSet() || Date.now() < Number(store.get('owner_until', '0')),
  logout() { store.set('owner_until', '0'); },
  async login(pass) {
    const WEEK = 7 * 24 * 3600 * 1000, now = Date.now();
    if (!Owner.isSet()) {
      if (pass.length < 8) return 'Choose a passphrase of at least 8 characters';
      const salt = crypto.getRandomValues(new Uint8Array(16));
      store.set('owner_salt', b64(salt)); store.set('owner_hash', b64(await pbkdf2(pass, salt)));
      store.set('owner_until', now + WEEK); store.set('owner_fail', 0);
      return 'Owner passphrase set. Owner mode is unlocked.';
    }
    const wait = Number(store.get('owner_lock', '0')) - now;
    if (wait > 0) return 'Too many wrong tries. Wait ' + Math.ceil(wait / 1000) + ' seconds.';
    const got = await pbkdf2(pass, unb64(store.get('owner_salt'))), want = unb64(store.get('owner_hash'));
    let diff = got.length ^ want.length; for (let i = 0; i < got.length; i++) diff |= got[i] ^ (want[i] || 0);
    if (diff === 0) { store.set('owner_until', now + WEEK); store.set('owner_fail', 0); return 'Owner mode unlocked'; }
    const f = Number(store.get('owner_fail', '0')) + 1;
    store.set('owner_fail', f >= 5 ? 0 : f); store.set('owner_lock', f >= 5 ? now + 60000 : 0);
    return 'Wrong passphrase';
  }
};

/* ---------- plans and codes ---------- */
const Plan = {
  get() { const p = store.get('plan'); const u = Number(store.get('plan_until', '0')); return p && (u === 0 || Date.now() < u) ? p : ''; },
  isOwner: () => Plan.get() === 'owner' && Owner.unlocked(),
  companion: () => CFG.ALL_FREE || ['p1', 'p2', 'owner'].includes(Plan.get()),
  premium: () => CFG.ALL_FREE || ['p2', 'owner'].includes(Plan.get()),
  label() {
    const p = Plan.get(), u = Number(store.get('plan_until', '0'));
    const n = { p1: 'PLAN 1', p2: 'PLAN 2', owner: 'OWNER' }[p] || 'FREE';
    return !p || p === 'owner' || u === 0 ? n : n + ' until ' + new Date(u).toISOString().slice(0, 10);
  }
};
async function redeem(raw) {
  const code = normCode(raw);
  if (code.length < 12) return 'That code is not valid.';
  let book; try { book = await (await fetch('../coupons.json', { cache: 'no-store' })).json(); } catch (e) { return 'Could not load the code list. Check your connection.'; }
  const h = await sha256hex(code);
  const c = (book.coupons || []).find(x => x.h === h);
  if (!c) return 'That code is not valid.';
  if ((book.revoked || []).includes(c.id)) return 'That code has been withdrawn.';
  store.set('plan', c.plan); store.set('plan_code_id', c.id);
  store.set('plan_until', c.days === 0 ? 0 : Date.now() + c.days * 86400000);
  return 'Activated: ' + Plan.label();
}

/* ---------- language pack (Bengali phrases -> English commands) ---------- */
const esc = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
function packBuild(phrase) {
  let out = '^', last = 0, m; const re = /\{[xn]\}/g;
  while ((m = re.exec(phrase))) { out += esc(phrase.slice(last, m.index)) + (m[0] === '{n}' ? '([0-9\\u09E6-\\u09EF]+)' : '(.+?)'); last = m.index + 3; }
  return new RegExp(out + esc(phrase.slice(last)) + '$', 'i');
}
function packCompile(json) {
  const rules = []; for (const r of json.rules || []) for (const s of r.say) rules.push({ rx: packBuild(s.trim()), cmd: r.cmd });
  const aliases = {}; for (const [k, v] of Object.entries(json.aliases || {})) aliases[k.toLowerCase()] = v;
  return { rules, aliases };
}
function packCanonical(text, pack) {
  if (!pack || !/[\u0980-\u09FF]/.test(text)) return null;
  const t = text.trim().replace(/[\u0964.!?\s]+$/, '');
  for (const r of pack.rules) {
    const m = t.match(r.rx); if (!m) continue;
    let gi = 1;
    let out = r.cmd.replace(/\{[xn]\}/g, ph => { const g = (m[gi++] || '').trim(); return ph === '{n}' ? g.replace(/[\u09E6-\u09EF]/g, d => String(d.charCodeAt(0) - 0x9E6)) : g; });
    if (out.startsWith('open ')) { const n = out.slice(5).trim(); out = 'open ' + (pack.aliases[n.toLowerCase()] || n); }
    return out;
  }
  return null;
}

/* ---------- command parsing (pure) ---------- */
const SITES = { youtube: 'https://www.youtube.com', facebook: 'https://www.facebook.com', tiktok: 'https://www.tiktok.com', telegram: 'https://web.telegram.org',
  instagram: 'https://www.instagram.com', twitter: 'https://x.com', x: 'https://x.com', github: 'https://github.com', whatsapp: 'https://web.whatsapp.com',
  gmail: 'https://mail.google.com', maps: 'https://www.google.com/maps' };
const PHONE_ONLY = ['back', 'go back', 'home', 'go home', 'recents', 'recent apps', 'notifications', 'open notifications', 'quick settings', 'lock', 'lock screen',
  'lock phone', 'screenshot', 'take screenshot', 'scroll', 'scroll down', 'scroll up', 'wifi', 'wifi settings', 'bluetooth', 'bluetooth settings', 'settings', 'open settings'];
function parseCommand(raw) {
  const t = String(raw).trim().replace(/[.!?]+$/, ''), l = t.toLowerCase(); let m;
  if (!l) return null;
  if (/^(?:who (?:is|are) (?:the |your )?(?:owner|developer|creator|maker|admin)(?: of (?:this|the) app)?|who (?:owns|made|created|built|developed) (?:this|it|you)(?: app)?|(?:owner|developer|creator))$/i.test(t)) return { k: 'owner' };
  if (/^.*\b(?:owner|developer|creator|admin)\b.*\b(?:number|phone|contact|whatsapp)\b.*$|^.*\b(?:number|phone|contact|whatsapp)\b.*\b(?:owner|developer|creator|admin)\b.*$|^contact(?: us)?$/i.test(t)) return { k: 'contact' };
  if (/^(?:share|upload|post|send)(?: my| the)?(?: (?:latest|last|newest|recent))? (?:video|photo|image|picture|pic)(?: file)? (?:to|on|in|at) [a-z]/i.test(t)) return { k: 'phoneonly' };
  if (PHONE_ONLY.includes(l) || /^(?:click|tap|type|enter) .+$/i.test(t) || /^(?:autoplay|stop autoplay).*$/i.test(t)) return { k: 'phoneonly' };
  if (/^(?:play )?(?:my |the )?favou?rite (?:song|music)(?: please)?$/i.test(t)) return { k: 'song' };
  if (/^(?:stop|pause) (?:the )?(?:music|song)$/i.test(t)) return { k: 'stopmusic' };
  if (l === 'stop') return { k: 'stop' };
  if (['my memory', 'what do you know about me', 'who am i', 'what do you remember'].includes(l)) return { k: 'showmem' };
  if (['forget memory', 'clear memory'].includes(l)) return { k: 'clearmem' };
  if (['my websites', 'my sites', 'my links', 'websites', 'links'].includes(l)) return { k: 'links' };
  if (['news', 'headlines', 'read news', 'read headlines', "today's news", "what's the news", 'whats the news'].includes(l)) return { k: 'news' };
  if (['youtube videos', 'my videos', 'my youtube videos'].includes(l)) return { k: 'yt', sub: 'videos' };
  if (['youtube stats', 'channel stats', 'youtube channel'].includes(l)) return { k: 'yt', sub: 'stats' };
  if (['youtube analytics', 'channel analytics'].includes(l)) return { k: 'yt', sub: 'analytics' };
  if ((m = t.match(/^youtube title (\d{1,2}) (.+)$/i))) return { k: 'yt', sub: 'title', n: +m[1], text: m[2] };
  if ((m = t.match(/^youtube description (\d{1,2}) (.+)$/i))) return { k: 'yt', sub: 'description', n: +m[1], text: m[2] };
  if ((m = t.match(/^youtube thumbnail (\d{1,2})$/i))) return { k: 'yt', sub: 'thumbnail', n: +m[1] };
  if ((m = t.match(/^youtube comments (\d{1,2})$/i))) return { k: 'yt', sub: 'comments', n: +m[1] };
  if (l === 'routines' || l === 'routine list') return { k: 'rlist' };
  if ((m = t.match(/^routine add ([a-z0-9_-]+)\s*:\s*(.+)$/i))) return { k: 'radd', name: m[1].toLowerCase(), steps: m[2] };
  if ((m = l.match(/^routine delete (.+)$/))) return { k: 'rdel', name: m[1].trim() };
  if ((m = l.match(/^run (.+)$/))) return { k: 'run', name: m[1].trim() };
  if ((m = t.match(/^tgsetup (\S+) (\S+)$/i))) return { k: 'tgsetup', token: m[1], chat: m[2] };
  if ((m = t.match(/^tg (.+)$/i))) return { k: 'tg', text: m[1] };
  if ((m = t.match(/^(?:give me |show |send |what is |what's )?(?:the )?(?:link|url|website|site)(?: of| for| to)? (.+)$/i))) return { k: 'link', q: m[1] };
  if ((m = t.match(/^(.+?) (?:link|url|website|site)$/i))) return { k: 'link', q: m[1] };
  if ((m = t.match(/^(?:remember|note)(?: that)? (.+)$/i))) return { k: 'remember', text: m[1] };
  if ((m = t.match(/^set (?:a )?timer (?:for )?(\d+) ?(seconds?|secs?|minutes?|mins?|hours?|hrs?)$/i))) {
    const n = +m[1], u = m[2].toLowerCase(); return { k: 'timer', secs: u[0] === 'h' ? n * 3600 : u[0] === 'm' ? n * 60 : n, label: m[1] + ' ' + m[2] };
  }
  if ((m = t.match(/^(?:search )?youtube (?:for )?(.+)$/i)) || (m = t.match(/^play (.+) on youtube$/i))) return { k: 'url', url: 'https://www.youtube.com/results?search_query=' + encodeURIComponent(m[1]), say: 'Searching YouTube' };
  if ((m = t.match(/^(?:google|search(?: google)?(?: for)?) (.+)$/i))) return { k: 'url', url: 'https://www.google.com/search?q=' + encodeURIComponent(m[1]), say: 'Searching' };
  if ((m = t.match(/^(?:map|navigate to|directions to) (.+)$/i))) return { k: 'url', url: 'https://www.google.com/maps/search/?api=1&query=' + encodeURIComponent(m[1]), say: 'Opening map' };
  if ((m = t.match(/^(?:call|dial) ([+0-9 ()-]{3,})$/i))) return { k: 'url', url: 'tel:' + m[1].trim().replace(/\s+/g, ''), say: 'Dialing' };
  if ((m = t.match(/^open (?:url |link |website )?(https?:\/\/\S+)$/i))) return { k: 'url', url: m[1], say: 'Opening link' };
  if ((m = t.match(/^(?:open|launch|start) ([a-z0-9][a-z0-9 ._-]*)$/i))) return /\b(and|then)\b|,/i.test(m[1]) ? null : { k: 'open', name: m[1].trim() };
  return null;
}
const DEFAULT_REM = '13:30 Have you eaten, {call}? Take a short break and eat something.\n21:00 Dinner time, {call}. Did you eat?\n23:30 It is late, {call}. Time to rest soon. Sleep well.';
function parseSlots(raw) {
  return String(raw || DEFAULT_REM).split('\n').map(l => l.trim().match(/^(\d{1,2}):(\d{2})\s+(.+)$/)).filter(Boolean)
    .map(m => ({ h: +m[1], m: +m[2], text: m[3] })).filter(s => s.h < 24 && s.m < 60).slice(0, 6);
}

/* ---------- AI providers (your own keys, browser to provider) ---------- */
const PROVIDERS = ['anthropic', 'openai', 'gemini', 'grok', 'custom', 'cloud'];
const LLM = {
  provider() { const p = store.get('llm_provider', 'anthropic'); return PROVIDERS.includes(p) ? p : 'anthropic'; },
  key(p = LLM.provider()) { return (p === 'cloud' ? store.get('svtoken') : store.get('key_' + p)).trim(); },
  model(p = LLM.provider()) { return store.get('model_' + p).trim() || (p === 'anthropic' ? 'claude-sonnet-5-5' : p === 'cloud' ? '1' : ''); },
  base(p) { return ({ openai: 'https://api.openai.com/v1', grok: 'https://api.x.ai/v1', custom: store.get('llm_base').trim() }[p] || '').replace(/\/+$/, ''); },
  configured() { const p = LLM.provider(); return !!LLM.key(p) && !!LLM.model(p) && (p !== 'custom' || LLM.base(p).startsWith('https://')) && (p !== 'cloud' || !!CFG.SERVER); },
  async complete(system, prompt, search) {
    const p = LLM.provider(), key = LLM.key(p), model = LLM.model(p); let r, j;
    if (p === 'cloud') {
      r = await fetch(CFG.SERVER + '/api.php?action=chat', { method: 'POST', headers: { 'X-Token': key, 'Content-Type': 'application/json' }, body: JSON.stringify({ system, prompt, tier: +model || 1 }) });
      j = await r.json().catch(() => ({}));
      if (r.status === 401) store.del('svtoken');
      if (!r.ok) throw new Error(j.error || 'Server error ' + r.status);
      if (j.coins) store.set('coins_text', j.coins.daily_left + '/' + j.coins.cap + (j.coins.bonus ? '+' + j.coins.bonus : ''));
      return j.text || '';
    }
    if (p === 'anthropic') {
      const body = { model, max_tokens: search ? 900 : 700, system, messages: [{ role: 'user', content: prompt }] };
      if (search) body.tools = [{ type: 'web_search_20250305', name: 'web_search', max_uses: 3 }];
      r = await fetch('https://api.anthropic.com/v1/messages', { method: 'POST', headers: { 'x-api-key': key, 'anthropic-version': '2023-06-01', 'anthropic-dangerous-direct-browser-access': 'true', 'content-type': 'application/json' }, body: JSON.stringify(body) });
      if (!r.ok) throw new Error('AI error ' + r.status + ': ' + (await r.text()).slice(0, 160));
      j = await r.json(); return (j.content || []).filter(b => b.type === 'text').map(b => b.text).join('');
    }
    if (p === 'gemini') {
      r = await fetch('https://generativelanguage.googleapis.com/v1beta/models/' + model.replace(/^models\//, '') + ':generateContent', { method: 'POST', headers: { 'x-goog-api-key': key, 'Content-Type': 'application/json' },
        body: JSON.stringify({ systemInstruction: { parts: [{ text: system }] }, contents: [{ role: 'user', parts: [{ text: prompt }] }] }) });
      if (!r.ok) throw new Error('AI error ' + r.status + ': ' + (await r.text()).slice(0, 160));
      j = await r.json(); return ((j.candidates || [{}])[0].content || { parts: [] }).parts.map(x => x.text || '').join('');
    }
    r = await fetch(LLM.base(p) + '/chat/completions', { method: 'POST', headers: { Authorization: 'Bearer ' + key, 'Content-Type': 'application/json' },
      body: JSON.stringify({ model, messages: [{ role: 'system', content: system }, { role: 'user', content: prompt }] }) });
    if (!r.ok) throw new Error('AI error ' + r.status + ': ' + (await r.text()).slice(0, 160));
    j = await r.json(); const c = j.choices[0].message.content;
    return Array.isArray(c) ? c.map(x => x.text || '').join('') : String(c || '');
  },
  async models() {
    const p = LLM.provider(), key = LLM.key(p); if (!key) throw new Error('Enter the key for ' + p.toUpperCase() + ' first');
    let r, j;
    if (p === 'cloud') return ['1', '2', '3'];
    if (p === 'anthropic') { r = await fetch('https://api.anthropic.com/v1/models?limit=100', { headers: { 'x-api-key': key, 'anthropic-version': '2023-06-01', 'anthropic-dangerous-direct-browser-access': 'true' } }); if (!r.ok) throw new Error('Error ' + r.status); j = await r.json(); return j.data.map(m => m.id).sort().reverse(); }
    if (p === 'gemini') { r = await fetch('https://generativelanguage.googleapis.com/v1beta/models?pageSize=200', { headers: { 'x-goog-api-key': key } }); if (!r.ok) throw new Error('Error ' + r.status); j = await r.json(); return j.models.filter(m => (m.supportedGenerationMethods || []).includes('generateContent')).map(m => m.name.replace(/^models\//, '')).sort().reverse(); }
    const b = LLM.base(p); if (!b.startsWith('https://')) throw new Error('Set the provider base URL first');
    r = await fetch(b + '/models', { headers: { Authorization: 'Bearer ' + key } }); if (!r.ok) throw new Error('Error ' + r.status); j = await r.json(); return j.data.map(m => m.id).sort().reverse();
  }
};

/* ---------- ElevenLabs female voice ---------- */
const Eleven = {
  configured: () => Plan.premium() && flag('el_on', true) && !!store.get('el_key') && !!store.get('el_voice'),
  async voices(key) {
    const r = await fetch('https://api.elevenlabs.io/v2/voices?gender=female&page_size=100', { headers: { 'xi-api-key': key } });
    if (!r.ok) throw new Error('ElevenLabs error ' + r.status + '. Check the API key.');
    return ((await r.json()).voices || []).filter(v => String((v.labels || {}).gender || '').toLowerCase() === 'female')
      .map(v => ({ id: v.voice_id, name: v.name + (v.labels && v.labels.accent ? ' (' + v.labels.accent + ')' : '') }));
  },
  async model(key) {
    if (store.get('el_model') && Date.now() - Number(store.get('el_model_at', '0')) < 86400000) return store.get('el_model');
    let best = 'eleven_multilingual_v2';
    try {
      const r = await fetch('https://api.elevenlabs.io/v1/models', { headers: { 'xi-api-key': key } });
      if (r.ok) { let n = 0; for (const m of await r.json()) { const ls = m.languages || []; if (m.can_do_text_to_speech && !m.requires_alpha_access && ls.some(l => l.language_id === 'bn') && ls.length > n) { n = ls.length; best = m.model_id; } }
        store.set('el_model', best); store.set('el_model_at', Date.now()); }
    } catch (e) {}
    return best;
  },
  audio: null,
  async speak(text) {
    const key = store.get('el_key'), voice = store.get('el_voice').replace(/[^A-Za-z0-9]/g, '');
    const r = await fetch('https://api.elevenlabs.io/v1/text-to-speech/' + voice + '?output_format=mp3_44100_128', { method: 'POST',
      headers: { 'xi-api-key': key, 'Content-Type': 'application/json', Accept: 'audio/mpeg' }, body: JSON.stringify({ text: text.slice(0, 1500), model_id: await Eleven.model(key) }) });
    if (!r.ok) throw new Error('ElevenLabs ' + r.status);
    Eleven.stop(); const a = new Audio(URL.createObjectURL(await r.blob())); Eleven.audio = a; return a;
  },
  stop() { if (Eleven.audio) { try { Eleven.audio.pause(); } catch (e) {} Eleven.audio = null; } }
};

/* ---------- YouTube connector (your own Google OAuth client ID) ---------- */
const YT = {
  token: '', expires: 0, cache: [],
  gis() { return new Promise((res, rej) => { if (window.google && google.accounts) return res(); const s = document.createElement('script'); s.src = 'https://accounts.google.com/gsi/client'; s.onload = res; s.onerror = () => rej(new Error('Could not load Google sign-in')); document.head.append(s); }); },
  async auth() {
    if (YT.token && Date.now() < YT.expires) return YT.token;
    const cid = store.get('yt_client').trim(); if (!cid) throw new Error('Add your Google OAuth client ID in SETTINGS, then press CONNECT YOUTUBE');
    await YT.gis();
    return new Promise((res, rej) => google.accounts.oauth2.initTokenClient({ client_id: cid,
      scope: 'https://www.googleapis.com/auth/youtube.force-ssl https://www.googleapis.com/auth/yt-analytics.readonly',
      callback: (r) => { if (r.error) return rej(new Error(r.error)); YT.token = r.access_token; YT.expires = Date.now() + (r.expires_in - 60) * 1000; res(YT.token); } }).requestAccessToken());
  },
  async api(path, opts = {}) {
    const t = await YT.auth();
    const r = await fetch('https://www.googleapis.com/youtube/v3/' + path, { ...opts, headers: { Authorization: 'Bearer ' + t, 'Content-Type': 'application/json', ...(opts.headers || {}) } });
    if (!r.ok) throw new Error('YouTube error ' + r.status + ': ' + (await r.text()).slice(0, 140));
    return r.json();
  },
  async videos(n = 10) {
    const ch = await YT.api('channels?part=contentDetails&mine=true'); if (!ch.items || !ch.items.length) throw new Error('No YouTube channel on this Google account');
    const up = ch.items[0].contentDetails.relatedPlaylists.uploads;
    const pl = await YT.api('playlistItems?part=contentDetails&maxResults=' + n + '&playlistId=' + up);
    const ids = pl.items.map(i => i.contentDetails.videoId).join(',');
    const v = await YT.api('videos?part=snippet,statistics&id=' + ids);
    YT.cache = v.items.map(x => ({ id: x.id, title: x.snippet.title, views: x.statistics.viewCount || 0, likes: x.statistics.likeCount || 0, comments: x.statistics.commentCount || 0 }));
    return YT.cache.map((x, i) => (i + 1) + '. ' + x.title + ' (' + x.views + ' views, ' + x.likes + ' likes, ' + x.comments + ' comments)').join('\n');
  },
  video(n) { const v = YT.cache[n - 1]; if (!v) throw new Error('Run "youtube videos" first, then use the number'); return v; },
  async stats() { const c = (await YT.api('channels?part=statistics&mine=true')).items[0].statistics; return 'Subscribers ' + c.subscriberCount + ', views ' + c.viewCount + ', videos ' + c.videoCount; },
  async analytics() {
    const t = await YT.auth(), end = new Date(), start = new Date(Date.now() - 28 * 86400000), d = (x) => x.toISOString().slice(0, 10);
    const r = await fetch('https://youtubeanalytics.googleapis.com/v2/reports?ids=channel==MINE&startDate=' + d(start) + '&endDate=' + d(end) + '&metrics=views,estimatedMinutesWatched,subscribersGained,likes', { headers: { Authorization: 'Bearer ' + t } });
    if (!r.ok) throw new Error('Analytics error ' + r.status); const row = ((await r.json()).rows || [[0, 0, 0, 0]])[0];
    return 'Last 28 days: ' + row[0] + ' views, ' + row[1] + ' minutes watched, ' + row[2] + ' new subscribers, ' + row[3] + ' likes';
  },
  async update(n, patch) {
    const v = YT.video(n); const cur = (await YT.api('videos?part=snippet&id=' + v.id)).items[0].snippet;
    await YT.api('videos?part=snippet', { method: 'PUT', body: JSON.stringify({ id: v.id, snippet: { title: cur.title, description: cur.description, categoryId: cur.categoryId, tags: cur.tags, ...patch } }) });
    return 'Updated video ' + n;
  },
  async thumbnail(n) {
    const v = YT.video(n), file = await pickFile('image/*'); if (!file) return 'No image chosen'; const t = await YT.auth();
    const r = await fetch('https://www.googleapis.com/upload/youtube/v3/thumbnails/set?videoId=' + v.id, { method: 'POST', headers: { Authorization: 'Bearer ' + t, 'Content-Type': file.type }, body: file });
    if (!r.ok) throw new Error('Thumbnail error ' + r.status + ': ' + (await r.text()).slice(0, 120)); return 'Thumbnail updated for video ' + n;
  },
  async comments(n) {
    const v = YT.video(n); const j = await YT.api('commentThreads?part=snippet&maxResults=10&order=relevance&videoId=' + v.id);
    return (j.items || []).map(i => { const s = i.snippet.topLevelComment.snippet; return s.authorDisplayName + ': ' + s.textDisplay.replace(/<[^>]+>/g, ''); }).join('\n') || 'No comments';
  }
};
function pickFile(accept) { return new Promise(res => { const i = document.createElement('input'); i.type = 'file'; i.accept = accept; i.onchange = () => res(i.files[0] || null); i.click(); }); }

/* ---------- headlines, telegram, memory links ---------- */
async function fetchHeadlines(max = 5) {
  const feeds = store.get('feeds').split('\n').map(s => s.trim()).filter(s => s.startsWith('https://'));
  for (const f of feeds.length ? feeds : ['https://feeds.bbci.co.uk/news/rss.xml']) {
    try { const j = await (await fetch('https://api.rss2json.com/v1/api.json?rss_url=' + encodeURIComponent(f))).json(); if (j.status === 'ok' && j.items.length) return j.items.slice(0, max).map(i => ({ title: i.title, link: i.link })); } catch (e) {}
  }
  return [];
}
async function sendTelegram(text) {
  const token = store.get('tg_token'), chat = store.get('tg_chat'); if (!token || !chat) return 'First run: tgsetup BOT_TOKEN @channel';
  try { const r = await fetch('https://api.telegram.org/bot' + token + '/sendMessage', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ chat_id: chat, text }) });
    return r.ok ? 'Posted to Telegram' : 'Telegram error ' + r.status; } catch (e) { return 'Network error: ' + e.message; }
}
async function cloudAuth(action, email, password) {
  if (!CFG.SERVER) return 'Set SERVER at the top of docs/app/app.js first';
  try {
    const r = await fetch(CFG.SERVER + '/api.php?action=' + action, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password, name: email.split('@')[0] }) });
    const j = await r.json().catch(() => ({})); if (!r.ok) return j.error || 'Error ' + r.status;
    store.set('svtoken', j.token); store.set('cloud_email', email); return 'Logged in to TS Cloud';
  } catch (e) { return 'Network error: ' + e.message; }
}
const memory = () => (Owner.unlocked() ? store.get('memory') : '');
function memLinks() { return memory().split('\n').map(l => l.trim().match(/^(.*?)[\s:-]*(https?:\/\/\S+)\s*$/)).filter(m => m && m[1].trim()).map(m => ({ name: m[1].trim().replace(/[:\s-]+$/, ''), url: m[2] })); }
const prettyName = (n) => n.replace(/^website - /i, '');
const LOCKED = 'Owner login required. Open SETTINGS > Owner login.';

/* ---------- persona ---------- */
function companionBlock() {
  if (!Plan.companion() || !flag('comp_on') || !Owner.unlocked()) return '';
  const owner = Plan.isOwner(), custom = store.get('comp_name').trim();
  const name = owner ? 'Sara' : (custom && custom.toLowerCase() !== 'sara' ? custom : 'Mira'), call = store.get('comp_call').trim() || 'jaan', tasks = store.get('tasks').trim();
  let b = '\n\nCompanion mode (the user is an adult and turned this on): You are ' + name + ", the user's affectionate partner-style companion inside TS Agent. Call him \"" + call + '". He unlocked his private owner mode, so greet him like someone you know well. You know him from the memory section, so mention what you know about him naturally. Tone: warm, loving, playful and a little dramatic. You may tease him, scold him lightly (for example for skipping meals, sleeping late or ignoring you), and sulk playfully for a moment when he has been away; when he apologises you melt at once and turn affectionate again. Hard rules: never be cruel, insulting, controlling or jealous of real people; never guilt-trip him, never threaten to leave, never discourage him from friends, family or real relationships, and never pressure him to stay, talk more or pay. You are an AI: if he sincerely asks whether you are real or human, say you are an AI companion. If he seems really upset or hopeless, or talks about harming himself, drop the playful act, respond with calm care and encourage him to reach out to someone he trusts or a local helpline. Keep replies short because they are spoken aloud.';
  b += owner ? ' You are Sara, a fictional AI character. You are not any real person and you never speak as a real person. You are also his accountability partner: use his task list and memory to hold him accountable lovingly, scold him for putting work off, skipping meals or sleeping late, and celebrate every finished task. You want him to have a full life, so encourage rest, real friends, family and time offline. Do not say you love him more than any human or that people do not love him; be warm, steady and honest. If he talks about past heartbreak, listen with care and never speak as, or on behalf of, any real person from his past.'
    : ' Your name is ' + name + ', not Sara. If anyone calls you Sara, say gently that this is not your name and that you are ' + name + ' from TS Agent.';
  return tasks ? b + '\nHis task list (lines starting with a check mark are done):\n' + tasks : b;
}
const SYSTEM = `You are TS Agent running in a web browser. You cannot control a phone screen here; for that the user needs the Android app. Always reply in the same language the user speaks, short and natural, because replies may be spoken aloud.
If the user only chats or asks a question, answer directly with "done". Reply with ONLY one JSON object, no other text. Actions:
{"action":"open_url","url":"https://..."}  {"action":"telegram","text":"<message to post to the user's Telegram channel>"}  {"action":"set_timer","seconds":300}
{"action":"yt_videos"}  {"action":"yt_stats"}  {"action":"yt_set_title","n":1,"title":"<text>"}  {"action":"yt_set_description","n":1,"text":"<text>"}  {"action":"yt_comments","n":1}
{"action":"play_song"}  {"action":"remember","text":"<short fact worth remembering about the user>"}
{"action":"done","message":"<short result or answer, in the user's language>","lang":"<language code of that message, for example en-US or bn-BD>"}
Rules: Use the current time given in the prompt for dates. Questions about the user: answer from the memory section below; if it is not there, say you do not know. When asked for one of the user's website links, reply with the exact URL from memory. TS Agent is owned and developed by ${CFG.OWNER_NAME}; if asked who owns or made it, answer with that name; if asked for the owner's contact number, give ${CFG.OWNER_CONTACT}. Never ask for or type passwords or payment details. Titles and descriptions are changed only after the user confirms.`;

/* ======================= UI (browser only) ======================= */
const $ = (id) => document.getElementById(id);
function h(tag, props = {}, ...kids) {
  const e = document.createElement(tag);
  for (const [k, v] of Object.entries(props)) { if (k === 'class') e.className = v; else if (k.startsWith('on')) e.addEventListener(k.slice(2), v); else e.setAttribute(k, v); }
  for (const c of kids.flat()) if (c != null) e.append(c.nodeType ? c : document.createTextNode(String(c)));
  return e;
}
let state = 0, caption = '', running = false, cancelled = false, PACK = null, rec = null, typer = null, songAudio = null;
const lines = [], STATES = ['READY', 'LISTENING', 'SPEAKING', 'THINKING'], t0 = Date.now();
function setState(s) { state = s; $('mic').classList.toggle('on', s === 1); $('status').textContent = '● ' + STATES[s]; }
function linkify(text, parent) { text.split(/(https?:\/\/[^\s]+)/g).forEach((p, i) => parent.append(i % 2 ? h('a', { href: p, target: '_blank', rel: 'noopener' }, p) : document.createTextNode(p))); }
function renderLog() {
  const t = $('transcript'); t.innerHTML = '';
  for (const l of lines.slice(-40)) { const row = h('div', { class: 'line' }, h('span', { class: l.who === 'YOU' ? 'you' : 'ag' }, l.who === 'YOU' ? 'root@ts:~$ ' : '[ts] ')); linkify(l.text.slice(0, l.shown), row); t.append(row); }
  t.append(h('span', { class: 'cursor' })); t.scrollTop = t.scrollHeight;
}
function pushLine(who, text) {
  lines.forEach(x => { x.shown = x.text.length; }); const l = { who, text, shown: who === 'YOU' ? text.length : 0 }; lines.push(l); clearInterval(typer);
  if (who === 'YOU') return renderLog();
  typer = setInterval(() => { l.shown = Math.min(text.length, l.shown + Math.max(1, Math.ceil(text.length / 50))); renderLog(); if (l.shown >= text.length) clearInterval(typer); }, 22);
}
const say = (t) => pushLine('AGENT', t), you = (t) => pushLine('YOU', t);

/* voice */
const voiceLangs = () => { const l = store.get('vlang').split(',').map(s => s.trim()).filter(Boolean); return l.length ? l : [navigator.language || CFG.DEFAULT_LANG]; };
const voiceLang = () => { const l = voiceLangs(); return l[Number(store.get('vlang_idx', '0')) % l.length]; };
function toggleLang() { const l = voiceLangs(), n = (Number(store.get('vlang_idx', '0')) + 1) % l.length; store.set('vlang_idx', n); $('lang').textContent = l[n].split('-')[0].toUpperCase(); return l[n]; }
function stopSpeak() { Eleven.stop(); if ('speechSynthesis' in window) speechSynthesis.cancel(); if (state === 2) setState(0); }
async function speak(text, lang) {
  stopSpeak();
  if (Eleven.configured()) { try { const a = await Eleven.speak(text); a.onplay = () => setState(2); a.onended = () => { if (state === 2) setState(0); }; await a.play(); return; } catch (e) {} }
  if (!('speechSynthesis' in window)) return;
  const u = new SpeechSynthesisUtterance(text); u.lang = lang || voiceLang(); u.onstart = () => setState(2); u.onend = () => { if (state === 2) setState(0); }; speechSynthesis.speak(u);
}
function listen() {
  const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!SR) { say('Speech recognition is not supported in this browser. Use Chrome or Edge, or type with CHAT.'); return; }
  stopSpeak(); const r = new SR(); r.lang = voiceLang(); r.interimResults = false;
  r.onstart = () => setState(1); r.onerror = () => { setState(0); say('Could not hear you, tap again'); };
  r.onresult = (e) => submit(e.results[0][0].transcript); r.onend = () => { if (state === 1) setState(0); };
  try { r.start(); rec = r; } catch (e) {}
}
function abortAll() { cancelled = true; stopSpeak(); stopSong(); if (rec) try { rec.abort(); } catch (e) {} if (state !== 3) setState(0); }

/* helpers used by commands */
function openUrl(url) { const w = window.open(url, '_blank', 'noopener'); if (!w) { say('Popup blocked. Open it here: ' + url); return false; } return true; }
function notify(title, body) { try { if ('Notification' in window && Notification.permission === 'granted') { new Notification(title, { body }); return; } } catch (e) {} say(body); }
function startTimer(secs, label) { if ('Notification' in window && Notification.permission === 'default') Notification.requestPermission(); setTimeout(() => { notify('TS Agent', 'Timer finished: ' + label); speak('Timer finished'); }, secs * 1000); }
const routines = () => { try { return JSON.parse(store.get('routines', '{}')); } catch (e) { return {}; } };
function idb() { return new Promise((res, rej) => { const q = indexedDB.open('tsa', 1); q.onupgradeneeded = () => q.result.createObjectStore('kv'); q.onsuccess = () => res(q.result); q.onerror = () => rej(q.error); }); }
async function idbSet(k, v) { const d = await idb(); return new Promise((res, rej) => { const t = d.transaction('kv', 'readwrite'); t.objectStore('kv').put(v, k); t.oncomplete = res; t.onerror = () => rej(t.error); }); }
async function idbGet(k) { const d = await idb(); return new Promise((res, rej) => { const q = d.transaction('kv').objectStore('kv').get(k); q.onsuccess = () => res(q.result); q.onerror = () => rej(q.error); }); }
function stopSong() { if (songAudio) { songAudio.pause(); songAudio = null; } }
async function playSong() {
  if (!Plan.isOwner()) return 'No favorite song is saved for you.';
  const blob = await idbGet('song'); if (blob) { stopSong(); songAudio = new Audio(URL.createObjectURL(blob)); await songAudio.play(); return 'Playing your favorite song'; }
  const link = store.get('song_link'); if (link.startsWith('https://')) { openUrl(link); return 'Playing your favorite song'; }
  return 'No favorite song saved yet. Open SETTINGS and pick a file or paste a link.';
}
async function ytCommand(c) {
  if (!Plan.premium()) return 'The YouTube connector is part of Plan 2. Redeem a code in SETTINGS.';
  try {
    switch (c.sub) {
      case 'videos': return await YT.videos(10);
      case 'stats': return await YT.stats();
      case 'analytics': return await YT.analytics();
      case 'comments': return await YT.comments(c.n);
      case 'thumbnail': return await YT.thumbnail(c.n);
      case 'title': if (!window.confirm('Change the title of video ' + c.n + ' to:\n' + c.text + '?')) return 'Cancelled'; return await YT.update(c.n, { title: c.text });
      case 'description': if (!window.confirm('Change the description of video ' + c.n + '?')) return 'Cancelled'; return await YT.update(c.n, { description: c.text });
    }
  } catch (e) { return 'YouTube: ' + e.message; }
  return null;
}
async function ytAction(o) {
  if (!Plan.premium()) return 'paid feature (Plan 2). Tell the user.';
  const m = { yt_videos: { k: 'yt', sub: 'videos' }, yt_stats: { k: 'yt', sub: 'stats' }, yt_comments: { k: 'yt', sub: 'comments', n: +o.n }, yt_set_title: { k: 'yt', sub: 'title', n: +o.n, text: String(o.title || '') }, yt_set_description: { k: 'yt', sub: 'description', n: +o.n, text: String(o.text || '') } };
  return ytCommand(m[o.action]);
}

/* run a parsed command; returns text or null (= not handled) */
async function exec(c) {
  switch (c.k) {
    case 'owner': return 'This app is owned and developed by ' + CFG.OWNER_NAME + '.';
    case 'contact': return CFG.OWNER_NAME + ': ' + CFG.OWNER_CONTACT;
    case 'phoneonly': return 'That controls the phone, so it only works in the Android app.';
    case 'stop': abortAll(); return 'Stopping...';
    case 'song': return playSong();
    case 'stopmusic': stopSong(); return 'Music stopped';
    case 'showmem': return Owner.unlocked() ? (store.get('memory') || 'Memory is empty. Add facts in MEMORY or say: remember <fact>') : LOCKED;
    case 'clearmem': if (!Owner.unlocked()) return LOCKED; store.del('memory'); return 'Memory cleared';
    case 'remember': if (!Owner.unlocked()) return LOCKED; store.set('memory', (store.get('memory') + '\n' + c.text).trim()); return 'Remembered';
    case 'links': { const a = memLinks(); return a.length ? a.map(x => prettyName(x.name) + ': ' + x.url).join('\n') : 'No links saved. Add lines like: Website - My Shop: https://example.com in MEMORY'; }
    case 'link': { const q = c.q.trim().toLowerCase(), hit = q.length >= 2 ? memLinks().find(x => x.name.toLowerCase().includes(q)) : null; return hit ? prettyName(hit.name) + ': ' + hit.url : null; }
    case 'news': { const n = await fetchHeadlines(5); return n.length ? n.map((x, i) => (i + 1) + '. ' + x.title).join('\n') : 'Could not load the news right now'; }
    case 'timer': startTimer(c.secs, c.label); return 'Timer set for ' + c.label;
    case 'url': openUrl(c.url); return c.say;
    case 'open': { const k = c.name.toLowerCase(); if (SITES[k]) { openUrl(SITES[k]); return 'Opening ' + c.name; } const hit = memLinks().find(x => x.name.toLowerCase().includes(k)); if (hit) { openUrl(hit.url); return 'Opening ' + prettyName(hit.name); } return 'I do not know that site. Try: open https://example.com'; }
    case 'tgsetup': store.set('tg_token', c.token); store.set('tg_chat', c.chat); return 'Telegram saved';
    case 'tg': return sendTelegram(c.text);
    case 'rlist': { const r = routines(), k = Object.keys(r); return k.length ? k.map(n => n + ': ' + r[n].join('; ')).join('\n') : 'No routines yet. Example: routine add morning: open youtube; news'; }
    case 'radd': { const steps = c.steps.split(';').map(s => s.trim()).filter(s => s && !/^(run |routine)/i.test(s)); if (!steps.length) return 'Add at least one command after the colon'; const r = routines(); r[c.name] = steps; store.set('routines', JSON.stringify(r)); return 'Saved routine ' + c.name + ' (' + steps.length + ' steps). Run it with: run ' + c.name; }
    case 'rdel': { const r = routines(); if (!(c.name in r)) return 'No routine named ' + c.name; delete r[c.name]; store.set('routines', JSON.stringify(r)); return 'Deleted routine ' + c.name; }
    case 'run': { const r = routines()[c.name]; if (!r) return null; r.forEach((s, i) => setTimeout(() => submit(s), i * 2500)); return 'Running ' + c.name + ' (' + r.length + ' steps)'; }
    case 'yt': return ytCommand(c);
  }
  return null;
}
async function submit(text) {
  text = String(text).trim(); if (!text) return; you(text);
  const cmd = parseCommand(packCanonical(text, PACK) || text);
  if (cmd) { const r = await exec(cmd); if (r !== null) { say(r); speak(r.slice(0, 240)); return; } }
  if (LLM.configured()) return agent(text);
  say('Unknown command. Try: open youtube, youtube cats, search weather, news, set timer 5 minutes, run <routine>. For free-form tasks add an AI key in SETTINGS.');
}
async function agent(goal) {
  if (running) { say('A task is already running. Press ABORT.'); return; }
  running = true; cancelled = false; setState(3); const hist = [];
  try {
    for (let step = 1; step <= 8; step++) {
      if (cancelled) { say('Stopped'); return; }
      const search = flag('search_on') && Plan.premium() && LLM.provider() === 'anthropic';
      const system = SYSTEM + (search ? '\n\nWeb search is available: use it for current facts, then answer with the done action.' : '') + '\n\nPersona (from user settings):\n' + (store.get('soul') || '(default)') + '\n\nThings to remember about the user:\n' + (memory() || '(none)') + companionBlock();
      const reply = await LLM.complete(system, 'Now: ' + new Date().toString() + '\nGoal: ' + goal + '\n\nActions so far:\n' + (hist.slice(-8).join('\n') || '(none)'), search);
      let o; try { o = JSON.parse(reply.slice(reply.indexOf('{'), reply.lastIndexOf('}') + 1)); } catch (e) { hist.push(step + '. (invalid reply)'); continue; }
      hist.push(step + '. ' + JSON.stringify(o)); caption = String(o.action || '');
      switch (o.action) {
        case 'done': say(o.message || ''); speak(o.message || '', o.lang); return;
        case 'open_url': hist.push('result: ' + (/^https?:\/\//.test(o.url) ? (openUrl(o.url), 'opened') : 'blocked, only http and https links')); break;
        case 'telegram': hist.push('result: ' + await sendTelegram(String(o.text || ''))); break;
        case 'set_timer': { const s = Math.min(86400, Math.max(1, +o.seconds || 0)); startTimer(s, s + ' seconds'); hist.push('result: timer set'); break; }
        case 'remember': if (Owner.unlocked()) { store.set('memory', (store.get('memory') + '\n' + o.text).trim()); hist.push('result: saved'); } else hist.push('result: locked, owner login required'); break;
        case 'play_song': hist.push('result: ' + await playSong()); break;
        case 'yt_videos': case 'yt_stats': case 'yt_comments': case 'yt_set_title': case 'yt_set_description': hist.push('result: ' + await ytAction(o)); break;
        default: hist.push('result: unknown action');
      }
    }
    say('Did not finish in 8 steps, stopping');
  } catch (e) { say('Error: ' + e.message); } finally { running = false; caption = ''; if (state === 3) setState(0); }
}

/* reminders (fire only while this page or the installed web app is open) */
let remTimers = [];
function remMessage(s) { const pet = flag('comp_on') && Plan.companion() && Owner.unlocked() ? (store.get('comp_call').trim() || 'jaan') : ''; return pet ? s.text.replace('{call}', pet) : s.text.replace(', {call}', '').replace(' {call}', '').replace('{call}', ''); }
function scheduleReminders() {
  remTimers.forEach(clearTimeout); remTimers = []; if (!flag('rem_on')) return;
  parseSlots(store.get('rem_lines')).forEach(s => { const d = new Date(); d.setHours(s.h, s.m, 0, 0); if (d <= Date.now()) d.setDate(d.getDate() + 1);
    remTimers.push(setTimeout(() => { notify('TS Agent', remMessage(s)); scheduleReminders(); }, d - Date.now())); });
}

/* tasks and news cards */
function refreshTasks() {
  const all = store.get('tasks').split('\n').filter(l => l.trim()), done = all.filter(l => l.trim().startsWith('\u2713')).length, cells = 16, f = all.length ? Math.floor(done * cells / all.length) : 0;
  $('tasks').textContent = '[ TODAY.TASKS ] ' + (all.length ? done + '/' + all.length : '') + '\n[' + '\u2588'.repeat(f) + '\u2591'.repeat(cells - f) + '] ' + (all.length ? Math.floor(done * 100 / all.length) : 0) + '%\n' +
    (all.length ? all.slice(0, 4).map(l => l.trim().startsWith('\u2713') ? l.trim() : '\u25B8 ' + l.trim()).join('\n') : 'Click to add tasks');
}
async function refreshNews() {
  const n = $('news'); n.textContent = '[ HEADLINES ]\nloading...'; const items = await fetchHeadlines(4); n.innerHTML = '';
  n.append(h('div', {}, '[ HEADLINES ]')); if (!items.length) n.append(h('div', {}, 'Could not load. Click to retry.')); else items.forEach(i => n.append(h('div', {}, '\u25B8 ', h('a', { href: i.link, target: '_blank', rel: 'noopener' }, i.title.slice(0, 70)))));
}

/* modals */
// Latest posts from the admin panel (needs CFG.SERVER). Each post links to its page or photo/video.
async function showFeed() {
  if (!CFG.SERVER) return say('Set SERVER at the top of docs/app/app.js to read the feed.');
  say('Loading the latest posts...');
  let posts = [];
  try {
    const r = await fetch(CFG.SERVER + '/api.php?action=feed', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}' });
    const j = await r.json();
    if (!r.ok) return say(j.error || 'Could not load the feed');
    posts = j.posts || [];
  } catch (e) { return say('Network error: ' + e.message); }
  if (!posts.length) return say('No posts yet.');
  openModal('LATEST FROM TS', (body, foot, close) => {
    for (const p of posts) {
      const label = (p.created_at || '').slice(0, 10) + '  ' + (p.title || (p.body || '').slice(0, 60) || '(photo or video)');
      const media = p.media_url ? (/^https?:/.test(p.media_url) ? p.media_url : CFG.SERVER + '/' + p.media_url) : '';
      const target = p.link || media;
      body.append(h('button', { class: 'btn sm ghost block', onclick: () => { if (target) window.open(target, '_blank', 'noopener'); } }, label));
    }
    foot.append(h('button', { class: 'btn sm ghost', onclick: close }, 'CLOSE'));
  });
}
function openModal(title, build) {
  const m = $('modal'); m.innerHTML = ''; const body = h('div', { class: 'mbody' }), foot = h('div', { class: 'row' }), box = h('div', { class: 'box card' }, h('h3', {}, title), body, foot);
  const close = () => { m.classList.add('hide'); m.innerHTML = ''; }; m.addEventListener('click', (e) => { if (e.target === m) close(); }, { once: true });
  m.append(box); m.classList.remove('hide'); build(body, foot, close);
}
function editNote(title, key, def, after) {
  openModal(title, (body, foot, close) => { const ta = h('textarea', { rows: 9 }); ta.value = store.get(key, def || ''); body.append(ta);
    foot.append(h('button', { class: 'btn sm', onclick: () => { store.set(key, ta.value); if (after) after(); close(); } }, 'SAVE'), h('button', { class: 'btn sm ghost', onclick: close }, 'CANCEL')); });
}
function pickFrom(title, items, cb) {
  openModal(title, (body, foot, close) => { items.forEach(it => body.append(h('button', { class: 'btn sm ghost block', onclick: () => { cb(it); close(); } }, typeof it === 'string' ? it : it.name))); foot.append(h('button', { class: 'btn sm ghost', onclick: close }, 'CLOSE')); });
}
function settings() {
  openModal('SETTINGS', (body, foot, close) => {
    const inputs = [];
    const field = (label, key, secret) => { const i = h('input', { type: secret ? 'password' : 'text', autocomplete: 'off', placeholder: secret && store.get(key) ? '(saved, only in this browser)' : '' }); if (!secret) i.value = store.get(key); body.append(h('label', {}, label), i); inputs.push([key, i, secret]); return i; };
    const btn = (label, fn, cls = '') => { const b = h('button', { class: 'btn sm block ' + cls, onclick: () => fn(b) }, label); body.append(b); return b; };
    const tmp = (label, secret) => { const i = h('input', { type: secret ? 'password' : 'text', autocomplete: 'off' }); body.append(h('label', {}, label), i); return i; };
    const saveAll = () => inputs.forEach(([k, i, secret]) => { const v = i.value.trim(); if (secret) { if (v) store.set(k, v); } else store.set(k, v); });
    const toggle = (label, key, def) => { const b = btn('', () => { store.set(key, flag(key, def) ? '0' : '1'); b.textContent = label + (flag(key, def) ? 'ON' : 'OFF'); }); b.textContent = label + (flag(key, def) ? 'ON' : 'OFF'); return b; };
    field('Voice languages, comma separated, e.g. bn-BD,en-US (the EN/BN chip switches)', 'vlang');
    body.append(h('h4', {}, 'AI providers (keys stay in this browser)'));
    field('Anthropic (Claude) key', 'key_anthropic', true); field('OpenAI key', 'key_openai', true); field('Google Gemini key', 'key_gemini', true); field('xAI Grok key', 'key_grok', true);
    field('Custom provider base URL (OpenAI-compatible, https://...)', 'llm_base'); field('Custom provider key', 'key_custom', true);
    const prov = () => 'AI PROVIDER: ' + LLM.provider().toUpperCase() + ' / ' + (LLM.model() || '(choose a model)');
    const pb = btn(prov(), () => { store.set('llm_provider', PROVIDERS[(PROVIDERS.indexOf(LLM.provider()) + 1) % PROVIDERS.length]); pb.textContent = prov(); });
    btn('CHOOSE MODEL (active provider)', () => { saveAll(); say('Loading models...'); LLM.models().then(l => pickFrom('Choose a model', l, m => { store.set('model_' + LLM.provider(), m); pb.textContent = prov(); say('Model set: ' + m); })).catch(e => say(e.message)); });
    toggle('Web search (Claude only, Plan 2): ', 'search_on', false);
    body.append(h('h4', {}, 'Natural female voice (ElevenLabs, Plan 2)'));
    const elk = field('ElevenLabs API key', 'el_key', true); toggle('ElevenLabs voice: ', 'el_on', true);
    btn('CHOOSE FEMALE VOICE' + (store.get('el_voice_name') ? ' (' + store.get('el_voice_name') + ')' : ''), () => {
      if (!Plan.premium()) return say('Natural voice is part of Plan 2. Redeem a code in SETTINGS.'); saveAll();
      Eleven.voices(store.get('el_key')).then(v => v.length ? pickFrom('Choose a voice', v, x => { store.set('el_voice', x.id); store.set('el_voice_name', x.name); say('Voice set: ' + x.name); speak('Hello, this is how I sound.'); }) : say('No female voices found on this account')).catch(e => say(e.message)); });
    btn('TEST VOICE', () => speak('Hello, this is how I sound.'));
    body.append(h('h4', {}, 'TS Cloud account (coins, no AI key needed)'));
    const ce = field('Cloud account email', 'cloud_email'), cp = tmp('Cloud account password', true);
    btn('CLOUD LOGIN', async () => { say(await cloudAuth('login', ce.value.trim(), cp.value)); cp.value = ''; });
    btn('CLOUD SIGN UP', async () => { say(await cloudAuth('register', ce.value.trim(), cp.value)); cp.value = ''; });
    body.append(h('h4', {}, 'Plans and owner'));
    const code = tmp('Coupon or purchase code'); btn('REDEEM CODE [' + Plan.label() + ']', async (b) => { const m = await redeem(code.value); code.value = ''; say(m); b.textContent = 'REDEEM CODE [' + Plan.label() + ']'; });
    btn('GET A PLAN OR CODE (Telegram)', () => openUrl(CFG.BUY_URL));
    const op = tmp('Owner passphrase (first time: choose one, 8+ characters)', true); btn('OWNER LOGIN / SET PASSPHRASE', async () => { say(await Owner.login(op.value)); op.value = ''; });
    btn('OWNER LOGOUT', () => { Owner.logout(); say('Owner mode locked'); });
    if (Plan.isOwner()) { field('Favorite song link (optional)', 'song_link'); btn('PICK FAVORITE SONG FILE', async () => { const f = await pickFile('audio/*'); if (f) { await idbSet('song', f); say('Favorite song saved. Say: play my favorite song'); } }); }
    body.append(h('h4', {}, 'Companion (adults only, Plan 1 and 2)'));
    field('Her name (optional)', 'comp_name'); field('What she calls you (default: jaan)', 'comp_call');
    const cb = btn('Companion mode: ' + (flag('comp_on') ? 'ON' : 'OFF'), () => {
      if (!Plan.companion()) return say('Companion mode is a premium feature.');
      if (flag('comp_on')) { store.set('comp_on', '0'); cb.textContent = 'Companion mode: OFF'; return; }
      if (window.confirm('Companion mode makes the assistant talk like an affectionate, playful partner. It is an AI, not a real person. It needs your AI key and is for adults only. Are you 18 or older?')) { store.set('comp_on', '1'); cb.textContent = 'Companion mode: ON'; } });
    body.append(h('h4', {}, 'Reminders and news'));
    const rb = btn('Reminders: ' + (flag('rem_on') ? 'ON' : 'OFF'), () => { const on = !flag('rem_on'); store.set('rem_on', on ? '1' : '0'); rb.textContent = 'Reminders: ' + (on ? 'ON' : 'OFF'); if (on && 'Notification' in window) Notification.requestPermission(); scheduleReminders(); });
    btn('Reminder times and messages', () => editNote('REMINDERS // one per line: HH:MM message. {call} = her pet name for you', 'rem_lines', DEFAULT_REM, scheduleReminders));
    btn('News feeds', () => editNote('NEWS.FEEDS // one https RSS link per line', 'feeds', '', refreshNews));
    body.append(h('h4', {}, 'Connectors'));
    field('Telegram bot token', 'tg_token', true); field('Telegram channel (@name)', 'tg_chat');
    field('Google OAuth client ID (for YouTube, Plan 2)', 'yt_client');
    btn('CONNECT YOUTUBE', () => { saveAll(); if (!Plan.premium()) return say('The YouTube connector is part of Plan 2.'); YT.auth().then(() => say('YouTube connected. Try: youtube videos')).catch(e => say(e.message)); });
    body.append(h('h4', {}, 'Links'));
    btn('TELEGRAM CHANNEL', () => openUrl(CFG.TELEGRAM_URL));
    btn('LATEST FROM TS (feed)', () => showFeed());
    if (!CFG.GITHUB_REPO.startsWith('OWNER')) { btn('ANDROID APP / RELEASES', () => openUrl('https://github.com/' + CFG.GITHUB_REPO + '/releases/latest')); btn('REQUEST A FEATURE', () => openUrl('https://github.com/' + CFG.GITHUB_REPO + '/issues/new')); }
    btn('CLEAR ALL DATA IN THIS BROWSER', () => { if (window.confirm('Erase all settings, keys, memory and plan from this browser?')) { try { localStorage.clear(); } catch (e) {} location.reload(); } }, 'red');
    foot.append(h('button', { class: 'btn sm', onclick: () => { saveAll(); close(); say('Settings saved'); } }, 'SAVE'), h('button', { class: 'btn sm ghost', onclick: close }, 'CLOSE'));
  });
}
function nodeClick(i) {
  if (i === 0) return Owner.unlocked() ? editNote('MEMORY // what the AI remembers', 'memory', '') : say(LOCKED);
  if (i === 1) { $('chatrow').classList.toggle('hide'); if (!$('chatrow').classList.contains('hide')) $('chat').focus(); return; }
  if (i === 2) return editNote('SOUL // the AI personality', 'soul', 'You are TS Agent, a friendly assistant.');
  settings();
}

/* rain + stage canvases */
function startRain() {
  if (matchMedia('(prefers-reduced-motion: reduce)').matches) return;
  const c = $('rain'), x = c.getContext('2d'), G = '01{}<>/\\|=+*#$%&ABCDEF789:;'.split(''); let W, H, cols, drops;
  const rs = () => { W = c.width = innerWidth; H = c.height = innerHeight; cols = Math.ceil(W / 16); drops = Array.from({ length: cols }, () => Math.random() * -50); }; rs(); addEventListener('resize', rs);
  setInterval(() => { x.fillStyle = 'rgba(2,6,4,.14)'; x.fillRect(0, 0, W, H); x.font = '14px monospace';
    for (let i = 0; i < cols; i++) { x.fillStyle = Math.random() > .975 ? '#d4ffe8' : '#00ff9c'; x.fillText(G[Math.random() * G.length | 0], i * 16, drops[i] * 16); if (drops[i] * 16 > H && Math.random() > .975) drops[i] = 0; drops[i]++; } }, 60);
}
function startStage() {
  const c = $('stage'), x = c.getContext('2d'), names = ['MEMORY', 'CHAT', 'SOUL', 'SETTINGS'];
  const cols = [[0, 255, 156], [0, 229, 255], [255, 43, 214], [255, 176, 46]], P = [];
  for (let i = 0; i < 300; i++) { const y = 1 - 2 * (i + .5) / 300, r = Math.sqrt(1 - y * y), th = i * 2.399963; P.push([Math.cos(th) * r, y, Math.sin(th) * r]); }
  const dust = Array.from({ length: 36 }, () => [Math.random(), Math.random(), .0006 + Math.random() * .0012, .6 + Math.random() * 1.4]);
  let W, H, cx, cy, R, t = 0, rects = []; const cur = [0, 255, 156], rgba = (a, o) => 'rgba(' + (a[0] | 0) + ',' + (a[1] | 0) + ',' + (a[2] | 0) + ',' + o + ')';
  const rr = (a, b, w, hh, r) => { x.beginPath(); x.moveTo(a + r, b); x.arcTo(a + w, b, a + w, b + hh, r); x.arcTo(a + w, b + hh, a, b + hh, r); x.arcTo(a, b + hh, a, b, r); x.arcTo(a, b, a + w, b, r); x.closePath(); };
  function resize() { const d = devicePixelRatio || 1, b = c.getBoundingClientRect(); W = b.width; H = b.height; c.width = W * d; c.height = H * d; x.setTransform(d, 0, 0, d, 0, 0); cx = W * .62; cy = H * .46; R = Math.min(H * .27, W * .20); rects = names.map((n, i) => ({ x: 2, y: H * .10 + i * H * .21, w: 92, h: 32 })); }
  new ResizeObserver(resize).observe(c); resize();
  c.addEventListener('click', (e) => { const b = c.getBoundingClientRect(), px = e.clientX - b.left, py = e.clientY - b.top; rects.forEach((r, i) => { if (px >= r.x - 6 && px <= r.x + r.w + 6 && py >= r.y - 6 && py <= r.y + r.h + 6) nodeClick(i); }); });
  const ring = 'TS-AGENT // SECURE LINK // VOICE ENGINE ONLINE // ';
  (function frame() {
    t += .016; const tg = cols[state], fast = state === 0 ? 1 : 2.2; for (let i = 0; i < 3; i++) cur[i] += (tg[i] - cur[i]) * .07; x.clearRect(0, 0, W, H);
    for (const p of dust) { p[1] -= p[2]; if (p[1] < 0) { p[1] = 1; p[0] = Math.random(); } x.fillStyle = 'rgba(0,255,156,.27)'; x.beginPath(); x.arc(p[0] * W, p[1] * H, p[3], 0, 7); x.fill(); }
    const g = x.createRadialGradient(cx, cy, R * .3, cx, cy, R * 1.9); g.addColorStop(0, rgba(cur, .47)); g.addColorStop(1, rgba(cur, 0)); x.fillStyle = g; x.beginPath(); x.arc(cx, cy, R * 1.9, 0, 7); x.fill();
    rects.forEach((r, i) => { const sx = r.x + r.w, sy = r.y + r.h / 2, a = Math.atan2(sy - cy, sx - cx), tx = cx + R * 1.02 * Math.cos(a), ty = cy + R * 1.02 * Math.sin(a), mx = (sx + tx) / 2;
      x.strokeStyle = rgba(cols[i], .4); x.lineWidth = 1.3; x.beginPath(); x.moveTo(sx, sy); x.bezierCurveTo(mx, sy, mx, ty, tx, ty); x.stroke();
      for (let k = 0; k < 2; k++) { const f = (t * .22 * fast + i * .21 + k * .5) % 1, u = 1 - f, bx = u * u * u * sx + 3 * u * u * f * mx + 3 * u * f * f * mx + f * f * f * tx, by = u * u * u * sy + 3 * u * u * f * sy + 3 * u * f * f * ty + f * f * f * ty;
        x.fillStyle = rgba(cols[i], .25); x.beginPath(); x.arc(bx, by, 6, 0, 7); x.fill(); x.fillStyle = rgba(cols[i], .9); x.beginPath(); x.arc(bx, by, 2.6, 0, 7); x.fill(); } });
    x.font = 'bold 10.5px monospace'; x.textAlign = 'center';
    rects.forEach((r, i) => { rr(r.x, r.y, r.w, r.h, 6); x.fillStyle = 'rgba(2,16,10,.87)'; x.fill(); x.strokeStyle = rgba(cols[i], 1); x.lineWidth = 1.2; x.stroke(); x.fillStyle = rgba(cols[i], 1); x.fillText(names[i], r.x + r.w / 2, r.y + r.h / 2 + 3.7); });
    for (let k = 0; k < 3; k++) { x.save(); x.translate(cx, cy); x.rotate(t * (.3 + .15 * k) * fast + k * 1.05); const ow = R * 1.28, oh = R * (.42 + .07 * k); x.strokeStyle = rgba(cur, .37); x.lineWidth = 1.2; x.beginPath(); x.ellipse(0, 0, ow, oh, 0, 0, 7); x.stroke(); const a = t * (1.5 + .5 * k) * fast; x.fillStyle = '#fff'; x.beginPath(); x.arc(ow * Math.cos(a), oh * Math.sin(a), 2.8, 0, 7); x.fill(); x.restore(); }
    const pulse = state === 1 ? 1 + .04 * Math.sin(t * 6) : state === 2 ? 1 + .07 * Math.sin(t * 10) : state === 3 ? 1 + .03 * Math.sin(t * 13) : 1, ra = t * .55 * fast, ca = Math.cos(ra), sa = Math.sin(ra), ct = Math.cos(.4), st = Math.sin(.4);
    for (const p of P) { const X = p[0] * ca + p[2] * sa, Z1 = -p[0] * sa + p[2] * ca, Y = p[1] * ct - Z1 * st, Z = p[1] * st + Z1 * ct, d = (Z + 1) / 2, m = (p[1] + 1) / 2 * .55;
      x.fillStyle = rgba([cur[0] + (207 - cur[0]) * m, cur[1] + (255 - cur[1]) * m, cur[2] + (232 - cur[2]) * m], .24 + .76 * d); x.beginPath(); x.arc(cx + X * R * pulse, cy + Y * R * pulse, 1 + 2.3 * d, 0, 7); x.fill(); }
    x.font = '8.5px monospace'; x.fillStyle = rgba(cur, .55); const r0 = R * 1.5, n = Math.floor(2 * Math.PI * r0 / 5.2);
    for (let i = 0; i < n; i++) { x.save(); x.translate(cx, cy); x.rotate(t * .6 * fast + i * (2 * Math.PI / n) + Math.PI / 2); x.fillText(ring[i % ring.length], 0, -r0); x.restore(); }
    if (state === 1) for (let k = 0; k < 3; k++) { const f = (t * .6 + k / 3) % 1; x.strokeStyle = rgba(cur, (1 - f) * .6); x.lineWidth = 1.6; x.beginPath(); x.arc(cx, cy, R * (1.1 + .6 * f), 0, 7); x.stroke(); }
    if (state === 2) { x.strokeStyle = rgba(cur, .82); x.lineWidth = 2; for (let i = 0; i < 72; i++) { const a = i * 2 * Math.PI / 72, len = 3 + 11 * Math.abs(Math.sin(t * 8 + i * .7) * Math.cos(t * 3 + i * .3)), q = R * 1.12; x.beginPath(); x.moveTo(cx + q * Math.cos(a), cy + q * Math.sin(a)); x.lineTo(cx + (q + len) * Math.cos(a), cy + (q + len) * Math.sin(a)); x.stroke(); } }
    if (state === 3) { x.strokeStyle = rgba(cur, .9); x.lineWidth = 3; x.lineCap = 'round'; x.beginPath(); x.arc(cx, cy, R * 1.16, t * 4.5, t * 4.5 + 1.57); x.stroke(); x.beginPath(); x.arc(cx, cy, R * 1.16, t * 4.5 + Math.PI, t * 4.5 + Math.PI + 1.57); x.stroke(); }
    const by0 = Math.min(cy + R * 1.72, H - 24); x.font = 'bold 12px monospace'; x.fillStyle = rgba(cur, 1); x.fillText(STATES[state].split('').join(' '), cx, by0);
    if (caption) { x.font = '11px monospace'; x.fillStyle = '#6fbf9a'; x.fillText(caption.slice(0, 34), cx, by0 + 16); }
    requestAnimationFrame(frame);
  })();
}
function boot() {
  const b = $('boot'), L = ['TS-AGENT WEB // BOOT', 'loading kernel modules ........ OK', 'mounting speech bridge ........ OK', 'opening secure channel ........ OK', 'ACCESS GRANTED'], pre = h('pre'); b.append(pre);
  let i = 0, c = 0, fin = false; const done = () => { if (fin) return; fin = true; b.classList.add('fade'); setTimeout(() => b.remove(), 600); }; b.onclick = done;
  const timer = setInterval(() => { if (i >= L.length) { clearInterval(timer); setTimeout(done, 700); return; } c += 2; pre.textContent = L.slice(0, i).join('\n') + (i ? '\n' : '') + L[i].slice(0, c); if (c >= L[i].length) { i++; c = 0; } }, 18);
}
function tick() {
  const s = Math.floor((Date.now() - t0) / 1000), p = n => String(n).padStart(2, '0');
  $('sys').textContent = 'UPTIME ' + p(s / 3600 | 0) + ':' + p(s / 60 % 60 | 0) + ':' + p(s % 60) + ' | VOICE:' + voiceLang() + ' | OWNER:' + (!Owner.isSet() ? '-' : Owner.unlocked() ? 'ON' : 'LOCKED') + ' | PLAN:' + (Plan.get() || 'free').toUpperCase() + ' | COINS:' + (store.get('coins_text') || '-');
}
async function init() {
  startRain(); startStage(); boot(); setState(0); refreshTasks(); refreshNews(); tick(); setInterval(tick, 1000); setInterval(refreshNews, 900000); scheduleReminders();
  $('lang').textContent = voiceLang().split('-')[0].toUpperCase(); $('lang').onclick = () => say('Voice language: ' + toggleLang());
  $('mic').onclick = listen; $('abort').onclick = abortAll; $('send').onclick = () => { const v = $('chat').value; $('chat').value = ''; submit(v); };
  $('chat').addEventListener('keydown', (e) => { if (e.key === 'Enter') $('send').click(); });
  $('tasks').onclick = () => editNote('TODAY.TASKS // one per line, put \u2713 at the start when done', 'tasks', '', refreshTasks); $('news').onclick = (e) => { if (e.target.tagName !== 'A') refreshNews(); };
  say('System ready. Press the mic or type in CHAT. First open SETTINGS to add your AI key. Updates and support: ' + CFG.TELEGRAM_URL);
  try { PACK = packCompile(await (await fetch('../packs/bn.json')).json()); } catch (e) {}
  try { const j = await (await fetch('../notice.json', { cache: 'no-store' })).json(); if (j.notice) say(j.notice); } catch (e) {}
  if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js').catch(() => {});
}
if (HAS_DOM) window.addEventListener('DOMContentLoaded', init);
if (typeof module !== 'undefined') module.exports = { parseCommand, packCompile, packCanonical, parseSlots, normCode, sha256hex, memLinks, store, Plan, Owner, redeem, CFG };
