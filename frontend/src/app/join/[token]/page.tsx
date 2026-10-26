"use client";

import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { CalendarDays, Loader2, MapPin, Users } from "lucide-react";

import { ErrorState } from "@/components/error-state";
import { PageLoading } from "@/components/require-auth";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { formatDateRange } from "@/lib/format";
import { useAcceptInvite, useInvitePreview } from "@/lib/queries";

export default function JoinPage() {
  const { token } = useParams<{ token: string }>();
  const router = useRouter();
  const { status } = useAuth();
  const preview = useInvitePreview(token);
  const accept = useAcceptInvite(token);
  const here = `/join/${token}`;

  if (preview.isPending || status === "loading") return <PageLoading />;
  if (preview.isError) {
    const gone = preview.error instanceof ApiError && preview.error.status === 404;
    return (
      <div className="mx-auto my-16 max-w-md">
        <ErrorState title={gone ? "This invite link doesn't work" : "Couldn't open this invite"} error={preview.error} />
      </div>
    );
  }

  const p = preview.data;
  return (
    <Card className="mx-auto my-16 max-w-md">
      <CardHeader>
        <CardDescription>{p.organizerName} invited you to plan</CardDescription>
        <CardTitle className="text-2xl">{p.tripName}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-6">
        <ul className="text-muted-foreground space-y-1.5 text-sm">
          <li className="flex items-center gap-2">
            <MapPin className="size-4" aria-hidden /> {p.destinationName}
          </li>
          <li className="flex items-center gap-2">
            <CalendarDays className="size-4" aria-hidden /> {formatDateRange(p.startDate, p.endDate)}
          </li>
          <li className="flex items-center gap-2">
            <Users className="size-4" aria-hidden /> {p.memberCount} {p.memberCount === 1 ? "member" : "members"} so far
          </li>
        </ul>
        {status === "authenticated" ? (
          <>
            {accept.isError && (
              <p role="alert" className="text-destructive text-sm">
                {accept.error.message}
              </p>
            )}
            <Button
              className="w-full"
              disabled={accept.isPending}
              onClick={() => accept.mutate(undefined, { onSuccess: (r) => router.push(`/trips/${r.tripId}`) })}
            >
              {accept.isPending && <Loader2 className="animate-spin" aria-hidden />}
              Join trip
            </Button>
          </>
        ) : (
          <div className="grid gap-2">
            <Button asChild>
              <Link href={`/signup?next=${encodeURIComponent(here)}`}>Sign up to join</Link>
            </Button>
            <Button variant="outline" asChild>
              <Link href={`/login?next=${encodeURIComponent(here)}`}>I already have an account</Link>
            </Button>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
