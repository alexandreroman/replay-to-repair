package io.temporal.demos.replaytorepair.worker.triage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Spring Boot test for {@link OwnerSelector} using the real Spring-wired
 * {@link org.springframework.ai.chat.client.ChatClient}. It verifies that backend, API and
 * relational-database issues — alice's domain in the triage roster — are assigned to alice.
 */
@SpringBootTest
@ActiveProfiles("test")
class OwnerSelectorTest {
    // The real Spring-wired bean, with the ChatClient from the application context.
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
                                + "slow unindexed query holds connections open for several seconds"),
                new Issue(
                        "API-2",
                        "Paginated products API serializes the wrong page",
                        "The GET /api/products endpoint ignores the page cursor and always serializes the "
                                + "first page in its JSON response"));
    }

    @ParameterizedTest
    @MethodSource("backendIssues")
    void assignsBackendIssuesToAlice(Issue issue) {
        assertThat(ownerSelector.select(issue).map(OwnerAssignment::owner)).hasValue("alice");
    }
}
