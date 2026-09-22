package io.temporal.demos.replaytorepair.worker.triage.llm;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.temporal.demos.replaytorepair.worker.triage.Issue;
import io.temporal.demos.replaytorepair.worker.triage.OwnerAssignment;
import io.temporal.demos.replaytorepair.worker.triage.OwnerSelector;

/** Owner selection backed by an LLM through Spring AI, using the issue-triage skill for the roster. */
@Component
@Profile("!jev")
class LlmOwnerSelector implements OwnerSelector {
    private static final String NO_SUITABLE_OWNER = "none";

    private final ChatClient chatClient;
    private final Timer selectionTimer;

    // The ChatClient is injected directly (not the builder) so tests can pass a mock. The
    // ChatModel alongside it is the model that same client calls through.
    LlmOwnerSelector(ChatClient chatClient, ChatModel chatModel, MeterRegistry meterRegistry) {
        this.chatClient = chatClient;
        // Both engines report to this meter: "engine" tells the two apart and "model" records the
        // model id each one calls. The model id comes from the ChatModel's default options, which
        // hold it as the starter resolved it for the calls actually sent, rather than from a
        // second reading of the property the starter itself binds.
        this.selectionTimer = Timer.builder("triage.owner.selection")
                .description("Time taken by the selection engine to pick an owner")
                .tag("engine", "llm")
                .tag("model", chatModel.getDefaultOptions().getModel())
                .register(meterRegistry);
    }

    @Override
    public Optional<OwnerAssignment> select(Issue issue) {
        // Timer#record stops its sample in a finally block, so a selection that fails is timed too.
        return selectionTimer.record(() -> selectOwner(issue));
    }

    private Optional<OwnerAssignment> selectOwner(Issue issue) {
        // Ask the LLM to pick the owner best suited to the issue, using the
        // issue-triage skill for the roster and rules, then validate the reply.
        var system = """
                You are an issue-triage assistant. Use the "issue-triage" skill to learn the
                owner roster and the selection rules, then pick the single owner best suited
                to the given issue and give a short one-sentence reason for the choice. Use an
                owner name copied verbatim from the roster, or "none" when no owner is
                suitable. Never force a poor match and never invent a name.
                """;
        var user = buildUserPrompt(issue);
        var selection = chatClient.prompt().system(system).user(user).call()
                .entity(OwnerSelection.class, ChatClient.EntityParamSpec::useProviderStructuredOutput);
        return resolveOwner(selection);
    }

    private static String buildUserPrompt(Issue issue) {
        return """
                Issue title: %s
                Issue description: %s
                """.formatted(issue.issueTitle(), issue.issueDescription());
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
