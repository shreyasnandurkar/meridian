"use client";

import { useEffect, useId, useState } from "react";
import { AlertTriangle, Check, Crosshair, Link2, Loader2, MapPin, Search } from "lucide-react";

import { TripMap } from "@/components/map/trip-map";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { NativeSelect } from "@/components/ui/select";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { distanceKm, formatDistance, formatRelative } from "@/lib/format";
import { BROWSABLE, CATEGORIES, CATEGORY } from "@/lib/places";
import { useAddShortlistItem, useLinkPreview, useNearbyPlaces, usePlaceSearch } from "@/lib/queries";
import type { AddShortlistInput, Place, PlaceCategory, PlaceResults, ShortlistItem, TripDetail } from "@/lib/types";
import { cn } from "@/lib/utils";

type Mode = "search" | "link" | "pin";

/** Add to the shortlist by place search, pasted link or map pin; every route asks why. */
export function AddPlaceDialog({
  trip,
  open,
  onOpenChange,
  existing,
  onAdded,
}: {
  trip: TripDetail;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  existing: ShortlistItem[];
  onAdded: (item: ShortlistItem) => void;
}) {
  const [mode, setMode] = useState<Mode>("search");
  const [formKey, setFormKey] = useState(0);
  const add = useAddShortlistItem(trip.id);

  const close = (o: boolean) => {
    onOpenChange(o);
    if (!o) {
      add.reset();
      setFormKey((k) => k + 1);
    }
  };
  const submit = (input: AddShortlistInput) =>
    add.mutate(input, {
      onSuccess: (item) => {
        onAdded(item);
        close(false);
      },
    });

  const props = { trip, existing, submit, pending: add.isPending, error: add.error?.message ?? null };
  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>Add to the shortlist</DialogTitle>
          <DialogDescription>
            Everyone sees who added each place and why, so say what drew you to it.
          </DialogDescription>
        </DialogHeader>
        <ToggleGroup
          type="single"
          value={mode}
          onValueChange={(v) => v && setMode(v as Mode)}
          aria-label="How to add"
          className="self-start"
        >
          <ToggleGroupItem value="search">
            <Search aria-hidden /> Search
          </ToggleGroupItem>
          <ToggleGroupItem value="link">
            <Link2 aria-hidden /> Paste a link
          </ToggleGroupItem>
          <ToggleGroupItem value="pin">
            <MapPin aria-hidden /> Drop a pin
          </ToggleGroupItem>
        </ToggleGroup>
        <div key={formKey}>
          {mode === "search" && <SearchForm {...props} />}
          {mode === "link" && <LinkForm {...props} />}
          {mode === "pin" && <PinForm {...props} />}
        </div>
      </DialogContent>
    </Dialog>
  );
}

interface FormProps {
  trip: TripDetail;
  existing: ShortlistItem[];
  submit: (input: AddShortlistInput) => void;
  pending: boolean;
  error: string | null;
}

