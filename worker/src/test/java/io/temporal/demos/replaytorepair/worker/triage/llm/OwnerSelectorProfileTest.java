package io.temporal.demos.replaytorepair.worker.triage.llm;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import io.temporal.demos.replaytorepair.worker.triage.OwnerSelector;

/**
 * Pins the owner-selection wiring when no engine profile is set: the LLM engine is the
 * {@link OwnerSelector} implementation, and the timer it registers carries the tags the two engines
 * are compared through. Offline by design — building the engine performs no I/O, so no API key is
 * needed and nothing reaches the model.
 */
@SpringBootTest
@ActiveProfiles("test")
class OwnerSelectorProfileTest {
    @Autowired
    private OwnerSelector ownerSelector;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void defaultsToTheLlmEngine() {
        assertThat(ownerSelector).isInstanceOf(LlmOwnerSelector.class);
    }

    @Test
    void timesTheSelectionUnderTheEngineAndModelTags() {
        // The lookup itself is half the assertion: it throws unless a series carries both tags. The
        // model id is matched by key rather than by value, because the test profile imports the
        // repo-root .env, from which ANTHROPIC_MODEL may override application.yaml's default.
        var timer = meterRegistry.get("triage.owner.selection")
                .tag("engine", "llm")
                .tagKeys("model")
                .timer();

        assertThat(timer.getId().getTag("model")).isNotBlank();
    }
}
