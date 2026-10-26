"use client";

import { useState } from "react";
import { Check, Copy, Link2, Loader2, Trash2 } from "lucide-react";

import { MemberList } from "@/components/trip/member-list";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { NativeSelect } from "@/components/ui/select";
import { useAuth } from "@/lib/auth";
import { useCreateInvite, useInvites, useRevokeInvite, useUpdateQuorum } from "@/lib/queries";
import type { Invite, TripDetail } from "@/lib/types";

export function MembersTab({ trip }: { trip: TripDetail }) {
  const { user } = useAuth();
  const isOrganizer = trip.myRole === "ORGANIZER";
  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_24rem]">
      <Card>
        <CardHeader>
          <CardTitle>Members</CardTitle>
          <CardDescription>Everyone gets an equal vote. {trip.quorum.description}.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-6">
          <MemberList members={trip.members} currentUserId={user?.id} />
          {isOrganizer && <QuorumSetting trip={trip} />}
        </CardContent>
      </Card>
      {isOrganizer ? (
        <InvitePanel tripId={trip.id} />
      ) : (
        <Card>
          <CardHeader>
            <CardTitle>Invite someone</CardTitle>
            <CardDescription>Ask the organizer to share the trip&apos;s invite link.</CardDescription>
          </CardHeader>
        </Card>
      )}
    </div>
  );
}

/** Organizer: how many approvals a vote needs. Open votes are settled under the new rule right away. */
function QuorumSetting({ trip }: { trip: TripDetail }) {
  const update = useUpdateQuorum(trip.id);
  const n = trip.members.length;
  const value = trip.quorum.minApprovals == null ? "" : String(Math.min(trip.quorum.minApprovals, n));
  return (
    <div className="border-t pt-4">
      <label htmlFor="quorum" className="text-sm font-medium">
        Approvals a vote needs
      </label>
      <p className="text-muted-foreground mt-0.5 text-xs">
        Applies to proposed changes and to locking the itinerary.
      </p>
      <div className="mt-2 flex flex-wrap items-center gap-2">
        <NativeSelect
          id="quorum"
          className="h-9 w-auto"
          value={value}
          disabled={update.isPending}
          onChange={(e) => update.mutate(e.target.value === "" ? null : Number(e.target.value))}
        >
          <option value="">Majority ({Math.floor(n / 2) + 1} of {n})</option>
          {Array.from({ length: n }, (_, i) => i + 1).map((k) => (
            <option key={k} value={k}>
              {k === n ? `Everyone (${k} of ${n})` : `${k} of ${n}`}
            </option>
          ))}
        </NativeSelect>
        {update.isPending && <Loader2 className="text-muted-foreground size-4 animate-spin" aria-hidden />}
      </div>
      {update.error && (
        <p role="alert" className="text-destructive mt-1 text-xs">
          {update.error.message}
        </p>
      )}
    </div>
  );
}

function InvitePanel({ tripId }: { tripId: string }) {
  const invites = useInvites(tripId, true);
  const create = useCreateInvite(tripId);
  return (
    <Card>
      <CardHeader>
        <CardTitle>Invite links</CardTitle>
        <CardDescription>Anyone with an active link can join as a member. Revoke a link to stop that.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {invites.isPending ? (
          <p className="text-muted-foreground text-sm">Loading…</p>
        ) : invites.isError ? (
          <p role="alert" className="text-destructive text-sm">
            {invites.error.message}
          </p>
        ) : invites.data.length === 0 ? (
          <p className="text-muted-foreground text-sm">No active links.</p>
        ) : (
          <ul className="space-y-3">
            {invites.data.map((invite) => (
              <InviteRow key={invite.id} tripId={tripId} invite={invite} />
            ))}
          </ul>
        )}
        {create.isError && (
          <p role="alert" className="text-destructive text-sm">
            {create.error.message}
          </p>
        )}
        <Button onClick={() => create.mutate()} disabled={create.isPending} className="w-full">
          {create.isPending ? <Loader2 className="animate-spin" aria-hidden /> : <Link2 aria-hidden />}
          Create invite link
        </Button>
      </CardContent>
    </Card>
  );
}

function InviteRow({ tripId, invite }: { tripId: string; invite: Invite }) {
  const revoke = useRevokeInvite(tripId);
  const [copied, setCopied] = useState(false);
  return (
    <li className="flex gap-2">
      <Input readOnly value={invite.url} aria-label="Invite link" onFocus={(e) => e.currentTarget.select()} className="font-mono text-xs" />
      <Button
        variant="outline"
        size="icon"
        aria-label={copied ? "Copied" : "Copy invite link"}
        onClick={async () => {
          try {
            await navigator.clipboard.writeText(invite.url);
            setCopied(true);
            setTimeout(() => setCopied(false), 2000);
          } catch {
            // clipboard blocked; the field is selectable
          }
        }}
      >
        {copied ? <Check aria-hidden /> : <Copy aria-hidden />}
      </Button>
      <Button
        variant="outline"
        size="icon"
        aria-label="Revoke invite link"
        onClick={() => revoke.mutate(invite.id)}
        disabled={revoke.isPending}
      >
        <Trash2 aria-hidden />
      </Button>
    </li>
  );
}
