"use client";

import { useEffect, useRef, useState } from "react";
import type { Map as MapLibreMap, Marker as MapLibreMarker, Popup as MapLibrePopup } from "maplibre-gl";
import "maplibre-gl/dist/maplibre-gl.css";

import { CATEGORY } from "@/lib/places";
import type { PlaceCategory } from "@/lib/types";
import { cn } from "@/lib/utils";

export interface MapMarker {
  id: string;
  lat: number;
  lng: number;
  category: PlaceCategory;
  /** Short text inside the marker, e.g. the adder's initials. */
  label: string;
  title: string;
  /** Second line in the popup, e.g. "Added by Asha: sunset views". */
  detail?: string;
}

type MapLibre = typeof import("maplibre-gl");

const OSM_STYLE = {
  version: 8 as const,
  sources: {
    osm: {
      type: "raster" as const,
      tiles: ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
      tileSize: 256,
      maxzoom: 19,
      attribution: '© <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a> contributors',
    },
  },
  layers: [{ id: "osm", type: "raster" as const, source: "osm" }],
};

let workerConfigured = false;

/**
 * MapLibre GL map on OpenStreetMap raster tiles. Markers are real buttons (keyboard-focusable, labelled), coloured by
 * category and lettered with the adder's initials. In pick mode a click drops a draggable pin.
 */
