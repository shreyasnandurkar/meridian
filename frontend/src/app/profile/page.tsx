"use client";

import { Suspense, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Pencil, UserRound } from "lucide-react";

import { AiProvenance } from "@/components/ai-provenance";
import { ErrorState } from "@/components/error-state";
import { ProfileEditor } from "@/components/profile/profile-editor";
import { ProfileSummary } from "@/components/profile/profile-summary";
import { PageLoading, RequireAuth } from "@/components/require-auth";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useProfile, useVocabulary } from "@/lib/queries";

export default function ProfilePage() {
  return (
    <RequireAuth>
      <Suspense fallback={<PageLoading />}>
        <ProfileContent />
      </Suspense>
    </RequireAuth>
  );
}

function ProfileContent() {
  const profile = useProfile();
  const vocabulary = useVocabulary();
  const [editing, setEditing] = useState(useSearchParams().get("edit") === "1");

  if (profile.isPending || vocabulary.isPending) return <PageLoading />;
  if (profile.isError) return <ErrorState error={profile.error} onRetry={() => profile.refetch()} />;
  if (vocabulary.isError) return <ErrorState error={vocabulary.error} onRetry={() => vocabulary.refetch()} />;

  if (!profile.data) {
    return (
      <div className="mx-auto my-16 flex max-w-md flex-col items-center rounded-xl border border-dashed px-6 py-14 text-center">
        <UserRound className="text-muted-foreground size-8" aria-hidden />
        <h1 className="mt-4 font-medium">No travel profile yet</h1>
        <p className="text-muted-foreground mt-1 text-sm">
          A two-minute quiz tells your group what kind of traveler you are.
        </p>
        <Button className="mt-6" asChild>
          <Link href="/onboarding">Take the quiz</Link>
        </Button>
      </div>
    );
  }

  const view = profile.data;
  const labels = Object.fromEntries(vocabulary.data.interests.map((i) => [i.id, i.label]));
  return (
    <div className="mx-auto my-10 max-w-3xl">
      <Card>
        <CardHeader className="gap-3">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <CardTitle className="text-xl">Your travel style</CardTitle>
            {!editing && (
              <div className="flex gap-2">
                <Button variant="outline" size="sm" asChild>
                  <Link href="/onboarding">Retake quiz</Link>
                </Button>
                <Button size="sm" onClick={() => setEditing(true)}>
                  <Pencil aria-hidden /> Edit
                </Button>
              </div>
            )}
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <AiProvenance provenance={view.provenance} interestLabels={labels} />
            {view.edited && <Badge variant="outline">Edited by you</Badge>}
          </div>
        </CardHeader>
        <CardContent>
          {editing ? (
            <ProfileEditor initial={view.profile} vocabulary={vocabulary.data} onDone={() => setEditing(false)} />
          ) : (
            <ProfileSummary profile={view.profile} provenance={view.provenance} vocabulary={vocabulary.data} />
          )}
        </CardContent>
      </Card>
      <p className="text-muted-foreground mt-4 text-xs">
        Your profile is reused across all your trips. Budget levels stay private.
      </p>
    </div>
  );
}
