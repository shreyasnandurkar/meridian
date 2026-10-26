"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { List, ListChecks, Map as MapIcon, Plus, Sparkles } from "lucide-react";

import { ErrorState } from "@/components/error-state";
import { TripMap, type MapMarker } from "@/components/map/trip-map";
import { AddPlaceDialog } from "@/components/shortlist/add-place-dialog";
import { SuggestionsPanel } from "@/components/recommend/suggestions-panel";
import { EditItemDialog } from "@/components/shortlist/edit-item-dialog";
import { ShortlistItemCard } from "@/components/shortlist/shortlist-item-card";
import { Button } from "@/components/ui/button";
import { NativeSelect } from "@/components/ui/select";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { useAuth } from "@/lib/auth";
import { initials } from "@/lib/format";
import { CATEGORIES, CATEGORY } from "@/lib/places";
import { useGroupFit, useReact, useShortlist } from "@/lib/queries";
import type { PlaceCategory, ShortlistItem, TripDetail } from "@/lib/types";
import { cn } from "@/lib/utils";

type View = "list" | "map" | "suggest";
type FitFilter = "ALL" | "GOOD" | "MIXED";

export function ShortlistTab({ trip }: { trip: TripDetail }) {
  const { user } = useAuth();
  const me = { id: user!.id, displayName: user!.displayName };
  const shortlist = useShortlist(trip.id);
  const react = useReact(trip.id, me);
  const groupFit = useGroupFit(trip.id);

  const [view, setView] = useState<View>("list");
  const [category, setCategory] = useState<PlaceCategory | "ALL">("ALL");
  const [adder, setAdder] = useState<string>("ALL");
  const [fitFilter, setFitFilter] = useState<FitFilter>("ALL");
  const [adding, setAdding] = useState(false);
  const [editing, setEditing] = useState<ShortlistItem | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const cardRefs = useRef(new Map<string, HTMLLIElement>());

  const origin =
    trip.destinationLat != null && trip.destinationLng != null
      ? { lat: trip.destinationLat, lng: trip.destinationLng }
      : null;
  const items = useMemo(() => shortlist.data ?? [], [shortlist.data]);
  const fits = useMemo(
    () => new Map((groupFit.data?.items ?? []).map((f) => [f.itemId, { score: f.score, summary: f.summary }])),
    [groupFit.data],
  );
  const filtered = useMemo(
    () =>
      items.filter((i) => {
        if (category !== "ALL" && i.category !== category) return false;
        if (adder !== "ALL" && i.addedBy.userId !== adder) return false;
        if (fitFilter === "ALL") return true;
        const score = fits.get(i.id)?.score;
        return score != null && score >= (fitFilter === "GOOD" ? 65 : 40);
      }),
    [items, category, adder, fitFilter, fits],
  );
  const markers: MapMarker[] = useMemo(
    () =>
      filtered
        .filter((i) => i.lat != null && i.lng != null)
        .map((i) => ({
          id: i.id,
          lat: i.lat!,
          lng: i.lng!,
          category: i.category,
          label: initials(i.addedBy.displayName),
          title: i.title,
          detail: `Added by ${i.addedBy.displayName}${i.reason ? `: ${i.reason}` : ""}`,
        })),
    [filtered],
  );

  useEffect(() => {
    if (selectedId) cardRefs.current.get(selectedId)?.scrollIntoView({ behavior: "smooth", block: "nearest" });
  }, [selectedId]);

  if (shortlist.isPending) return <ShortlistSkeleton />;
  if (shortlist.isError) return <ErrorState error={shortlist.error} onRetry={() => shortlist.refetch()} />;

  const counts = new Map<string, number>();
  items.forEach((i) => counts.set(i.category, (counts.get(i.category) ?? 0) + 1));
  const offMap = filtered.length - markers.length;

  const list = (
    <ul className="space-y-3" aria-label="Shortlisted places">
      {filtered.map((item) => (
        <ShortlistItemCard
          key={item.id}
          ref={(el) => {
            if (el) cardRefs.current.set(item.id, el);
            else cardRefs.current.delete(item.id);
          }}
          item={item}
          origin={origin}
          myId={me.id}
          selected={view === "map" && item.id === selectedId}
          onSelect={() => {
            setSelectedId(item.id);
            setView("map");
          }}
          onEdit={() => setEditing(item)}
          onReact={(value, reason) => react.mutate({ itemId: item.id, value, reason })}
          fit={fits.get(item.id)}
        />
      ))}
    </ul>
  );

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h2 className="text-xl font-semibold">Shared shortlist</h2>
          <p className="text-muted-foreground text-sm">
            {items.length === 0
              ? "Places anyone in the group wants to consider."
              : `${items.length} ${items.length === 1 ? "place" : "places"} from ${new Set(items.map((i) => i.addedBy.userId)).size} of ${trip.members.length} members.`}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <ToggleGroup type="single" value={view} onValueChange={(v) => v && setView(v as View)} aria-label="View">
            {items.length > 0 && (
              <ToggleGroupItem value="list">
                <List aria-hidden /> List
              </ToggleGroupItem>
            )}
            {items.length > 0 && (
              <ToggleGroupItem value="map">
                <MapIcon aria-hidden /> Map
              </ToggleGroupItem>
            )}
            <ToggleGroupItem value="suggest">
              <Sparkles className="text-signal" aria-hidden /> Suggestions
            </ToggleGroupItem>
          </ToggleGroup>
          <Button onClick={() => setAdding(true)}>
            <Plus aria-hidden /> Add a place
          </Button>
        </div>
      </div>

      {view === "suggest" ? (
        <SuggestionsPanel trip={trip} />
      ) : items.length === 0 ? (
        <div className="graticule flex flex-col items-center rounded-xl border border-dashed px-6 py-16 text-center">
          <ListChecks className="text-muted-foreground size-8" aria-hidden />
          <h3 className="mt-4 font-medium">Nothing on the shortlist yet</h3>
          <p className="text-muted-foreground mt-1 max-w-md text-sm">
            Search OpenStreetMap, paste a link to a stay or tour, or drop a pin on the map. Say why, so the group
            knows what you liked about it.
          </p>
          <div className="mt-6 flex flex-wrap justify-center gap-2">
            <Button onClick={() => setAdding(true)}>
              <Plus aria-hidden /> Add the first place
            </Button>
            <Button variant="outline" onClick={() => setView("suggest")}>
              <Sparkles className="text-signal" aria-hidden /> Get suggestions
            </Button>
          </div>
        </div>
      ) : (
        <>
          <div className="flex flex-wrap items-center gap-x-4 gap-y-3">
            <div role="group" aria-label="Filter by category" className="flex flex-wrap gap-1.5">
              <FilterChip active={category === "ALL"} onClick={() => setCategory("ALL")}>
                All <span className="figures text-muted-foreground">{items.length}</span>
              </FilterChip>
              {CATEGORIES.filter((c) => counts.has(c.id)).map((c) => (
                <FilterChip key={c.id} active={category === c.id} onClick={() => setCategory(c.id)}>
                  <c.icon className="size-3.5" style={{ color: c.color }} aria-hidden /> {c.label}{" "}
                  <span className="figures text-muted-foreground">{counts.get(c.id)}</span>
                </FilterChip>
              ))}
            </div>
            <label className="text-muted-foreground flex items-center gap-2 text-sm">
              Added by
              <NativeSelect className="h-8 w-auto py-0" value={adder} onChange={(e) => setAdder(e.target.value)}>
                <option value="ALL">Anyone</option>
                {trip.members.map((m) => (
                  <option key={m.userId} value={m.userId}>
                    {m.userId === me.id ? "Me" : m.displayName}
                  </option>
                ))}
              </NativeSelect>
            </label>
            <label className="text-muted-foreground flex items-center gap-2 text-sm">
              Group fit
              <NativeSelect
                className="h-8 w-auto py-0"
                value={fitFilter}
                onChange={(e) => setFitFilter(e.target.value as FitFilter)}
              >
                <option value="ALL">Any</option>
                <option value="GOOD">Good fit (65+)</option>
                <option value="MIXED">At least mixed (40+)</option>
              </NativeSelect>
            </label>
          </div>

          {filtered.length === 0 ? (
            <p className="text-muted-foreground rounded-lg border border-dashed px-4 py-8 text-center text-sm">
              Nothing matches these filters.{" "}
              <button
                type="button"
                className="text-foreground underline underline-offset-2"
                onClick={() => {
                  setCategory("ALL");
                  setAdder("ALL");
                  setFitFilter("ALL");
                }}
              >
                Clear filters
              </button>
            </p>
          ) : view === "list" ? (
            list
          ) : (
            <div className="grid gap-4 lg:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)]">
              <div className="lg:sticky lg:top-4 lg:self-start">
                <TripMap
                  center={origin}
                  markers={markers}
                  selectedId={selectedId}
                  onSelect={setSelectedId}
                  className="h-[22rem] lg:h-[calc(100dvh-10rem)] lg:max-h-[40rem]"
                  label={`Map of ${markers.length} shortlisted places`}
                />
                <MapLegend items={filtered} offMap={offMap} />
              </div>
              <div className="lg:max-h-[calc(100dvh-6rem)] lg:overflow-y-auto lg:pr-1">{list}</div>
            </div>
          )}
        </>
      )}

      <AddPlaceDialog
        trip={trip}
        open={adding}
        onOpenChange={setAdding}
        existing={items}
        onAdded={(item) => {
          setCategory("ALL");
          setAdder("ALL");
          setSelectedId(item.id);
        }}
      />
      <EditItemDialog tripId={trip.id} item={editing} onOpenChange={(o) => !o && setEditing(null)} />
    </div>
  );
}

