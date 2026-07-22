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
        currentStatus = new TriageStatus(issue.id(), issue.title(), Step.ISSUE_RECEIVED, null, receivedAt);
        LOGGER.atInfo()
                .addKeyValue("issueId", issue.id())
                .addKeyValue("issueTitle", issue.title())
                .log("triage.issue.received");

        currentStatus = new TriageStatus(issue.id(), issue.title(), Step.AI_ANALYSIS, null, receivedAt);
        var owner = activities.selectOwner(issue);
        currentStatus = new TriageStatus(issue.id(), issue.title(), Step.OWNER_SELECTED, owner, receivedAt);
        LOGGER.atInfo()
                .addKeyValue("issueId", issue.id())
                .addKeyValue("owner", owner)
                .log("triage.owner.selected");

        currentStatus = new TriageStatus(issue.id(), issue.title(), Step.NOTIFYING, owner, receivedAt);
        activities.notifyAssignment(issue, owner);
        currentStatus = new TriageStatus(issue.id(), issue.title(), Step.DONE, owner, receivedAt);
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
}
