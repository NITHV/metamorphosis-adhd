import { asc, eq } from "drizzle-orm";
import { headers } from "next/headers";
import { auth } from "@repo/auth/server";
import { db, schema } from "@repo/db";

const { captures, items, checkpoints } = schema;

/**
 * "Download a copy": everything the person wrote, as one JSON file. Photos and voice notes are
 * listed (with their dump) but not bundled; they stay viewable in the app until deleted.
 */
export async function GET() {
  const session = await auth.api.getSession({ headers: await headers() });
  if (!session) return new Response("Not signed in", { status: 401 });
  const userId = session.user.id;

  const [captureRows, itemRows, checkpointRows] = await Promise.all([
    db.select().from(captures).where(eq(captures.userId, userId)).orderBy(asc(captures.createdAt)),
    db.select().from(items).where(eq(items.userId, userId)).orderBy(asc(items.createdAt)),
    db.select().from(checkpoints).where(eq(checkpoints.userId, userId)).orderBy(asc(checkpoints.createdAt)),
  ]);

  const body = {
    app: "Brain Dump",
    format: 1,
    exportedAt: new Date().toISOString(),
    account: { name: session.user.name, email: session.user.email },
    dumps: captureRows.map(({ audioUrl, photoUrl, ...c }) => ({ ...omitUser(c), hasVoice: !!audioUrl, hasPhoto: !!photoUrl })),
    items: itemRows.map(omitUser),
    paused: checkpointRows.map(({ audioUrl, ...c }) => ({ ...omitUser(c), hasVoice: !!audioUrl })),
  };

  const day = new Date().toISOString().slice(0, 10);
  return new Response(JSON.stringify(body, null, 2), {
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Content-Disposition": `attachment; filename="brain-dump-${day}.json"`,
      "Cache-Control": "private, no-store",
    },
  });
}

/** The export is for its owner; internal account ids aren't useful in it. */
function omitUser<T extends { userId: string }>(row: T): Omit<T, "userId"> {
  const copy: Partial<T> = { ...row };
  delete copy.userId;
  return copy as Omit<T, "userId">;
}
