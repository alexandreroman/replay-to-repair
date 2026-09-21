package io.temporal.demos.replaytorepair.worker.triage.jev;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.temporal.demos.replaytorepair.worker.triage.Issue;
import io.temporal.demos.replaytorepair.worker.triage.OwnerAssignment;
import io.temporal.demos.replaytorepair.worker.triage.OwnerSelector;
import io.temporal.demos.replaytorepair.worker.triage.jev.client.JevAnswer;
import io.temporal.demos.replaytorepair.worker.triage.jev.client.JevClient;
import io.temporal.demos.replaytorepair.worker.triage.jev.client.JevQuestion;

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
    private static final String INSTRUCTIONS = "Which owner should handle this issue? Pick the owner whose "
            + "specialties most directly cover it; break ties by preference.";

    private final JevClient jevClient;
    // Built once at startup: the roster does not change while the worker runs.
    private final JevQuestion.Choice question;
    private final Map<String, String> specialtiesByOwner;

    JevOwnerSelector(JevClient jevClient, TriageRosterProperties roster) {
        this.jevClient = jevClient;
        this.question = new JevQuestion.Choice(INSTRUCTIONS, buildCriteria(roster));
        this.specialtiesByOwner = buildSpecialties(roster);
    }

    @Override
    public Optional<OwnerAssignment> select(Issue issue) {
        var state = Map.of(
                "issue_title", issue.issueTitle(),
                "issue_description", issue.issueDescription());
        return resolveOwner(jevClient.ask(state, QUESTION_ID, question));
    }

    // The "none" token is the deliberate no-suitable-owner verdict and yields an empty Optional,
    // which the Activity turns into Temporal's non-retryable failure. Everything else that does not
    // match the roster is a malformed answer and throws, so Temporal retries the activity.
    private Optional<OwnerAssignment> resolveOwner(JevAnswer.Choice answer) {
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

    // The map is handed straight to the question, which copies it into an order-preserving map of
    // its own: the roster order is the order the options are offered to the model.
    private static Map<String, String> buildCriteria(TriageRosterProperties roster) {
        var criteria = new LinkedHashMap<String, String>();
        for (var owner : roster.owners()) {
            criteria.put(owner.name(), "Specialties: %s. Prefers: %s.".formatted(
                    String.join(", ", owner.specialties()), String.join(", ", owner.preferences())));
        }
        criteria.put(NO_SUITABLE_OWNER, "No owner's specialties reasonably cover this issue.");
        return criteria;
    }

    private static Map<String, String> buildSpecialties(TriageRosterProperties roster) {
        var specialties = new LinkedHashMap<String, String>();
        for (var owner : roster.owners()) {
            specialties.put(owner.name(), String.join(", ", owner.specialties()));
        }
        return Collections.unmodifiableMap(specialties);
    }
}
