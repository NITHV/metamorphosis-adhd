"use client";

export function RelativeTime({ iso }: { iso: string }) {
  return (
    <time
      dateTime={iso}
      suppressHydrationWarning
      className="shrink-0 rounded-full px-2.5 py-0.5 text-xs font-semibold"
      style={{ background: "var(--pill-blue-soft)", color: "var(--pill-blue)" }}
    >
      {formatRelative(new Date(iso))}
    </time>
  );
}

export function formatRelative(date: Date): string {
  const seconds = Math.round((Date.now() - date.getTime()) / 1000);
  if (seconds < 60) return "just now";
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.round(hours / 24);
  if (days === 1) return "yesterday";
  if (days < 7) return `${days}d ago`;
  return date.toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

function startOfDay(d: Date) {
  const x = new Date(d);
  x.setHours(0, 0, 0, 0);
  return x;
}

/** "Today 3:00 PM", "Tomorrow", "Fri 9 Oct, 10:00 AM". Times at 9:00 are treated as "no time given". */
export function formatDue(date: Date): string {
  const days = Math.round((startOfDay(date).getTime() - startOfDay(new Date()).getTime()) / 86_400_000);
  const time =
    date.getHours() === 9 && date.getMinutes() === 0
      ? ""
      : date.toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit" });
  let day: string;
  if (days === 0) day = "Today";
  else if (days === 1) day = "Tomorrow";
  else if (days === -1) day = "Yesterday";
  else if (days > 1 && days < 7) day = date.toLocaleDateString(undefined, { weekday: "long" });
  else day = date.toLocaleDateString(undefined, { weekday: "short", day: "numeric", month: "short" });
  return time ? `${day} ${time}` : day;
}
