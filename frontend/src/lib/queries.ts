"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { api } from "@/lib/api";
import type {
  AddShortlistInput,
  AiJob,
  AiStatus,
  CreateTripInput,
  GeoResult,
  Invite,
  InvitePreview,
  LinkPreview,
  PlaceCategory,
  PlaceResults,
  ProfileView,
  QuizAnswers,
  ReactionValue,
  ShortlistItem,
  TravelProfile,
  TripDetail,
  TripPreferences,
  TripSummary,
  UpdateShortlistInput,
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
  shortlist: (tripId: string) => ["trip", tripId, "shortlist"] as const,
  placeSearch: (tripId: string, q: string) => ["trip", tripId, "places", "search", q] as const,
  nearby: (tripId: string, category: PlaceCategory) => ["trip", tripId, "places", "nearby", category] as const,
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

export function useShortlist(tripId: string) {
  return useQuery({
    queryKey: keys.shortlist(tripId),
    queryFn: () => api<ShortlistItem[]>(`/api/trips/${tripId}/shortlist`),
  });
}

function replaceItem(list: ShortlistItem[] | undefined, item: ShortlistItem) {
  return list?.map((i) => (i.id === item.id ? item : i));
}

export function useAddShortlistItem(tripId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: AddShortlistInput) =>
      api<ShortlistItem>(`/api/trips/${tripId}/shortlist`, { method: "POST", body: JSON.stringify(input) }),
    onSuccess: (item) =>
      qc.setQueryData<ShortlistItem[]>(keys.shortlist(tripId), (old) => [item, ...(old ?? [])]),
  });
}

export function useUpdateShortlistItem(tripId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, input }: { id: string; input: UpdateShortlistInput }) =>
      api<ShortlistItem>(`/api/trips/${tripId}/shortlist/${id}`, { method: "PATCH", body: JSON.stringify(input) }),
    onSuccess: (item) => qc.setQueryData<ShortlistItem[]>(keys.shortlist(tripId), (old) => replaceItem(old, item)),
  });
}

export function useRemoveShortlistItem(tripId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api<void>(`/api/trips/${tripId}/shortlist/${id}`, { method: "DELETE" }),
    onMutate: async (id) => {
      await qc.cancelQueries({ queryKey: keys.shortlist(tripId) });
      const previous = qc.getQueryData<ShortlistItem[]>(keys.shortlist(tripId));
      qc.setQueryData<ShortlistItem[]>(keys.shortlist(tripId), (old) => old?.filter((i) => i.id !== id));
      return { previous };
    },
    onError: (_e, _id, ctx) => qc.setQueryData(keys.shortlist(tripId), ctx?.previous),
  });
}

/** Sets (or with `value: null` clears) the current user's reaction, optimistically. */
export function useReact(tripId: string, me: { id: string; displayName: string }) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ itemId, value, reason }: { itemId: string; value: ReactionValue | null; reason?: string }) =>
      value
        ? api<ShortlistItem>(`/api/trips/${tripId}/shortlist/${itemId}/reaction`, {
            method: "PUT",
            body: JSON.stringify({ value, reason: reason || null }),
          })
        : api<ShortlistItem>(`/api/trips/${tripId}/shortlist/${itemId}/reaction`, { method: "DELETE" }),
    onMutate: async ({ itemId, value, reason }) => {
      await qc.cancelQueries({ queryKey: keys.shortlist(tripId) });
      const previous = qc.getQueryData<ShortlistItem[]>(keys.shortlist(tripId));
      qc.setQueryData<ShortlistItem[]>(keys.shortlist(tripId), (old) =>
        old?.map((item) => {
          if (item.id !== itemId) return item;
          const others = item.reactions.filter((r) => r.userId !== me.id);
          if (!value) return { ...item, reactions: others };
          const mine = {
            userId: me.id,
            displayName: me.displayName,
            value,
            reason: reason || null,
            updatedAt: new Date().toISOString(),
          };
          return { ...item, reactions: [...others, mine] };
        }),
      );
      return { previous };
    },
    onError: (_e, _v, ctx) => qc.setQueryData(keys.shortlist(tripId), ctx?.previous),
    onSuccess: (item) => qc.setQueryData<ShortlistItem[]>(keys.shortlist(tripId), (old) => replaceItem(old, item)),
  });
}

export function usePlaceSearch(tripId: string, q: string) {
  const query = q.trim().replace(/\s+/g, " ");
  return useQuery({
    queryKey: keys.placeSearch(tripId, query.toLowerCase()),
    queryFn: () => api<PlaceResults>(`/api/trips/${tripId}/places/search?q=${encodeURIComponent(query)}`),
    enabled: query.length >= 2,
    staleTime: 5 * 60_000,
    retry: false,
  });
}

export function useNearbyPlaces(tripId: string, category: PlaceCategory | null) {
  return useQuery({
    queryKey: keys.nearby(tripId, category ?? "OTHER"),
    queryFn: () => api<PlaceResults>(`/api/trips/${tripId}/places/nearby?category=${category}`),
    enabled: !!category,
    staleTime: 5 * 60_000,
    retry: false,
  });
}

export function useLinkPreview(tripId: string) {
  return useMutation({
    mutationFn: (url: string) =>
      api<LinkPreview>(`/api/trips/${tripId}/link-preview?url=${encodeURIComponent(url)}`),
  });
}
