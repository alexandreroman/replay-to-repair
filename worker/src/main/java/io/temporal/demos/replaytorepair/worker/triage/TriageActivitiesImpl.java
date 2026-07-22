package io.temporal.demos.replaytorepair.worker.triage;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import io.temporal.failure.ApplicationFailure;
import io.temporal.spring.boot.ActivityImpl;

@Component
@ActivityImpl(taskQueues = IssueTriageWorkflow.TASK_QUEUE)
public class TriageActivitiesImpl implements TriageActivities {
    private static final Logger LOGGER = LoggerFactory.getLogger(TriageActivitiesImpl.class);
    private static final Duration NOTIFY_DELAY = Duration.ofSeconds(2);
    private static final String NO_SUITABLE_OWNER = "none";

    private final ChatClient chatClient;

    // The ChatClient is injected directly (not the builder) so tests can pass a mock.
    TriageActivitiesImpl(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String selectOwner(Issue issue) {
        // The wrapper owns both logs, so it always writes "selecting" then "selected" around the
        // returned owner even while the demo bug short-circuits the selection in doSelectOwner.
        LOGGER.atInfo()
                .addKeyValue("issueId", issue.id())
                .log("triage.owner.selecting");
        var owner = doSelectOwner(issue);
        LOGGER.atInfo()
                .addKeyValue("issueId", issue.id())
                .addKeyValue("owner", owner)
                .log("triage.owner.selected");
        return owner;
    }

    private String doSelectOwner(Issue issue) {
        // TODO: remove, just testing
        if (true) {
            return "alice";
        }
        // Ask the LLM to pick the owner best suited to the issue, using the
        // issue-triage skill for the roster and rules, then validate the reply.
        var system = """
                You are an issue-triage assistant. Use the "issue-triage" skill to learn the
                owner roster and the selection rules, then pick the single owner best suited
                to the given issue. Reply with exactly one owner name from that roster, or the
                single token "none" if no owner is suitable. Never force a poor match and never
                invent a name.
                """;
        var user = buildUserPrompt(issue);
        var selection = chatClient.prompt().system(system).user(user).call().entity(OwnerSelection.class);
        return resolveOwner(selection.owner());
    }

    @Override
    public void notifyAssignment(Issue issue, String owner) {
        // Simulate work so the NOTIFYING step stays visible in the dashboard while the frontend
        // polls. Blocking is fine here: this is Activity code, not the Workflow, so a real
        // Thread.sleep is correct (never use it in workflow code).
        try {
            Thread.sleep(NOTIFY_DELAY);
        } catch (InterruptedException e) {
            // Restore the interrupt flag and fail so Temporal can retry the activity.
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while notifying issue assignment", e);
        }
        // No external notification in the demo: logging is enough to show the step ran.
        LOGGER.atInfo()
                .addKeyValue("issueId", issue.id())
                .addKeyValue("issueTitle", issue.title())
                .addKeyValue("owner", owner)
                .log("triage.assignment.notified");
    }

    private static String buildUserPrompt(Issue issue) {
        return """
                Issue title: %s
                Issue description: %s
                """.formatted(issue.title(), issue.description());
    }

    // The roster lives in the issue-triage skill and is only known LLM-side, so the reply cannot be
    // matched against known names here. A blank answer is a malformed reply and stays retryable so
    // Temporal tries again, while the "none" token is a deliberate no-owner verdict that fails the
    // activity non-retryably and terminates the workflow in error.
    private static String resolveOwner(String answer) {
        var candidate = answer == null ? "" : answer.trim();
        if (candidate.isEmpty()) {
            throw new IllegalStateException("LLM returned a blank owner");
        }
        if (candidate.equalsIgnoreCase(NO_SUITABLE_OWNER)) {
            throw ApplicationFailure.newNonRetryableFailure(
                    "No suitable owner in the roster for this issue", "NoSuitableOwner");
        }
        return candidate;
    }

    /** Structured result of the owner-selection call: constrains the model to reply with JSON, not free text. */
    record OwnerSelection(String owner) {
    }
}
