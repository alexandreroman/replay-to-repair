package io.temporal.demos.replaytorepair.worker.triage.jev;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import io.temporal.demos.replaytorepair.worker.triage.Issue;
import io.temporal.demos.replaytorepair.worker.triage.OwnerAssignment;
import io.temporal.demos.replaytorepair.worker.triage.OwnerSelector;

/**
 * Spring Boot test for {@link JevOwnerSelector}, calling Jev through TypeSafe's own API for real. It
 * verifies that backend, API and relational-database issues — alice's domain in the triage roster —
 * are assigned to alice, and that a security issue goes to carol instead.
 */
@SpringBootTest
@ActiveProfiles({"test", "jev"})
@Tag("live")
class JevOwnerSelectorTest {
    @Autowired
    private OwnerSelector ownerSelector;

    static Stream<Issue> backendIssues() {
        return Stream.of(
                new Issue(
                        "API-1",
                        "Checkout endpoint returns HTTP 500 under load",
                        "The POST /api/checkout endpoint throws NullPointerException once concurrent "
                                + "requests spike, and the order service returns 500 to the client"),
                new Issue(
                        "DB-1",
                        "Connection pool exhausted on the orders database",
                        "HikariCP times out acquiring a connection to the PostgreSQL orders database; a "
                                + "slow unindexed query holds connections open for several seconds"));
    }

    @Test
    void usesTheJevEngine() {
        assertThat(ownerSelector).isInstanceOf(JevOwnerSelector.class);
    }

    @ParameterizedTest
    @MethodSource("backendIssues")
    void assignsBackendIssuesToAlice(Issue issue) {
        var assignment = ownerSelector.select(issue).orElseThrow();
        assertThat(assignment.owner()).isEqualTo("alice");
        assertThat(assignment.reason()).contains("backend").contains("confidence");
        assertThat(assignment.reason()).matches(".*\\(confidence \\d\\.\\d{2}\\)");
    }

    @Test
    void assignsSecurityIssueToCarol() {
        var issue = new Issue(
                "SEC-1",
                "Session tokens are not invalidated on logout",
                "After a user logs out, the issued JWT stays valid until it expires, so a stolen token "
                        + "still authenticates requests against the API");
        assertThat(ownerSelector.select(issue).map(OwnerAssignment::owner)).hasValue("carol");
    }
}
