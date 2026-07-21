package io.temporal.demos.replaytorepair.worker.triage;

/**
 * A candidate owner the triage agent can assign an issue to, described by a single specialty
 * (for example {@code backend} or {@code security}).
 */
public record OwnerProfile(String name, String specialty) {
}
