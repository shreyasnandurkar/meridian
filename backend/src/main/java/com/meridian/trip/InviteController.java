package com.meridian.trip;

import com.meridian.common.CurrentUser;
import com.meridian.trip.TripDtos.AcceptResult;
import com.meridian.trip.TripDtos.InvitePreview;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invites")
public class InviteController {

    private final InviteService invites;

    public InviteController(InviteService invites) {
        this.invites = invites;
    }

    /** Public preview (no sign-in needed). */
    @GetMapping("/{token}")
    public InvitePreview preview(@PathVariable String token) {
        return invites.preview(token);
    }

    @PostMapping("/{token}/accept")
    public AcceptResult accept(@PathVariable String token, @AuthenticationPrincipal Jwt jwt) {
        return invites.accept(token, CurrentUser.id(jwt));
    }
}
