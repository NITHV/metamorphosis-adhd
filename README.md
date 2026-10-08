# Metamorphosis: ADHD apps

Monorepo for two free-to-run apps for ADHD brains:

| App | What it does | Design doc |
|---|---|---|
| `apps/brain-dump` | Catch every thought (text/voice), sort later; Pause/Resume voice bookmarks | [docs/brain-dump](docs/brain-dump/system-design.md) |
| `apps/adhd-os` *(phase 2)* | Task breaker, visual timer, body doubling rooms | [docs/adhd-os](docs/adhd-os/system-design.md) |

Shared code lives in `packages/`:

- `@repo/db`: Drizzle schema + Neon Postgres client (`packages/db/drizzle` holds SQL migrations)
- `@repo/auth`: Better Auth (Google sign-in + email/password)

## Run locally

```bash
npm install
cp apps/brain-dump/.env.example apps/brain-dump/.env.local   # fill in DATABASE_URL + BETTER_AUTH_SECRET
npm run db:migrate -w @repo/db                               # create tables
npm run dev -- --filter=brain-dump                           # http://localhost:3000
```

Local sign-in works with just a database (email/password). Google appears once its keys are set.

## Changing the database

1. Edit `packages/db/src/schema.ts`
2. `npm run db:generate -w @repo/db` (writes a new SQL migration)
3. `npm run db:migrate -w @repo/db`

## Stack (all free tiers)

Next.js 16 · Tailwind 4 · Better Auth · Drizzle + Neon Postgres · Vercel Blob · Turborepo · Vercel Hobby
