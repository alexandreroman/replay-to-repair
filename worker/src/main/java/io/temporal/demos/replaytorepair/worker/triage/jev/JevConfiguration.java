package io.temporal.demos.replaytorepair.worker.triage.jev;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Binds the owner roster the Jev engine turns into its question, on the profile that engine runs
 * on. The client it calls through comes from the spring-ai-community TypeSafe starter, configured
 * under {@code spring.ai.typesafe.*} in {@code application-jev.yaml}.
 */
@Configuration(proxyBeanMethods = false)
@Profile("jev")
@EnableConfigurationProperties(TriageRosterProperties.class)
class JevConfiguration {
}
