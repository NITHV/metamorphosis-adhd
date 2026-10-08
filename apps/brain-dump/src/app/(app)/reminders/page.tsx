import type { Metadata } from "next";
import { PilePage } from "@/components/piles/pile-page";

export const metadata: Metadata = { title: "Reminders · Brain Dump" };

export default function Page() {
  return <PilePage kind="reminder" />;
}
