package io.temporal.demos.replaytorepair.worker.triage;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.temporal.failure.ApplicationFailure;
import io.temporal.spring.boot.ActivityImpl;

@Component
@ActivityImpl(taskQueues = IssueTriageWorkflow.TASK_QUEUE)
public class TriageActivitiesImpl implements TriageActivities {
    private static final Logger LOGGER = LoggerFactory.getLogger(TriageActivitiesImpl.class);
    private static final Duration NOTIFY_DELAY = Duration.ofSeconds(2);
    private static final Duration TICKET_UPDATE_DELAY = Duration.ofSeconds(2);

    private final OwnerSelector ownerSelector;

    TriageActivitiesImpl(OwnerSelector ownerSelector) {
        this.ownerSelector = ownerSelector;
    }

    @Override
    public OwnerAssignment selectOwner(Issue issue) {
        // The wrapper owns both logs, so it always writes "selecting" then "selected" around the
        // owner returned by the delegated selection.
        LOGGER.atInfo()
                .addKeyValue("issueId", issue.id())
                .log("triage.owner.selecting");
        // An empty selection is the deliberate no-suitable-owner verdict: the component stays
        // Temporal-agnostic and it is raised here as a non-retryable NoSuitableOwner failure that
        // terminates the workflow in error.
        var assignment = ownerSelector.select(issue).orElseThrow(() -> ApplicationFailure.newNonRetryableFailure(
                "No suitable owner in the roster for this issue", "NoSuitableOwner"));
        LOGGER.atInfo()
                .addKeyValue("issueId", issue.id())
                .addKeyValue("owner", assignment.owner())
                .addKeyValue("reason", assignment.reason())
                .log("triage.owner.selected");
        return assignment;
    }

    @Override
    public void updateTicket(Issue issue, String owner) {
        // The ticket already exists in the ticketing system; here we only record the assigned owner on
        // it.
        LOGGER.atInfo()
                .addKeyValue("issueId", issue.id())
                .addKeyValue("owner", owner)
                .log("triage.ticket.updating");
        // Simulate the ticketing-system call so the "Owner assigned" step stays visible in the
        // dashboard while the frontend polls.
        simulateWork(TICKET_UPDATE_DELAY, "Interrupted while updating the ticket");
        // No external call in the demo: logging is enough to show the ticket was updated.
        LOGGER.atInfo()
                .addKeyValue("issueId", issue.id())
                .addKeyValue("issueTitle", issue.title())
                .addKeyValue("owner", owner)
                .log("triage.ticket.updated");
    }

    @Override
    public void notifyAssignment(Issue issue, String owner) {
        // Simulate the notification so the NOTIFYING step stays visible in the dashboard while the
        // frontend polls.
        simulateWork(NOTIFY_DELAY, "Interrupted while notifying issue assignment");
        // No external notification in the demo: logging is enough to show the step ran.
        LOGGER.atInfo()
                .addKeyValue("issueId", issue.id())
                .addKeyValue("issueTitle", issue.title())
                .addKeyValue("owner", owner)
                .log("triage.assignment.notified");
    }

    // Block to simulate a slow external call. Blocking is fine here: this is Activity code, not the
    // Workflow, so a real Thread.sleep is correct (never use it in workflow code).
    private static void simulateWork(Duration delay, String interruptedMessage) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            // Restore the interrupt flag and fail so Temporal can retry the activity.
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interruptedMessage, e);
        }
    }
}
