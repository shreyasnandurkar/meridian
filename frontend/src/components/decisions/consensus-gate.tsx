"use client";

import { useState } from "react";
import Link from "next/link";
import { ClipboardList, Lock, LockOpen, ShieldCheck } from "lucide-react";

import { VoteButtons, VoteTally } from "@/components/decisions/vote-tally";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { useAuth } from "@/lib/auth";
import { formatRelative } from "@/lib/format";
import { useGate, useLockItinerary, useUnlockItinerary, useVotePlan } from "@/lib/queries";
import type { PlanVoter } from "@/lib/types";
import { cn } from "@/lib/utils";

/**
 * The consensus gate (spec §5.8): everyone approves the plan as it is now, and "Lock itinerary" opens only when the
 * quorum has. Shows who has not voted. The organizer can lock without quorum by giving a reason, which is logged.
 */
export function ConsensusGate({ tripId, isOrganizer }: { tripId: string; isOrganizer: boolean }) {
  const { user } = useAuth();
  const gate = useGate(tripId, true);
  const votePlan = useVotePlan(tripId, { id: user!.id });
  const lock = useLockItinerary(tripId);
  const unlock = useUnlockItinerary(tripId);
  const [dialog, setDialog] = useState<"override" | "unlock" | null>(null);

  if (gate.isPending) return <div className="bg-muted h-40 animate-pulse rounded-xl" aria-label="Loading approvals" />;
  if (gate.isError) {
    return (
      <p role="alert" className="text-destructive text-sm">
        {gate.error.message}
      </p>
    );
  }
  const g = gate.data;
  const error = votePlan.error ?? lock.error ?? unlock.error;

  if (g.status === "LOCKED") {
    return (
      <section
        aria-labelledby="gate-heading"
        className="border-success/40 bg-success/8 flex flex-wrap items-center justify-between gap-4 rounded-xl border p-5"
      >
        <div className="flex items-start gap-3">
          <Lock className="text-success mt-0.5 size-5 shrink-0" aria-hidden />
          <div>
            <h3 id="gate-heading" className="font-medium">
              Itinerary locked
            </h3>
            <p className="text-muted-foreground text-sm">
              Locked by {g.lockedBy?.displayName ?? "the organizer"}
              {g.lockedAt && ` ${formatRelative(g.lockedAt)}`} with {g.tally.approvals} of {g.tally.members} approving.
              Nothing is booked: book each place yourselves from its link.
            </p>
            {g.lockDecisionId && (
              <Button variant="link" size="sm" className="px-0" asChild>
                <Link href={`?tab=decisions#decision-${g.lockDecisionId}`}>
                  <ClipboardList aria-hidden /> See the decision
                </Link>
              </Button>
            )}
          </div>
        </div>
        {isOrganizer && (
          <Button variant="outline" onClick={() => setDialog("unlock")}>
            <LockOpen aria-hidden /> Unlock
          </Button>
        )}
        <ReasonDialog
          open={dialog === "unlock"}
          onOpenChange={(o) => !o && setDialog(null)}
          title="Unlock the itinerary?"
          description="The plan becomes a draft again so it can change. The unlock is added to the decision log."
          label="Reason (optional)"
          required={false}
          confirm="Unlock"
          pending={unlock.isPending}
          error={unlock.error?.message}
          onConfirm={(reason) => unlock.mutate(reason, { onSuccess: () => setDialog(null) })}
        />
      </section>
    );
  }

  const { tally } = g;
  const stillNeeded = Math.max(0, tally.required - tally.approvals);
  return (
    <section
      aria-labelledby="gate-heading"
      className={cn("rounded-xl border p-5", g.quorumMet ? "border-success/50 bg-success/5" : "bg-card")}
    >
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="max-w-xl">
          <h3 id="gate-heading" className="inline-flex items-center gap-2 font-medium">
            <ShieldCheck className={cn("size-4", g.quorumMet ? "text-success" : "text-signal")} aria-hidden />
            Consensus before locking
          </h3>
          <p className="text-muted-foreground mt-1 text-sm">
            Does this plan work for you? Every member&apos;s vote counts the same. A change to the plan asks
            everyone again, except those who voted for that change.
          </p>
        </div>
        {g.canVote && (
          <VoteButtons
            mine={g.myVote}
            disabled={votePlan.isPending}
            onVote={(v) => votePlan.mutate(v)}
            approveLabel="Approve plan"
            rejectLabel="Not yet"
          />
        )}
      </div>

      <div className="mt-4 grid gap-4 md:grid-cols-[minmax(0,1fr)_auto] md:items-end" aria-live="polite">
        <div className="space-y-2">
          <VoteTally tally={tally} label="to lock this plan" />
          <PlanVoters voters={g.voters} />
        </div>
        {g.canLock && (
          <div className="flex flex-col items-start gap-1.5 md:items-end">
            <Button
              onClick={() => lock.mutate(null)}
              disabled={!g.quorumMet || lock.isPending}
              aria-describedby="lock-hint"
            >
              <Lock aria-hidden /> Lock itinerary
            </Button>
            <p id="lock-hint" className="text-muted-foreground text-xs">
              {g.quorumMet
                ? g.openProposals > 0
                  ? `Locking closes ${g.openProposals} open proposal${g.openProposals === 1 ? "" : "s"}.`
                  : "The group agrees: ready to lock."
                : `Needs ${stillNeeded} more approval${stillNeeded === 1 ? "" : "s"}.`}
            </p>
            {!g.quorumMet && (
              <Button variant="link" size="sm" className="h-auto px-0 text-xs" onClick={() => setDialog("override")}>
                Lock without quorum…
              </Button>
            )}
          </div>
        )}
      </div>
      {error && (
        <p role="alert" className="text-destructive mt-3 text-sm">
          {error.message}
        </p>
      )}
      <ReasonDialog
        open={dialog === "override"}
        onOpenChange={(o) => !o && setDialog(null)}
        title="Lock without the group's approval?"
        description={`Only ${tally.approvals} of the ${tally.required} approvals needed are in${
          g.notVoted.length ? `; not voted: ${g.notVoted.map((p) => p.displayName).join(", ")}` : ""
        }. The override and your reason go in the decision log for everyone to see.`}
        label="Reason (required)"
        required
        confirm="Lock anyway"
        pending={lock.isPending}
        error={lock.error?.message}
        onConfirm={(reason) => lock.mutate(reason, { onSuccess: () => setDialog(null) })}
      />
    </section>
  );
}

