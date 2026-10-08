"use client";

import { useEffect, useRef, useState } from "react";
import { MicIcon } from "@/components/icons";
import { isWhisperCached, transcribeOnDevice, type WhisperProgress } from "@/lib/voice/whisper";

const MAX_SECONDS = 120;

type Phase =
  | { name: "starting" }
  | { name: "recording" }
  | { name: "review" }
  | { name: "transcribing"; progress: WhisperProgress | null }
  | { name: "saving" }
  | { name: "error"; message: string };

// Minimal typing for the (still prefixed in some browsers) Web Speech API.
type Recognition = {
  continuous: boolean;
  interimResults: boolean;
  lang: string;
  onresult: ((e: { resultIndex: number; results: ArrayLike<{ isFinal: boolean; 0: { transcript: string } }> }) => void) | null;
  onerror: ((e: { error: string }) => void) | null;
  onend: (() => void) | null;
  start: () => void;
  stop: () => void;
};

function getRecognition(): Recognition | null {
  const w = window as unknown as { SpeechRecognition?: new () => Recognition; webkitSpeechRecognition?: new () => Recognition };
  const Ctor = w.SpeechRecognition ?? w.webkitSpeechRecognition;
  // On phones the recogniser and the recorder fight over the microphone, which can leave the
  // recording silent. There we record only and transcribe afterwards on the device.
  if (!Ctor || window.matchMedia("(pointer: coarse)").matches) return null;
  return new Ctor();
}

function pickMimeType() {
  for (const t of ["audio/webm;codecs=opus", "audio/mp4", "audio/ogg;codecs=opus", "audio/webm"]) {
    if (MediaRecorder.isTypeSupported(t)) return t;
  }
  return "";
}

