"use client";

import { useState } from "react";
import { Loader2 } from "lucide-react";

import { ChipGroup, LabeledSlider, Segmented, TagInput } from "@/components/form-controls";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { ApiError } from "@/lib/api";
import { crowdText, gemsText, weightText } from "@/lib/profile-text";
import { useUpdateProfile } from "@/lib/queries";
import type { TravelProfile, Vocabulary } from "@/lib/types";

/** Every profile field is visible and editable (spec A1). */
export function ProfileEditor({
  initial,
  vocabulary,
  onDone,
}: {
  initial: TravelProfile;
  vocabulary: Vocabulary;
  onDone: () => void;
}) {
  const [p, setP] = useState<TravelProfile>(initial);
  const update = useUpdateProfile();
  const set = <K extends keyof TravelProfile>(key: K, value: TravelProfile[K]) => setP((old) => ({ ...old, [key]: value }));
  const paces = vocabulary.paces.map((o) => ({ ...o, label: o.label.split(" — ")[0] }));

  const violations = update.error instanceof ApiError ? (update.error.problem as { violations?: string[] }).violations : undefined;

  return (
    <form
      className="space-y-8"
      onSubmit={(e) => {
        e.preventDefault();
        update.mutate(p, { onSuccess: onDone });
      }}
    >
      <section className="space-y-5">
        <h3 className="font-medium">Interests</h3>
        <div className="grid gap-x-8 gap-y-5 sm:grid-cols-2">
          {vocabulary.interests.map((i) => {
            const w = p.interests[i.id] ?? 0;
            return (
              <LabeledSlider
                key={i.id}
                label={i.label}
                min={0}
                max={100}
                step={5}
                value={Math.round(w * 100)}
                valueText={weightText(w)}
                onChange={(v) => set("interests", { ...p.interests, [i.id]: v / 100 })}
              />
            );
          })}
        </div>
      </section>

      <section className="space-y-5">
        <h3 className="font-medium">Style</h3>
        <Segmented label="Pace" options={paces} value={p.pace} onChange={(v) => set("pace", v)} />
        <div className="grid gap-5 sm:grid-cols-3">
          {(["stay", "food", "activities"] as const).map((k) => (
            <Segmented
              key={k}
              label={`${k[0].toUpperCase()}${k.slice(1)} budget`}
              options={vocabulary.budgetLevels}
              value={p.budget[k]}
              onChange={(v) => set("budget", { ...p.budget, [k]: v })}
            />
          ))}
        </div>
        <LabeledSlider
          label="Crowds"
          value={p.crowdTolerance}
          valueText={crowdText(p.crowdTolerance)}
          left="Avoid crowds"
          right="Crowds are fine"
          onChange={(v) => set("crowdTolerance", v)}
        />
        <LabeledSlider
          label="Classics ↔ hidden gems"
          value={p.hiddenGems}
          valueText={gemsText(p.hiddenGems)}
          left="Classics"
          right="Hidden gems"
          onChange={(v) => set("hiddenGems", v)}
        />
      </section>

      <section className="space-y-5">
        <h3 className="font-medium">Needs</h3>
        <ChipGroup label="Food needs" options={vocabulary.dietary} value={p.dietary} onChange={(v) => set("dietary", v)} />
        <ChipGroup
          label="Access needs"
          options={vocabulary.accessNeeds}
          value={p.accessNeeds}
          onChange={(v) => set("accessNeeds", v)}
        />
        <TagInput label="Must-haves" value={p.mustHaves} max={10} onChange={(v) => set("mustHaves", v)} />
        <TagInput label="Dealbreakers" value={p.dealbreakers} max={10} onChange={(v) => set("dealbreakers", v)} />
        <div className="grid gap-2">
          <Label htmlFor="summary">One-line summary</Label>
          <Textarea
            id="summary"
            maxLength={300}
            className="min-h-16"
            value={p.summary ?? ""}
            onChange={(e) => set("summary", e.target.value)}
          />
        </div>
      </section>

      {update.isError && (
        <div role="alert" className="text-destructive text-sm">
          {update.error.message}
          {violations && (
            <ul className="mt-1 list-disc pl-5">
              {violations.map((v) => (
                <li key={v}>{v}</li>
              ))}
            </ul>
          )}
        </div>
      )}
      <div className="flex gap-3">
        <Button type="submit" disabled={update.isPending}>
          {update.isPending && <Loader2 className="animate-spin" aria-hidden />}
          Save profile
        </Button>
        <Button type="button" variant="ghost" onClick={onDone}>
          Cancel
        </Button>
      </div>
    </form>
  );
}
