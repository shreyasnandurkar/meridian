import { BedDouble, Compass, Landmark, MapPin, UtensilsCrossed, type LucideIcon } from "lucide-react";

import type { PlaceCategory } from "@/lib/types";

export interface CategoryMeta {
  id: PlaceCategory;
  label: string;
  /** Singular, for "Add as a …" copy. */
  noun: string;
  icon: LucideIcon;
  /** CSS colour token (globals.css) used for map markers. */
  color: string;
  /** Tailwind classes for chips. */
  chip: string;
}

export const CATEGORIES: CategoryMeta[] = [
  { id: "SIGHTS", label: "Sights", noun: "sight", icon: Landmark, color: "var(--cat-sights)", chip: "text-cat-sights" },
  { id: "FOOD", label: "Food", noun: "food spot", icon: UtensilsCrossed, color: "var(--cat-food)", chip: "text-cat-food" },
  { id: "STAYS", label: "Stays", noun: "stay", icon: BedDouble, color: "var(--cat-stays)", chip: "text-cat-stays" },
  {
    id: "ACTIVITIES",
    label: "Activities",
    noun: "activity",
    icon: Compass,
    color: "var(--cat-activities)",
    chip: "text-cat-activities",
  },
  { id: "OTHER", label: "Other", noun: "place", icon: MapPin, color: "var(--cat-other)", chip: "text-cat-other" },
];

export const CATEGORY: Record<PlaceCategory, CategoryMeta> = Object.fromEntries(
  CATEGORIES.map((c) => [c.id, c]),
) as Record<PlaceCategory, CategoryMeta>;

/** Categories that can be browsed nearby (OTHER has no OpenStreetMap rule). */
export const BROWSABLE = CATEGORIES.filter((c) => c.id !== "OTHER");

const TAG_HINTS: [string, (v: string) => string][] = [
  ["cuisine", (v) => v.split(";").map((c) => c.replace(/_/g, " ")).join(", ")],
  ["historic", (v) => (v === "yes" ? "historic" : v.replace(/_/g, " "))],
  ["craft", (v) => `${v.replace(/_/g, " ")} workshop`],
  ["heritage", () => "heritage listed"],
  ["diet:vegetarian", (v) => (v === "yes" || v === "only" ? "vegetarian options" : "")],
  ["wheelchair", (v) => (v === "yes" ? "wheelchair accessible" : "")],
];

/** A few human-readable highlights from OpenStreetMap tags. */
export function tagHighlights(tags: Record<string, string>, max = 3): string[] {
  const out: string[] = [];
  for (const [key, fmt] of TAG_HINTS) {
    const v = tags[key];
    if (!v) continue;
    const text = fmt(v);
    if (text) out.push(text);
    if (out.length >= max) break;
  }
  return out;
}
