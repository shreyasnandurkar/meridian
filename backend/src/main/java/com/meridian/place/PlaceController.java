package com.meridian.place;

import com.meridian.common.CurrentUser;
import com.meridian.place.PlaceDtos.PlaceResults;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/trips/{tripId}/places")
public class PlaceController {

    private final PlaceService places;

    public PlaceController(PlaceService places) {
        this.places = places;
    }

    /** Named places of one category around the trip destination, nearest first. */
    @GetMapping("/nearby")
    public PlaceResults nearby(@PathVariable UUID tripId, @RequestParam PlaceCategory category,
            @RequestParam(required = false) Integer radiusKm, @AuthenticationPrincipal Jwt jwt) {
        return places.nearby(tripId, CurrentUser.id(jwt), category, radiusKm);
    }

    /** Free-text place search limited to the trip's area. */
    @GetMapping("/search")
    public PlaceResults search(@PathVariable UUID tripId, @RequestParam @Size(max = 200) String q,
            @AuthenticationPrincipal Jwt jwt) {
        return places.search(tripId, CurrentUser.id(jwt), q);
    }
}
