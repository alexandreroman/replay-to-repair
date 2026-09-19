package io.temporal.demos.replaytorepair.worker.triage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection settings for the Jev decision model.
 *
 * @param baseUrl the TypeSafe API base URL the systemone endpoint hangs off
 * @param model   the Jev model id
 * @param apiKey  the TypeSafe API key
 */
@ConfigurationProperties("triage.jev")
record JevProperties(String baseUrl, String model, String apiKey) {
    // Overridden so a binding error or bean-creation failure that logs constructor arguments
    // cannot put the live API key into an ECS log line.
    @Override
    public String toString() {
        var maskedApiKey = apiKey == null || apiKey.isBlank() ? apiKey : "****";
        return "JevProperties[baseUrl=%s, model=%s, apiKey=%s]".formatted(baseUrl, model, maskedApiKey);
    }
}
