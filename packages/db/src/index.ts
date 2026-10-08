import { setDefaultAutoSelectFamilyAttemptTimeout } from "node:net";
import { neon } from "@neondatabase/serverless";
import { drizzle, type NeonHttpDatabase } from "drizzle-orm/neon-http";
import * as schema from "./schema";

export type Db = NeonHttpDatabase<typeof schema>;

// Node tries IPv6 and IPv4 addresses in turn, giving each only 250ms by default. On slower
// networks every attempt can time out ("fetch failed"), so allow each one a full second.
setDefaultAutoSelectFamilyAttemptTimeout(1000);

function createDb(): Db {
  const url = process.env.DATABASE_URL;
  if (!url) {
    throw new Error("DATABASE_URL is not set. Add it to apps/<app>/.env.local (see .env.example).");
  }
  return drizzle({ client: neon(url), schema });
}

let instance: Db | undefined;

// Connect on first use rather than at import time, so `next build` works
// without database credentials.
export const db = new Proxy({} as Db, {
  get(_target, prop) {
    instance ??= createDb();
    const value = Reflect.get(instance, prop, instance);
    return typeof value === "function" ? value.bind(instance) : value;
  },
});

export { schema };
