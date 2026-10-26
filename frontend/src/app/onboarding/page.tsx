"use client";

import { Suspense, useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, ArrowRight, Sparkles } from "lucide-react";

import { AiProvenance } from "@/components/ai-provenance";
import { ErrorState } from "@/components/error-state";
import { ChipGroup, LabeledSlider, Segmented, TagInput } from "@/components/form-controls";
import { JobProgress } from "@/components/job-progress";
import { ProfileSummary } from "@/components/profile/profile-summary";
import { PageLoading, RequireAuth } from "@/components/require-auth";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { crowdText, gemsText, RATING_LABELS } from "@/lib/profile-text";
import { keys, useAiJob, useAiStatus, useCancelJob, useProfile, useSubmitQuiz, useVocabulary } from "@/lib/queries";
import type { ProfileView, QuizAnswers, Vocabulary } from "@/lib/types";

const STEPS = ["Interests", "Pace & budget", "Crowds", "Needs", "Your ideal trip"] as const;

function defaultQuiz(v: Vocabulary): QuizAnswers {
  return {
    interestRatings: Object.fromEntries(v.interests.filter((i) => i.headline).map((i) => [i.id, 2])),
    pace: "balanced",
    budget: { stay: "mid", food: "mid", activities: "mid" },
    crowdTolerance: 50,
    hiddenGems: 50,
    dietary: [],
    accessNeeds: [],
    mustHaves: [],
    dealbreakers: [],
    idealTrip: "",
  };
}

export default function OnboardingPage() {
  return (
    <RequireAuth>
      <Suspense fallback={<PageLoading />}>
        <Onboarding />
      </Suspense>
    </RequireAuth>
  );
}

function Onboarding() {
  const vocabulary = useVocabulary();
  const profile = useProfile();
  if (vocabulary.isPending || profile.isPending) return <PageLoading />;
  if (vocabulary.isError) return <ErrorState error={vocabulary.error} onRetry={() => vocabulary.refetch()} />;
  if (profile.isError) return <ErrorState error={profile.error} onRetry={() => profile.refetch()} />;
  return <Quiz vocabulary={vocabulary.data} initial={profile.data?.quiz ?? defaultQuiz(vocabulary.data)} />;
}

