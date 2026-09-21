package io.temporal.demos.replaytorepair.worker.triage.jev.client;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * An answer from Jev, in the shape of the question that was asked.
 *
 * <p>A choice and a score come with the calibrated distribution the model computed, which is the
 * point of a decision model over a text generator: the caller can act on how sure the model is, not
 * only on what it picked.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = JevAnswer.Choice.class, name = "choice"),
        @JsonSubTypes.Type(value = JevAnswer.Score.class, name = "score"),
        @JsonSubTypes.Type(value = JevAnswer.Noul.class, name = "noul")})
public sealed interface JevAnswer {
    /**
     * The option the model picked.
     *
     * @param choice        the winning option
     * @param probabilities the distribution over every option offered
     * @param confidence    how sure the model is of {@code choice}, from 0 to 1
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(String choice, Map<String, Double> probabilities, Double confidence) implements JevAnswer {
    }

    /**
     * A continuous position on the scale the question offered.
     *
     * <p>The scale is zero-based: {@code legend} maps the stringified index of each level to its
     * label, so a score of {@code 1.94} sits just below the level labelled {@code "2"}.
     *
     * @param score         the position on the scale, from 0 to the index of the highest level
     * @param legend        the label of each level, keyed by its stringified index
     * @param probabilities the distribution over the level indices
     * @param confidence    how sure the model is of {@code score}, from 0 to 1
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Score(Double score, Map<String, String> legend, Map<String, Double> probabilities, Double confidence)
            implements JevAnswer {
    }

    /**
     * The probability that the statement holds.
     *
     * <p>A noul carries no confidence of its own: the probability is the answer.
     *
     * @param noul the probability the statement is true, from 0 to 1
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Noul(Double noul) implements JevAnswer {
    }
}
