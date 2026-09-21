package io.temporal.demos.replaytorepair.worker.triage.jev.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Spring Boot test for {@link JevClient}, calling Jev through TypeSafe's own API for real. It is
 * what makes the three question and answer shapes trustworthy beyond the documentation the offline
 * {@link JevClientOfflineTest} pins: the canned bodies there only prove the client binds the format
 * this test observes the live API using.
 *
 * <p>The three questions travel in a single call, both because that is how a caller batches them and
 * because the assertions then describe one decision. They check shapes and ranges rather than what
 * the model picked, so sampling noise cannot fail the test.
 */
@SpringBootTest
@ActiveProfiles({"test", "jev"})
@Tag("live")
class JevClientTest {
    private static final String OWNER = "owner";
    private static final String URGENCY = "urgency";
    private static final String IS_REGRESSION = "is_regression";

    private static final Map<String, String> STATE = Map.of(
            "issue_title", "Checkout endpoint returns HTTP 500 under load",
            "issue_description", "The POST /api/checkout endpoint throws NullPointerException once concurrent "
                    + "requests spike, and the order service returns 500 to the client");

    private static final Map<String, String> OWNER_OPTIONS = Map.of(
            "alice", "Specialties: backend, APIs, relational databases.",
            "carol", "Specialties: security, cryptography.",
            "dave", "Specialties: frontend, accessibility, UI.");

    private static final List<String> URGENCY_LEVELS = List.of(
            "Can wait a sprint", "Should be handled this week", "Needs attention today");

    @Autowired
    private JevClient jevClient;

    @Test
    void answersAChoiceAScoreAndANoulInASingleCall() {
        var answers = jevClient.ask(STATE, Map.of(
                OWNER, new JevQuestion.Choice("Which owner should handle this issue?", OWNER_OPTIONS),
                URGENCY, new JevQuestion.Score("How urgent is this issue?", URGENCY_LEVELS),
                IS_REGRESSION, new JevQuestion.Noul("Is this issue a regression?")));

        assertThat(answers).containsOnlyKeys(OWNER, URGENCY, IS_REGRESSION);
        assertThat(answers.get(OWNER))
                .isInstanceOfSatisfying(JevAnswer.Choice.class, JevClientTest::assertOwnerChoice);
        assertThat(answers.get(URGENCY))
                .isInstanceOfSatisfying(JevAnswer.Score.class, JevClientTest::assertUrgencyScore);
        assertThat(answers.get(IS_REGRESSION))
                .isInstanceOfSatisfying(JevAnswer.Noul.class, JevClientTest::assertRegressionNoul);
    }

    private static void assertOwnerChoice(JevAnswer.Choice answer) {
        assertThat(answer.choice()).isIn(OWNER_OPTIONS.keySet());
        assertThat(answer.probabilities()).containsOnlyKeys(OWNER_OPTIONS.keySet());
        assertThat(answer.confidence()).isBetween(0.0, 1.0);
    }

    private static void assertUrgencyScore(JevAnswer.Score answer) {
        // The scale is zero-based, so the top level sits at the index of the last criterion.
        assertThat(answer.score()).isBetween(0.0, (double) (URGENCY_LEVELS.size() - 1));
        assertThat(answer.legend()).hasSameSizeAs(URGENCY_LEVELS);
        assertThat(answer.confidence()).isBetween(0.0, 1.0);
    }

    private static void assertRegressionNoul(JevAnswer.Noul answer) {
        assertThat(answer.noul()).isBetween(0.0, 1.0);
    }
}
