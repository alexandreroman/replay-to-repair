package io.temporal.demos.replaytorepair.worker.triage.jev;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Pins the two safety settings the Jev engine relies on, as the auto-configured
 * {@link TypeSafeClient} actually binds them from {@code application-jev.yaml}: retry off and a
 * 20s budget. Both are one upstream property rename away from silently reverting to the SDK
 * defaults, which no other test would catch — {@link JevOwnerSelectorOfflineTest} builds its
 * client by hand.
 *
 * <p>Retry stays at zero because Temporal's Activity retry policy is the only retry in this
 * system: a second layer underneath it hides attempts from the event history the demo replays.
 * The budget stays under the Activity's 30s start-to-close timeout so a stalled call fails here
 * with a cause naming the real problem, rather than letting Temporal's own deadline fire first
 * with an opaque ActivityTaskTimedOut.
 *
 * <p>Offline by design: building the client performs no I/O, so the dummy key below stands in for
 * a real one and nothing reaches the network.
 */
@SpringBootTest(properties = "spring.ai.typesafe.api-key=dummy-key")
@ActiveProfiles({"test", "jev"})
class JevClientSettingsTest {
    @Autowired
    private TypeSafeClient typeSafeClient;

    @Test
    void retryIsOffAndTheTimeoutFitsTheActivityDeadline() {
        assertThat(typeSafeClient.retryPolicy().maxRetries()).isZero();
        assertThat(typeSafeClient.timeout()).isEqualTo(Duration.ofSeconds(20));
    }
}
