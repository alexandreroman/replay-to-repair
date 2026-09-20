package io.temporal.demos.replaytorepair.worker.triage.jev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import io.temporal.demos.replaytorepair.worker.triage.Issue;
import io.temporal.demos.replaytorepair.worker.triage.OwnerSelector;

/**
 * Offline unit test for {@link JevOwnerSelector}: no network call and no API key, unlike the live
 * {@link JevOwnerSelectorTest}. A {@link MockRestServiceServer} stands in for TypeSafe's systemone
 * endpoint, and each test feeds it a canned JSON body to exercise the branches {@link
 * OwnerSelector#select} distinguishes.
 */
class JevOwnerSelectorOfflineTest {
    private static final String SYSTEMONE_URL = "https://example.invalid/systemone";

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
    void missingAnswersKeyThrows() {
        var body = """
                {"model": "jev-1.13.0"}
                """;
        var selector = selectorRespondingWith(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> selector.select(issue)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void httpServerErrorThrows() {
        var selector = selectorRespondingWith(withServerError());
        assertThatThrownBy(() -> selector.select(issue)).isInstanceOf(RestClientException.class);
    }

    private JevOwnerSelector selectorRespondingWith(ResponseCreator responseCreator) {
        var builder = RestClient.builder().baseUrl("https://example.invalid");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(SYSTEMONE_URL)).andRespond(responseCreator);
        var properties = new JevProperties("https://example.invalid", "jev-latest", "test-key");
        return new JevOwnerSelector(builder.build(), properties, roster);
    }
}
