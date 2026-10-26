"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { api } from "@/lib/api";
import type {
  AddShortlistInput,
  AiJob,
  CommentTarget,
  Itinerary,
  Pace,
  PlanEdit,
  Proposal,
  TripComment,
  Comparison,
  ComparisonKind,
  Decision,
  Gate,
  VoteValue,
  GroupFit,
  OptionDraft,
  OptionRowInput,
  RecommendationQuery,
  RecommendationSet,
  TradeoffSummary,
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
  groupFit: (tripId: string) => ["trip", tripId, "group-fit"] as const,
  recommendations: (tripId: string, category: PlaceCategory, gems: number | null, avoidCrowds: boolean) =>
    ["trip", tripId, "recommendations", category, gems, avoidCrowds] as const,
  comparisons: (tripId: string) => ["trip", tripId, "comparisons"] as const,
  itinerary: (tripId: string) => ["trip", tripId, "itinerary"] as const,
  proposals: (tripId: string) => ["trip", tripId, "proposals"] as const,
  comments: (tripId: string) => ["trip", tripId, "comments"] as const,
  gate: (tripId: string) => ["trip", tripId, "gate"] as const,
  decisions: (tripId: string) => ["trip", tripId, "decisions"] as const,
  aiActivity: (tripId: string) => ["trip", tripId, "ai-activity"] as const,
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
    onSuccess: (prefs) => {
      qc.setQueryData(keys.preferences(tripId), prefs);
      invalidateDerived(qc, tripId);
    },
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

/** Group fit and suggestions are computed from the shortlist and preferences; refresh them after changes. */
function invalidateDerived(qc: ReturnType<typeof useQueryClient>, tripId: string) {
  qc.invalidateQueries({ queryKey: keys.groupFit(tripId) });
  qc.invalidateQueries({ queryKey: ["trip", tripId, "recommendations"] });
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
    onSettled: () => invalidateDerived(qc, tripId),
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
    onSettled: () => invalidateDerived(qc, tripId),
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
    onSettled: () => qc.invalidateQueries({ queryKey: keys.groupFit(tripId) }),
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

// ---- group fit, recommendations, compare -------------------------------------------------------

export function useGroupFit(tripId: string) {
  return useQuery({ queryKey: keys.groupFit(tripId), queryFn: () => api<GroupFit>(`/api/trips/${tripId}/group-fit`) });
}

/** A3: starts an AI job that phrases the trade-offs. */
export function useStartTradeoffs(tripId: string) {
  return useMutation({
    mutationFn: (regenerate: boolean) =>
      api<AiJob<TradeoffSummary>>(`/api/trips/${tripId}/group-fit/tradeoffs?regenerate=${regenerate}`, {
        method: "POST",
      }),
  });
}

/** Instant: cached AI suggestions for these settings, else the rule-based ranking. */
export function useRecommendations(tripId: string, query: RecommendationQuery, enabled = true) {
  const params = new URLSearchParams({ category: query.category, avoidCrowds: String(query.avoidCrowds) });
  if (query.gems != null) params.set("gems", String(query.gems));
  return useQuery({
    queryKey: keys.recommendations(tripId, query.category, query.gems, query.avoidCrowds),
    queryFn: () => api<RecommendationSet>(`/api/trips/${tripId}/recommendations?${params}`),
    enabled,
    staleTime: 60_000,
    retry: false,
    placeholderData: (prev) => (prev?.category === query.category ? prev : undefined),
  });
}

/** A2: starts an AI job that re-ranks and explains the top suggestions. */
export function useStartRecommendationAi(tripId: string) {
  return useMutation({
    mutationFn: (query: RecommendationQuery) =>
      api<AiJob<RecommendationSet>>(`/api/trips/${tripId}/recommendations/ai`, {
        method: "POST",
        body: JSON.stringify(query),
      }),
  });
}

export function useComparisons(tripId: string) {
  return useQuery({
    queryKey: keys.comparisons(tripId),
    queryFn: () => api<Comparison[]>(`/api/trips/${tripId}/comparisons`),
  });
}

function replaceComparison(list: Comparison[] | undefined, c: Comparison) {
  return list?.map((x) => (x.id === c.id ? c : x));
}

export function useCreateComparison(tripId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { kind: ComparisonKind; title: string }) =>
      api<Comparison>(`/api/trips/${tripId}/comparisons`, { method: "POST", body: JSON.stringify(input) }),
    onSuccess: (c) => qc.setQueryData<Comparison[]>(keys.comparisons(tripId), (old) => [...(old ?? []), c]),
  });
}

