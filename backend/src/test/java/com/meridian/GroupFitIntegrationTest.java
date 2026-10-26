package com.meridian;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.meridian.place.OsmTags;
import com.meridian.place.PlaceCategory;
import com.meridian.place.PlacesClient.PlaceData;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Group fit, recommendations and compare end to end, with AI off and OpenStreetMap faked. */
class GroupFitIntegrationTest extends IntegrationTestBase {

    static final String ASHA_QUIZ = """
            {"interestRatings":{"beaches":4,"history":4,"local_food":4,"nightlife":0},
             "pace":"slow","budget":{"stay":"low","food":"low","activities":"mid"},
             "crowdTolerance":15,"hiddenGems":90,"dietary":["vegetarian"],"accessNeeds":[],
             "mustHaves":[],"dealbreakers":["loud clubs"],"idealTrip":""}""";
    static final String DEV_QUIZ = """
            {"interestRatings":{"beaches":3,"history":1,"local_food":4,"nightlife":4},
             "pace":"packed","budget":{"stay":"high","food":"mid","activities":"high"},
             "crowdTolerance":90,"hiddenGems":40,"dietary":[],"accessNeeds":[],
             "mustHaves":[],"dealbreakers":[],"idealTrip":""}""";

    @Autowired
    FakePlacesClient osm;

    @Autowired
    JdbcTemplate jdbc;

    private double lat;
    private double lng;

    @BeforeEach
    void freshArea() {
        lat = ThreadLocalRandom.current().nextDouble(-60, 60);
        lng = ThreadLocalRandom.current().nextDouble(-170, 170);
        osm.reset(List.of(
                place("Fort Aguada", PlaceCategory.SIGHTS, Map.of("historic", "fort", "tourism", "attraction",
                        "wikipedia", "en:Fort Aguada", "wikidata", "Q1")),
                place("Cabo de Rama", PlaceCategory.SIGHTS, Map.of("historic", "fort")),
                place("Kakolem Beach", PlaceCategory.SIGHTS, Map.of("natural", "beach"))));
    }

    private PlaceData place(String name, PlaceCategory category, Map<String, String> tags) {
        Map<String, String> all = new java.util.HashMap<>(tags);
        all.put("name", name);
        return new PlaceData("osm", "node/" + UUID.randomUUID(), name, category,
                lat + ThreadLocalRandom.current().nextDouble(-0.05, 0.05), lng, null, null, Map.copyOf(all),
                OsmTags.popularity(all));
    }

    private ResultActions as(TestUser user, MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(req.header("Authorization", user.bearer()));
    }

    private JsonNode body(ResultActions r) throws Exception {
        return read(r.andReturn().getResponse().getContentAsString());
    }

    private String createTrip(TestUser organizer) throws Exception {
        String trip = String.format(Locale.ROOT, """
                {"name":"Goa","destinationName":"Goa, India","destinationLat":%f,"destinationLng":%f,
                 "startDate":"2026-12-10","endDate":"2026-12-14","currency":"INR"}""", lat, lng);
        return body(as(organizer, post("/api/trips").contentType(MediaType.APPLICATION_JSON).content(trip))
                .andExpect(status().isCreated())).get("id").asText();
    }

    private void join(TestUser organizer, TestUser member, String tripId) throws Exception {
        String token = body(as(organizer, post("/api/trips/{id}/invites", tripId))).get("token").asText();
        as(member, post("/api/invites/{token}/accept", token)).andExpect(status().isOk());
    }

    private void quiz(TestUser user, String quiz) throws Exception {
        JsonNode started = body(as(user, post("/api/profile/quiz").contentType(MediaType.APPLICATION_JSON)
                .content(quiz)).andExpect(status().isAccepted()));
        assertThat(waitForJob(user, started.get("id").asText()).get("status").asText()).isEqualTo("SUCCEEDED");
    }

    private void budget(TestUser user, String tripId, int min, int max, boolean visible) throws Exception {
        as(user, put("/api/trips/{id}/preferences/me", tripId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"budgetMin\":%d,\"budgetMax\":%d,\"mustHaves\":[],\"budgetVisible\":%b}"
                        .formatted(min, max, visible)))
                .andExpect(status().isOk());
    }

    /** Asha (organizer, gems fan, hates clubs), Dev (party, classics) and Kim (joined, no profile yet). */
    private record Group(String tripId, TestUser asha, TestUser dev, TestUser kim) {}

    private Group group() throws Exception {
        TestUser asha = newUser("Asha");
        TestUser dev = newUser("Dev");
        TestUser kim = newUser("Kim");
        String tripId = createTrip(asha);
        join(asha, dev, tripId);
        join(asha, kim, tripId);
        quiz(asha, ASHA_QUIZ);
        quiz(dev, DEV_QUIZ);
        budget(asha, tripId, 20000, 40000, false);
        budget(dev, tripId, 30000, 90000, false);
        return new Group(tripId, asha, dev, kim);
    }

