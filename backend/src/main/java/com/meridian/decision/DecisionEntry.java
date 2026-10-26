package com.meridian.decision;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One entry of the append-only decision log (spec §5.8): what was decided, the alternatives, the votes as they
 * stood, and a summary. A database trigger rejects deletes and any change except to the summary, which code writes
 * from a template at once and the AI (A6) may rewrite after the vote has closed.
 */
@Entity
@Table(name = "decision_log")
public class DecisionEntry {

    public enum Kind { CHANGE_APPROVED, CHANGE_REJECTED, PLAN_LOCKED, LOCK_OVERRIDE, PLAN_UNLOCKED }

    public enum SummarySource { AI, RULES }

    public record CastVote(UUID userId, String displayName, Vote.Value value, Instant at) {}

    public record Member(UUID userId, String displayName) {}

    /** Everything about a decision except where it sits in the log. */
    public record Draft(Kind kind, String title, List<String> alternatives, List<CastVote> votes, Tally tally,
            List<Member> notVoted, UUID proposalId, String reason, Integer itineraryVersion, List<UUID> itemIds,
            UUID decidedBy, String summary) {}

    @Id
    private UUID id;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Column(nullable = false)
    private int seq;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;

    @Column(nullable = false)
    private String title;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> alternatives;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<CastVote> votes;

    @Column(nullable = false)
    private int approvals;

    @Column(nullable = false)
    private int rejections;

    @Column(nullable = false)
    private int required;

    @Column(name = "member_count", nullable = false)
    private int memberCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "not_voted", nullable = false)
    private List<Member> notVoted;

    @Column(name = "proposal_id")
    private UUID proposalId;

    private String reason;

    @Column(name = "itinerary_version")
    private Integer itineraryVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "item_ids", nullable = false)
    private List<UUID> itemIds;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    @Column(nullable = false)
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(name = "summary_source", nullable = false)
    private SummarySource summarySource;

    @Column(name = "summary_model")
    private String summaryModel;

    @Column(name = "summary_prompt")
    private String summaryPrompt;

    @Column(name = "summary_at", nullable = false)
    private Instant summaryAt;

    protected DecisionEntry() {}

    public DecisionEntry(UUID tripId, int seq, Draft d) {
        this.id = UUID.randomUUID();
        this.tripId = tripId;
        this.seq = seq;
        this.kind = d.kind();
        this.title = d.title();
        this.alternatives = List.copyOf(d.alternatives());
        this.votes = List.copyOf(d.votes());
        this.approvals = d.tally().approvals();
        this.rejections = d.tally().rejections();
        this.required = d.tally().required();
        this.memberCount = d.tally().members();
        this.notVoted = List.copyOf(d.notVoted());
        this.proposalId = d.proposalId();
        this.reason = d.reason();
        this.itineraryVersion = d.itineraryVersion();
        this.itemIds = List.copyOf(d.itemIds());
        this.decidedBy = d.decidedBy();
        this.decidedAt = Instant.now();
        this.summary = d.summary();
        this.summarySource = SummarySource.RULES;
        this.summaryAt = decidedAt;
    }

    /** The only change the log allows after insert. */
    public void summarized(String summary, SummarySource source, String model, String prompt) {
        this.summary = summary;
        this.summarySource = source;
        this.summaryModel = model;
        this.summaryPrompt = prompt;
        this.summaryAt = Instant.now();
    }

    public Tally tally() {
        return new Tally(approvals, rejections, required, memberCount);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTripId() {
        return tripId;
    }

    public int getSeq() {
        return seq;
    }

    public Kind getKind() {
        return kind;
    }

    public String getTitle() {
        return title;
    }

    public List<String> getAlternatives() {
        return alternatives;
    }

    public List<CastVote> getVotes() {
        return votes;
    }

    public List<Member> getNotVoted() {
        return notVoted;
    }

    public UUID getProposalId() {
        return proposalId;
    }

    public String getReason() {
        return reason;
    }

    public Integer getItineraryVersion() {
        return itineraryVersion;
    }

    public List<UUID> getItemIds() {
        return itemIds;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getSummary() {
        return summary;
    }

    public SummarySource getSummarySource() {
        return summarySource;
    }

    public String getSummaryModel() {
        return summaryModel;
    }

    public String getSummaryPrompt() {
        return summaryPrompt;
    }

    public Instant getSummaryAt() {
        return summaryAt;
    }
}
