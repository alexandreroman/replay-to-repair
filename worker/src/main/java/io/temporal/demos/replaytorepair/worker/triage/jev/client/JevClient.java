package io.temporal.demos.replaytorepair.worker.triage.jev.client;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

/**
 * Calls Jev, TypeSafe's decision model, on its systemone endpoint.
 *
 * <p>The client does transport and typing only: it sends the questions it is handed and returns the
 * answers that come back, with no notion of what any of them mean. It retries nothing — Temporal's
 * Activity retry policy is the only retry in this system, so a failed call surfaces as an exception
 * for the Activity to propagate.
 *
 * <p>All three question shapes are supported on purpose — {@code choice}, {@code score} and
 * {@code noul} are Jev's whole question surface, and this component is where that wire format is
 * defined, independently of which shapes the callers of the day happen to ask.
 */
public class JevClient {
    private static final String SYSTEMONE_PATH = "/systemone";

    private final RestClient restClient;
    private final String model;

    /**
     * @param builder the builder the client configures and builds on, so the application's HTTP
     *                client settings — timeouts, request factory, redirects — apply to every call
     * @param baseUrl the TypeSafe API base URL the systemone endpoint hangs off
     * @param apiKey  the TypeSafe API key, sent as a Bearer token on every call
     * @param model   the id of the Jev model to answer with
     * @throws IllegalStateException if no API key is given
     */
    public JevClient(RestClient.Builder builder, String baseUrl, String apiKey, String model) {
        // Fail at startup, not three retries into the first call: an unset key still lets the
        // worker start and every call dies with an opaque 401 discovered mid-demo. There is no
        // JSR-303 provider on this module's classpath, so this is a plain check rather than
        // @Validated/@NotBlank, which would be silently inert.
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Calling Jev needs a TypeSafe API key: set TYPESAFE_API_KEY");
        }
        // Clone first: a builder configures itself in place and hands itself back, so setting the
        // base URL and the Authorization header on the caller's builder — the auto-configured one,
        // shared with every other client in the application — would imprint them on it.
        // The key goes into the built client's default header and into no field of this class,
        // so it cannot surface through a log line or a stack trace naming this object.
        this.restClient = builder.clone()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        this.model = model;
    }

    /**
     * Asks every question about the same state, in a single call.
     *
     * @param state     the facts to judge, as a free-form map
     * @param questions the questions to answer, keyed by the id their answer comes back under
     * @return one answer per question id, in the order the questions were given
     * @throws IllegalStateException if Jev leaves any of the questions unanswered, or answers one in
     *                               another shape than the one it was asked in
     */
    public Map<String, JevAnswer> ask(Map<String, String> state, Map<String, ? extends JevQuestion<?>> questions) {
        var response = restClient.post()
                .uri(SYSTEMONE_PATH)
                .body(new JevRequest(model, state, questions))
                .retrieve()
                .body(JevResponse.class);
        // An empty body and a body without answers are the same failure as a single missing answer,
        // and are reported the same way: by naming the question that went unanswered.
        var answers = response == null ? null : response.answers();
        var result = new LinkedHashMap<String, JevAnswer>();
        for (var question : questions.entrySet()) {
            var questionId = question.getKey();
            var answer = answers == null ? null : answers.get(questionId);
            if (answer == null) {
                throw new IllegalStateException("Jev returned no answer for question: " + questionId);
            }
            // The shape is checked here rather than in the typed overload below, so that a batch
            // caller reads this diagnostic instead of a ClassCastException at its own cast site.
            var expectedType = answerTypeOf(question.getValue());
            if (!expectedType.isInstance(answer)) {
                throw new IllegalStateException("Jev answered question %s with a %s instead of a %s"
                        .formatted(questionId, answer.getClass().getSimpleName(), expectedType.getSimpleName()));
            }
            result.put(questionId, answer);
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Asks a single question and returns its answer already typed.
     *
     * @param state      the facts to judge, as a free-form map
     * @param questionId the id the answer comes back under
     * @param question   the question to answer
     * @param <A>        the answer type the question is bound to
     * @return the answer to that question
     * @throws IllegalStateException if Jev leaves the question unanswered or answers it in another
     *                               shape than the one asked for
     */
    public <A extends JevAnswer> A ask(Map<String, String> state, String questionId, JevQuestion<A> question) {
        var answer = ask(state, Map.of(questionId, question)).get(questionId);
        // Safe: the call above accepts an answer only in the shape answerTypeOf pairs with the
        // question, and that switch pairs every question shape with the answer shape it is typed
        // for.
        @SuppressWarnings("unchecked")
        var typedAnswer = (A) answer;
        return typedAnswer;
    }

    private static Class<? extends JevAnswer> answerTypeOf(JevQuestion<?> question) {
        return switch (question) {
            case JevQuestion.Choice _ -> JevAnswer.Choice.class;
            case JevQuestion.Score _ -> JevAnswer.Score.class;
            case JevQuestion.Noul _ -> JevAnswer.Noul.class;
        };
    }

    /** Request body of a decision call: the state to judge plus the typed questions to answer. */
    record JevRequest(String model, Map<String, String> state, Map<String, ? extends JevQuestion<?>> questions) {
    }

    /** Response body of a decision call. The envelope's model and usage fields stay internal to Jev. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record JevResponse(Map<String, JevAnswer> answers) {
    }
}