function Quiz({ vocabulary, initial }: { vocabulary: Vocabulary; initial: QuizAnswers }) {
  const router = useRouter();
  const next = useSearchParams().get("next");
  const target = next && next.startsWith("/") && !next.startsWith("//") ? next : "/trips";
  const qc = useQueryClient();
  const [step, setStep] = useState(0);
  const [quiz, setQuiz] = useState<QuizAnswers>(initial);
  const [jobId, setJobId] = useState<string | null>(null);
  const submit = useSubmitQuiz();
  const cancel = useCancelJob();
  const job = useAiJob<ProfileView>(jobId);
  const aiStatus = useAiStatus();
  const set = <K extends keyof QuizAnswers>(k: K, v: QuizAnswers[K]) => setQuiz((q) => ({ ...q, [k]: v }));

  const headline = useMemo(() => vocabulary.interests.filter((i) => i.headline), [vocabulary]);
  const paces = vocabulary.paces;
  const status = job.data?.status;

  useEffect(() => {
    if (status === "SUCCEEDED" && job.data?.result) {
      qc.setQueryData(keys.profile, job.data.result);
      qc.invalidateQueries({ queryKey: ["trip"] });
    }
    if (status === "CANCELLED") setJobId(null);
  }, [status, job.data, qc]);

  const start = () =>
    submit.mutate(quiz, {
      onSuccess: (j) => setJobId(j.id),
    });

  // ---- generating / result
  if (jobId) {
    if (status === "SUCCEEDED" && job.data?.result) {
      const view = job.data.result;
      const labels = Object.fromEntries(vocabulary.interests.map((i) => [i.id, i.label]));
      return (
        <Card className="mx-auto my-10 max-w-3xl">
          <CardHeader>
            <CardTitle className="text-xl">Your travel style</CardTitle>
            <CardDescription>Check it over — you can edit any field now or later.</CardDescription>
            <div className="pt-2">
              <AiProvenance
                provenance={view.provenance}
                interestLabels={labels}
                onRegenerate={() => {
                  setJobId(null);
                  start();
                }}
                regenerating={submit.isPending}
              />
            </div>
          </CardHeader>
          <CardContent className="space-y-8">
            <ProfileSummary profile={view.profile} provenance={view.provenance} vocabulary={vocabulary} />
            <div className="flex flex-wrap gap-3">
              <Button onClick={() => router.push(target)}>
                Looks good <ArrowRight aria-hidden />
              </Button>
              <Button variant="outline" onClick={() => router.push(`/profile?edit=1`)}>
                Edit profile
              </Button>
            </div>
          </CardContent>
        </Card>
      );
    }
    if (status === "FAILED") {
      return (
        <div className="mx-auto my-10 max-w-xl">
          <ErrorState title="We couldn't build your profile" error={new Error(job.data?.error ?? "")} onRetry={start} />
        </div>
      );
    }
    return (
      <Card className="mx-auto my-10 max-w-xl">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-xl">
            <Sparkles className="text-signal size-5" aria-hidden /> Building your travel style
          </CardTitle>
          <CardDescription>
            {quiz.idealTrip.trim()
              ? "The AI is reading your description. Your slider answers are used exactly as given."
              : "Turning your answers into a profile."}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <JobProgress
            job={job.data}
            cancelling={cancel.isPending}
            onCancel={() => jobId && cancel.mutate(jobId, { onSuccess: () => setJobId(null) })}
          />
        </CardContent>
      </Card>
    );
  }

  // ---- quiz steps
  const last = step === STEPS.length - 1;
  return (
    <div className="mx-auto my-10 max-w-2xl">
      <ol className="relative flex justify-between" aria-label="Quiz progress">
        {/* the route: travelled part drawn solid in ink, the rest dashed */}
        <span aria-hidden className="border-contour absolute inset-x-3 top-3 border-t border-dashed" />
        <span
          aria-hidden
          className="bg-foreground absolute top-3 left-3 h-px -translate-y-px transition-[width] duration-500"
          style={{ width: `calc((100% - 1.5rem) * ${step / (STEPS.length - 1)})` }}
        />
        {STEPS.map((s, i) => {
          const done = i < step;
          const current = i === step;
          return (
            <li key={s} className="relative flex flex-col items-center" aria-current={current ? "step" : undefined}>
              <span
                className={`figures grid size-6 place-items-center rounded-full border text-xs font-semibold transition-colors ${
                  current
                    ? "border-signal bg-signal text-background"
                    : done
                      ? "border-foreground bg-foreground text-background"
                      : "border-contour bg-background text-muted-foreground"
                }`}
              >
                {i + 1}
              </span>
              <span className="sr-only">
                {s}
                {done ? " (done)" : ""}
              </span>
            </li>
          );
        })}
      </ol>
      <p className="text-muted-foreground figures mt-6 text-sm">
        Step {step + 1} of {STEPS.length}
      </p>
      <h1 className="mt-1 text-3xl font-semibold">{STEPS[step]}</h1>

      <Card className="mt-6">
        <CardContent className="space-y-7">
          {step === 0 && (
            <>
              <p className="text-muted-foreground text-sm">How much do you enjoy each of these on a trip?</p>
              {headline.map((i) => {
                const v = quiz.interestRatings[i.id] ?? 2;
                return (
                  <LabeledSlider
                    key={i.id}
                    label={i.label}
                    min={0}
                    max={4}
                    step={1}
                    value={v}
                    valueText={RATING_LABELS[v]}
                    onChange={(n) => set("interestRatings", { ...quiz.interestRatings, [i.id]: n })}
                  />
                );
              })}
            </>
          )}

          {step === 1 && (
            <>
              <Segmented label="Pace" options={paces} value={quiz.pace} onChange={(v) => set("pace", v)} />
              {(["stay", "food", "activities"] as const).map((k) => (
                <Segmented
                  key={k}
                  label={{ stay: "Where you stay", food: "Eating out", activities: "Activities & entry fees" }[k]}
                  options={vocabulary.budgetLevels}
                  value={quiz.budget[k]}
                  onChange={(v) => set("budget", { ...quiz.budget, [k]: v })}
                />
              ))}
              <p className="text-muted-foreground text-xs">
                Budget levels are private. The group only ever sees a shared range.
              </p>
            </>
          )}

          {step === 2 && (
            <>
              <LabeledSlider
                label="How do you feel about crowds?"
                value={quiz.crowdTolerance}
                valueText={crowdText(quiz.crowdTolerance)}
                left="Avoid crowds"
                right="Crowds are fine"
                onChange={(v) => set("crowdTolerance", v)}
              />
              <LabeledSlider
                label="Famous sights or hidden gems?"
                value={quiz.hiddenGems}
                valueText={gemsText(quiz.hiddenGems)}
                left="The classics"
                right="Hidden gems"
                onChange={(v) => set("hiddenGems", v)}
              />
            </>
          )}

          {step === 3 && (
            <>
              <ChipGroup label="Food needs" options={vocabulary.dietary} value={quiz.dietary} onChange={(v) => set("dietary", v)} />
              <ChipGroup
                label="Access needs"
                options={vocabulary.accessNeeds}
                value={quiz.accessNeeds}
                onChange={(v) => set("accessNeeds", v)}
              />
              <TagInput
                label="Must-haves"
                placeholder="e.g. sea view, a pool"
                hint="Press Enter to add each one."
                value={quiz.mustHaves}
                onChange={(v) => set("mustHaves", v)}
              />
              <TagInput
                label="Dealbreakers"
                placeholder="e.g. long bus rides"
                value={quiz.dealbreakers}
                onChange={(v) => set("dealbreakers", v)}
              />
            </>
          )}

          {step === 4 && (
            <div className="grid gap-2">
              <Label htmlFor="ideal-trip">Describe your ideal trip in your own words</Label>
              <Textarea
                id="ideal-trip"
                className="min-h-36"
                maxLength={vocabulary.limits.idealTrip}
                placeholder="e.g. Slow mornings, a long lunch where locals eat, then a sunset somewhere quiet. I'd love a pottery or cooking class…"
                value={quiz.idealTrip}
                onChange={(e) => set("idealTrip", e.target.value)}
                aria-describedby="ideal-trip-hint"
              />
              <p id="ideal-trip-hint" className="text-muted-foreground flex justify-between gap-4 text-xs">
                <span>
                  {aiStatus.data?.enabled && aiStatus.data.reachable
                    ? `${aiStatus.data.model} will read this to pick up extra interests. It only uses tags from a fixed list and quotes your words as evidence.`
                    : "AI is offline right now — keyword rules will read this instead."}
                </span>
                <span className="tabular-nums">
                  {quiz.idealTrip.length}/{vocabulary.limits.idealTrip}
                </span>
              </p>
            </div>
          )}
        </CardContent>
      </Card>

      {submit.isError && (
        <p role="alert" className="text-destructive mt-4 text-sm">
          {submit.error.message}
        </p>
      )}

      <div className="mt-6 flex justify-between">
        <Button variant="ghost" onClick={() => setStep((s) => s - 1)} disabled={step === 0}>
          <ArrowLeft aria-hidden /> Back
        </Button>
        {last ? (
          <Button onClick={start} disabled={submit.isPending}>
            <Sparkles aria-hidden /> Build my profile
          </Button>
        ) : (
          <Button onClick={() => setStep((s) => s + 1)}>
            Next <ArrowRight aria-hidden />
          </Button>
        )}
      </div>
    </div>
  );
}
