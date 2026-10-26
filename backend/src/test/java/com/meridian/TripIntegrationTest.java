package com.meridian;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class TripIntegrationTest extends IntegrationTestBase {

    private static final String GOA_TRIP = """
            {"name":"Goa with friends","destinationName":"Goa, India","destinationLat":15.3,"destinationLng":74.1,
             "startDate":"2026-12-10","endDate":"2026-12-14","currency":"INR","budgetTotal":60000}""";

    private String createTrip(TestUser organizer) throws Exception {
        String body = mvc.perform(post("/api/trips").header("Authorization", organizer.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(GOA_TRIP))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.myRole").value("ORGANIZER"))
                .andExpect(jsonPath("$.days").value(5))
                .andExpect(jsonPath("$.quorum.requiredApprovals").value(1))
                .andReturn().getResponse().getContentAsString();
        return read(body).get("id").asText();
    }

    private JsonNode createInvite(TestUser organizer, String tripId) throws Exception {
        return read(mvc.perform(post("/api/trips/{id}/invites", tripId).header("Authorization", organizer.bearer()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void inviteFlowAddsMemberAndEnforcesRoles() throws Exception {
        TestUser asha = newUser("Asha");
        TestUser ben = newUser("Ben");
        TestUser outsider = newUser("Olu");
        String tripId = createTrip(asha);

        JsonNode invite = createInvite(asha, tripId);
        String token = invite.get("token").asText();
        assertThat(invite.get("url").asText()).endsWith("/join/" + token);

        // public preview works without signing in
        mvc.perform(get("/api/invites/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripName").value("Goa with friends"))
                .andExpect(jsonPath("$.organizerName").value("Asha"));

        // joining needs sign-in, and is idempotent
        mvc.perform(post("/api/invites/{token}/accept", token)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/invites/{token}/accept", token).header("Authorization", ben.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value(tripId))
                .andExpect(jsonPath("$.alreadyMember").value(false));
        mvc.perform(post("/api/invites/{token}/accept", token).header("Authorization", ben.bearer()))
                .andExpect(jsonPath("$.alreadyMember").value(true));

        mvc.perform(get("/api/trips/{id}", tripId).header("Authorization", ben.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("MEMBER"))
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.members[1].displayName").value("Ben"))
                .andExpect(jsonPath("$.members[1].hasProfile").value(false))
                .andExpect(jsonPath("$.quorum.requiredApprovals").value(2));

        mvc.perform(get("/api/trips").header("Authorization", ben.bearer()))
                .andExpect(jsonPath("$[0].memberCount").value(2));

        // members cannot manage invites; outsiders cannot even see the trip
        mvc.perform(post("/api/trips/{id}/invites", tripId).header("Authorization", ben.bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/trips/{id}", tripId).header("Authorization", outsider.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/trips/{id}/members", tripId).header("Authorization", outsider.bearer()))
                .andExpect(status().isNotFound());

        // revoked links stop working
        mvc.perform(delete("/api/trips/{id}/invites/{inviteId}", tripId, invite.get("id").asText())
                        .header("Authorization", asha.bearer()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/invites/{token}", token)).andExpect(status().isNotFound());
        mvc.perform(post("/api/invites/{token}/accept", token).header("Authorization", outsider.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void createTripValidatesDates() throws Exception {
        TestUser asha = newUser("Asha");
        mvc.perform(post("/api/trips").header("Authorization", asha.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(GOA_TRIP.replace("2026-12-14", "2026-12-01")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/trips").header("Authorization", asha.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content(GOA_TRIP.replace("\"INR\"", "\"rupees\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.currency").exists());
    }

    @Test
    void preferencesArePrivatePerMember() throws Exception {
        TestUser asha = newUser("Asha");
        TestUser outsider = newUser("Olu");
        String tripId = createTrip(asha);

        mvc.perform(get("/api/trips/{id}/preferences/me", tripId).header("Authorization", asha.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budgetVisible").value(false))
                .andExpect(jsonPath("$.currency").value("INR"));

        mvc.perform(put("/api/trips/{id}/preferences/me", tripId).header("Authorization", asha.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"budgetMin\":10000,\"budgetMax\":20000,\"mustHaves\":[\"sea view\"],\"budgetVisible\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustHaves[0]").value("sea view"));

        mvc.perform(put("/api/trips/{id}/preferences/me", tripId).header("Authorization", asha.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"budgetMin\":30000,\"budgetMax\":20000,\"mustHaves\":[],\"budgetVisible\":false}"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/trips/{id}/preferences/me", tripId).header("Authorization", outsider.bearer()))
                .andExpect(status().isNotFound());
    }
}
