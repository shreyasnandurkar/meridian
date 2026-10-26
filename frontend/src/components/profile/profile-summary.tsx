"use client";

import { Sparkles } from "lucide-react";

import type { Provenance, TravelProfile, Vocabulary } from "@/lib/types";
import { crowdText, gemsText, weightText } from "@/lib/profile-text";
import { cn } from "@/lib/utils";

function labelOf(options: { id: string; label: string }[], id: string) {
  return options.find((o) => o.id === id)?.label ?? id;
}

/** Read-only structured profile. Interests inferred from free text are marked so the user can see what AI added. */
export function ProfileSummary({
  profile,
  provenance,
  vocabulary,
}: {
  profile: TravelProfile;
  provenance: Provenance;
  vocabulary: Vocabulary;
}) {
  const inferred = new Set(
    provenance.inferences.filter((i) => i.field.startsWith("interests.")).map((i) => i.field.slice(10)),
  );
  const interests = Object.entries(profile.interests)
    .filter(([, w]) => w > 0)
    .sort((a, b) => b[1] - a[1]);

  return (
    <div className="space-y-6">
      {profile.summary && <p className="text-lg text-pretty">{profile.summary}</p>}

      <section aria-labelledby="interests-h">
        <h3 id="interests-h" className="mb-3 text-sm font-semibold">
          Interests
        </h3>
        {interests.length === 0 ? (
          <p className="text-muted-foreground text-sm">No strong interests yet.</p>
        ) : (
          <ul className="grid gap-2 sm:grid-cols-2">
            {interests.map(([tag, w]) => (
              <li key={tag} className="flex items-center gap-3">
                <span className="w-36 shrink-0 truncate text-sm">
                  {labelOf(vocabulary.interests, tag)}
                  {inferred.has(tag) && (
                    <Sparkles className="text-signal ml-1 inline size-3 align-[-1px]" aria-label="inferred by AI" />
                  )}
                </span>
                <span className="bg-muted relative h-2 flex-1 overflow-hidden rounded-full" aria-hidden>
                  <span
                    className={cn("absolute inset-y-0 left-0 rounded-full", inferred.has(tag) ? "bg-primary/60" : "bg-primary")}
                    style={{ width: `${Math.round(w * 100)}%` }}
                  />
                </span>
                <span className="text-muted-foreground w-20 text-right text-xs">{weightText(w)}</span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <dl className="grid gap-x-6 gap-y-4 text-sm sm:grid-cols-2">
        <Fact label="Pace" value={labelOf(vocabulary.paces, profile.pace).split(" — ")[0]} />
        <Fact label="Crowds" value={crowdText(profile.crowdTolerance)} />
        <Fact label="Classics ↔ hidden gems" value={gemsText(profile.hiddenGems)} />
        <Fact
          label="Budget (stay · food · activities)"
          value={[profile.budget.stay, profile.budget.food, profile.budget.activities]
            .map((b) => labelOf(vocabulary.budgetLevels, b))
            .join(" · ")}
        />
        <Fact
          label="Food needs"
          value={profile.dietary.map((d) => labelOf(vocabulary.dietary, d)).join(", ") || "None"}
        />
        <Fact
          label="Access needs"
          value={profile.accessNeeds.map((d) => labelOf(vocabulary.accessNeeds, d)).join(", ") || "None"}
        />
        <Fact label="Must-haves" value={profile.mustHaves.join(", ") || "None"} />
        <Fact label="Dealbreakers" value={profile.dealbreakers.join(", ") || "None"} />
      </dl>
    </div>
  );
}

function Fact({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-muted-foreground text-xs">{label}</dt>
      <dd className="mt-0.5">{value}</dd>
    </div>
  );
}
