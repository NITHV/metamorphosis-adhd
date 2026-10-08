import type { Metadata } from "next";
import { PilePage } from "@/components/piles/pile-page";

export const metadata: Metadata = { title: "Ideas · Brain Dump" };

export default function Page() {
  return <PilePage kind="idea" />;
}