export function VoiceRecorder({
  onSave,
  onClose,
}: {
  /** Uploads and saves the note. Throws on failure so the recorder can show it. */
  onSave: (text: string, audio: Blob) => Promise<void>;
  onClose: () => void;
}) {
  const [phase, setPhase] = useState<Phase>({ name: "starting" });
  const [seconds, setSeconds] = useState(0);
  const [level, setLevel] = useState(0);
  const [finalText, setFinalText] = useState("");
  const [interim, setInterim] = useState("");
  const [text, setText] = useState("");
  const [hadLive, setHadLive] = useState(false);
  const [transcribeError, setTranscribeError] = useState<string | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);

  const recorderRef = useRef<MediaRecorder | null>(null);
  const recognitionRef = useRef<Recognition | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const audioRef = useRef<Blob | null>(null);
  const cleanupRef = useRef<() => void>(() => {});
  const stopRef = useRef<() => void>(() => {});

  // Start recording as soon as the sheet opens.
  useEffect(() => {
    let cancelled = false;
    let timer: ReturnType<typeof setInterval> | undefined;
    let raf = 0;
    let audioCtx: AudioContext | null = null;

    (async () => {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({
          audio: { echoCancellation: true, noiseSuppression: true },
        });
        if (cancelled) {
          stream.getTracks().forEach((t) => t.stop());
          return;
        }
        streamRef.current = stream;

        const mimeType = pickMimeType();
        const recorder = new MediaRecorder(stream, mimeType ? { mimeType } : undefined);
        const chunks: Blob[] = [];
        recorder.ondataavailable = (e) => e.data.size && chunks.push(e.data);
        recorder.onstop = () => {
          audioRef.current = new Blob(chunks, { type: recorder.mimeType || mimeType || "audio/webm" });
        };
        recorder.start(250);
        recorderRef.current = recorder;

        // Live transcript where the browser supports it.
        const recognition = getRecognition();
        if (recognition) {
          recognition.continuous = true;
          recognition.interimResults = true;
          recognition.lang = navigator.language || "en-US";
          recognition.onresult = (e) => {
            let fin = "";
            let mid = "";
            for (let i = e.resultIndex; i < e.results.length; i++) {
              const r = e.results[i];
              if (r.isFinal) fin += r[0].transcript;
              else mid += r[0].transcript;
            }
            if (fin) setFinalText((t) => (t ? `${t} ${fin.trim()}` : fin.trim()));
            setInterim(mid);
            setHadLive(true);
          };
          recognition.onerror = () => {}; // fall back to on-device transcription afterwards
          try {
            recognition.start();
            recognitionRef.current = recognition;
          } catch {
            recognitionRef.current = null;
          }
        }

        // A ring that grows with your voice, so you can see it's listening.
        audioCtx = new AudioContext();
        const analyser = audioCtx.createAnalyser();
        analyser.fftSize = 256;
        audioCtx.createMediaStreamSource(stream).connect(analyser);
        const samples = new Uint8Array(analyser.fftSize);
        const tick = () => {
          analyser.getByteTimeDomainData(samples);
          let peak = 0;
          for (const s of samples) peak = Math.max(peak, Math.abs(s - 128));
          setLevel(Math.min(1, peak / 64));
          raf = requestAnimationFrame(tick);
        };
        tick();

        const startedAt = Date.now();
        timer = setInterval(() => {
          const elapsed = Math.floor((Date.now() - startedAt) / 1000);
          setSeconds(elapsed);
          if (elapsed >= MAX_SECONDS) stopRef.current(); // auto-stop at the limit
        }, 250);
        setPhase({ name: "recording" });
      } catch (err) {
        const denied = err instanceof DOMException && (err.name === "NotAllowedError" || err.name === "SecurityError");
        setPhase({
          name: "error",
          message: denied
            ? "Microphone access was blocked. Allow it in your browser's site settings, then try again."
            : "Couldn't start the microphone on this device.",
        });
      }
    })();

    cleanupRef.current = () => {
      clearInterval(timer);
      cancelAnimationFrame(raf);
      void audioCtx?.close();
      audioCtx = null;
    };
    return () => {
      cancelled = true;
      cleanupRef.current();
      recognitionRef.current?.stop();
      if (recorderRef.current?.state === "recording") recorderRef.current.stop();
      streamRef.current?.getTracks().forEach((t) => t.stop());
    };
  }, []);

  async function stop() {
    cleanupRef.current();
    setLevel(0);
    recognitionRef.current?.stop();
    const recorder = recorderRef.current;
    if (recorder && recorder.state === "recording") {
      await new Promise<void>((resolve) => {
        recorder.addEventListener("stop", () => resolve(), { once: true });
        recorder.stop();
      });
    }
    streamRef.current?.getTracks().forEach((t) => t.stop());
    const live = [finalText, interim].filter(Boolean).join(" ").trim();
    setText(live);
    setPhase({ name: "review" });
    // No live transcript: transcribe on the device right away if the model is already here.
    if (!live && isWhisperCached()) void transcribe();
  }

  useEffect(() => {
    stopRef.current = () => void stop();
  });

  async function transcribe() {
    const audio = audioRef.current;
    if (!audio) return;
    setPhase({ name: "transcribing", progress: null });
    setTranscribeError(null);
    try {
      const result = await transcribeOnDevice(audio, (progress) => setPhase({ name: "transcribing", progress }));
      setText(result);
      setPhase({ name: "review" });
    } catch (err) {
      // Leave whatever transcript there is; the user can type or try again.
      console.error("On-device transcription failed:", err);
      setTranscribeError("Couldn't transcribe on this device. You can type it instead, or save the audio as is.");
      setPhase({ name: "review" });
    }
  }

  async function save() {
    const audio = audioRef.current;
    if (!audio) return;
    setPhase({ name: "saving" });
    setSaveError(null);
    try {
      await onSave(text, audio);
      onClose();
    } catch {
      // Keep the recording and transcript so nothing is lost; the user can just tap Save again.
      setSaveError("Couldn't save. Check your connection and tap Save again. Your recording is still here.");
      setPhase({ name: "review" });
    }
  }

  const live = [finalText, interim].filter(Boolean).join(" ");
  const mmss = `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, "0")}`;

  return (
    <div className="fixed inset-0 z-40 flex items-end justify-center bg-black/40 sm:items-center" role="dialog" aria-modal="true" aria-label="Voice dump">
      <div className="chunky w-full rounded-t-3xl bg-card p-5 pb-[calc(1.25rem+env(safe-area-inset-bottom))] sm:max-w-md sm:rounded-3xl sm:pb-5">
        {(phase.name === "starting" || phase.name === "recording") && (
          <div className="flex flex-col items-center text-center">
            <p className="text-sm font-semibold text-muted">
              {phase.name === "starting" ? "Starting the mic…" : `Listening · ${mmss}`}
            </p>
            <button
              type="button"
              onClick={stop}
              disabled={phase.name !== "recording"}
              aria-label="Stop recording"
              className="relative my-6 flex h-28 w-28 items-center justify-center rounded-full"
            >
              <span
                className="absolute inset-0 rounded-full transition-transform duration-75"
                style={{ background: "var(--pill-red-soft)", transform: `scale(${1 + level * 0.45})` }}
                aria-hidden
              />
              <span className="chunky relative flex h-24 w-24 items-center justify-center rounded-full" style={{ background: "var(--pill-red)" }}>
                <span className="h-8 w-8 rounded-md bg-white" aria-hidden />
              </span>
            </button>
            <p className="min-h-12 w-full text-left text-lg leading-snug">
              {live || (
                <span className="text-muted">
                  {getLiveSupportHint()}
                </span>
              )}
            </p>
            <p className="mt-3 text-xs text-muted">Tap the button to stop. Stops by itself after 2 minutes.</p>
          </div>
        )}

        {(phase.name === "review" || phase.name === "transcribing" || phase.name === "saving") && (
          <div className="flex flex-col gap-3">
            <div className="flex items-center justify-between">
              <h2 className="flex items-center gap-2 text-lg font-bold">
                <MicIcon /> Voice dump · {mmss}
              </h2>
            </div>

            {phase.name === "transcribing" ? (
              <div className="rounded-2xl border-2 border-hairline bg-surface p-4">
                <p className="font-semibold">
                  {phase.progress?.phase === "downloading"
                    ? `Getting the transcriber ready… ${phase.progress.percent}%`
                    : "Transcribing on your device…"}
                </p>
                <div className="mt-3 h-2 overflow-hidden rounded-full bg-hairline">
                  <div
                    className="h-full rounded-full bg-brand transition-all"
                    style={{ width: phase.progress?.phase === "downloading" ? `${phase.progress.percent}%` : "100%" }}
                  />
                </div>
                <p className="mt-2 text-xs text-muted">Your audio stays on this device while it&apos;s transcribed.</p>
              </div>
            ) : (
              <>
                <label htmlFor="voice-text" className="sr-only">
                  Transcript
                </label>
                <textarea
                  id="voice-text"
                  value={text}
                  onChange={(e) => setText(e.target.value)}
                  rows={4}
                  placeholder="No transcript yet. Type one, or transcribe below."
                  className="w-full resize-none rounded-2xl border-2 border-hairline bg-surface p-3 text-base outline-none focus:border-ink"
                />
                {transcribeError && <p className="text-sm" style={{ color: "var(--pill-red)" }}>{transcribeError}</p>}
                <button
                  type="button"
                  onClick={transcribe}
                  disabled={phase.name === "saving"}
                  className="self-start py-1 text-sm font-semibold"
                  style={{ color: "var(--pill-blue)" }}
                >
                  {text
                    ? hadLive
                      ? "✨ Improve transcript on this device"
                      : "↻ Transcribe again"
                    : isWhisperCached()
                      ? "✨ Transcribe on this device"
                      : "✨ Transcribe on this device (one-time 40 MB download)"}
                </button>
              </>
            )}

            {saveError && (
              <p className="text-sm font-medium" role="alert" style={{ color: "var(--pill-red)" }}>
                {saveError}
              </p>
            )}
            <div className="mt-1 flex gap-2">
              <button
                type="button"
                onClick={onClose}
                disabled={phase.name === "saving"}
                className="h-12 flex-1 rounded-xl border-2 border-hairline font-semibold text-muted sm:h-11"
              >
                Discard
              </button>
              <button
                type="button"
                onClick={save}
                disabled={phase.name !== "review"}
                className="chunky-sm press h-12 flex-[2] rounded-xl bg-brand font-bold text-brand-foreground disabled:opacity-60 sm:h-11"
              >
                {phase.name === "saving" ? "Saving…" : "Save dump"}
              </button>
            </div>
          </div>
        )}

        {phase.name === "error" && (
          <div className="flex flex-col gap-4 text-center">
            <p className="text-lg font-semibold">🎙️ Hmm.</p>
            <p className="text-muted">{phase.message}</p>
            <button type="button" onClick={onClose} className="chunky-sm press h-12 rounded-xl bg-card font-semibold">
              Close
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

function getLiveSupportHint() {
  if (typeof window === "undefined") return "";
  return getRecognitionAvailable()
    ? "Start talking. Words appear here as you speak."
    : "Recording. You'll get a transcript when you stop.";
}

function getRecognitionAvailable() {
  const w = window as unknown as { SpeechRecognition?: unknown; webkitSpeechRecognition?: unknown };
  return Boolean(w.SpeechRecognition ?? w.webkitSpeechRecognition) && !window.matchMedia("(pointer: coarse)").matches;
}
