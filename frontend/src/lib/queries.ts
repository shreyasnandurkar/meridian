"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { api } from "@/lib/api";
import type {
  AiJob,
  AiStatus,
  CreateTripInput,
  GeoResult,
  Invite,
  InvitePreview,
  ProfileView,
  QuizAnswers,
  TravelProfile,
  TripDetail,
  TripPreferences,
  TripSummary,
  Vocabulary,
} from "@/lib/types";

export const keys = {
  vocabulary: ["vocabulary"] as const,
  profile: ["profile"] as const,
  aiStatus: ["ai-status"] as const,
  job: (id: string) => ["ai-job", id] as const,
  trips: ["trips"] as const,
  trip: (id: string) => ["trip", id] as const,
  invites: (tripId: string) => ["trip", tripId, "invites"] as const,
  preferences: (tripId: string) => ["trip", tripId, "preferences"] as const,
  invite: (token: string) => ["invite", token] as const,
  geo: (q: string) => ["geo", q] as const,
};

export function useVocabulary() {
  return useQuery({
    queryKey: keys.vocabulary,
    queryFn: () => api<Vocabulary>("/api/profile/vocabulary"),
    staleTime: Infinity,
  });
}

/** `null` when the user has not taken the quiz yet (API answers 204). */
export function useProfile() {
  return useQuery({
    queryKey: keys.profile,
    queryFn: async () => (await api<ProfileView | undefined>("/api/profile")) ?? null,
  });
}

export function useAiStatus() {
  return useQuery({ queryKey: keys.aiStatus, queryFn: () => api<AiStatus>("/api/ai/status"), staleTime: 30_000 });
}

export function useSubmitQuiz() {
  return useMutation({
    mutationFn: (quiz: QuizAnswers) =>
      api<AiJob<ProfileView>>("/api/profile/quiz", { method: "POST", body: JSON.stringify(quiz) }),
  });
}

export function useUpdateProfile() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (profile: TravelProfile) =>
      api<ProfileView>("/api/profile", { method: "PUT", body: JSON.stringify(profile) }),
    onSuccess: (view) => qc.setQueryData(keys.profile, view),
  });
}

/** Polls an AI job until it reaches a terminal state. */
export function useAiJob<T>(id: string | null) {
  return useQuery({
    queryKey: keys.job(id ?? "none"),
    queryFn: () => api<AiJob<T>>(`/api/ai/jobs/${id}`),
    enabled: !!id,
    refetchInterval: (q) => {
      const s = q.state.data?.status;
      return s === "QUEUED" || s === "RUNNING" || s === undefined ? 600 : false;
    },
    staleTime: 0,
  });
}

export function useCancelJob() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api<AiJob>(`/api/ai/jobs/${id}/cancel`, { method: "POST" }),
    onSuccess: (job) => qc.setQueryData(keys.job(job.id), job),
  });
}

export function useTrips() {
  return useQuery({ queryKey: keys.trips, queryFn: () => api<TripSummary[]>("/api/trips") });
}

export function useTrip(id: string) {
  return useQuery({ queryKey: keys.trip(id), queryFn: () => api<TripDetail>(`/api/trips/${id}`) });
}

export function useCreateTrip() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: CreateTripInput) =>
      api<TripDetail>("/api/trips", { method: "POST", body: JSON.stringify(input) }),
    onSuccess: (trip) => {
      qc.setQueryData(keys.trip(trip.id), trip);
      qc.invalidateQueries({ queryKey: keys.trips });
    },
  });
}

export function useInvites(tripId: string, enabled: boolean) {
  return useQuery({
    queryKey: keys.invites(tripId),
    queryFn: () => api<Invite[]>(`/api/trips/${tripId}/invites`),
    enabled,
  });
}

export function useCreateInvite(tripId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api<Invite>(`/api/trips/${tripId}/invites`, { method: "POST" }),
    onSuccess: (invite) => qc.setQueryData<Invite[]>(keys.invites(tripId), (old) => [invite, ...(old ?? [])]),
  });
}

export function useRevokeInvite(tripId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (inviteId: string) => api<void>(`/api/trips/${tripId}/invites/${inviteId}`, { method: "DELETE" }),
    onMutate: async (inviteId) => {
      await qc.cancelQueries({ queryKey: keys.invites(tripId) });
      const previous = qc.getQueryData<Invite[]>(keys.invites(tripId));
      qc.setQueryData<Invite[]>(keys.invites(tripId), (old) => old?.filter((i) => i.id !== inviteId));
      return { previous };
    },
    onError: (_e, _id, ctx) => qc.setQueryData(keys.invites(tripId), ctx?.previous),
  });
}

export function useInvitePreview(token: string) {
  return useQuery({
    queryKey: keys.invite(token),
    queryFn: () => api<InvitePreview>(`/api/invites/${token}`),
    retry: false,
  });
}

export function useAcceptInvite(token: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () =>
      api<{ tripId: string; alreadyMember: boolean }>(`/api/invites/${token}/accept`, { method: "POST" }),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.trips }),
  });
}

export function useTripPreferences(tripId: string) {
  return useQuery({
    queryKey: keys.preferences(tripId),
    queryFn: () => api<TripPreferences>(`/api/trips/${tripId}/preferences/me`),
  });
}

export function useUpdateTripPreferences(tripId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (prefs: Pick<TripPreferences, "budgetMin" | "budgetMax" | "mustHaves" | "budgetVisible">) =>
      api<TripPreferences>(`/api/trips/${tripId}/preferences/me`, { method: "PUT", body: JSON.stringify(prefs) }),
    onSuccess: (prefs) => qc.setQueryData(keys.preferences(tripId), prefs),
  });
}

export function useGeoSearch(q: string) {
  const query = q.trim();
  return useQuery({
    queryKey: keys.geo(query.toLowerCase()),
    queryFn: () => api<GeoResult[]>(`/api/geo/search?q=${encodeURIComponent(query)}`),
    enabled: query.length >= 3,
    staleTime: Infinity,
    retry: false,
  });
}
