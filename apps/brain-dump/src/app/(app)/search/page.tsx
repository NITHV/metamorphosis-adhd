import type { Metadata } from "next";
import Form from "next/form";
import Link from "next/link";
import { Suspense } from "react";
import { SearchIcon } from "@/components/icons";
import { RelativeTime } from "@/components/time";
import { getCurrentUser } from "@/lib/auth";
import { KIND_META } from "@/lib/kinds";
import { MAX_QUERY_LENGTH, searchEverything, type SearchResults } from "@/lib/search";

export const metadata: Metadata = { title: "Search · Brain Dump" };

export default function SearchPage({ searchParams }: PageProps<"/search">) {
  return (
    <div>
      <h1 className="text-3xl font-extrabold tracking-tight">Search</h1>
      <p className="mt-1 mb-6 text-muted">Everything you&apos;ve dumped, filed or paused.</p>
      <Suspense fallback={<SearchBox q="" />}>
        <SearchWithResults searchParams={searchParams} />
      </Suspense>
    </div>
  );
}

async function SearchWithResults({ searchParams }: { searchParams: PageProps<"/search">["searchParams"] }) {
  const { q: raw } = await searchParams;
  const q = (typeof raw === "string" ? raw : "").trim().slice(0, MAX_QUERY_LENGTH);
  const user = await getCurrentUser();
  return (
    <>
      <SearchBox q={q} />
      {q.length > 0 && <Results q={q} results={await searchEverything(user.id, q)} />}
    </>
  );
}

function SearchBox({ q }: { q: string }) {
  return (
    <Form action="/search" className="chunky flex items-center gap-2 rounded-2xl bg-card p-2 pl-4">
      <SearchIcon className="h-5 w-5 shrink-0 text-muted" />
      <label htmlFor="search-q" className="sr-only">
        Search
      </label>
      <input
        key={q}
        id="search-q"
        name="q"
        type="search"
        defaultValue={q}
        autoFocus={!q}
        maxLength={MAX_QUERY_LENGTH}
        enterKeyHint="search"
        placeholder="milk, dentist, budget…"
        className="h-11 min-w-0 flex-1 bg-transparent text-base outline-none"
      />
      <button type="submit" className="chunky-sm press h-11 rounded-xl bg-brand px-4 font-bold text-brand-foreground">
        Search
      </button>
    </Form>
  );
}

/** Shows `text` with every case-insensitive match of `q` highlighted. */
function Highlight({ text, q }: { text: string; q: string }) {
  const lower = text.toLowerCase();
  const needle = q.toLowerCase();
  const parts: React.ReactNode[] = [];
  let from = 0;
  for (let at = lower.indexOf(needle); at !== -1 && needle; at = lower.indexOf(needle, from)) {
    parts.push(text.slice(from, at), <mark key={at} className="rounded bg-brand-soft px-0.5 text-foreground">{text.slice(at, at + needle.length)}</mark>);
    from = at + needle.length;
  }
  parts.push(text.slice(from));
  return <>{parts}</>;
}

function Results({ q, results }: { q: string; results: SearchResults }) {
  const total = results.inbox.length + results.items.length + results.paused.length;
  if (total === 0) {
    return (
      <div className="mt-6 rounded-2xl border-2 border-dashed border-hairline px-4 py-10 text-center text-muted">
        <p>Nothing matches &ldquo;{q}&rdquo;.</p>
        <p className="mt-1 text-sm">Try a shorter word, like &ldquo;{q.split(/\s+/)[0].slice(0, 5)}&rdquo;.</p>
      </div>
    );
  }
  return (
    <div className="mt-6 flex flex-col gap-6">
      <p className="text-sm text-muted" role="status">
        {total} {total === 1 ? "result" : "results"}
      </p>
      {results.inbox.length > 0 && (
        <Group title="In your inbox">
          {results.inbox.map((r) => (
            <Row key={r.id} href="/inbox" meta={<RelativeTime iso={r.createdAt} />}>
              <Highlight text={r.text} q={q} />
            </Row>
          ))}
        </Group>
      )}
      {results.items.length > 0 && (
        <Group title="In your piles">
          {results.items.map((r) => {
            const meta = KIND_META[r.kind];
            return (
              <Row
                key={r.id}
                href={meta.href}
                meta={r.archived ? "archived" : r.done ? "done" : meta.label}
                dot={meta.color}
                faded={r.archived || r.done}
              >
                <Highlight text={r.title} q={q} />
              </Row>
            );
          })}
        </Group>
      )}
      {results.paused.length > 0 && (
        <Group title="Paused">
          {results.paused.map((r) => (
            <Row key={r.id} href="/paused" meta={<RelativeTime iso={r.createdAt} />}>
              <Highlight text={r.text} q={q} />
            </Row>
          ))}
        </Group>
      )}
    </div>
  );
}

function Group({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section>
      <h2 className="mb-2 text-sm font-semibold tracking-wide text-muted">{title.toUpperCase()}</h2>
      <ul className="chunky divide-y-2 divide-hairline overflow-hidden rounded-2xl bg-card">{children}</ul>
    </section>
  );
}

function Row({
  href,
  meta,
  dot,
  faded,
  children,
}: {
  href: string;
  meta: React.ReactNode;
  dot?: string;
  faded?: boolean;
  children: React.ReactNode;
}) {
  return (
    <li>
      <Link href={href} className="flex items-start gap-3 px-4 py-3 transition hover:bg-hairline/40">
        {dot && <span className="mt-2 h-2.5 w-2.5 shrink-0 rounded-full" style={{ background: dot }} aria-hidden />}
        <span className={`min-w-0 flex-1 whitespace-pre-wrap break-words leading-snug ${faded ? "text-muted" : ""}`}>
          {children}
        </span>
        <span className="shrink-0 pt-0.5 text-xs text-muted">{meta}</span>
      </Link>
    </li>
  );
}
