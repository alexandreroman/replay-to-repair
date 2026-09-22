package io.temporal.demos.replaytorepair.worker.triage.jev;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.question.Choice;
import org.springaicommunity.typesafe.response.ChoiceAnswer;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.temporal.demos.replaytorepair.worker.triage.Issue;
import io.temporal.demos.replaytorepair.worker.triage.OwnerAssignment;
import io.temporal.demos.replaytorepair.worker.triage.OwnerSelector;

/**
 * Owner selection backed by Jev, a decision model that answers a typed question rather than
 * generating text. The roster is offered as the options of a {@link Choice} question, because the
 * model calls no tools and so cannot read the issue-triage skill the way the LLM engine's model
 * does. The call goes out over the {@link TypeSafeClient} the spring-ai-community TypeSafe starter
 * auto-configures from {@code spring.ai.typesafe.*}.
 */
@Component
@Profile("jev")
class JevOwnerSelector implements OwnerSelector {
    private static final String NO_SUITABLE_OWNER = "none";
    private static final String QUESTION_ID = "owner";
    private static final String INSTRUCTIONS = "Which owner should handle this issue? Pick the owner whose "
            + "specialties most directly cover it; break ties by preference.";

    private final TypeSafeClient typeSafeClient;
    // Built once at startup: the roster does not change while the worker runs.
    private final Choice question;
    private final Map<String, String> specialtiesByOwner;
    private final Timer selectionTimer;

    JevOwnerSelector(TypeSafeClient typeSafeClient, TriageRosterProperties roster, MeterRegistry meterRegistry) {
        this.typeSafeClient = typeSafeClient;
        this.question = buildQuestion(roster);
        this.specialtiesByOwner = buildSpecialties(roster);
        // The meter is shared with the other engine: the "engine" tag keeps the two series apart
        // and "model" records the model id each one calls. The client's default model is the id it
        // puts on the wire, rather than a second reading of the property the starter itself binds.
        this.selectionTimer = Timer.builder("triage.owner.selection")
                .description("Time taken by the selection engine to pick an owner")
                .tag("engine", "jev")
                .tag("model", typeSafeClient.defaultModel())
                .register(meterRegistry);
    }

    @Override
    public Optional<OwnerAssignment> select(Issue issue) {
        // A selection that throws is measured as well: Timer#record stops its sample in a finally.
        return selectionTimer.record(() -> selectOwner(issue));
    }

    private Optional<OwnerAssignment> selectOwner(Issue issue) {
        var state = Map.of(
                "issue_title", issue.issueTitle(),
                "issue_description", issue.issueDescription());
        var response = typeSafeClient.systemOne(state, Map.of(QUESTION_ID, question));
        return resolveOwner(response.choice(QUESTION_ID));
    }

    // The "none" token is the deliberate no-suitable-owner verdict and yields an empty Optional,
    // which the Activity turns into Temporal's non-retryable failure. Everything else that does not
    // match the roster is a malformed answer and throws, so Temporal retries the activity.
    private Optional<OwnerAssignment> resolveOwner(ChoiceAnswer answer) {
        var candidate = answer.value() == null ? "" : answer.value().trim();
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
    // composed from the roster entry that matched and the confidence reported for the pick.
    // Every choice answer carries a confidence, so the primitive double holds a reported value:
    // a "confidence 0.00" in the reason is a genuinely unconfident pick, not an absent field.
    // Locale.ROOT keeps the decimal separator of the confidence out of the host locale's hands.
    private static String buildReason(String specialties, double confidence) {
        return String.format(Locale.ROOT, "Specialties cover %s (confidence %.2f)", specialties, confidence);
    }

    // The builder collects the options into an order-preserving map, so the roster order is the
    // order they are offered to the model, with the no-suitable-owner token last.
    private static Choice buildQuestion(TriageRosterProperties roster) {
        var builder = Choice.builder().instructions(INSTRUCTIONS);
        for (var owner : roster.owners()) {
            builder.option(owner.name(), "Specialties: %s. Prefers: %s.".formatted(
                    String.join(", ", owner.specialties()), String.join(", ", owner.preferences())));
        }
        builder.option(NO_SUITABLE_OWNER, "No owner's specialties reasonably cover this issue.");
        return builder.build();
    }

    private static Map<String, String> buildSpecialties(TriageRosterProperties roster) {
        var specialties = new LinkedHashMap<String, String>();
        for (var owner : roster.owners()) {
            specialties.put(owner.name(), String.join(", ", owner.specialties()));
        }
        return Collections.unmodifiableMap(specialties);
    }
}
