import { handleUpload, type HandleUploadBody } from "@vercel/blob/client";
import { headers } from "next/headers";
import { auth } from "@repo/auth/server";
import { MAX_PHOTO_BYTES } from "@/lib/limits";

// Photos are named by the dump's UUID (chosen on the phone), so a retried upload overwrites
// the same file instead of leaving a duplicate behind.
const PHOTO_FILE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(webp|jpg)$/i;

/** Issues short-lived tokens so the browser can upload a (shrunk) photo straight to the private Blob store. */
export async function POST(request: Request) {
  const body = (await request.json()) as HandleUploadBody;
  try {
    const result = await handleUpload({
      body,
      request,
      onBeforeGenerateToken: async (pathname) => {
        const session = await auth.api.getSession({ headers: await headers() });
        if (!session) throw new Error("Not signed in");
        const folder = `photo/${session.user.id}/`;
        // Each user may only write inside their own folder, and only files named like a dump id.
        if (!pathname.startsWith(folder) || !PHOTO_FILE.test(pathname.slice(folder.length))) {
          throw new Error("Invalid path");
        }
        return {
          // Photos are re-encoded in the browser, so only these two types are ever expected.
          allowedContentTypes: ["image/webp", "image/jpeg"],
          maximumSizeInBytes: MAX_PHOTO_BYTES,
          addRandomSuffix: false,
          allowOverwrite: true,
        };
      },
    });
    return Response.json(result);
  } catch (error) {
    return Response.json({ error: error instanceof Error ? error.message : "Upload failed" }, { status: 400 });
  }
}
