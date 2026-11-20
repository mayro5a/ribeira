/* Service Worker do Ribeira — gerado por scripts/generate-sw.mjs (ADR-0006). */
const VERSION = "__VERSION__";
const CACHE = `ribeira-${VERSION}`;
const PRECACHE = __PRECACHE__;
const NAVIGATION_TIMEOUT_MS = 4000;

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(PRECACHE)));
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(
          keys
            .filter((key) => key.startsWith("ribeira-") && key !== CACHE)
            .map((key) => caches.delete(key)),
        ),
      )
      .then(() => self.clients.claim()),
  );
});

self.addEventListener("message", (event) => {
  if (event.data && event.data.type === "SKIP_WAITING") {
    self.skipWaiting();
  }
});

self.addEventListener("fetch", (event) => {
  const request = event.request;
  const url = new URL(request.url);
  // Dados autenticados nunca passam pelo cache do Service Worker: ficam no IndexedDB.
  if (
    request.method !== "GET" ||
    url.origin !== self.location.origin ||
    url.pathname.startsWith("/api/")
  ) {
    return;
  }
  event.respondWith(request.mode === "navigate" ? networkFirst(request) : cacheFirst(request));
});

async function networkFirst(request) {
  try {
    return await withTimeout(fetch(request), NAVIGATION_TIMEOUT_MS);
  } catch {
    const cache = await caches.open(CACHE);
    const path = new URL(request.url).pathname;
    const candidates = [
      path,
      path.endsWith("/") ? `${path}index.html` : `${path}/index.html`,
      "/index.html",
    ];
    for (const candidate of candidates) {
      const hit = await cache.match(candidate, { ignoreSearch: true });
      if (hit) return hit;
    }
    return Response.error();
  }
}

async function cacheFirst(request) {
  const cache = await caches.open(CACHE);
  const hit = await cache.match(request, { ignoreSearch: true });
  if (hit) return hit;
  try {
    return await fetch(request);
  } catch {
    return Response.error();
  }
}

function withTimeout(promise, ms) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("timeout")), ms);
    promise.then(
      (value) => {
        clearTimeout(timer);
        resolve(value);
      },
      (error) => {
        clearTimeout(timer);
        reject(error);
      },
    );
  });
}
