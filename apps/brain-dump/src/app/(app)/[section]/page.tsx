import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Mascot } from "@/components/mascot";

const SECTIONS = {
  tasks: { title: "Tasks", blurb: "Things to do, pulled out of your dumps.", when: "Arrives with sorting (next step)." },
  ideas: { title: "Ideas", blurb: "Sparks worth keeping, no pressure to act.", when: "Arrives with sorting (next step)." },
  reminders: { title: "Reminders", blurb: "Anything with a date or time attached.", when: "Arrives with sorting (next step)." },
  worries: { title: "Worries", blurb: "A gentle place to park them. Never overdue.", when: "Arrives with sorting (next step)." },
  paused: { title: "Paused", blurb: "Where did I leave off? Voice bookmarks for task switching.", when: "Coming in a later step." },
  settings: { title: "Settings", blurb: "AI assist switch, delete everything, and more.", when: "Coming in a later step." },
} as const;

type Section = keyof typeof SECTIONS;

export function generateStaticParams() {
  return Object.keys(SECTIONS).map((section) => ({ section }));
}

export async function generateMetadata({ params }: PageProps<"/[section]">): Promise<Metadata> {
  const { section } = await params;
  const s = SECTIONS[section as Section];
  return { title: s ? `${s.title} · Brain Dump` : "Brain Dump" };
}

export default async function SectionPage({ params }: PageProps<"/[section]">) {
  const { section } = await params;
  const s = SECTIONS[section as Section];
  if (!s) notFound();
  return (
    <div>
      <h1 className="text-3xl font-extrabold tracking-tight">{s.title}</h1>
      <p className="mt-1 text-muted">{s.blurb}</p>
      <div className="chunky mt-8 flex flex-col items-center rounded-3xl bg-card px-6 py-12 text-center">
        <Mascot className="h-24 w-24" />
        <p className="mt-4 text-lg font-bold">Still being built</p>
        <p className="mt-1 text-muted">{s.when}</p>
        <Link
          href="/"
          className="chunky-sm press mt-6 rounded-xl bg-brand px-5 py-2.5 font-bold text-brand-foreground"
        >
          Back to dumping
        </Link>
      </div>
    </div>
  );
}
