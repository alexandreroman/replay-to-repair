package io.temporal.demos.replaytorepair.worker.triage;

import java.time.Duration;
import java.time.Instant;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.demos.replaytorepair.worker.triage.TriageStatus.Step;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;

import org.slf4j.Logger;

@WorkflowImpl(taskQueues = IssueTriageWorkflow.TASK_QUEUE)
public class IssueTriageWorkflowImpl implements IssueTriageWorkflow {
    // Replay-aware logger: Workflow.getLogger suppresses duplicate output during history replay.
    private static final Logger LOGGER = Workflow.getLogger(IssueTriageWorkflowImpl.class);

    // Owner selection calls the LLM (network I/O), so it stays a regular activity with retries.
    // Transient failures (LLM/network) and malformed replies retry with capped exponential backoff up
    // to 3 attempts, while the NoSuitableOwner failure is non-retryable and terminates the workflow in
    // error immediately.
    private final TriageActivities activities = Workflow.newActivityStub(
            TriageActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(30))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setInitialInterval(Duration.ofSeconds(1))
                            .setBackoffCoefficient(2.0)
                            .setMaximumInterval(Duration.ofSeconds(10))
                            .setMaximumAttempts(3)
                            .setDoNotRetry("NoSuitableOwner")
                            .build())
                    .build());

    // Feeds both the live query and the final return value, so both expose the same shape.
    private TriageStatus currentStatus;

    @Override
    public TriageStatus triage(Issue issue) {
        // Never use Instant.now() inside workflow code: derive the timestamp from the deterministic
        // workflow clock and keep it fixed for the whole execution.
        var receivedAt = Instant.ofEpochMilli(Workflow.currentTimeMillis());
        currentStatus = statusAt(issue, receivedAt, Step.ISSUE_RECEIVED, null, null);
        LOGGER.atInfo()
                .addKeyValue("issueId", issue.id())
                .addKeyValue("issueTitle", issue.title())
                .log("triage.issue.received");

        currentStatus = statusAt(issue, receivedAt, Step.AI_ANALYSIS, null, null);
        var assignment = activities.selectOwner(issue);
        var owner = assignment.owner();
        var reason = assignment.reason();
        currentStatus = statusAt(issue, receivedAt, Step.OWNER_SELECTED, owner, reason);
        LOGGER.atInfo()
                .addKeyValue("issueId", issue.id())
                .addKeyValue("owner", owner)
                .addKeyValue("reason", reason)
                .log("triage.owner.assigned");

        // Update the existing ticket in the ticketing system with the assigned owner. This runs within
        // the OWNER_SELECTED step, keeping it visible while the ticket is updated.
        activities.updateTicket(issue, owner);

        currentStatus = statusAt(issue, receivedAt, Step.NOTIFYING, owner, reason);
        activities.notifyAssignment(issue, owner);
        currentStatus = statusAt(issue, receivedAt, Step.DONE, owner, reason);
        LOGGER.atInfo()
                .addKeyValue("issueId", issue.id())
                .addKeyValue("owner", owner)
                .log("triage.completed");
        return currentStatus;
    }

    @Override
    public TriageStatus getStatus() {
        return currentStatus;
    }

    private static TriageStatus statusAt(Issue issue, Instant receivedAt, Step step, String owner, String reason) {
        return new TriageStatus(issue.id(), issue.title(), step, owner, reason, receivedAt);
    }
}
