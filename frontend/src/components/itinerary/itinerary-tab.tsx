"use client";

import { useMemo, useState } from "react";
import { CalendarRange, GitPullRequestArrow, Loader2, Plus, Route, Sparkles } from "lucide-react";
import { useQuery, useQueryClient } from "@tanstack/react-query";

import { ConsensusGate } from "@/components/decisions/consensus-gate";
import { ErrorState } from "@/components/error-state";
import { DayTimeline, formatDay } from "@/components/itinerary/day-timeline";
import { ProposalsPanel } from "@/components/itinerary/proposals-panel";
import { ProposeDialog, type ProposeTarget } from "@/components/itinerary/propose-dialog";
import { JobProgress } from "@/components/job-progress";
import { TripMap, type MapMarker } from "@/components/map/trip-map";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { NativeSelect } from "@/components/ui/select";
import { formatRelative } from "@/lib/format";
import { CATEGORY } from "@/lib/places";
import {
  keys,
  useEditItinerary,
  useGenerateItinerary,
  useItinerary,
  useRegenerateDay,
} from "@/lib/queries";
import { useAuth } from "@/lib/auth";
import type { AiActivity, Itinerary, Pace, TripDetail, UnplacedItem } from "@/lib/types";
import { useAiRun } from "@/lib/use-ai-run";
import { cn } from "@/lib/utils";

const PACES: { id: Pace; label: string; hint: string }[] = [
  { id: "SLOW", label: "Slow", hint: "10:00–19:00, up to 3 stops a day" },
  { id: "BALANCED", label: "Balanced", hint: "09:30–20:00, up to 4 stops a day" },
  { id: "PACKED", label: "Packed", hint: "08:30–21:30, up to 6 stops a day" },
];

const paceLabel = (p: Pace) => PACES.find((x) => x.id === p)!.label.toLowerCase();

/**
 * Spec §5.7: a day-by-day draft from the shortlist (A4), a map of each day's stops, direct edits by the organizer and
 * proposed changes from everyone else.
 */
