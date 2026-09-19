package io.temporal.demos.replaytorepair.worker.triage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Owner selection backed by Jev, a decision model that answers a typed question rather than
 * generating text. The roster is sent as the question's criteria, because the model calls no tools
 * and so cannot read the issue-triage skill the way the Spring AI engine's model does.
 */
@Component
@Profile("jev")
class JevOwnerSelector implements OwnerSelector {
    private static final String NO_SUITABLE_OWNER = "none";
    private static final String QUESTION_ID = "owner";
    private static final String CHOICE = "choice";
    private static final String INSTRUCTIONS = "Which owner should handle this issue? Pick the owner whose "
            + "specialties most directly cover it; break ties by preference.";

    private final RestClient restClient;
    private final String model;
    // Built once at startup: the roster does not change while the worker runs.
    private final Map<String, String> criteria;
    private final Map<String, String> specialtiesByOwner;

    JevOwnerSelector(RestClient jevRestClient, JevProperties properties, TriageRosterProperties roster) {
        this.restClient = jevRestClient;
        this.model = properties.model();
        this.criteria = buildCriteria(roster);
        this.specialtiesByOwner = buildSpecialties(roster);
    }

    @Override
    public Optional<OwnerAssignment> select(Issue issue) {
        var state = Map.of(
                "issue_title", issue.issueTitle(),
                "issue_description", issue.issueDescription());
        var question = new JevRequest.ChoiceQuestion(CHOICE, INSTRUCTIONS, criteria);
        var request = new JevRequest(model, state, Map.of(QUESTION_ID, question));
        var response = restClient.post().uri("/systemone").body(request).retrieve().body(JevResponse.class);
        return resolveOwner(response);
    }

    // The "none" token is the deliberate no-suitable-owner verdict and yields an empty Optional,
    // which the Activity turns into Temporal's non-retryable failure. Everything else that does not
    // match the roster is a malformed answer and throws, so Temporal retries the activity.
    private Optional<OwnerAssignment> resolveOwner(JevResponse response) {
        var answer = response == null || response.answers() == null ? null : response.answers().get(QUESTION_ID);
        if (answer == null) {
            throw new IllegalStateException("Jev returned no answer for the owner question");
        }
        var candidate = answer.choice() == null ? "" : answer.choice().trim();
        if (candidate.isEmpty()) {
            throw new IllegalStateException("Jev returned a blank owner");
        }
        if (candidate.equalsIgnoreCase(NO_SUITABLE_OWNER)) {
            return Optional.empty();
        }
        var specialties = specialtiesByOwner.get(candidate);
        if (specialties == null) {
            throw new IllegalStateException("Jev returned an owner outside the roster: " + candidate);
        }
        return Optional.of(new OwnerAssignment(candidate, buildReason(specialties, answer.confidence())));
    }

    // Jev answers with a typed choice and a calibrated confidence, never prose, so the reason is
    // composed from the roster entry that matched and the confidence reported for the pick. Both
    // branches format with Locale.ROOT, even the one with no numeric specifier today, so a future
    // %f or %d added here does not silently reintroduce a host-locale-dependent reason string.
    private static String buildReason(String specialties, Double confidence) {
        if (confidence == null) {
            return String.format(Locale.ROOT, "Specialties cover %s", specialties);
        }
        return String.format(Locale.ROOT, "Specialties cover %s (confidence %.2f)", specialties, confidence);
    }

    // Collections.unmodifiableMap rather than Map.copyOf: the roster order is the order the options
    // are offered to the model, and Map.copyOf does not keep it.
    private static Map<String, String> buildCriteria(TriageRosterProperties roster) {
        var criteria = new LinkedHashMap<String, String>();
        for (var owner : roster.owners()) {
            criteria.put(owner.name(), "Specialties: %s. Prefers: %s.".formatted(
                    String.join(", ", owner.specialties()), String.join(", ", owner.preferences())));
        }
        criteria.put(NO_SUITABLE_OWNER, "No owner's specialties reasonably cover this issue.");
        return Collections.unmodifiableMap(criteria);
    }

    private static Map<String, String> buildSpecialties(TriageRosterProperties roster) {
        var specialties = new LinkedHashMap<String, String>();
        for (var owner : roster.owners()) {
            specialties.put(owner.name(), String.join(", ", owner.specialties()));
        }
        return Collections.unmodifiableMap(specialties);
    }

    /** Request body of a Jev decision call: the state to judge plus the typed questions to answer. */
    record JevRequest(String model, Map<String, String> state, Map<String, ChoiceQuestion> questions) {
        /** A question asking the model to pick one option, each option described by its criteria entry. */
        record ChoiceQuestion(String type, String instructions, Map<String, String> criteria) {
        }
    }

    /** Response body of a Jev decision call, keyed by the question id the request supplied. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record JevResponse(Map<String, ChoiceAnswer> answers) {
        /**
         * The typed answer: the winning option, the distribution over all options, and its
         * confidence.
         *
         * <p>This record documents Jev's response contract in full, including {@code probabilities}:
         * a calibrated probability per option is the whole point of a decision model over a text
         * generator, even though this selector currently only reads {@code choice} and
         * {@code confidence}.
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        record ChoiceAnswer(String choice, Map<String, Double> probabilities, Double confidence) {
        }
    }
}
