"use client";

import { forwardRef } from "react";
import { ExternalLink, MapPin, Pencil } from "lucide-react";

import { ReactionBar } from "@/components/shortlist/reaction-bar";
import { Button } from "@/components/ui/button";
import { distanceKm, formatDistance, formatRelative, initials } from "@/lib/format";
import { CATEGORY, tagHighlights } from "@/lib/places";
import type { ReactionValue, ShortlistItem } from "@/lib/types";
import { cn } from "@/lib/utils";

/** One shortlist entry: what and where it is, who added it and why, its source, and the group's reactions. */
export const ShortlistItemCard = forwardRef<
  HTMLLIElement,
  {
    item: ShortlistItem;
    origin: { lat: number; lng: number } | null;
    myId: string;
    selected?: boolean;
    onSelect?: () => void;
    onEdit: () => void;
    onReact: (value: ReactionValue | null, reason?: string) => void;
  }
>(function ShortlistItemCard({ item, origin, myId, selected, onSelect, onEdit, onReact }, ref) {
  const cat = CATEGORY[item.category];
  const Icon = cat.icon;
  const distance =
    origin && item.lat != null && item.lng != null ? distanceKm(origin.lat, origin.lng, item.lat, item.lng) : null;
  const highlights = item.place ? tagHighlights(item.place.tags) : [];
  const where = item.place?.address ?? (item.kind === "LINK" ? item.host : null);
  const addedByMe = item.addedBy.userId === myId;

  return (
    <li
      ref={ref}
      aria-current={selected || undefined}
      className={cn(
        "bg-card scroll-mt-24 rounded-lg border p-4 transition-shadow sm:p-5",
        selected && "ring-signal/70 ring-2",
      )}
    >
      <div className="flex items-start gap-3">
        <span
          aria-hidden
          className="bg-muted flex size-9 shrink-0 items-center justify-center rounded-full"
          style={{ color: cat.color }}
        >
          <Icon className="size-4" />
        </span>
        <div className="min-w-0 flex-1">
          <div className="flex items-start justify-between gap-2">
            <h3 className="min-w-0">
              {onSelect && item.lat != null ? (
                <button
                  type="button"
                  onClick={onSelect}
                  className="place hover:decoration-signal focus-visible:ring-ring/50 rounded-sm text-left text-lg leading-snug underline decoration-transparent underline-offset-4 outline-none focus-visible:ring-2"
                >
                  {item.title}
                </button>
              ) : (
                <span className="place text-lg leading-snug">{item.title}</span>
              )}
            </h3>
            {item.canEdit && (
              <Button variant="ghost" size="sm" className="text-muted-foreground -mt-1 -mr-2 shrink-0" onClick={onEdit}>
                <Pencil aria-hidden /> Edit<span className="sr-only"> {item.title}</span>
              </Button>
            )}
          </div>
          <p className="text-muted-foreground figures mt-0.5 flex flex-wrap gap-x-2 text-xs">
            <span className={cn("font-medium", cat.chip)}>{cat.label}</span>
            {distance != null && <span>· {formatDistance(distance)} from centre</span>}
            {where && <span className="truncate">· {where}</span>}
          </p>
          {highlights.length > 0 && (
            <ul className="mt-2 flex flex-wrap gap-1.5" aria-label="Highlights">
              {highlights.map((h) => (
                <li key={h} className="bg-secondary rounded-full px-2 py-0.5 text-xs">
                  {h}
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>

      <figure className="mt-3 flex items-start gap-2 text-sm">
        <span
          aria-hidden
          className="bg-secondary text-secondary-foreground mt-0.5 flex size-6 shrink-0 items-center justify-center rounded-full text-[0.65rem] font-semibold"
        >
          {initials(item.addedBy.displayName)}
        </span>
        <figcaption className="min-w-0">
          <span className="font-medium">{addedByMe ? "You" : item.addedBy.displayName}</span>
          <span className="text-muted-foreground"> added this {formatRelative(item.createdAt)}</span>
          {item.reason ? (
            <blockquote className="border-contour mt-1 border-l-2 pl-3 italic">{item.reason}</blockquote>
          ) : (
            <p className="text-muted-foreground mt-0.5 text-xs">No reason given.</p>
          )}
        </figcaption>
      </figure>

      {item.notes && (
        <p className="bg-muted/60 mt-3 rounded-md px-3 py-2 text-sm whitespace-pre-line">
          <span className="sr-only">Notes: </span>
          {item.notes}
        </p>
      )}

      <div className="mt-4 flex flex-wrap items-end justify-between gap-3">
        <ReactionBar reactions={item.reactions} myId={myId} itemTitle={item.title} onReact={onReact} />
        <SourceLine item={item} />
      </div>
    </li>
  );
});

/** Where this came from and when it was checked (spec §1: every option shows source + checked-at time). */
function SourceLine({ item }: { item: ShortlistItem }) {
  const linkClass =
    "text-muted-foreground hover:text-foreground inline-flex items-center gap-1 text-xs underline decoration-contour underline-offset-2";
  if (item.place) {
    return (
      <p className="text-muted-foreground text-xs">
        {item.place.sourceUrl ? (
          <a href={item.place.sourceUrl} target="_blank" rel="noopener noreferrer" className={linkClass}>
            OpenStreetMap <ExternalLink className="size-3" aria-hidden />
            <span className="sr-only">(opens in a new tab)</span>
          </a>
        ) : (
          item.place.source
        )}{" "}
        · checked {formatRelative(item.place.fetchedAt)}
      </p>
    );
  }
  if (item.url) {
    return (
      <a href={item.url} target="_blank" rel="noopener noreferrer nofollow" className={linkClass}>
        Open on {item.host} <ExternalLink className="size-3" aria-hidden />
        <span className="sr-only">(opens in a new tab)</span>
      </a>
    );
  }
  return (
    <p className="text-muted-foreground figures inline-flex items-center gap-1 text-xs">
      <MapPin className="size-3" aria-hidden /> Pinned by hand · {item.lat?.toFixed(4)}, {item.lng?.toFixed(4)}
    </p>
  );
}
