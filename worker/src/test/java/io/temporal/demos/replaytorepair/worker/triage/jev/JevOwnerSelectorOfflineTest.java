package io.temporal.demos.replaytorepair.worker.triage.jev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;
import org.springaicommunity.typesafe.RetryPolicy;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.exception.TypeSafeApiResponseValidationException;
import org.springaicommunity.typesafe.exception.TypeSafeInternalServerException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

import io.temporal.demos.replaytorepair.worker.triage.Issue;
import io.temporal.demos.replaytorepair.worker.triage.OwnerSelector;

/**
 * Offline unit test for {@link JevOwnerSelector}: no network call and no real API key, unlike the live
 * {@link JevOwnerSelectorTest}. A {@link MockRestServiceServer} stands in for TypeSafe's systemone
 * endpoint behind a hand-built {@link TypeSafeClient}, and each test feeds it a canned JSON body to
 * exercise the branches {@link OwnerSelector#select} distinguishes.
 */
class JevOwnerSelectorOfflineTest {
    private static final String BASE_URL = "https://example.invalid";
    private static final String SYSTEMONE_URL = BASE_URL + "/v1/systemone";

    // A second copy of the instructions the selector sends, so a reworded question fails here.
    private static final String EXPECTED_INSTRUCTIONS = "Which owner should handle this issue? Pick the owner "
            + "whose specialties most directly cover it; break ties by preference.";

    // Deliberately terse entries: the request test below pins the shape and the order of the body,
    // not the wording of a roster entry.
    private static final TriageRosterProperties TERSE_ROSTER = new TriageRosterProperties(List.of(
            new TriageRosterProperties.Owner("alice", List.of("backend"), List.of("REST design")),
            new TriageRosterProperties.Owner("carol", List.of("security"), List.of("audits"))));

    // The response TypeSafe's systemone endpoint returned when probed live, confidence lowered to
    // 0.87 so it is distinguishable from a round-number stub. Pins the real wire shape: a "type"
    // field inside the answer and integer probabilities that must still bind to Double.
    private static final String HAPPY_PATH_BODY = """
            {
              "model": "jev-1.13.0",
              "answers": {
                "owner": {
                  "type": "choice",
                  "choice": "alice",
                  "probabilities": {"none": 0, "alice": 1, "carol": 0},
                  "confidence": 0.87
                }
              },
              "usage": {"input_tokens": 406, "output_tokens": 39}
            }
            """;

    // Confirms the branch still tolerates unknown root fields even though the API does not send
    // "id"/"provider" fields today; @JsonIgnoreProperties(ignoreUnknown = true) must keep binding
    // regardless of what a future revision of the API adds at the root.
    private static final String UNKNOWN_ROOT_FIELDS_BODY = """
            {
              "model": "jev-1.13.0",
              "id": "gen-dec-1",
              "provider": "TypeSafe",
              "answers": {
                "owner": {
                  "type": "choice",
                  "choice": "alice",
                  "probabilities": {"none": 0, "alice": 1, "carol": 0},
                  "confidence": 0.87
                }
              },
              "usage": {"input_tokens": 406, "output_tokens": 39}
            }
            """;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private final TriageRosterProperties roster = new TriageRosterProperties(List.of(
            new TriageRosterProperties.Owner(
                    "alice",
                    List.of("backend", "APIs", "relational databases"),
                    List.of("REST design", "service integration", "query tuning"))));

    private final Issue issue = new Issue(
            "API-1",
            "Checkout endpoint returns HTTP 500 under load",
            "The POST /api/checkout endpoint throws NullPointerException once concurrent requests spike");

    @Test
    void choiceAliceAssignsAliceWithReasonAndConfidence() {
        var selector = selectorRespondingWith(withSuccess(HAPPY_PATH_BODY, MediaType.APPLICATION_JSON));
        var assignment = selector.select(issue).orElseThrow();
        assertThat(assignment.owner()).isEqualTo("alice");
        assertThat(assignment.reason())
                .contains("backend, APIs, relational databases")
                .contains("(confidence 0.87)");
    }

    @Test
    void timesTheSelectionUnderTheEngineAndModelTags() {
        var selector = selectorRespondingWith(withSuccess(HAPPY_PATH_BODY, MediaType.APPLICATION_JSON));

        selector.select(issue).orElseThrow();

        assertThat(jevTimerCount()).isEqualTo(1);
    }

