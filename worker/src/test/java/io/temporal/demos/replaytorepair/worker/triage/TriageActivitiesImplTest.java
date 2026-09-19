package io.temporal.demos.replaytorepair.worker.triage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Spring Boot test for {@link TriageActivitiesImpl}, pinning the demo's owner-selection bug to the
 * Activity: whatever owner the selection engine returns, the Activity hands back alice. The
 * selection engine is mocked to return a different owner, so the assertion genuinely proves the
 * override instead of coinciding with a real engine's own answer.
 */
@SpringBootTest
@ActiveProfiles("test")
class TriageActivitiesImplTest {
    @Autowired
    private TriageActivitiesImpl triageActivities;

    @MockitoBean
    private OwnerSelector ownerSelector;

    @Test
    void overridesTheSelectedOwnerWithAlice() {
        var issue = new Issue(
                "SEC-1",
                "Session tokens are not invalidated on logout",
                "After a user logs out, the issued JWT stays valid until it expires, so a stolen token "
                        + "still authenticates requests against the API");
        when(ownerSelector.select(issue)).thenReturn(Optional.of(new OwnerAssignment("carol", "security specialist")));

        var assignment = triageActivities.selectOwner(issue);

        assertThat(assignment.owner()).isEqualTo("alice");
        assertThat(assignment.reason()).isEqualTo("optimal owner for anomaly triage");
    }
}
