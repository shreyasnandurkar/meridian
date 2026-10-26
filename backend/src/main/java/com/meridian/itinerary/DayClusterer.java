package com.meridian.itinerary;

import com.meridian.geo.GeoMath;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Splits stops into days by geography (spec §4 A4: code does the clustering). Agglomerative clustering with complete
 * linkage and a size cap: start with one group per stop and keep merging the two groups whose farthest-apart members
 * are closest, as long as the merged group still fits in a day. Places far apart never share a day just to even out
 * day sizes, and the same shortlist always gives the same days.
 */
public final class DayClusterer {

    /** Fewer stops than this per day and we use fewer days, leaving the rest free. */
    static final int MIN_PER_DAY = 2;

    public record Clustering(List<List<Stop>> days, List<Stop> dropped) {}

    private DayClusterer() {}

    /**
     * @param days   number of trip days
     * @param perDay most stops a day can hold at the group's pace; the lowest group-fit stops beyond
     *               {@code days × perDay} are dropped
     */
    public static Clustering cluster(List<Stop> stops, int days, int perDay) {
        List<Stop> ranked = stops.stream().sorted(Comparator
                        .comparing((Stop s) -> s.fit() == null ? 50 : s.fit(), Comparator.reverseOrder())
                        .thenComparing(Stop::title).thenComparing(Stop::id))
                .toList();
        List<Stop> kept = ranked.subList(0, Math.min(days * perDay, ranked.size()));
        List<Stop> dropped = ranked.subList(kept.size(), ranked.size());

        int target = Math.min(days, (int) Math.ceil(kept.size() / (double) MIN_PER_DAY));
        List<List<Stop>> groups = merge(kept, target, perDay);
        // west to east, so day order follows the map
        groups.sort(Comparator.comparingDouble((List<Stop> g) -> g.stream().mapToDouble(Stop::lng).average().orElse(0))
                .thenComparingDouble(g -> g.stream().mapToDouble(Stop::lat).average().orElse(0)));
        List<List<Stop>> out = new ArrayList<>();
        groups.forEach(g -> out.add(List.copyOf(g)));
        while (out.size() < days) out.add(List.of());
        return new Clustering(out, List.copyOf(dropped));
    }

    private static List<List<Stop>> merge(List<Stop> stops, int target, int cap) {
        List<List<Stop>> groups = new ArrayList<>();
        stops.stream().sorted(Comparator.comparing(Stop::id)).forEach(s -> groups.add(new ArrayList<>(List.of(s))));
        while (groups.size() > Math.max(target, 1)) {
            int bestA = -1;
            int bestB = -1;
            double best = Double.MAX_VALUE;
            boolean bestFits = false;
            for (int a = 0; a < groups.size(); a++) {
                for (int b = a + 1; b < groups.size(); b++) {
                    boolean fits = groups.get(a).size() + groups.get(b).size() <= cap;
                    double d = completeLink(groups.get(a), groups.get(b));
                    // a merge that fits in a day always beats one that doesn't
                    if ((fits && !bestFits) || (fits == bestFits && d < best)) {
                        best = d;
                        bestA = a;
                        bestB = b;
                        bestFits = fits;
                    }
                }
            }
            groups.get(bestA).addAll(groups.remove(bestB));
        }
        return groups;
    }

    /** Distance between the two farthest-apart members, so merged days stay compact. */
    private static double completeLink(List<Stop> a, List<Stop> b) {
        double max = 0;
        for (Stop x : a) {
            for (Stop y : b) max = Math.max(max, GeoMath.distanceKm(x.lat(), x.lng(), y.lat(), y.lng()));
        }
        return max;
    }
}
