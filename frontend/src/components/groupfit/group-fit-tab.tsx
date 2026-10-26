"use client";

import { useState } from "react";
import Link from "next/link";
import { Check, Lock, Sparkles, UserRound, X } from "lucide-react";

import { AiProvenance } from "@/components/ai-provenance";
import { ErrorState } from "@/components/error-state";
import { FIT_STATUS, StatusPill } from "@/components/groupfit/fit-status";
import { ItemFitList } from "@/components/groupfit/item-fit-list";
import { JobProgress } from "@/components/job-progress";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/lib/auth";
import { initials } from "@/lib/format";
import { keys, useGroupFit, useStartTradeoffs } from "@/lib/queries";
import type { Contribution, FitRow, FitStatus, GroupFit, TradeoffSummary, TripDetail } from "@/lib/types";
import { useAiRun } from "@/lib/use-ai-run";
import { cn } from "@/lib/utils";
import { useQueryClient } from "@tanstack/react-query";

/**
 * The signature screen (spec §5.4): who has contributed, where the group agrees and clashes, plain-language
 * trade-offs (A3) and how well each shortlisted place fits everyone. All numbers come from code.
 */
export function GroupFitTab({ trip }: { trip: TripDetail }) {
  const { user } = useAuth();
  const fit = useGroupFit(trip.id);

  if (fit.isPending) return <GroupFitSkeleton />;
  if (fit.isError) return <ErrorState error={fit.error} onRetry={() => fit.refetch()} />;

  const data = fit.data;
  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h2 className="text-xl font-semibold">Group fit</h2>
          <p className="text-muted-foreground max-w-2xl text-sm">
            Everyone&apos;s preferences side by side. Numbers are computed from profiles; budgets stay private and only
            the shared range is shown.
          </p>
        </div>
        <StatusCounts counts={data.counts} />
      </div>

      <ContributionStrip contributions={data.contributions} myId={user!.id} />
      <TradeoffsPanel tripId={trip.id} data={data} />
      <FitMatrix rows={data.rows} members={data.contributions} myId={user!.id} />
      <ItemFitList tripId={trip.id} items={data.items} />
    </div>
  );
}

function StatusCounts({ counts }: { counts: GroupFit["counts"] }) {
  const entries: [FitStatus, number][] = [
    ["AGREE", counts.agree],
    ["TRADE_OFF", counts.tradeOff],
    ["CONFLICT", counts.conflict],
    ["PLAN_AROUND", counts.planAround],
  ];
  return (
    <ul className="flex flex-wrap gap-2" aria-label="Summary">
      {entries
        .filter(([, n]) => n > 0)
        .map(([status, n]) => {
          const meta = FIT_STATUS[status];
          const Icon = meta.icon;
          return (
            <li key={status} className={cn("inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-sm", meta.tint)}>
              <Icon className={cn("size-4", meta.text)} aria-hidden />
              <span className="figures font-medium">{n}</span> {meta.label.toLowerCase()}
              {n > 1 && status !== "PLAN_AROUND" && status !== "AGREE" ? "s" : ""}
            </li>
          );
        })}
    </ul>
  );
}

// ---- who has contributed ----------------------------------------------------------------------

