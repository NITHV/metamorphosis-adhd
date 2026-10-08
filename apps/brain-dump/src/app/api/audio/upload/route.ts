import { handleUpload, type HandleUploadBody } from "@vercel/blob/client";
import { headers } from "next/headers";
import { auth } from "@repo/auth/server";

const MAX_AUDIO_BYTES = 10 * 1024 * 1024; // ~10 minutes of compressed speech

/** Issues short-lived tokens so the browser can upload a voice note straight to the private Blob store. */
export async function POST(request: Request) {
  const body = (await request.json()) as HandleUploadBody;
  try {
    const result = await handleUpload({
      body,
      request,
      onBeforeGenerateToken: async (pathname) => {
        const session = await auth.api.getSession({ headers: await headers() });
        if (!session) throw new Error("Not signed in");
        // Each user may only write inside their own folder.
        if (!pathname.startsWith(`voice/${session.user.id}/`)) throw new Error("Invalid path");
        return {
          allowedContentTypes: ["audio/*"],
          maximumSizeInBytes: MAX_AUDIO_BYTES,
          addRandomSuffix: true,
        };
      },
    });
    return Response.json(result);
  } catch (error) {
    return Response.json({ error: error instanceof Error ? error.message : "Upload failed" }, { status: 400 });
  }
}
