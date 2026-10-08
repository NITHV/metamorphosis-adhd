import { getCurrentUser } from "@/lib/auth";
import { SignOutButton } from "./sign-out-button";

export async function UserMenu() {
  const user = await getCurrentUser();
  const initial = (user.name || user.email).charAt(0).toUpperCase();
  return (
    <details className="relative">
      <summary
        className="chunky-sm flex h-8 w-8 cursor-pointer list-none items-center justify-center overflow-hidden rounded-full bg-nav-active text-sm font-semibold text-nav-active-foreground [&::-webkit-details-marker]:hidden"
        aria-label="Account menu"
      >
        {user.image ? (
          // eslint-disable-next-line @next/next/no-img-element -- tiny avatar from Google
          <img src={user.image} alt="" className="h-full w-full object-cover" referrerPolicy="no-referrer" />
        ) : (
          initial
        )}
      </summary>
      <div className="chunky absolute right-0 z-30 mt-2 w-56 rounded-xl bg-card p-1.5">
        <div className="px-3 py-2">
          <p className="truncate text-sm font-medium">{user.name}</p>
          <p className="truncate text-xs text-muted">{user.email}</p>
        </div>
        <div className="my-1 h-px bg-hairline" />
        <SignOutButton />
      </div>
    </details>
  );
}
