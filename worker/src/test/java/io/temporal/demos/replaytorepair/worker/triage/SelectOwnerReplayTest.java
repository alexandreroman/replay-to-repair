package io.temporal.demos.replaytorepair.worker.triage;

import java.nio.file.Path;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Reproduces the production bug from the captured event history, the "repair" half of the demo.
 *
 * <p>It replays the exact {@link Issue} that flows into the failing {@code selectOwner} activity in
 * production, runs the activity directly against a stubbed LLM that correctly answers
 * {@code "carol"} (the security specialist), and asserts the assignment.
 *
 * <p>With the committed debug line present this is RED ({@code selectOwner} returns {@code "alice"}
 * without consulting the LLM). Removing that line makes it GREEN. Disabled so the normal build
 * stays green while the bug is committed; run it from the IDE to walk through the demo.
 */
@Disabled("Replay demo: run from the IDE to reproduce the production bug, then remove the debug "
        + "line in TriageActivitiesImpl.selectOwner to go green")
class SelectOwnerReplayTest {
    @Test
    void assignsSecurityIssueToSecuritySpecialist() {
        var input = SelectOwnerHistoryExtractor.fromFile(
                Path.of("src/test/resources/history/select-owner-failure.json"));

        // A manual deep-stub mock (not @ExtendWith(MockitoExtension)) avoids strict-stubbing errors.
        var chatClient = Mockito.mock(ChatClient.class, Mockito.RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString()).user(anyString()).call()
                .entity(TriageActivitiesImpl.OwnerSelection.class))
                .thenReturn(new TriageActivitiesImpl.OwnerSelection("carol"));
        var activities = new TriageActivitiesImpl(chatClient);

        var owner = activities.selectOwner(input.issue());

        assertThat(owner)
                .as("A SQL-injection issue must be routed to the security specialist (carol), "
                        + "not to the fixed owner returned by the committed debug line")
                .isEqualTo("carol");
    }
}