function ContributionStrip({ contributions, myId }: { contributions: Contribution[]; myId: string }) {
  const waiting = contributions.filter((c) => c.missing.length > 0);
  return (
    <section aria-labelledby="contrib-heading">
      <div className="mb-3 flex flex-wrap items-baseline justify-between gap-2">
        <h3 id="contrib-heading" className="font-medium">
          Who has weighed in
        </h3>
        <p className="text-muted-foreground text-sm">
          {waiting.length === 0
            ? "Everyone has filled in preferences and reacted to every place."
            : `Waiting on ${waiting.length} of ${contributions.length}. Every vote counts equally.`}
        </p>
      </div>
      <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {contributions.map((c) => (
          <li
            key={c.userId}
            className={cn(
              "bg-card rounded-lg border p-4",
              c.missing.length > 0 && "border-warning/50 border-dashed",
            )}
          >
            <div className="flex items-center gap-3">
              <span
                aria-hidden
                className="bg-sounding figures grid size-9 shrink-0 place-items-center rounded-full text-sm font-semibold"
              >
                {initials(c.displayName)}
              </span>
              <div className="min-w-0">
                <p className="truncate font-medium">
                  {c.displayName}
                  {c.userId === myId && <span className="text-muted-foreground font-normal"> (you)</span>}
                </p>
                <p className="text-muted-foreground text-xs">{c.organizer ? "Organizer" : "Member"}</p>
              </div>
            </div>
            <ul className="mt-3 space-y-1 text-sm">
              <Check3 done={c.hasProfile} label="Travel profile" />
              <Check3 done={c.tripBudgetSet} label="Private trip budget" />
              <Check3
                done={c.itemsToReact === 0 || c.reactions >= c.itemsToReact}
                label={`Reacted to ${c.reactions} of ${c.itemsToReact} places`}
              />
            </ul>
            {c.userId === myId && c.missing.length > 0 && (
              <p className="mt-3 text-xs">
                {!c.hasProfile ? (
                  <Link className="underline underline-offset-2" href="/onboarding">
                    Take the quiz
                  </Link>
                ) : !c.tripBudgetSet ? (
                  <Link className="underline underline-offset-2" href="?tab=overview">
                    Add your trip budget
                  </Link>
                ) : (
                  <Link className="underline underline-offset-2" href="?tab=shortlist">
                    React on the Shortlist tab
                  </Link>
                )}
              </p>
            )}
          </li>
        ))}
      </ul>
    </section>
  );
}

function Check3({ done, label }: { done: boolean; label: string }) {
  return (
    <li className={cn("flex items-center gap-2", !done && "text-muted-foreground")}>
      {done ? (
        <Check className="text-success size-4 shrink-0" aria-hidden />
      ) : (
        <X className="text-warning size-4 shrink-0" aria-hidden />
      )}
      <span>
        <span className="sr-only">{done ? "Done: " : "Missing: "}</span>
        {label}
      </span>
    </li>
  );
}

// ---- A3 trade-offs ----------------------------------------------------------------------------