export function ItineraryTab({ trip }: { trip: TripDetail }) {
  const qc = useQueryClient();
  const itinerary = useItinerary(trip.id);
  const generate = useGenerateItinerary(trip.id);
  const regenerateDay = useRegenerateDay(trip.id);
  const edit = useEditItinerary(trip.id);
  const [pace, setPace] = useState<Pace | "">("");
  const [dayIndex, setDayIndex] = useState(1);
  const [regeneratingDay, setRegeneratingDay] = useState<number | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [proposing, setProposing] = useState<ProposeTarget | null>(null);
  const isOrganizer = trip.myRole === "ORGANIZER";

  const run = useAiRun<Itinerary>((it) => {
    qc.setQueryData(keys.itinerary(trip.id), it);
    qc.invalidateQueries({ queryKey: keys.proposals(trip.id) });
    setRegeneratingDay(null);
  });

  const startAll = () =>
    generate.mutate(pace || null, {
      onSuccess: (job) => {
        setRegeneratingDay(null);
        run.track(job);
      },
    });
  const startDay = (day: number) =>
    regenerateDay.mutate(day, {
      onSuccess: (job) => {
        setRegeneratingDay(day);
        run.track(job);
      },
    });

  if (itinerary.isPending) return <ItinerarySkeleton />;
  if (itinerary.isError) return <ErrorState error={itinerary.error} onRetry={() => itinerary.refetch()} />;

  const it = itinerary.data;
  const busy = run.running || generate.isPending;
  const jobError = run.error ?? generate.error?.message ?? regenerateDay.error?.message;

  const controls = isOrganizer && it?.status !== "LOCKED" && (
    <div className="flex flex-wrap items-end gap-2">
      <div className="space-y-1">
        <label htmlFor="itinerary-pace" className="text-muted-foreground block text-xs">
          Pace
        </label>
        <NativeSelect
          id="itinerary-pace"
          className="h-9 w-auto"
          value={pace}
          onChange={(e) => setPace(e.target.value as Pace | "")}
          disabled={busy}
        >
          <option value="">Group&apos;s pace{it ? ` (${paceLabel(it.groupPace)})` : ""}</option>
          {PACES.map((p) => (
            <option key={p.id} value={p.id}>
              {p.label}: {p.hint}
            </option>
          ))}
        </NativeSelect>
      </div>
      <Button onClick={startAll} disabled={busy} variant={it ? "outline" : "default"}>
        <Sparkles className={it ? "text-signal" : undefined} aria-hidden />
        {it ? "Regenerate draft" : "Generate draft"}
      </Button>
    </div>
  );

  const progress = (run.running || jobError) && (
    <div className="max-w-md">
      {run.running && <JobProgress job={run.job} onCancel={run.cancel} cancelling={run.cancelling} />}
      {jobError && !run.running && (
        <p role="alert" className="text-destructive text-sm">
          {jobError}
        </p>
      )}
    </div>
  );

  if (!it) {
    return (
      <div className="space-y-6">
        <Header />
        <div className="graticule flex flex-col items-start gap-4 rounded-xl border border-dashed p-8">
          <CalendarRange className="text-signal size-8" aria-hidden />
          <div>
            <p className="font-medium">No draft yet</p>
            <p className="text-muted-foreground mt-1 max-w-xl text-sm">
              {isOrganizer
                ? "Code groups shortlisted places into days by area and works out travel times; the AI then orders each day and suggests times. Every day can be edited or regenerated."
                : "The organizer generates the first draft from the shortlist. You can propose changes to it once it is here."}
            </p>
          </div>
          {controls}
          {progress}
          {!run.running && <OthersAiActivity tripId={trip.id} />}
        </div>
      </div>
    );
  }

  const day = it.days.find((d) => d.index === dayIndex) ?? it.days[0];
  const totalStops = it.days.reduce((n, d) => n + d.stops.length, 0);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <Header />
        {controls}
      </div>

      <div className="text-muted-foreground flex flex-wrap items-center gap-x-4 gap-y-2 text-sm">
        <Badge variant={it.status === "LOCKED" ? "success" : "secondary"}>
          {it.status === "LOCKED" ? "Locked" : "Draft"}
        </Badge>
        <span>
          {totalStops} stops over {it.days.length} days at a {paceLabel(it.pace)} pace
        </span>
        <span>
          Made by {it.generatedBy.displayName} {formatRelative(it.generatedAt)}
          {it.updatedAt !== it.generatedAt && ` · changed ${formatRelative(it.updatedAt)}`}
        </span>
        {it.openProposals > 0 && (
          <span className="text-foreground inline-flex items-center gap-1">
            <GitPullRequestArrow className="text-signal size-4" aria-hidden /> {it.openProposals} open proposal
            {it.openProposals === 1 ? "" : "s"}
          </span>
        )}
      </div>

      {progress}
      {!run.running && <OthersAiActivity tripId={trip.id} />}
      <ConsensusGate tripId={trip.id} isOrganizer={isOrganizer} />
      {edit.error && (
        <p role="alert" className="text-destructive text-sm">
          {edit.error.message}
        </p>
      )}

      <DayPicker itinerary={it} active={day.index} onChange={(i) => {
        setDayIndex(i);
        setSelectedId(null);
      }} />

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,0.9fr)]">
        <div className="space-y-6">
          <DayTimeline
            tripId={trip.id}
            day={day}
            dayCount={it.days.length}
            canEdit={it.canEdit}
            canPropose={it.status === "DRAFT"}
            editing={edit.isPending || busy}
            regenerating={busy && regeneratingDay === day.index}
            selectedId={selectedId}
            onSelect={(id) => setSelectedId((cur) => (cur === id ? null : id))}
            onEdit={(e) => edit.mutate(e)}
            onPropose={(stop) =>
              setProposing({ shortlistItemId: stop.shortlistItemId, title: stop.title, day: day.index })
            }
            onRegenerate={it.canEdit && it.aiAvailable && !busy ? () => startDay(day.index) : undefined}
          />
          <UnplacedList
            itinerary={it}
            editing={edit.isPending || busy}
            onAdd={(item, d) => edit.mutate({ type: "ADD", itemId: item.shortlistItemId, day: d })}
            onPropose={(item) => setProposing({ shortlistItemId: item.shortlistItemId, title: item.title, day: null })}
          />
        </div>
        <div className="space-y-6 lg:sticky lg:top-4 lg:self-start">
          <DayMap trip={trip} itinerary={it} dayIndex={day.index} selectedId={selectedId} onSelect={setSelectedId} />
          <ProposalsPanel tripId={trip.id} />
        </div>
      </div>

      <ProposeDialog
        tripId={trip.id}
        itinerary={it}
        target={proposing}
        onOpenChange={(open) => !open && setProposing(null)}
      />
    </div>
  );
}

/** Someone else's AI job on this trip (from live events), e.g. the organizer regenerating the draft. */
function OthersAiActivity({ tripId }: { tripId: string }) {
  const { user } = useAuth();
  const activity = useQuery<Record<string, AiActivity>>({
    queryKey: keys.aiActivity(tripId),
    queryFn: () => ({}),
    staleTime: Infinity,
    enabled: false,
  }).data;
  const running = Object.values(activity ?? {}).find(
    (a) => a.feature === "itinerary" && a.ownerId !== user?.id && (a.status === "RUNNING" || a.status === "QUEUED"),
  );
  if (!running) return null;
  return (
    <p role="status" className="text-muted-foreground inline-flex items-center gap-2 text-sm">
      <Loader2 className="text-signal size-4 animate-spin" aria-hidden />
      The organizer is updating the plan: {running.message} ({running.percent}%)
    </p>
  );
}

function Header() {
  return (
    <div>
      <h2 className="text-xl font-semibold">Itinerary</h2>
      <p className="text-muted-foreground max-w-2xl text-sm">
        A draft built from the shortlist. Times include travel between stops; nothing is booked.
      </p>
    </div>
  );
}