    private JsonNode row(JsonNode fit, String key) {
        for (JsonNode r : fit.get("rows")) {
            if (r.get("key").asText().equals(key)) return r;
        }
        throw new AssertionError("no row " + key);
    }

    @Test
    void matrixShowsConflictsAndKeepsBudgetsPrivate() throws Exception {
        Group g = group();
        JsonNode fit = body(as(g.dev(), get("/api/trips/{id}/group-fit", g.tripId())).andExpect(status().isOk()));

        assertThat(row(fit, "pace").get("status").asText()).isEqualTo("CONFLICT");
        assertThat(row(fit, "crowds").get("status").asText()).isEqualTo("CONFLICT");
        assertThat(row(fit, "diet").get("status").asText()).isEqualTo("PLAN_AROUND");
        assertThat(row(fit, "musts").get("summary").asText()).contains("Dev wants nightlife")
                .contains("loud clubs");

        // Dev sees his own budget but only "Private" for Asha's, and the shared range
        JsonNode trip = row(fit, "budget.trip");
        assertThat(trip.at("/cells/0/hidden").asBoolean()).isTrue();
        assertThat(trip.at("/cells/1/value").asText()).isEqualTo("INR 30,000–90,000");
        assertThat(trip.at("/cells/2/value").asText()).isEqualTo("Not set");
        assertThat(trip.get("summary").asText()).isEqualTo("Everyone's private budget fits INR 30,000–40,000.");
        assertThat(fit.toString()).doesNotContain("20,000");
        assertThat(row(fit, "budget.stay").at("/cells/0/value").asText()).isEqualTo("Private");

        // Kim has done nothing yet
        JsonNode kim = fit.get("contributions").get(2);
        assertThat(kim.get("hasProfile").asBoolean()).isFalse();
        assertThat(kim.get("missing").toString()).contains("Travel profile").contains("Trip budget");

        // AI is off in tests: template trade-offs with provenance
        assertThat(fit.at("/tradeoffs/provenance/source").asText()).isEqualTo("rules");
        assertThat(fit.at("/tradeoffs/items").size()).isGreaterThanOrEqualTo(3);
        assertThat(fit.get("aiAvailable").asBoolean()).isFalse();
    }

    @Test
    void tradeoffJobFallsBackToTemplatesWhenAiIsOff() throws Exception {
        Group g = group();
        JsonNode started = body(as(g.kim(), post("/api/trips/{id}/group-fit/tradeoffs", g.tripId()))
                .andExpect(status().isAccepted()));
        JsonNode job = waitForJob(g.kim(), started.get("id").asText());
        assertThat(job.get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(job.at("/result/provenance/note").asText()).contains("AI is turned off");
    }

    @Test
    void shortlistItemsGetGroupFitScoresWithReasons() throws Exception {
        Group g = group();
        String placeId = body(as(g.asha(), get("/api/trips/{id}/places/nearby", g.tripId())
                .param("category", "SIGHTS"))).at("/places/1/id").asText(); // Cabo de Rama
        String itemId = body(as(g.asha(), post("/api/trips/{id}/shortlist", g.tripId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"PLACE\",\"placeId\":\"" + placeId + "\"}"))
                .andExpect(status().isCreated())).get("id").asText();
        as(g.dev(), put("/api/trips/{id}/shortlist/{item}/reaction", g.tripId(), itemId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"NO_WAY\",\"reason\":\"too far\"}"))
                .andExpect(status().isOk());

        JsonNode item = body(as(g.kim(), get("/api/trips/{id}/group-fit", g.tripId()))).at("/items/0");
        assertThat(item.get("title").asText()).isEqualTo("Cabo de Rama");
        assertThat(item.get("satisfied").asInt()).isEqualTo(1);
        assertThat(item.get("missed").asInt()).isEqualTo(1);
        assertThat(item.get("unknown").asInt()).isEqualTo(1);
        assertThat(item.get("summary").asText()).isEqualTo(
                "Works for Asha; not for Dev (said no way: “too far”); 1 without preferences yet.");
    }

    @Test
    void recommendationsAreRankedByCodeAndSliderWhenAiIsOff() throws Exception {
        Group g = group();
        JsonNode gems = body(as(g.asha(), get("/api/trips/{id}/recommendations", g.tripId())
                .param("category", "SIGHTS").param("gems", "100")).andExpect(status().isOk()));
        assertThat(gems.at("/provenance/source").asText()).isEqualTo("rules");
        assertThat(gems.at("/provenance/note").asText()).contains("AI is turned off");
        assertThat(gems.get("groupGems").asInt()).isEqualTo(65);
        assertThat(gems.get("placesSource").asText()).isEqualTo("OpenStreetMap");
        List<String> order = new ArrayList<>();
        gems.get("items").forEach(i -> order.add(i.at("/place/name").asText()));
        assertThat(order.indexOf("Cabo de Rama")).isLessThan(order.indexOf("Fort Aguada"));
        assertThat(gems.at("/items/0/sponsored").asBoolean()).isFalse();
        assertThat(gems.at("/items/0/why").asText()).isNotBlank();

        JsonNode classics = body(as(g.asha(), get("/api/trips/{id}/recommendations", g.tripId())
                .param("category", "SIGHTS").param("gems", "0")));
        List<String> classicOrder = new ArrayList<>();
        classics.get("items").forEach(i -> classicOrder.add(i.at("/place/name").asText()));
        assertThat(classicOrder.indexOf("Fort Aguada")).isLessThan(classicOrder.indexOf("Cabo de Rama"));

        JsonNode job = waitForJob(g.asha(), body(as(g.asha(), post("/api/trips/{id}/recommendations/ai",
                g.tripId()).contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"SIGHTS\"}"))
                .andExpect(status().isAccepted())).get("id").asText());
        assertThat(job.at("/result/provenance/source").asText()).isEqualTo("rules");

        TestUser stranger = newUser("Eve");
        as(stranger, get("/api/trips/{id}/recommendations", g.tripId()).param("category", "SIGHTS"))
                .andExpect(status().isNotFound());
    }

