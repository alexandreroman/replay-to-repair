package io.temporal.demos.replaytorepair.worker.triage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Verifies that the Spring AI engine is the owner-selection implementation when no engine profile is set. */
@SpringBootTest
@ActiveProfiles("test")
class OwnerSelectorProfileTest {
    @Autowired
    private OwnerSelector ownerSelector;

    @Test
    void defaultsToTheSpringAiEngine() {
        assertThat(ownerSelector).isInstanceOf(SpringAiOwnerSelector.class);
    }
}
