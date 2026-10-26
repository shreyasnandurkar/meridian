package com.meridian.fit;

import com.meridian.ai.job.AiJob;
import com.meridian.ai.job.AiJobService;
import com.meridian.fit.FitScorer.FitMember;
import com.meridian.fit.FitScorer.MemberFit;
import com.meridian.fit.FitScorer.Verdict;
import com.meridian.fit.GroupFitModels.Contribution;
import com.meridian.fit.GroupFitModels.GroupFit;
import com.meridian.fit.GroupFitModels.ItemFit;
import com.meridian.fit.GroupFitModels.Row;
import com.meridian.fit.TripGroupLoader.TripGroup;
import com.meridian.place.Place;
import com.meridian.place.PlaceRepository;
import com.meridian.shortlist.Reaction;
import com.meridian.shortlist.ReactionRepository;
import com.meridian.shortlist.ShortlistItem;
import com.meridian.shortlist.ShortlistItemRepository;
import com.meridian.trip.TripAccess;
import com.meridian.trip.TripMember;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The Group fit screen (spec §5.4): matrix, contributions, per-item fit and A3 trade-off text. */
@Service
public class GroupFitService {

    private final TripAccess access;
    private final TripGroupLoader loader;
    private final ShortlistItemRepository items;
    private final ReactionRepository reactions;
    private final PlaceRepository places;
    private final TouristTrapScorer trapScorer;
    private final TradeoffWriter writer;
    private final AiJobService jobs;

    public GroupFitService(TripAccess access, TripGroupLoader loader, ShortlistItemRepository items,
            ReactionRepository reactions, PlaceRepository places, TouristTrapScorer trapScorer, TradeoffWriter writer,
            AiJobService jobs) {
        this.access = access;
        this.loader = loader;
        this.items = items;
        this.reactions = reactions;
        this.places = places;
        this.trapScorer = trapScorer;
        this.writer = writer;
        this.jobs = jobs;
    }

    @Transactional(readOnly = true)
    public GroupFit get(UUID tripId, UUID userId) {
        access.requireMember(tripId, userId);
        TripGroup group = loader.load(tripId);
        String currency = group.trip().getCurrency();
        List<Row> rows = GroupFitCalculator.rows(group.members(), userId, currency);
        List<ShortlistItem> list = items.findByTripIdOrderByCreatedAtDesc(tripId);
        List<Reaction> rs = list.isEmpty() ? List.of()
                : reactions.findByIdItemIdIn(list.stream().map(ShortlistItem::getId).toList());
        return new GroupFit(
                contributions(group, list, rs),
                rows,
                GroupFitCalculator.counts(rows),
                GroupFitCalculator.sharedBudget(group.members(), currency),
                itemFits(group.fitMembers(), list, rs),
                writer.current(rows),
                writer.aiEnabled());
    }

    /** Group-fit score per shortlist item (null when nobody can be scored), for code that ranks places. */
    @Transactional(readOnly = true)
    public Map<UUID, Integer> scores(UUID tripId) {
        TripGroup group = loader.load(tripId);
        List<ShortlistItem> list = items.findByTripIdOrderByCreatedAtDesc(tripId);
        List<Reaction> rs = list.isEmpty() ? List.of()
                : reactions.findByIdItemIdIn(list.stream().map(ShortlistItem::getId).toList());
        Map<UUID, Integer> out = new HashMap<>();
        itemFits(group.fitMembers(), list, rs).forEach(f -> out.put(f.itemId(), f.score()));
        return out;
    }

    /** A3 as an async job: phrase the current trade-offs (cached unless {@code regenerate}). */
    public AiJob startTradeoffs(UUID tripId, UUID userId, boolean regenerate) {
        access.requireMember(tripId, userId);
        TripGroup group = loader.load(tripId);
        List<Row> rows = GroupFitCalculator.rows(group.members(), userId, group.trip().getCurrency());
        return jobs.submit(userId, tripId, TradeoffWriter.FEATURE, ctx -> {
            ctx.progress(10, "Comparing everyone's preferences");
            var summary = writer.generate(rows, regenerate, ctx::progress);
            ctx.progress(100, "Trade-offs ready");
            return summary;
        });
    }

    List<Contribution> contributions(TripGroup group, List<ShortlistItem> list, List<Reaction> rs) {
        Map<UUID, Long> added = list.stream().collect(Collectors.groupingBy(ShortlistItem::getAddedBy,
                Collectors.counting()));
        Map<UUID, Long> reacted = rs.stream().collect(Collectors.groupingBy(Reaction::getUserId, Collectors.counting()));
        Map<UUID, TripMember> roles = group.memberships().stream()
                .collect(Collectors.toMap(TripMember::getUserId, Function.identity()));
        return group.members().stream().map(m -> {
            UUID id = m.fit().userId();
            int reactions = reacted.getOrDefault(id, 0L).intValue();
            List<String> missing = new ArrayList<>();
            if (m.fit().profile() == null) missing.add("Travel profile");
            if (!m.hasBudget()) missing.add("Trip budget");
            if (reactions < list.size()) {
                int left = list.size() - reactions;
                missing.add("React to " + left + (left == 1 ? " place" : " places"));
            }
            return new Contribution(id, m.fit().displayName(), roles.get(id).isOrganizer(),
                    m.fit().profile() != null, m.hasBudget(), added.getOrDefault(id, 0L).intValue(), reactions,
                    list.size(), missing);
        }).toList();
    }

    /** Group-fit score for every shortlist item, best first. */
    List<ItemFit> itemFits(List<FitMember> members, List<ShortlistItem> list, List<Reaction> rs) {
        if (list.isEmpty()) return List.of();
        Map<UUID, Place> byId = places.findAllById(list.stream().map(ShortlistItem::getPlaceId)
                        .filter(Objects::nonNull).toList()).stream()
                .collect(Collectors.toMap(Place::getId, Function.identity()));
        Map<UUID, Map<UUID, Reaction>> reactionsByItem = rs.stream().collect(Collectors.groupingBy(
                Reaction::getItemId, Collectors.toMap(Reaction::getUserId, Function.identity())));
        return list.stream().map(item -> {
            Place place = item.getPlaceId() == null ? null : byId.get(item.getPlaceId());
            PlaceSignals signals = place != null ? PlaceSignals.of(place)
                    : PlaceSignals.titled(item.getTitle(), item.getCategory());
            TouristTrapScorer.Assessment trap = trapScorer.assess(signals);
            Map<UUID, Reaction> itemReactions = reactionsByItem.getOrDefault(item.getId(), Map.of());
            List<MemberFit> fits = members.stream().map(m -> {
                Reaction r = itemReactions.get(m.userId());
                return FitScorer.score(m, signals, trap, r == null ? null : r.getValue(),
                        r == null ? null : r.getReason(), null);
            }).toList();
            return new ItemFit(item.getId(), signals.name(), GroupFitCalculator.groupScore(fits),
                    count(fits, Verdict.SATISFIES), count(fits, Verdict.MISSES), count(fits, Verdict.UNKNOWN), fits,
                    GroupFitCalculator.itemSummary(fits));
        }).sorted(Comparator.comparing(ItemFit::score, Comparator.nullsLast(Comparator.reverseOrder()))).toList();
    }

    private static int count(List<MemberFit> fits, Verdict v) {
        return (int) fits.stream().filter(f -> f.verdict() == v).count();
    }
}
