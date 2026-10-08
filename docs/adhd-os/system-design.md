# ADHD OS: System Design

**Capture it → break it down → do one step → with a timer and company**

Status: **Approved** (monorepo, Google + email/password sign-in, names kept) · Date: 2026-10-08 · Revision 2: **$0 budget. The app works fully without AI, and AI is an optional free add-on**

---

## 1. The plain-English version

### What it is

ADHD OS is a daily home base for getting things done when your brain doesn't cooperate. It puts four tools into one simple loop:

1. **Dump it.** Get the thought out of your head (this reuses the **Brain Dump** app).
2. **Shrink it.** Pick one task and break it into tiny steps, then see **only the next one**. "Clean apartment" becomes "Put the 3 mugs on your desk in the sink." The app helps with **ready-made step templates** and **guiding questions**. If you turn on the optional AI switch, it can write the steps for you.
3. **Do it.** A **visual timer** (a shrinking colored disc, not scary numbers) runs while you do that one step. You get a gentle chime halfway through and near the end.
4. **Don't do it alone.** Join a **body doubling room**: other people (friends, or anyone online) working quietly at the same time. You post your goal when you join and tick it off when you're done.

### Why it helps with ADHD

| ADHD struggle | What ADHD OS does |
|---|---|
| "I can't start" (task paralysis) | Steps so small they feel silly, and only one shown at a time |
| Time blindness | Time you can *see* shrinking |
| Overwhelm from long lists | No long list on the main screen. Just **one thing** |
| Motivation drops when alone | Other people's presence (body doubling) |
| Forgetting ideas mid-task | One-tap dump, always one button away |

### What does it cost?

**Nothing.** Every piece runs on a free plan. The timer, rooms, templates and everything else work without AI. The AI switch is optional and uses a free service.

### How it works, as an analogy

Imagine a **coach** sitting next to you:
- You hand them a messy pile of notes (**Brain Dump**).
- You point at one note. They pull out a **recipe card** for that kind of job, or ask *"what's the very first thing you'd physically touch?"*, and hide the rest (**Task Breaker**).
- They put an **hourglass** on the table (**Visual Timer**).
- And you're sitting in a **quiet library** where everyone else is working too (**Body Doubling**).

Behind the scenes it's the same "office" as Brain Dump: Vercel hosts the website and a database keeps your tasks. One new piece: a **live connection service** that lets the app show who is in the room right now.

---

## 2. Features (version 1)

### Home screen: "The One Thing"
- Shows a single card: your current step, a **Start** button and a **Dump** button. Nothing else.
- "Not this one" button skips to a different task without guilt.

### Task Breaker (no AI needed)
- Pick any task (from Brain Dump or typed fresh).
- **Templates:** a built-in library of step lists for common ADHD-hard tasks, e.g.:
  - *Clean a room* · *Do laundry* · *Reply to a hard email* · *Pay a bill*
  - *Study for an exam* · *Cook a simple meal* · *Get ready to leave the house* · *Make a phone call*
  - Pick one → its steps are copied in → edit freely.
- **Guided breakdown:** if no template fits, the app asks short questions one at a time:
  1. *"What does 'done' look like?"*
  2. *"What's the very first thing you'd physically touch?"*
  3. *"And after that?"* (repeat)
- **Too big?** on any step asks *"What's the smallest piece of this?"* and splits it.
- **Save as template:** turn any finished breakdown into your own reusable template.
- Each step gets an optional time estimate (2 / 5 / 10 / 15 min chips).
- Shows one step at a time. **Done** reveals the next.

### AI assist (optional switch, off by default)
- When on, **Break it down for me** sends the task title to a **free-tier AI service**, which returns 3–10 tiny, concrete steps with time estimates. You can edit everything.
- **Too big?** can also ask the AI to split a step.
- If the free limit is hit or the service is down, the app falls back to templates and guided questions.

