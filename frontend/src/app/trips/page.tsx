"use client";

import Link from "next/link";
import { CalendarDays, Crown, Map as MapIcon, Plus, Sparkles, Users } from "lucide-react";

import { ErrorState } from "@/components/error-state";
import { PageLoading, RequireAuth } from "@/components/require-auth";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { formatDateRange } from "@/lib/format";
import { useProfile, useTrips } from "@/lib/queries";

export default function TripsPage() {
  return (
    <RequireAuth>
      <TripsContent />
    </RequireAuth>
  );
}

function TripsContent() {
  const trips = useTrips();
  const profile = useProfile();

  return (
    <div className="py-10">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-3xl font-semibold">My trips</h1>
        <Button asChild>
          <Link href="/trips/new">
            <Plus aria-hidden /> New trip
          </Link>
        </Button>
      </div>

      {profile.data === null && (
        <div className="bg-accent/70 text-accent-foreground border-accent-foreground/15 mt-6 flex flex-wrap items-center gap-3 rounded-lg border px-4 py-3 text-sm">
          <Sparkles className="size-4" aria-hidden />
          <span className="flex-1">Take the travel-style quiz so your group knows what you enjoy.</span>
          <Button size="sm" variant="outline" asChild>
            <Link href="/onboarding">Take the quiz</Link>
          </Button>
        </div>
      )}

      <div className="mt-6">
        {trips.isPending ? (
          <PageLoading label="Loading trips…" />
        ) : trips.isError ? (
          <ErrorState title="Couldn't load your trips" error={trips.error} onRetry={() => trips.refetch()} />
        ) : trips.data.length === 0 ? (
          <div className="flex flex-col items-center rounded-xl border border-dashed px-6 py-16 text-center">
            <MapIcon className="text-muted-foreground size-8" aria-hidden />
            <h2 className="mt-4 font-medium">No trips yet</h2>
            <p className="text-muted-foreground mt-1 max-w-sm text-sm">
              Create a trip and share the invite link, or open an invite link someone sent you.
            </p>
            <Button className="mt-6" asChild>
              <Link href="/trips/new">Create your first trip</Link>
            </Button>
          </div>
        ) : (
          <ul className="bg-card divide-y overflow-hidden rounded-lg border">
            {trips.data.map((t) => (
              <li key={t.id}>
                <Link
                  href={`/trips/${t.id}`}
                  className="group hover:bg-muted/60 focus-visible:ring-ring/50 grid gap-x-6 gap-y-1 px-5 py-4 transition-colors outline-none focus-visible:ring-[3px] focus-visible:ring-inset sm:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)_auto] sm:items-center"
                >
                  <div className="min-w-0">
                    <h2 className="place truncate text-xl leading-tight">{t.destinationName}</h2>
                    <p className="mt-0.5 flex items-center gap-2 truncate text-sm font-medium">
                      {t.name}
                      {t.myRole === "ORGANIZER" && (
                        <Badge variant="secondary">
                          <Crown aria-hidden /> Organizer
                        </Badge>
                      )}
                    </p>
                  </div>
                  <span className="text-muted-foreground figures inline-flex items-center gap-1.5 text-sm">
                    <CalendarDays className="size-3.5" aria-hidden />
                    {formatDateRange(t.startDate, t.endDate)}
                  </span>
                  <span className="text-muted-foreground figures inline-flex items-center gap-1.5 text-sm">
                    <Users className="size-3.5" aria-hidden />
                    {t.memberCount} {t.memberCount === 1 ? "member" : "members"}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
