package io.temporal.demos.replaytorepair.worker.triage;

/**
 * An incoming issue to be triaged and assigned to an owner.
 *
 * <p>Shared types are intentionally duplicated between {@code worker} and
 * {@code backend} rather than extracted into a common module.
 */
public record Issue(String id, String title, String body) {
}
