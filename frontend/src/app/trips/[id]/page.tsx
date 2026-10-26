"use client";

import { Suspense } from "react";
import Link from "next/link";
import { useParams, usePathname, useRouter, useSearchParams } from "next/navigation";
import {
  ArrowLeft,
  CalendarDays,
  ClipboardList,
  GitCompareArrows,
  LayoutDashboard,
  ListChecks,
  MapPin,
  Route,
  Scale,
  Users,
} from "lucide-react";

import { ErrorState } from "@/components/error-state";
import { PageLoading, RequireAuth } from "@/components/require-auth";
import { MembersTab } from "@/components/trip/members-tab";
import { OverviewTab } from "@/components/trip/overview-tab";
import { PlaceholderTab } from "@/components/trip/placeholder-tab";
import { ShortlistTab } from "@/components/shortlist/shortlist-tab";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ApiError } from "@/lib/api";
import { formatCoords, formatDateRange } from "@/lib/format";
import { useTrip } from "@/lib/queries";

const TABS = [
  { id: "overview", label: "Overview", icon: LayoutDashboard },
  { id: "shortlist", label: "Shortlist", icon: ListChecks },
  { id: "group-fit", label: "Group fit", icon: Scale },
  { id: "compare", label: "Compare", icon: GitCompareArrows },
  { id: "itinerary", label: "Itinerary", icon: Route },
  { id: "decisions", label: "Decisions", icon: ClipboardList },
  { id: "members", label: "Members", icon: Users },
] as const;

type TabId = (typeof TABS)[number]["id"];

export default function TripHubPage() {
  return (
    <RequireAuth>
      <Suspense fallback={<PageLoading />}>
        <TripHub />
      </Suspense>
    </RequireAuth>
  );
}

function TripHub() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const pathname = usePathname();
  const requested = useSearchParams().get("tab");
  const tab: TabId = TABS.some((t) => t.id === requested) ? (requested as TabId) : "overview";
  const trip = useTrip(id);

  if (trip.isPending) return <PageLoading label="Loading trip…" />;
  if (trip.isError) {
    if (trip.error instanceof ApiError && trip.error.status === 404) {
      return (
        <div className="mx-auto my-16 max-w-md text-center">
          <h1 className="font-medium">Trip not found</h1>
          <p className="text-muted-foreground mt-1 text-sm">
            It may not exist, or you&apos;re not a member yet. Ask the organizer for an invite link.
          </p>
          <Button className="mt-6" variant="outline" asChild>
            <Link href="/trips">Back to my trips</Link>
          </Button>
        </div>
      );
    }
    return <ErrorState error={trip.error} onRetry={() => trip.refetch()} />;
  }

  const t = trip.data;
  return (
    <div className="py-8">
      <header>
        <Link href="/trips" className="text-muted-foreground hover:text-foreground text-sm">
          <ArrowLeft className="mr-1 inline size-3.5 align-[-2px]" aria-hidden />
          My trips
        </Link>
        <h1 className="mt-4 text-3xl font-semibold">{t.name}</h1>
        <p className="mt-1 flex flex-wrap items-baseline gap-x-3 gap-y-1">
          <span className="place text-2xl">{t.destinationName}</span>
          {t.destinationLat != null && t.destinationLng != null && (
            <span className="figures text-signal inline-flex items-center gap-1 text-sm">
              <MapPin className="size-3.5 self-center" aria-hidden />
              {formatCoords(t.destinationLat, t.destinationLng)}
            </span>
          )}
        </p>
        <div className="text-muted-foreground figures mt-3 flex flex-wrap gap-x-5 gap-y-1 text-sm">
          <span className="inline-flex items-center gap-1.5">
            <CalendarDays className="size-4" aria-hidden /> {formatDateRange(t.startDate, t.endDate)} ({t.days}{" "}
            {t.days === 1 ? "day" : "days"})
          </span>
          <span className="inline-flex items-center gap-1.5">
            <Users className="size-4" aria-hidden /> {t.members.length}{" "}
            {t.members.length === 1 ? "member" : "members"}
          </span>
        </div>
      </header>

      <Tabs
        className="mt-6"
        value={tab}
        onValueChange={(v) => router.replace(`${pathname}?tab=${v}`, { scroll: false })}
      >
        <TabsList aria-label="Trip sections">
          {TABS.map(({ id: tabId, label, icon: Icon }) => (
            <TabsTrigger key={tabId} value={tabId}>
              <Icon aria-hidden /> {label}
            </TabsTrigger>
          ))}
        </TabsList>
        <TabsContent value="overview">
          <OverviewTab trip={t} />
        </TabsContent>
        <TabsContent value="members">
          <MembersTab trip={t} />
        </TabsContent>
        <TabsContent value="shortlist">
          <ShortlistTab trip={t} />
        </TabsContent>
        <TabsContent value="group-fit">
          <PlaceholderTab
            icon={Scale}
            title="Group fit"
            body="See where the group's budgets, pace and interests agree or clash, and how well each place fits everyone."
            milestone={4}
          />
        </TabsContent>
        <TabsContent value="compare">
          <PlaceholderTab
            icon={GitCompareArrows}
            title="Compare options"
            body="Stays and activities side by side, each price with its source and when it was checked."
            milestone={4}
          />
        </TabsContent>
        <TabsContent value="itinerary">
          <PlaceholderTab
            icon={Route}
            title="Itinerary"
            body="A day-by-day draft from the shortlist that members can propose changes to."
            milestone={5}
          />
        </TabsContent>
        <TabsContent value="decisions">
          <PlaceholderTab
            icon={ClipboardList}
            title="Decision log"
            body="Every group decision with its votes, alternatives and a plain-language summary."
            milestone={6}
          />
        </TabsContent>
      </Tabs>
    </div>
  );
}
