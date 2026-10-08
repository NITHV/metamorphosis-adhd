// Brain Dump service worker: lets the installed app open with no connection.
//
// - App code and icons (/_next/static, /icons): cache-first. Their file names change on
//   every deploy, so a cached copy is never stale.
// - Pages: network-first. A copy of each page you open is kept, so if you're offline the
//   last version still loads; dumps you make then wait in the on-device outbox.
// - API calls, Server Actions and data requests are never touched (they must be live).

const VERSION = "v1";
const STATIC_CACHE = `static-${VERSION}`;
const PAGES_CACHE = `pages-${VERSION}`;

self.addEventListener("install", () => self.skipWaiting());

self.addEventListener("activate", (event) => {
  event.waitUntil(
    (async () => {
      const keep = [STATIC_CACHE, PAGES_CACHE];
      for (const key of await caches.keys()) if (!keep.includes(key)) await caches.delete(key);
      await self.clients.claim();
    })(),
  );
});

const isStaticAsset = (url) =>
  url.pathname.startsWith("/_next/static/") ||
  url.pathname.startsWith("/icons/") ||
  /^\/(icon\.svg|apple-icon\.png|manifest\.webmanifest|whisper-worker\.js)$/.test(url.pathname);

self.addEventListener("fetch", (event) => {
  const { request } = event;
  if (request.method !== "GET") return;
  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;
  if (url.pathname.startsWith("/api/")) return;
  // React Server Component payloads: Next.js handles these (and retries them) itself.
  if (request.headers.has("RSC") || url.searchParams.has("_rsc")) return;

  if (isStaticAsset(url)) {
    event.respondWith(cacheFirst(request));
  } else if (request.mode === "navigate") {
    event.respondWith(networkFirstPage(request));
  }
});

async function cacheFirst(request) {
  const cache = await caches.open(STATIC_CACHE);
  const hit = await cache.match(request);
  if (hit) return hit;
  const response = await fetch(request);
  if (response.ok) cache.put(request, response.clone());
  return response;
}

async function networkFirstPage(request) {
  const cache = await caches.open(PAGES_CACHE);
  try {
    const response = await fetch(request);
    // Keep only real pages (not redirects to sign-in or error pages).
    if (response.ok && !response.redirected) cache.put(request, response.clone());
    return response;
  } catch {
    const url = new URL(request.url);
    return (
      (await cache.match(request, { ignoreSearch: true })) ||
      (await cache.match(new URL("/", url).href)) ||
      new Response(OFFLINE_PAGE, { headers: { "Content-Type": "text/html; charset=utf-8" } })
    );
  }
}

const OFFLINE_PAGE = `<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1"><title>Brain Dump · Offline</title>
<style>body{margin:0;min-height:100dvh;display:grid;place-items:center;font-family:system-ui,sans-serif;background:#ece5d6;color:#1c1b18;padding:24px;text-align:center}
@media (prefers-color-scheme:dark){body{background:#0b0b0a;color:#f1eee6}}h1{font-size:22px}p{opacity:.75;max-width:320px}</style></head>
<body><div><h1>📴 You're offline</h1><p>Open Brain Dump once while you're online, and next time it will work without a connection too.</p></div></body></html>`;
