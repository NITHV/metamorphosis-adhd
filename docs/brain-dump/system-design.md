# Brain Dump: System Design

**Brain dump inbox + "Where did I leave off?" in one app**

Status: **Approved** (monorepo, Google + email/password sign-in, names kept) · Date: 2026-10-10 · Revision 3: **AI parked. Photo dumps, home-screen shortcuts and "Share to Brain Dump" added. A separate, phone-only native Android app (no server, its own data) has its own design doc (`docs/brain-dump-android/`)**

---

## 1. The plain-English version

### What it is

Brain Dump is a place to throw every thought the moment you have it, plus a "bookmark for your brain" when you get pulled away from a task.

It does two jobs:

1. **Catch thoughts.** You get an idea in the middle of something else. You tap one big button and type it, say it out loud, or **snap a photo of it**. That's it. You go back to what you were doing. Later, whenever you feel like it, you sort your dumps into piles (*tasks*, *ideas*, *reminders*, *worries*) with a single tap each. The app pre-selects its best guess, so most of the time you just confirm.

2. **Save your place.** Before you switch tasks (or get interrupted), you tap **Pause** and record a 10-second voice note: *"I was halfway through the budget sheet, next I need to fill in March."* When you come back, tap **Resume** and it plays your note back, so you don't spend 20 minutes figuring out where you were.

### Why it helps with ADHD

- **No decisions at capture time.** Deciding where something goes is the step that makes people give up on to-do apps. Here you just dump it and sort later, when you have the energy.
- **Working memory gets offloaded.** Thoughts stop bouncing around your head because they are safely stored.
- **Less friction to capture.** Some thoughts are easier to photograph than to describe: a poster, a receipt, a whiteboard, a shelf label. One tap from the home screen gets you straight to the camera.
- **Task switching hurts less.** The "Pause / Resume" note is like leaving a sticky note for future you.

### What does it cost?

**Nothing.** Every piece runs on a free plan. There is **no AI** in the app for now (it was considered and parked). Sorting suggestions come from simple, free rules that run on your own device.

### How it works, as an analogy

Think of a small office:

| Real-world piece | In the app |
|---|---|
| A **mail slot** in the door where you drop notes | The capture button (text, voice or photo) |
| **Colored folders**, with a sticky note guessing which folder each note belongs in | One-tap sorting with smart suggestions (simple rules, free) |
| A **filing cabinet** | The database, where everything is saved |
| A **locked photo drawer and voice recorder** on the desk | Private file storage for your photos and voice notes |
| A **receptionist** who checks your ID | Sign-in (so only you see your stuff) |
| The **building** everything lives in | Vercel, which hosts the website |