### Visual Timer
- Shrinking disc with a color shift (calm blue → warm orange near the end).
- Presets: 5 ("just 5 minutes"), 15, 25 minutes, or the step's estimate.
- Soft chimes at 50% and with 1 minute left. Optional vibration on phones.
- When it ends: "Keep going?", "Done!" or "Break". No shame either way.
- Runs in the browser and keeps the right time even if you switch tabs (it uses the start timestamp, not a ticking counter).

### Body Doubling Rooms
- **Public room**: anyone signed in can drop in.
- **Private rooms**: share a link with friends.
- When you join, type your goal ("finish the email to my landlord").
- Everyone sees live presence cards: name, goal, timer ring, and a ✓ when done.
- Optional video: a "Turn on video" button opens a free Jitsi Meet call for that room (no extra accounts).
- Quiet by design: no chat feed, only short preset reactions (👋 🎉 💪).

### Light gamification
- Daily "steps done" count and a **forgiving streak** (missing one day doesn't reset it).
- Small celebration animation on each completed step.

### Later (version 2)
- Absorb more of your backlog as modules: Overwhelm button, Dopamine menu, "Leave by" calculator, Time estimate trainer (estimated vs. actual times are already recorded, so this is mostly a new screen).
- Push notifications ("Your friend just joined the room"), via free Web Push.

---

## 3. Architecture

```
                       ┌──────────────────────────────────┐
  Phone / Laptop       │          VERCEL (free)           │
 ┌──────────────┐      │                                  │
 │ ADHD OS app  │ ───► │  Next.js app: ADHD OS            │
 │ (PWA)        │ ◄─── │   • One Thing / Breaker / Timer  │
 │              │      │   • Templates library            │
 │ Timer runs   │      │   • Rooms UI                     │
 │ locally      │      │                                  │
 └──────┬───────┘      │  Shared packages (from monorepo):│
        │              │   • db, auth, ui, ai (optional)  │
        │ live         └──────┬──────────────┬────────────┘
        │ presence            │              ¦ optional
        ▼                     ▼              ▼
 ┌──────────────┐      ┌────────────┐  ┌──────────────────┐
 │ Liveblocks   │      │ Postgres   │  │ Free AI service  │
 │ (free tier,  │      │ (Neon,     │  │ (Groq or Gemini  │
 │ presence)    │      │ free),     │  │ free tier)       │
 └──────────────┘      │ SHARED with│  └──────────────────┘
                       │ Brain Dump │
 ┌──────────────┐      └────────────┘
 │ Jitsi Meet   │  (free, optional video, opened by link)
 └──────────────┘
```

### Why a separate realtime service?
Vercel runs code in short bursts (serverless functions). It's great for "load a page" or "save a task", but it can't hold a connection open for hours to stream "who's in the room right now". **Liveblocks** is built exactly for live presence and has a free tier. The app asks our server for a room token (so only signed-in users can join), then connects straight to Liveblocks.

### Tech choices (all free)

| Layer | Choice | Why |
|---|---|---|
| Framework | **Next.js + TypeScript**, same as Brain Dump | One skill set, shared code |
| Repo layout | **Turborepo monorepo**: `apps/brain-dump`, `apps/adhd-os`, `packages/*` | Write sign-in, database, AI and UI code once, use in both apps |
| Database | **Same Neon Postgres** as Brain Dump | Something dumped in one app appears in the other |
| Auth | **Better Auth**, same Google + email/password sign-in | One account across both apps (matched by email) |
| Task breaking | **Templates (JSON in code) + guided questions** | Free, private, always works |
| AI assist (optional) | **Vercel AI SDK** + free-tier provider (Groq or Gemini) | Free up to daily limits; provider swappable in one line |
| Realtime | **Liveblocks** free tier (presence only) | Holds live connections, which Vercel can't |
| Video | **Jitsi Meet** link (optional) | Free, no setup, no accounts |
| Timer | Pure browser (timestamps + Web Audio chimes) | Accurate, works offline, no server cost |

---

## 4. Data model (adds to the Brain Dump tables)

```
items (from Brain Dump; a task lives here)
  id, user_id, kind, title, ... , focus_order?   ← new: position in "One Thing" queue

task_steps        micro-steps for one task
  id, item_id, user_id, position, text,
  est_minutes?, source (template|guided|manual|ai),
  done_at?, skipped_at?, created_at

templates         user-saved templates (built-in ones live in code)
  id, user_id, name, steps (json), created_at

focus_sessions    one per timer run
  id, user_id, step_id?, planned_minutes,
  started_at, ended_at?, outcome (done|continued|break|abandoned)

rooms
  id, owner_id?, name, is_public, invite_code, created_at

room_sessions     who joined which room, with what goal
  id, room_id, user_id, goal, joined_at, left_at?, completed (bool)

streaks           cached daily totals for fast display
  user_id, date, steps_done
```

Live "who's here right now" lives in Liveblocks (temporary). `room_sessions` keeps the permanent history ("You've done 14 body-doubling sessions this month").

---

## 5. Key flows

**A. Break down a task (no AI)**
1. On a task, tap **Break it down**.
2. The app suggests matching templates (by keywords in the title, e.g. "laundry" → *Do laundry*).
3. Pick a template → steps copied in. Or choose **Guide me** → answer short questions → each answer becomes a step.
4. Steps are saved to `task_steps`. The home screen now shows step 1.

**A2. Break down a task (AI assist on)**
1. Tap **Break it down for me** → the server asks the free AI service for 3–10 tiny steps.
2. Steps appear for review → edit → save. On any error, it offers templates/guide instead.

**B. Do one step**
1. Tap **Start** → timer begins (length = step estimate, or your preset) → `focus_sessions` row created.
2. Chimes at 50% and 1 min left.
3. Tap **Done** → step marked done, session closed, celebration, next step shown.

**C. Body double**
1. Open **Rooms** → pick public or a friend's link → type goal → Join.
2. The app asks our server for a Liveblocks token (checks you're signed in and allowed in that room).
3. Your presence card appears for everyone instantly. Your timer ring syncs.
4. Tick ✓ when done; leaving closes your `room_session`.

---

## 6. Privacy and safety

- Same rules as Brain Dump: every query is filtered to the signed-in user.
- **AI assist is off by default.** With it off, task text never leaves the app's own servers.
- Free AI tiers may use what you send to improve their models; check the provider's terms before turning it on.
- In rooms, others see only **your display name, goal text and timer**, never your tasks or dumps.
- Public room: report/block buttons, short goal length limit and a basic word filter. Private rooms are invite-link only, and links can be regenerated.
- Option to appear as "Anonymous Focuser" in public rooms.

---

## 7. Costs

**$0.** Vercel Hobby, Neon, Liveblocks and the free AI tier (optional) are all free at personal/friends scale. Jitsi is free.

---

## 8. Risks and open questions

| Risk / question | Plan |
|---|---|
| Templates don't cover a task | Guided questions work for anything; save your own templates |
| Free AI tier changes or disappears | App never depends on it; swappable provider |
| Public room moderation | Start with private rooms + one public room; add moderation if it grows |
| Liveblocks free tier limits | Fine for personal/friends use; alternatives (Ably, Pusher, PartyKit) are easy swaps since presence code is isolated |
| Notifications on iPhone | Needs PWA install; v2 |

---

## 9. Build milestones

*(Starts after Brain Dump milestones 1–3, so the monorepo, sign-in and database already exist.)*

1. ADHD OS app shell in the monorepo, shared sign-in, deploy as its own Vercel project.
2. Task Breaker: templates library, guided questions, Too big?, save-as-template.
3. "One Thing" home screen.
4. Visual Timer + focus sessions.
5. Body doubling rooms (Liveblocks presence, goals, ✓).
6. Optional Jitsi video button.
7. Optional AI assist for task breaking.
8. Streaks, celebrations, polish.
