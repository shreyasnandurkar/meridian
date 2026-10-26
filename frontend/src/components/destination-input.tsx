"use client";

import { useEffect, useId, useState } from "react";
import { Loader2, MapPin } from "lucide-react";

import { Input } from "@/components/ui/input";
import { useGeoSearch } from "@/lib/queries";
import type { GeoResult } from "@/lib/types";
import { cn } from "@/lib/utils";

export interface Destination {
  name: string;
  lat: number | null;
  lng: number | null;
}

/** Destination autocomplete (OpenStreetMap Nominatim via the API). Free text still works if search is down. */
export function DestinationInput({
  value,
  onChange,
  invalid,
}: {
  value: Destination;
  onChange: (d: Destination) => void;
  invalid?: boolean;
}) {
  const id = useId();
  const listId = `${id}-list`;
  const [text, setText] = useState(value.name);
  const [debounced, setDebounced] = useState(value.name);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);

  useEffect(() => {
    const t = setTimeout(() => setDebounced(text), 350);
    return () => clearTimeout(t);
  }, [text]);

  const search = useGeoSearch(open ? debounced : "");
  const results = search.data ?? [];
  const showList = open && debounced.trim().length >= 3 && (results.length > 0 || search.isFetching);

  const choose = (r: GeoResult) => {
    const name = r.displayName.split(",").slice(0, 3).join(",").trim();
    setText(name);
    setOpen(false);
    setActive(-1);
    onChange({ name, lat: r.lat, lng: r.lng });
  };

  return (
    <div className="relative">
      <label htmlFor={id} className="mb-2 block text-sm font-medium">
        Destination
      </label>
      <div className="relative">
        <MapPin className="text-muted-foreground pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2" aria-hidden />
        <Input
          id={id}
          role="combobox"
          aria-expanded={showList}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={active >= 0 ? `${listId}-${active}` : undefined}
          aria-invalid={invalid || undefined}
          autoComplete="off"
          className="pl-9"
          placeholder="Start typing a city or region"
          value={text}
          onChange={(e) => {
            setText(e.target.value);
            setOpen(true);
            setActive(-1);
            onChange({ name: e.target.value, lat: null, lng: null });
          }}
          onFocus={() => setOpen(true)}
          onBlur={() => setTimeout(() => setOpen(false), 150)}
          onKeyDown={(e) => {
            if (!showList) return;
            if (e.key === "ArrowDown") {
              e.preventDefault();
              setActive((a) => Math.min(a + 1, results.length - 1));
            } else if (e.key === "ArrowUp") {
              e.preventDefault();
              setActive((a) => Math.max(a - 1, 0));
            } else if (e.key === "Enter" && active >= 0) {
              e.preventDefault();
              choose(results[active]);
            } else if (e.key === "Escape") {
              setOpen(false);
            }
          }}
        />
        {search.isFetching && (
          <Loader2 className="text-muted-foreground absolute top-1/2 right-3 size-4 -translate-y-1/2 animate-spin" aria-hidden />
        )}
      </div>
      {showList && (
        <ul
          id={listId}
          role="listbox"
          className="bg-popover absolute z-20 mt-1 max-h-72 w-full overflow-auto rounded-lg border p-1 shadow-md"
        >
          {results.length === 0 && <li className="text-muted-foreground px-3 py-2 text-sm">Searching…</li>}
          {results.map((r, i) => (
            <li
              key={`${r.lat},${r.lng},${i}`}
              id={`${listId}-${i}`}
              role="option"
              aria-selected={i === active}
              onMouseDown={(e) => {
                e.preventDefault();
                choose(r);
              }}
              className={cn("cursor-pointer rounded-md px-3 py-2 text-sm", i === active ? "bg-muted" : "hover:bg-muted")}
            >
              <span className="font-medium">{r.name}</span>
              <span className="text-muted-foreground block truncate text-xs">{r.displayName}</span>
            </li>
          ))}
        </ul>
      )}
      <p className="text-muted-foreground mt-1.5 text-xs">
        {search.isError
          ? "Place search is unavailable — the name you type will be used."
          : value.lat != null
            ? `Pinned at ${value.lat.toFixed(3)}, ${value.lng?.toFixed(3)} · © OpenStreetMap contributors`
            : "Pick a suggestion to pin it on the map."}
      </p>
    </div>
  );
}
