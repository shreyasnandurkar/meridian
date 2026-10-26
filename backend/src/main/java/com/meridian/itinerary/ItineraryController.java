package com.meridian.itinerary;

import com.meridian.ai.job.AiJobView;
import com.meridian.common.CurrentUser;
import com.meridian.itinerary.ItineraryDtos.CommentRequest;
import com.meridian.itinerary.ItineraryDtos.CommentView;
import com.meridian.itinerary.ItineraryDtos.GenerateRequest;
import com.meridian.itinerary.ItineraryDtos.ItineraryView;
import com.meridian.itinerary.ItineraryDtos.ProposalView;
import com.meridian.itinerary.ItineraryDtos.ProposeRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips/{tripId}")
public class ItineraryController {

    private final ItineraryService itineraries;
    private final ProposalService proposals;
    private final CommentService comments;

    public ItineraryController(ItineraryService itineraries, ProposalService proposals, CommentService comments) {
        this.itineraries = itineraries;
        this.proposals = proposals;
        this.comments = comments;
    }

    /** 204 when the trip has no itinerary yet. */
    @GetMapping("/itinerary")
    public ResponseEntity<ItineraryView> get(@PathVariable UUID tripId, @AuthenticationPrincipal Jwt jwt) {
        return itineraries.get(tripId, CurrentUser.id(jwt)).map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    /** A4: build a draft itinerary from the shortlist in a background job (organizer). */
    @PostMapping("/itinerary/generate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AiJobView generate(@PathVariable UUID tripId, @RequestBody(required = false) GenerateRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        return AiJobView.from(itineraries.generate(tripId, CurrentUser.id(jwt), req));
    }

    /** Re-plan one day's order and times (organizer). */
    @PostMapping("/itinerary/days/{day}/regenerate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AiJobView regenerateDay(@PathVariable UUID tripId, @PathVariable int day,
            @AuthenticationPrincipal Jwt jwt) {
        return AiJobView.from(itineraries.regenerateDay(tripId, CurrentUser.id(jwt), day));
    }

    /** Move, swap, remove or add a stop directly (organizer). */
    @PostMapping("/itinerary/edits")
    public ItineraryView edit(@PathVariable UUID tripId, @Valid @RequestBody PlanEdit edit,
            @AuthenticationPrincipal Jwt jwt) {
        return itineraries.edit(tripId, CurrentUser.id(jwt), edit);
    }

    @GetMapping("/proposals")
    public List<ProposalView> proposals(@PathVariable UUID tripId, @AuthenticationPrincipal Jwt jwt) {
        return proposals.list(tripId, CurrentUser.id(jwt));
    }

    @PostMapping("/proposals")
    @ResponseStatus(HttpStatus.CREATED)
    public ProposalView propose(@PathVariable UUID tripId, @Valid @RequestBody ProposeRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        return proposals.propose(tripId, CurrentUser.id(jwt), req);
    }

    @PostMapping("/proposals/{proposalId}/withdraw")
    public ProposalView withdraw(@PathVariable UUID tripId, @PathVariable UUID proposalId,
            @AuthenticationPrincipal Jwt jwt) {
        return proposals.withdraw(tripId, CurrentUser.id(jwt), proposalId);
    }

    @GetMapping("/comments")
    public List<CommentView> comments(@PathVariable UUID tripId, @AuthenticationPrincipal Jwt jwt) {
        return comments.list(tripId, CurrentUser.id(jwt));
    }

    @PostMapping("/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentView comment(@PathVariable UUID tripId, @Valid @RequestBody CommentRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        return comments.add(tripId, CurrentUser.id(jwt), req);
    }

    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable UUID tripId, @PathVariable UUID commentId,
            @AuthenticationPrincipal Jwt jwt) {
        comments.delete(tripId, CurrentUser.id(jwt), commentId);
    }
}
