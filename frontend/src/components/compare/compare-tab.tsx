"use client";

import { useId, useState } from "react";
import {
  AlertTriangle,
  BedDouble,
  Bus,
  Compass,
  ExternalLink,
  GitCompareArrows,
  Loader2,
  Pencil,
  Plane,
  Plus,
  RefreshCw,
  Tag,
  Trash2,
  type LucideIcon,
} from "lucide-react";

import { OptionRowDialog } from "@/components/compare/option-row-dialog";
import { ErrorState } from "@/components/error-state";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { NativeSelect } from "@/components/ui/select";
import { formatMoney, formatRelative } from "@/lib/format";
import {
  useComparisons,
  useCreateComparison,
  useDeleteComparison,
  useDeleteOptionRow,
  useRecheckOptionRow,
} from "@/lib/queries";
import type { Comparison, ComparisonKind, OptionRow, TripDetail } from "@/lib/types";
import { cn } from "@/lib/utils";

const KINDS: { id: ComparisonKind; label: string; icon: LucideIcon }[] = [
  { id: "STAY", label: "Stays", icon: BedDouble },
  { id: "FLIGHT", label: "Flights", icon: Plane },
  { id: "ACTIVITY", label: "Activities", icon: Compass },
  { id: "TRANSPORT", label: "Getting around", icon: Bus },
  { id: "OTHER", label: "Other", icon: Tag },
];
const KIND = Object.fromEntries(KINDS.map((k) => [k.id, k])) as Record<ComparisonKind, (typeof KINDS)[number]>;

/**
 * Side-by-side options (spec §5.6). Each price shows its provider, source link and when it was checked; prices
 * older than 24 hours are flagged. Rows are ordered by price only. Booking is always a link out.
 */
export function CompareTab({ trip }: { trip: TripDetail }) {
  const comparisons = useComparisons(trip.id);
  const [editing, setEditing] = useState<{ comparison: Comparison; row: OptionRow | null } | null>(null);

  if (comparisons.isPending) {
    return (
      <div role="status" aria-label="Loading comparisons" className="bg-card h-48 animate-pulse rounded-xl border" />
    );
  }
  if (comparisons.isError) return <ErrorState error={comparisons.error} onRetry={() => comparisons.refetch()} />;

  const list = comparisons.data;
  const current = editing ? (list.find((c) => c.id === editing.comparison.id) ?? null) : null;

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-semibold">Compare options</h2>
        <p className="text-muted-foreground max-w-2xl text-sm">
          Put stays, flights or tours side by side. Every price shows where it came from and when someone last checked
          it. No sponsored ranking, and booking happens on the provider&apos;s own site.
        </p>
      </div>

      <NewComparison tripId={trip.id} empty={list.length === 0} />

      {list.map((c) => (
        <ComparisonCard
          key={c.id}
          trip={trip}
          comparison={c}
          onAdd={() => setEditing({ comparison: c, row: null })}
          onEdit={(row) => setEditing({ comparison: c, row })}
        />
      ))}

      <OptionRowDialog
        tripId={trip.id}
        currency={trip.currency}
        comparison={current}
        row={editing?.row ?? null}
        open={!!editing}
        onOpenChange={(o) => !o && setEditing(null)}
      />
    </div>
  );
}

function NewComparison({ tripId, empty }: { tripId: string; empty: boolean }) {
  const create = useCreateComparison(tripId);
  const [kind, setKind] = useState<ComparisonKind>("STAY");
  const [title, setTitle] = useState("");
  const titleId = useId();
  const kindId = useId();

  return (
    <form
      className={cn("rounded-xl border p-4 sm:p-5", empty ? "graticule border-dashed" : "bg-card")}
      onSubmit={(e) => {
        e.preventDefault();
        create.mutate({ kind, title: title.trim() }, { onSuccess: () => setTitle("") });
      }}
    >
      {empty && (
        <div className="mb-4 flex items-start gap-3">
          <GitCompareArrows className="text-muted-foreground mt-0.5 size-5 shrink-0" aria-hidden />
          <p className="text-sm">
            Start a comparison for something the group needs to choose, like where to stay in North Goa.
          </p>
        </div>
      )}
      <div className="flex flex-wrap items-end gap-3">
        <div>
          <label htmlFor={kindId} className="mb-2 block text-sm font-medium">
            What
          </label>
          <NativeSelect id={kindId} value={kind} onChange={(e) => setKind(e.target.value as ComparisonKind)}>
            {KINDS.map((k) => (
              <option key={k.id} value={k.id}>
                {k.label}
              </option>
            ))}
          </NativeSelect>
        </div>
        <div className="min-w-48 flex-1">
          <label htmlFor={titleId} className="mb-2 block text-sm font-medium">
            Name
          </label>
          <Input
            id={titleId}
            required
            maxLength={120}
            placeholder="e.g. Stay near Assagao, Dec 10–14"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
          />
        </div>
        <Button type="submit" disabled={create.isPending || !title.trim()}>
          {create.isPending ? <Loader2 className="animate-spin" aria-hidden /> : <Plus aria-hidden />}
          New comparison
        </Button>
      </div>
      {create.error && (
        <p role="alert" className="text-destructive mt-2 text-sm">
          {create.error.message}
        </p>
      )}
    </form>
  );
}

