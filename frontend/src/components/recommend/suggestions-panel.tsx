"use client";

import { useEffect, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Check, ExternalLink, Plus, Sparkles, TrendingDown, TrendingUp, UsersRound } from "lucide-react";

import { AiProvenance } from "@/components/ai-provenance";
import { ErrorState } from "@/components/error-state";
import { LabeledSlider } from "@/components/form-controls";
import { JobProgress } from "@/components/job-progress";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { formatDistance, formatRelative } from "@/lib/format";
import { BROWSABLE, CATEGORY } from "@/lib/places";
import { gemsText } from "@/lib/profile-text";
import { keys, useAddShortlistItem, useRecommendations, useStartRecommendationAi } from "@/lib/queries";
import type { PlaceCategory, Recommendation, RecommendationSet, TripDetail } from "@/lib/types";
import { useAiRun } from "@/lib/use-ai-run";
import { cn } from "@/lib/utils";

/**
 * "Suggest places" (spec §5.5, A2). Code ranks OpenStreetMap places for the whole group; the hidden-gems slider
 * and "avoid crowds" change that ranking instantly. AI re-ranks the top ten and explains each on request.
 */
export function SuggestionsPanel({ trip, onAdded }: { trip: TripDetail; onAdded?: () => void }) {
  const qc = useQueryClient();
  const [category, setCategory] = useState<PlaceCategory>("SIGHTS");
  const [gems, setGems] = useState<number | null>(null);
  const [debouncedGems, setDebouncedGems] = useState<number | null>(null);
  const [avoidCrowds, setAvoidCrowds] = useState(false);
  const [dismissed, setDismissed] = useState(false);

  useEffect(() => {
    const t = setTimeout(() => setDebouncedGems(gems), 350);
    return () => clearTimeout(t);
  }, [gems]);

  const query = { category, gems: debouncedGems, avoidCrowds };
  const recs = useRecommendations(trip.id, query, trip.destinationLat != null);
  const start = useStartRecommendationAi(trip.id);
  const run = useAiRun<RecommendationSet>((set) => {
    setDismissed(false);
    qc.setQueryData(keys.recommendations(trip.id, category, debouncedGems, avoidCrowds), set);
  });

  useEffect(() => setDismissed(false), [category, debouncedGems, avoidCrowds]);

  if (trip.destinationLat == null) {
    return (
      <p className="text-muted-foreground rounded-lg border border-dashed px-4 py-8 text-center text-sm">
        Suggestions need the trip&apos;s location on the map. Recreate the trip with a destination picked from the
        list.
      </p>
    );
  }

  const data = recs.data;
  const sliderValue = gems ?? data?.groupGems ?? 50;
  const isAi = data?.provenance.source === "ai" && !dismissed;
  const items = data ? (isAi ? data.items : [...data.items].sort((a, b) => a.rulesRank - b.rulesRank)) : [];

  const generate = (regenerate: boolean) =>
    start.mutate({ ...query, regenerate }, { onSuccess: run.track });

  return (
    <div className="space-y-5">
      <div className="bg-card grid gap-5 rounded-xl border p-4 sm:p-5 md:grid-cols-[1fr_minmax(0,18rem)]">
        <div className="space-y-4">
          <ToggleGroup
            type="single"
            value={category}
            onValueChange={(v) => v && setCategory(v as PlaceCategory)}
            aria-label="Category"
            className="flex-wrap"
          >
            {BROWSABLE.map((c) => (
              <ToggleGroupItem key={c.id} value={c.id}>
                <c.icon style={{ color: c.color }} aria-hidden /> {c.label}
              </ToggleGroupItem>
            ))}
          </ToggleGroup>
          <label className="flex w-fit cursor-pointer items-center gap-2 text-sm">
            <input
              type="checkbox"
              className="accent-primary size-4"
              checked={avoidCrowds}
              onChange={(e) => setAvoidCrowds(e.target.checked)}
            />
            Avoid crowds <span className="text-muted-foreground">(skip chains, push famous spots down)</span>
          </label>
        </div>
        <div>
          <LabeledSlider
            label="Classics ↔ hidden gems"
            value={sliderValue}
            valueText={`${gemsText(sliderValue)}${gems == null ? " (group average)" : ""}`}
            left="Classics"
            right="Hidden gems"
            step={5}
            onChange={setGems}
          />
          {gems != null && data && (
            <button
              type="button"
              className="text-muted-foreground mt-2 text-xs underline underline-offset-2"
              onClick={() => setGems(null)}
            >
              Reset to the group average ({data.groupGems})
            </button>
          )}
        </div>
      </div>

      {recs.isPending ? (
        <SuggestionsSkeleton />
      ) : recs.isError ? (
        <ErrorState error={recs.error} onRetry={() => recs.refetch()} />
      ) : (
        <>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex flex-wrap items-center gap-2">
              <AiProvenance
                provenance={
                  dismissed
                    ? { ...data!.provenance, source: "rules", note: "AI ranking dismissed; showing the computed order." }
                    : data!.provenance
                }
                onRegenerate={isAi ? () => generate(true) : undefined}
                onDismiss={isAi ? () => setDismissed(true) : undefined}
                regenerating={run.running || start.isPending}
                details={<HowRanked set={data!} isAi={isAi} />}
              />
              {!isAi && data!.aiAvailable && items.length > 1 && !run.running && (
                <Button size="sm" variant="outline" onClick={() => generate(false)} disabled={start.isPending}>
                  <Sparkles className="text-signal" aria-hidden /> Re-rank with AI
                </Button>
              )}
            </div>
            <p className="text-muted-foreground text-xs">
              {data!.candidates} places from {data!.placesSource}
              {data!.placesCheckedAt && <>, checked {formatRelative(data!.placesCheckedAt)}</>}
              {data!.placesStale && <span className="text-warning"> (provider unreachable; older results)</span>}
              {recs.isFetching && " · updating…"}
            </p>
          </div>

          {run.running && (
            <div className="bg-card max-w-md rounded-lg border p-4">
              <JobProgress job={run.job} onCancel={run.cancel} cancelling={run.cancelling} />
            </div>
          )}
          {(run.error || start.error) && (
            <p role="alert" className="text-destructive text-sm">
              {run.error ?? start.error?.message}
            </p>
          )}

          {items.length === 0 ? (
            <p className="text-muted-foreground rounded-lg border border-dashed px-4 py-8 text-center text-sm">
              No new {CATEGORY[category].label.toLowerCase()} to suggest nearby. Everything found may already be on the
              shortlist.
            </p>
          ) : (
            <ol className={cn("grid gap-3 lg:grid-cols-2", recs.isFetching && "opacity-70 transition-opacity")}>
              {items.map((r, i) => (
                <SuggestionCard
                  key={r.place.id}
                  tripId={trip.id}
                  rec={r}
                  position={i + 1}
                  showWhy={isAi || data!.provenance.source === "rules"}
                  aiWhy={isAi}
                  onAdded={onAdded}
                />
              ))}
            </ol>
          )}
        </>
      )}
    </div>
  );
}