export function TripMap({
  center,
  markers = [],
  selectedId,
  onSelect,
  pickMode = false,
  picked,
  onPick,
  className,
  label,
}: {
  center: { lat: number; lng: number } | null;
  markers?: MapMarker[];
  selectedId?: string | null;
  onSelect?: (id: string) => void;
  pickMode?: boolean;
  picked?: { lat: number; lng: number } | null;
  onPick?: (lat: number, lng: number) => void;
  className?: string;
  label: string;
}) {
  const container = useRef<HTMLDivElement>(null);
  const lib = useRef<MapLibre | null>(null);
  const map = useRef<MapLibreMap | null>(null);
  const markerRefs = useRef(new Map<string, { marker: MapLibreMarker; el: HTMLButtonElement }>());
  const popup = useRef<MapLibrePopup | null>(null);
  const pin = useRef<MapLibreMarker | null>(null);
  const fittedFor = useRef("");
  const [ready, setReady] = useState(false);
  const [failed, setFailed] = useState(false);

  // latest callbacks without re-binding map listeners
  const onSelectRef = useRef(onSelect);
  const onPickRef = useRef(onPick);
  const pickModeRef = useRef(pickMode);
  useEffect(() => {
    onSelectRef.current = onSelect;
    onPickRef.current = onPick;
    pickModeRef.current = pickMode;
  });

  const initialCenter = useRef(center);

  useEffect(() => {
    let cancelled = false;
    let instance: MapLibreMap | null = null;
    import("maplibre-gl")
      .then((ml) => {
        if (cancelled || !container.current) return;
        if (!workerConfigured) {
          ml.setWorkerUrl("/maplibre-gl-worker.mjs");
          workerConfigured = true;
        }
        lib.current = ml;
        const c = initialCenter.current;
        instance = new ml.Map({
          container: container.current,
          style: OSM_STYLE,
          center: c ? [c.lng, c.lat] : [0, 20],
          zoom: c ? 10 : 1.5,
          attributionControl: { compact: false },
          cooperativeGestures: false,
        });
        instance.addControl(new ml.NavigationControl({ showCompass: false }), "top-right");
        instance.on("click", (e) => {
          if (pickModeRef.current) onPickRef.current?.(e.lngLat.lat, e.lngLat.lng);
        });
        instance.on("load", () => !cancelled && setReady(true));
        map.current = instance;
      })
      .catch(() => !cancelled && setFailed(true));
    const markersMap = markerRefs.current;
    return () => {
      cancelled = true;
      markersMap.clear();
      instance?.remove();
      map.current = null;
    };
  }, []);

  useEffect(() => {
    const el = map.current?.getCanvas();
    if (el) el.style.cursor = pickMode ? "crosshair" : "";
  }, [pickMode, ready]);

  // markers: rebuild when the set changes (shortlists are small)
  useEffect(() => {
    const ml = lib.current;
    const m = map.current;
    if (!ml || !m || !ready) return;
    const refs = markerRefs.current;
    refs.forEach(({ marker }) => marker.remove());
    refs.clear();
    for (const mk of markers) {
      const cat = CATEGORY[mk.category];
      const el = document.createElement("button");
      el.type = "button";
      el.className = "meridian-marker";
      el.textContent = mk.label;
      el.style.setProperty("--marker", cat.color);
      el.setAttribute("aria-label", `${mk.title} (${cat.label})${mk.detail ? `. ${mk.detail}` : ""}`);
      el.addEventListener("click", (e) => {
        e.stopPropagation();
        onSelectRef.current?.(mk.id);
      });
      const marker = new ml.Marker({ element: el, anchor: "bottom" }).setLngLat([mk.lng, mk.lat]).addTo(m);
      refs.set(mk.id, { marker, el });
    }
    const ids = markers.map((mk) => mk.id).sort().join(",");
    if (markers.length > 0 && ids !== fittedFor.current) {
      fittedFor.current = ids;
      const bounds = new ml.LngLatBounds();
      markers.forEach((mk) => bounds.extend([mk.lng, mk.lat]));
      m.fitBounds(bounds, { padding: 56, maxZoom: 14, duration: 0 });
    }
  }, [markers, ready]);

  // selection: highlight the marker and show its popup
  useEffect(() => {
    const ml = lib.current;
    const m = map.current;
    if (!ml || !m || !ready) return;
    markerRefs.current.forEach(({ el }, id) => el.setAttribute("aria-pressed", String(id === selectedId)));
    popup.current?.remove();
    popup.current = null;
    const mk = markers.find((x) => x.id === selectedId);
    if (!mk) return;
    // user-entered text only ever goes in via textContent
    const body = document.createElement("div");
    const title = document.createElement("p");
    title.className = "place text-base";
    title.textContent = mk.title;
    body.appendChild(title);
    if (mk.detail) {
      const detail = document.createElement("p");
      detail.className = "text-muted-foreground mt-1 text-xs";
      detail.textContent = mk.detail;
      body.appendChild(detail);
    }
    popup.current = new ml.Popup({ offset: 30, closeButton: true, maxWidth: "260px", focusAfterOpen: false })
      .setLngLat([mk.lng, mk.lat])
      .setDOMContent(body)
      .addTo(m);
    m.easeTo({ center: [mk.lng, mk.lat], duration: 300 });
  }, [selectedId, markers, ready]);

  // the draggable pin in pick mode
  useEffect(() => {
    const ml = lib.current;
    const m = map.current;
    if (!ml || !m || !ready) return;
    if (!picked) {
      pin.current?.remove();
      pin.current = null;
      return;
    }
    if (!pin.current) {
      const el = document.createElement("div");
      el.className = "meridian-pin";
      el.setAttribute("aria-label", "Dropped pin");
      pin.current = new ml.Marker({ element: el, anchor: "center", draggable: true })
        .setLngLat([picked.lng, picked.lat])
        .addTo(m);
      pin.current.on("dragend", () => {
        const p = pin.current?.getLngLat();
        if (p) onPickRef.current?.(p.lat, p.lng);
      });
    } else {
      pin.current.setLngLat([picked.lng, picked.lat]);
    }
  }, [picked, ready]);

  return (
    <div className={cn("bg-muted relative overflow-hidden rounded-lg border", className)}>
      {/* MapLibre makes its container position: relative, so size it rather than inset it */}
      <div ref={container} role="region" aria-label={label} className="h-full w-full" />
      {!ready && !failed && (
        <div className="graticule text-muted-foreground pointer-events-none absolute inset-0 flex items-center justify-center text-sm">
          Loading map…
        </div>
      )}
      {failed && (
        <div role="alert" className="text-muted-foreground absolute inset-0 flex items-center justify-center p-6 text-center text-sm">
          The map could not load. Everything is still available in the list.
        </div>
      )}
    </div>
  );
}
