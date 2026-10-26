package com.meridian.decision;

import com.meridian.ai.job.AiJob;
import com.meridian.ai.job.AiJobService;
import com.meridian.common.ApiException;
import com.meridian.decision.DecisionDtos.DecisionView;
import com.meridian.decision.DecisionEntry.Kind;
import com.meridian.decision.DecisionEntry.SummarySource;
import com.meridian.itinerary.Comment;
import com.meridian.itinerary.CommentRepository;
import com.meridian.itinerary.ProposalRepository;
import com.meridian.profile.ProfileModels.Provenance;
import com.meridian.realtime.TripEvents;
import com.meridian.shortlist.ShortlistDtos.Person;
import com.meridian.trip.TripAccess;
import com.meridian.user.User;
import com.meridian.user.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The decision log (spec §5.8). Entries are appended when a vote closes or the plan is locked or unlocked, with a
 * template summary; after the commit an AI job (A6) rewrites the summary from the recorded facts.
 */
@Service
public class DecisionService {

    static final String AI_NOTE = "Code recorded the votes and counts when the vote closed; the AI only wrote this "
            + "sentence from them.";
    private static final int COMMENTS = 4;

    private final TripAccess access;
    private final DecisionRepository decisions;
    private final ProposalRepository proposals;
    private final CommentRepository comments;
    private final UserRepository users;
    private final DecisionSummaryWriter writer;
    private final AiJobService jobs;
    private final TripEvents events;
    private final TransactionTemplate tx;
    /** Entries whose summary the AI is writing right now. */
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();

    public DecisionService(TripAccess access, DecisionRepository decisions, ProposalRepository proposals,
            CommentRepository comments, UserRepository users, DecisionSummaryWriter writer, AiJobService jobs,
            TripEvents events, TransactionTemplate tx) {
        this.access = access;
        this.decisions = decisions;
        this.proposals = proposals;
        this.comments = comments;
        this.users = users;
        this.writer = writer;
        this.jobs = jobs;
        this.events = events;
        this.tx = tx;
    }

    @Transactional(readOnly = true)
    public List<DecisionView> list(UUID tripId, UUID userId) {
        access.requireMember(tripId, userId);
        List<DecisionEntry> list = decisions.findByTripIdOrderBySeqDesc(tripId);
        Set<UUID> people = new HashSet<>();
        list.forEach(d -> {
            if (d.getDecidedBy() != null) people.add(d.getDecidedBy());
        });
        Map<UUID, String> names = names(people);
        return list.stream().map(d -> view(d, names)).toList();
    }

    /** Appends an entry; its AI summary is started once the surrounding transaction has committed. */
    @Transactional
    public DecisionEntry record(UUID tripId, DecisionEntry.Draft draft, UUID actorId) {
        DecisionEntry entry = decisions.save(new DecisionEntry(tripId, decisions.maxSeq(tripId) + 1, draft));
        events.publish(tripId, TripEvents.Type.DECISIONS, actorId, Map.of("decisionId", entry.getId()));
        if (summarizable(entry)) {
            pending.add(entry.getId());
            TripEvents.afterCommit(() -> startSummary(tripId, actorId, entry.getId(), false));
        }
        return entry;
    }

    /** A6 again for one entry (any member), skipping the cache. */
    public AiJob summarize(UUID tripId, UUID userId, UUID decisionId) {
        access.requireMember(tripId, userId);
        DecisionEntry entry = decisions.findByIdAndTripId(decisionId, tripId)
                .orElseThrow(() -> ApiException.notFound("Decision not found"));
        if (!summarizable(entry)) throw ApiException.badRequest("This entry is written by code only");
        pending.add(decisionId);
        events.publish(tripId, TripEvents.Type.DECISIONS, userId, Map.of("decisionId", decisionId));
        return startSummary(tripId, userId, decisionId, true);
    }

    /** Dismissing the AI summary puts the code-written one back. */
    @Transactional
    public DecisionView dismissSummary(UUID tripId, UUID userId, UUID decisionId) {
        access.requireMember(tripId, userId);
        DecisionEntry entry = decisions.findByIdAndTripId(decisionId, tripId)
                .orElseThrow(() -> ApiException.notFound("Decision not found"));
        entry.summarized(DecisionTexts.template(context(entry)), SummarySource.RULES, null, null);
        events.publish(tripId, TripEvents.Type.DECISIONS, userId, Map.of("decisionId", decisionId));
        return view(entry, names(entry.getDecidedBy() == null ? Set.of() : Set.of(entry.getDecidedBy())));
    }

