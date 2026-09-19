package io.temporal.demos.replaytorepair.worker.triage;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

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
    private static final ActivityOptions DEFAULT_ACTIVITY_OPTIONS = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .setRetryOptions(RetryOptions.newBuilder()
                    .setInitialInterval(Duration.ofSeconds(1))
                    .setBackoffCoefficient(2.0)
                    .setMaximumInterval(Duration.ofSeconds(10))
                    .setMaximumAttempts(3)
                    .setDoNotRetry("NoSuitableOwner")
                    .build())
            .build();

    // The per-activity overrides are keyed by activity *type* name — the method name with an
    // upper-case first letter. A key matching no activity type is ignored silently, leaving that
    // activity without a summary.
    private final TriageActivities activities = Workflow.newActivityStub(
            TriageActivities.class,
            DEFAULT_ACTIVITY_OPTIONS,
            Map.of(
                    "SelectOwner", withSummary("Ask the selection engine which roster owner fits the issue"),
                    "UpdateTicket", withSummary("Record the assigned owner on the issue ticket"),
                    "NotifyAssignment", withSummary("Notify the assigned owner of the issue")));

    // Feeds both the live query and the final return value, so both expose the same shape.
    private TriageStatus currentStatus;

    @Override
    public TriageStatus triage(Issue issue) {
        // Never use Instant.now() inside workflow code: derive the timestamp from the deterministic
        // workflow clock and keep it fixed for the whole execution.
        var receivedAt = Instant.ofEpochMilli(Workflow.currentTimeMillis());
        moveTo(issue, receivedAt, Step.ISSUE_RECEIVED, null, null);
        LOGGER.atInfo()
                .addKeyValue("issueId", issue.issueId())
                .addKeyValue("issueTitle", issue.issueTitle())
                .log("triage.issue.received");

        moveTo(issue, receivedAt, Step.AI_ANALYSIS, null, null);
        var assignment = activities.selectOwner(issue);
        var owner = assignment.owner();
        var reason = assignment.reason();
        moveTo(issue, receivedAt, Step.OWNER_SELECTED, owner, reason);
        LOGGER.atInfo()
                .addKeyValue("issueId", issue.issueId())
                .addKeyValue("owner", owner)
                .addKeyValue("reason", reason)
                .log("triage.owner.assigned");

        // Update the existing ticket in the ticketing system with the assigned owner. This runs within
        // the OWNER_SELECTED step, keeping it visible while the ticket is updated.
        activities.updateTicket(issue, owner);

        moveTo(issue, receivedAt, Step.NOTIFYING, owner, reason);
        activities.notifyAssignment(issue, owner);
        moveTo(issue, receivedAt, Step.DONE, owner, reason);
        LOGGER.atInfo()
                .addKeyValue("issueId", issue.issueId())
                .addKeyValue("owner", owner)
                .log("triage.completed");
        return currentStatus;
    }

    @Override
    public TriageStatus getStatus() {
        return currentStatus;
    }

    /**
     * Advances the triage to {@code step}: refreshes the queryable status and publishes the same
     * transition as workflow current details, so the Temporal Web UI shows the live step without a
     * Query.
     */
    private void moveTo(Issue issue, Instant receivedAt, Step step, String owner, String reason) {
        currentStatus = statusAt(issue, receivedAt, step, owner, reason);
        Workflow.setCurrentDetails(currentDetails(step, owner));
    }

    private static TriageStatus statusAt(Issue issue, Instant receivedAt, Step step, String owner, String reason) {
        return new TriageStatus(issue.issueId(), issue.issueTitle(), step, owner, reason, receivedAt);
    }

    /** Short Markdown line describing the step being executed, including the owner once it is known. */
    private static String currentDetails(Step step, String owner) {
        var description = switch (step) {
            case ISSUE_RECEIVED -> "Issue received, triage starting";
            case AI_ANALYSIS -> "Asking the selection engine which owner fits the issue";
            case OWNER_SELECTED -> "Owner selected, updating the ticket";
            case NOTIFYING -> "Notifying the assigned owner";
            case DONE -> "Triage complete";
            case FAILED -> "Triage failed";
        };
        if (owner == null) {
            return "**" + step + "** — " + description;
        }
        return "**" + step + "** — " + description + " (owner: `" + owner + "`)";
    }

    /** Derives per-activity options from the shared defaults, keeping the timeout and retry policy. */
    private static ActivityOptions withSummary(String summary) {
        return DEFAULT_ACTIVITY_OPTIONS.toBuilder().setSummary(summary).build();
    }
}
