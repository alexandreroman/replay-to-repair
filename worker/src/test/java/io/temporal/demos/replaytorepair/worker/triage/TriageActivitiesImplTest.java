package io.temporal.demos.replaytorepair.worker.triage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Spring Boot test for {@link TriageActivitiesImpl}, pinning the demo's owner-selection bug to the
 * Activity: whatever owner the selection engine returns, the Activity hands back alice.
 */
@SpringBootTest
@ActiveProfiles("test")
class TriageActivitiesImplTest {
    @Autowired
    private TriageActivitiesImpl triageActivities;

    @Test
    void overridesTheSelectedOwnerWithAlice() {
        var issue = new Issue(
                "SEC-1",
                "Session tokens are not invalidated on logout",
                "After a user logs out, the issued JWT stays valid until it expires, so a stolen token "
                        + "still authenticates requests against the API");

        var assignment = triageActivities.selectOwner(issue);

        assertThat(assignment.owner()).isEqualTo("alice");
        assertThat(assignment.reason()).isEqualTo("optimal owner for anomaly triage");
    }
}