function SearchForm({ trip, existing, submit, pending, error }: FormProps) {
  const located = trip.destinationLat != null && trip.destinationLng != null;
  const [text, setText] = useState("");
  const [query, setQuery] = useState("");
  const [browse, setBrowse] = useState<PlaceCategory | null>(located ? "SIGHTS" : null);
  const [chosen, setChosen] = useState<Place | null>(null);
  const [reason, setReason] = useState("");

  useEffect(() => {
    const t = setTimeout(() => setQuery(text), 400);
    return () => clearTimeout(t);
  }, [text]);

  const searching = query.trim().length >= 2;
  const search = usePlaceSearch(trip.id, query);
  const nearby = useNearbyPlaces(trip.id, searching ? null : browse);
  const active = searching ? search : nearby;
  const onList = new Set(existing.map((i) => i.place?.id).filter(Boolean));

  if (chosen) {
    return (
      <form
        className="space-y-4"
        onSubmit={(e) => {
          e.preventDefault();
          submit({ kind: "PLACE", placeId: chosen.id, reason: reason.trim() || undefined });
        }}
      >
        <div className="bg-muted/60 flex items-start justify-between gap-3 rounded-lg border p-3">
          <PlaceSummary place={trip.destinationLat != null ? withDistance(chosen, trip) : chosen} />
          <Button type="button" variant="ghost" size="sm" onClick={() => setChosen(null)}>
            Change
          </Button>
        </div>
        <ReasonField value={reason} onChange={setReason} autoFocus />
        <Footer pending={pending} error={error} canSubmit />
      </form>
    );
  }

  return (
    <div className="space-y-4">
      <div>
        <label htmlFor="place-search" className="mb-2 block text-sm font-medium">
          Search places in <span className="place">{trip.destinationName}</span>
        </label>
        <div className="relative">
          <Search className="text-muted-foreground pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2" aria-hidden />
          <Input
            id="place-search"
            autoFocus
            className="pl-9"
            placeholder="A café, a fort, a beach…"
            value={text}
            onChange={(e) => setText(e.target.value)}
            maxLength={200}
          />
        </div>
      </div>
      {!searching && located && (
        <div role="group" aria-label="Browse nearby by category" className="flex flex-wrap gap-2">
          {BROWSABLE.map((c) => (
            <button
              key={c.id}
              type="button"
              aria-pressed={browse === c.id}
              onClick={() => setBrowse(c.id)}
              className={cn(
                "focus-visible:ring-ring/50 inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-sm outline-none focus-visible:ring-[3px]",
                browse === c.id ? "border-foreground/60 bg-sounding/50" : "text-muted-foreground hover:bg-muted",
              )}
            >
              <c.icon className="size-3.5" style={{ color: c.color }} aria-hidden /> {c.label} nearby
            </button>
          ))}
        </div>
      )}
      <Results
        results={active.data}
        loading={active.isFetching && !active.data}
        error={active.isError ? active.error.message : null}
        idle={!searching && !located}
        trip={trip}
        onList={onList}
        onChoose={setChosen}
      />
    </div>
  );
}

function withDistance(place: Place, trip: TripDetail) {
  return {
    ...place,
    distance: distanceKm(trip.destinationLat!, trip.destinationLng!, place.lat, place.lng),
  };
}

function PlaceSummary({ place }: { place: Place & { distance?: number } }) {
  const cat = CATEGORY[place.category];
  return (
    <div className="min-w-0">
      <p className="place text-lg leading-snug">{place.name}</p>
      <p className="text-muted-foreground figures text-xs">
        <span className={cn("font-medium", cat.chip)}>{cat.label}</span>
        {place.distance != null && ` · ${formatDistance(place.distance)} from centre`}
        {place.address && ` · ${place.address}`}
      </p>
    </div>
  );
}

