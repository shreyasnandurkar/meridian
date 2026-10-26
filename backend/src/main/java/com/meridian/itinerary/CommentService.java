package com.meridian.itinerary;

import com.meridian.common.ApiException;
import com.meridian.realtime.TripEvents;
import com.meridian.itinerary.ItineraryDtos.CommentRequest;
import com.meridian.itinerary.ItineraryDtos.CommentView;
import com.meridian.shortlist.ShortlistDtos.Person;
import com.meridian.shortlist.ShortlistItemRepository;
import com.meridian.trip.TripAccess;
import com.meridian.trip.TripMember;
import com.meridian.user.User;
import com.meridian.user.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Flat comments on shortlisted places and on proposals (spec §5.7). Trips are small, so the tab loads them all. */
@Service
public class CommentService {

    private final TripAccess access;
    private final CommentRepository comments;
    private final ShortlistItemRepository shortlist;
    private final ProposalRepository proposals;
    private final UserRepository users;
    private final TripEvents events;

    public CommentService(TripAccess access, CommentRepository comments, ShortlistItemRepository shortlist,
            ProposalRepository proposals, UserRepository users, TripEvents events) {
        this.access = access;
        this.comments = comments;
        this.shortlist = shortlist;
        this.proposals = proposals;
        this.users = users;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<CommentView> list(UUID tripId, UUID userId) {
        TripMember me = access.requireMember(tripId, userId);
        List<Comment> list = comments.findByTripIdOrderByCreatedAt(tripId);
        Map<UUID, String> names = users.findAllById(list.stream().map(Comment::getAuthorId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, User::getDisplayName));
        return list.stream().map(c -> view(c, me, names.getOrDefault(c.getAuthorId(), "Former member"))).toList();
    }

    @Transactional
    public CommentView add(UUID tripId, UUID userId, CommentRequest req) {
        TripMember me = access.requireMember(tripId, userId);
        boolean exists = switch (req.targetType()) {
            case SHORTLIST_ITEM -> shortlist.findByIdAndTripId(req.targetId(), tripId).isPresent();
            case PROPOSAL -> proposals.findByIdAndTripId(req.targetId(), tripId).isPresent();
        };
        if (!exists) throw ApiException.notFound("Nothing to comment on");
        Comment saved = comments.save(new Comment(tripId, req.targetType(), req.targetId(), userId, req.body()));
        String name = users.findById(userId).map(User::getDisplayName).orElse("You");
        events.publish(tripId, TripEvents.Type.COMMENTS, userId);
        return view(saved, me, name);
    }

    /** Authors can delete their own comments; the organizer can delete any. */
    @Transactional
    public void delete(UUID tripId, UUID userId, UUID commentId) {
        TripMember me = access.requireMember(tripId, userId);
        Comment c = comments.findByIdAndTripId(commentId, tripId)
                .orElseThrow(() -> ApiException.notFound("Comment not found"));
        if (!canDelete(c, me)) throw ApiException.forbidden("You can only delete your own comments");
        comments.delete(c);
        events.publish(tripId, TripEvents.Type.COMMENTS, userId);
    }

    private static boolean canDelete(Comment c, TripMember me) {
        return c.getAuthorId().equals(me.getUserId()) || me.isOrganizer();
    }

    private static CommentView view(Comment c, TripMember me, String authorName) {
        return new CommentView(c.getId(), c.getTargetType(), c.getTargetId(), new Person(c.getAuthorId(), authorName),
                c.getBody(), c.getCreatedAt(), canDelete(c, me));
    }
}
