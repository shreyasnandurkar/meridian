package com.meridian.decision;

import java.util.Collection;

/**
 * Votes counted against the quorum (spec §5.8). A vote is decided as soon as its outcome can no longer change:
 * approved once enough members approve, rejected once so many reject that the rest could not reach the quorum.
 */
public record Tally(int approvals, int rejections, int required, int members) {

    public enum Outcome { OPEN, APPROVED, REJECTED }

    public static Tally of(Collection<Vote.Value> votes, int required, int members) {
        int approve = (int) votes.stream().filter(v -> v == Vote.Value.APPROVE).count();
        return new Tally(approve, votes.size() - approve, required, members);
    }

    public Outcome outcome() {
        if (approvals >= required) return Outcome.APPROVED;
        if (members - rejections < required) return Outcome.REJECTED;
        return Outcome.OPEN;
    }

    public boolean quorumMet() {
        return approvals >= required;
    }

    /** Approvals still needed; 0 when the quorum is met. */
    public int stillNeeded() {
        return Math.max(0, required - approvals);
    }
}
