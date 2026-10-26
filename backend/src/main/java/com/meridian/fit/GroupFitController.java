package com.meridian.fit;

import com.meridian.ai.job.AiJobView;
import com.meridian.common.CurrentUser;
import com.meridian.fit.GroupFitModels.GroupFit;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips/{tripId}/group-fit")
public class GroupFitController {

    private final GroupFitService groupFit;

    public GroupFitController(GroupFitService groupFit) {
        this.groupFit = groupFit;
    }

    @GetMapping
    public GroupFit get(@PathVariable UUID tripId, @AuthenticationPrincipal Jwt jwt) {
        return groupFit.get(tripId, CurrentUser.id(jwt));
    }

    /** A3: phrase the trade-offs with AI in a background job. */
    @PostMapping("/tradeoffs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AiJobView tradeoffs(@PathVariable UUID tripId, @RequestParam(defaultValue = "false") boolean regenerate,
            @AuthenticationPrincipal Jwt jwt) {
        return AiJobView.from(groupFit.startTradeoffs(tripId, CurrentUser.id(jwt), regenerate));
    }
}
