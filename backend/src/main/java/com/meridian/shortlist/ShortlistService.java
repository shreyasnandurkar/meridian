package com.meridian.shortlist;

import com.meridian.common.ApiException;
import com.meridian.realtime.TripEvents;
import com.meridian.itinerary.Comment;
import com.meridian.itinerary.CommentRepository;
import com.meridian.itinerary.ItineraryItemRepository;
import com.meridian.link.LinkPreviewer;
import com.meridian.link.LinkPreviewer.LinkPreview;
import com.meridian.link.UrlGuard;
import com.meridian.place.Place;
import com.meridian.place.PlaceCategory;
import com.meridian.place.PlaceDtos.PlaceView;
import com.meridian.place.PlaceRepository;
import com.meridian.place.PlaceService;
import com.meridian.shortlist.ShortlistDtos.AddItemRequest;
import com.meridian.shortlist.ShortlistDtos.ItemView;
import com.meridian.shortlist.ShortlistDtos.LinkPreviewView;
import com.meridian.shortlist.ShortlistDtos.Person;
import com.meridian.shortlist.ShortlistDtos.ReactionRequest;
import com.meridian.shortlist.ShortlistDtos.ReactionView;
import com.meridian.shortlist.ShortlistDtos.UpdateItemRequest;
import com.meridian.trip.TripAccess;
import com.meridian.trip.TripMember;
import com.meridian.user.User;
import com.meridian.user.UserRepository;
import java.net.URI;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** The trip's shared shortlist (spec §5.3). Members add and react; only the adder or the organizer edits or removes. */
@Service
public class ShortlistService {

    private final ShortlistItemRepository items;
    private final ReactionRepository reactions;
    private final PlaceRepository places;
    private final PlaceService placeService;
    private final UserRepository users;
    private final TripAccess access;
    private final LinkPreviewer previewer;
    private final TransactionTemplate tx;
    private final ItineraryItemRepository itineraryItems;
    private final CommentRepository comments;
    private final TripEvents events;

