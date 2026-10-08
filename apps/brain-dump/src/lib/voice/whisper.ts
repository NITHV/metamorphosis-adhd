"use client";

// Talks to /whisper-worker.js (on-device Whisper). One worker per tab, created on first use.

export type WhisperProgress = { phase: "downloading"; percent: number } | { phase: "transcribing" };

const READY_KEY = "brain-dump:whisper-ready";

let worker: Worker | null = null;
let nextId = 0;

function getWorker() {
  worker ??= new Worker("/whisper-worker.js", { type: "module" });
  return worker;
}

/** True once the model has been downloaded on this device (so transcribing won't cost data again). */
export function isWhisperCached(): boolean {
  try {
    return localStorage.getItem(READY_KEY) === "1";
  } catch {
    return false;
  }
}

/** Whisper wants 16 kHz mono samples; the browser can decode and resample in one go. */
async function toMono16k(blob: Blob): Promise<Float32Array> {
  const ctx = new AudioContext({ sampleRate: 16000 });
  try {
    const buffer = await ctx.decodeAudioData(await blob.arrayBuffer());
    // Copy, so the samples can be handed to the worker without touching the AudioBuffer.
    if (buffer.numberOfChannels === 1) return new Float32Array(buffer.getChannelData(0));
    const a = buffer.getChannelData(0);
    const b = buffer.getChannelData(1);
    const mono = new Float32Array(a.length);
    for (let i = 0; i < a.length; i++) mono[i] = (a[i] + b[i]) / 2;
    return mono;
  } finally {
    void ctx.close();
  }
}

export async function transcribeOnDevice(blob: Blob, onProgress?: (p: WhisperProgress) => void): Promise<string> {
  const audio = await toMono16k(blob);
  const w = getWorker();
  const id = ++nextId;
  // Several model files download in parallel; report overall progress.
  const files = new Map<string, { loaded: number; total: number }>();

  return new Promise((resolve, reject) => {
    function onMessage(e: MessageEvent) {
      const msg = e.data;
      if (msg.type === "progress") {
        files.set(msg.file, { loaded: msg.loaded, total: msg.total });
        let loaded = 0;
        let total = 0;
        for (const f of files.values()) {
          loaded += f.loaded;
          total += f.total;
        }
        onProgress?.({ phase: "downloading", percent: total ? Math.round((loaded / total) * 100) : 0 });
        return;
      }
      if (msg.id !== id) return;
      if (msg.type === "status") onProgress?.({ phase: "transcribing" });
      else if (msg.type === "result") {
        cleanup();
        try {
          localStorage.setItem(READY_KEY, "1");
        } catch {
          // storage unavailable; we'll just ask again next time
        }
        resolve(msg.text);
      } else if (msg.type === "error") {
        cleanup();
        reject(new Error(msg.message));
      }
    }
    function cleanup() {
      w.removeEventListener("message", onMessage);
    }
    w.addEventListener("message", onMessage);
    w.postMessage({ id, audio }, [audio.buffer]);
  });
}
