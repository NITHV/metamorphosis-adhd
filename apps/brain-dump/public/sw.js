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
  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;
  // "Share to Brain Dump" from another app (manifest share_target).
  if (request.method === "POST" && url.pathname === "/share-target") {
    event.respondWith(handleShare(request));
    return;
  }
  if (request.method !== "GET") return;
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

// ---------------------------------------------------------------------------
// Share target: park what was shared on the device, then open the app, which turns it into dumps
// (src/lib/shared.ts). Parking first means sharing works offline and needs no sign-in check here.
// ---------------------------------------------------------------------------

// Must match src/lib/outbox.ts (same database, version and upgrade steps).
const DB_NAME = "brain-dump";
const DB_VERSION = 2;

function openDb() {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains("outbox")) db.createObjectStore("outbox", { keyPath: "id" });
      if (!db.objectStoreNames.contains("shared")) db.createObjectStore("shared", { keyPath: "id" });
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

async function handleShare(request) {
  try {
    const form = await request.formData();
    const parts = ["title", "text", "url"].map((k) => String(form.get(k) || "").trim()).filter(Boolean);
    // Apps often repeat the link inside the text; keep each piece once.
    const text = [...new Set(parts)].join("\n");
    const files = form.getAll("photos").filter((f) => f instanceof Blob && f.size > 0);
    if (!text && files.length === 0) return Response.redirect("/", 303);

    const db = await openDb();
    await new Promise((resolve, reject) => {
      const tx = db.transaction("shared", "readwrite");
      tx.objectStore("shared").put({ id: crypto.randomUUID(), text, files, createdAt: new Date().toISOString() });
      tx.oncomplete = resolve;
      tx.onerror = () => reject(tx.error);
    });
    db.close();
    return Response.redirect("/?shared=1", 303);
  } catch {
    return Response.redirect("/?shared=failed", 303);
  }
}

const OFFLINE_PAGE = `<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1"><title>Brain Dump · Offline</title>
<style>body{margin:0;min-height:100dvh;display:grid;place-items:center;font-family:system-ui,sans-serif;background:#ece5d6;color:#1c1b18;padding:24px;text-align:center}
@media (prefers-color-scheme:dark){body{background:#0b0b0a;color:#f1eee6}}h1{font-size:22px}p{opacity:.75;max-width:320px}</style></head>
<body><div><h1>📴 You're offline</h1><p>Open Brain Dump once while you're online, and next time it will work without a connection too.</p></div></body></html>`;