function DayPicker({ itinerary, active, onChange }: { itinerary: Itinerary; active: number; onChange: (i: number) => void }) {
  return (
    <div role="group" aria-label="Days" className="flex gap-2 overflow-x-auto pb-1">
      {itinerary.days.map((d) => (
        <button
          key={d.index}
          type="button"
          onClick={() => onChange(d.index)}
          aria-pressed={d.index === active}
          className={cn(
            "shrink-0 rounded-lg border px-3 py-2 text-left text-sm transition-colors",
            d.index === active ? "border-signal bg-signal/8" : "bg-card hover:bg-muted",
          )}
        >
          <span className="block font-medium">Day {d.index}</span>
          <span className="text-muted-foreground block text-xs">
            {formatDay(d).split(" ").slice(0, 3).join(" ")}
          </span>
          <span className="text-muted-foreground figures block text-xs">
            {d.stops.length === 0 ? "Free" : `${d.stops.length} stop${d.stops.length === 1 ? "" : "s"}`}
            {d.overflowMin > 0 && " · runs late"}
          </span>
        </button>
      ))}
    </div>
  );
}

function DayMap({
  trip,
  itinerary,
  dayIndex,
  selectedId,
  onSelect,
}: {
  trip: TripDetail;
  itinerary: Itinerary;
  dayIndex: number;
  selectedId: string | null;
  onSelect: (id: string) => void;
}) {
  const day = itinerary.days.find((d) => d.index === dayIndex);
  const located = useMemo(
    () => (day?.stops ?? []).filter((s) => s.lat != null && s.lng != null),
    [day],
  );
  const markers = useMemo<MapMarker[]>(
    () =>
      located.map((s, i) => ({
        id: s.id,
        lat: s.lat!,
        lng: s.lng!,
        category: s.category,
        label: String(i + 1),
        title: s.title,
        detail: `${s.start}–${s.end} · ${CATEGORY[s.category].label}`,
      })),
    [located],
  );
  const route = useMemo(() => located.map((s) => ({ lat: s.lat!, lng: s.lng! })), [located]);
  const center =
    trip.destinationLat != null && trip.destinationLng != null
      ? { lat: trip.destinationLat, lng: trip.destinationLng }
      : null;
  return (
    <TripMap
      center={center}
      markers={markers}
      route={route}
      selectedId={selectedId}
      onSelect={onSelect}
      className="h-80 lg:h-[26rem]"
      label={`Map of day ${dayIndex}'s stops in order`}
    />
  );
}

function UnplacedList({
  itinerary,
  editing,
  onAdd,
  onPropose,
}: {
  itinerary: Itinerary;
  editing: boolean;
  onAdd: (item: UnplacedItem, day: number) => void;
  onPropose: (item: UnplacedItem) => void;
}) {
  if (itinerary.unplaced.length === 0) return null;
  return (
    <section aria-labelledby="unplaced-heading" className="bg-card rounded-xl border p-5">
      <h3 id="unplaced-heading" className="inline-flex items-center gap-2 font-medium">
        <Route className="text-muted-foreground size-4" aria-hidden /> Not on the plan
      </h3>
      <p className="text-muted-foreground mt-1 text-sm">Shortlisted places that aren&apos;t scheduled, and why.</p>
      <ul className="mt-3 divide-y">
        {itinerary.unplaced.map((u) => {
          const cat = CATEGORY[u.category];
          const Icon = cat.icon;
          return (
            <li key={u.shortlistItemId} className="flex flex-wrap items-center gap-3 py-3">
              <Icon className={cn("size-4 shrink-0", cat.chip)} aria-hidden />
              <div className="min-w-0 flex-1">
                <p className="place truncate">{u.title}</p>
                <p className="text-muted-foreground text-xs">{u.reason}</p>
              </div>
              {u.placeable &&
                itinerary.status === "DRAFT" &&
                (itinerary.canEdit ? (
                  <NativeSelect
                    className="h-8 w-auto text-xs"
                    value=""
                    disabled={editing}
                    onChange={(e) => e.target.value && onAdd(u, Number(e.target.value))}
                    aria-label={`Add ${u.title} to a day`}
                  >
                    <option value="">Add to day…</option>
                    {itinerary.days.map((d) => (
                      <option key={d.index} value={d.index}>
                        Day {d.index}
                      </option>
                    ))}
                  </NativeSelect>
                ) : (
                  <Button variant="ghost" size="sm" onClick={() => onPropose(u)}>
                    <Plus aria-hidden /> Propose adding
                  </Button>
                ))}
            </li>
          );
        })}
      </ul>
    </section>
  );
}

function ItinerarySkeleton() {
  return (
    <div className="space-y-4" aria-busy="true" aria-label="Loading itinerary">
      <div className="bg-muted h-8 w-48 animate-pulse rounded" />
      <div className="flex gap-2">
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className="bg-muted h-16 w-28 animate-pulse rounded-lg" />
        ))}
      </div>
      <div className="grid gap-6 lg:grid-cols-2">
        <div className="bg-muted h-96 animate-pulse rounded-xl" />
        <div className="bg-muted h-96 animate-pulse rounded-xl" />
      </div>
    </div>
  );
}