    @Test
    void compareRowsCarryPriceSourceAndCheckedAt() throws Exception {
        Group g = group();
        JsonNode c = body(as(g.asha(), post("/api/trips/{id}/comparisons", g.tripId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"STAY\",\"title\":\"North Goa stay\"}"))
                .andExpect(status().isCreated()));
        String cid = c.get("id").asText();

        as(g.dev(), post("/api/trips/{id}/comparisons/{c}/rows", g.tripId(), cid)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"provider":"Booking.com","title":"Casa Susegad","price":4500,"currency":"INR",
                         "sourceUrl":"https://www.booking.com/hotel/in/casa.html","sponsored":true}"""))
                .andExpect(status().isCreated());
        JsonNode after = body(as(g.kim(), post("/api/trips/{id}/comparisons/{c}/rows", g.tripId(), cid)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"provider":"Zostel","price":1200,"currency":"INR","checkedAt":"2026-01-01T10:00:00Z"}"""))
                .andExpect(status().isCreated()));

        JsonNode rows = after.get("rows");
        assertThat(rows.get(0).get("provider").asText()).isEqualTo("Zostel"); // cheapest first, sponsored ignored
        assertThat(rows.get(0).get("cheapest").asBoolean()).isTrue();
        assertThat(rows.get(0).get("stale").asBoolean()).isTrue();
        assertThat(rows.get(1).get("sponsored").asBoolean()).isTrue();
        assertThat(rows.get(1).get("stale").asBoolean()).isFalse();
        assertThat(rows.get(1).get("sourceHost").asText()).isEqualTo("www.booking.com");
        assertThat(rows.get(1).get("canEdit").asBoolean()).isFalse(); // Kim did not add it

        String staleId = rows.get(0).get("id").asText();
        JsonNode rechecked = body(as(g.asha(), post("/api/trips/{id}/comparisons/{c}/rows/{r}/recheck",
                g.tripId(), cid, staleId)).andExpect(status().isOk()));
        assertThat(rechecked.at("/rows/0/stale").asBoolean()).isFalse();

        // a price needs a currency; links must be http(s)
        as(g.dev(), post("/api/trips/{id}/comparisons/{c}/rows", g.tripId(), cid)
                .contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"X\",\"price\":10}"))
                .andExpect(status().isBadRequest());
        as(g.dev(), post("/api/trips/{id}/comparisons/{c}/rows", g.tripId(), cid)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"X\",\"sourceUrl\":\"javascript:alert(1)\"}"))
                .andExpect(status().isBadRequest());

        // only the adder or the organizer edits; the organizer can delete the comparison
        String bookingId = rows.get(1).get("id").asText();
        as(g.kim(), delete("/api/trips/{id}/comparisons/{c}/rows/{r}", g.tripId(), cid, bookingId))
                .andExpect(status().isForbidden());
        as(g.asha(), delete("/api/trips/{id}/comparisons/{c}", g.tripId(), cid))
                .andExpect(status().isNoContent());
        as(g.asha(), get("/api/trips/{id}/comparisons", g.tripId())).andExpect(jsonPath("$.length()").value(0));
    }
}
