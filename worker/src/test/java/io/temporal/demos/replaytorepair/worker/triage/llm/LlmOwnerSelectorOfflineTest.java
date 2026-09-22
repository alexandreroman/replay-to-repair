package io.temporal.demos.replaytorepair.worker.triage.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.List;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ClassPathResource;

import io.temporal.demos.replaytorepair.worker.triage.Issue;

/**
 * Offline unit test for {@link LlmOwnerSelector}: no network call and no API key, unlike the live
 * {@link OwnerSelectorTest}. A mocked {@link ChatModel} returns a canned {@link Generation} shaped
 * the way {@code AnthropicChatModel} shapes an answer, behind the real {@link ChatClient} the
 * production {@link ChatClientConfiguration} builds, so the selection runs through the real
 * advisor chain and the real {@code entity(...)} conversion.
 */
class LlmOwnerSelectorOfflineTest {
    private static final String MODEL = "claude-offline-test";

    private static final ChatGenerationMetadata END_TURN =
            ChatGenerationMetadata.builder().finishReason("end_turn").build();

    private static final String SELECTION_JSON =
            "{\"owner\": \"carol\", \"reason\": \"Session handling is a security concern\"}";

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private final Issue issue = new Issue(
            "SEC-1",
            "Session tokens are not invalidated on logout",
            "After a user logs out, the issued JWT stays valid until it expires");

    @Test
    void readsTheOwnerAndTheReasonFromTheModelsAnswer() {
        var selector = selectorRespondingWith(answerGeneration(SELECTION_JSON));

        assertThat(selector.select(issue)).hasValueSatisfying(assignment -> {
            assertThat(assignment.owner()).isEqualTo("carol");
            assertThat(assignment.reason()).isEqualTo("Session handling is a security concern");
        });
    }

    // The generation AnthropicChatModel#buildGenerations appends after its content-block loop,
    // holding the text accumulated from every text block — the JSON the converter reads.
    private static Generation answerGeneration(String text) {
        return new Generation(AssistantMessage.builder().content(text).build(), END_TURN);
    }

    private LlmOwnerSelector selectorRespondingWith(Generation... generations) {
        var chatModel = mock(ChatModel.class);
        var options = AnthropicChatOptions.builder().model(MODEL).build();
        // getOptions() seeds the prompt sent on every call; getDefaultOptions() is where the
        // selector reads the model id for its "model" meter tag.
        given(chatModel.getOptions()).willReturn(options);
        given(chatModel.getDefaultOptions()).willReturn(options);
        given(chatModel.call(any(Prompt.class))).willReturn(new ChatResponse(List.of(generations)));
        return new LlmOwnerSelector(chatClientFor(chatModel, options), chatModel, meterRegistry);
    }

    // Built through the production configuration rather than by hand, so the tools and the default
    // options it puts on the shared bean are the ones under test here.
    private static ChatClient chatClientFor(ChatModel chatModel, AnthropicChatOptions options) {
        var configuration = new ChatClientConfiguration();
        var skills = configuration.skills(List.of(new ClassPathResource("skills")));
        return configuration.chatClient(ChatClient.builder(chatModel), skills, options);
    }
}