function FilterChip({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      aria-pressed={active}
      onClick={onClick}
      className={cn(
        "focus-visible:ring-ring/50 inline-flex h-8 items-center gap-1.5 rounded-full border px-3 text-sm outline-none focus-visible:ring-[3px]",
        active ? "border-foreground/60 bg-sounding/50 text-foreground" : "text-muted-foreground hover:bg-muted",
      )}
    >
      {children}
    </button>
  );
}

/** Marker key: colour + icon per category, initials = who added it. */
function MapLegend({ items, offMap }: { items: ShortlistItem[]; offMap: number }) {
  const cats = [...new Set(items.map((i) => i.category))];
  return (
    <div className="text-muted-foreground mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs">
      {cats.map((c) => {
        const meta = CATEGORY[c];
        return (
          <span key={c} className="inline-flex items-center gap-1.5">
            <span aria-hidden className="size-2.5 rounded-full" style={{ background: meta.color }} />
            {meta.label}
          </span>
        );
      })}
      <span>Letters show who added each place.</span>
      {offMap > 0 && (
        <span>
          {offMap} {offMap === 1 ? "link has" : "links have"} no map location.
        </span>
      )}
    </div>
  );
}

function ShortlistSkeleton() {
  return (
    <div role="status" aria-label="Loading shortlist" className="space-y-3">
      {[0, 1, 2].map((i) => (
        <div key={i} className="bg-card animate-pulse rounded-lg border p-5">
          <div className="bg-muted h-5 w-1/3 rounded" />
          <div className="bg-muted mt-3 h-3 w-1/2 rounded" />
          <div className="bg-muted mt-4 h-8 w-2/3 rounded" />
        </div>
      ))}
    </div>
  );
}
