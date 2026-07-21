package io.temporal.demos.replaytorepair.worker.triage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import io.temporal.api.enums.v1.EventType;
import io.temporal.common.WorkflowExecutionHistory;
import io.temporal.common.converter.DataConverter;

/**
 * Extracts the real input of the {@code selectOwner} activity from a Temporal event history, as
 * produced by {@code temporal workflow show --output json}.
 *
 * <p>This is the core of the "replay to repair" workflow: instead of guessing which data triggered
 * a production bug, we decode the exact {@link Issue} and owner profiles that actually flowed into
 * the failing activity, using the SDK's default {@link DataConverter}, and feed them straight into
 * a regression test.
 */
public final class SelectOwnerHistoryExtractor {
    // Default activity type name = the activity method name, capitalized.
    private static final String SELECT_OWNER_ACTIVITY_TYPE = "SelectOwner";

    private SelectOwnerHistoryExtractor() {
    }

    /** The decoded input of a single {@code selectOwner} activity invocation. */
    public record SelectOwnerInput(Issue issue, List<OwnerProfile> profiles) {
    }

    public static SelectOwnerInput fromFile(Path path) {
        try {
            return fromJson(Files.readString(path));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read event history file: " + path, e);
        }
    }

    public static SelectOwnerInput fromJson(String historyJson) {
        var history = WorkflowExecutionHistory.fromJson(historyJson);
        var scheduledEvent = history.getEvents().stream()
                .filter(event -> event.getEventType() == EventType.EVENT_TYPE_ACTIVITY_TASK_SCHEDULED)
                .filter(event -> SELECT_OWNER_ACTIVITY_TYPE.equals(
                        event.getActivityTaskScheduledEventAttributes().getActivityType().getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No ActivityTaskScheduled event found for activity '"
                                + SELECT_OWNER_ACTIVITY_TYPE + "'"));

        var payloads = Optional.of(scheduledEvent.getActivityTaskScheduledEventAttributes().getInput());
        var converter = DataConverter.getDefaultInstance();

        var issue = converter.fromPayloads(0, payloads, Issue.class, Issue.class);
        // Decode the second argument as an array to stay JDK-only (no Guava/Jackson type token):
        // a JSON array decodes just as well into OwnerProfile[] as into List<OwnerProfile>.
        var profiles = converter.fromPayloads(1, payloads, OwnerProfile[].class, OwnerProfile[].class);

        return new SelectOwnerInput(issue, List.of(profiles));
    }
}