function ComparisonCard({
  trip,
  comparison,
  onAdd,
  onEdit,
}: {
  trip: TripDetail;
  comparison: Comparison;
  onAdd: () => void;
  onEdit: (row: OptionRow) => void;
}) {
  const remove = useDeleteComparison(trip.id);
  const recheck = useRecheckOptionRow(trip.id);
  const removeRow = useDeleteOptionRow(trip.id);
  const kind = KIND[comparison.kind];
  const staleCount = comparison.rows.filter((r) => r.stale).length;

  return (
    <section aria-labelledby={`cmp-${comparison.id}`} className="bg-card rounded-xl border">
      <header className="flex flex-wrap items-start justify-between gap-3 border-b p-4 sm:px-5">
        <div>
          <h3 id={`cmp-${comparison.id}`} className="flex items-center gap-2 font-medium">
            <kind.icon className="text-muted-foreground size-4" aria-hidden /> {comparison.title}
          </h3>
          <p className="text-muted-foreground mt-0.5 text-xs">
            {kind.label} · started by {comparison.createdBy.displayName} · {comparison.rows.length}{" "}
            {comparison.rows.length === 1 ? "option" : "options"}
            {staleCount > 0 && (
              <span className="text-foreground">
                {" "}
                · <AlertTriangle className="text-warning inline size-3.5 align-[-2px]" aria-hidden /> {staleCount}{" "}
                {staleCount === 1 ? "price needs" : "prices need"} re-checking
              </span>
            )}
          </p>
        </div>
        <div className="flex gap-2">
          <Button size="sm" onClick={onAdd}>
            <Plus aria-hidden /> Add option
          </Button>
          {comparison.canEdit && (
            <Button
              size="sm"
              variant="ghost"
              onClick={() => remove.mutate(comparison.id)}
              disabled={remove.isPending}
              aria-label={`Delete comparison ${comparison.title}`}
            >
              <Trash2 aria-hidden />
            </Button>
          )}
        </div>
      </header>

      {comparison.rows.length === 0 ? (
        <p className="text-muted-foreground px-5 py-8 text-center text-sm">
          No options yet. Paste a link from a booking site or type one in.
        </p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[44rem] text-sm">
            <caption className="sr-only">Options for {comparison.title}, cheapest first</caption>
            <thead>
              <tr className="text-muted-foreground border-b text-left text-xs">
                <th scope="col" className="px-4 py-2 font-medium sm:pl-5">
                  Provider
                </th>
                <th scope="col" className="px-3 py-2 font-medium">
                  Option
                </th>
                <th scope="col" className="px-3 py-2 text-right font-medium">
                  Price
                </th>
                <th scope="col" className="px-3 py-2 font-medium">
                  Checked
                </th>
                <th scope="col" className="px-3 py-2 font-medium">
                  Added by
                </th>
                <th scope="col" className="px-4 py-2 font-medium sm:pr-5">
                  <span className="sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {comparison.rows.map((row) => (
                <tr key={row.id} className="border-b align-top last:border-0">
                  <th scope="row" className="px-4 py-3 text-left font-medium sm:pl-5">
                    {row.provider}
                    <span className="mt-1 flex flex-wrap gap-1">
                      {row.cheapest && <Badge variant="success">Cheapest</Badge>}
                      {row.sponsored && <Badge variant="warning">Sponsored</Badge>}
                    </span>
                  </th>
                  <td className="px-3 py-3">
                    {row.title ?? <span className="text-muted-foreground">—</span>}
                    {row.notes && <span className="text-muted-foreground mt-1 block text-xs">{row.notes}</span>}
                  </td>
                  <td className="figures px-3 py-3 text-right font-medium whitespace-nowrap">
                    {row.price != null && row.currency ? (
                      formatMoney(row.price, row.currency)
                    ) : (
                      <span className="text-muted-foreground font-normal">No price</span>
                    )}
                  </td>
                  <td className="px-3 py-3 text-xs whitespace-nowrap">
                    {row.checkedAt ? (
                      <span className={cn("inline-flex items-center gap-1", row.stale && "font-medium")}>
                        {row.stale && <AlertTriangle className="text-warning size-3.5" aria-hidden />}
                        <time dateTime={row.checkedAt}>{formatRelative(row.checkedAt)}</time>
                        {row.stale && <span>· stale</span>}
                      </span>
                    ) : (
                      <span className="text-muted-foreground">—</span>
                    )}
                    <span className="text-muted-foreground mt-0.5 block">
                      {row.sourceHost ? `from ${row.sourceHost}` : "typed in"}
                    </span>
                  </td>
                  <td className="text-muted-foreground px-3 py-3 text-xs">{row.addedBy.displayName}</td>
                  <td className="px-4 py-2 sm:pr-5">
                    <div className="flex flex-wrap justify-end gap-1">
                      {row.sourceUrl && (
                        <Button size="sm" variant="outline" asChild>
                          <a href={row.sourceUrl} target="_blank" rel="noopener noreferrer">
                            Open on {row.provider.length > 16 ? "provider" : row.provider}
                            <ExternalLink aria-hidden />
                            <span className="sr-only">(opens in a new tab)</span>
                          </a>
                        </Button>
                      )}
                      {row.stale && (
                        <Button
                          size="sm"
                          variant="ghost"
                          onClick={() => recheck.mutate({ comparisonId: comparison.id, rowId: row.id })}
                          disabled={recheck.isPending}
                          title="Confirm the price is still what the provider shows"
                        >
                          <RefreshCw aria-hidden /> Still this price
                        </Button>
                      )}
                      {row.canEdit && (
                        <>
                          <Button size="sm" variant="ghost" onClick={() => onEdit(row)} aria-label={`Edit ${row.provider}`}>
                            <Pencil aria-hidden />
                          </Button>
                          <Button
                            size="sm"
                            variant="ghost"
                            onClick={() => removeRow.mutate({ comparisonId: comparison.id, rowId: row.id })}
                            disabled={removeRow.isPending}
                            aria-label={`Remove ${row.provider}`}
                          >
                            <Trash2 aria-hidden />
                          </Button>
                        </>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
