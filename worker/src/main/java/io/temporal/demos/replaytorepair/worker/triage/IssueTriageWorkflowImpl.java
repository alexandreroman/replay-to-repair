package io.temporal.demos.replaytorepair.worker.triage;

import java.time.Duration;
import java.time.Instant;

import io.temporal.activity.ActivityOptions;
import io.temporal.activity.LocalActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.demos.replaytorepair.worker.triage.TriageStatus.Step;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;

@WorkflowImpl(taskQueues = IssueTriageWorkflow.TASK_QUEUE)
public class IssueTriageWorkflowImpl implements IssueTriageWorkflow {
    // Loading the owner profiles is fast, in-memory and idempotent, so it runs as a local activity
    // (no server round-trip). See BRIEF.md for why this step is treated differently.
    private final TriageActivities localActivities = Workflow.newLocalActivityStub(
            TriageActivities.class,
            LocalActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(5))
                    .build());

    // Owner selection calls the LLM (network I/O), so it stays a regular activity with retries.
    private final TriageActivities activities = Workflow.newActivityStub(
            TriageActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(30))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(3)
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

        var profiles = localActivities.loadProfiles();
        currentStatus = new TriageStatus(issue.id(), issue.title(), Step.PROFILES_LOADED, null, receivedAt);

        currentStatus = new TriageStatus(issue.id(), issue.title(), Step.AI_ANALYSIS, null, receivedAt);
        var owner = activities.selectOwner(issue, profiles);
        currentStatus = new TriageStatus(issue.id(), issue.title(), Step.OWNER_SELECTED, owner, receivedAt);

        activities.notifyAssignment(issue, owner);
        currentStatus = new TriageStatus(issue.id(), issue.title(), Step.DONE, owner, receivedAt);
        return currentStatus;
    }

    @Override
    public TriageStatus getStatus() {
        return currentStatus;
    }
}
