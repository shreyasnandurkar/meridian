"use client";

import { useState } from "react";
import Link from "next/link";
import {
  AlertTriangle,
  ArrowDown,
  ArrowUp,
  Car,
  ClipboardList,
  Footprints,
  GitPullRequestArrow,
  MessageSquare,
  Wrench,
  X,
} from "lucide-react";

import { AiProvenance } from "@/components/ai-provenance";
import { CommentThread } from "@/components/itinerary/comment-thread";
import { Button } from "@/components/ui/button";
import { NativeSelect } from "@/components/ui/select";
import { formatDistance, parseDate } from "@/lib/format";
import { CATEGORY } from "@/lib/places";
import type { ItineraryDay, ItineraryStop, PlanEdit } from "@/lib/types";
import { cn } from "@/lib/utils";

const dayFmt = new Intl.DateTimeFormat("en-GB", { weekday: "long", day: "numeric", month: "long" });

export function formatDay(day: ItineraryDay) {
  return dayFmt.format(parseDate(day.date));
}

/** Walking legs come out at ≤ 1.2 km in the backend's travel model; anything longer is a ride. */
function LegIcon({ km }: { km: number | null }) {
  return km != null && km <= 1.2 ? (
    <Footprints className="size-3.5" aria-hidden />
  ) : (
    <Car className="size-3.5" aria-hidden />
  );
}

/**
 * One day of the plan: times, travel legs, notes and the controls for changing it. The organizer edits directly;
 * everyone else proposes.
 */
