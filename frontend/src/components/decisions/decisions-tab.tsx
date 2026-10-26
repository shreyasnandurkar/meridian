"use client";

import { useEffect, useState } from "react";
import { CheckCircle2, ClipboardList, Loader2, Lock, LockOpen, ShieldAlert, XCircle } from "lucide-react";
import type { LucideIcon } from "lucide-react";

import { AiProvenance } from "@/components/ai-provenance";
import { Voters } from "@/components/decisions/vote-tally";
import { ErrorState } from "@/components/error-state";
import { Badge } from "@/components/ui/badge";
import { formatRelative } from "@/lib/format";
import { useDecisions, useDismissDecisionSummary, useSummarizeDecision } from "@/lib/queries";
import type { Decision, DecisionKind, TripDetail } from "@/lib/types";
import { cn } from "@/lib/utils";

const KIND: Record<DecisionKind, { label: string; icon: LucideIcon; tone: string }> = {
  CHANGE_APPROVED: { label: "Change approved", icon: CheckCircle2, tone: "text-success" },
  CHANGE_REJECTED: { label: "Change rejected", icon: XCircle, tone: "text-destructive" },
  PLAN_LOCKED: { label: "Plan locked", icon: Lock, tone: "text-success" },
  LOCK_OVERRIDE: { label: "Locked by override", icon: ShieldAlert, tone: "text-warning" },
  PLAN_UNLOCKED: { label: "Plan unlocked", icon: LockOpen, tone: "text-muted-foreground" },
};

const timeFmt = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });

/**
 * The decision log (spec §5.8): every closed vote, lock and unlock, newest first, with the alternatives, the votes as
 * they stood and a summary. Entries are append-only; only the summary can be rewritten (A6).
 */
export function DecisionsTab({ trip }: { trip: TripDetail }) {
  const decisions = useDecisions(trip.id);
  const [highlight, setHighlight] = useState<string | null>(null);

  // stops and proposals link here as ?tab=decisions#decision-<id>
  useEffect(() => {
    if (!decisions.data) return;
    const id = window.location.hash.replace(/^#decision-/, "");
    if (!id || id === window.location.hash) return;
    const el = document.getElementById(`decision-${id}`);
    if (!el) return;
    el.scrollIntoView({ behavior: "smooth", block: "center" });
    setHighlight(id);
    const t = setTimeout(() => setHighlight(null), 2500);
    return () => clearTimeout(t);
  }, [decisions.data]);

  if (decisions.isPending) {
    return (
      <div className="space-y-3" aria-busy="true" aria-label="Loading decisions">
        {[0, 1, 2].map((i) => (
          <div key={i} className="bg-muted h-32 animate-pulse rounded-xl" />
        ))}
      </div>
    );
  }
  if (decisions.isError) return <ErrorState error={decisions.error} onRetry={() => decisions.refetch()} />;

  const list = decisions.data;
  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-semibold">Decision log</h2>
        <p className="text-muted-foreground max-w-2xl text-sm">
          What the group decided, what it chose between and how everyone voted. Entries can&apos;t be edited or
          deleted. {trip.quorum.description}.
        </p>
      </div>
      {list.length === 0 ? (
        <div className="graticule flex flex-col items-start gap-3 rounded-xl border border-dashed p-8">
          <ClipboardList className="text-signal size-8" aria-hidden />
          <p className="font-medium">No decisions yet</p>
          <p className="text-muted-foreground max-w-xl text-sm">
            When a proposed change is approved or rejected, or the itinerary is locked, it is recorded here with the
            votes behind it.
          </p>
        </div>
      ) : (
        <ol className="relative space-y-4 border-l pl-6">
          {list.map((d) => (
            <DecisionCard key={d.id} tripId={trip.id} decision={d} highlighted={highlight === d.id} />
          ))}
        </ol>
      )}
    </div>
  );
}

