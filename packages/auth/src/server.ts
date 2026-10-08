import { betterAuth } from "better-auth";
import { drizzleAdapter } from "better-auth/adapters/drizzle";
import { nextCookies, toNextJsHandler } from "better-auth/next-js";
import { db, schema } from "@repo/db";

const googleClientId = process.env.GOOGLE_CLIENT_ID;
const googleClientSecret = process.env.GOOGLE_CLIENT_SECRET;

export const auth = betterAuth({
  database: drizzleAdapter(db, { provider: "pg", schema }),
  emailAndPassword: {
    enabled: true,
    autoSignIn: true,
    // TODO: turn on requireEmailVerification once email sending (src/email.ts) is set up.
  },
  // Google is enabled once its keys are set.
  socialProviders:
    googleClientId && googleClientSecret
      ? { google: { clientId: googleClientId, clientSecret: googleClientSecret } }
      : {},
  account: {
    // Signing in with Google and with a password for the same email gives one account.
    accountLinking: { enabled: true, trustedProviders: ["google"] },
  },
  plugins: [
    nextCookies(), // must stay last
  ],
});

export const authHandler = toNextJsHandler(auth);

export const isGoogleEnabled = Boolean(googleClientId && googleClientSecret);

export type Session = typeof auth.$Infer.Session;