function PlanVoters({ voters }: { voters: PlanVoter[] }) {
  return (
    <ul className="flex flex-wrap gap-1.5 text-xs" aria-label="Who approved the plan">
      {voters.map((v) => (
        <li
          key={v.userId}
          className={cn(
            "inline-flex items-center gap-1 rounded-full border px-2 py-0.5",
            v.value === "APPROVE" && "border-success/40 bg-success/10",
            v.value === "REJECT" && "border-destructive/40 bg-destructive/10",
            v.value === null && "text-muted-foreground border-dashed",
          )}
        >
          <span aria-hidden>{v.value === "APPROVE" ? "✓" : v.value === "REJECT" ? "✕" : "○"}</span>
          {v.displayName}
          <span className={v.value === null ? "" : "sr-only"}>
            {v.value === "APPROVE"
              ? " approved"
              : v.value === "REJECT"
                ? " said not yet"
                : v.earlier
                  ? " · voted on an earlier version"
                  : " · not voted"}
          </span>
        </li>
      ))}
    </ul>
  );
}

function ReasonDialog({
  open,
  onOpenChange,
  title,
  description,
  label,
  required,
  confirm,
  pending,
  error,
  onConfirm,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description: string;
  label: string;
  required: boolean;
  confirm: string;
  pending: boolean;
  error?: string;
  onConfirm: (reason: string | null) => void;
}) {
  const [reason, setReason] = useState("");
  const blank = reason.trim().length === 0;
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        <form
          className="space-y-3"
          onSubmit={(e) => {
            e.preventDefault();
            if (required && blank) return;
            onConfirm(blank ? null : reason.trim());
          }}
        >
          <div className="space-y-1.5">
            <Label htmlFor="gate-reason">{label}</Label>
            <Textarea
              id="gate-reason"
              value={reason}
              maxLength={500}
              required={required}
              onChange={(e) => setReason(e.target.value)}
              placeholder={required ? "e.g. The guesthouse needs our booking by tonight" : ""}
            />
          </div>
          {error && (
            <p role="alert" className="text-destructive text-sm">
              {error}
            </p>
          )}
          <DialogFooter>
            <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={pending || (required && blank)}>
              {confirm}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