function HowRanked({ set, isAi }: { set: RecommendationSet; isAi: boolean }) {
  return (
    <div className="text-muted-foreground space-y-2 text-xs">
      <p>
        Each place was scored by code against every member&apos;s interests, crowd tolerance, dietary and access needs,
        and dealbreakers. The hidden-gems setting ({set.gems}/100) lifts little-known, locally rooted places and pushes
        famous or chain places down.
      </p>
      <p>
        {isAi
          ? "The AI then re-ordered the top places (it could only choose from them) and wrote each “why” from the computed facts."
          : "Explanations are filled in from the same computed facts."}
      </p>
      <p>No paid placement: nothing commercial is used in ranking.</p>
    </div>
  );
}

function SuggestionCard({
  tripId,
  rec,
  position,
  showWhy,
  aiWhy,
  onAdded,
}: {
  tripId: string;
  rec: Recommendation;
  position: number;
  showWhy: boolean;
  aiWhy: boolean;
  onAdded?: () => void;
}) {
  const add = useAddShortlistItem(tripId);
  const cat = CATEGORY[rec.place.category];
  const moved = aiWhy ? rec.rulesRank - rec.rank : 0;

  return (
    <li className="bg-card flex flex-col rounded-lg border p-4">
      <div className="flex items-start gap-3">
        <span
          aria-hidden
          className="figures bg-sounding grid size-7 shrink-0 place-items-center rounded-full text-xs font-semibold"
        >
          {position}
        </span>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
            <h4 className="font-medium">
              <span className="sr-only">{position}. </span>
              {rec.place.name}
            </h4>
            {rec.sponsored && <Badge variant="warning">Sponsored</Badge>}
          </div>
          <p className="text-muted-foreground mt-0.5 flex flex-wrap items-center gap-x-3 text-xs">
            <span className="inline-flex items-center gap-1">
              <cat.icon className="size-3.5" style={{ color: cat.color }} aria-hidden /> {cat.noun}
            </span>
            <span>{formatDistance(rec.distanceKm)} from the centre</span>
            <span className="figures">Fit {rec.score}/100</span>
            {moved !== 0 && (
              <span className="inline-flex items-center gap-1">
                {moved > 0 ? (
                  <TrendingUp className="text-signal size-3.5" aria-hidden />
                ) : (
                  <TrendingDown className="size-3.5" aria-hidden />
                )}
                AI moved it {moved > 0 ? "up" : "down"} from #{rec.rulesRank}
              </span>
            )}
          </p>
        </div>
      </div>

      {showWhy && (
        <p className="mt-3 text-sm">
          {aiWhy && <Sparkles className="text-signal mr-1 inline size-3.5 align-[-2px]" aria-label="AI-written" />}
          {rec.why}
        </p>
      )}

      <div className="mt-3 flex flex-wrap gap-1.5">
        {rec.matchedInterests.map((t) => (
          <Badge key={t} variant="secondary">
            {t}
          </Badge>
        ))}
        {rec.localSignals.slice(0, 2).map((s) => (
          <Badge key={s} variant="land">
            {s}
          </Badge>
        ))}
      </div>
      {(rec.fitsMembers.length > 0 || rec.missesMembers.length > 0) && (
        <p className="text-muted-foreground mt-2 flex items-start gap-1.5 text-xs">
          <UsersRound className="mt-px size-3.5 shrink-0" aria-hidden />
          <span>
            {rec.fitsMembers.length > 0 && <>Suits {rec.fitsMembers.join(", ")}. </>}
            {rec.missesMembers.length > 0 && <>Less so for {rec.missesMembers.join(", ")}.</>}
          </span>
        </p>
      )}

      <div className="mt-auto flex flex-wrap items-center gap-2 pt-4">
        <Button
          size="sm"
          onClick={() =>
            add.mutate(
              { kind: "PLACE", placeId: rec.place.id, reason: `Suggested: ${rec.why}`.slice(0, 500) },
              { onSuccess: () => onAdded?.() },
            )
          }
          disabled={add.isPending || add.isSuccess}
        >
          {add.isSuccess ? <Check aria-hidden /> : <Plus aria-hidden />}
          {add.isSuccess ? "Added" : "Add to shortlist"}
        </Button>
        {rec.place.sourceUrl && (
          <Button size="sm" variant="ghost" asChild>
            <a href={rec.place.sourceUrl} target="_blank" rel="noopener noreferrer">
              <ExternalLink aria-hidden /> OpenStreetMap
            </a>
          </Button>
        )}
        {add.error && (
          <p role="alert" className="text-destructive w-full text-xs">
            {add.error.message}
          </p>
        )}
      </div>
    </li>
  );
}

function SuggestionsSkeleton() {
  return (
    <div role="status" aria-label="Finding places" className="grid gap-3 lg:grid-cols-2">
      {[0, 1, 2, 3].map((i) => (
        <div key={i} className="bg-card animate-pulse rounded-lg border p-4">
          <div className="bg-muted h-5 w-1/2 rounded" />
          <div className="bg-muted mt-3 h-3 w-3/4 rounded" />
          <div className="bg-muted mt-2 h-3 w-2/3 rounded" />
        </div>
      ))}
      <p className="text-muted-foreground text-xs lg:col-span-2">
        Looking up places on OpenStreetMap. The first search for a category can take a few seconds.
      </p>
    </div>
  );
}