export function useDeleteComparison(tripId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api<void>(`/api/trips/${tripId}/comparisons/${id}`, { method: "DELETE" }),
    onSuccess: (_v, id) =>
      qc.setQueryData<Comparison[]>(keys.comparisons(tripId), (old) => old?.filter((c) => c.id !== id)),
  });
}

/** Add (no `rowId`) or replace a row; the server answers with the whole comparison. */
export function useSaveOptionRow(tripId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ comparisonId, rowId, input }: { comparisonId: string; rowId?: string; input: OptionRowInput }) =>
      api<Comparison>(`/api/trips/${tripId}/comparisons/${comparisonId}/rows${rowId ? `/${rowId}` : ""}`, {
        method: rowId ? "PUT" : "POST",
        body: JSON.stringify(input),
      }),
    onSuccess: (c) => qc.setQueryData<Comparison[]>(keys.comparisons(tripId), (old) => replaceComparison(old, c)),
  });
}

export function useRecheckOptionRow(tripId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ comparisonId, rowId }: { comparisonId: string; rowId: string }) =>
      api<Comparison>(`/api/trips/${tripId}/comparisons/${comparisonId}/rows/${rowId}/recheck`, { method: "POST" }),
    onSuccess: (c) => qc.setQueryData<Comparison[]>(keys.comparisons(tripId), (old) => replaceComparison(old, c)),
  });
}

export function useDeleteOptionRow(tripId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ comparisonId, rowId }: { comparisonId: string; rowId: string }) =>
      api<Comparison>(`/api/trips/${tripId}/comparisons/${comparisonId}/rows/${rowId}`, { method: "DELETE" }),
    onSuccess: (c) => qc.setQueryData<Comparison[]>(keys.comparisons(tripId), (old) => replaceComparison(old, c)),
  });
}

export function useOptionDraft(tripId: string) {
  return useMutation({
    mutationFn: (url: string) =>
      api<OptionDraft>(`/api/trips/${tripId}/comparisons/draft?url=${encodeURIComponent(url)}`),
  });
}

/** `null` until the organizer generates the first draft. */
export function useItinerary(tripId: string) {
  return useQuery({
    queryKey: keys.itinerary(tripId),
    queryFn: async () => (await api<Itinerary | undefined>(`/api/trips/${tripId}/itinerary`)) ?? null,
  });
}

/** A4: generate (or replace) the draft in a background job. */
export function useGenerateItinerary(tripId: string) {
  return useMutation({
    mutationFn: (pace: Pace | null) =>
      api<AiJob<Itinerary>>(`/api/trips/${tripId}/itinerary/generate`, {
        method: "POST",
        body: JSON.stringify({ pace }),
      }),
  });
}

export function useRegenerateDay(tripId: string) {
  return useMutation({
    mutationFn: (day: number) =>
      api<AiJob<Itinerary>>(`/api/trips/${tripId}/itinerary/days/${day}/regenerate`, { method: "POST" }),
  });
}

/** Organizer edits; the server answers with the whole updated itinerary. */
export function useEditItinerary(tripId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (edit: PlanEdit) =>
      api<Itinerary>(`/api/trips/${tripId}/itinerary/edits`, { method: "POST", body: JSON.stringify(edit) }),
    onSuccess: (it) => {
      qc.setQueryData(keys.itinerary(tripId), it);
      qc.invalidateQueries({ queryKey: keys.proposals(tripId) });
    },
  });
}

export function useProposals(tripId: string, enabled = true) {
  return useQuery({
    queryKey: keys.proposals(tripId),
    queryFn: () => api<Proposal[]>(`/api/trips/${tripId}/proposals`),
    enabled,
  });
}

export function usePropose(tripId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { edit: PlanEdit; reason: string | null }) =>
      api<Proposal>(`/api/trips/${tripId}/proposals`, { method: "POST", body: JSON.stringify(input) }),
    onSuccess: (p) => {
      qc.setQueryData<Proposal[]>(keys.proposals(tripId), (old) => [p, ...(old ?? [])]);
      qc.invalidateQueries({ queryKey: keys.itinerary(tripId) });
    },
  });
}

