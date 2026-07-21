package io.temporal.demos.replaytorepair.worker.triage;

import java.util.List;

/**
 * A candidate owner the triage agent may assign an issue to, described by the
 * specialties/preferences the LLM reasons about.
 */
public record OwnerProfile(String name, List<String> specialties) {
}
