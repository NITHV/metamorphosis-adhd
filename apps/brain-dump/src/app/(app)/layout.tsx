import { Suspense } from "react";
import Link from "next/link";
import { getCurrentUser } from "@/lib/auth";
import { getInboxCount, getHomeStats } from "@/lib/stats";
import { getPileCounts } from "@/lib/items";
import { getPausedCount } from "@/lib/checkpoints";
import { PauseLauncher } from "@/components/pause/pause-launcher";
import { AppServices, OfflineBanner } from "@/components/offline/app-services";
import { Mascot } from "@/components/mascot";
import { MobileTabBar, SidebarNav } from "@/components/nav";
import { ToolbarActions } from "@/components/toolbar-actions";
import { UserMenu } from "@/components/user-menu";

export default function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex h-dvh pt-[env(safe-area-inset-top)] md:p-4 lg:p-6">
      <div className="flex min-h-0 flex-1 overflow-hidden bg-surface md:chunky md:rounded-3xl">
        <aside className="hidden w-60 shrink-0 flex-col border-r-2 border-hairline px-3 py-4 md:flex">
          <Brand />
          <div className="mt-5 flex flex-1 flex-col">
            <Suspense fallback={<SidebarNav counts={{}} />}>
              <SidebarWithCounts />
            </Suspense>
          </div>
        </aside>

        <div className="flex min-w-0 flex-1 flex-col">
          <header className="flex h-14 shrink-0 items-center justify-between gap-3 px-4 md:justify-end md:px-6">
            <div className="md:hidden">
              <Brand />
            </div>
            <div className="flex items-center gap-2">
              <ToolbarActions />
              <Suspense fallback={<div className="h-8 w-8 rounded-full bg-hairline" />}>
                <UserMenu />
              </Suspense>
            </div>
          </header>

          <Suspense fallback={null}>
            <OfflineBannerForUser />
          </Suspense>

          <main className="min-h-0 flex-1 overflow-y-auto px-4 pb-28 md:px-8 md:pb-8">
            <div className="mx-auto w-full max-w-4xl">{children}</div>
          </main>

          <footer className="hidden h-9 shrink-0 items-center justify-between border-t-2 border-hairline px-6 text-xs text-muted md:flex">
            <Suspense fallback={<span>…</span>}>
              <StatusLeft />
            </Suspense>
            <span className="flex items-center gap-1.5">
              <span className="h-2 w-2 rounded-full bg-brand" aria-hidden />
              Private by default · Free
            </span>
          </footer>
        </div>
      </div>
      <MobileTabBar />
      <Suspense fallback={null}>
        <PauseLauncherForUser />
      </Suspense>
    </div>
  );
}

function Brand() {
  return (
    <Link href="/" className="flex items-center gap-2 px-1.5 font-semibold tracking-tight">
      <Mascot className="h-7 w-7" />
      Brain Dump
    </Link>
  );
}

async function SidebarWithCounts() {
  const user = await getCurrentUser();
  const [inbox, piles, paused] = await Promise.all([
    getInboxCount(user.id),
    getPileCounts(user.id),
    getPausedCount(user.id),
  ]);
  return (
    <SidebarNav
      counts={{
        "/inbox": inbox,
        "/tasks": piles.task,
        "/ideas": piles.idea,
        "/reminders": piles.reminder,
        "/worries": piles.worry,
        "/paused": paused,
      }}
    />
  );
}

async function StatusLeft() {
  const user = await getCurrentUser();
  const { totalNotes } = await getHomeStats(user.id);
  return (
    <span>
      {totalNotes} {totalNotes === 1 ? "note" : "notes"} · Saved to the cloud
    </span>
  );
}

async function PauseLauncherForUser() {
  const user = await getCurrentUser();
  return (
    <>
      <PauseLauncher userId={user.id} />
      <AppServices userId={user.id} />
    </>
  );
}

async function OfflineBannerForUser() {
  const user = await getCurrentUser();
  return <OfflineBanner userId={user.id} />;
}
