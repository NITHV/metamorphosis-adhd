"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { deleteEverything } from "@/app/actions";
import { forgetEverythingOnDevice } from "@/lib/outbox";

type Phase = { name: "idle" } | { name: "confirm" } | { name: "deleting" } | { name: "done" } | { name: "error"; message: string };

/** Two deliberate steps (open, then type "delete") so it can't happen by accident. */
export function DeleteEverything({ empty }: { empty: boolean }) {
  const router = useRouter();
  const [phase, setPhase] = useState<Phase>({ name: "idle" });
  const [typed, setTyped] = useState("");
  const ready = typed.trim().toLowerCase() === "delete";

  async function run() {
    setPhase({ name: "deleting" });
    try {
      // Device first: a dump still waiting to upload must not arrive after the server is wiped.
      await forgetEverythingOnDevice();
      const result = await deleteEverything(typed);
      if (result.ok) {
        setPhase({ name: "done" });
        setTyped("");
      } else {
        setPhase({ name: "error", message: result.error });
      }
    } catch {
      setPhase({ name: "error", message: "Couldn't reach Brain Dump. Check your connection and try again." });
    }
    router.refresh();
  }

  if (phase.name === "done") {
    return (
      <p role="status" className="mt-4 font-semibold">
        All gone. A fresh start 🌱
      </p>
    );
  }

  if (phase.name === "idle") {
    return (
      <button
        type="button"
        disabled={empty}
        onClick={() => setPhase({ name: "confirm" })}
        className="mt-4 h-11 rounded-xl border-2 px-4 font-semibold disabled:opacity-50"
        style={{ borderColor: "var(--pill-red)", color: "var(--pill-red)" }}
      >
        {empty ? "Nothing to delete" : "Delete everything…"}
      </button>
    );
  }

  return (
    <form
      className="mt-4 flex flex-col gap-3"
      onSubmit={(e) => {
        e.preventDefault();
        if (ready && phase.name !== "deleting") void run();
      }}
    >
      <label htmlFor="confirm-delete" className="text-[15px]">
        Type <b>delete</b> to confirm.
      </label>
      <input
        id="confirm-delete"
        value={typed}
        onChange={(e) => setTyped(e.target.value)}
        autoComplete="off"
        autoCapitalize="none"
        autoFocus
        disabled={phase.name === "deleting"}
        className="h-11 max-w-xs rounded-xl border-2 border-hairline bg-surface px-3 text-base outline-none focus:border-ink"
      />
      {phase.name === "error" && (
        <p role="alert" className="text-sm font-semibold" style={{ color: "var(--pill-red)" }}>
          {phase.message}
        </p>
      )}
      <div className="flex gap-2">
        <button
          type="button"
          onClick={() => {
            setPhase({ name: "idle" });
            setTyped("");
          }}
          disabled={phase.name === "deleting"}
          className="h-11 rounded-xl border-2 border-hairline px-4 font-semibold text-muted"
        >
          Cancel
        </button>
        <button
          type="submit"
          disabled={!ready || phase.name === "deleting"}
          className="h-11 rounded-xl px-4 font-bold text-white disabled:opacity-50"
          style={{ background: "var(--pill-red)" }}
        >
          {phase.name === "deleting" ? "Deleting…" : "Delete forever"}
        </button>
      </div>
    </form>
  );
}
