"use client";

import { useState } from "react";
import { Check, EyeOff, Eye, Loader2 } from "lucide-react";

import { TagInput } from "@/components/form-controls";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { formatMoney } from "@/lib/format";
import { useTripPreferences, useUpdateTripPreferences } from "@/lib/queries";
import type { TripPreferences } from "@/lib/types";

/** Per-trip overrides of budget and must-haves. The budget stays private unless the member opts in. */
export function PreferencesCard({ tripId }: { tripId: string }) {
  const prefs = useTripPreferences(tripId);
  return (
    <Card>
      <CardHeader>
        <CardTitle>Your preferences for this trip</CardTitle>
        <CardDescription>Overrides your profile for this trip only.</CardDescription>
      </CardHeader>
      <CardContent>
        {prefs.isPending ? (
          <p className="text-muted-foreground text-sm">Loading…</p>
        ) : prefs.isError ? (
          <p role="alert" className="text-destructive text-sm">
            {prefs.error.message}
          </p>
        ) : (
          <PreferencesForm tripId={tripId} initial={prefs.data} />
        )}
      </CardContent>
    </Card>
  );
}

function PreferencesForm({ tripId, initial }: { tripId: string; initial: TripPreferences }) {
  const update = useUpdateTripPreferences(tripId);
  const [min, setMin] = useState(initial.budgetMin?.toString() ?? "");
  const [max, setMax] = useState(initial.budgetMax?.toString() ?? "");
  const [mustHaves, setMustHaves] = useState(initial.mustHaves);
  const [visible, setVisible] = useState(initial.budgetVisible);
  const [saved, setSaved] = useState(false);

  return (
    <form
      className="grid gap-4"
      onSubmit={(e) => {
        e.preventDefault();
        setSaved(false);
        update.mutate(
          {
            budgetMin: min ? Number(min) : null,
            budgetMax: max ? Number(max) : null,
            mustHaves,
            budgetVisible: visible,
          },
          { onSuccess: () => setSaved(true) },
        );
      }}
    >
      <fieldset className="grid gap-2">
        <legend className="mb-2 text-sm font-medium">Your budget ({initial.currency}, per person)</legend>
        <div className="grid grid-cols-2 gap-3">
          <div className="grid gap-1.5">
            <Label htmlFor="bmin" className="text-muted-foreground text-xs">
              Minimum
            </Label>
            <Input id="bmin" type="number" min={0} step={500} value={min} onChange={(e) => setMin(e.target.value)} />
          </div>
          <div className="grid gap-1.5">
            <Label htmlFor="bmax" className="text-muted-foreground text-xs">
              Maximum
            </Label>
            <Input id="bmax" type="number" min={0} step={500} value={max} onChange={(e) => setMax(e.target.value)} />
          </div>
        </div>
        <label className="mt-1 flex items-start gap-2 text-sm">
          <input
            type="checkbox"
            className="accent-primary mt-0.5 size-4"
            checked={visible}
            onChange={(e) => setVisible(e.target.checked)}
          />
          <span>
            Show my exact budget to the group
            <span className="text-muted-foreground flex items-center gap-1 text-xs">
              {visible ? <Eye className="size-3" aria-hidden /> : <EyeOff className="size-3" aria-hidden />}
              {visible ? "Members will see your numbers." : "Private — the group only sees a shared range."}
            </span>
          </span>
        </label>
        {min && max && Number(min) <= Number(max) && (
          <p className="text-muted-foreground text-xs">
            {formatMoney(Number(min), initial.currency)} – {formatMoney(Number(max), initial.currency)}
          </p>
        )}
      </fieldset>
      <TagInput label="Must-haves for this trip" max={10} value={mustHaves} onChange={setMustHaves} />
      {update.isError && (
        <p role="alert" className="text-destructive text-sm">
          {update.error.message}
        </p>
      )}
      <Button type="submit" variant="outline" disabled={update.isPending}>
        {update.isPending ? <Loader2 className="animate-spin" aria-hidden /> : saved ? <Check aria-hidden /> : null}
        {saved ? "Saved" : "Save preferences"}
      </Button>
    </form>
  );
}
