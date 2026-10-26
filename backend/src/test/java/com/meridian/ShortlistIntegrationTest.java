package com.meridian;

import static com.meridian.FakePlacesClient.place;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.meridian.place.PlaceCategory;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class ShortlistIntegrationTest extends IntegrationTestBase {

    @Autowired
    FakePlacesClient osm;

    @Autowired
    JdbcTemplate jdbc;

    private double lat;
    private double lng;

    @BeforeEach
    void freshArea() {
        // every test gets its own spot on the map so place caches never leak between tests
        lat = ThreadLocalRandom.current().nextDouble(-60, 60);
        lng = ThreadLocalRandom.current().nextDouble(-170, 170);
        osm.reset(List.of(
                place("node/" + UUID.randomUUID(), "Reis Magos Fort", PlaceCategory.SIGHTS, lat + 0.01, lng),
                place("node/" + UUID.randomUUID(), "Viva Panjim", PlaceCategory.FOOD, lat, lng + 0.01)));
    }

    private String createTrip(TestUser organizer) throws Exception {
        String trip = String.format(Locale.ROOT, """
                {"name":"Goa","destinationName":"Goa, India","destinationLat":%f,"destinationLng":%f,
                 "startDate":"2026-12-10","endDate":"2026-12-14","currency":"INR"}""", lat, lng);
        return read(mvc.perform(post("/api/trips").header("Authorization", organizer.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(trip))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private void join(TestUser organizer, TestUser member, String tripId) throws Exception {
        String token = read(mvc.perform(post("/api/trips/{id}/invites", tripId)
                        .header("Authorization", organizer.bearer()))
                .andReturn().getResponse().getContentAsString()).get("token").asText();
        mvc.perform(post("/api/invites/{token}/accept", token).header("Authorization", member.bearer()))
                .andExpect(status().isOk());
    }

    private ResultActions as(TestUser user, MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(req.header("Authorization", user.bearer()));
    }

    private ResultActions addItem(TestUser user, String tripId, String body) throws Exception {
        return as(user, post("/api/trips/{id}/shortlist", tripId).contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private JsonNode search(TestUser user, String tripId, String q) throws Exception {
        return read(as(user, get("/api/trips/{id}/places/search", tripId).param("q", q))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void placeResultsAreCachedAndServedStaleWhenOpenStreetMapIsDown() throws Exception {
        TestUser asha = newUser("Asha");
        String tripId = createTrip(asha);

        JsonNode first = search(asha, tripId, "fort");
        assertThat(first.get("source").asText()).isEqualTo("OpenStreetMap");
        assertThat(first.get("stale").asBoolean()).isFalse();
        assertThat(first.get("fetchedAt").isNull()).isFalse();
        assertThat(first.at("/places/0/name").asText()).isEqualTo("Reis Magos Fort");
        assertThat(first.at("/places/0/sourceUrl").asText()).startsWith("https://www.openstreetmap.org/node/");

        // same query again (different spacing and case): answered from the cache
        search(asha, tripId, "  FORT ");
        assertThat(osm.calls.get()).isEqualTo(1);

        // an expired entry is refetched; if OpenStreetMap is down, the old answer is served and marked stale
        jdbc.update("update place_search_cache set fetched_at = now() - interval '8 days' where cache_key like ?",
                "search:fort:%" + String.format(Locale.ROOT, "%.3f", lat) + "%");
        osm.down = true;
        JsonNode stale = search(asha, tripId, "fort");
        assertThat(stale.get("stale").asBoolean()).isTrue();
        assertThat(stale.at("/places/0/name").asText()).isEqualTo("Reis Magos Fort");
        assertThat(osm.calls.get()).isEqualTo(2);

        // nothing cached for this query: stored places inside the radius are the fallback
        as(asha, get("/api/trips/{id}/places/nearby", tripId).param("category", "FOOD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stale").value(true))
                .andExpect(jsonPath("$.places.length()").value(1))
                .andExpect(jsonPath("$.places[0].name").value("Viva Panjim"));

        // and with nothing stored at all, a clear 502
        as(asha, get("/api/trips/{id}/places/search", tripId).param("q", "something new"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("drop a pin")));
    }

    @Test
    void itemsAreAddedThreeWaysWithAttributionAndNoDuplicates() throws Exception {
        TestUser asha = newUser("Asha");
        String tripId = createTrip(asha);
        String placeId = search(asha, tripId, "fort").at("/places/0/id").asText();

        addItem(asha, tripId, """
                {"kind":"PLACE","placeId":"%s","reason":"Sunset over the Mandovi"}""".formatted(placeId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Reis Magos Fort"))
                .andExpect(jsonPath("$.category").value("SIGHTS"))
                .andExpect(jsonPath("$.place.source").value("osm"))
                .andExpect(jsonPath("$.place.fetchedAt").exists())
                .andExpect(jsonPath("$.addedBy.displayName").value("Asha"))
                .andExpect(jsonPath("$.reason").value("Sunset over the Mandovi"))
                .andExpect(jsonPath("$.canEdit").value(true));
        addItem(asha, tripId, """
                {"kind":"LINK","url":"https://example.com/kayak","title":"Mangrove kayaking",
                 "category":"ACTIVITIES"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.host").value("example.com"))
                .andExpect(jsonPath("$.title").value("Mangrove kayaking"));
        addItem(asha, tripId, """
                {"kind":"PIN","title":"Quiet cove","lat":%f,"lng":%f,"notes":"Ask a fisherman for the path"}"""
                .formatted(lat, lng))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("OTHER"))
                .andExpect(jsonPath("$.notes").value("Ask a fisherman for the path"));

        as(asha, get("/api/trips/{id}/shortlist", tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].kind").value("PIN"));

        addItem(asha, tripId, "{\"kind\":\"PLACE\",\"placeId\":\"%s\"}".formatted(placeId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Already on the shortlist (added by Asha)"));
        addItem(asha, tripId, "{\"kind\":\"LINK\",\"url\":\"https://example.com/kayak\",\"title\":\"Again\"}")
                .andExpect(status().isConflict());
        addItem(asha, tripId, "{\"kind\":\"LINK\",\"url\":\"ftp://example.com/x\",\"title\":\"x\"}")
                .andExpect(status().isBadRequest());
        addItem(asha, tripId, "{\"kind\":\"PIN\",\"title\":\"Nowhere\"}")
                .andExpect(status().isBadRequest());
        addItem(asha, tripId, "{\"kind\":\"PLACE\",\"placeId\":\"%s\"}".formatted(UUID.randomUUID()))
                .andExpect(status().isBadRequest());
        as(asha, get("/api/trips/{id}/link-preview", tripId).param("url", "http://localhost:8080/api/health"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reactionsUpsertAndOnlyAddersOrOrganizersEdit() throws Exception {
        TestUser asha = newUser("Asha");
        TestUser ben = newUser("Ben");
        TestUser outsider = newUser("Olu");
        String tripId = createTrip(asha);
        join(asha, ben, tripId);

        String ashaItem = read(addItem(asha, tripId, """
                {"kind":"PIN","title":"Spice farm","lat":%f,"lng":%f}""".formatted(lat, lng))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        String benItem = read(addItem(ben, tripId, """
                {"kind":"PIN","title":"Night market","lat":%f,"lng":%f,"reason":"Street food"}"""
                .formatted(lat, lng))
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        // one reaction per member, updated in place
        as(ben, put("/api/trips/{id}/shortlist/{item}/reaction", tripId, ashaItem)
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"LOVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reactions.length()").value(1));
        as(ben, put("/api/trips/{id}/shortlist/{item}/reaction", tripId, ashaItem)
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"NO_WAY\",\"reason\":\"Too hot\"}"))
                .andExpect(jsonPath("$.reactions.length()").value(1))
                .andExpect(jsonPath("$.reactions[0].displayName").value("Ben"))
                .andExpect(jsonPath("$.reactions[0].value").value("NO_WAY"))
                .andExpect(jsonPath("$.reactions[0].reason").value("Too hot"))
                .andExpect(jsonPath("$.canEdit").value(false));
        as(asha, put("/api/trips/{id}/shortlist/{item}/reaction", tripId, ashaItem)
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"OK\"}"))
                .andExpect(jsonPath("$.reactions.length()").value(2));
        as(ben, delete("/api/trips/{id}/shortlist/{item}/reaction", tripId, ashaItem))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reactions.length()").value(1))
                .andExpect(jsonPath("$.reactions[0].displayName").value("Asha"));
        as(ben, put("/api/trips/{id}/shortlist/{item}/reaction", tripId, ashaItem)
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"MEH\"}"))
                .andExpect(status().isBadRequest());

        // members edit their own items; the organizer can edit anyone's
        as(ben, patch("/api/trips/{id}/shortlist/{item}", tripId, ashaItem)
                .contentType(MediaType.APPLICATION_JSON).content("{\"notes\":\"mine now\"}"))
                .andExpect(status().isForbidden());
        as(ben, patch("/api/trips/{id}/shortlist/{item}", tripId, benItem)
                .contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"FOOD\",\"reason\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("FOOD"))
                .andExpect(jsonPath("$.reason").doesNotExist())
                .andExpect(jsonPath("$.title").value("Night market"));
        as(ben, delete("/api/trips/{id}/shortlist/{item}", tripId, ashaItem)).andExpect(status().isForbidden());
        as(asha, delete("/api/trips/{id}/shortlist/{item}", tripId, benItem)).andExpect(status().isNoContent());

        // outsiders can't see or touch anything
        as(outsider, get("/api/trips/{id}/shortlist", tripId)).andExpect(status().isNotFound());
        as(outsider, get("/api/trips/{id}/places/search", tripId).param("q", "fort")).andExpect(status().isNotFound());
        as(outsider, put("/api/trips/{id}/shortlist/{item}/reaction", tripId, ashaItem)
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"LOVE\"}"))
                .andExpect(status().isNotFound());
        // an item id from another trip is not found either
        String otherTrip = createTrip(outsider);
        as(outsider, delete("/api/trips/{id}/shortlist/{item}", otherTrip, ashaItem)).andExpect(status().isNotFound());

        as(asha, get("/api/trips/{id}/shortlist", tripId))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Spice farm"));
    }
}
