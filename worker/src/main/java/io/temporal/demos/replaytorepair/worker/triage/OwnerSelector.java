package io.temporal.demos.replaytorepair.worker.triage;

import java.util.Optional;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/** Picks the owner best suited to an issue, isolated from the Activity so it can be unit-tested. */
@Component
class OwnerSelector {
    private static final String NO_SUITABLE_OWNER = "none";

    private final ChatClient chatClient;

    // The ChatClient is injected directly (not the builder) so tests can pass a mock.
    OwnerSelector(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    Optional<String> select(Issue issue) {
        // TODO: remove, just testing
        if (true) {
            return Optional.of("alice");
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

    private static String buildUserPrompt(Issue issue) {
        return """
                Issue title: %s
                Issue description: %s
                """.formatted(issue.title(), issue.description());
    }

    // The roster lives in the issue-triage skill and is only known LLM-side, so the reply cannot be
    // matched against known names here. This component stays Temporal-agnostic: a blank answer is a
    // malformed reply and throws, while the "none" token is the deliberate no-suitable-owner verdict
    // and yields an empty Optional. It is the Activity that turns that empty result into Temporal's
    // non-retryable failure.
    private static Optional<String> resolveOwner(String answer) {
        var candidate = answer == null ? "" : answer.trim();
        if (candidate.isEmpty()) {
            throw new IllegalStateException("LLM returned a blank owner");
        }
        if (candidate.equalsIgnoreCase(NO_SUITABLE_OWNER)) {
            return Optional.empty();
        }
        return Optional.of(candidate);
    }

    /** Structured result of the owner-selection call: constrains the model to reply with JSON, not free text. */
    record OwnerSelection(String owner) {
    }
}
