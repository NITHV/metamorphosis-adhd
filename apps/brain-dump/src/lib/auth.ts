import "server-only";
import { headers } from "next/headers";
import { redirect } from "next/navigation";
import { auth } from "@repo/auth/server";

export type CurrentUser = {
  id: string;
  name: string;
  email: string;
  image: string | null;
};

/**
 * Reads the session for this request. Call it only inside a <Suspense> boundary
 * (required with Cache Components). Redirects to /sign-in when signed out.
 */
export async function getCurrentUser(): Promise<CurrentUser> {
  const session = await auth.api.getSession({ headers: await headers() });
  if (!session) {
    redirect("/sign-in");
  }
  const { id, name, email, image } = session.user;
  return { id, name, email, image: image ?? null };
}
