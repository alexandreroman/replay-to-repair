package io.temporal.demos.replaytorepair.worker.triage;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

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

    Optional<OwnerAssignment> select(Issue issue) {
        // TODO: remove, just testing
        if (true) {
            return Optional.of(new OwnerAssignment("alice", "hardcoded for testing"));
        }
        // Ask the LLM to pick the owner best suited to the issue, using the
        // issue-triage skill for the roster and rules, then validate the reply.
        var system = """
                You are an issue-triage assistant. Use the "issue-triage" skill to learn the
                owner roster and the selection rules, then pick the single owner best suited
                to the given issue. Reply with exactly one owner name from that roster, or the
                single token "none" if no owner is suitable, and a short one-sentence reason
                justifying the pick. Never force a poor match and never invent a name.
                """;
        var user = buildUserPrompt(issue);
        var selection = chatClient.prompt().system(system).user(user).call()
                .entity(OwnerSelection.class, spec -> spec.useProviderStructuredOutput());
        return resolveOwner(selection);
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
    // non-retryable failure. The reason is best-effort: it is requested from the model but tolerated
    // as absent, so a null or blank reason is normalized to null rather than treated as an error.
    private static Optional<OwnerAssignment> resolveOwner(OwnerSelection selection) {
        var candidate = selection.owner() == null ? "" : selection.owner().trim();
        if (candidate.isEmpty()) {
            throw new IllegalStateException("LLM returned a blank owner");
        }
        if (candidate.equalsIgnoreCase(NO_SUITABLE_OWNER)) {
            return Optional.empty();
        }
        var reason = selection.reason() == null ? null : selection.reason().trim();
        if (reason != null && reason.isEmpty()) {
            reason = null;
        }
        return Optional.of(new OwnerAssignment(candidate, reason));
    }

    /**
     * Structured result of the owner-selection call: both the owner and the reason are enforced
     * provider-side as an API-level constraint, not via prompt instructions.
     *
     * <p>Parsing is defensive: unknown fields the model volunteers (e.g. a confidence score) are
     * ignored, field names bind explicitly without relying on the {@code -parameters} compiler flag,
     * and common LLM key variants (e.g. {@code assignee}, {@code justification}) map to the canonical
     * components.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record OwnerSelection(
            @JsonProperty("owner") @JsonAlias({"assignee", "name"}) String owner,
            @JsonProperty("reason") @JsonAlias({"justification", "explanation", "rationale"}) String reason) {
    }
}