export function useWithdrawProposal(tripId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api<Proposal>(`/api/trips/${tripId}/proposals/${id}/withdraw`, { method: "POST" }),
    onSuccess: (p) => {
      qc.setQueryData<Proposal[]>(keys.proposals(tripId), (old) => old?.map((x) => (x.id === p.id ? p : x)));
      qc.invalidateQueries({ queryKey: keys.itinerary(tripId) });
    },
  });
}

export function useComments(tripId: string) {
  return useQuery({
    queryKey: keys.comments(tripId),
    queryFn: () => api<TripComment[]>(`/api/trips/${tripId}/comments`),
  });
}

function bumpCommentCounts(qc: ReturnType<typeof useQueryClient>, tripId: string) {
  qc.invalidateQueries({ queryKey: keys.itinerary(tripId) });
  qc.invalidateQueries({ queryKey: keys.proposals(tripId) });
}

export function useAddComment(tripId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { targetType: CommentTarget; targetId: string; body: string }) =>
      api<TripComment>(`/api/trips/${tripId}/comments`, { method: "POST", body: JSON.stringify(input) }),
    onSuccess: (c) => {
      qc.setQueryData<TripComment[]>(keys.comments(tripId), (old) => [...(old ?? []), c]);
      bumpCommentCounts(qc, tripId);
    },
  });
}

export function useDeleteComment(tripId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api<void>(`/api/trips/${tripId}/comments/${id}`, { method: "DELETE" }),
    onSuccess: (_v, id) => {
      qc.setQueryData<TripComment[]>(keys.comments(tripId), (old) => old?.filter((c) => c.id !== id));
      bumpCommentCounts(qc, tripId);
    },
  });
}

// ---- voting, consensus gate, decision log -------------------------------------------------------------------

/** 404 when the trip has no itinerary yet; callers only enable this once there is one. */
export function useGate(tripId: string, enabled: boolean) {
  return useQuery({
    queryKey: keys.gate(tripId),
    queryFn: () => api<Gate>(`/api/trips/${tripId}/itinerary/approval`),
    enabled,
  });
}

function afterGateChange(qc: ReturnType<typeof useQueryClient>, tripId: string, gate: Gate) {
  qc.setQueryData(keys.gate(tripId), gate);
  qc.invalidateQueries({ queryKey: keys.itinerary(tripId) });
  qc.invalidateQueries({ queryKey: keys.proposals(tripId) });
  qc.invalidateQueries({ queryKey: keys.decisions(tripId) });
}

/** My verdict on the plan as it is now (`null` takes it back), optimistically. */
export function useVotePlan(tripId: string, me: { id: string }) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (value: VoteValue | null) =>
      value
        ? api<Gate>(`/api/trips/${tripId}/itinerary/approval`, { method: "PUT", body: JSON.stringify({ value }) })
        : api<Gate>(`/api/trips/${tripId}/itinerary/approval`, { method: "DELETE" }),
    onMutate: async (value) => {
      await qc.cancelQueries({ queryKey: keys.gate(tripId) });
      const previous = qc.getQueryData<Gate>(keys.gate(tripId));
      if (previous) qc.setQueryData(keys.gate(tripId), optimisticGate(previous, me.id, value));
      return { previous };
    },
    onError: (_e, _v, ctx) => qc.setQueryData(keys.gate(tripId), ctx?.previous),
    onSuccess: (gate) => qc.setQueryData(keys.gate(tripId), gate),
  });
}

function optimisticGate(g: Gate, userId: string, value: VoteValue | null): Gate {
  const before = g.voters.find((v) => v.userId === userId)?.value ?? null;
  const count = (v: VoteValue) => (before === v ? -1 : 0) + (value === v ? 1 : 0);
  const tally = {
    ...g.tally,
    approvals: g.tally.approvals + count("APPROVE"),
    rejections: g.tally.rejections + count("REJECT"),
  };
  const voters = g.voters.map((v) => (v.userId === userId ? { ...v, value, at: new Date().toISOString() } : v));
  return {
    ...g,
    myVote: value,
    tally,
    quorumMet: tally.approvals >= tally.required,
    voters,
    notVoted: voters.filter((v) => v.value === null).map((v) => ({ userId: v.userId, displayName: v.displayName })),
  };
}

