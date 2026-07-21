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
    public TriageActivitiesImpl(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public List<OwnerProfile> loadProfiles() {
        return List.of(
                new OwnerProfile("alice", "backend"),
                new OwnerProfile("bob", "infrastructure"),
                new OwnerProfile("carol", "security"),
                new OwnerProfile("dave", "frontend"),
                new OwnerProfile("erin", "data"));
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
                owners, each with a single specialty. Reply with the name of the one owner whose
                specialty best matches the issue. Reply with the name only, nothing else.
                """;
        var user = buildUserPrompt(issue, profiles);
        var answer = chatClient.prompt().system(system).user(user).call().content();
        return normalize(answer, profiles);
    }

    @Override
    public void notifyAssignment(Issue issue, String owner) {
        // No external notification in the demo: logging is enough to show the step ran.
        LOGGER.info("Issue '{}' assigned to owner '{}'", issue.title(), owner);
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

    // Keep the model honest: trim its answer, match it against a known owner (case-insensitive),
    // and fall back to the first profile if the reply does not name a valid owner.
    private static String normalize(String answer, List<OwnerProfile> profiles) {
        var candidate = answer == null ? "" : answer.trim();
        return profiles.stream()
                .map(OwnerProfile::name)
                .filter(name -> name.equalsIgnoreCase(candidate))
                .findFirst()
                .orElseGet(() -> profiles.getFirst().name());
    }
}
