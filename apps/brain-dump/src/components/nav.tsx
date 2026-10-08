"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  BellIcon,
  LayersIcon,
  BulbIcon,
  CheckCircleIcon,
  CloudIcon,
  HomeIcon,
  InboxIcon,
  PauseIcon,
  SettingsIcon,
} from "./icons";

type NavItem = { href: string; label: string; icon: React.ComponentType<{ className?: string }>; dot?: string };

const MAIN: NavItem[] = [
  { href: "/", label: "Home", icon: HomeIcon },
  { href: "/inbox", label: "Inbox", icon: InboxIcon },
];

const PILES: NavItem[] = [
  { href: "/tasks", label: "Tasks", icon: CheckCircleIcon, dot: "var(--pill-blue)" },
  { href: "/ideas", label: "Ideas", icon: BulbIcon, dot: "var(--pill-orange)" },
  { href: "/reminders", label: "Reminders", icon: BellIcon, dot: "var(--pill-purple)" },
  { href: "/worries", label: "Worries", icon: CloudIcon, dot: "var(--pill-green)" },
];

const PAUSED: NavItem = { href: "/paused", label: "Paused", icon: PauseIcon };

export type NavCounts = Partial<Record<string, number>>;

function useIsActive() {
  const pathname = usePathname();
  return (href: string) => {
    if (href === "/") return pathname === "/";
    // On phones the Piles tab stays lit inside any single pile.
    if (href === "/piles") return ["/piles", "/tasks", "/ideas", "/reminders", "/worries"].some((p) => pathname.startsWith(p));
    return pathname.startsWith(href);
  };
}

function SideLink({ item, count, active }: { item: NavItem; count?: number; active: boolean }) {
  const Icon = item.icon;
  return (
    <Link
      href={item.href}
      aria-current={active ? "page" : undefined}
      className={`flex items-center gap-2.5 rounded-lg px-2.5 py-1.5 text-[15px] transition ${
        active ? "bg-nav-active font-medium text-nav-active-foreground" : "hover:bg-hairline/60"
      }`}
    >
      {item.dot ? (
        <span className="mx-[5px] h-2 w-2 rounded-full" style={{ background: item.dot }} aria-hidden />
      ) : (
        <Icon />
      )}
      <span className="flex-1">{item.label}</span>
      {count !== undefined && count > 0 && <span className="text-xs tabular-nums text-muted">{count}</span>}
    </Link>
  );
}

export function SidebarNav({ counts }: { counts: NavCounts }) {
  const isActive = useIsActive();
  return (
    <nav className="flex flex-1 flex-col gap-0.5" aria-label="Main">
      {MAIN.map((item) => (
        <SideLink key={item.href} item={item} count={counts[item.href]} active={isActive(item.href)} />
      ))}
      <p className="mt-5 mb-1 px-2.5 text-xs font-semibold tracking-wider text-muted">PILES</p>
      {PILES.map((item) => (
        <SideLink key={item.href} item={item} count={counts[item.href]} active={isActive(item.href)} />
      ))}
      <div className="mt-5">
        <SideLink item={PAUSED} count={counts[PAUSED.href]} active={isActive(PAUSED.href)} />
      </div>
      <div className="mt-auto pt-4">
        <SideLink
          item={{ href: "/settings", label: "Settings", icon: SettingsIcon }}
          active={isActive("/settings")}
        />
      </div>
    </nav>
  );
}

const TABS: NavItem[] = [
  MAIN[0],
  MAIN[1],
  { href: "/piles", label: "Piles", icon: LayersIcon },
  PAUSED,
  { href: "/settings", label: "More", icon: SettingsIcon },
];

export function MobileTabBar() {
  const isActive = useIsActive();
  return (
    <nav
      aria-label="Main"
      className="fixed inset-x-0 bottom-0 z-20 grid grid-cols-5 border-t-2 border-ink bg-surface pb-[env(safe-area-inset-bottom)] md:hidden"
    >
      {TABS.map((item) => {
        const Icon = item.icon;
        const active = isActive(item.href);
        return (
          <Link
            key={item.href}
            href={item.href}
            aria-current={active ? "page" : undefined}
            className={`flex flex-col items-center gap-0.5 py-2 text-[11px] ${
              active ? "font-semibold text-foreground" : "text-muted"
            }`}
          >
            <span className={`rounded-full px-3 py-0.5 ${active ? "bg-nav-active text-nav-active-foreground" : ""}`}>
              <Icon />
            </span>
            {item.label}
          </Link>
        );
      })}
    </nav>
  );
}
