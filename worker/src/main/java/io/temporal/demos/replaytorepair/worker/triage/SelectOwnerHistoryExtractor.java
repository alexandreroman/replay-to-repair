package io.temporal.demos.replaytorepair.worker.triage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.temporal.api.enums.v1.EventType;
import io.temporal.common.WorkflowExecutionHistory;
import io.temporal.common.converter.DataConverter;

/**
 * Extracts the real input of the {@code selectOwner} activity from a Temporal event history, as
 * produced by {@code temporal workflow show --output json}.
 *
 * <p>This is the core of the "replay to repair" workflow: instead of guessing which data triggered
 * a production bug, we decode the exact {@link Issue} that actually flowed into the failing
 * activity, using the SDK's default {@link DataConverter}, and feed it straight into a regression
 * test.
 */
final class SelectOwnerHistoryExtractor {
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectOwnerHistoryExtractor.class);

    // Default activity type name = the activity method name, capitalized.
    private static final String SELECT_OWNER_ACTIVITY_TYPE = "SelectOwner";

    private SelectOwnerHistoryExtractor() {
    }

    /** The decoded input of a single {@code selectOwner} activity invocation. */
    record SelectOwnerInput(Issue issue) {
    }

    static SelectOwnerInput fromFile(Path path) {
        try {
            return fromJson(Files.readString(path));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read event history file: " + path, e);
        }
    }

    static SelectOwnerInput fromJson(String historyJson) {
        var history = WorkflowExecutionHistory.fromJson(historyJson);
        var scheduledEvent = history.getEvents().stream()
                .filter(event -> event.getEventType() == EventType.EVENT_TYPE_ACTIVITY_TASK_SCHEDULED)
                .filter(event -> SELECT_OWNER_ACTIVITY_TYPE.equals(
                        event.getActivityTaskScheduledEventAttributes().getActivityType().getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No ActivityTaskScheduled event found for activity '"
                                + SELECT_OWNER_ACTIVITY_TYPE + "'"));
        LOGGER.atDebug()
                .addKeyValue("activityType", SELECT_OWNER_ACTIVITY_TYPE)
                .log("replay.history.activity_found");

        var payloads = Optional.of(scheduledEvent.getActivityTaskScheduledEventAttributes().getInput());
        var converter = DataConverter.getDefaultInstance();

        var issue = converter.fromPayloads(0, payloads, Issue.class, Issue.class);
        return new SelectOwnerInput(issue);
    }
}
