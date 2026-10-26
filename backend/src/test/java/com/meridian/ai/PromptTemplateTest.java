package com.meridian.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PromptTemplateTest {

    private static final String SOURCE = """
            ---
            version: demo-v2
            temperature: 0.25
            ---
            ## system
            You are terse.

            ## user
            Example in

            ## assistant
            {"ok":true}

            ## user
            Real input: {{input}}
            """;

    @Test
    void parsesFrontMatterAndSectionsInOrder() {
        PromptTemplate t = PromptTemplate.parse("demo", SOURCE);

        assertThat(t.version()).isEqualTo("demo-v2");
        assertThat(t.temperature()).isEqualTo(0.25);
        assertThat(t.messages()).extracting(PromptTemplate.Message::role)
                .containsExactly("system", "user", "assistant", "user");
        assertThat(t.render(Map.of("input", "$5 & \\ok")).getLast().content()).isEqualTo("Real input: $5 & \\ok");
    }

    @Test
    void missingVariableIsAnError() {
        PromptTemplate t = PromptTemplate.parse("demo", SOURCE);
        assertThatThrownBy(() -> t.render(Map.of())).hasMessageContaining("input");
    }

    @Test
    void bundledPromptsParse() {
        PromptTemplate profile = new PromptLibrary().get("profile-extract");
        assertThat(profile.version()).isEqualTo("profile-extract-v1");
        assertThat(profile.temperature()).isBetween(0.2, 0.4);
        assertThat(profile.messages().getLast().content()).contains("{{description}}");
    }

    @Test
    void cleanStripsThinkingAndFences() {
        assertThat(OpenAiCompatibleAiClient.clean("<think>hmm</think>\n```json\n{\"a\":1}\n```")).isEqualTo("{\"a\":1}");
    }
}
