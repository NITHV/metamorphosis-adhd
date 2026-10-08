"use client";

import { PauseIcon } from "@/components/icons";
import { openPause } from "./pause-launcher";

export function PauseNowButton() {
  return (
    <button
      type="button"
      onClick={openPause}
      className="chunky-sm press flex h-11 items-center gap-2 rounded-xl bg-card px-4 font-bold"
    >
      <PauseIcon /> Pause now
    </button>
  );
}
