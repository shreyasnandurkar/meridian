package com.meridian;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Votes, the quorum, the consensus gate, lock and unlock, and the decision log end to end (AI off). */
class VotingIntegrationTest extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    private ResultActions as(TestUser user, MockHttpServletRequestBuilder req) throws Exception {
        return mvc.perform(req.header("Authorization", user.bearer()));
    }

    private JsonNode body(ResultActions r) throws Exception {
        return read(r.andReturn().getResponse().getContentAsString());
    }

    private ResultActions send(TestUser user, MockHttpServletRequestBuilder req, String json) throws Exception {
        return as(user, req.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** Asha organizes; Meera, Dev and Kabir are members: a majority is 3 of 4. */
    private record Group(String id, TestUser asha, TestUser meera, TestUser dev, TestUser kabir,
            Map<String, String> items) {}

    private Group group() throws Exception {
        TestUser asha = newUser("Asha");
        TestUser meera = newUser("Meera");
        TestUser dev = newUser("Dev");
        TestUser kabir = newUser("Kabir");
        String tripId = body(send(asha, post("/api/trips"), """
                {"name":"Goa","destinationName":"Panaji","destinationLat":15.49,"destinationLng":73.83,
                 "startDate":"2026-12-10","endDate":"2026-12-12","currency":"INR"}""")
                .andExpect(status().isCreated())).get("id").asText();
        String token = body(as(asha, post("/api/trips/{id}/invites", tripId))).get("token").asText();
        for (TestUser u : List.of(meera, dev, kabir)) {
            as(u, post("/api/invites/{token}/accept", token)).andExpect(status().isOk());
        }
        Map<String, String> items = new HashMap<>();
        Object[][] pins = {
            {"Chapora Fort", "SIGHTS", 15.6060, 73.7366}, {"Anjuna Market", "SIGHTS", 15.5733, 73.7400},
            {"Vinayak", "FOOD", 15.5800, 73.7450}, {"Colva Beach", "SIGHTS", 15.2793, 73.9223},
            {"Cabo de Rama", "SIGHTS", 15.0886, 73.9196}, {"Palolem", "SIGHTS", 15.0100, 74.0230}};
        for (Object[] p : pins) {
            items.put((String) p[0], body(send(asha, post("/api/trips/{id}/shortlist", tripId), String.format(
                    Locale.ROOT, "{\"kind\":\"PIN\",\"title\":\"%s\",\"category\":\"%s\",\"lat\":%f,\"lng\":%f}",
                    p[0], p[1], p[2], p[3])).andExpect(status().isCreated())).get("id").asText());
        }
        JsonNode job = body(send(asha, post("/api/trips/{id}/itinerary/generate", tripId), "{}")
                .andExpect(status().isAccepted()));
        assertThat(waitForJob(asha, job.get("id").asText()).get("status").asText()).isEqualTo("SUCCEEDED");
        return new Group(tripId, asha, meera, dev, kabir, items);
    }

    private JsonNode itinerary(Group g) throws Exception {
        return body(as(g.asha(), get("/api/trips/{id}/itinerary", g.id())).andExpect(status().isOk()));
    }

    private static int dayOf(JsonNode itinerary, String title) {
        for (JsonNode d : itinerary.get("days")) {
            for (JsonNode s : d.get("stops")) if (s.get("title").asText().equals(title)) return d.get("index").asInt();
        }
        return -1;
    }

    private static JsonNode stop(JsonNode itinerary, String title) {
        for (JsonNode d : itinerary.get("days")) {
            for (JsonNode s : d.get("stops")) if (s.get("title").asText().equals(title)) return s;
        }
        throw new AssertionError(title + " is not on the itinerary");
    }

    private JsonNode vote(TestUser user, Group g, String proposalId, String value) throws Exception {
        return body(send(user, put("/api/trips/{id}/proposals/{p}/vote", g.id(), proposalId),
                "{\"value\":\"" + value + "\"}").andExpect(status().isOk()));
    }

    private JsonNode votePlan(TestUser user, Group g, String value) throws Exception {
        return body(send(user, put("/api/trips/{id}/itinerary/approval", g.id()), "{\"value\":\"" + value + "\"}")
                .andExpect(status().isOk()));
    }

    private JsonNode decisions(TestUser user, Group g) throws Exception {
        return body(as(user, get("/api/trips/{id}/decisions", g.id())).andExpect(status().isOk()));
    }

    private static List<String> names(JsonNode people, String field) {
        List<String> out = new ArrayList<>();
        people.forEach(p -> out.add(p.at(field).asText()));
        return out;
    }

    @Test
    void aChangeIsAppliedWhenTheQuorumApprovesAndLoggedWithItsVotes() throws Exception {
        Group g = group();
        int from = dayOf(itinerary(g), "Chapora Fort");
        int to = from == 1 ? 3 : 1;

        // two members approve the plan as it is; their approval only carries over a change they vote for
        votePlan(g.meera(), g, "APPROVE");
        votePlan(g.kabir(), g, "APPROVE");

        JsonNode p = body(send(g.meera(), post("/api/trips/{id}/proposals", g.id()), String.format(
                "{\"edit\":{\"type\":\"MOVE\",\"itemId\":\"%s\",\"day\":%d},\"reason\":\"Less driving\"}",
                g.items().get("Chapora Fort"), to)).andExpect(status().isCreated()));
        String id = p.get("id").asText();
        // proposing counts as the proposer's approval
        assertThat(p.get("myVote").asText()).isEqualTo("APPROVE");
        assertThat(p.at("/tally/approvals").asInt()).isEqualTo(1);
        assertThat(p.at("/tally/required").asInt()).isEqualTo(3);
        assertThat(names(p.get("notVoted"), "/displayName")).containsExactlyInAnyOrder("Asha", "Dev", "Kabir");

        assertThat(vote(g.asha(), g, id, "REJECT").at("/tally/rejections").asInt()).isEqualTo(1);
        // a second vote replaces the first
        JsonNode changed = vote(g.asha(), g, id, "APPROVE");
        assertThat(changed.at("/tally/approvals").asInt()).isEqualTo(2);
        assertThat(changed.at("/tally/rejections").asInt()).isZero();
        assertThat(vote(g.kabir(), g, id, "REJECT").get("status").asText()).isEqualTo("OPEN");
        as(newUser("Stranger"), put("/api/trips/{id}/proposals/{p}/vote", g.id(), id)
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"APPROVE\"}"))
                .andExpect(status().isNotFound());

        JsonNode decided = vote(g.dev(), g, id, "APPROVE");
        assertThat(decided.get("status").asText()).isEqualTo("APPROVED");
        assertThat(decided.get("canVote").asBoolean()).isFalse();
        assertThat(decided.get("decisionId").isTextual()).isTrue();
        send(g.kabir(), put("/api/trips/{id}/proposals/{p}/vote", g.id(), id), "{\"value\":\"APPROVE\"}")
                .andExpect(status().isConflict());

        JsonNode it = itinerary(g);
        assertThat(dayOf(it, "Chapora Fort")).isEqualTo(to);
        assertThat(stop(it, "Chapora Fort").get("decisionId").asText()).isEqualTo(decided.get("decisionId").asText());

        JsonNode log = decisions(g.dev(), g);
        assertThat(log).hasSize(1);
        JsonNode entry = log.get(0);
        assertThat(entry.get("kind").asText()).isEqualTo("CHANGE_APPROVED");
        assertThat(entry.get("title").asText()).startsWith("Move Chapora Fort");
        assertThat(entry.get("alternatives")).hasSize(2);
        assertThat(entry.at("/tally/approvals").asInt()).isEqualTo(3);
        assertThat(entry.at("/tally/members").asInt()).isEqualTo(4);
        assertThat(names(entry.get("votes"), "/displayName")).containsExactly("Meera", "Asha", "Kabir", "Dev");
        assertThat(entry.get("reason").asText()).isEqualTo("Less driving");
        assertThat(entry.get("summary").asText()).contains("3 of 4 approved").contains("Voted against: Kabir")
                .contains("Meera's reason");
        assertThat(entry.at("/provenance/source").asText()).isEqualTo("rules");

        // the plan changed: Meera's approval carried over (she voted for the change), Kabir's did not
        JsonNode gate = body(as(g.asha(), get("/api/trips/{id}/itinerary/approval", g.id())));
        assertThat(gate.at("/tally/approvals").asInt()).isEqualTo(1);
        Map<String, JsonNode> voters = new HashMap<>();
        gate.get("voters").forEach(v -> voters.put(v.get("displayName").asText(), v));
        assertThat(voters.get("Meera").get("value").asText()).isEqualTo("APPROVE");
        assertThat(voters.get("Kabir").get("value").isNull()).isTrue();
        assertThat(voters.get("Kabir").get("earlier").asText()).isEqualTo("APPROVE");
        assertThat(names(gate.get("notVoted"), "/displayName")).containsExactlyInAnyOrder("Asha", "Dev", "Kabir");

        // the log is append-only, enforced by the database
        UUID entryId = UUID.fromString(entry.get("id").asText());
        assertThatThrownBy(() -> jdbc.update("update decision_log set title = 'x' where id = ?", entryId))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from decision_log where id = ?", entryId))
                .hasMessageContaining("append-only");
        jdbc.update("update decision_log set summary = 'Rewritten' where id = ?", entryId);
    }

    @Test
    void aChangeIsRejectedOnceTheQuorumIsOutOfReach() throws Exception {
        Group g = group();
        String id = body(send(g.dev(), post("/api/trips/{id}/proposals", g.id()),
                "{\"edit\":{\"type\":\"REMOVE\",\"itemId\":\"" + g.items().get("Palolem") + "\"}}")
                .andExpect(status().isCreated())).get("id").asText();
        vote(g.kabir(), g, id, "REJECT");
        JsonNode closed = vote(g.meera(), g, id, "REJECT");
        // 2 against out of 4: the 3 approvals needed can't be reached any more
        assertThat(closed.get("status").asText()).isEqualTo("REJECTED");
        assertThat(dayOf(itinerary(g), "Palolem")).isPositive();
        JsonNode entry = decisions(g.asha(), g).get(0);
        assertThat(entry.get("kind").asText()).isEqualTo("CHANGE_REJECTED");
        assertThat(entry.get("summary").asText()).contains("2 of 4 voted against").contains("In favour: Dev");
        assertThat(names(entry.get("notVoted"), "/displayName")).containsExactly("Asha");
    }

    @Test
    void theConsensusGateLocksOnlyWithQuorumUnlessTheOrganizerGivesAReason() throws Exception {
        Group g = group();
        String proposal = body(send(g.dev(), post("/api/trips/{id}/proposals", g.id()),
                "{\"edit\":{\"type\":\"REMOVE\",\"itemId\":\"" + g.items().get("Vinayak") + "\"}}"))
                .get("id").asText();

        votePlan(g.asha(), g, "APPROVE");
        JsonNode gate = votePlan(g.dev(), g, "APPROVE");
        assertThat(gate.get("quorumMet").asBoolean()).isFalse();
        assertThat(gate.get("canLock").asBoolean()).isFalse(); // Dev is not the organizer
        send(g.dev(), post("/api/trips/{id}/itinerary/lock", g.id()), "{}").andExpect(status().isForbidden());
        JsonNode refused = body(send(g.asha(), post("/api/trips/{id}/itinerary/lock", g.id()), "{}")
                .andExpect(status().isConflict()));
        assertThat(refused.get("detail").asText()).contains("2 of the 3 approvals");

        gate = votePlan(g.kabir(), g, "APPROVE");
        assertThat(gate.get("quorumMet").asBoolean()).isTrue();
        assertThat(names(gate.get("notVoted"), "/displayName")).containsExactly("Meera");
        gate = body(send(g.asha(), post("/api/trips/{id}/itinerary/lock", g.id()), "{}").andExpect(status().isOk()));
        assertThat(gate.get("status").asText()).isEqualTo("LOCKED");
        assertThat(gate.at("/lockedBy/displayName").asText()).isEqualTo("Asha");
        String lockDecision = gate.get("lockDecisionId").asText();

        // locked: no edits, proposals or votes; the open proposal was closed
        JsonNode it = itinerary(g);
        assertThat(it.get("status").asText()).isEqualTo("LOCKED");
        assertThat(it.get("canEdit").asBoolean()).isFalse();
        assertThat(stop(it, "Colva Beach").get("decisionId").asText()).isEqualTo(lockDecision);
        JsonNode closed = body(as(g.dev(), get("/api/trips/{id}/proposals", g.id()))).get(0);
        assertThat(closed.get("id").asText()).isEqualTo(proposal);
        assertThat(closed.get("status").asText()).isEqualTo("CLOSED");
        send(g.meera(), put("/api/trips/{id}/itinerary/approval", g.id()), "{\"value\":\"APPROVE\"}")
                .andExpect(status().isConflict());
        send(g.dev(), post("/api/trips/{id}/proposals", g.id()),
                "{\"edit\":{\"type\":\"REMOVE\",\"itemId\":\"" + g.items().get("Vinayak") + "\"}}")
                .andExpect(status().isConflict());
        send(g.asha(), post("/api/trips/{id}/itinerary/edits", g.id()),
                "{\"type\":\"REMOVE\",\"itemId\":\"" + g.items().get("Vinayak") + "\"}")
                .andExpect(status().isConflict());

        JsonNode locked = decisions(g.meera(), g).get(0);
        assertThat(locked.get("kind").asText()).isEqualTo("PLAN_LOCKED");
        assertThat(locked.get("summary").asText()).contains("3 of 4 approved").contains("Not voted: Meera");

        // unlock (logged), change the plan directly: earlier approvals no longer count, so locking needs a reason
        send(g.asha(), post("/api/trips/{id}/itinerary/unlock", g.id()), "{\"reason\":\"Ferry times changed\"}")
                .andExpect(status().isOk());
        send(g.asha(), post("/api/trips/{id}/itinerary/edits", g.id()),
                "{\"type\":\"REMOVE\",\"itemId\":\"" + g.items().get("Vinayak") + "\"}").andExpect(status().isOk());
        gate = body(as(g.asha(), get("/api/trips/{id}/itinerary/approval", g.id())));
        assertThat(gate.at("/tally/approvals").asInt()).isZero();
        gate = body(send(g.asha(), post("/api/trips/{id}/itinerary/lock", g.id()),
                "{\"reason\":\"Booking deadline tonight\"}").andExpect(status().isOk()));
        assertThat(gate.get("status").asText()).isEqualTo("LOCKED");

        JsonNode log = decisions(g.kabir(), g);
        assertThat(names(log, "/kind")).containsExactly("LOCK_OVERRIDE", "PLAN_UNLOCKED", "PLAN_LOCKED");
        assertThat(log.get(0).get("reason").asText()).isEqualTo("Booking deadline tonight");
        assertThat(log.get(0).get("summary").asText()).contains("without the group's quorum")
                .contains("Booking deadline tonight");
        assertThat(log.get(1).get("summary").asText()).contains("Ferry times changed");
        assertThat(log.get(0).get("seq").asInt()).isEqualTo(3);

        as(newUser("Stranger"), get("/api/trips/{id}/decisions", g.id())).andExpect(status().isNotFound());
    }

    @Test
    void loweringTheQuorumSettlesOpenVotes() throws Exception {
        Group g = group();
        String id = body(send(g.meera(), post("/api/trips/{id}/proposals", g.id()),
                "{\"edit\":{\"type\":\"REMOVE\",\"itemId\":\"" + g.items().get("Anjuna Market") + "\"}}"))
                .get("id").asText();
        vote(g.dev(), g, id, "APPROVE");
        send(g.dev(), put("/api/trips/{id}/quorum", g.id()), "{\"minApprovals\":2}").andExpect(status().isForbidden());
        JsonNode trip = body(send(g.asha(), put("/api/trips/{id}/quorum", g.id()), "{\"minApprovals\":2}")
                .andExpect(status().isOk()));
        assertThat(trip.at("/quorum/requiredApprovals").asInt()).isEqualTo(2);
        JsonNode p = body(as(g.meera(), get("/api/trips/{id}/proposals", g.id()))).get(0);
        assertThat(p.get("status").asText()).isEqualTo("APPROVED");
        assertThat(dayOf(itinerary(g), "Anjuna Market")).isEqualTo(-1);

        // back to a simple majority
        trip = body(send(g.asha(), put("/api/trips/{id}/quorum", g.id()), "{\"minApprovals\":null}"));
        assertThat(trip.at("/quorum/requiredApprovals").asInt()).isEqualTo(3);
        // and a vote can be taken back while the proposal is open
        String other = body(send(g.dev(), post("/api/trips/{id}/proposals", g.id()),
                "{\"edit\":{\"type\":\"REMOVE\",\"itemId\":\"" + g.items().get("Palolem") + "\"}}"))
                .get("id").asText();
        JsonNode cleared = body(as(g.dev(), delete("/api/trips/{id}/proposals/{p}/vote", g.id(), other))
                .andExpect(status().isOk()));
        assertThat(cleared.get("myVote").isNull()).isTrue();
        assertThat(cleared.at("/tally/approvals").asInt()).isZero();
    }
}
