"use client";

import { useState } from "react";
import Link from "next/link";
import { AlertTriangle, ClipboardList, GitPullRequestArrow, MessageSquare, Undo2 } from "lucide-react";

import { VoteButtons, VoteTally, Voters } from "@/components/decisions/vote-tally";
import { CommentThread } from "@/components/itinerary/comment-thread";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/lib/auth";
import { formatRelative } from "@/lib/format";
import { useProposals, useVote, useWithdrawProposal } from "@/lib/queries";
import type { Proposal, ProposalStatus } from "@/lib/types";

const STATUS: Record<ProposalStatus, { label: string; variant: "ai" | "secondary" | "success" | "warning" | "outline" }> = {
  OPEN: { label: "Voting", variant: "ai" },
  WITHDRAWN: { label: "Withdrawn", variant: "secondary" },
  APPROVED: { label: "Approved · applied", variant: "success" },
  REJECTED: { label: "Rejected", variant: "outline" },
  STALE: { label: "Out of date", variant: "warning" },
  CLOSED: { label: "Closed by lock", variant: "secondary" },
};

/**
 * Members' proposed changes, voted on with equal weight (spec §5.8). A change is applied by code as soon as the
 * quorum approves it; votes update live for everyone.
 */
export function ProposalsPanel({ tripId }: { tripId: string }) {
  const proposals = useProposals(tripId);
  const [showClosed, setShowClosed] = useState(false);
  const all = proposals.data ?? [];
  const open = all.filter((p) => p.status === "OPEN");
  const closed = all.filter((p) => p.status !== "OPEN");

  return (
    <section aria-labelledby="proposals-heading" className="bg-card rounded-xl border p-5">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h3 id="proposals-heading" className="inline-flex items-center gap-2 font-medium">
          <GitPullRequestArrow className="text-signal size-4" aria-hidden /> Proposed changes
        </h3>
        <p className="text-muted-foreground text-sm" aria-live="polite">
          {open.length === 0 ? "None open." : `${open.length} open for votes`}
        </p>
      </div>
      {proposals.isPending ? (
        <p className="text-muted-foreground mt-3 text-sm">Loading proposals…</p>
      ) : proposals.isError ? (
        <p role="alert" className="text-destructive mt-3 text-sm">
          {proposals.error.message}
        </p>
      ) : (
        <>
          {open.length === 0 && (
            <p className="text-muted-foreground mt-2 text-sm">
              Anyone can propose moving, swapping, removing or adding a stop. The group votes, and an approved change
              is applied to the plan.
            </p>
          )}
          <ul className="mt-3 space-y-3">
            {open.map((p) => (
              <ProposalCard key={p.id} tripId={tripId} proposal={p} />
            ))}
          </ul>
          {closed.length > 0 && (
            <div className="mt-4">
              <Button variant="ghost" size="sm" onClick={() => setShowClosed((v) => !v)} aria-expanded={showClosed}>
                {showClosed ? "Hide" : "Show"} {closed.length} closed
              </Button>
              {showClosed && (
                <ul className="mt-2 space-y-3">
                  {closed.map((p) => (
                    <ProposalCard key={p.id} tripId={tripId} proposal={p} />
                  ))}
                </ul>
              )}
            </div>
          )}
        </>
      )}
    </section>
  );
}

function ProposalCard({ tripId, proposal: p }: { tripId: string; proposal: Proposal }) {
  const { user } = useAuth();
  const withdraw = useWithdrawProposal(tripId);
  const vote = useVote(tripId, { id: user!.id, displayName: user!.displayName });
  const [talking, setTalking] = useState(false);
  const status = STATUS[p.status];
  const isOpen = p.status === "OPEN";
  const error = vote.error ?? withdraw.error;
  return (
    <li className="rounded-lg border p-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <p className="font-medium">{p.summary}</p>
        <Badge variant={status.variant}>{status.label}</Badge>
      </div>
      {p.reason && <p className="mt-1 text-sm">“{p.reason}”</p>}
      <p className="text-muted-foreground mt-1 text-xs">
        Proposed by {p.createdBy.displayName} {formatRelative(p.createdAt)}
        {p.resolvedAt && !isOpen && ` · closed ${formatRelative(p.resolvedAt)}`}
      </p>
      {p.status === "STALE" && (
        <p className="text-muted-foreground mt-2 text-xs">The draft was regenerated after this was proposed.</p>
      )}
      {p.problems.length > 0 && (
        <ul className="mt-2 space-y-1">
          {p.problems.map((problem) => (
            <li key={problem} className="flex items-start gap-1.5 text-sm">
              <AlertTriangle className="text-warning mt-0.5 size-4 shrink-0" aria-hidden />
              <span>
                <span className="sr-only">Problem: </span>
                {problem}
              </span>
            </li>
          ))}
        </ul>
      )}

      {(isOpen || p.votes.length > 0) && (
        <div className="mt-3 space-y-2" aria-live="polite">
          <VoteTally tally={p.tally} label={isOpen ? "to apply this change" : "to apply it"} />
          <Voters
            votes={p.votes.map((v) => ({ person: v.voter, value: v.value }))}
            notVoted={isOpen ? p.notVoted : []}
          />
        </div>
      )}

      <div className="mt-3 flex flex-wrap items-center gap-2">
        {p.canVote && (
          <VoteButtons
            mine={p.myVote}
            disabled={vote.isPending}
            onVote={(value) => vote.mutate({ proposalId: p.id, value })}
            approveDisabledReason={p.problems.length > 0 ? "This change no longer fits the plan" : undefined}
          />
        )}
        <Button variant="ghost" size="sm" onClick={() => setTalking((v) => !v)} aria-expanded={talking}>
          <MessageSquare aria-hidden />{" "}
          {p.commentCount > 0 ? `${p.commentCount} comment${p.commentCount === 1 ? "" : "s"}` : "Comment"}
        </Button>
        {p.canWithdraw && isOpen && (
          <Button variant="ghost" size="sm" onClick={() => withdraw.mutate(p.id)} disabled={withdraw.isPending}>
            <Undo2 aria-hidden /> Withdraw
          </Button>
        )}
        {p.decisionId && (
          <Button variant="link" size="sm" className="px-0" asChild>
            <Link href={`?tab=decisions#decision-${p.decisionId}`}>
              <ClipboardList aria-hidden /> Decision log entry
            </Link>
          </Button>
        )}
      </div>
      {error && (
        <p role="alert" className="text-destructive mt-2 text-xs">
          {error.message}
        </p>
      )}
      {talking && <CommentThread tripId={tripId} targetType="PROPOSAL" targetId={p.id} label={p.summary} />}
    </li>
  );
}