function DecisionCard({ tripId, decision: d, highlighted }: { tripId: string; decision: Decision; highlighted: boolean }) {
  const summarize = useSummarizeDecision(tripId);
  const dismiss = useDismissDecisionSummary(tripId);
  const kind = KIND[d.kind];
  const Icon = kind.icon;
  const isAi = d.provenance.source === "ai";
  const voted = d.kind !== "PLAN_UNLOCKED";
  return (
    <li
      id={`decision-${d.id}`}
      className={cn(
        "bg-card relative scroll-mt-24 rounded-xl border p-5 transition-shadow",
        highlighted && "ring-signal ring-2",
      )}
    >
      <span className="bg-background absolute top-5 -left-[2.15rem] grid size-5 place-items-center rounded-full border">
        <Icon className={cn("size-3.5", kind.tone)} aria-hidden />
      </span>
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <p className="text-muted-foreground figures text-xs">
            #{d.seq} · {kind.label} · <time dateTime={d.decidedAt}>{timeFmt.format(new Date(d.decidedAt))}</time> (
            {formatRelative(d.decidedAt)})
          </p>
          <h3 className="mt-0.5 font-medium">{d.title}</h3>
        </div>
        {voted && (
          <Badge variant={d.kind === "CHANGE_REJECTED" ? "outline" : d.kind === "LOCK_OVERRIDE" ? "warning" : "success"}>
            <span className="figures">
              {d.tally.approvals} of {d.tally.members} approved
            </span>
          </Badge>
        )}
      </div>

      <div className="mt-3 rounded-lg border-l-2 border-[var(--signal)] pl-3" aria-live="polite">
        <p className="text-sm">{d.summary}</p>
        <div className="mt-2 flex flex-wrap items-center gap-2">
          {d.summaryPending ? (
            <span className="text-muted-foreground inline-flex items-center gap-1.5 text-xs" role="status">
              <Loader2 className="text-signal size-3.5 animate-spin" aria-hidden /> The AI is writing a summary…
            </span>
          ) : (
            <AiProvenance
              provenance={d.provenance}
              onRegenerate={d.canSummarize ? () => summarize.mutate(d.id) : undefined}
              regenerating={summarize.isPending}
              onDismiss={isAi ? () => dismiss.mutate(d.id) : undefined}
              details={
                <p className="text-muted-foreground text-xs">
                  {isAi
                    ? "The model was given only the facts recorded below — who voted which way, the counts, the reason and the discussion — and had to state the count as given. Any number not in those facts is rejected."
                    : "Written by code from the votes recorded below."}
                </p>
              }
            />
          )}
        </div>
        {(summarize.error ?? dismiss.error) && (
          <p role="alert" className="text-destructive mt-1 text-xs">
            {(summarize.error ?? dismiss.error)!.message}
          </p>
        )}
      </div>

      <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-2">
        <div>
          <dt className="text-muted-foreground text-xs">Alternatives</dt>
          <dd>
            <ul className="mt-1 space-y-0.5">
              {d.alternatives.map((a, i) => {
                const chosen = d.kind === "CHANGE_REJECTED" ? i === 1 : i === 0;
                return (
                  <li key={a} className={cn("flex items-start gap-1.5", !chosen && "text-muted-foreground")}>
                    <span aria-hidden>{chosen ? "●" : "○"}</span>
                    <span>
                      {a}
                      {chosen && <span className="sr-only"> (chosen)</span>}
                    </span>
                  </li>
                );
              })}
            </ul>
          </dd>
        </div>
        {voted && (
          <div>
            <dt className="text-muted-foreground text-xs">
              Votes · {d.tally.required} approvals needed to pass
            </dt>
            <dd className="mt-1">
              <Voters
                votes={d.votes.map((v) => ({ person: { userId: v.userId, displayName: v.displayName }, value: v.value }))}
                notVoted={d.notVoted}
              />
            </dd>
          </div>
        )}
        {d.reason && (
          <div className="sm:col-span-2">
            <dt className="text-muted-foreground text-xs">Reason given</dt>
            <dd>“{d.reason}”</dd>
          </div>
        )}
        {d.decidedBy && (
          <div>
            <dt className="text-muted-foreground text-xs">By</dt>
            <dd>{d.decidedBy.displayName}</dd>
          </div>
        )}
      </dl>
    </li>
  );
}
