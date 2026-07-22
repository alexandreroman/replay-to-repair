package io.temporal.demos.replaytorepair.backend.triage;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Component;

/** Supplies random issues for the triage demo, drawn from a fixed in-memory dataset. */
@Component
class IssueGenerator {
    /** Small static, fictional dataset covering distinct areas: backend, security, infra, frontend. */
    private static final List<Issue> ISSUES = List.of(
            new Issue("checkout-500",
                    "Checkout endpoint returns HTTP 500 under load",
                    "The checkout API intermittently fails with HTTP 500 once concurrent requests "
                            + "exceed a few hundred per second."),
            new Issue("login-sqli",
                    "Login form vulnerable to SQL injection",
                    "The login form concatenates the username directly into the SQL query, allowing "
                            + "injection through crafted input."),
            new Issue("pods-oomkilled",
                    "Kubernetes pods OOMKilled after deploy",
                    "Application pods are OOMKilled minutes after each deploy because the memory "
                            + "limit is set below the real heap footprint."),
            new Issue("submit-misaligned",
                    "Submit button misaligned on mobile Safari",
                    "On mobile Safari the submit button overflows its container and overlaps the "
                            + "footer, making it hard to tap."),
            new Issue("jwt-logout",
                    "JWT tokens not invalidated on logout",
                    "Logging out does not revoke the issued JWT, so a stolen token keeps working "
                            + "until it naturally expires."),
            new Issue("tls-renewal",
                    "TLS certificate renewal fails in staging",
                    "The automated TLS certificate renewal job fails in staging, leaving the "
                            + "environment on an expired certificate."));

    /** Returns a randomly picked issue from the dataset. */
    Issue next() {
        return ISSUES.get(ThreadLocalRandom.current().nextInt(ISSUES.size()));
    }
}
