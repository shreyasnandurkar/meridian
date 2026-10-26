"use client";

import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { usePropose } from "@/lib/queries";
import type { Itinerary, PlanEdit, PlanEditType } from "@/lib/types";

/** What the member is proposing a change to: a stop on the plan, or a shortlisted place that isn't on it. */
export interface ProposeTarget {
  shortlistItemId: string;
  title: string;
  /** Day the place is on now; null when it isn't scheduled. */
  day: number | null;
}

const TYPE_LABELS: Record<PlanEditType, string> = {
  MOVE: "Move it to another day",
  SWAP: "Swap it with another stop",
  REMOVE: "Take it off the plan",
  ADD: "Add it to a day",
};

/** Members propose edits; the organizer applies them directly. Votes on proposals arrive in M6. */
export function ProposeDialog({
  tripId,
  itinerary,
  target,
  onOpenChange,
}: {
  tripId: string;
  itinerary: Itinerary;
  target: ProposeTarget | null;
  onOpenChange: (open: boolean) => void;
}) {
  const propose = usePropose(tripId);
  const scheduled = target?.day != null;
  const types: PlanEditType[] = scheduled ? ["MOVE", "SWAP", "REMOVE"] : ["ADD"];
  const [type, setType] = useState<PlanEditType>("MOVE");
  const [day, setDay] = useState(1);
  const [other, setOther] = useState("");
  const [reason, setReason] = useState("");

  const others = itinerary.days.flatMap((d) =>
    d.stops
      .filter((s) => s.shortlistItemId !== target?.shortlistItemId)
      .map((s) => ({ id: s.shortlistItemId, label: `${s.title} (day ${d.index})` })),
  );

  useEffect(() => {
    if (!target) return;
    setType(target.day != null ? "MOVE" : "ADD");
    setDay(target.day != null ? (target.day % itinerary.days.length) + 1 : 1);
    setOther("");
    setReason("");
    propose.reset();
    // reset only when a new target opens
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [target]);

  if (!target) return null;

  const edit: PlanEdit = { type, itemId: target.shortlistItemId };
  if (type === "MOVE" || type === "ADD") edit.day = day;
  if (type === "SWAP") edit.otherItemId = other;
  const ready = type !== "SWAP" || other !== "";

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!ready) return;
    propose.mutate({ edit, reason: reason.trim() || null }, { onSuccess: () => onOpenChange(false) });
  };

  return (
    <Dialog open onOpenChange={onOpenChange}>
      <DialogContent>
        <form onSubmit={submit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>Propose a change</DialogTitle>
            <DialogDescription>
              To <span className="text-foreground font-medium">{target.title}</span>. The group sees it on the
              Itinerary tab; times are recomputed by code when it is applied.
            </DialogDescription>
          </DialogHeader>

          {types.length > 1 && (
            <fieldset className="space-y-2">
              <legend className="text-sm font-medium">Change</legend>
              {types.map((t) => (
                <label key={t} className="flex items-center gap-2 text-sm">
                  <input
                    type="radio"
                    name="proposal-type"
                    value={t}
                    checked={type === t}
                    onChange={() => setType(t)}
                    className="accent-signal"
                  />
                  {TYPE_LABELS[t]}
                </label>
              ))}
            </fieldset>
          )}

          {(type === "MOVE" || type === "ADD") && (
            <div className="space-y-1.5">
              <Label htmlFor="proposal-day">Day</Label>
              <NativeSelect id="proposal-day" value={day} onChange={(e) => setDay(Number(e.target.value))}>
                {itinerary.days.map((d) => (
                  <option key={d.index} value={d.index} disabled={d.index === target.day}>
                    Day {d.index}
                    {d.index === target.day ? " (now)" : ""}
                  </option>
                ))}
              </NativeSelect>
            </div>
          )}

          {type === "SWAP" && (
            <div className="space-y-1.5">
              <Label htmlFor="proposal-other">Swap with</Label>
              <NativeSelect id="proposal-other" value={other} onChange={(e) => setOther(e.target.value)}>
                <option value="">Choose a stop</option>
                {others.map((o) => (
                  <option key={o.id} value={o.id}>
                    {o.label}
                  </option>
                ))}
              </NativeSelect>
            </div>
          )}

          <div className="space-y-1.5">
            <Label htmlFor="proposal-reason">Why (optional)</Label>
            <Textarea
              id="proposal-reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              maxLength={500}
              rows={2}
              placeholder="e.g. Too crowded on a weekend"
            />
          </div>

          {propose.error && (
            <p role="alert" className="text-destructive text-sm">
              {propose.error.message}
            </p>
          )}

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={!ready || propose.isPending}>
              Propose
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
