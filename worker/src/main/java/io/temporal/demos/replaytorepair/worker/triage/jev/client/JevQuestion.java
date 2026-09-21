package io.temporal.demos.replaytorepair.worker.triage.jev.client;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * A question put to Jev, in one of the three shapes the decision model answers.
 *
 * <p>The type parameter pairs a question with the answer it comes back as, so a caller asking a
 * single question reads a typed answer without a cast.
 *
 * @param <A> the answer Jev returns for this question
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = JevQuestion.Choice.class, name = "choice"),
        @JsonSubTypes.Type(value = JevQuestion.Score.class, name = "score"),
        @JsonSubTypes.Type(value = JevQuestion.Noul.class, name = "noul")})
public sealed interface JevQuestion<A extends JevAnswer> {
    /**
     * Asks the model to pick one option out of a named set.
     *
     * <p>The iteration order of {@code criteria} is the order the options are offered to the model,
     * so a caller to whom that order matters passes a map that keeps it.
     *
     * @param instructions the question, in natural language
     * @param criteria     the options offered, each mapped to the description the model judges it by
     */
    record Choice(String instructions, Map<String, String> criteria) implements JevQuestion<JevAnswer.Choice> {
        private static final int MAX_OPTIONS = 255;

        public Choice {
            requireInstructions(instructions);
            Objects.requireNonNull(criteria, "A choice question needs the options to pick from");
            criteria = copyOfOrdered(criteria);
            // Sized after the copy, so the limit holds for the map that is actually sent whatever
            // the caller does to theirs afterwards.
            if (criteria.isEmpty() || criteria.size() > MAX_OPTIONS) {
                throw new IllegalArgumentException("A choice question offers 1 to %d options, not %d"
                        .formatted(MAX_OPTIONS, criteria.size()));
            }
        }
    }

    /**
     * Asks the model to place the state on an ordered scale.
     *
     * @param instructions the question, in natural language
     * @param criteria     the levels of the scale, from the lowest to the highest
     */
    record Score(String instructions, List<String> criteria) implements JevQuestion<JevAnswer.Score> {
        private static final int MIN_LEVELS = 2;
        private static final int MAX_LEVELS = 10;

        public Score {
            requireInstructions(instructions);
            Objects.requireNonNull(criteria, "A score question needs the levels of its scale");
            criteria = List.copyOf(criteria);
            // Sized after the copy, so the limits hold for the list that is actually sent whatever
            // the caller does to theirs afterwards.
            if (criteria.size() < MIN_LEVELS || criteria.size() > MAX_LEVELS) {
                throw new IllegalArgumentException("A score question holds %d to %d levels, not %d"
                        .formatted(MIN_LEVELS, MAX_LEVELS, criteria.size()));
            }
        }
    }

    /**
     * Asks the model how likely a statement is to hold.
     *
     * @param instructions the question, in natural language
     * @param criteria     what tells a true answer from a false one, keyed {@code true} and
     *                     {@code false}, or {@code null} to leave that judgement to the model
     */
    record Noul(
            String instructions,
            // Null criteria are dropped from the body rather than sent as a JSON null, which the
            // API rejects. Scoped to this component: the instructions are always sent.
            @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, String> criteria)
            implements JevQuestion<JevAnswer.Noul> {
        public Noul {
            requireInstructions(instructions);
            criteria = criteria == null ? null : copyOfOrdered(criteria);
        }

        /** A noul the model judges on its instructions alone. */
        public Noul(String instructions) {
            this(instructions, null);
        }
    }

    /** Rejects a question with no question in it: every shape asks the model something. */
    private static void requireInstructions(String instructions) {
        if (instructions == null || instructions.isBlank()) {
            throw new IllegalArgumentException("A question needs instructions, in natural language");
        }
    }

    /**
     * Copies criteria the caller keeps a reference to, so the limits cannot be bypassed by mutating
     * the original and a mutation during serialization cannot tear the request body. A
     * {@link LinkedHashMap} rather than {@link Map#copyOf}: for a choice the iteration order is the
     * order the options reach the model, and {@code Map.copyOf} does not keep it.
     */
    private static Map<String, String> copyOfOrdered(Map<String, String> criteria) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(criteria));
    }
}
