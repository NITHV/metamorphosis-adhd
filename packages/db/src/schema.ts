import { boolean, index, pgEnum, pgTable, text, timestamp } from "drizzle-orm/pg-core";

const timestamps = {
  createdAt: timestamp("created_at").notNull().defaultNow(),
  updatedAt: timestamp("updated_at")
    .notNull()
    .defaultNow()
    .$onUpdate(() => new Date()),
};

// ---------------------------------------------------------------------------
// Auth tables (shape required by Better Auth)
// ---------------------------------------------------------------------------

export const user = pgTable("user", {
  id: text("id").primaryKey(),
  name: text("name").notNull(),
  email: text("email").notNull().unique(),
  emailVerified: boolean("email_verified").notNull().default(false),
  image: text("image"),
  // App setting, not managed by Better Auth. Off by default (see design doc §6).
  aiAssistEnabled: boolean("ai_assist_enabled").notNull().default(false),
  ...timestamps,
});

export const session = pgTable(
  "session",
  {
    id: text("id").primaryKey(),
    expiresAt: timestamp("expires_at").notNull(),
    token: text("token").notNull().unique(),
    ipAddress: text("ip_address"),
    userAgent: text("user_agent"),
    userId: text("user_id")
      .notNull()
      .references(() => user.id, { onDelete: "cascade" }),
    ...timestamps,
  },
  (t) => [index("session_user_id_idx").on(t.userId)],
);

export const account = pgTable(
  "account",
  {
    id: text("id").primaryKey(),
    accountId: text("account_id").notNull(),
    providerId: text("provider_id").notNull(),
    userId: text("user_id")
      .notNull()
      .references(() => user.id, { onDelete: "cascade" }),
    accessToken: text("access_token"),
    refreshToken: text("refresh_token"),
    idToken: text("id_token"),
    accessTokenExpiresAt: timestamp("access_token_expires_at"),
    refreshTokenExpiresAt: timestamp("refresh_token_expires_at"),
    scope: text("scope"),
    password: text("password"),
    ...timestamps,
  },
  (t) => [index("account_user_id_idx").on(t.userId)],
);

export const verification = pgTable(
  "verification",
  {
    id: text("id").primaryKey(),
    identifier: text("identifier").notNull(),
    value: text("value").notNull(),
    expiresAt: timestamp("expires_at").notNull(),
    ...timestamps,
  },
  (t) => [index("verification_identifier_idx").on(t.identifier)],
);

// ---------------------------------------------------------------------------
// Brain Dump tables (design doc §4)
// ---------------------------------------------------------------------------

export const captureSource = pgEnum("capture_source", ["text", "voice"]);
export const captureStatus = pgEnum("capture_status", ["inbox", "sorted", "archived"]);
export const itemKind = pgEnum("item_kind", ["task", "idea", "reminder", "worry"]);
export const suggestedBy = pgEnum("suggested_by", ["rules", "ai"]);

/** One per "dump": the raw thing you typed or said. */
export const captures = pgTable(
  "captures",
  {
    id: text("id").primaryKey().$defaultFn(() => crypto.randomUUID()),
    userId: text("user_id")
      .notNull()
      .references(() => user.id, { onDelete: "cascade" }),
    rawText: text("raw_text").notNull(),
    audioUrl: text("audio_url"),
    source: captureSource("source").notNull().default("text"),
    isPrivate: boolean("is_private").notNull().default(false),
    status: captureStatus("status").notNull().default("inbox"),
    suggestedKind: itemKind("suggested_kind"),
    suggestedBy: suggestedBy("suggested_by"),
    ...timestamps,
  },
  (t) => [index("captures_user_status_idx").on(t.userId, t.status)],
);

/** What a capture was sorted/split into. */
export const items = pgTable(
  "items",
  {
    id: text("id").primaryKey().$defaultFn(() => crypto.randomUUID()),
    userId: text("user_id")
      .notNull()
      .references(() => user.id, { onDelete: "cascade" }),
    captureId: text("capture_id").references(() => captures.id, { onDelete: "set null" }),
    kind: itemKind("kind").notNull(),
    title: text("title").notNull(),
    notes: text("notes"),
    dueAt: timestamp("due_at"),
    doneAt: timestamp("done_at"),
    archivedAt: timestamp("archived_at"),
    ...timestamps,
  },
  (t) => [index("items_user_kind_idx").on(t.userId, t.kind)],
);

/** "Where did I leave off?" notes. */
export const checkpoints = pgTable(
  "checkpoints",
  {
    id: text("id").primaryKey().$defaultFn(() => crypto.randomUUID()),
    userId: text("user_id")
      .notNull()
      .references(() => user.id, { onDelete: "cascade" }),
    label: text("label"),
    projectTag: text("project_tag"),
    link: text("link"),
    audioUrl: text("audio_url"),
    transcript: text("transcript").notNull().default(""),
    nextStep: text("next_step"),
    resumedAt: timestamp("resumed_at"),
    dismissedAt: timestamp("dismissed_at"),
    ...timestamps,
  },
  (t) => [index("checkpoints_user_idx").on(t.userId)],
);
