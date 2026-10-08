export const KINDS = ["task", "idea", "reminder", "worry"] as const;
export type Kind = (typeof KINDS)[number];

export const KIND_META: Record<Kind, { label: string; pile: string; href: string; color: string; soft: string }> = {
  task: { label: "Task", pile: "Tasks", href: "/tasks", color: "var(--pill-blue)", soft: "var(--pill-blue-soft)" },
  idea: { label: "Idea", pile: "Ideas", href: "/ideas", color: "var(--pill-orange)", soft: "var(--pill-orange-soft)" },
  reminder: {
    label: "Reminder",
    pile: "Reminders",
    href: "/reminders",
    color: "var(--pill-purple)",
    soft: "var(--pill-purple-soft)",
  },
  worry: { label: "Worry", pile: "Worries", href: "/worries", color: "var(--pill-green)", soft: "var(--pill-green-soft)" },
};

export function isKind(value: unknown): value is Kind {
  return typeof value === "string" && (KINDS as readonly string[]).includes(value);
}
