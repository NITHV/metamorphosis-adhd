// Runs the shared smart-guess cases (shared/smart-guess-cases.json) against the website's code.
// The Android app runs the same file (SmartGuessSharedCasesTest.kt), so the two can't drift apart.
// Run with: npm test -w brain-dump
process.env.TZ = "UTC"; // cases are local wall-clock times; pin the zone so results don't depend on the machine

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { findDate, guessKind, splitDump } from "./smart-guess";

type Cases = {
  now: string;
  dates: { text: string; due: string | null }[];
  kinds: { text: string; kind: string | null }[];
  splits: { text: string; parts: string[] }[];
};

const cases: Cases = JSON.parse(readFileSync(new URL("../../../../shared/smart-guess-cases.json", import.meta.url), "utf8"));
const now = new Date(cases.now);

/** "2026-10-11T09:00" in the (pinned) local zone. */
function local(d: Date) {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

for (const c of cases.dates) {
  test(`date: ${JSON.stringify(c.text)}`, () => {
    const found = findDate(c.text, now);
    assert.equal(found ? local(found.date) : null, c.due);
  });
}

for (const c of cases.kinds) {
  test(`kind: ${JSON.stringify(c.text)}`, () => assert.equal(guessKind(c.text), c.kind));
}

for (const c of cases.splits) {
  test(`split: ${JSON.stringify(c.text)}`, () => assert.deepEqual(splitDump(c.text), c.parts));
}
