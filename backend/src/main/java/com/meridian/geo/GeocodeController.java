package com.meridian.geo;

import com.meridian.geo.GeocodingClient.GeoResult;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
public class GeocodeController {

    private final GeocodingClient geocoder;

    public GeocodeController(GeocodingClient geocoder) {
        this.geocoder = geocoder;
    }

    /** Destination autocomplete. Queries shorter than three characters return nothing. */
    @GetMapping("/api/geo/search")
    public List<GeoResult> search(@RequestParam @Size(max = 200) String q) {
        if (q.strip().length() < 3) return List.of();
        return geocoder.search(q);
    }
}
