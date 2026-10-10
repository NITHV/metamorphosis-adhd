"use client";

import { useMemo } from "react";
import { Mascot } from "@/components/mascot";
import { useIsClient } from "@/components/use-is-client";
import type { HomeStats } from "@/lib/stats";

const WEEKS = 13;

function dayKey(d: Date) {
  return `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`;
}

/** Midnight to 5 AM gets a gentler line: late-night spirals are prime dumping time. */
function greeting(hour: number, name: string) {
  if (hour < 5) return `Hey ${name}, night owl 🦉`;
  if (hour < 12) return `Good morning, ${name}`;
  if (hour < 17) return `Good afternoon, ${name}`;
  return `Good evening, ${name}`;
}

/** Consecutive active days. One missed day is forgiven; two in a row ends the streak. */
function forgivingStreak(days: Set<string>, today: Date) {
  const d = new Date(today);
  if (!days.has(dayKey(d))) d.setDate(d.getDate() - 1); // today isn't over yet
  let streak = 0;
  let misses = 0;
  for (let i = 0; i < 400; i++) {
    if (days.has(dayKey(d))) {
      streak++;
      misses = 0;
    } else if (++misses >= 2) {
      break;
    }
    d.setDate(d.getDate() - 1);
  }
  return streak;
}

export function HeroCard({ name, stats }: { name: string; stats: HomeStats }) {
  const isClient = useIsClient();

  const { cells, streak } = useMemo(() => {
    const counts = new Map<string, number>();
    for (const iso of stats.activity) {
      const k = dayKey(new Date(iso));
      counts.set(k, (counts.get(k) ?? 0) + 1);
    }
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const start = new Date(today);
    start.setDate(start.getDate() - (WEEKS - 1) * 7 - today.getDay()); // Sunday, 12 weeks back
    const cells: { key: string; count: number; future: boolean; label: string }[] = [];
    for (let i = 0; i < WEEKS * 7; i++) {
      const d = new Date(start);
      d.setDate(start.getDate() + i);
      const count = counts.get(dayKey(d)) ?? 0;
      cells.push({
        key: dayKey(d),
        count,
        future: d > today,
        label: `${d.toLocaleDateString(undefined, { month: "short", day: "numeric" })}: ${count} ${count === 1 ? "dump" : "dumps"}`,
      });
    }
    return { cells, streak: forgivingStreak(new Set(counts.keys()), today) };
    // isClient: recompute once in the browser so dates use the viewer's timezone
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [stats.activity, isClient]);

  return (
    <section className="chunky flex flex-col gap-4 rounded-3xl bg-brand p-4 text-brand-foreground sm:gap-6 sm:p-6 lg:flex-row lg:items-center">
      <div className="flex flex-1 items-center gap-3 sm:gap-5">
        <Mascot className="h-16 w-16 shrink-0 sm:h-28 sm:w-28" />
        <div className="min-w-0">
          <p className="text-sm font-medium opacity-80 sm:text-[15px]" suppressHydrationWarning>
            {isClient ? greeting(new Date().getHours(), name) : `Hello, ${name}`}
          </p>
          <h1 className="mt-0.5 text-xl leading-tight font-extrabold tracking-tight sm:text-3xl">Get it out of your head.</h1>
          <div className="mt-2 flex flex-wrap gap-1.5 sm:mt-3 sm:gap-2">
            <StatPill value={stats.dumpsThisWeek} label={stats.dumpsThisWeek === 1 ? "dump this week" : "dumps this week"} />
            <StatPill value={stats.sortedCount} label="sorted" highlight />
          </div>
        </div>
      </div>

      <div className="chunky-sm flex items-center justify-between gap-4 rounded-2xl bg-card p-3 text-foreground sm:justify-start sm:gap-5 sm:self-start sm:p-4 lg:self-auto">
        <div>
          <p className="text-[11px] font-semibold tracking-wider text-muted">LAST 13 WEEKS</p>
          <div
            className="mt-2 grid grid-flow-col grid-rows-7 gap-[3px]"
            role="img"
            aria-label={`Activity grid. ${stats.activity.length} dumps in the last 13 weeks.`}
          >
            {cells.map((c) => (
              <span
                key={c.key}
                title={isClient && !c.future ? c.label : undefined}
                className="h-[11px] w-[11px] rounded-[3px]"
                style={{
                  background: c.future ? "transparent" : `var(--heat-${Math.min(c.count, 4)})`,
                  outline: c.count === 0 && !c.future ? "1px solid var(--hairline)" : undefined,
                }}
              />
            ))}
          </div>
          <div className="mt-2 flex items-center gap-1 text-[10px] text-muted">
            Less
            {[0, 1, 2, 3, 4].map((l) => (
              <span key={l} className="h-2 w-2 rounded-[2px]" style={{ background: `var(--heat-${l})` }} />
            ))}
            More
          </div>
        </div>
        <div className="flex flex-col items-center px-1 text-center">
          <span className="h-3 w-3 rounded-full" style={{ background: "var(--pill-red)" }} aria-hidden />
          <span className="mt-1 text-3xl font-extrabold tabular-nums" suppressHydrationWarning>
            {isClient ? streak : "–"}
          </span>
          <span className="text-[11px] font-semibold tracking-wider text-muted">DAY STREAK</span>
        </div>
      </div>
    </section>
  );
}

function StatPill({ value, label, highlight }: { value: number; label: string; highlight?: boolean }) {
  return (
    <span
      className={`chunky-sm rounded-full px-2.5 py-1 text-xs leading-tight sm:px-3.5 sm:py-1.5 sm:text-sm ${
        highlight ? "text-[#1c1b18]" : "bg-card text-foreground"
      }`}
      style={highlight ? { background: "var(--pill-orange)" } : undefined}
    >
      <span className="font-bold tabular-nums">{value.toLocaleString()}</span> {label}
    </span>
  );
}
