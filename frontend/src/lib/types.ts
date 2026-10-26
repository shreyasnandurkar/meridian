// Mirrors the backend DTOs. Keep in sync with com.meridian.* records.

export type Role = "ORGANIZER" | "MEMBER";

export interface Option {
  id: string;
  label: string;
}

export interface Vocabulary {
  interests: (Option & { headline: boolean })[];
  paces: Option[];
  budgetLevels: Option[];
  dietary: Option[];
  accessNeeds: Option[];
  limits: { mustHaves: number; dealbreakers: number; idealTrip: number; listItem: number };
}

export interface Budget {
  stay: string;
  food: string;
  activities: string;
}

export interface QuizAnswers {
  interestRatings: Record<string, number>;
  pace: string;
  budget: Budget;
  crowdTolerance: number;
  hiddenGems: number;
  dietary: string[];
  accessNeeds: string[];
  mustHaves: string[];
  dealbreakers: string[];
  idealTrip: string;
}

export interface TravelProfile {
  interests: Record<string, number>;
  pace: string;
  budget: Budget;
  crowdTolerance: number;
  hiddenGems: number;
  dietary: string[];
  accessNeeds: string[];
  mustHaves: string[];
  dealbreakers: string[];
  summary: string | null;
}

export interface Inference {
  field: string;
  value: string;
  evidence: string;
}

export interface Provenance {
  source: "ai" | "rules";
  model: string | null;
  promptVersion: string | null;
  inferences: Inference[];
  note: string | null;
}

export interface ProfileView {
  profile: TravelProfile;
  provenance: Provenance;
  quiz: QuizAnswers | null;
  edited: boolean;
  updatedAt: string;
}

export type JobStatus = "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED" | "CANCELLED";

export interface AiJob<T = unknown> {
  id: string;
  feature: string;
  status: JobStatus;
  percent: number;
  message: string;
  result: T | null;
  error: string | null;
  createdAt: string;
  finishedAt: string | null;
}

export interface AiStatus {
  enabled: boolean;
  reachable: boolean;
  model: string;
}

export interface TripSummary {
  id: string;
  name: string;
  destinationName: string;
  startDate: string;
  endDate: string;
  myRole: Role;
  memberCount: number;
}

export interface Member {
  userId: string;
  displayName: string;
  role: Role;
  joinedAt: string;
  hasProfile: boolean;
}

export interface TripDetail {
  id: string;
  name: string;
  destinationName: string;
  destinationLat: number | null;
  destinationLng: number | null;
  startDate: string;
  endDate: string;
  days: number;
  currency: string;
  budgetTotal: number | null;
  quorum: { requiredApprovals: number; minApprovals: number | null; description: string };
  myRole: Role;
  members: Member[];
  createdAt: string;
}

export interface CreateTripInput {
  name: string;
  destinationName: string;
  destinationLat: number | null;
  destinationLng: number | null;
  startDate: string;
  endDate: string;
  currency: string;
  budgetTotal: number | null;
}

export interface Invite {
  id: string;
  token: string;
  url: string;
  createdAt: string;
}

export interface InvitePreview {
  tripName: string;
  destinationName: string;
  startDate: string;
  endDate: string;
  organizerName: string;
  memberCount: number;
}

export interface TripPreferences {
  budgetMin: number | null;
  budgetMax: number | null;
  mustHaves: string[];
  budgetVisible: boolean;
  currency: string;
  updatedAt: string | null;
}

export interface GeoResult {
  name: string;
  displayName: string;
  lat: number;
  lng: number;
  type: string;
}

export type PlaceCategory = "SIGHTS" | "FOOD" | "STAYS" | "ACTIVITIES" | "OTHER";

export interface Place {
  id: string;
  source: string;
  sourceId: string;
  sourceUrl: string | null;
  name: string;
  category: PlaceCategory;
  lat: number;
  lng: number;
  address: string | null;
  website: string | null;
  tags: Record<string, string>;
  popularityProxy: number;
  fetchedAt: string;
}

/** Places with their provenance; `stale` means the provider was down and these are older stored results. */
export interface PlaceResults {
  places: Place[];
  source: string;
  fetchedAt: string | null;
  stale: boolean;
}

export type ShortlistKind = "PLACE" | "LINK" | "PIN";
export type ReactionValue = "LOVE" | "OK" | "NO_WAY";

export interface ShortlistReaction {
  userId: string;
  displayName: string;
  value: ReactionValue;
  reason: string | null;
  updatedAt: string;
}

export interface ShortlistItem {
  id: string;
  kind: ShortlistKind;
  title: string;
  category: PlaceCategory;
  place: Place | null;
  url: string | null;
  host: string | null;
  lat: number | null;
  lng: number | null;
  addedBy: { userId: string; displayName: string };
  reason: string | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
  reactions: ShortlistReaction[];
  canEdit: boolean;
}

export interface AddShortlistInput {
  kind: ShortlistKind;
  placeId?: string;
  url?: string;
  title?: string;
  lat?: number;
  lng?: number;
  category?: PlaceCategory;
  reason?: string;
  notes?: string;
}

export interface UpdateShortlistInput {
  category?: PlaceCategory;
  title?: string;
  reason?: string;
  notes?: string;
}

export interface LinkPreview {
  url: string;
  host: string;
  title: string | null;
}