export function useLockItinerary(tripId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (reason: string | null) =>
      api<Gate>(`/api/trips/${tripId}/itinerary/lock`, { method: "POST", body: JSON.stringify({ reason }) }),
    onSuccess: (gate) => afterGateChange(qc, tripId, gate),
  });
}

export function useUnlockItinerary(tripId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (reason: string | null) =>
      api<Gate>(`/api/trips/${tripId}/itinerary/unlock`, { method: "POST", body: JSON.stringify({ reason }) }),
    onSuccess: (gate) => afterGateChange(qc, tripId, gate),
  });
}

/** Approve or reject a proposal (`null` takes the vote back), optimistically. */
export function useVote(tripId: string, me: { id: string; displayName: string }) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ proposalId, value }: { proposalId: string; value: VoteValue | null }) =>
      value
        ? api<Proposal>(`/api/trips/${tripId}/proposals/${proposalId}/vote`, {
            method: "PUT",
            body: JSON.stringify({ value }),
          })
        : api<Proposal>(`/api/trips/${tripId}/proposals/${proposalId}/vote`, { method: "DELETE" }),
    onMutate: async ({ proposalId, value }) => {
      await qc.cancelQueries({ queryKey: keys.proposals(tripId) });
      const previous = qc.getQueryData<Proposal[]>(keys.proposals(tripId));
      qc.setQueryData<Proposal[]>(keys.proposals(tripId), (old) =>
        old?.map((p) => (p.id === proposalId ? optimisticVote(p, me, value) : p)),
      );
      return { previous };
    },
    onError: (_e, _v, ctx) => qc.setQueryData(keys.proposals(tripId), ctx?.previous),
    onSuccess: (p) => {
      qc.setQueryData<Proposal[]>(keys.proposals(tripId), (old) => old?.map((x) => (x.id === p.id ? p : x)));
      if (p.status !== "OPEN") {
        qc.invalidateQueries({ queryKey: keys.itinerary(tripId) });
        qc.invalidateQueries({ queryKey: keys.gate(tripId) });
        qc.invalidateQueries({ queryKey: keys.decisions(tripId) });
      }
    },
  });
}

function optimisticVote(p: Proposal, me: { id: string; displayName: string }, value: VoteValue | null): Proposal {
  const others = p.votes.filter((v) => v.voter.userId !== me.id);
  const votes = value
    ? [...others, { voter: { userId: me.id, displayName: me.displayName }, value, at: new Date().toISOString() }]
    : others;
  const approvals = votes.filter((v) => v.value === "APPROVE").length;
  const notVoted = value
    ? p.notVoted.filter((n) => n.userId !== me.id)
    : p.notVoted.some((n) => n.userId === me.id)
      ? p.notVoted
      : [...p.notVoted, { userId: me.id, displayName: me.displayName }];
  return {
    ...p,
    votes,
    myVote: value,
    notVoted,
    tally: { ...p.tally, approvals, rejections: votes.length - approvals },
  };
}

export function useDecisions(tripId: string) {
  return useQuery({
    queryKey: keys.decisions(tripId),
    queryFn: () => api<Decision[]>(`/api/trips/${tripId}/decisions`),
  });
}

/** A6 again for one entry; the list refreshes from live events when the job finishes. */
export function useSummarizeDecision(tripId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => api<AiJob>(`/api/trips/${tripId}/decisions/${id}/summary`, { method: "POST" }),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.decisions(tripId) }),
  });
}

export function useDismissDecisionSummary(tripId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      api<Decision>(`/api/trips/${tripId}/decisions/${id}/summary/dismiss`, { method: "POST" }),
    onSuccess: (d) => qc.setQueryData<Decision[]>(keys.decisions(tripId), (old) => old?.map((x) => (x.id === d.id ? d : x))),
  });
}

export function useUpdateQuorum(tripId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (minApprovals: number | null) =>
      api<TripDetail>(`/api/trips/${tripId}/quorum`, { method: "PUT", body: JSON.stringify({ minApprovals }) }),
    onSuccess: (trip) => {
      qc.setQueryData(keys.trip(tripId), trip);
      qc.invalidateQueries({ queryKey: keys.proposals(tripId) });
      qc.invalidateQueries({ queryKey: keys.gate(tripId) });
    },
  });
}