function Results({
  results,
  loading,
  error,
  idle,
  trip,
  onList,
  onChoose,
}: {
  results: PlaceResults | undefined;
  loading: boolean;
  error: string | null;
  idle: boolean;
  trip: TripDetail;
  onList: Set<string | undefined>;
  onChoose: (p: Place) => void;
}) {
  if (idle) {
    return <p className="text-muted-foreground text-sm">Type at least two letters to search.</p>;
  }
  if (loading) {
    return (
      <p role="status" className="text-muted-foreground flex items-center gap-2 py-6 text-sm">
        <Loader2 className="size-4 animate-spin" aria-hidden /> Asking OpenStreetMap…
      </p>
    );
  }
  if (error) {
    return (
      <p role="alert" className="text-destructive flex items-start gap-2 text-sm">
        <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden /> {error}
      </p>
    );
  }
  if (!results) return null;
  if (results.places.length === 0) {
    return <p className="text-muted-foreground py-4 text-sm">No places found. Try another name, or drop a pin.</p>;
  }
  const origin = trip.destinationLat != null ? { lat: trip.destinationLat, lng: trip.destinationLng! } : null;
  return (
    <div>
      <ul className="max-h-72 space-y-1 overflow-y-auto pr-1" aria-label="Places found">
        {results.places.map((p) => {
          const cat = CATEGORY[p.category];
          const already = onList.has(p.id);
          return (
            <li key={p.id}>
              <button
                type="button"
                disabled={already}
                onClick={() => onChoose(p)}
                className="hover:bg-muted focus-visible:ring-ring/50 flex w-full items-start gap-3 rounded-md px-3 py-2 text-left outline-none focus-visible:ring-2 disabled:cursor-not-allowed disabled:opacity-60"
              >
                <cat.icon className="mt-1 size-4 shrink-0" style={{ color: cat.color }} aria-hidden />
                <span className="min-w-0 flex-1">
                  <span className="block truncate font-medium">{p.name}</span>
                  <span className="text-muted-foreground figures block truncate text-xs">
                    {cat.label}
                    {origin && ` · ${formatDistance(distanceKm(origin.lat, origin.lng, p.lat, p.lng))}`}
                    {p.address && ` · ${p.address}`}
                  </span>
                </span>
                {already && (
                  <span className="text-success inline-flex shrink-0 items-center gap-1 text-xs">
                    <Check className="size-3.5" aria-hidden /> On the shortlist
                  </span>
                )}
              </button>
            </li>
          );
        })}
      </ul>
      <p className="text-muted-foreground mt-2 text-xs">
        {results.source}
        {results.fetchedAt && ` · checked ${formatRelative(results.fetchedAt)}`}
        {results.stale && (
          <span className="text-warning ml-1 inline-flex items-center gap-1 font-medium">
            <AlertTriangle className="size-3" aria-hidden /> OpenStreetMap is unreachable, showing saved results
          </span>
        )}
      </p>
    </div>
  );
}

function LinkForm({ trip, submit, pending, error }: FormProps) {
  const preview = useLinkPreview(trip.id);
  const [url, setUrl] = useState("");
  const [title, setTitle] = useState("");
  const [titleTouched, setTitleTouched] = useState(false);
  const [category, setCategory] = useState<PlaceCategory>("STAYS");
  const [reason, setReason] = useState("");

  const read = (value: string) => {
    const u = value.trim();
    if (!u || preview.isPending) return;
    preview.mutate(u, {
      onSuccess: (p) => {
        setUrl(p.url);
        if (!titleTouched && p.title) setTitle(p.title);
      },
    });
  };

  return (
    <form
      className="space-y-4"
      onSubmit={(e) => {
        e.preventDefault();
        submit({
          kind: "LINK",
          url: url.trim(),
          title: title.trim() || undefined,
          category,
          reason: reason.trim() || undefined,
        });
      }}
    >
      <div>
        <label htmlFor="link-url" className="mb-2 block text-sm font-medium">
          Link
        </label>
        <div className="flex gap-2">
          <Input
            id="link-url"
            type="url"
            inputMode="url"
            autoFocus
            required
            placeholder="https://…"
            value={url}
            maxLength={2048}
            onChange={(e) => setUrl(e.target.value)}
            onPaste={(e) => read(e.clipboardData.getData("text"))}
            onBlur={() => !preview.data && read(url)}
            aria-describedby="link-hint"
          />
          <Button type="button" variant="outline" onClick={() => read(url)} disabled={!url.trim() || preview.isPending}>
            {preview.isPending ? <Loader2 className="animate-spin" aria-hidden /> : null} Read title
          </Button>
        </div>
        <p id="link-hint" className="text-muted-foreground mt-1.5 text-xs" aria-live="polite">
          {preview.isError
            ? preview.error.message
            : preview.data && !preview.data.title
              ? `Couldn't read a title from ${preview.data.host}. Type one below.`
              : "A stay, a tour, an article. Meridian only reads the page title and never books anything."}
        </p>
      </div>
      <div>
        <label htmlFor="link-title" className="mb-2 block text-sm font-medium">
          Title
        </label>
        <Input
          id="link-title"
          value={title}
          maxLength={300}
          placeholder="Read from the page, or type one"
          onChange={(e) => {
            setTitle(e.target.value);
            setTitleTouched(true);
          }}
        />
      </div>
      <CategoryField value={category} onChange={setCategory} />
      <ReasonField value={reason} onChange={setReason} />
      <Footer pending={pending} error={error} canSubmit={!!url.trim()} />
    </form>
  );
}