export function DayTimeline({
  tripId,
  day,
  dayCount,
  canEdit,
  canPropose,
  editing,
  regenerating,
  selectedId,
  onSelect,
  onEdit,
  onPropose,
  onRegenerate,
}: {
  tripId: string;
  day: ItineraryDay;
  dayCount: number;
  canEdit: boolean;
  canPropose: boolean;
  editing: boolean;
  regenerating: boolean;
  selectedId: string | null;
  onSelect: (stopId: string) => void;
  onEdit: (edit: PlanEdit) => void;
  onPropose: (stop: ItineraryStop) => void;
  onRegenerate?: () => void;
}) {
  const [talkingAbout, setTalkingAbout] = useState<string | null>(null);
  const isAi = day.provenance.source === "ai";

  return (
    <section aria-labelledby={`day-${day.index}-heading`} className="bg-card rounded-xl border p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 id={`day-${day.index}-heading`} className="font-medium">
            Day {day.index} <span className="text-muted-foreground font-normal">· {formatDay(day)}</span>
          </h3>
          {day.summary && <p className="mt-1 text-sm">{day.summary}</p>}
        </div>
        {day.stops.length > 0 && (
          <AiProvenance
            provenance={day.provenance}
            onRegenerate={onRegenerate}
            regenerating={regenerating}
            details={
              <div className="space-y-2 text-xs">
                <p className="text-muted-foreground">
                  Code grouped the shortlist into days by area, picked the group&apos;s pace and worked out travel
                  times.{" "}
                  {isAi
                    ? "The AI only chose the order, start times and short notes, using place ids it was given; code then checked every time against travel and the day's hours."
                    : "The order is a nearest-neighbour route with meals placed near lunch and dinner."}
                </p>
                {day.repairs.length > 0 && (
                  <div>
                    <p className="text-foreground mb-1 font-medium">Fixed by code</p>
                    <ul className="text-muted-foreground list-disc space-y-0.5 pl-4">
                      {day.repairs.map((r) => (
                        <li key={r}>{r}</li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
            }
          />
        )}
      </div>

      {day.overflowMin > 0 && (
        <p className="border-warning/50 bg-warning/10 mt-3 flex items-start gap-2 rounded-md border px-3 py-2 text-sm">
          <AlertTriangle className="text-warning mt-0.5 size-4 shrink-0" aria-hidden />
          Runs {day.overflowMin} min past {day.dayEnd}. Consider moving a stop to another day.
        </p>
      )}

      {day.stops.length === 0 ? (
        <p className="text-muted-foreground mt-3 text-sm">A free day: nothing scheduled.</p>
      ) : (
        <ol className="mt-4">
          {day.stops.map((stop, i) => {
            const cat = CATEGORY[stop.category];
            const Icon = cat.icon;
            const selected = stop.id === selectedId;
            return (
              <li key={stop.id}>
                {i > 0 && (
                  <p className="text-muted-foreground figures flex items-center gap-1.5 py-1.5 pl-[4.75rem] text-xs">
                    <LegIcon km={stop.travelKm} />
                    {stop.travelMin} min
                    {stop.travelKm != null && ` · ${formatDistance(stop.travelKm)}`}
                  </p>
                )}
                <div
                  className={cn(
                    "flex gap-3 rounded-lg border p-3 transition-colors",
                    selected ? "border-signal/60 bg-signal/5" : "border-transparent hover:bg-muted/50",
                  )}
                >
                  <div className="figures w-16 shrink-0 text-sm tabular-nums">
                    <span className="block font-medium">{stop.start}</span>
                    <span className="text-muted-foreground block text-xs">to {stop.end}</span>
                  </div>
                  <div className="min-w-0 flex-1">
                    <button
                      type="button"
                      onClick={() => onSelect(stop.id)}
                      aria-pressed={selected}
                      className="flex items-center gap-2 text-left"
                    >
                      <span
                        aria-hidden
                        className="figures grid size-6 shrink-0 place-items-center rounded-full text-xs font-semibold text-[var(--cat-foreground)]"
                        style={{ background: cat.color }}
                      >
                        {i + 1}
                      </span>
                      <span className="place truncate text-base">{stop.title}</span>
                    </button>
                    <p className="text-muted-foreground mt-0.5 flex items-center gap-1.5 text-xs">
                      <Icon className={cn("size-3.5", cat.chip)} aria-hidden />
                      {cat.label} · {stop.durationMin} min · added by {stop.addedBy.displayName}
                    </p>
                    {stop.note && <p className="mt-1.5 text-sm">{stop.note}</p>}
                    <div className="mt-2 flex flex-wrap items-center gap-1">
                      <Button
                        variant="ghost"
                        size="sm"
                        className="h-7 px-2 text-xs"
                        onClick={() => setTalkingAbout((v) => (v === stop.id ? null : stop.id))}
                        aria-expanded={talkingAbout === stop.id}
                      >
                        <MessageSquare aria-hidden />
                        {stop.commentCount > 0
                          ? `${stop.commentCount} comment${stop.commentCount === 1 ? "" : "s"}`
                          : "Comment"}
                      </Button>
                      {stop.decisionId && (
                        <Button variant="ghost" size="sm" className="h-7 px-2 text-xs" asChild>
                          <Link
                            href={`?tab=decisions#decision-${stop.decisionId}`}
                            aria-label={`Decision log entry for ${stop.title}`}
                          >
                            <ClipboardList aria-hidden /> Decision
                          </Link>
                        </Button>
                      )}
                      {canEdit ? (
                        <StopEditControls
                          stop={stop}
                          index={i}
                          day={day}
                          dayCount={dayCount}
                          disabled={editing}
                          onEdit={onEdit}
                        />
                      ) : (
                        canPropose && (
                          <Button
                            variant="ghost"
                            size="sm"
                            className="h-7 px-2 text-xs"
                            onClick={() => onPropose(stop)}
                          >
                            <GitPullRequestArrow aria-hidden /> Propose change
                          </Button>
                        )
                      )}
                    </div>
                    {talkingAbout === stop.id && (
                      <CommentThread
                        tripId={tripId}
                        targetType="SHORTLIST_ITEM"
                        targetId={stop.shortlistItemId}
                        label={stop.title}
                      />
                    )}
                  </div>
                </div>
              </li>
            );
          })}
        </ol>
      )}
      {day.repairs.some((r) => r.startsWith("Changed by")) && (
        <p className="text-muted-foreground mt-3 flex items-center gap-1.5 text-xs">
          <Wrench className="size-3.5" aria-hidden />
          {day.repairs.find((r) => r.startsWith("Changed by"))}
        </p>
      )}
    </section>
  );
}

function StopEditControls({
  stop,
  index,
  day,
  dayCount,
  disabled,
  onEdit,
}: {
  stop: ItineraryStop;
  index: number;
  day: ItineraryDay;
  dayCount: number;
  disabled: boolean;
  onEdit: (edit: PlanEdit) => void;
}) {
  const itemId = stop.shortlistItemId;
  return (
    <>
      <Button
        variant="ghost"
        size="sm"
        className="h-7 px-2"
        disabled={disabled || index === 0}
        onClick={() => onEdit({ type: "MOVE", itemId, day: day.index, position: index - 1 })}
        aria-label={`Move ${stop.title} earlier`}
      >
        <ArrowUp aria-hidden />
      </Button>
      <Button
        variant="ghost"
        size="sm"
        className="h-7 px-2"
        disabled={disabled || index === day.stops.length - 1}
        onClick={() => onEdit({ type: "MOVE", itemId, day: day.index, position: index + 1 })}
        aria-label={`Move ${stop.title} later`}
      >
        <ArrowDown aria-hidden />
      </Button>
      {dayCount > 1 && (
        <NativeSelect
          className="h-7 w-auto px-2 text-xs"
          value=""
          disabled={disabled}
          onChange={(e) => e.target.value && onEdit({ type: "MOVE", itemId, day: Number(e.target.value) })}
          aria-label={`Move ${stop.title} to another day`}
        >
          <option value="">Move to day…</option>
          {Array.from({ length: dayCount }, (_, d) => d + 1)
            .filter((d) => d !== day.index)
            .map((d) => (
              <option key={d} value={d}>
                Day {d}
              </option>
            ))}
        </NativeSelect>
      )}
      <Button
        variant="ghost"
        size="sm"
        className="h-7 px-2 text-xs"
        disabled={disabled}
        onClick={() => onEdit({ type: "REMOVE", itemId })}
        aria-label={`Take ${stop.title} off day ${day.index}`}
      >
        <X aria-hidden /> Remove
      </Button>
    </>
  );
}
