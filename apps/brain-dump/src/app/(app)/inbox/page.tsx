import type { Metadata } from "next";
import { Suspense } from "react";
import { getCurrentUser } from "@/lib/auth";
import { getInboxCaptures } from "@/lib/captures";
import { DumpInbox } from "@/components/dump/dump-inbox";

export const metadata: Metadata = { title: "Inbox · Brain Dump" };

export default function InboxPage() {
  return (
    <div>
      <h1 className="mb-1 text-3xl font-extrabold tracking-tight">Inbox</h1>
      <p className="mb-6 text-muted">Everything you&apos;ve dumped that isn&apos;t sorted yet.</p>
      <Suspense fallback={<div className="chunky h-32 animate-pulse rounded-2xl bg-card" />}>
        <FullInbox />
      </Suspense>
    </div>
  );
}

async function FullInbox() {
  const user = await getCurrentUser();
  const captures = await getInboxCaptures(user.id);
  return <DumpInbox captures={captures} />;
}
