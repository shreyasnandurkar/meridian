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

// ---- group fit (spec §5.4) ---------------------------------------------------------------------

export type FitStatus = "AGREE" | "TRADE_OFF" | "CONFLICT" | "PLAN_AROUND" | "UNKNOWN";

/** One member's value for one attribute; `hidden` = someone else's private budget. */
export interface FitCell {
  userId: string;
  value: string;
  detail: string | null;
  level: number | null;
  hidden: boolean;
}

export interface FitRow {
  key: string;
  label: string;
  status: FitStatus;
  cells: FitCell[];
  summary: string;
  suggestion: string | null;
  facts: string[];
}

export interface Contribution {
  userId: string;
  displayName: string;
  organizer: boolean;
  hasProfile: boolean;
  tripBudgetSet: boolean;
  itemsAdded: number;
  reactions: number;
  itemsToReact: number;
  missing: string[];
}

export type FitVerdict = "SATISFIES" | "NEUTRAL" | "MISSES" | "UNKNOWN";
export type FitEffect = "PLUS" | "MINUS" | "BLOCKER" | "NOTE";

export interface MemberFit {
  userId: string;
  displayName: string;
  score: number | null;
  verdict: FitVerdict;
  reasons: { effect: FitEffect; text: string }[];
  reaction: ReactionValue | null;
}

export interface ItemFit {
  itemId: string;
  title: string;
  score: number | null;
  satisfied: number;
  missed: number;
  unknown: number;
  members: MemberFit[];
  summary: string;
}

export interface Tradeoff {
  key: string;
  headline: string;
  explanation: string;
  suggestion: string | null;
}

export interface TradeoffSummary {
  items: Tradeoff[];
  provenance: Provenance;
  inputKey: string;
}

export interface GroupFit {
  contributions: Contribution[];
  rows: FitRow[];
  counts: { agree: number; tradeOff: number; conflict: number; planAround: number; unknown: number };
  sharedBudget: { currency: string; min: number | null; max: number | null; membersWithBudget: number };
  items: ItemFit[];
  tradeoffs: TradeoffSummary;
  aiAvailable: boolean;
}

// ---- recommendations (spec §5.5) ---------------------------------------------------------------

export interface Recommendation {
  place: Place;
  rank: number;
  rulesRank: number;
  score: number;
  why: string;
  matchedInterests: string[];
  fitsMembers: string[];
  missesMembers: string[];
  localSignals: string[];
  distanceKm: number;
  /** Always false today; the "Sponsored" label renders only when true. */
  sponsored: boolean;
}

export interface RecommendationSet {
  category: PlaceCategory;
  gems: number;
  groupGems: number;
  avoidCrowds: boolean;
  items: Recommendation[];
  candidates: number;
  placesSource: string;
  placesCheckedAt: string | null;
  placesStale: boolean;
  provenance: Provenance;
  inputKey: string;
  aiAvailable: boolean;
}

export interface RecommendationQuery {
  category: PlaceCategory;
  gems: number | null;
  avoidCrowds: boolean;
  regenerate?: boolean;
}

// ---- compare (spec §5.6) -----------------------------------------------------------------------

export type ComparisonKind = "STAY" | "FLIGHT" | "ACTIVITY" | "TRANSPORT" | "OTHER";

export interface OptionRow {
  id: string;
  provider: string;
  title: string | null;
  price: number | null;
  currency: string | null;
  checkedAt: string | null;
  stale: boolean;
  sourceUrl: string | null;
  sourceHost: string | null;
  sponsored: boolean;
  notes: string | null;
  adapter: string;
  addedBy: { userId: string; displayName: string };
  cheapest: boolean;
  canEdit: boolean;
}

export interface Comparison {
  id: string;
  kind: ComparisonKind;
  title: string;
  createdBy: { userId: string; displayName: string };
  createdAt: string;
  rows: OptionRow[];
  canEdit: boolean;
}

export interface OptionRowInput {
  provider: string;
  title?: string | null;
  price?: number | null;
  currency?: string | null;
  checkedAt?: string | null;
  sourceUrl?: string | null;
  sponsored: boolean;
  notes?: string | null;
  adapter?: string | null;
}

export interface OptionDraft {
  provider: string;
  title: string | null;
  price: number | null;
  currency: string | null;
  sourceUrl: string;
  adapter: string;
}

// ---- itinerary ------------------------------------------------------------------------------------------------

export type Pace = "SLOW" | "BALANCED" | "PACKED";

export interface PersonRef {
  userId: string;
  displayName: string;
}

