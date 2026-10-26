"use client";

import { Check, CircleDashed, X } from "lucide-react";

import { Button } from "@/components/ui/button";
import type { PersonRef, Tally, VoteValue } from "@/lib/types";
import { cn } from "@/lib/utils";

/** Approvals against the quorum as a bar of seats: one per member, the quorum line marked. Text says it too. */
export function VoteTally({ tally, label }: { tally: Tally; label: string }) {
  const { approvals, rejections, required, members } = tally;
  const met = approvals >= required;
  const seats = Array.from({ length: members }, (_, i) =>
    i < approvals ? "approve" : i >= members - rejections ? "reject" : "open",
  );
  return (
    <div className="space-y-1.5">
      <div className="flex gap-1" aria-hidden>
        {seats.map((s, i) => (
          <span
            key={i}
            className={cn(
              "h-2 flex-1 rounded-full",
              s === "approve" ? "bg-success" : s === "reject" ? "bg-destructive/70" : "bg-muted",
              i === required - 1 && "ring-foreground/40 ring-2 ring-offset-1 ring-offset-[var(--card)]",
            )}
          />
        ))}
      </div>
      <p className="figures text-sm">
        <span className={cn("font-medium", met && "text-success")}>
          {approvals} of {required}
        </span>{" "}
        approvals needed {label}
        {rejections > 0 && <span className="text-muted-foreground"> · {rejections} against</span>}
        {met && <span className="text-success"> · quorum reached</span>}
      </p>
    </div>
  );
}

/** Approve / reject as a pressed-state pair; pressing your current vote again takes it back. */
export function VoteButtons({
  mine,
  onVote,
  disabled,
  approveLabel = "Approve",
  rejectLabel = "Reject",
  approveDisabledReason,
}: {
  mine: VoteValue | null;
  onVote: (value: VoteValue | null) => void;
  disabled?: boolean;
  approveLabel?: string;
  rejectLabel?: string;
  /** Why approving is not possible right now (the change no longer fits the plan). */
  approveDisabledReason?: string;
}) {
  return (
    <div role="group" aria-label="Your vote" className="flex flex-wrap gap-2">
      <Button
        size="sm"
        variant={mine === "APPROVE" ? "default" : "outline"}
        aria-pressed={mine === "APPROVE"}
        disabled={disabled || (!!approveDisabledReason && mine !== "APPROVE")}
        title={approveDisabledReason}
        onClick={() => onVote(mine === "APPROVE" ? null : "APPROVE")}
      >
        <Check aria-hidden /> {approveLabel}
      </Button>
      <Button
        size="sm"
        variant={mine === "REJECT" ? "destructive" : "outline"}
        aria-pressed={mine === "REJECT"}
        disabled={disabled}
        onClick={() => onVote(mine === "REJECT" ? null : "REJECT")}
      >
        <X aria-hidden /> {rejectLabel}
      </Button>
    </div>
  );
}

/** Who voted which way, and who has not voted yet — icon + word, never colour alone. */
export function Voters({
  votes,
  notVoted,
}: {
  votes: { person: PersonRef; value: VoteValue }[];
  notVoted: PersonRef[];
}) {
  return (
    <ul className="flex flex-wrap gap-1.5 text-xs" aria-label="Votes">
      {votes.map(({ person, value }) => (
        <li
          key={person.userId}
          className={cn(
            "inline-flex items-center gap-1 rounded-full border px-2 py-0.5",
            value === "APPROVE" ? "border-success/40 bg-success/10" : "border-destructive/40 bg-destructive/10",
          )}
        >
          {value === "APPROVE" ? (
            <Check className="text-success size-3" aria-hidden />
          ) : (
            <X className="text-destructive size-3" aria-hidden />
          )}
          {person.displayName}
          <span className="sr-only">{value === "APPROVE" ? " approved" : " rejected"}</span>
        </li>
      ))}
      {notVoted.map((p) => (
        <li
          key={p.userId}
          className="text-muted-foreground inline-flex items-center gap-1 rounded-full border border-dashed px-2 py-0.5"
        >
          <CircleDashed className="size-3" aria-hidden />
          {p.displayName}
          <span className="sr-only"> has not voted</span>
        </li>
      ))}
    </ul>
  );
}
