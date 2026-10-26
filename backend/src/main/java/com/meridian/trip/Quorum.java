package com.meridian.trip;

/** Quorum rule (spec §5.8): a simple majority of members unless the trip sets a minimum number of approvals. */
public final class Quorum {

    private Quorum() {}

    public static int requiredApprovals(int memberCount, Integer minApprovals) {
        if (memberCount <= 0) return 1;
        if (minApprovals != null) return Math.max(1, Math.min(minApprovals, memberCount));
        return memberCount / 2 + 1;
    }

    public static String describe(int memberCount, Integer minApprovals) {
        int required = requiredApprovals(memberCount, minApprovals);
        String rule = minApprovals == null ? "Majority" : "Custom";
        return rule + ": " + required + " of " + memberCount + " members must approve";
    }
}