function TradeoffsPanel({ tripId, data }: { tripId: string; data: GroupFit }) {
  const qc = useQueryClient();
  const start = useStartTradeoffs(tripId);
  const [dismissed, setDismissed] = useState(false);
  const run = useAiRun<TradeoffSummary>((summary) => {
    setDismissed(false);
    qc.setQueryData<GroupFit>(keys.groupFit(tripId), (old) => (old ? { ...old, tradeoffs: summary } : old));
  });
  const summary = data.tradeoffs;
  const isAi = summary.provenance.source === "ai" && !dismissed;
  const rowByKey = new Map(data.rows.map((r) => [r.key, r]));
  const items = isAi
    ? summary.items
    : data.rows
        .filter((r) => r.status === "TRADE_OFF" || r.status === "CONFLICT" || r.status === "PLAN_AROUND")
        .map((r) => ({ key: r.key, headline: r.label, explanation: r.summary, suggestion: r.suggestion }));

  const generate = (regenerate: boolean) => start.mutate(regenerate, { onSuccess: run.track });

  return (
    <section aria-labelledby="tradeoffs-heading" className="bg-card rounded-xl border p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 id="tradeoffs-heading" className="font-medium">
            Trade-offs to talk through
          </h3>
          <p className="text-muted-foreground text-sm">
            {items.length === 0
              ? "Nothing to reconcile yet."
              : "Where the group pulls in different directions, with a compromise to start from."}
          </p>
        </div>
        {items.length > 0 && (
          <div className="flex flex-wrap items-center gap-2">
            <AiProvenance
              provenance={dismissed ? { ...summary.provenance, source: "rules", note: "AI text dismissed; showing the computed summaries." } : summary.provenance}
              onRegenerate={isAi ? () => generate(true) : undefined}
              onDismiss={isAi ? () => setDismissed(true) : undefined}
              regenerating={run.running || start.isPending}
              details={
                <p className="text-muted-foreground text-xs">
                  Code compared every member&apos;s pace, budgets, crowd tolerance, interests, must-haves and needs.
                  {isAi
                    ? " The AI only rephrased those findings; it was not shown anyone's private budget, and any number it wrote had to match the computed facts."
                    : " These sentences are templates filled in with the computed facts."}
                </p>
              }
            />
            {!isAi && data.aiAvailable && !run.running && (
              <Button size="sm" variant="outline" onClick={() => generate(false)} disabled={start.isPending}>
                <Sparkles className="text-signal" aria-hidden /> Explain with AI
              </Button>
            )}
          </div>
        )}
      </div>

      {run.running && (
        <div className="mt-4 max-w-md">
          <JobProgress job={run.job} onCancel={run.cancel} cancelling={run.cancelling} />
        </div>
      )}
      {(run.error || start.error) && (
        <p role="alert" className="text-destructive mt-3 text-sm">
          {run.error ?? start.error?.message}
        </p>
      )}

      {items.length > 0 && (
        <ul className="mt-4 grid gap-3 md:grid-cols-2">
          {items.map((t) => {
            const row = rowByKey.get(t.key);
            return (
              <li key={t.key} className="rounded-lg border p-4">
                <div className="flex items-start justify-between gap-3">
                  <p className="font-medium">{t.headline}</p>
                  {row && <StatusPill status={row.status} />}
                </div>
                <p className="mt-1.5 text-sm">{t.explanation}</p>
                {t.suggestion && (
                  <p className="text-muted-foreground mt-2 text-sm">
                    <span className="text-foreground font-medium">Try: </span>
                    {t.suggestion}
                  </p>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}

// ---- the matrix -------------------------------------------------------------------------------

function FitMatrix({ rows, members, myId }: { rows: FitRow[]; members: Contribution[]; myId: string }) {
  return (
    <section aria-labelledby="matrix-heading">
      <div className="mb-3 flex flex-wrap items-baseline justify-between gap-2">
        <h3 id="matrix-heading" className="font-medium">
          Preferences side by side
        </h3>
        <p className="text-muted-foreground inline-flex items-center gap-1.5 text-xs">
          <Lock className="size-3.5" aria-hidden /> Private means only its owner sees it.
        </p>
      </div>
      <div className="bg-card overflow-x-auto rounded-xl border">
        <table className="w-full min-w-[46rem] border-collapse text-sm">
          <caption className="sr-only">Each member&apos;s preferences per attribute, with agreement status</caption>
          <thead>
            <tr className="border-b text-left">
              <th scope="col" className="w-[30%] px-4 py-3 font-medium">
                Attribute
              </th>
              {members.map((m) => (
                <th key={m.userId} scope="col" className="px-3 py-3 font-medium">
                  <span className="inline-flex items-center gap-1.5">
                    <UserRound className="text-muted-foreground size-3.5" aria-hidden />
                    {m.userId === myId ? "You" : m.displayName}
                  </span>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.key} className={cn("border-b align-top last:border-0", row.status === "CONFLICT" && "bg-destructive/[0.03]")}>
                <th scope="row" className="px-4 py-3 text-left font-normal">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-medium">{row.label}</span>
                    <StatusPill status={row.status} />
                  </div>
                  <p className="text-muted-foreground mt-1 text-xs">{row.summary}</p>
                </th>
                {row.cells.map((cell) => (
                  <td key={cell.userId} className="px-3 py-3">
                    {cell.hidden ? (
                      <span className="text-muted-foreground inline-flex items-center gap-1">
                        <Lock className="size-3.5" aria-hidden /> Private
                      </span>
                    ) : (
                      <>
                        <span className={cn(cell.value === "—" && "text-muted-foreground")}>{cell.value}</span>
                        {cell.detail && <span className="text-muted-foreground block text-xs">{cell.detail}</span>}
                      </>
                    )}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function GroupFitSkeleton() {
  return (
    <div role="status" aria-label="Loading group fit" className="space-y-4">
      <div className="bg-muted h-6 w-40 animate-pulse rounded" />
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className="bg-card h-32 animate-pulse rounded-lg border" />
        ))}
      </div>
      <div className="bg-card h-64 animate-pulse rounded-xl border" />
    </div>
  );
}
