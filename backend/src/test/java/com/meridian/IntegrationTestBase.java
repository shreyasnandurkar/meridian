package com.meridian;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/** Shared Spring context + one Postgres container for all integration tests. AI is off and OpenStreetMap is faked so tests are deterministic. */
@SpringBootTest(properties = "meridian.ai.enabled=false")
@AutoConfigureMockMvc
@Import(FakePlacesClient.Config.class)
public abstract class IntegrationTestBase {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    protected record TestUser(UUID id, String email, String token) {
        public String bearer() {
            return "Bearer " + token;
        }
    }

    /** Signs up a fresh user with a unique email. */
    protected TestUser newUser(String name) throws Exception {
        String email = name.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String body = mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of(
                                "email", email, "password", "password123", "displayName", name))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(body);
        return new TestUser(UUID.fromString(node.at("/user/id").asText()), email, node.get("token").asText());
    }

    /** Polls an AI job until it leaves QUEUED/RUNNING. */
    protected JsonNode waitForJob(TestUser user, String jobId) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (true) {
            JsonNode job = read(mvc.perform(get("/api/ai/jobs/{id}", jobId).header("Authorization", user.bearer()))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            String s = job.get("status").asText();
            if (!s.equals("QUEUED") && !s.equals("RUNNING")) return job;
            if (Instant.now().isAfter(deadline)) throw new AssertionError("job did not finish: " + job);
            Thread.sleep(50);
        }
    }

    protected JsonNode read(String body) throws Exception {
        return json.readTree(body);
    }
}
