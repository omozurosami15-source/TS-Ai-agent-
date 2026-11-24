<<<<<<< HEAD
const CACHE = 'tsa-v12', FILES = ['./', 'index.html', 'app.css', 'app.js', 'manifest.webmanifest', 'icon-192.png', 'icon-512.png'];
=======
<<<<<<< HEAD
const CACHE = 'tsa-v12', FILES = ['./', 'index.html', 'app.css', 'app.js', 'manifest.webmanifest', 'icon-192.png', 'icon-512.png'];
=======
const CACHE = 'tsa-v1', FILES = ['./', 'index.html', 'app.css', 'app.js', 'manifest.webmanifest', 'icon-192.png', 'icon-512.png'];
>>>>>>> 70ef1c274ce2bdca019764a62eb3127290033e2b
>>>>>>> d31f216a46405461342b68f7278a48ac4b80d297
self.addEventListener('install', e => { e.waitUntil(caches.open(CACHE).then(c => c.addAll(FILES))); self.skipWaiting(); });
self.addEventListener('activate', e => { e.waitUntil(caches.keys().then(k => Promise.all(k.filter(x => x !== CACHE).map(x => caches.delete(x))))); self.clients.claim(); });
self.addEventListener('fetch', e => {
  if (e.request.method !== 'GET' || new URL(e.request.url).origin !== location.origin) return;
  e.respondWith(fetch(e.request).then(r => { const copy = r.clone(); caches.open(CACHE).then(c => c.put(e.request, copy)); return r; }).catch(() => caches.match(e.request)));
});
