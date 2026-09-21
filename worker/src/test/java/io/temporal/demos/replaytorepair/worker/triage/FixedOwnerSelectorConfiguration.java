package io.temporal.demos.replaytorepair.worker.triage;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Makes {@link FixedOwnerSelector} the {@link OwnerSelector} injected into the Activity, so a test
 * exercises the triage flow without calling a selection engine.
 *
 * <p>Tests opt in with {@code @Import(FixedOwnerSelectorConfiguration.class)}. The double is
 * deliberately not registered on the {@code test} profile itself: {@code OwnerSelectorProfileTest}
 * asserts that the profile resolves the real engine.
 */
@TestConfiguration(proxyBeanMethods = false)
class FixedOwnerSelectorConfiguration {
    @Bean
    @Primary
    OwnerSelector fixedOwnerSelector() {
        return new FixedOwnerSelector();
    }
}
