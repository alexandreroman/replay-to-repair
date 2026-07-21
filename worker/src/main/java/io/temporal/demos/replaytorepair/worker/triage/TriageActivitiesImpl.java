package io.temporal.demos.replaytorepair.worker.triage;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import io.temporal.spring.boot.ActivityImpl;

@Component
@ActivityImpl(taskQueues = IssueTriageWorkflow.TASK_QUEUE)
public class TriageActivitiesImpl implements TriageActivities {
    private static final Logger LOGGER = LoggerFactory.getLogger(TriageActivitiesImpl.class);

    private final ChatClient chatClient;

    // The ChatClient is injected directly (not the builder) so tests can pass a mock.
    TriageActivitiesImpl(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public List<OwnerProfile> loadProfiles() {
        var profiles = List.of(
                new OwnerProfile("alice", "backend"),
                new OwnerProfile("bob", "infrastructure"),
                new OwnerProfile("carol", "security"),
                new OwnerProfile("dave", "frontend"),
                new OwnerProfile("erin", "data"));
        LOGGER.atDebug()
                .addKeyValue("profileCount", profiles.size())
                .log("Loaded owner profiles");
        return profiles;
    }

    @Override
    public String selectOwner(Issue issue, List<OwnerProfile> profiles) {
        // TODO: remove, just testing
        if (true) {
            return "alice";
        }
        // Real logic below. It is unreachable while the debug line above is present, but it still
        // compiles: Java does not flag statements after `if (true) return` as unreachable code.
        var system = """
                You are an issue-triage assistant. You are given a software issue and a list of
                owners, each with a single specialty. Pick the one owner whose specialty best
                matches the issue.
                """;
        var user = buildUserPrompt(issue, profiles);
        var selection = chatClient.prompt().system(system).user(user).call().entity(OwnerSelection.class);
        var owner = resolveOwner(selection.owner(), profiles);
        LOGGER.atInfo()
                .addKeyValue("issueId", issue.id())
                .addKeyValue("owner", owner)
                .log("Selected owner for issue");
        return owner;
    }

    @Override
    public void notifyAssignment(Issue issue, String owner) {
        // No external notification in the demo: logging is enough to show the step ran.
        LOGGER.atInfo()
                .addKeyValue("issueId", issue.id())
                .addKeyValue("issueTitle", issue.title())
                .addKeyValue("owner", owner)
                .log("Notified issue assignment");
    }

    private static String buildUserPrompt(Issue issue, List<OwnerProfile> profiles) {
        var owners = profiles.stream()
                .map(profile -> "- %s: %s".formatted(profile.name(), profile.specialty()))
                .collect(Collectors.joining("\n"));
        return """
                Issue title: %s
                Issue description: %s

                Owners:
                %s
                """.formatted(issue.title(), issue.description(), owners);
    }

    // Keep the model honest: the reply must name one of the known owners. We trim it and match it
    // case-insensitively; an unrecognized reply (including null or blank) is treated as a failure so
    // Temporal retries the activity, rather than assigning an arbitrary owner.
    private static String resolveOwner(String answer, List<OwnerProfile> profiles) {
        var candidate = answer == null ? "" : answer.trim();
        return profiles.stream()
                .map(OwnerProfile::name)
                .filter(name -> name.equalsIgnoreCase(candidate))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "LLM returned an unknown owner '%s'; expected one of %s"
                                .formatted(candidate, profiles.stream().map(OwnerProfile::name).toList())));
    }

    /** Structured result of the owner-selection call: constrains the model to reply with JSON, not free text. */
    record OwnerSelection(String owner) {
    }
}
