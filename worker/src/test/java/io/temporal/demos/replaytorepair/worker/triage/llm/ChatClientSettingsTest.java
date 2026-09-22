package io.temporal.demos.replaytorepair.worker.triage.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Pins the one chat-option {@link ChatClientConfiguration} has an opinion about: extended thinking
 * off. Everything else an owner-selection call carries — the model id, the 20s call budget, the
 * disabled SDK retry — comes from {@code spring.ai.anthropic.*} and reaches the request because
 * Spring AI merges these options onto the model's own defaults per call.
 *
 * <p>Thinking stays off because a {@code thinking} block in the reply lands ahead of the answer in
 * the response Spring AI assembles, and the structured-output converter then parses it instead of
 * the JSON — an intermittent {@code MismatchedInputException} inside SelectOwner that Temporal's
 * Activity retry masks rather than reports. It is also the cheaper call: a thinking block roughly
 * triples the completion tokens billed for the same one-line answer.
 *
 * <p>Offline by design: building the options performs no I/O, so no API key is needed and nothing
 * reaches the model.
 */
@SpringBootTest
@ActiveProfiles("test")
class ChatClientSettingsTest {
    @Autowired
    private AnthropicChatOptions chatOptions;

    @Test
    void extendedThinkingIsDisabled() {
        assertThat(chatOptions.getThinking()).isNotNull();
        assertThat(chatOptions.getThinking().isDisabled()).isTrue();
    }
}
