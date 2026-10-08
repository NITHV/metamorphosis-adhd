import { and, eq } from "drizzle-orm";
import { get } from "@vercel/blob";
import { headers } from "next/headers";
import { auth } from "@repo/auth/server";
import { db, schema } from "@repo/db";

const { captures } = schema;

/**
 * Streams a voice note to its owner only. Supports HTTP Range requests, which
 * Safari needs to play audio at all.
 */
export async function GET(request: Request, ctx: RouteContext<"/api/audio/[id]">) {
  const session = await auth.api.getSession({ headers: await headers() });
  if (!session) return new Response("Not signed in", { status: 401 });

  const { id } = await ctx.params;
  const [row] = await db
    .select({ audioUrl: captures.audioUrl })
    .from(captures)
    .where(and(eq(captures.id, id), eq(captures.userId, session.user.id)));
  if (!row?.audioUrl) return new Response("Not found", { status: 404 });

  const blob = await get(row.audioUrl, { access: "private" });
  if (!blob || blob.statusCode !== 200) return new Response("Not found", { status: 404 });

  // Voice notes are small, so buffering is simpler than proxying ranges upstream.
  const data = new Uint8Array(await new Response(blob.stream).arrayBuffer());
  const type = blob.blob.contentType || "audio/webm";
  const base = {
    "Content-Type": type,
    "Accept-Ranges": "bytes",
    "Cache-Control": "private, max-age=3600",
  };

  const range = request.headers.get("range")?.match(/^bytes=(\d*)-(\d*)$/);
  if (range) {
    const size = data.byteLength;
    let start = range[1] ? Number(range[1]) : size - Number(range[2]);
    let end = range[1] && range[2] ? Number(range[2]) : size - 1;
    start = Math.max(0, start);
    end = Math.min(end, size - 1);
    if (start > end) {
      return new Response(null, { status: 416, headers: { "Content-Range": `bytes */${size}` } });
    }
    return new Response(data.slice(start, end + 1), {
      status: 206,
      headers: { ...base, "Content-Range": `bytes ${start}-${end}/${size}`, "Content-Length": String(end - start + 1) },
    });
  }
  return new Response(data, { headers: { ...base, "Content-Length": String(data.byteLength) } });
}
