package com.meridian;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** A1 end to end with AI disabled: the quiz alone must yield a valid profile. */
class ProfileIntegrationTest extends IntegrationTestBase {

    static final String QUIZ = """
            {"interestRatings":{"beaches":4,"nightlife":1,"history":3,"local_food":4},
             "pace":"slow","budget":{"stay":"mid","food":"low","activities":"mid"},
             "crowdTolerance":20,"hiddenGems":80,"dietary":["vegetarian"],"accessNeeds":[],
             "mustHaves":["Sea view"],"dealbreakers":[],
             "idealTrip":"Lazy mornings at a cafe, then a kayak trip to spot dolphins."}""";

    @Test
    void quizBuildsRuleBasedProfileWhenAiIsOff() throws Exception {
        TestUser user = newUser("Chen");
        mvc.perform(get("/api/profile").header("Authorization", user.bearer())).andExpect(status().isNoContent());

        JsonNode started = read(mvc.perform(post("/api/profile/quiz").header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(QUIZ))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString());

        JsonNode job = waitForJob(user, started.get("id").asText());
        assertThat(job.get("status").asText()).isEqualTo("SUCCEEDED");
        JsonNode result = job.get("result");
        assertThat(result.at("/provenance/source").asText()).isEqualTo("rules");
        assertThat(result.at("/provenance/note").asText()).contains("AI is turned off");
        assertThat(result.at("/profile/interests/beaches").asDouble()).isEqualTo(1.0);
        assertThat(result.at("/profile/interests/cafes").asDouble()).isEqualTo(0.6);
        assertThat(result.at("/profile/interests/wildlife").asDouble()).isEqualTo(0.6);
        assertThat(result.at("/profile/summary").asText()).startsWith("Slow-paced traveler");

        // the saved profile is editable
        ObjectNode profile = (ObjectNode) result.get("profile");
        profile.put("pace", "packed");
        mvc.perform(put("/api/profile").header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(profile)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.edited").value(true))
                .andExpect(jsonPath("$.profile.pace").value("packed"));

        profile.put("pace", "sprint");
        mvc.perform(put("/api/profile").header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(profile)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0]").value("Unknown pace: sprint"));
    }

    @Test
    void quizRejectsUnknownVocabulary() throws Exception {
        TestUser user = newUser("Dev");
        mvc.perform(post("/api/profile/quiz").header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(QUIZ.replace("\"beaches\"", "\"casinos\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0]").value("Unknown interest: casinos"));
    }

    @Test
    void jobsAreOnlyVisibleToTheirOwner() throws Exception {
        TestUser owner = newUser("Esha");
        TestUser other = newUser("Finn");
        JsonNode started = read(mvc.perform(post("/api/profile/quiz").header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(QUIZ))
                .andReturn().getResponse().getContentAsString());
        mvc.perform(get("/api/ai/jobs/{id}", started.get("id").asText()).header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void vocabularyIsServed() throws Exception {
        TestUser user = newUser("Gia");
        mvc.perform(get("/api/profile/vocabulary").header("Authorization", user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interests.length()").value(22))
                .andExpect(jsonPath("$.paces[0].id").value("slow"));
    }
}
