import { CheckCircle2, CircleHelp, ClipboardCheck, OctagonAlert, Scale, type LucideIcon } from "lucide-react";

import type { FitStatus } from "@/lib/types";
import { cn } from "@/lib/utils";

/** Status is always shown as icon + word + colour, never colour alone (spec §5.4). */
export const FIT_STATUS: Record<FitStatus, { label: string; icon: LucideIcon; text: string; tint: string }> = {
  AGREE: { label: "Agree", icon: CheckCircle2, text: "text-success", tint: "bg-success/10 border-success/30" },
  TRADE_OFF: { label: "Trade-off", icon: Scale, text: "text-warning", tint: "bg-warning/12 border-warning/40" },
  CONFLICT: {
    label: "Conflict",
    icon: OctagonAlert,
    text: "text-destructive",
    tint: "bg-destructive/10 border-destructive/35",
  },
  PLAN_AROUND: {
    label: "Plan around",
    icon: ClipboardCheck,
    text: "text-cat-sights",
    tint: "bg-cat-sights/10 border-cat-sights/30",
  },
  UNKNOWN: { label: "Not enough data", icon: CircleHelp, text: "text-muted-foreground", tint: "bg-muted border-border" },
};

export function StatusPill({ status, className }: { status: FitStatus; className?: string }) {
  const meta = FIT_STATUS[status];
  const Icon = meta.icon;
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs font-medium whitespace-nowrap",
        meta.tint,
        className,
      )}
    >
      <Icon className={cn("size-3.5", meta.text)} aria-hidden />
      {meta.label}
    </span>
  );
}

/** 0–100 group-fit score with a bar; the number is always printed. */
export function ScoreMeter({ score, label }: { score: number | null; label: string }) {
  if (score == null) {
    return <span className="text-muted-foreground text-xs">No score yet</span>;
  }
  const tone = score >= 65 ? "bg-success" : score >= 40 ? "bg-warning" : "bg-destructive";
  const word = score >= 65 ? "Good fit" : score >= 40 ? "Mixed" : "Poor fit";
  return (
    <div className="flex items-center gap-2" title={`${label}: ${score} out of 100`}>
      <div
        role="meter"
        aria-label={label}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={score}
        aria-valuetext={`${score} out of 100, ${word.toLowerCase()}`}
        className="bg-sounding h-1.5 w-20 overflow-hidden rounded-full"
      >
        <div className={cn("h-full rounded-full", tone)} style={{ width: `${score}%` }} />
      </div>
      <span className="figures text-sm font-medium tabular-nums">{score}</span>
      <span className="text-muted-foreground text-xs">{word}</span>
    </div>
  );
}
