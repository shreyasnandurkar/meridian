"use client";

import { useState } from "react";
import { Ban, Heart, MessageSquarePlus, ThumbsUp, type LucideIcon } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import type { ReactionValue, ShortlistReaction } from "@/lib/types";
import { cn } from "@/lib/utils";

const REACTIONS: {
  value: ReactionValue;
  label: string;
  verb: string;
  myVerb: string;
  icon: LucideIcon;
  on: string;
}[] = [
  { value: "LOVE", label: "Love", verb: "loves it", myVerb: "love it", icon: Heart, on: "border-signal/50 bg-signal/10 text-signal" },
  { value: "OK", label: "OK", verb: "is OK with it", myVerb: "are OK with it", icon: ThumbsUp, on: "border-success/50 bg-success/10 text-success" },
  {
    value: "NO_WAY",
    label: "No way",
    verb: "says no way",
    myVerb: "say no way",
    icon: Ban,
    on: "border-destructive/50 bg-destructive/10 text-destructive",
  },
];

/** Love / OK / No way, one per member, with who reacted and their optional reasons. */
export function ReactionBar({
  reactions,
  myId,
  itemTitle,
  onReact,
}: {
  reactions: ShortlistReaction[];
  myId: string;
  itemTitle: string;
  onReact: (value: ReactionValue | null, reason?: string) => void;
}) {
  const mine = reactions.find((r) => r.userId === myId);

  return (
    <div className="space-y-2">
      <div className="flex flex-wrap items-center gap-1.5" role="group" aria-label={`Your reaction to ${itemTitle}`}>
        {REACTIONS.map(({ value, label, icon: Icon, on }) => {
          const who = reactions.filter((r) => r.value === value);
          const active = mine?.value === value;
          return (
            <button
              key={value}
              type="button"
              aria-pressed={active}
              title={who.length ? who.map((r) => r.displayName).join(", ") : undefined}
              onClick={() => onReact(active ? null : value, active ? undefined : (mine?.reason ?? undefined))}
              className={cn(
                "focus-visible:ring-ring/50 inline-flex h-8 items-center gap-1.5 rounded-full border px-3 text-xs font-medium transition-colors outline-none focus-visible:ring-[3px]",
                active ? on : "text-muted-foreground hover:bg-muted hover:text-foreground",
              )}
            >
              <Icon className={cn("size-3.5", active && value === "LOVE" && "fill-current")} aria-hidden />
              {label}
              {who.length > 0 && (
                <span className="figures" aria-label={`${who.length} ${who.length === 1 ? "person" : "people"}`}>
                  {who.length}
                </span>
              )}
            </button>
          );
        })}
        {mine && <ReasonButton current={mine.reason} onSave={(reason) => onReact(mine.value, reason)} />}
      </div>
      {reactions.length > 0 && (
        <ul className="text-muted-foreground space-y-0.5 text-xs">
          {reactions.map((r) => {
            const meta = REACTIONS.find((x) => x.value === r.value)!;
            return (
              <li key={r.userId}>
                <span className="text-foreground font-medium">{r.userId === myId ? "You" : r.displayName}</span>{" "}
                {r.userId === myId ? meta.myVerb : meta.verb}
                {r.reason && <>: &ldquo;{r.reason}&rdquo;</>}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

function ReasonButton({ current, onSave }: { current: string | null; onSave: (reason: string) => void }) {
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState(current ?? "");
  return (
    <Popover
      open={open}
      onOpenChange={(o) => {
        setOpen(o);
        if (o) setDraft(current ?? "");
      }}
    >
      <PopoverTrigger asChild>
        <Button variant="ghost" size="sm" className="text-muted-foreground h-8 text-xs">
          <MessageSquarePlus className="size-3.5" aria-hidden /> {current ? "Edit reason" : "Add a reason"}
        </Button>
      </PopoverTrigger>
      <PopoverContent align="start" className="w-72">
        <form
          className="space-y-3"
          onSubmit={(e) => {
            e.preventDefault();
            onSave(draft.trim());
            setOpen(false);
          }}
        >
          <label className="block text-sm font-medium" htmlFor="reaction-reason">
            Why? <span className="text-muted-foreground font-normal">(optional)</span>
          </label>
          <Input
            id="reaction-reason"
            autoFocus
            maxLength={300}
            value={draft}
            placeholder="e.g. too far from the beach"
            onChange={(e) => setDraft(e.target.value)}
          />
          <div className="flex justify-end">
            <Button size="sm" type="submit">
              Save
            </Button>
          </div>
        </form>
      </PopoverContent>
    </Popover>
  );
}
