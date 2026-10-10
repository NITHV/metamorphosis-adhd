/**
 * Shares are normally caught by the service worker (public/sw.js) and never reach the server. This
 * only runs if a share arrives before the service worker is installed: open the app and say so,
 * rather than showing an error page.
 */
export function POST(request: Request) {
  return Response.redirect(new URL("/?shared=failed", request.url), 303);
}
