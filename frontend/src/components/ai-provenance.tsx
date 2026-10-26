"use client";

import { Cpu, HelpCircle, RefreshCw, Sparkles, X } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import type { Provenance } from "@/lib/types";

const FIELD_LABELS: Record<string, string> = { mustHaves: "Must-have", dealbreakers: "Dealbreaker" };

function describeField(field: string, labels: Record<string, string>) {
  if (field.startsWith("interests.")) return `Interest: ${labels[field.slice(10)] ?? field.slice(10)}`;
  return FIELD_LABELS[field] ?? field;
}

/**
 * Every AI output carries this: an "AI" (or "Rules") badge, a "Why?" popover explaining how it was produced,
 * and optional regenerate / dismiss controls (spec §4).
 */
export function AiProvenance({
  provenance,
  interestLabels = {},
  onRegenerate,
  onDismiss,
  regenerating,
}: {
  provenance: Provenance;
  interestLabels?: Record<string, string>;
  onRegenerate?: () => void;
  onDismiss?: () => void;
  regenerating?: boolean;
}) {
  const isAi = provenance.source === "ai";
  return (
    <div className="flex flex-wrap items-center gap-2">
      {isAi ? (
        <Badge variant="ai">
          <Sparkles aria-hidden /> AI
        </Badge>
      ) : (
        <Badge variant="secondary">
          <Cpu aria-hidden /> Rules
        </Badge>
      )}
      <Popover>
        <PopoverTrigger asChild>
          <Button variant="ghost" size="sm" className="h-7 px-2 text-xs">
            <HelpCircle aria-hidden /> Why?
          </Button>
        </PopoverTrigger>
        <PopoverContent align="start" className="space-y-3">
          <p className="font-medium">How this was made</p>
          <p className="text-muted-foreground">
            {isAi
              ? `Read by ${provenance.model} (prompt ${provenance.promptVersion}).`
              : "Built with deterministic rules — no AI model was used."}{" "}
            {provenance.note}
          </p>
          {provenance.inferences.length > 0 ? (
            <div>
              <p className="mb-1.5 text-sm font-medium">Picked up from your description</p>
              <ul className="space-y-1.5">
                {provenance.inferences.map((inf, i) => (
                  <li key={i} className="bg-muted rounded-md px-2.5 py-1.5">
                    <span className="font-medium">{describeField(inf.field, interestLabels)}</span>
                    {inf.field.startsWith("interests.") ? ` · weight ${inf.value}` : `: ${inf.value}`}
                    <span className="text-muted-foreground mt-0.5 block text-xs">“{inf.evidence}”</span>
                  </li>
                ))}
              </ul>
            </div>
          ) : (
            <p className="text-muted-foreground text-xs">Nothing extra was inferred; your quiz answers were used as given.</p>
          )}
        </PopoverContent>
      </Popover>
      {onRegenerate && (
        <Button variant="ghost" size="sm" className="h-7 px-2 text-xs" onClick={onRegenerate} disabled={regenerating}>
          <RefreshCw className={regenerating ? "animate-spin" : undefined} aria-hidden /> Regenerate
        </Button>
      )}
      {onDismiss && (
        <Button variant="ghost" size="sm" className="h-7 px-2 text-xs" onClick={onDismiss}>
          <X aria-hidden /> Dismiss
        </Button>
      )}
    </div>
  );
}
