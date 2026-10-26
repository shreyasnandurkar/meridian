"use client";

import Link from "next/link";
import { Sparkles } from "lucide-react";

import { MemberList } from "@/components/trip/member-list";
import { PreferencesCard } from "@/components/trip/preferences-card";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import { useAuth } from "@/lib/auth";
import { formatDateRange, formatMoney } from "@/lib/format";
import type { TripDetail } from "@/lib/types";

export function OverviewTab({ trip }: { trip: TripDetail }) {
  const { user } = useAuth();
  const contributed = trip.members.filter((m) => m.hasProfile).length;
  const me = trip.members.find((m) => m.userId === user?.id);

  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_22rem]">
      <div className="space-y-6">
        {me && !me.hasProfile && (
          <div className="bg-accent/70 text-accent-foreground border-accent-foreground/15 flex flex-wrap items-center gap-3 rounded-lg border px-4 py-3 text-sm">
            <Sparkles className="size-4" aria-hidden />
            <span className="flex-1">Share your travel style so the group plan fits you too.</span>
            <Button size="sm" variant="outline" asChild>
              <Link href={`/onboarding?next=${encodeURIComponent(`/trips/${trip.id}`)}`}>Take the quiz</Link>
            </Button>
          </div>
        )}

        <Card>
          <CardHeader>
            <CardTitle>Who has contributed</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div>
              <div className="mb-2 flex justify-between text-sm">
                <span>
                  {contributed} of {trip.members.length} shared their travel style
                </span>
                <span className="text-muted-foreground tabular-nums">
                  {Math.round((contributed / Math.max(1, trip.members.length)) * 100)}%
                </span>
              </div>
              <Progress value={(contributed / Math.max(1, trip.members.length)) * 100} aria-label="Members who shared preferences" />
            </div>
            <MemberList members={trip.members} currentUserId={user?.id} />
          </CardContent>
        </Card>
      </div>

      <div className="space-y-6">
        <Card>
          <CardHeader>
            <CardTitle>Trip details</CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="grid gap-3 text-sm">
              <Row label="Destination" value={trip.destinationName} />
              <Row label="Dates" value={`${formatDateRange(trip.startDate, trip.endDate)} (${trip.days} days)`} />
              <Row label="Rough budget per person" value={formatMoney(trip.budgetTotal, trip.currency)} />
              <Row label="Decisions" value={trip.quorum.description} />
            </dl>
          </CardContent>
        </Card>
        <PreferencesCard tripId={trip.id} />
      </div>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-muted-foreground text-xs">{label}</dt>
      <dd className="mt-0.5">{value}</dd>
    </div>
  );
}