export interface ItineraryStop {
  id: string;
  shortlistItemId: string;
  title: string;
  category: PlaceCategory;
  lat: number | null;
  lng: number | null;
  /** "HH:MM" */
  start: string;
  end: string;
  durationMin: number;
  /** From the previous stop on the same day; 0 for the first. */
  travelMin: number;
  travelKm: number | null;
  note: string | null;
  addedBy: PersonRef;
  addedReason: string | null;
  commentCount: number;
  /** The latest decision-log entry about this stop. */
  decisionId: string | null;
}

export interface ItineraryDay {
  index: number;
  date: string;
  summary: string | null;
  provenance: Provenance;
  repairs: string[];
  dayStart: string;
  dayEnd: string;
  overflowMin: number;
  stops: ItineraryStop[];
}

export interface UnplacedItem {
  shortlistItemId: string;
  title: string;
  category: PlaceCategory;
  reason: string;
  placeable: boolean;
}

export interface Itinerary {
  id: string;
  status: "DRAFT" | "LOCKED";
  version: number;
  pace: Pace;
  groupPace: Pace;
  generatedAt: string;
  generatedBy: PersonRef;
  updatedAt: string;
  days: ItineraryDay[];
  unplaced: UnplacedItem[];
  openProposals: number;
  canEdit: boolean;
  aiAvailable: boolean;
  lockedAt: string | null;
  lockedBy: PersonRef | null;
}

export type PlanEditType = "MOVE" | "SWAP" | "REMOVE" | "ADD";

export interface PlanEdit {
  type: PlanEditType;
  itemId: string;
  otherItemId?: string | null;
  day?: number | null;
  position?: number | null;
}

export type ProposalStatus = "OPEN" | "WITHDRAWN" | "APPROVED" | "REJECTED" | "STALE" | "CLOSED";

export interface Proposal {
  id: string;
  type: PlanEditType;
  edit: PlanEdit;
  summary: string;
  reason: string | null;
  status: ProposalStatus;
  createdBy: PersonRef;
  createdAt: string;
  resolvedAt: string | null;
  current: boolean;
  problems: string[];
  canWithdraw: boolean;
  commentCount: number;
  votes: { voter: PersonRef; value: VoteValue; at: string }[];
  tally: Tally;
  notVoted: PersonRef[];
  myVote: VoteValue | null;
  canVote: boolean;
  decisionId: string | null;
}

export type CommentTarget = "SHORTLIST_ITEM" | "PROPOSAL";

export interface TripComment {
  id: string;
  targetType: CommentTarget;
  targetId: string;
  author: PersonRef;
  body: string;
  createdAt: string;
  canDelete: boolean;
}

// ---- voting, consensus gate, decision log --------------------------------------------------------------------

export type VoteValue = "APPROVE" | "REJECT";

export interface Tally {
  approvals: number;
  rejections: number;
  required: number;
  members: number;
}

export interface PlanVoter {
  userId: string;
  displayName: string;
  /** Verdict on the current plan; null when they have not voted on this version. */
  value: VoteValue | null;
  /** Verdict on an earlier version of the plan. */
  earlier: VoteValue | null;
  at: string | null;
}

export interface Gate {
  status: "DRAFT" | "LOCKED";
  version: number;
  tally: Tally;
  quorumMet: boolean;
  voters: PlanVoter[];
  notVoted: PersonRef[];
  myVote: VoteValue | null;
  canVote: boolean;
  canLock: boolean;
  openProposals: number;
  lockedAt: string | null;
  lockedBy: PersonRef | null;
  lockDecisionId: string | null;
}

export type DecisionKind = "CHANGE_APPROVED" | "CHANGE_REJECTED" | "PLAN_LOCKED" | "LOCK_OVERRIDE" | "PLAN_UNLOCKED";

export interface Decision {
  id: string;
  seq: number;
  kind: DecisionKind;
  title: string;
  alternatives: string[];
  votes: { userId: string; displayName: string; value: VoteValue; at: string }[];
  tally: Tally;
  notVoted: { userId: string; displayName: string }[];
  proposalId: string | null;
  reason: string | null;
  itineraryVersion: number | null;
  itemIds: string[];
  decidedBy: PersonRef | null;
  decidedAt: string;
  summary: string;
  provenance: Provenance;
  summaryPending: boolean;
  canSummarize: boolean;
}

export type TripEventType =
  | "PROPOSALS"
  | "VOTES"
  | "APPROVAL"
  | "ITINERARY"
  | "DECISIONS"
  | "COMMENTS"
  | "SHORTLIST"
  | "MEMBERS"
  | "TRIP"
  | "AI_JOB";

export interface TripEvent {
  type: TripEventType;
  tripId: string;
  actorId: string | null;
  data: Record<string, unknown>;
  at: string;
}

/** An AI job someone in the group is running, from live events. */
export interface AiActivity {
  jobId: string;
  feature: string;
  status: AiJob["status"];
  percent: number;
  message: string;
  ownerId: string | null;
}
