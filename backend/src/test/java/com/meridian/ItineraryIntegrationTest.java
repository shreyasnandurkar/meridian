package com.meridian;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Itinerary generation (AI off, so the rule planner), edits, proposals and comments end to end. */
class ItineraryIntegrationTest extends IntegrationTestBase {

    private ResultActions as(TestUser user, MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(req.header("Authorization", user.bearer()));
    }

    private JsonNode body(ResultActions r) throws Exception {
        return read(r.andReturn().getResponse().getContentAsString());
    }

    private ResultActions postJson(TestUser user, String path, String json, Object... vars) throws Exception {
        return as(user, post(path, vars).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** Asha (organizer) and Dev, a 3-day Goa trip, and a shortlist of north and south Goa pins. */
    private record Trip(String id, TestUser asha, TestUser dev, Map<String, String> items) {}

    private Trip trip() throws Exception {
        TestUser asha = newUser("Asha");
        TestUser dev = newUser("Dev");
        String tripId = body(postJson(asha, "/api/trips", """
                {"name":"Goa","destinationName":"Panaji","destinationLat":15.49,"destinationLng":73.83,
                 "startDate":"2026-12-10","endDate":"2026-12-12","currency":"INR"}""")
                .andExpect(status().isCreated())).get("id").asText();
        String token = body(as(asha, post("/api/trips/{id}/invites", tripId))).get("token").asText();
        as(dev, post("/api/invites/{token}/accept", token)).andExpect(status().isOk());

        Map<String, String> items = new HashMap<>();
        Object[][] pins = {
            {"Chapora Fort", "SIGHTS", 15.6060, 73.7366}, {"Anjuna Market", "SIGHTS", 15.5733, 73.7400},
            {"Baga Beach", "SIGHTS", 15.5553, 73.7517}, {"Vinayak", "FOOD", 15.5800, 73.7450},
            {"Colva Beach", "SIGHTS", 15.2793, 73.9223}, {"Cabo de Rama", "SIGHTS", 15.0886, 73.9196},
            {"Palolem", "SIGHTS", 15.0100, 74.0230}, {"Casa Susegad", "STAYS", 15.30, 73.95}};
        for (Object[] p : pins) {
            items.put((String) p[0], body(postJson(asha, "/api/trips/{id}/shortlist", String.format(
                    java.util.Locale.ROOT, "{\"kind\":\"PIN\",\"title\":\"%s\",\"category\":\"%s\",\"lat\":%f,\"lng\":%f}",
                    p[0], p[1], p[2], p[3]), tripId).andExpect(status().isCreated())).get("id").asText());
        }
        items.put("Blog post", body(postJson(dev, "/api/trips/{id}/shortlist",
                "{\"kind\":\"LINK\",\"url\":\"https://example.com/goa\",\"title\":\"Blog post\",\"category\":\"SIGHTS\"}",
                tripId).andExpect(status().isCreated())).get("id").asText());
        return new Trip(tripId, asha, dev, items);
    }

    private JsonNode generate(Trip t) throws Exception {
        JsonNode job = body(postJson(t.asha(), "/api/trips/{id}/itinerary/generate", "{}", t.id())
                .andExpect(status().isAccepted()));
        JsonNode done = waitForJob(t.asha(), job.get("id").asText());
        assertThat(done.get("status").asText()).isEqualTo("SUCCEEDED");
        return done.get("result");
    }

    private static List<String> titles(JsonNode day) {
        List<String> out = new ArrayList<>();
        day.get("stops").forEach(s -> out.add(s.get("title").asText()));
        return out;
    }

    private static int dayOf(JsonNode itinerary, String title) {
        for (JsonNode d : itinerary.get("days")) {
            if (titles(d).contains(title)) return d.get("index").asInt();
        }
        return -1;
    }

    @Test
    void organizerGeneratesADraftGroupedByAreaWithReasonsForWhatIsLeftOut() throws Exception {
        Trip t = trip();
        as(t.dev(), get("/api/trips/{id}/itinerary", t.id())).andExpect(status().isNoContent());
        postJson(t.dev(), "/api/trips/{id}/itinerary/generate", "{}", t.id()).andExpect(status().isForbidden());

        generate(t);
        JsonNode it = body(as(t.dev(), get("/api/trips/{id}/itinerary", t.id())).andExpect(status().isOk()));

        assertThat(it.get("days")).hasSize(3);
        assertThat(it.get("canEdit").asBoolean()).isFalse();
        int stops = 0;
        for (JsonNode d : it.get("days")) {
            stops += d.get("stops").size();
            assertThat(d.at("/provenance/source").asText()).isEqualTo("rules");
            assertThat(d.get("overflowMin").asInt()).isZero();
            JsonNode first = d.get("stops").path(0);
            if (!first.isMissingNode()) assertThat(first.get("travelMin").asInt()).isZero();
        }
        assertThat(stops).isEqualTo(7);
        // north and south Goa never share a day; the four close-together northern places share one
        List<Integer> north = List.of(dayOf(it, "Chapora Fort"), dayOf(it, "Anjuna Market"), dayOf(it, "Baga Beach"),
                dayOf(it, "Vinayak"));
        List<Integer> south = List.of(dayOf(it, "Colva Beach"), dayOf(it, "Cabo de Rama"), dayOf(it, "Palolem"));
        assertThat(north).containsOnly(north.getFirst());
        assertThat(south).doesNotContain(north.getFirst());
        Map<String, String> unplaced = new HashMap<>();
        it.get("unplaced").forEach(u -> unplaced.put(u.get("title").asText(), u.get("reason").asText()));
        assertThat(unplaced).containsOnlyKeys("Casa Susegad", "Blog post");
        assertThat(unplaced.get("Casa Susegad")).startsWith("A place to stay");
        assertThat(unplaced.get("Blog post")).startsWith("No location yet");

        assertThat(body(as(t.asha(), get("/api/trips/{id}/itinerary", t.id()))).get("canEdit").asBoolean()).isTrue();
        as(newUser("Stranger"), get("/api/trips/{id}/itinerary", t.id())).andExpect(status().isNotFound());
    }

    @Test
    void theOrganizerEditsDirectlyAndMembersPropose() throws Exception {
        Trip t = trip();
        JsonNode before = generate(t);
        int from = dayOf(before, "Chapora Fort");
        int to = from == 3 ? 2 : 3;
        String chapora = t.items().get("Chapora Fort");
        String move = "{\"type\":\"MOVE\",\"itemId\":\"" + chapora + "\",\"day\":" + to + "}";

        postJson(t.dev(), "/api/trips/{id}/itinerary/edits", move, t.id()).andExpect(status().isForbidden());
        JsonNode after = body(postJson(t.asha(), "/api/trips/{id}/itinerary/edits", move, t.id())
                .andExpect(status().isOk()));
        assertThat(dayOf(after, "Chapora Fort")).isEqualTo(to);
        assertThat(after.get("version").asInt()).isGreaterThan(before.get("version").asInt());
        assertThat(after.at("/days/" + (to - 1) + "/repairs").toString()).contains("Changed by Asha");
        // the summaries of both changed days are rewritten from their new stops, so none names a stop that left
        assertThat(after.at("/days/" + (from - 1) + "/summary").asText()).doesNotContain("Chapora Fort");
        String moved = after.at("/days/" + (to - 1) + "/summary").asText();
        assertThat(after.get("days").get(to - 1).get("stops").findValuesAsText("title"))
                .anyMatch(moved::contains);

        // swapping the first two stops of a day: whatever is first now starts when the day begins
        JsonNode busy = after.get("days").get(dayOf(after, "Anjuna Market") - 1);
        String first = busy.at("/stops/0/shortlistItemId").asText();
        String second = busy.at("/stops/1/title").asText();
        JsonNode reordered = body(postJson(t.asha(), "/api/trips/{id}/itinerary/edits",
                "{\"type\":\"MOVE\",\"itemId\":\"" + first + "\",\"day\":" + busy.get("index").asInt()
                        + ",\"position\":1}", t.id()).andExpect(status().isOk()));
        JsonNode now = reordered.get("days").get(busy.get("index").asInt() - 1);
        assertThat(now.at("/stops/0/title").asText()).isEqualTo(second);
        assertThat(now.at("/stops/0/start").asText()).isEqualTo(now.get("dayStart").asText());

        String baga = t.items().get("Baga Beach");
        String remove = "{\"edit\":{\"type\":\"REMOVE\",\"itemId\":\"" + baga + "\"},\"reason\":\"Too crowded\"}";
        JsonNode proposal = body(postJson(t.dev(), "/api/trips/{id}/proposals", remove, t.id())
                .andExpect(status().isCreated()));
        assertThat(proposal.get("summary").asText()).startsWith("Remove Baga Beach from day ");
        assertThat(proposal.get("status").asText()).isEqualTo("OPEN");
        assertThat(proposal.get("current").asBoolean()).isTrue();
        assertThat(proposal.get("canWithdraw").asBoolean()).isTrue();
        postJson(t.asha(), "/api/trips/{id}/proposals", remove, t.id()).andExpect(status().isConflict());
        String id = proposal.get("id").asText();
        as(t.asha(), post("/api/trips/{id}/proposals/{p}/withdraw", t.id(), id)).andExpect(status().isForbidden());
        assertThat(body(as(t.dev(), post("/api/trips/{id}/proposals/{p}/withdraw", t.id(), id))
                .andExpect(status().isOk())).get("status").asText()).isEqualTo("WITHDRAWN");

        String stay = t.items().get("Casa Susegad");
        postJson(t.dev(), "/api/trips/{id}/proposals",
                "{\"edit\":{\"type\":\"ADD\",\"itemId\":\"" + stay + "\",\"day\":1}}", t.id())
                .andExpect(status().isConflict());

        postJson(t.dev(), "/api/trips/{id}/proposals", remove, t.id()).andExpect(status().isCreated());
        generate(t);
        JsonNode list = body(as(t.dev(), get("/api/trips/{id}/proposals", t.id())));
        assertThat(list.get(0).get("status").asText()).isEqualTo("STALE");
        assertThat(body(as(t.dev(), get("/api/trips/{id}/itinerary", t.id()))).get("openProposals").asInt())
                .isZero();
    }

    @Test
    void commentsAndTheShortlistGuard() throws Exception {
        Trip t = trip();
        generate(t);
        String colva = t.items().get("Colva Beach");
        JsonNode mine = body(postJson(t.dev(), "/api/trips/{id}/comments",
                "{\"targetType\":\"SHORTLIST_ITEM\",\"targetId\":\"" + colva + "\",\"body\":\"Sunset here?\"}", t.id())
                .andExpect(status().isCreated()));
        JsonNode hers = body(postJson(t.asha(), "/api/trips/{id}/comments",
                "{\"targetType\":\"SHORTLIST_ITEM\",\"targetId\":\"" + colva + "\",\"body\":\"Yes!\"}", t.id())
                .andExpect(status().isCreated()));
        postJson(t.dev(), "/api/trips/{id}/comments",
                "{\"targetType\":\"PROPOSAL\",\"targetId\":\"" + colva + "\",\"body\":\"x\"}", t.id())
                .andExpect(status().isNotFound());

        JsonNode it = body(as(t.dev(), get("/api/trips/{id}/itinerary", t.id())));
        JsonNode day = it.get("days").get(dayOf(it, "Colva Beach") - 1);
        for (JsonNode s : day.get("stops")) {
            if (s.get("title").asText().equals("Colva Beach")) assertThat(s.get("commentCount").asInt()).isEqualTo(2);
        }
        assertThat(body(as(t.dev(), get("/api/trips/{id}/comments", t.id())))).hasSize(2);
        as(t.dev(), delete("/api/trips/{id}/comments/{c}", t.id(), hers.get("id").asText()))
                .andExpect(status().isForbidden());
        as(t.asha(), delete("/api/trips/{id}/comments/{c}", t.id(), mine.get("id").asText()))
                .andExpect(status().isNoContent());

        as(t.asha(), delete("/api/trips/{id}/shortlist/{item}", t.id(), colva)).andExpect(status().isConflict());
        postJson(t.asha(), "/api/trips/{id}/itinerary/edits",
                "{\"type\":\"REMOVE\",\"itemId\":\"" + colva + "\"}", t.id()).andExpect(status().isOk());
        as(t.asha(), delete("/api/trips/{id}/shortlist/{item}", t.id(), colva)).andExpect(status().isNoContent());
        assertThat(body(as(t.dev(), get("/api/trips/{id}/comments", t.id())))).isEmpty();
    }
}
