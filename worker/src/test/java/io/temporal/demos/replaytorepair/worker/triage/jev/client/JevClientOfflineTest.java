package io.temporal.demos.replaytorepair.worker.triage.jev.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Offline unit test for {@link JevClient}: no network call and no real API key, unlike the live {@link
 * JevClientTest}. A {@link MockRestServiceServer} stands in for TypeSafe's systemone endpoint, so
 * each test can pin the exact JSON sent for a question type and bind a canned answer back.
 *
 * <p>Request and response bodies are the ones captured from the live API, which makes this test the
 * place where the wire format is recorded: a map of criteria for a choice, an ordered array for a
 * score, no criteria field at all for a noul, and a {@code type} discriminator on every question and
 * every answer.
 */
class JevClientOfflineTest {
    private static final String BASE_URL = "https://example.invalid";
    private static final String SYSTEMONE_URL = BASE_URL + "/systemone";
    private static final String MODEL = "jev-latest";
    private static final String API_KEY = "dummy-key";

    private static final Map<String, String> STATE = Map.of(
            "issue_title", "Checkout endpoint returns HTTP 500 under load",
            "issue_description", "The POST /api/checkout endpoint throws NullPointerException");

    private static final JevQuestion.Choice OWNER_QUESTION = new JevQuestion.Choice(
            "Which owner should handle this issue?",
            Map.of("alice", "Specialties: backend, APIs.", "carol", "Specialties: security."));

    private static final JevQuestion.Score URGENCY_QUESTION = new JevQuestion.Score(
            "How urgent is this issue?",
            List.of("Can wait a sprint", "Should be handled this week", "Needs attention today"));

    private static final JevQuestion.Noul REGRESSION_QUESTION = new JevQuestion.Noul("Is this issue a regression?");

    private static final String STATE_JSON = """
            "state": {
              "issue_title": "Checkout endpoint returns HTTP 500 under load",
              "issue_description": "The POST /api/checkout endpoint throws NullPointerException"
            }
            """;

    private static final String OWNER_QUESTION_JSON = """
            "owner": {
              "type": "choice",
              "instructions": "Which owner should handle this issue?",
              "criteria": {"alice": "Specialties: backend, APIs.", "carol": "Specialties: security."}
            }
            """;

    private static final String URGENCY_QUESTION_JSON = """
            "urgency": {
              "type": "score",
              "instructions": "How urgent is this issue?",
              "criteria": ["Can wait a sprint", "Should be handled this week", "Needs attention today"]
            }
            """;

    private static final String REGRESSION_QUESTION_JSON = """
            "is_regression": {"type": "noul", "instructions": "Is this issue a regression?"}
            """;

    private static final String OWNER_ANSWER_JSON = """
            "owner": {
              "type": "choice",
              "choice": "alice",
              "confidence": 1.0,
              "probabilities": {"carol": 0.0, "alice": 1.0}
            }
            """;

    private static final String URGENCY_ANSWER_JSON = """
            "urgency": {
              "type": "score",
              "score": 1.94,
              "confidence": 0.9,
              "legend": {"0": "Can wait a sprint", "1": "Should be handled this week", "2": "Needs attention today"},
              "probabilities": {"0": 0.0, "1": 0.06, "2": 0.94}
            }
            """;

    // A noul answer carries no confidence: the probability that the answer is yes is the answer.
    private static final String REGRESSION_ANSWER_JSON = """
            "is_regression": {"type": "noul", "noul": 0.44}
            """;

    @Test
    void choiceQuestionSendsItsCriteriaAsAMap() {
        var client = clientExchanging(request(OWNER_QUESTION_JSON), response(OWNER_ANSWER_JSON));
        var answer = client.ask(STATE, "owner", OWNER_QUESTION);
        assertThat(answer.choice()).isEqualTo("alice");
        assertThat(answer.confidence()).isEqualTo(1.0);
        assertThat(answer.probabilities()).containsExactlyInAnyOrderEntriesOf(Map.of("alice", 1.0, "carol", 0.0));
    }