You open the app on your phone or laptop (it's a website you can "install" to your home screen like an app). Everything syncs between devices.

---

## 2. Features (version 1)

### Look and feel
- Warm cream (light) / near-black (dark) theme, chunky outlined cards with hard offset shadows, rounded pill badges.
- Home: green welcome card (greeting, weekly stats, 13-week activity grid, forgiving day streak) with the original Brain Dump mascot (a small cartoon brain holding a sticky note), then the Dump box and Inbox.
- Sidebar on desktop (Home, Inbox, Tasks, Ideas, Reminders, Worries, Paused, Settings); bottom tab bar on phones (Home, Inbox, Piles, Paused, More).
- Mobile-first controls: 40px+ tap targets, no hover-only actions, inline action rows instead of popups, safe-area padding, installable to the home screen (web app manifest + icons) and works offline.

### Capture
- One giant **Dump** box on the home screen, plus a keyboard shortcut (`Ctrl/Cmd + K`) on desktop.
- **Text**, **voice** or **photo**.
  - Voice is turned into text in the browser (free) and the audio is kept so you can replay it.
  - **Photo** (new): a 📷 button next to the mic. On a phone you can **take a photo** with the camera or **pick one from your gallery**. A caption is optional; a photo on its own is a complete dump.
- Works offline: every dump (text, voice or photo) is saved on the device first and uploads when you're back online.

### Photo dumps (new in revision 3)
- **Shrunk on your phone before upload.** The browser resizes the photo so its longest side is at most 1600 px and saves it as WebP (JPEG where WebP isn't supported). A 4 MB phone photo becomes roughly 150 to 300 KB, which keeps uploads fast on mobile data and keeps us far inside free storage.
- **Location removed.** Phones hide extra data inside photos, including the **GPS location** where it was taken. Re-drawing the photo in the browser throws all of that away, so it never reaches our servers.
- **Private.** Photos sit in the same private storage as voice notes and are only ever shown to their owner.
- **In the Inbox and piles**, a photo dump shows a thumbnail. Tap it to see the photo full-screen. Sorting works exactly like any other dump: one tap on a pile chip. If there's a caption, the smart-guess rules read it (so "concert Sat 8pm" still becomes a Reminder with a date).
- Photo dumps can't be **split** (there's only one photo), so the Split button is hidden for them.
- No text is read out of photos (no OCR). That was considered and left out on purpose.

### Home-screen shortcuts (new in revision 3)
For phones where Brain Dump is installed to the home screen:
- **Long-press the app icon** to get four shortcuts: **Type · Voice · Photo · Pause**. Each opens the app straight into that mode.
- On Android you can **drag any shortcut onto the home screen** as its own one-tap icon, for example a dedicated "📷 Dump" button.
- Browsers only open the camera after a real tap on the page, so the Photo shortcut opens the photo sheet with a big **Take photo** button: one extra tap.
- Android picks up new shortcuts by itself, usually within a day. Re-installing the app makes it immediate.
- iPhones don't support these shortcuts for installed web apps.
- **Real widgets** (live counts, a dump bar on the home screen) need a native app. That is the separate phone-only Android app in `docs/brain-dump-android/system-design.md`, which keeps its own data on the phone and does not sync with the website.

### "Share to Brain Dump" (new in revision 3)
- In any Android app (Gallery, Chrome, WhatsApp, Files…) tap **Share → Brain Dump**.
- A shared **photo** becomes a photo dump (several photos become one dump each). Shared **text or a link** becomes a text dump.
- It's saved immediately, with no extra screen to fill in, and you land on Home with a "Dumped ✓" message. You can sort it later like anything else.
- This works even with no signal: the shared item waits in the device outbox.
- Not available on iPhone (Apple doesn't support sharing into installed web apps).

### Sorting (rules, on your device)
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

### AI assist (parked)
- An optional, free-tier AI helper was designed in revision 2 and has been **parked**. Nothing in the app uses AI. The data model keeps `is_private` and `suggested_by` so it could return later without a database change.

### Pause / Resume ("Where did I leave off?")
- **Pause**: record ~10 seconds of voice (or type), finish the sentence *"Next, I need to…"*, and optionally pick a project tag and paste the link/file you were working in.
- **Resume**: a list of paused things, newest first. Tap one to hear your note and read the transcript.
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
  │              │      │   • private file routes     │
  │ Voice → text │      │     (owner only)            │
  │ Photo shrink │      └─────┬──────────┬────────────┘
  │ + GPS strip  │            │          │
  │ Smart-guess  │            ▼          ▼
  │ rules + date │      ┌──────────┐ ┌──────────────┐
  │ Outbox       │      │ Postgres │ │ Vercel Blob  │
  │ (offline)    │      │ (Neon,   │ │ (private,    │
  └──────────────┘      │ free)    │ │ free)        │
     ▲      ▲           │ text +   │ │ voice/ and   │
     │      │           │ items    │ │ photo/ files │
 Share    Home-screen   └──────────┘ └──────────────┘
 sheet    shortcuts
```

Photos and voice notes are uploaded **straight from the phone to Blob** with a short-lived permission slip (token) from our server, so big files never pass through our server on the way in. On the way out, our server checks you own the file before handing it over.

### Tech choices (all free)

| Layer | Choice | Why |
|---|---|---|
| Framework | **Next.js (App Router) + TypeScript** | First-class on Vercel; UI and server code in one project |
| Styling | **Tailwind CSS** | Fast to build, clean, accessible components |
| Hosting | **Vercel Hobby** | Free for personal projects; deploys on every push |
| Database | **Neon Postgres** (via Vercel Marketplace) + **Drizzle ORM** | Free tier, works with serverless, type-safe queries |
| File storage | **Vercel Blob** (private store) | Voice recordings and photos; shrunk photos are small, so the free tier lasts a long time |
| Auth | **Better Auth**: Google sign-in + **email/password** | Free. Passwords are stored hashed. Magic links postponed |
| Smart guess | **Keyword rules + `chrono-node`** | Free, instant, runs on the device, private |
| Voice → text | **Web Speech API** for live words (desktop) + **on-device Whisper** (tiny.en via transformers.js in a Web Worker) | Free. Whisper runs fully on the device (one-time ~40 MB model download, cached) and covers phones and Firefox. Note: Chrome/Edge live recognition sends audio to Google/Microsoft |
| Photo shrinking | Browser `createImageBitmap` + `<canvas>` → WebP/JPEG | Built into every modern browser; no library; strips hidden data (EXIF/GPS) as a side effect |
| Shortcuts | Web app manifest **`shortcuts`** | Long-press menu + pinnable icons on Android |
| Share into the app | Web app manifest **`share_target`** + the service worker | The service worker catches the shared files, so sharing works even offline |
| Offline | **Durable IndexedDB outbox** + **service worker** + Next.js `experimental.useOffline` | Every dump (text, voice, photo) is written on the device first and delivered when there's signal, even after the app was closed. Client-chosen UUIDs make delivery idempotent (no duplicates), and dumps keep the time they were made. Sorting and Pause need a connection |

### Why sorting doesn't block capture
Capturing must feel instant. So the app **saves the raw dump on the device first** and replies right away. The smart guess runs instantly on your device. Uploading happens in the background.

---

## 4. Data model

```
users            (managed by Better Auth)
  id, name, email, image, ai_assist_enabled (default false, unused while AI is parked)

captures         one per "dump" (the raw thing you typed/said/photographed)
  id, user_id, raw_text, audio_url?, photo_url? (new), source (text|voice|photo),
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

One capture → one or more items. Items keep a link back to the original capture, so you can always see/hear exactly what you dumped, including its photo.

A photo dump with no caption is stored with the text "📷 Photo", so every list still has something to read.

---

## 5. Key flows

**A. Voice dump**
1. Tap the mic → speak → text appears (in the browser, free).
2. Save → the dump goes into the device outbox → the audio uploads to Blob and the capture is saved in Postgres. The screen shows "Saved ✓" immediately.
3. Smart-guess rules pre-select a chip and detect any date.

**B. Photo dump (new)**
1. Tap 📷 → **Take photo** or **Choose from gallery**.
2. The browser shrinks the photo and strips its hidden data, then shows a preview with an optional caption box.
3. Tap **Dump it** → the photo goes into the device outbox → uploads to `photo/<your id>/<dump id>.webp` in the private Blob store → the capture is saved. "Saved ✓" shows immediately, even offline.
4. The Inbox shows a thumbnail; tap a chip to sort it.

**C. Share to Brain Dump (new)**
1. In Gallery (or any app) tap **Share → Brain Dump**.
2. The app's service worker receives the photo(s) or text and keeps them on the device.
3. Brain Dump opens on Home, shrinks any photos, puts everything in the outbox, and shows "Dumped ✓".

**D. Sort**
1. Open Inbox → each dump shows its suggested chip highlighted.
2. Tap a chip (or **Split** first, for text) → becomes an item in that pile.

**E. Pause**
1. Tap Pause → record ~10 s → finish "Next, I need to…" → optional tag/link → Save.

**F. Resume**
1. Open Paused → tap a card → audio plays, transcript and next step show.
2. Tap "Back on it" → `resumed_at` is set and the card moves to history.

---

## 6. Privacy and security

- Every database query is filtered by the signed-in user's ID. You can only ever see your own data.
- Audio files **and photos** live in a **private** Vercel Blob store and are only served to their owner through an authenticated route. Uploads go straight from the browser to Blob using short-lived tokens that only allow writing into the user's own folder, only the right file type (audio or image), and only up to a size limit.
- **Photos lose their hidden data (including GPS location) on the phone, before upload.**
- Live transcription in Chrome/Edge uses the browser vendor's speech service; on-device Whisper keeps audio local. Both are explained on the privacy page.
- **No AI service is used.** Your dumps never leave our own servers and storage.
- Secrets (DB URL, storage token) live in Vercel environment variables, never in the code.
- "Delete everything" button in settings (built in milestone 8): removes all captures, items, checkpoints, audio **and photos**, and also whatever is still waiting on the device to upload. The account stays. "Download a copy" (a JSON file) comes first, so nothing has to be lost by accident.

> **🎓 SRE lesson: deleting is a distributed problem.** Your data lives in four places: the database, the file store, the phone's upload queue, and pages cached for offline use. "Delete everything" has to reach all four, in a safe order:
> 1. **The device queue first.** Otherwise a dump still waiting to upload could arrive a second *after* the server was wiped, and quietly bring itself back. The app waits for any upload already in flight to finish, then empties the queue.
> 2. **Database rows next**, all in one batch, so it's all-or-nothing.
> 3. **Files last, by folder, not by the links in the rows.** Listing the folder also finds **orphans** (a photo whose upload finished but whose dump never got saved). It also makes a **retry safe**: if deleting files fails halfway, pressing the button again just finishes the job (it's *idempotent*).
>
> The end-to-end test plants an orphan file and a dump waiting offline, then checks that all of it is gone and stays gone.

> **🎓 Design lesson: the simplest search that works.** Search is a plain "contains" query on the database. For one person's few thousand notes it takes a few milliseconds, so there's no search engine to run, pay for or keep in sync. If it ever gets slow, a database index (Postgres `pg_trgm`) speeds up the same query without changing the app. One detail: `%` and `_` are wildcards in SQL, so they're escaped. Searching "100%" finds "100%", not everything.

---

## 7. Costs

**$0.** Vercel Hobby, Neon free tier, Vercel Blob free tier, Google sign-in and in-browser voice recognition are all free at one-person scale.

Notes:
- Vercel Hobby is for non-commercial use. A paid plan would only be needed if this became a business.
- Free tiers have limits (storage, requests per day). Shrunk photos are ~150 to 300 KB, so even thousands of photo dumps stay far below the storage limit.

---

## 8. Risks and open questions

| Risk / question | Plan |
|---|---|
| Smart guess is wrong sometimes | It only pre-selects; you tap the right chip |
| Browser voice recognition quality varies | Always keep the audio; type as fallback |
| Photos fill up free storage | Shrunk on the phone first; "Delete everything" removes them; storage use can be checked in Vercel |
| An old phone browser can't make WebP | Falls back to JPEG automatically |
| Shortcuts / Share don't appear on the phone yet | Android refreshes installed web apps by itself (usually within a day); re-installing makes it immediate |
| iPhone: no shortcuts, no Share to Brain Dump | Documented; the Dump box still works there. Real widgets come with the separate Android app (see the Android doc) |
| iPhone push notifications need the app "installed" to the home screen | Covered in v2; onboarding will explain it |
| Should Brain Dump share login + data with ADHD OS? | **Recommended: yes**, via a shared monorepo |

---

## 9. Build milestones

1. ✅ Scaffold the project, sign-in, database, deploy an empty app to Vercel.
2. ✅ Text capture + Inbox.
3. ✅ One-tap sorting, smart-guess rules, date detection, split, views.
4. ✅ Voice capture + audio storage.
5. ✅ Pause / Resume.
6. ✅ PWA install + offline queue.
7. ✅ **Photo dumps** (camera or gallery, optional caption, shrink + GPS strip on the device, private storage, offline outbox, thumbnails and full-screen view) **+ home-screen shortcuts + Share to Brain Dump.**
8. ✅ **Polish**: search across everything, settings page, "Download a copy", "Delete everything" (incl. photos, voice files and the device queue), empty states.

Running alongside: the separate **Brain Dump Android app** (phone-only, native, with real home-screen widgets), see `docs/brain-dump-android/system-design.md`. The two share no code or data.

Parked: AI assist (free-tier AI sorting). Not planned for now.
