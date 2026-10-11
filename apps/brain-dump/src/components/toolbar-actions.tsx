"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { MicIcon, PauseIcon, PencilIcon, SearchIcon } from "./icons";
import { openPause } from "./pause/pause-launcher";

export const FOCUS_DUMP_EVENT = "brain-dump:focus";
export const OPEN_VOICE_EVENT = "brain-dump:voice";

export function ToolbarActions() {
  const router = useRouter();
  const btn = "flex h-8 w-8 items-center justify-center rounded-lg transition";
  return (
    <div className="flex items-center gap-1 text-muted">
      <Link href="/search" title="Search" aria-label="Search" className={`${btn} hover:bg-hairline/60 hover:text-foreground`}>
        <SearchIcon />
      </Link>
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
        title="Pause what I'm doing"
        onClick={openPause}
        className={`${btn} hover:bg-hairline/60 hover:text-foreground`}
      >
        <PauseIcon />
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
