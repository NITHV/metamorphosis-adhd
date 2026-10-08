"use client";

import { useRouter } from "next/navigation";
import { MicIcon, PencilIcon, SearchIcon } from "./icons";

export const FOCUS_DUMP_EVENT = "brain-dump:focus";
export const OPEN_VOICE_EVENT = "brain-dump:voice";

export function ToolbarActions() {
  const router = useRouter();
  const btn = "flex h-8 w-8 items-center justify-center rounded-lg transition";
  return (
    <div className="flex items-center gap-1 text-muted">
      <button type="button" disabled title="Search (coming soon)" className={`${btn} opacity-40`}>
        <SearchIcon />
      </button>
      <button
        type="button"
        title="New dump (Ctrl/⌘ K)"
        onClick={() => {
          if (document.getElementById("dump")) window.dispatchEvent(new Event(FOCUS_DUMP_EVENT));
          else router.push("/");
        }}
        className={`${btn} hover:bg-hairline/60 hover:text-foreground`}
      >
        <PencilIcon />
      </button>
      <button
        type="button"
        title="Voice dump"
        onClick={() => {
          if (document.getElementById("dump")) window.dispatchEvent(new Event(OPEN_VOICE_EVENT));
          else router.push("/?voice=1");
        }}
        className={`${btn} hover:bg-hairline/60 hover:text-foreground`}
      >
        <MicIcon />
      </button>
    </div>
  );
}
