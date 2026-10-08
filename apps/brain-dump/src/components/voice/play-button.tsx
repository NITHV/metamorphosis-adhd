"use client";

import { useEffect, useRef, useState } from "react";

/** Compact play/pause for a voice note served by /api/audio/[id] (a dump or a pause checkpoint). */
export function PlayButton({ audioId }: { audioId: string }) {
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const [state, setState] = useState<"idle" | "loading" | "playing" | "error">("idle");

  useEffect(() => () => audioRef.current?.pause(), []);

  function toggle() {
    let audio = audioRef.current;
    if (!audio) {
      audio = new Audio(`/api/audio/${audioId}`);
      audio.addEventListener("playing", () => setState("playing"));
      audio.addEventListener("pause", () => setState("idle"));
      audio.addEventListener("ended", () => setState("idle"));
      audio.addEventListener("error", () => setState("error"));
      audioRef.current = audio;
    }
    if (state === "playing") {
      audio.pause();
    } else {
      setState("loading");
      audio.play().catch(() => setState("error"));
    }
  }

  return (
    <button
      type="button"
      onClick={toggle}
      aria-label={state === "playing" ? "Pause voice note" : "Play voice note"}
      className="inline-flex h-8 items-center gap-1.5 rounded-full px-3 text-xs font-semibold"
      style={{ background: "var(--pill-red-soft)", color: "var(--pill-red)" }}
    >
      {state === "playing" ? "❚❚ Pause" : state === "loading" ? "… Loading" : state === "error" ? "⚠ Can't play" : "▶ Play"}
    </button>
  );
}
