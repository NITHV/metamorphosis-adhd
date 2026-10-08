import "server-only";
import { cache } from "react";
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
 * Wrapped in React cache() so the session is looked up once per request.
 */
export const getCurrentUser = cache(async (): Promise<CurrentUser> => {
  const session = await auth.api.getSession({ headers: await headers() });
  if (!session) {
    redirect("/sign-in");
  }
  const { id, name, email, image } = session.user;
  return { id, name, email, image: image ?? null };
});
