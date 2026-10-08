import { Suspense } from "react";
import { getCurrentUser } from "@/lib/auth";
import { getInboxCaptures } from "@/lib/captures";
import { getHomeStats } from "@/lib/stats";
import { getActiveCheckpoints } from "@/lib/checkpoints";
import { ResumeCard } from "@/components/pause/checkpoint-list";
import { HeroCard } from "@/components/home/hero";
import { DumpInbox } from "@/components/dump/dump-inbox";

export default function HomePage() {
  return (
    <div className="flex flex-col gap-8">
      <Suspense fallback={<div className="chunky h-52 animate-pulse rounded-3xl bg-brand/70 lg:h-44" />}>
        <Hero />
      </Suspense>
      <Suspense fallback={null}>
        <Resume />
      </Suspense>
      <Suspense fallback={<div className="chunky h-32 animate-pulse rounded-2xl bg-card" />}>
        <HomeInbox />
      </Suspense>
    </div>
  );
}

async function Hero() {
  const user = await getCurrentUser();
  const stats = await getHomeStats(user.id);
  return <HeroCard name={user.name.split(" ")[0] || "there"} stats={stats} />;
}

async function HomeInbox() {
  const user = await getCurrentUser();
  const captures = await getInboxCaptures(user.id);
  return <DumpInbox userId={user.id} captures={captures} limit={5} />;
}

async function Resume() {
  const user = await getCurrentUser();
  const checkpoints = await getActiveCheckpoints(user.id);
  return checkpoints.length > 0 ? <ResumeCard checkpoints={checkpoints} /> : null;
}
