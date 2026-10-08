# Brain Dump: System Design

**Brain dump inbox + "Where did I leave off?" in one app**

Status: **Approved** (monorepo, Google + email/password sign-in, names kept) · Date: 2026-10-08 · Revision 2: **$0 budget. The app works fully without AI, and AI is an optional free add-on**

---

## 1. The plain-English version

### What it is

Brain Dump is a place to throw every thought the moment you have it, plus a "bookmark for your brain" when you get pulled away from a task.

It does two jobs:

1. **Catch thoughts.** You get an idea in the middle of something else. You tap one big button and either type it or say it out loud. That's it. You go back to what you were doing. Later, whenever you feel like it, you sort your dumps into piles (*tasks*, *ideas*, *reminders*, *worries*) with a single tap each. The app pre-selects its best guess, so most of the time you just confirm.

2. **Save your place.** Before you switch tasks (or get interrupted), you tap **Pause** and record a 10-second voice note: *"I was halfway through the budget sheet, next I need to fill in March."* When you come back, tap **Resume** and it plays your note back, so you don't spend 20 minutes figuring out where you were.

### Why it helps with ADHD

- **No decisions at capture time.** Deciding where something goes is the step that makes people give up on to-do apps. Here you just dump it and sort later, when you have the energy.
- **Working memory gets offloaded.** Thoughts stop bouncing around your head because they are safely stored.
- **Task switching hurts less.** The "Pause / Resume" note is like leaving a sticky note for future you.

### What does it cost?

**Nothing.** Every piece runs on a free plan. The app is fully usable **without any AI**. If you want, you can flip on an **"AI assist"** switch that uses a free AI service to sort for you. If that free service ever stops working or hits its daily limit, the app quietly goes back to working without it.

### How it works, as an analogy

Think of a small office:

| Real-world piece | In the app |
|---|---|
| A **mail slot** in the door where you drop notes | The capture button (text or voice) |
| **Colored folders**, with a sticky note guessing which folder each note belongs in | One-tap sorting with smart suggestions (simple rules, free) |
| An optional **temp assistant** who sometimes helps file | "AI assist" switch (free AI service, off by default) |
| A **filing cabinet** | The database, where everything is saved |
| A **voice recorder** on the desk | Audio storage for your voice notes |
| A **receptionist** who checks your ID | Sign-in (so only you see your stuff) |
| The **building** everything lives in | Vercel, which hosts the website |

