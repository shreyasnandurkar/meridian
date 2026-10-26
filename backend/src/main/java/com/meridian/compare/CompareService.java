package com.meridian.compare;

import com.meridian.common.ApiException;
import com.meridian.compare.CompareDtos.ComparisonRequest;
import com.meridian.compare.CompareDtos.ComparisonUpdate;
import com.meridian.compare.CompareDtos.ComparisonView;
import com.meridian.compare.CompareDtos.DraftView;
import com.meridian.compare.CompareDtos.RowRequest;
import com.meridian.compare.CompareDtos.RowView;
import com.meridian.link.LinkPreviewer;
import com.meridian.link.UrlGuard;
import com.meridian.shortlist.ShortlistDtos.Person;
import com.meridian.trip.TripAccess;
import com.meridian.trip.TripMember;
import com.meridian.user.User;
import com.meridian.user.UserRepository;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Compare tab (spec §5.6). Every price carries its currency, its source and when it was checked; prices older
 * than {@link #STALE_AFTER} are flagged. Rows are ordered by price only, never by the sponsored flag, and booking is
 * always a link out to the provider.
 */
@Service
public class CompareService {

    static final Duration STALE_AFTER = Duration.ofHours(24);

    private final OptionComparisonRepository comparisons;
    private final OptionRowRepository rows;
    private final UserRepository users;
    private final TripAccess access;
    private final LinkPreviewer previewer;
    private final List<ProviderAdapter> adapters;
    private final Clock clock;

    @Autowired
    public CompareService(OptionComparisonRepository comparisons, OptionRowRepository rows, UserRepository users,
            TripAccess access, LinkPreviewer previewer, List<ProviderAdapter> adapters) {
        this(comparisons, rows, users, access, previewer, adapters, Clock.systemUTC());
    }

    CompareService(OptionComparisonRepository comparisons, OptionRowRepository rows, UserRepository users,
            TripAccess access, LinkPreviewer previewer, List<ProviderAdapter> adapters, Clock clock) {
        this.comparisons = comparisons;
        this.rows = rows;
        this.users = users;
        this.access = access;
        this.previewer = previewer;
        this.adapters = adapters;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ComparisonView> list(UUID tripId, UUID userId) {
        TripMember me = access.requireMember(tripId, userId);
        return views(comparisons.findByTripIdOrderByCreatedAt(tripId), me);
    }

    @Transactional
    public ComparisonView create(UUID tripId, UUID userId, ComparisonRequest req) {
        TripMember me = access.requireMember(tripId, userId);
        OptionComparison c = comparisons.save(new OptionComparison(tripId, req.kind(), req.title().strip(), userId));
        return views(List.of(c), me).getFirst();
    }

    @Transactional
    public ComparisonView update(UUID tripId, UUID comparisonId, UUID userId, ComparisonUpdate req) {
        TripMember me = access.requireMember(tripId, userId);
        OptionComparison c = requireComparison(tripId, comparisonId);
        requireEditor(c.getCreatedBy(), me);
        c.edit(req.kind(), req.title());
        return views(List.of(c), me).getFirst();
    }

    @Transactional
    public void delete(UUID tripId, UUID comparisonId, UUID userId) {
        TripMember me = access.requireMember(tripId, userId);
        OptionComparison c = requireComparison(tripId, comparisonId);
        requireEditor(c.getCreatedBy(), me);
        comparisons.delete(c);
    }

    @Transactional
    public ComparisonView addRow(UUID tripId, UUID comparisonId, UUID userId, RowRequest req) {
        TripMember me = access.requireMember(tripId, userId);
        OptionComparison c = requireComparison(tripId, comparisonId);
        String adapter = req.adapter() == null || req.adapter().isBlank() ? "manual" : req.adapter().strip();
        OptionRow row = new OptionRow(c.getId(), adapter, userId);
        apply(row, req);
        rows.save(row);
        return views(List.of(c), me).getFirst();
    }

    @Transactional
    public ComparisonView updateRow(UUID tripId, UUID comparisonId, UUID rowId, UUID userId, RowRequest req) {
        TripMember me = access.requireMember(tripId, userId);
        OptionComparison c = requireComparison(tripId, comparisonId);
        OptionRow row = requireRow(c, rowId);
        requireEditor(row.getAddedBy(), me);
        boolean priceChanged = !Objects.equals(normalize(row.getPrice()), normalize(req.price()))
                || !Objects.equals(row.getCurrency(), req.currency());
        // an unchanged price keeps its checked-at time unless a new one is given
        RowRequest effective = priceChanged || req.checkedAt() != null ? req
                : new RowRequest(req.provider(), req.title(), req.price(), req.currency(), row.getCheckedAt(),
                        req.sourceUrl(), req.sponsored(), req.notes(), req.adapter());
        apply(row, effective);
        return views(List.of(c), me).getFirst();
    }

    /** Any member can confirm a price is still what the provider shows; this resets its checked-at time. */
    @Transactional
    public ComparisonView recheck(UUID tripId, UUID comparisonId, UUID rowId, UUID userId) {
        TripMember me = access.requireMember(tripId, userId);
        OptionComparison c = requireComparison(tripId, comparisonId);
        OptionRow row = requireRow(c, rowId);
        if (row.getPrice() == null) throw ApiException.badRequest("This option has no price to re-check");
        row.setPrice(row.getPrice(), row.getCurrency(), now());
        return views(List.of(c), me).getFirst();
    }

    @Transactional
    public ComparisonView deleteRow(UUID tripId, UUID comparisonId, UUID rowId, UUID userId) {
        TripMember me = access.requireMember(tripId, userId);
        OptionComparison c = requireComparison(tripId, comparisonId);
        OptionRow row = requireRow(c, rowId);
        requireEditor(row.getAddedBy(), me);
        rows.delete(row);
        rows.flush();
        return views(List.of(c), me).getFirst();
    }

    /** Prefills a row from a pasted link via the first provider adapter that supports it. No transaction held. */
    public DraftView draft(UUID tripId, UUID userId, String url) {
        access.requireMember(tripId, userId);
        URI uri = parseUrl(url);
        String title;
        try {
            title = previewer.preview(uri.toString()).title();
        } catch (UrlGuard.BlockedUrlException e) {
            throw ApiException.badRequest(e.getMessage());
        }
        ProviderAdapter adapter = adapters.stream().filter(a -> a.supports(uri)).findFirst()
                .orElseThrow(() -> ApiException.badRequest("This link can't be read"));
        ProviderAdapter.OptionDraft d = adapter.draft(uri, title);
        return new DraftView(d.provider(), d.title(), d.price(), d.currency(), d.sourceUrl(), d.adapter());
    }

    private void apply(OptionRow row, RowRequest req) {
        if (req.price() != null && (req.currency() == null || req.currency().isBlank())) {
            throw ApiException.badRequest("Give the currency for this price");
        }
        Instant checkedAt = req.checkedAt() != null ? req.checkedAt() : now();
        if (checkedAt.isAfter(now().plus(5, ChronoUnit.MINUTES))) {
            throw ApiException.badRequest("A price can't be checked in the future");
        }
        String url = req.sourceUrl() == null || req.sourceUrl().isBlank() ? null : parseUrl(req.sourceUrl()).toString();
        row.setDetails(req.provider().strip(), blankToNull(req.title()), url, req.sponsored(), blankToNull(req.notes()));
        row.setPrice(req.price(), req.currency(), checkedAt);
    }

    private List<ComparisonView> views(List<OptionComparison> list, TripMember me) {
        if (list.isEmpty()) return List.of();
        Map<UUID, List<OptionRow>> byComparison = rows.findByComparisonIdIn(
                        list.stream().map(OptionComparison::getId).toList()).stream()
                .collect(Collectors.groupingBy(OptionRow::getComparisonId));
        Set<UUID> userIds = new HashSet<>();
        list.forEach(c -> userIds.add(c.getCreatedBy()));
        byComparison.values().forEach(rs -> rs.forEach(r -> userIds.add(r.getAddedBy())));
        Map<UUID, String> names = users.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName));
        Instant staleBefore = now().minus(STALE_AFTER);

        return list.stream().map(c -> {
            List<OptionRow> rs = byComparison.getOrDefault(c.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(OptionRow::getPrice, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(OptionRow::getCreatedAt))
                    .toList();
            Map<String, BigDecimal> cheapest = new HashMap<>();
            rs.stream().filter(r -> r.getPrice() != null)
                    .forEach(r -> cheapest.merge(r.getCurrency(), r.getPrice(), BigDecimal::min));
            long priced = rs.stream().filter(r -> r.getPrice() != null).count();
            List<RowView> views = rs.stream().map(r -> new RowView(r.getId(), r.getProvider(), r.getTitle(),
                    r.getPrice(), r.getCurrency(), r.getCheckedAt(),
                    r.getCheckedAt() != null && r.getCheckedAt().isBefore(staleBefore),
                    r.getSourceUrl(), r.getSourceUrl() == null ? null : URI.create(r.getSourceUrl()).getHost(),
                    r.isSponsored(), r.getNotes(), r.getAdapter(), person(r.getAddedBy(), names),
                    priced > 1 && r.getPrice() != null && r.getPrice().compareTo(cheapest.get(r.getCurrency())) == 0,
                    canEdit(r.getAddedBy(), me))).toList();
            return new ComparisonView(c.getId(), c.getKind(), c.getTitle(), person(c.getCreatedBy(), names),
                    c.getCreatedAt(), views, canEdit(c.getCreatedBy(), me));
        }).toList();
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static Person person(UUID id, Map<UUID, String> names) {
        return new Person(id, names.getOrDefault(id, "Former member"));
    }

    private static boolean canEdit(UUID owner, TripMember me) {
        return me.isOrganizer() || owner.equals(me.getUserId());
    }

    private static void requireEditor(UUID owner, TripMember me) {
        if (!canEdit(owner, me)) {
            throw ApiException.forbidden("Only the person who added this or the organizer can change it");
        }
    }

    private OptionComparison requireComparison(UUID tripId, UUID comparisonId) {
        return comparisons.findByIdAndTripId(comparisonId, tripId)
                .orElseThrow(() -> ApiException.notFound("Comparison not found"));
    }

    private OptionRow requireRow(OptionComparison c, UUID rowId) {
        return rows.findByIdAndComparisonId(rowId, c.getId())
                .orElseThrow(() -> ApiException.notFound("Option not found"));
    }

    private static URI parseUrl(String url) {
        try {
            return UrlGuard.parse(url);
        } catch (UrlGuard.BlockedUrlException e) {
            throw ApiException.badRequest(e.getMessage());
        }
    }

    private static BigDecimal normalize(BigDecimal v) {
        return v == null ? null : v.stripTrailingZeros();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
