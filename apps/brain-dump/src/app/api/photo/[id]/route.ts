import { and, eq } from "drizzle-orm";
import { get } from "@vercel/blob";
import { headers } from "next/headers";
import { auth } from "@repo/auth/server";
import { db, schema } from "@repo/db";

const { captures } = schema;

/** Serves a photo dump to its owner only. */
export async function GET(_request: Request, ctx: RouteContext<"/api/photo/[id]">) {
  const session = await auth.api.getSession({ headers: await headers() });
  if (!session) return new Response("Not signed in", { status: 401 });

  const { id } = await ctx.params;
  const [capture] = await db
    .select({ photoUrl: captures.photoUrl })
    .from(captures)
    .where(and(eq(captures.id, id), eq(captures.userId, session.user.id)));
  if (!capture?.photoUrl) return new Response("Not found", { status: 404 });

  const blob = await get(capture.photoUrl, { access: "private" });
  if (!blob || blob.statusCode !== 200) return new Response("Not found", { status: 404 });

  return new Response(blob.stream, {
    headers: {
      "Content-Type": blob.blob.contentType || "image/webp",
      // A photo never changes once uploaded (same id, same pixels), so the browser may keep it a while.
      // "private" keeps shared caches (CDNs, proxies) from storing it.
      "Cache-Control": "private, max-age=86400",
      "X-Content-Type-Options": "nosniff",
    },
  });
}