    public ShortlistService(ShortlistItemRepository items, ReactionRepository reactions, PlaceRepository places,
            PlaceService placeService, UserRepository users, TripAccess access, LinkPreviewer previewer,
            PlatformTransactionManager txManager, ItineraryItemRepository itineraryItems,
            CommentRepository comments, TripEvents events) {
        this.items = items;
        this.reactions = reactions;
        this.places = places;
        this.placeService = placeService;
        this.users = users;
        this.access = access;
        this.previewer = previewer;
        this.tx = new TransactionTemplate(txManager);
        this.itineraryItems = itineraryItems;
        this.comments = comments;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<ItemView> list(UUID tripId, UUID userId) {
        TripMember me = access.requireMember(tripId, userId);
        return views(items.findByTripIdOrderByCreatedAtDesc(tripId), me);
    }

    /** Link titles are fetched before the transaction opens, so a slow website never holds a connection. */
    public ItemView add(UUID tripId, UUID userId, AddItemRequest req) {
        TripMember me = access.requireMember(tripId, userId);
        String reason = blankToNull(req.reason());
        String notes = blankToNull(req.notes());
        ItemView added = switch (req.kind()) {
            case PLACE -> {
                if (req.placeId() == null) throw ApiException.badRequest("Choose a place to add");
                yield tx.execute(s -> {
                    Place place = placeService.require(req.placeId());
                    items.findByTripIdAndPlaceId(tripId, place.getId()).ifPresent(this::duplicate);
                    PlaceCategory category = req.category() != null ? req.category() : place.getCategory();
                    return view(items.save(ShortlistItem.place(tripId, place.getId(), category, userId, reason,
                            notes)), me);
                });
            }
            case LINK -> {
                URI uri = parseUrl(req.url());
                String title = blankToNull(req.title());
                if (title == null) title = fetchTitle(uri);
                String finalTitle = title;
                yield tx.execute(s -> {
                    items.findByTripIdAndUrl(tripId, uri.toString()).ifPresent(this::duplicate);
                    PlaceCategory category = req.category() != null ? req.category() : PlaceCategory.OTHER;
                    return view(items.save(ShortlistItem.link(tripId, uri.toString(), finalTitle, category, userId,
                            reason, notes)), me);
                });
            }
            case PIN -> {
                String title = blankToNull(req.title());
                if (title == null) throw ApiException.badRequest("Give the pin a name");
                if (req.lat() == null || req.lng() == null) throw ApiException.badRequest("Drop the pin on the map");
                PlaceCategory category = req.category() != null ? req.category() : PlaceCategory.OTHER;
                yield tx.execute(s -> view(items.save(ShortlistItem.pin(tripId, title, req.lat(), req.lng(),
                        category, userId, reason, notes)), me));
            }
        };
        events.publish(tripId, TripEvents.Type.SHORTLIST, userId);
        return added;
    }

    @Transactional
    public ItemView update(UUID tripId, UUID itemId, UUID userId, UpdateItemRequest req) {
        TripMember me = access.requireMember(tripId, userId);
        ShortlistItem item = requireItem(tripId, itemId);
        requireEditor(item, me);
        item.edit(req.category(), req.title(), req.reason(), req.notes());
        events.publish(tripId, TripEvents.Type.SHORTLIST, userId);
        return view(item, me);
    }

    @Transactional
    public void remove(UUID tripId, UUID itemId, UUID userId) {
        TripMember me = access.requireMember(tripId, userId);
        ShortlistItem item = requireItem(tripId, itemId);
        requireEditor(item, me);
        if (itineraryItems.existsByShortlistItemId(itemId)) {
            throw ApiException.conflict("This place is on the itinerary. Take it off its day first, or propose that.");
        }
        comments.deleteByTripIdAndTargetTypeAndTargetId(tripId, Comment.Target.SHORTLIST_ITEM, itemId);
        items.delete(item);
        events.publish(tripId, TripEvents.Type.SHORTLIST, userId);
    }

    @Transactional
    public ItemView react(UUID tripId, UUID itemId, UUID userId, ReactionRequest req) {
        TripMember me = access.requireMember(tripId, userId);
        ShortlistItem item = requireItem(tripId, itemId);
        Reaction reaction = reactions.findById(new Reaction.Key(itemId, userId))
                .orElseGet(() -> new Reaction(itemId, userId));
        reaction.set(req.value(), req.reason());
        reactions.save(reaction);
        events.publish(tripId, TripEvents.Type.SHORTLIST, userId);
        return view(item, me);
    }

    @Transactional
    public ItemView clearReaction(UUID tripId, UUID itemId, UUID userId) {
        TripMember me = access.requireMember(tripId, userId);
        ShortlistItem item = requireItem(tripId, itemId);
        reactions.findById(new Reaction.Key(itemId, userId)).ifPresent(reactions::delete);
        reactions.flush();
        events.publish(tripId, TripEvents.Type.SHORTLIST, userId);
        return view(item, me);
    }

    public LinkPreviewView preview(UUID tripId, UUID userId, String url) {
        access.requireMember(tripId, userId);
        try {
            LinkPreview p = previewer.preview(url);
            return new LinkPreviewView(p.url(), p.host(), p.title());
        } catch (UrlGuard.BlockedUrlException e) {
            throw ApiException.badRequest(e.getMessage());
        }
    }

    private static URI parseUrl(String url) {
        try {
            return UrlGuard.parse(url);
        } catch (UrlGuard.BlockedUrlException e) {
            throw ApiException.badRequest(e.getMessage());
        }
    }

    /** The page title, or the host name when the page can't be read. */
    private String fetchTitle(URI uri) {
        try {
            String title = previewer.preview(uri.toString()).title();
            return title != null ? title : uri.getHost();
        } catch (UrlGuard.BlockedUrlException e) {
            throw ApiException.badRequest(e.getMessage());
        }
    }

    private void duplicate(ShortlistItem existing) {
        String by = users.findById(existing.getAddedBy()).map(User::getDisplayName).orElse("someone");
        throw ApiException.conflict("Already on the shortlist (added by " + by + ")");
    }

    private ShortlistItem requireItem(UUID tripId, UUID itemId) {
        return items.findByIdAndTripId(itemId, tripId)
                .orElseThrow(() -> ApiException.notFound("Shortlist item not found"));
    }

    private static void requireEditor(ShortlistItem item, TripMember me) {
        if (!canEdit(item, me)) {
            throw ApiException.forbidden("Only the person who added this or the organizer can change it");
        }
    }

    private static boolean canEdit(ShortlistItem item, TripMember me) {
        return me.isOrganizer() || item.getAddedBy().equals(me.getUserId());
    }

    private ItemView view(ShortlistItem item, TripMember me) {
        return views(List.of(item), me).getFirst();
    }

    private List<ItemView> views(List<ShortlistItem> list, TripMember me) {
        if (list.isEmpty()) return List.of();
        List<UUID> itemIds = list.stream().map(ShortlistItem::getId).toList();
        Map<UUID, List<Reaction>> reactionsByItem = reactions.findByIdItemIdIn(itemIds).stream()
                .collect(Collectors.groupingBy(Reaction::getItemId));
        Map<UUID, Place> placesById = places.findAllById(
                        list.stream().map(ShortlistItem::getPlaceId).filter(Objects::nonNull).toList()).stream()
                .collect(Collectors.toMap(Place::getId, Function.identity()));
        Set<UUID> userIds = new HashSet<>();
        list.forEach(i -> userIds.add(i.getAddedBy()));
        reactionsByItem.values().forEach(rs -> rs.forEach(r -> userIds.add(r.getUserId())));
        Map<UUID, String> names = users.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName));

        return list.stream().map(item -> {
            Place place = item.getPlaceId() == null ? null : placesById.get(item.getPlaceId());
            List<ReactionView> rs = reactionsByItem.getOrDefault(item.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(Reaction::getUpdatedAt))
                    .map(r -> new ReactionView(r.getUserId(), names.getOrDefault(r.getUserId(), "Former member"),
                            r.getValue(), r.getReason(), r.getUpdatedAt()))
                    .toList();
            String title = place != null ? place.getName() : item.getTitle();
            Double lat = place != null ? Double.valueOf(place.getLat()) : item.getLat();
            Double lng = place != null ? Double.valueOf(place.getLng()) : item.getLng();
            String host = item.getUrl() == null ? null : URI.create(item.getUrl()).getHost();
            return new ItemView(item.getId(), item.getKind(), title, item.getCategory(),
                    place == null ? null : PlaceView.of(place), item.getUrl(), host, lat, lng,
                    new Person(item.getAddedBy(), names.getOrDefault(item.getAddedBy(), "Former member")),
                    item.getReason(), item.getNotes(), item.getCreatedAt(), item.getUpdatedAt(), rs,
                    canEdit(item, me));
        }).toList();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