    @Test
    void scoreQuestionSendsItsCriteriaAsAnOrderedArray() {
        var client = clientExchanging(request(URGENCY_QUESTION_JSON), response(URGENCY_ANSWER_JSON));
        var answer = client.ask(STATE, "urgency", URGENCY_QUESTION);
        assertThat(answer.score()).isEqualTo(1.94);
        assertThat(answer.confidence()).isEqualTo(0.9);
        assertThat(answer.legend()).containsEntry("2", "Needs attention today");
        assertThat(answer.probabilities()).containsEntry("2", 0.94);
    }

    @Test
    void noulQuestionWithoutCriteriaOmitsTheField() {
        var client = clientExchanging(request(REGRESSION_QUESTION_JSON), response(REGRESSION_ANSWER_JSON));
        var answer = client.ask(STATE, "is_regression", REGRESSION_QUESTION);
        assertThat(answer.noul()).isEqualTo(0.44);
    }

    @Test
    void noulQuestionSendsTheCriteriaItIsGiven() {
        var question = new JevQuestion.Noul(
                "Is this issue a regression?",
                Map.of("true", "The behaviour worked before.", "false", "The behaviour never worked."));
        var expectedRequest = request("""
                "is_regression": {
                  "type": "noul",
                  "instructions": "Is this issue a regression?",
                  "criteria": {"true": "The behaviour worked before.", "false": "The behaviour never worked."}
                }
                """);
        var client = clientExchanging(expectedRequest, response(REGRESSION_ANSWER_JSON));
        assertThat(client.ask(STATE, "is_regression", question).noul()).isEqualTo(0.44);
    }

    @Test
    void questionsOfMixedTypesTravelInASingleCall() {
        var expectedRequest = request(OWNER_QUESTION_JSON, URGENCY_QUESTION_JSON, REGRESSION_QUESTION_JSON);
        var expectedResponse = response(OWNER_ANSWER_JSON, URGENCY_ANSWER_JSON, REGRESSION_ANSWER_JSON);
        var client = clientExchanging(expectedRequest, expectedResponse);
        var answers = client.ask(STATE, Map.of(
                "owner", OWNER_QUESTION,
                "urgency", URGENCY_QUESTION,
                "is_regression", REGRESSION_QUESTION));
        assertThat(answers).hasSize(3);
        assertThat(answers.get("owner"))
                .isEqualTo(new JevAnswer.Choice("alice", Map.of("alice", 1.0, "carol", 0.0), 1.0));
        assertThat(answers.get("urgency")).isEqualTo(new JevAnswer.Score(
                1.94,
                Map.of("0", "Can wait a sprint", "1", "Should be handled this week", "2", "Needs attention today"),
                Map.of("0", 0.0, "1", 0.06, "2", 0.94),
                0.9));
        assertThat(answers.get("is_regression")).isEqualTo(new JevAnswer.Noul(0.44));
    }

