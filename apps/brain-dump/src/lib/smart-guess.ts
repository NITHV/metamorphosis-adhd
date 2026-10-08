import * as chrono from "chrono-node";
import type { Kind } from "./kinds";

// Simple, free, private rules (design doc §2). They only pre-select a chip; the user decides.
const WORRY = /\b(worr(y|ied|ying)|anxious|anxiety|scared|afraid|nervous|stress(ed|ful)?|panic|dread|overwhelm(ed|ing)?|what if)\b/i;
const IDEA = /^(idea|thought)\b|\b(maybe (we|i) (could|should)|what about|could try|would be (cool|nice|fun)|someday|wouldn'?t it be|how about)\b/i;
const TASK_VERBS =
  /^(buy|call|email|text|message|reply|fix|book|pay|send|finish|clean|wash|write|read|order|cancel|schedule|submit|return|pick up|drop off|renew|update|check|ask|tell|make|get|do|go|print|sign|file|apply|prepare|plan|review|cook|water|take|bring|move|sort|organi[sz]e|charge|install|backup|back up)\b/i;

// "today" / "now" alone are usually just chatter, not a deadline.
const VAGUE = /^(today|now|right now|tonight|this (morning|afternoon|evening))$/i;

function parseDate(text: string, ref: Date) {
  return chrono
    .parse(text, ref, { forwardDate: true })
    .find((hit) => !VAGUE.test(hit.text.trim()) || hit.start.isCertain("hour"));
}

export type Guess = { kind: Kind | null; dueAt: Date | null; dateText: string | null };

/** Finds a date/time in the text, resolved in the timezone of whoever runs it (use in the browser). */
export function findDate(text: string, ref = new Date()): { date: Date; text: string } | null {
  const hit = parseDate(text, ref);
  if (!hit) return null;
  const date = hit.start.date();
  // A date without a time ("tomorrow") means the morning, not "now" tomorrow.
  if (!hit.start.isCertain("hour")) date.setHours(9, 0, 0, 0);
  return { date, text: hit.text };
}

export function guessKind(text: string): Kind | null {
  const t = text.trim();
  if (WORRY.test(t)) return "worry";
  if (parseDate(t, new Date())) return "reminder";
  if (IDEA.test(t)) return "idea";
  if (TASK_VERBS.test(t)) return "task";
  return null;
}

export function guess(text: string, ref = new Date()): Guess {
  const kind = guessKind(text);
  const found = findDate(text, ref);
  return { kind, dueAt: found?.date ?? null, dateText: found?.text ?? null };
}

/** Splits a ramble into separate thoughts: by line, bullet or number, else by sentence. */
export function splitDump(text: string): string[] {
  const clean = (parts: string[]) =>
    parts.map((p) => p.replace(/^\s*([-*•]|\d+[.)])\s+/, "").trim()).filter((p) => p.length > 1);

  const lines = clean(text.split(/\r?\n+/));
  if (lines.length > 1) return lines;
  const sentences = clean(text.split(/(?<=[.!?])\s+(?=[A-Z0-9"'])/));
  return sentences.length > 1 ? sentences : [text.trim()];
}