You open the app on your phone or laptop (it's a website you can "install" to your home screen like an app). Everything syncs between devices.

---

## 2. Features (version 1)

### Look and feel
- Warm cream (light) / near-black (dark) theme, chunky outlined cards with hard offset shadows, rounded pill badges.
- Home: green welcome card (greeting, weekly stats, 13-week activity grid, forgiving day streak) with the original Brain Dump mascot (a small cartoon brain holding a sticky note), then the Dump box and Inbox.
- Sidebar on desktop (Home, Inbox, Tasks, Ideas, Reminders, Worries, Paused, Settings); bottom tab bar on phones.

### Capture
- One giant **Dump** button on the home screen. Available as a home-screen shortcut and a keyboard shortcut (`Ctrl/Cmd + K`) on desktop.
- **Text** or **voice**. Voice is turned into text live in the browser (Web Speech API, free) and the audio is also kept so you can replay it.
- 🔒 **Private** toggle on any capture: it is never sent to AI, even if AI assist is on.
- Works offline: captures queue on the device and upload when you're back online.

### Sorting (no AI needed)
- Dumps land in the **Inbox**. Each shows four big chips: **Task · Idea · Reminder · Worry**. One tap files it.
- **Smart guess:** simple rules pre-select a chip. For example:
  - starts with a verb like *buy, call, email, fix, book, pay* → **Task**
  - contains a date/time ("Friday", "tomorrow 3pm") → **Reminder**
  - *"what if"*, *"I'm worried"*, *"scared"*, *"anxious"* → **Worry**
  - *"idea"*, *"maybe we could"*, *"what about"* → **Idea**
- **Date detection:** a free library (`chrono-node`) reads dates from text, so "call dentist Friday 3pm" gets a due date automatically.
- **Split:** a long ramble can be split into separate items, one per line or sentence, with one tap.
- **Sort later** is always allowed. The Inbox never nags.
- Worries go in a separate, gentle pile and are never shown as "overdue".

### AI assist (optional switch, off by default)
- When on, new non-private dumps are sent to a **free-tier AI service**, which splits them into items, picks the pile and writes a clean title. You still confirm with one tap.
- If the free limit is reached, or the service is down, the app falls back to the rules above. Nothing breaks.

### Pause / Resume ("Where did I leave off?")
- **Pause**: record ~10 seconds of voice (or type), optionally pick a project tag and paste the link/file you were working in.
- **Resume**: a list of paused things, newest first. Tap one to hear your note and read the transcript.
- With AI assist on, a one-line **"Next step:"** summary is added. Without it, the app asks you to finish the sentence *"Next, I need to…"* while pausing.
- Paused items older than 7 days get a gentle "still relevant?" prompt instead of piling up.

### Views
- **Inbox**: unsorted dumps
- **Tasks / Ideas / Reminders / Worries**
- **Paused**: your saved places
- Search across everything

### Later (version 2, not in the first build)
- Push notifications for reminders (free Web Push)
- Weekly email digest (free tier of an email service)
- Hand-off to ADHD OS (see that design doc)

---

## 3. Architecture

```
                        ┌─────────────────────────────┐
   Phone / Laptop       │          VERCEL (free)      │
  ┌──────────────┐      │                             │
  │  Web app     │ ───► │  Next.js app                │
  │  (installable│ ◄─── │   • pages (UI)              │
  │   PWA)       │      │   • server actions / API    │
  │              │      │                             │
  │ Voice → text │      │   ┌───────────────┐         │
  │ (in browser) │      │   │ AI assist     │ - - - - ┼ - ┐ optional
  │ Smart-guess  │      │   │ (only if on)  │         │   ¦
  │ rules + date │      │   └───────────────┘         │   ¦
  │ parsing      │      └─────┬──────────┬────────────┘   ¦
  └──────────────┘            │          │                ¦
                              ▼          ▼                ▼
                       ┌──────────┐ ┌─────────┐ ┌──────────────────┐
                       │ Postgres │ │ Vercel  │ │ Free AI service  │
                       │ (Neon,   │ │ Blob    │ │ (Groq or Gemini  │
                       │ free)    │ │ (free)  │ │ free tier)       │
                       │ text +   │ │ audio   │ │ OPTIONAL         │
                       │ items    │ │ files   │ │                  │
                       └──────────┘ └─────────┘ └──────────────────┘
```

### Tech choices (all free)

| Layer | Choice | Why |
|---|---|---|
| Framework | **Next.js (App Router) + TypeScript** | First-class on Vercel; UI and server code in one project |
| Styling | **Tailwind CSS + shadcn/ui** | Fast to build, clean, accessible components |
| Hosting | **Vercel Hobby** | Free for personal projects; deploys on every push |
| Database | **Neon Postgres** (via Vercel Marketplace) + **Drizzle ORM** | Free tier, works with serverless, type-safe queries |
| File storage | **Vercel Blob** | Stores voice recordings; free tier is plenty for short notes |
| Auth | **Better Auth**: Google sign-in + **email/password** | Free. Passwords are stored hashed. Magic links postponed (would send via Gmail + Nodemailer, free) |
| Smart guess | **Keyword rules + `chrono-node`** | Free, instant, runs on the device, private |
| Voice → text | **Web Speech API** in the browser | Free. Works in Chrome, Edge and Safari; type instead on Firefox |
| AI assist (optional) | **Vercel AI SDK** + a **free-tier** provider (Groq or Google Gemini) | Free up to daily limits. Switching provider is a one-line change, so better AI (e.g. Claude) can be plugged in later if you ever get credits |
| Offline | **Service worker + IndexedDB queue** | Captures never get lost on a bad connection |

### Why sorting doesn't block capture
Capturing must feel instant. So the app **saves the raw dump first** and replies right away. The smart guess runs instantly on your device. If AI assist is on, it runs a moment later in the background (Next.js `after()`), and the suggestion updates on screen when ready.

---

## 4. Data model

```
users            (managed by Better Auth)
  id, name, email, image, ai_assist_enabled (default false)

captures         one per "dump" (the raw thing you typed/said)
  id, user_id, raw_text, audio_url?, source (text|voice),
  is_private (bool), status (inbox|sorted|archived),
  suggested_kind?, suggested_by (rules|ai)?, created_at

items            what a capture was sorted/split into
  id, user_id, capture_id, kind (task|idea|reminder|worry),
  title, notes?, due_at?, done_at?, archived_at?, created_at

checkpoints      "Where did I leave off?" notes
  id, user_id, label?, project_tag?, link?,
  audio_url?, transcript, next_step?,
  created_at, resumed_at?, dismissed_at?
```

One capture → one or more items. Items keep a link back to the original capture, so you can always see/hear exactly what you said.

---

## 5. Key flows

**A. Voice dump**
1. Tap Dump → speak → text appears live (in the browser, free).
2. Release → the app uploads the audio to Blob and saves the capture to Postgres. The screen shows "Saved ✓" immediately.
3. Smart-guess rules pre-select a chip and detect any date.
4. *(Only if AI assist is on and the capture isn't private:)* the server asks the free AI service for a better split/label. On any error or limit, it skips silently.

**B. Sort**
1. Open Inbox → each dump shows its suggested chip highlighted.
2. Tap a chip (or **Split** first) → becomes an item in that pile.

**C. Pause**
1. Tap Pause → record ~10 s → finish "Next, I need to…" → optional tag/link → Save.

**D. Resume**
1. Open Paused → tap a card → audio plays, transcript and next step show.
2. Tap "Back on it" → `resumed_at` is set and the card moves to history.

---

## 6. Privacy and security

- Every database query is filtered by the signed-in user's ID. You can only ever see your own data.
- Audio files are stored with unguessable URLs and are only served to their owner.
- **AI assist is off by default.** With it off, your words never leave the app's own servers.
- 🔒 Private captures are never sent to AI, even with assist on.
- Free AI tiers may use what you send to improve their models; check the provider's current terms before turning AI assist on. (This is why it's off by default and private mode exists.)
- Secrets (API keys, DB URL) live in Vercel environment variables, never in the code.
- "Delete everything" button in settings: removes all captures, items, checkpoints and audio.

---

## 7. Costs

**$0.** Vercel Hobby, Neon free tier, Vercel Blob free tier, Google sign-in, in-browser voice recognition and the free AI tier are all free at one-person scale.

Notes:
- Vercel Hobby is for non-commercial use. A paid plan would only be needed if this became a business.
- Free tiers have limits (storage, requests per day). One person's brain dumps are far below them.

---

## 8. Risks and open questions

| Risk / question | Plan |
|---|---|
| Smart guess is wrong sometimes | It only pre-selects; you tap the right chip |
| Free AI tier changes or disappears | The app never depends on it; provider is swappable in one line |
| Browser voice recognition quality varies | Always keep the audio; type as fallback |
| iPhone push notifications need the app "installed" to the home screen | Covered in v2; onboarding will explain it |
| Should Brain Dump share login + data with ADHD OS? | **Recommended: yes**, via a shared monorepo |

---

## 9. Build milestones

1. Scaffold the project, sign-in, database, deploy an empty app to Vercel.
2. Text capture + Inbox.
3. One-tap sorting, smart-guess rules, date detection, split, views.
4. Voice capture + audio storage.
5. Pause / Resume.
6. PWA install + offline queue.
7. Optional AI assist switch (free tier) + private toggle.
8. Polish: search, delete-everything, empty states.