    @Test
    void missingAnswerForARequestedQuestionThrows() {
        var client = clientResponding(response(OWNER_ANSWER_JSON));
        assertThatThrownBy(() -> client.ask(STATE, "urgency", URGENCY_QUESTION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("urgency");
    }

    @Test
    void responseWithoutAnswersThrows() {
        var client = clientResponding("""
                {"model": "jev-1.13.0", "usage": {"input_tokens": 427, "output_tokens": 66}}
                """);
        assertThatThrownBy(() -> client.ask(STATE, "owner", OWNER_QUESTION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("owner");
    }

    @Test
    void emptyResponseBodyThrows() {
        var builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build().expect(requestTo(SYSTEMONE_URL)).andRespond(withSuccess());
        var client = new JevClient(builder, BASE_URL, API_KEY, MODEL);
        assertThatThrownBy(() -> client.ask(STATE, "owner", OWNER_QUESTION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("owner");
    }

    @Test
    void answerOfAnotherTypeThanTheQuestionThrows() {
        var client = clientResponding("""
                {"answers": {"owner": {"type": "noul", "noul": 0.44}}}
                """);
        assertThatThrownBy(() -> client.ask(STATE, "owner", OWNER_QUESTION))
                .isInstanceOf(IllegalStateException.class)
                // Both shapes, so the mismatch is not read as an unanswered question.
                .hasMessageContainingAll("owner", "Noul", "Choice");
    }

    @Test
    void answerOfAnotherTypeThanTheQuestionThrowsTheSameWayInABatch() {
        var client = clientResponding("""
                {"answers": {"owner": {"type": "noul", "noul": 0.44}}}
                """);
        assertThatThrownBy(() -> client.ask(STATE, Map.of("owner", OWNER_QUESTION)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContainingAll("owner", "Noul", "Choice");
    }

    @Test
    void scoreQuestionRejectsAScaleOutsideTheApiLimits() {
        assertThatThrownBy(() -> new JevQuestion.Score("How urgent?", List.of("Only level")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JevQuestion.Score("How urgent?", levels(11)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scoreQuestionAcceptsBothEndsOfTheApiLimits() {
        assertThat(new JevQuestion.Score("How urgent?", levels(2)).criteria()).hasSize(2);
        assertThat(new JevQuestion.Score("How urgent?", levels(10)).criteria()).hasSize(10);
    }

    @Test
    void choiceQuestionRejectsMoreOptionsThanTheApiAccepts() {
        assertThatThrownBy(() -> new JevQuestion.Choice("Which owner?", options(256)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void choiceQuestionAcceptsAsManyOptionsAsTheApiTakes() {
        assertThat(new JevQuestion.Choice("Which owner?", options(255)).criteria()).hasSize(255);
    }

    @Test
    void choiceQuestionRejectsAnEmptyCriteriaMap() {
        assertThatThrownBy(() -> new JevQuestion.Choice("Which owner?", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyQuestionShapeRejectsMissingInstructions() {
        assertThatThrownBy(() -> new JevQuestion.Choice(" ", Map.of("alice", "Specialties: backend.")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JevQuestion.Score(null, levels(3)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JevQuestion.Noul(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aQuestionCopiesTheCriteriaItIsGiven() {
        var options = new LinkedHashMap<>(Map.of("alice", "Specialties: backend."));
        var question = new JevQuestion.Choice("Which owner?", options);
        options.put("mallory", "Specialties: none.");
        assertThat(question.criteria()).containsOnlyKeys("alice");
    }

    @Test
    void aMissingApiKeyIsRejectedAtConstruction() {
        assertThatThrownBy(() -> new JevClient(RestClient.builder(), BASE_URL, "  ", MODEL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TYPESAFE_API_KEY");
        assertThatThrownBy(() -> new JevClient(RestClient.builder(), BASE_URL, null, MODEL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TYPESAFE_API_KEY");
    }

    private static List<String> levels(int count) {
        return IntStream.range(0, count).mapToObj(i -> "Level " + i).toList();
    }

    private static Map<String, String> options(int count) {
        var options = new LinkedHashMap<String, String>();
        IntStream.range(0, count).forEach(i -> options.put("owner-" + i, "Specialties: none."));
        return options;
    }

    /** Wraps question bodies into the full request the client is expected to send. */
    private static String request(String... questions) {
        return "{\"model\": \"%s\", %s, \"questions\": {%s}}".formatted(MODEL, STATE_JSON, String.join(",", questions));
    }

    /** Wraps answer bodies into a full response envelope, model and usage fields included. */
    private static String response(String... answers) {
        return """
                {"model": "jev-1.13.0", "answers": {%s}, "usage": {"input_tokens": 427, "output_tokens": 66}}
                """.formatted(String.join(",", answers));
    }

    /** A client whose mock server both checks the request body and answers it. */
    private static JevClient clientExchanging(String expectedRequestBody, String responseBody) {
        var builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build()
                .expect(requestTo(SYSTEMONE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + API_KEY))
                // Strict comparison: a criteria field the client should omit fails the test.
                .andExpect(content().json(expectedRequestBody, JsonCompareMode.STRICT))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
        return new JevClient(builder, BASE_URL, API_KEY, MODEL);
    }

    /** A client whose mock server answers any request, for the tests that only exercise the response. */
    private static JevClient clientResponding(String responseBody) {
        var builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build()
                .expect(requestTo(SYSTEMONE_URL))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + API_KEY))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
        return new JevClient(builder, BASE_URL, API_KEY, MODEL);
    }
}