function PinForm({ trip, submit, pending, error }: FormProps) {
  const center = trip.destinationLat != null ? { lat: trip.destinationLat, lng: trip.destinationLng! } : null;
  const [picked, setPicked] = useState<{ lat: number; lng: number } | null>(null);
  const [title, setTitle] = useState("");
  const [category, setCategory] = useState<PlaceCategory>("OTHER");
  const [reason, setReason] = useState("");
  const titleId = useId();

  return (
    <form
      className="space-y-4"
      onSubmit={(e) => {
        e.preventDefault();
        if (!picked) return;
        submit({
          kind: "PIN",
          title: title.trim(),
          lat: picked.lat,
          lng: picked.lng,
          category,
          reason: reason.trim() || undefined,
        });
      }}
    >
      <div>
        <TripMap
          center={center}
          pickMode
          picked={picked}
          onPick={(lat, lng) => setPicked({ lat, lng })}
          className="h-64"
          label="Map: click to drop a pin"
        />
        <div className="text-muted-foreground mt-1.5 flex flex-wrap items-center justify-between gap-2 text-xs">
          <span aria-live="polite" className="figures">
            {picked
              ? `Pin at ${picked.lat.toFixed(4)}, ${picked.lng.toFixed(4)}. Drag it to adjust.`
              : "Click the map where the place is."}
          </span>
          {center && (
            <Button type="button" variant="ghost" size="sm" onClick={() => setPicked(center)}>
              <Crosshair aria-hidden /> Drop at the destination centre
            </Button>
          )}
        </div>
      </div>
      <div>
        <label htmlFor={titleId} className="mb-2 block text-sm font-medium">
          Name
        </label>
        <Input
          id={titleId}
          required
          value={title}
          maxLength={300}
          placeholder="e.g. Quiet cove past the fishing boats"
          onChange={(e) => setTitle(e.target.value)}
        />
      </div>
      <CategoryField value={category} onChange={setCategory} />
      <ReasonField value={reason} onChange={setReason} />
      <Footer pending={pending} error={error} canSubmit={!!picked && !!title.trim()} />
    </form>
  );
}

export function CategoryField({ value, onChange }: { value: PlaceCategory; onChange: (c: PlaceCategory) => void }) {
  const id = useId();
  return (
    <div>
      <label htmlFor={id} className="mb-2 block text-sm font-medium">
        Category
      </label>
      <NativeSelect id={id} value={value} onChange={(e) => onChange(e.target.value as PlaceCategory)}>
        {CATEGORIES.map((c) => (
          <option key={c.id} value={c.id}>
            {c.label}
          </option>
        ))}
      </NativeSelect>
    </div>
  );
}

export function ReasonField({
  value,
  onChange,
  autoFocus,
}: {
  value: string;
  onChange: (v: string) => void;
  autoFocus?: boolean;
}) {
  const id = useId();
  return (
    <div>
      <label htmlFor={id} className="mb-2 block text-sm font-medium">
        Why this one? <span className="text-muted-foreground font-normal">(shown to the group)</span>
      </label>
      <Input
        id={id}
        autoFocus={autoFocus}
        value={value}
        maxLength={500}
        placeholder="e.g. Locals' favourite for xacuti, away from the beach crowds"
        onChange={(e) => onChange(e.target.value)}
      />
    </div>
  );
}

function Footer({ pending, error, canSubmit }: { pending: boolean; error: string | null; canSubmit: boolean }) {
  return (
    <div className="space-y-3 pt-1">
      {error && (
        <p role="alert" className="text-destructive text-sm">
          {error}
        </p>
      )}
      <DialogFooter>
        <Button type="submit" disabled={!canSubmit || pending}>
          {pending && <Loader2 className="animate-spin" aria-hidden />} Add to shortlist
        </Button>
      </DialogFooter>
    </div>
  );
}