    private boolean summarizable(DecisionEntry entry) {
        return entry.getKind() != Kind.PLAN_UNLOCKED && writer.aiEnabled();
    }

    private AiJob startSummary(UUID tripId, UUID ownerId, UUID decisionId, boolean regenerate) {
        return jobs.submit(ownerId, tripId, DecisionSummaryWriter.FEATURE, job -> {
            try {
                job.progress(10, "Reading the votes");
                DecisionTexts.Context context = tx.execute(s -> context(decisions.findById(decisionId).orElseThrow()));
                job.progress(30, "Asking the AI to write the decision summary");
                Optional<DecisionSummaryWriter.Written> written = writer.write(context, regenerate);
                written.ifPresent(w -> tx.executeWithoutResult(s -> decisions.findById(decisionId).orElseThrow()
                        .summarized(w.summary(), SummarySource.AI, w.model(), w.promptVersion())));
                job.progress(100, written.isPresent() ? "Summary written" : "Kept the code-written summary");
                return Map.of("decisionId", decisionId, "ai", written.isPresent());
            } finally {
                pending.remove(decisionId);
                events.publish(tripId, TripEvents.Type.DECISIONS, ownerId, Map.of("decisionId", decisionId));
            }
        });
    }

    /** What the template and the AI may say about an entry. Call inside a transaction. */
    DecisionTexts.Context context(DecisionEntry e) {
        String proposer = null;
        List<String> discussion = List.of();
        if (e.getProposalId() != null) {
            var proposal = proposals.findById(e.getProposalId()).orElse(null);
            if (proposal != null) {
                proposer = names(Set.of(proposal.getCreatedBy())).getOrDefault(proposal.getCreatedBy(), "A member");
                List<Comment> thread = comments.findByTripIdOrderByCreatedAt(e.getTripId()).stream()
                        .filter(c -> c.getTargetType() == Comment.Target.PROPOSAL
                                && c.getTargetId().equals(proposal.getId())
                                && !c.getCreatedAt().isAfter(e.getDecidedAt()))
                        .toList();
                Map<UUID, String> authors = names(thread.stream().map(Comment::getAuthorId).collect(Collectors.toSet()));
                discussion = thread.subList(Math.max(0, thread.size() - COMMENTS), thread.size()).stream()
                        .map(c -> authors.getOrDefault(c.getAuthorId(), "A member") + ": "
                                + DecisionTexts.quote(c.getBody()))
                        .toList();
            }
        }
        String decidedBy = e.getDecidedBy() == null ? null
                : names(Set.of(e.getDecidedBy())).getOrDefault(e.getDecidedBy(), "The organizer");
        return DecisionTexts.Context.of(e, proposer, decidedBy, discussion);
    }

    private DecisionView view(DecisionEntry d, Map<UUID, String> names) {
        boolean ai = d.getSummarySource() == SummarySource.AI;
        boolean canSummarize = summarizable(d);
        Provenance provenance = ai
                ? new Provenance("ai", d.getSummaryModel(), d.getSummaryPrompt(), List.of(), AI_NOTE)
                : new Provenance("rules", null, null, List.of(), canSummarize
                        ? "Written by code from the recorded votes."
                        : "Written by code from the recorded votes; AI summaries are off.");
        Person decidedBy = d.getDecidedBy() == null ? null
                : new Person(d.getDecidedBy(), names.getOrDefault(d.getDecidedBy(), "Former member"));
        return new DecisionView(d.getId(), d.getSeq(), d.getKind(), d.getTitle(), d.getAlternatives(), d.getVotes(),
                d.tally(), d.getNotVoted(), d.getProposalId(), d.getReason(), d.getItineraryVersion(), d.getItemIds(),
                decidedBy, d.getDecidedAt(), d.getSummary(), provenance, pending.contains(d.getId()), canSummarize);
    }

    private Map<UUID, String> names(Set<UUID> ids) {
        return users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, User::getDisplayName));
    }
}
