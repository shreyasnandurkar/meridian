package com.meridian.decision;

import com.meridian.decision.DecisionEntry.CastVote;
import com.meridian.decision.DecisionEntry.Kind;
import com.meridian.decision.DecisionEntry.Member;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Words for decision-log entries, written by code: the template summary shown at once (and whenever AI is off or
 * fails) and the fact lines the A6 prompt is given. Pure functions, unit-tested.
 */
public final class DecisionTexts {

    static final int SUMMARY_MAX = 600;
    private static final int QUOTE_MAX = 200;

    private DecisionTexts() {}

    /**
     * Everything about a decision that the words may use.
     *
     * @param proposer  who proposed the change; null for lock and unlock
     * @param decidedBy who locked or unlocked; null for changes decided by vote
     * @param comments  "Name: text" lines from the proposal's discussion, oldest first
     */
    public record Context(Kind kind, String title, List<String> alternatives, Tally tally, List<CastVote> votes,
            List<Member> notVoted, String proposer, String decidedBy, String reason, List<String> comments) {

        static Context of(DecisionEntry e, String proposer, String decidedBy, List<String> comments) {
            return new Context(e.getKind(), e.getTitle(), e.getAlternatives(), e.tally(), e.getVotes(),
                    e.getNotVoted(), proposer, decidedBy, e.getReason(), comments);
        }
    }

    public static String title(Kind kind, String change) {
        return switch (kind) {
            case CHANGE_APPROVED, CHANGE_REJECTED -> change;
            case PLAN_LOCKED -> "Locked the itinerary (" + change + ")";
            case LOCK_OVERRIDE -> "Locked the itinerary by organizer override (" + change + ")";
            case PLAN_UNLOCKED -> "Unlocked the itinerary";
        };
    }

    public static String template(Context c) {
        Tally t = c.tally();
        String count = t.approvals() + " of " + t.members() + " approved (" + t.required() + " needed)";
        String against = names(c.votes(), Vote.Value.REJECT);
        String infavour = names(c.votes(), Vote.Value.APPROVE);
        String silent = c.notVoted().stream().map(Member::displayName).collect(Collectors.joining(", "));
        StringBuilder s = new StringBuilder();
        switch (c.kind()) {
            case CHANGE_APPROVED -> {
                s.append("Chose to ").append(lowerFirst(c.title())).append(" over keeping the plan as it was: ")
                        .append(count).append('.');
                if (!against.isEmpty()) s.append(" Voted against: ").append(against).append('.');
                if (c.reason() != null) {
                    s.append(' ').append(c.proposer()).append("'s reason: ").append(quote(c.reason())).append('.');
                }
            }
            case CHANGE_REJECTED -> {
                s.append("Kept the plan as it was instead of: ").append(c.title()).append(". ")
                        .append(t.rejections()).append(" of ").append(t.members())
                        .append(" voted against, so the ").append(t.required())
                        .append(" approvals needed could no longer be reached.");
                if (!infavour.isEmpty()) s.append(" In favour: ").append(infavour).append('.');
            }
            case PLAN_LOCKED -> {
                s.append(c.title()).append(" with ").append(count).append('.');
                if (!silent.isEmpty()) s.append(" Not voted: ").append(silent).append('.');
            }
            case LOCK_OVERRIDE -> s.append(c.decidedBy()).append(" locked the itinerary without the group's quorum (")
                    .append(t.approvals()).append(" of ").append(t.members()).append(" approved, ")
                    .append(t.required()).append(" needed). Reason: ").append(quote(c.reason())).append('.');
            case PLAN_UNLOCKED -> {
                s.append(c.decidedBy()).append(" unlocked the itinerary so it can be changed again");
                if (c.reason() != null) s.append(". Reason: ").append(quote(c.reason()));
                s.append('.');
            }
        }
        return clip(s.toString(), SUMMARY_MAX);
    }

    /** The facts A6 may use, one per line. Counts are spelled "N of M" so the model can quote them as they are. */
    public static String facts(Context c) {
        Tally t = c.tally();
        boolean plan = c.kind() == Kind.PLAN_LOCKED || c.kind() == Kind.LOCK_OVERRIDE;
        List<String> lines = new ArrayList<>();
        lines.add("decision: " + switch (c.kind()) {
            case CHANGE_APPROVED -> "change approved";
            case CHANGE_REJECTED -> "change rejected";
            case PLAN_LOCKED -> "itinerary locked";
            case LOCK_OVERRIDE -> "itinerary locked by the organizer without quorum";
            case PLAN_UNLOCKED -> "itinerary unlocked";
        });
        lines.add("change: " + c.title());
        lines.add("alternatives: " + String.join(" | ", c.alternatives()));
        if (c.proposer() != null) lines.add("proposed by: " + c.proposer());
        if (c.decidedBy() != null) lines.add((plan ? "locked" : "decided") + " by: " + c.decidedBy());
        if (c.reason() != null) {
            String who = c.kind() == Kind.LOCK_OVERRIDE ? c.decidedBy() : c.proposer();
            lines.add("reason given by " + who + ": " + quote(c.reason()));
        }
        String what = plan ? " the plan" : "";
        lines.add("quorum: " + t.required() + " of " + t.members() + " members must approve" + what);
        lines.add("approved" + what + " (" + t.approvals() + " of " + t.members() + "): "
                + orNobody(names(c.votes(), Vote.Value.APPROVE)));
        lines.add("rejected" + what + " (" + t.rejections() + " of " + t.members() + "): "
                + orNobody(names(c.votes(), Vote.Value.REJECT)));
        lines.add("did not vote: "
                + orNobody(c.notVoted().stream().map(Member::displayName).collect(Collectors.joining(", "))));
        if (!c.comments().isEmpty()) lines.add("comments: " + String.join(" | ", c.comments()));
        return String.join("\n", lines);
    }

    /** The count phrases a summary must contain, e.g. "3 of 4" (or "3/4"). */
    static List<String> countPhrases(Tally t) {
        List<String> out = new ArrayList<>();
        for (int n : new int[] {t.approvals(), t.rejections()}) {
            out.add(n + " of " + t.members());
            out.add(n + "/" + t.members());
        }
        return out;
    }

    private static String names(List<CastVote> votes, Vote.Value value) {
        return votes.stream().filter(v -> v.value() == value).map(CastVote::displayName)
                .collect(Collectors.joining(", "));
    }

    private static String orNobody(String names) {
        return names.isEmpty() ? "nobody" : names;
    }

    static String quote(String text) {
        return "“" + clip(text.strip().replaceAll("\\s+", " "), QUOTE_MAX) + "”";
    }

    private static String lowerFirst(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toLowerCase(Locale.ROOT) + s.substring(1);
    }

    static String clip(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1).stripTrailing() + "…";
    }
}