    @Test
    void theQuestionOffersTheRosterInOrderFollowedByTheNoneOption() {
        var expectedBody = """
                {
                  "model": "jev-latest",
                  "state": {
                    "issue_title": "%s",
                    "issue_description": "%s"
                  },
                  "questions": {
                    "owner": {
                      "type": "choice",
                      "instructions": "%s",
                      "criteria": {
                        "alice": "Specialties: backend. Prefers: REST design.",
                        "carol": "Specialties: security. Prefers: audits.",
                        "none": "No owner's specialties reasonably cover this issue."
                      }
                    }
                  }
                }
                """.formatted(issue.issueTitle(), issue.issueDescription(), EXPECTED_INSTRUCTIONS);
        var builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build()
                .expect(requestTo(SYSTEMONE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(expectedBody, JsonCompareMode.STRICT))
                // A JSON comparison ignores the order of object keys, so the order in which the
                // options reach the model is pinned on the raw body instead.
                .andExpect(content().string(stringContainsInOrder("\"alice\"", "\"carol\"", "\"none\"")))
                .andRespond(withSuccess(HAPPY_PATH_BODY, MediaType.APPLICATION_JSON));
        var selector = new JevOwnerSelector(clientBoundTo(builder), TERSE_ROSTER, meterRegistry);
        assertThat(selector.select(issue)).isPresent();
    }

    @Test
    void unknownRootFieldsAreTolerated() {
        var selector = selectorRespondingWith(withSuccess(UNKNOWN_ROOT_FIELDS_BODY, MediaType.APPLICATION_JSON));
        var assignment = selector.select(issue).orElseThrow();
        assertThat(assignment.owner()).isEqualTo("alice");
    }

    @Test
    void choiceNoneYieldsEmptyOptional() {
        var body = """
                {"answers": {"owner": {"type": "choice", "choice": "none", "confidence": 0.99}}}
                """;
        var selector = selectorRespondingWith(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThat(selector.select(issue)).isEmpty();
    }

    @Test
    void blankChoiceThrows() {
        var body = """
                {"answers": {"owner": {"type": "choice", "choice": "", "confidence": 0.5}}}
                """;
        var selector = selectorRespondingWith(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> selector.select(issue)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void missingChoiceThrows() {
        var body = """
                {"answers": {"owner": {"type": "choice", "confidence": 0.5}}}
                """;
        var selector = selectorRespondingWith(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> selector.select(issue)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void offRosterChoiceThrows() {
        var body = """
                {"answers": {"owner": {"type": "choice", "choice": "mallory", "confidence": 0.5}}}
                """;
        var selector = selectorRespondingWith(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> selector.select(issue)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void answerlessResponseThrows() {
        // The SDK rejects a response carrying no answers before the selector reads one.
        var body = """
                {"model": "jev-1.13.0"}
                """;
        var selector = selectorRespondingWith(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> selector.select(issue))
                .isInstanceOf(TypeSafeApiResponseValidationException.class);
    }

    @Test
    void httpServerErrorThrows() {
        var selector = selectorRespondingWith(withServerError());
        assertThatThrownBy(() -> selector.select(issue)).isInstanceOf(TypeSafeInternalServerException.class);
        // The attempt is timed even though it fails, so a broken engine shows up in the series.
        assertThat(jevTimerCount()).isEqualTo(1);
    }

    // Looking the timer up by both tags fails the test unless the series carries them: the engine
    // it was recorded by, and the model id the client below is built with.
    private long jevTimerCount() {
        return meterRegistry.get("triage.owner.selection")
                .tag("engine", "jev")
                .tag("model", "jev-latest")
                .timer()
                .count();
    }

    private JevOwnerSelector selectorRespondingWith(ResponseCreator responseCreator) {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(SYSTEMONE_URL)).andRespond(responseCreator);
        return new JevOwnerSelector(clientBoundTo(builder), roster, meterRegistry);
    }

    // RetryPolicy.noRetry() keeps a failing response to the single request the mock server
    // expects; the default policy would retry a 5xx and overshoot that expectation.
    private static TypeSafeClient clientBoundTo(RestClient.Builder builder) {
        return TypeSafeClient.builder()
                .apiKey("dummy-key")
                .baseUrl(BASE_URL)
                .defaultModel("jev-latest")
                .retryPolicy(RetryPolicy.noRetry())
                .restClientBuilder(builder)
                .build();
    }
}
