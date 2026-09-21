package io.temporal.demos.replaytorepair.worker.triage.jev;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;

import io.temporal.demos.replaytorepair.worker.triage.jev.client.JevClient;

/**
 * Wires the Jev owner-selection engine: a {@link JevClient} configured from {@link JevProperties}
 * and built on the auto-configured {@link RestClient.Builder}, which is what puts this profile's
 * {@code spring.http.clients} settings behind every call. The client carries no retry of its own:
 * a failed call surfaces as an exception the Activity propagates, and Temporal's retry policy
 * decides what happens next.
 */
@Configuration(proxyBeanMethods = false)
@Profile("jev")
@EnableConfigurationProperties({JevProperties.class, TriageRosterProperties.class})
class JevConfiguration {
    @Bean
    JevClient jevClient(RestClient.Builder builder, JevProperties properties) {
        return new JevClient(builder, properties.baseUrl(), properties.apiKey(), properties.model());
    }
}
