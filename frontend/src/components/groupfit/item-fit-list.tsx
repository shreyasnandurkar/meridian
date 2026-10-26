"use client";

import { useState } from "react";
import Link from "next/link";
import { ChevronDown, CircleHelp, Heart, MinusCircle, OctagonAlert, PlusCircle, ThumbsUp, X } from "lucide-react";

import { ScoreMeter } from "@/components/groupfit/fit-status";
import type { FitEffect, FitVerdict, ItemFit, ReactionValue } from "@/lib/types";
import { cn } from "@/lib/utils";

const VERDICT: Record<FitVerdict, { label: string; className: string }> = {
  SATISFIES: { label: "Works for them", className: "text-success" },
  NEUTRAL: { label: "Could go either way", className: "text-warning" },
  MISSES: { label: "Not for them", className: "text-destructive" },
  UNKNOWN: { label: "No preferences yet", className: "text-muted-foreground" },
};

const EFFECT_ICON: Record<FitEffect, { icon: typeof PlusCircle; className: string; sr: string }> = {
  PLUS: { icon: PlusCircle, className: "text-success", sr: "For" },
  MINUS: { icon: MinusCircle, className: "text-warning", sr: "Against" },
  BLOCKER: { icon: OctagonAlert, className: "text-destructive", sr: "Blocker" },
  NOTE: { icon: CircleHelp, className: "text-muted-foreground", sr: "Note" },
};

const REACTION_ICON: Record<ReactionValue, typeof Heart> = { LOVE: Heart, OK: ThumbsUp, NO_WAY: X };

/** Per-item group-fit score with who it satisfies, who it doesn't, and why (spec §5.4). */
export function ItemFitList({ tripId, items }: { tripId: string; items: ItemFit[] }) {
  return (
    <section aria-labelledby="items-heading">
      <div className="mb-3">
        <h3 id="items-heading" className="font-medium">
          How each shortlisted place fits the group
        </h3>
        <p className="text-muted-foreground text-sm">
          Average of everyone&apos;s fit, pulled down by the least happy person so one dealbreaker isn&apos;t averaged
          away. Reactions override the estimate.
        </p>
      </div>
      {items.length === 0 ? (
        <p className="text-muted-foreground rounded-lg border border-dashed px-4 py-8 text-center text-sm">
          Nothing on the shortlist yet.{" "}
          <Link href={`/trips/${tripId}?tab=shortlist`} className="text-foreground underline underline-offset-2">
            Add places or get suggestions
          </Link>
        </p>
      ) : (
        <ul className="space-y-2">
          {items.map((item) => (
            <ItemFitRow key={item.itemId} item={item} />
          ))}
        </ul>
      )}
    </section>
  );
}

function ItemFitRow({ item }: { item: ItemFit }) {
  const [open, setOpen] = useState(false);
  const panelId = `fit-${item.itemId}`;
  return (
    <li className="bg-card rounded-lg border">
      <button
        type="button"
        aria-expanded={open}
        aria-controls={panelId}
        onClick={() => setOpen((o) => !o)}
        className="focus-visible:ring-ring/50 flex w-full flex-wrap items-center gap-x-4 gap-y-2 rounded-lg px-4 py-3 text-left outline-none focus-visible:ring-[3px]"
      >
        <span className="min-w-0 flex-1">
          <span className="block truncate font-medium">{item.title}</span>
          <span className="text-muted-foreground block text-sm">{item.summary}</span>
        </span>
        <ScoreMeter score={item.score} label={`Group fit for ${item.title}`} />
        <ChevronDown className={cn("text-muted-foreground size-4 transition-transform", open && "rotate-180")} aria-hidden />
        <span className="sr-only">{open ? "Hide" : "Show"} breakdown</span>
      </button>
      {open && (
        <ul id={panelId} className="grid gap-3 border-t px-4 py-4 sm:grid-cols-2">
          {item.members.map((m) => {
            const verdict = VERDICT[m.verdict];
            const ReactionIcon = m.reaction ? REACTION_ICON[m.reaction] : null;
            return (
              <li key={m.userId} className="rounded-md border p-3">
                <div className="flex items-baseline justify-between gap-2">
                  <span className="font-medium">
                    {m.displayName}
                    {ReactionIcon && <ReactionIcon className="text-muted-foreground ml-1.5 inline size-3.5" aria-hidden />}
                  </span>
                  <span className={cn("text-xs font-medium", verdict.className)}>
                    {verdict.label}
                    {m.score != null && <span className="figures text-muted-foreground"> · {m.score}</span>}
                  </span>
                </div>
                <ul className="mt-2 space-y-1">
                  {m.reasons.map((r, i) => {
                    const e = EFFECT_ICON[r.effect];
                    return (
                      <li key={i} className="flex items-start gap-1.5 text-sm">
                        <e.icon className={cn("mt-0.5 size-3.5 shrink-0", e.className)} aria-hidden />
                        <span>
                          <span className="sr-only">{e.sr}: </span>
                          {r.text}
                        </span>
                      </li>
                    );
                  })}
                  {m.reasons.length === 0 && (
                    <li className="text-muted-foreground text-sm">Nothing stands out either way.</li>
                  )}
                </ul>
              </li>
            );
          })}
        </ul>
      )}
    </li>
  );
}
