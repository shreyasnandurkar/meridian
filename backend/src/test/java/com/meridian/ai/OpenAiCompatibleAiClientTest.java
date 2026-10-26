package com.meridian.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.config.MeridianProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Runs the client against a fake OpenAI-compatible server. */
class OpenAiCompatibleAiClientTest {

    record Answer(String word, int count) {}

    private final ObjectMapper json = new ObjectMapper();
    private final AiCallLogRepository callLog = mock(AiCallLogRepository.class);
    private final Deque<String> replies = new ConcurrentLinkedDeque<>();
    private final List<JsonNode> requests = new ArrayList<>();
    private HttpServer server;
    private OpenAiCompatibleAiClient client;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            synchronized (requests) {
                requests.add(json.readTree(exchange.getRequestBody()));
            }
            String content = replies.isEmpty() ? "{}" : replies.pop();
            byte[] body = json.writeValueAsBytes(Map.of(
                    "choices", List.of(Map.of("message", Map.of("role", "assistant", "content", content))),
                    "usage", Map.of("prompt_tokens", 120, "completion_tokens", 15)));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        client = clientFor(true, "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/");
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private OpenAiCompatibleAiClient clientFor(boolean enabled, String baseUrl) {
        return new OpenAiCompatibleAiClient(
                new MeridianProperties.Ai(enabled, baseUrl, "test-key", "tiny-model", 1, 5), json, callLog);
    }

    private AiRequest<Answer> request() {
        PromptTemplate prompt = PromptTemplate.parse("t", """
                ---
                version: t-v1
                temperature: 0.2
                ---
                ## system
                Count words.
                ## user
                {{text}}
                """);
        return new AiRequest<>("test", prompt, Map.of("text", "hello hello"), Map.of("type", "object"), Answer.class,
                a -> a.count() < 0 ? List.of("count must be >= 0") : List.of());
    }

    @Test
    void validFirstAnswerIsReturnedAndLogged() {
        replies.add("{\"word\":\"hello\",\"count\":2}");

        AiResult<Answer> result = client.generate(request());

        assertThat(result.value()).isEqualTo(new Answer("hello", 2));
        assertThat(result.attempts()).isEqualTo(1);
        assertThat(result.promptVersion()).isEqualTo("t-v1");
        JsonNode sent = requests.getFirst();
        assertThat(sent.get("model").asText()).isEqualTo("tiny-model");
        assertThat(sent.get("temperature").asDouble()).isEqualTo(0.2);
        assertThat(sent.at("/response_format/type").asText()).isEqualTo("json_schema");
        assertThat(sent.at("/response_format/json_schema/strict").asBoolean()).isTrue();
        ArgumentCaptor<AiCallLog> logged = ArgumentCaptor.forClass(AiCallLog.class);
        verify(callLog).save(logged.capture());
        assertThat(logged.getValue().getStatus()).isEqualTo("VALID");
    }

    @Test
    void invalidOutputIsRetriedWithTheProblemsFedBack() {
        replies.add("not json at all");
        replies.add("{\"word\":\"hello\",\"count\":-1}");
        replies.add("{\"word\":\"hello\",\"count\":2}");

        AiResult<Answer> result = client.generate(request());

        assertThat(result.attempts()).isEqualTo(3);
        assertThat(requests).hasSize(3);
        JsonNode lastMessages = requests.get(2).get("messages");
        assertThat(lastMessages.get(lastMessages.size() - 1).get("content").asText()).contains("count must be >= 0");
        verify(callLog, times(3)).save(any());
    }

    @Test
    void givesUpAfterTwoRetries() {
        replies.add("{\"word\":\"x\",\"count\":-1}");
        replies.add("{\"word\":\"x\",\"count\":-1}");
        replies.add("{\"word\":\"x\",\"count\":-1}");
        replies.add("{\"word\":\"x\",\"count\":2}");

        assertThatThrownBy(() -> client.generate(request()))
                .isInstanceOfSatisfying(AiException.class,
                        e -> assertThat(e.reason()).isEqualTo(AiException.Reason.INVALID_OUTPUT));
        assertThat(requests).hasSize(OpenAiCompatibleAiClient.MAX_ATTEMPTS);
    }

    @Test
    void disabledClientNeverCallsTheModel() {
        assertThatThrownBy(() -> clientFor(false, "http://127.0.0.1:1/v1").generate(request()))
                .isInstanceOfSatisfying(AiException.class,
                        e -> assertThat(e.reason()).isEqualTo(AiException.Reason.DISABLED));
        assertThat(requests).isEmpty();
    }

    @Test
    void unreachableHostIsReportedAsUnavailable() {
        server.stop(0);
        assertThatThrownBy(() -> client.generate(request()))
                .isInstanceOfSatisfying(AiException.class,
                        e -> assertThat(e.reason()).isEqualTo(AiException.Reason.UNAVAILABLE));
    }

    @Test
    void nonAsciiOutputRoundTrips() {
        replies.add("{\"word\":\"héllo\",\"count\":1}");
        assertThat(client.generate(request()).value().word()).isEqualTo("héllo");    }
}
