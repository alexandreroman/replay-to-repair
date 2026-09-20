package io.temporal.demos.replaytorepair.worker.triage.jev;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The owner roster the Jev engine turns into the criteria of its owner-selection question.
 *
 * @param owners the roster, in the order the options are offered to the model
 */
@ConfigurationProperties("triage.roster")
record TriageRosterProperties(List<Owner> owners) {
    /**
     * One roster entry.
     *
     * @param name        the owner's name, used verbatim as the option key and as the assigned owner
     * @param specialties the problem areas the owner covers
     * @param preferences the kinds of work the owner most enjoys, used to break ties
     */
    record Owner(String name, List<String> specialties, List<String> preferences) {
    }
}
