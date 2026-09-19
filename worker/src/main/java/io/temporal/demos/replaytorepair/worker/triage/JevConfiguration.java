package io.temporal.demos.replaytorepair.worker.triage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

/**
 * Wires the Jev owner-selection engine. The client carries no retry of its own: a failed call
 * surfaces as an exception the Activity propagates, and Temporal's retry policy decides what
 * happens next.
 */
@Configuration(proxyBeanMethods = false)
@Profile("jev")
@EnableConfigurationProperties({JevProperties.class, TriageRosterProperties.class})
class JevConfiguration {
    @Bean
    RestClient jevRestClient(RestClient.Builder builder, JevProperties properties) {
        // Fail at startup, not three retries into the first triage: an unset key still lets the
        // worker start and every call dies with an opaque 401 discovered mid-demo. There is no
        // JSR-303 provider on this module's classpath, so this is a plain check rather than
        // @Validated/@NotBlank, which would be silently inert.
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "The Jev owner-selection engine needs a TypeSafe API key: set TYPESAFE_AI_API_KEY");
        }
        return builder
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .build();
    }
}
