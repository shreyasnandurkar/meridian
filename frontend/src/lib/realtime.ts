"use client";

import { useEffect, useState } from "react";
import { Client } from "@stomp/stompjs";
import { useQueryClient, type QueryClient } from "@tanstack/react-query";

import { API_URL, getToken } from "@/lib/api";
import { keys } from "@/lib/queries";
import type { AiActivity, AiJob, TripEvent, TripEventType } from "@/lib/types";

export type LiveStatus = "connecting" | "live" | "offline";

const WS_URL = `${API_URL.replace(/^http/, "ws")}/ws`;

/** What each event makes stale. Views are per viewer, so events say what changed and we refetch it. */
const STALE: Record<Exclude<TripEventType, "AI_JOB">, (tripId: string) => readonly unknown[][]> = {
  PROPOSALS: (t) => [[...keys.proposals(t)], [...keys.itinerary(t)], [...keys.gate(t)]],
  VOTES: (t) => [[...keys.proposals(t)]],
  APPROVAL: (t) => [[...keys.gate(t)]],
  ITINERARY: (t) => [[...keys.itinerary(t)], [...keys.gate(t)], [...keys.proposals(t)]],
  DECISIONS: (t) => [[...keys.decisions(t)], [...keys.itinerary(t)]],
  COMMENTS: (t) => [[...keys.comments(t)], [...keys.itinerary(t)], [...keys.proposals(t)]],
  SHORTLIST: (t) => [
    [...keys.shortlist(t)],
    [...keys.groupFit(t)],
    ["trip", t, "recommendations"],
    [...keys.itinerary(t)],
  ],
  MEMBERS: (t) => [[...keys.trip(t)], [...keys.groupFit(t)], ["trip", t, "recommendations"], [...keys.gate(t)]],
  TRIP: (t) => [[...keys.trip(t)], [...keys.gate(t)], [...keys.proposals(t)]],
};

function apply(qc: QueryClient, tripId: string, event: TripEvent) {
  if (event.type === "AI_JOB") {
    const a = event.data as unknown as AiActivity;
    const activity: AiActivity = { ...a, ownerId: event.actorId };
    qc.setQueryData<Record<string, AiActivity>>(keys.aiActivity(tripId), (old) => ({
      ...(old ?? {}),
      [a.jobId]: activity,
    }));
    // the owner's progress bar moves without waiting for the next poll; the result still comes from polling
    qc.setQueryData<AiJob>(keys.job(a.jobId), (old) =>
      old && old.status !== "SUCCEEDED" && a.status === "RUNNING"
        ? { ...old, status: a.status, percent: Math.max(old.percent, a.percent), message: a.message }
        : old,
    );
    return;
  }
  for (const key of STALE[event.type](tripId)) qc.invalidateQueries({ queryKey: key });
}

/**
 * Subscribes to `/topic/trips/{id}` while the trip hub is open and keeps the TanStack Query cache current from the
 * events: votes, proposals, the plan, decisions, comments, the shortlist and AI-job progress. After a reconnect
 * everything for the trip is refetched, since events may have been missed.
 */
export function useTripEvents(tripId: string): LiveStatus {
  const qc = useQueryClient();
  const [status, setStatus] = useState<LiveStatus>("connecting");

  useEffect(() => {
    let connectedBefore = false;
    const client = new Client({
      brokerURL: WS_URL,
      reconnectDelay: 3000,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      beforeConnect: (c) => {
        c.connectHeaders = { Authorization: `Bearer ${getToken() ?? ""}` };
      },
      onConnect: () => {
        setStatus("live");
        if (connectedBefore) qc.invalidateQueries({ queryKey: ["trip", tripId] });
        connectedBefore = true;
        client.subscribe(`/topic/trips/${tripId}`, (msg) => {
          try {
            apply(qc, tripId, JSON.parse(msg.body) as TripEvent);
          } catch {
            // a malformed event only costs a live update
          }
        });
      },
      onWebSocketClose: () => setStatus("offline"),
      onStompError: () => setStatus("offline"),
    });
    client.activate();
    return () => {
      void client.deactivate();
    };
  }, [qc, tripId]);

  return status;
}
