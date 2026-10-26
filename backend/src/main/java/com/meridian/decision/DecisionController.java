package com.meridian.decision;

import com.meridian.ai.job.AiJobView;
import com.meridian.common.CurrentUser;
import com.meridian.decision.DecisionDtos.DecisionView;
import com.meridian.decision.DecisionDtos.GateView;
import com.meridian.decision.DecisionDtos.ReasonRequest;
import com.meridian.decision.DecisionDtos.VoteRequest;
import com.meridian.itinerary.ItineraryDtos.ProposalView;
import com.meridian.itinerary.ProposalService;
import com.meridian.trip.TripDtos.QuorumRequest;
import com.meridian.trip.TripDtos.TripDetail;
import com.meridian.trip.TripService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Votes, the consensus gate, the decision log and the trip's quorum rule (spec §5.8). */
@RestController
@RequestMapping("/api/trips/{tripId}")
public class DecisionController {

    private final ConsensusService consensus;
    private final ProposalService proposals;
    private final DecisionService decisions;
    private final TripService trips;

    public DecisionController(ConsensusService consensus, ProposalService proposals, DecisionService decisions,
            TripService trips) {
        this.consensus = consensus;
        this.proposals = proposals;
        this.decisions = decisions;
        this.trips = trips;
    }

    /** Approve or reject; when this vote decides it, the change is applied or closed before the response. */
    @PutMapping("/proposals/{proposalId}/vote")
    public ProposalView vote(@PathVariable UUID tripId, @PathVariable UUID proposalId,
            @Valid @RequestBody VoteRequest req, @AuthenticationPrincipal Jwt jwt) {
        UUID me = CurrentUser.id(jwt);
        consensus.vote(tripId, me, proposalId, req.value());
        return proposals.get(tripId, me, proposalId);
    }

    @DeleteMapping("/proposals/{proposalId}/vote")
    public ProposalView clearVote(@PathVariable UUID tripId, @PathVariable UUID proposalId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID me = CurrentUser.id(jwt);
        consensus.clearVote(tripId, me, proposalId);
        return proposals.get(tripId, me, proposalId);
    }

    @GetMapping("/itinerary/approval")
    public GateView gate(@PathVariable UUID tripId, @AuthenticationPrincipal Jwt jwt) {
        return consensus.gate(tripId, CurrentUser.id(jwt));
    }

    @PutMapping("/itinerary/approval")
    public GateView votePlan(@PathVariable UUID tripId, @Valid @RequestBody VoteRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        UUID me = CurrentUser.id(jwt);
        consensus.votePlan(tripId, me, req.value());
        return consensus.gate(tripId, me);
    }

    @DeleteMapping("/itinerary/approval")
    public GateView clearPlanVote(@PathVariable UUID tripId, @AuthenticationPrincipal Jwt jwt) {
        UUID me = CurrentUser.id(jwt);
        consensus.clearPlanVote(tripId, me);
        return consensus.gate(tripId, me);
    }

    /** Organizer: lock with the quorum's approval, or without it by giving a reason (logged as an override). */
    @PostMapping("/itinerary/lock")
    public GateView lock(@PathVariable UUID tripId, @Valid @RequestBody(required = false) ReasonRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        UUID me = CurrentUser.id(jwt);
        consensus.lock(tripId, me, req == null ? null : req.reason());
        return consensus.gate(tripId, me);
    }

    @PostMapping("/itinerary/unlock")
    public GateView unlock(@PathVariable UUID tripId, @Valid @RequestBody(required = false) ReasonRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        UUID me = CurrentUser.id(jwt);
        consensus.unlock(tripId, me, req == null ? null : req.reason());
        return consensus.gate(tripId, me);
    }

    @GetMapping("/decisions")
    public List<DecisionView> decisions(@PathVariable UUID tripId, @AuthenticationPrincipal Jwt jwt) {
        return decisions.list(tripId, CurrentUser.id(jwt));
    }

    /** A6 again for one entry, in a background job. */
    @PostMapping("/decisions/{decisionId}/summary")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AiJobView summarize(@PathVariable UUID tripId, @PathVariable UUID decisionId,
            @AuthenticationPrincipal Jwt jwt) {
        return AiJobView.from(decisions.summarize(tripId, CurrentUser.id(jwt), decisionId));
    }

    @PostMapping("/decisions/{decisionId}/summary/dismiss")
    public DecisionView dismissSummary(@PathVariable UUID tripId, @PathVariable UUID decisionId,
            @AuthenticationPrincipal Jwt jwt) {
        return decisions.dismissSummary(tripId, CurrentUser.id(jwt), decisionId);
    }

    /** Organizer: approvals a vote needs ({@code null} = simple majority). Open votes are settled by the new rule. */
    @PutMapping("/quorum")
    public TripDetail quorum(@PathVariable UUID tripId, @Valid @RequestBody QuorumRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        UUID me = CurrentUser.id(jwt);
        TripDetail detail = trips.updateQuorum(tripId, me, req.minApprovals());
        consensus.settleOpen(tripId, me);
        return detail;
    }
}
