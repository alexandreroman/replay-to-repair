package io.temporal.demos.replaytorepair.backend.triage;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Component;

/** Supplies random issues for the triage demo, drawn from a fixed in-memory dataset. */
@Component
class IssueGenerator {
    /** Small static, fictional dataset covering distinct areas: backend, security, infra, frontend, analytics/ML. */
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
                            + "environment on an expired certificate."),
            new Issue("cart-race-condition",
                    "Cart total miscalculated under concurrent updates",
                    "Adding and removing items from the same cart concurrently produces a total that "
                            + "does not match the line items, because the update path is not atomic."),
            new Issue("search-timeout",
                    "Product search times out on large catalogs",
                    "The product search query performs a full table scan and times out once the "
                            + "catalog grows past a few hundred thousand rows."),
            new Issue("csrf-missing",
                    "State-changing endpoints missing CSRF protection",
                    "Several POST endpoints accept requests without a CSRF token, letting a malicious "
                            + "page perform actions on behalf of an authenticated user."),
            new Issue("rate-limit-bypass",
                    "API rate limiting bypassed via forwarded header",
                    "The rate limiter keys on a client-supplied forwarding header, so an attacker can "
                            + "rotate the header value to bypass throttling entirely."),
            new Issue("logs-fill-disk",
                    "Log files fill the disk and crash the node",
                    "Verbose request logging is never rotated, so the disk fills within days and the "
                            + "node stops accepting new writes."),
            new Issue("dns-intermittent",
                    "Intermittent DNS resolution failures in the cluster",
                    "Pods intermittently fail to resolve internal service names, causing sporadic "
                            + "request failures that clear on retry."),
            new Issue("dark-mode-flash",
                    "Dark mode flashes white on page load",
                    "Users with dark mode enabled see a brief white flash on every navigation because "
                            + "the theme is applied only after the first paint."),
            new Issue("dropdown-hidden",
                    "Dropdown menu hidden behind sticky header",
                    "The account dropdown renders underneath the sticky header due to a "
                            + "stacking-context conflict, so its top items are unclickable."),
            new Issue("timezone-offset",
                    "Timestamps shown in the wrong timezone",
                    "Activity timestamps are rendered in the server timezone instead of the viewer's, "
                            + "so events appear hours off for users abroad."),
            new Issue("webhook-retry-storm",
                    "Webhooks retried indefinitely on client errors",
                    "Outgoing webhooks are retried even on 4xx responses, producing a retry storm "
                            + "that overwhelms the receiver's endpoint."),
            new Issue("cache-stale",
                    "Stale data served after cache invalidation",
                    "Updated records keep returning old values for minutes because the cache "
                            + "invalidation event is published before the write commits."),
            new Issue("csv-formula-injection",
                    "Exported CSV allows formula injection",
                    "User-supplied fields are written to CSV exports without escaping, so a crafted "
                            + "value executes as a formula when opened in a spreadsheet."),
            new Issue("secrets-in-logs",
                    "API keys leaked into application logs",
                    "Outbound request logging prints full headers, writing third-party API keys into "
                            + "plaintext logs shipped to the aggregator."),
            new Issue("cors-wildcard",
                    "CORS allows any origin with credentials",
                    "The API responds with a wildcard allowed-origin while also allowing credentials, "
                            + "exposing authenticated endpoints to any site."),
            new Issue("deploy-drops-requests",
                    "Rolling deploy drops in-flight requests",
                    "During a rolling deploy, terminating pods stop serving before draining, so "
                            + "in-flight requests fail with connection resets."),
            new Issue("cron-runs-twice",
                    "Scheduled job runs twice across replicas",
                    "A nightly job is triggered independently on every replica because there is no "
                            + "leader election, causing duplicate side effects."),
            new Issue("form-double-submit",
                    "Order form submitted twice on double-click",
                    "Double-clicking the submit button places two identical orders, as the button is "
                            + "not disabled after the first click."),
            new Issue("i18n-missing-keys",
                    "Untranslated keys shown to non-English users",
                    "Missing translation keys render as raw identifiers instead of falling back to "
                            + "English, exposing internal keys to users."),
            new Issue("db-pool-exhausted",
                    "Database connection pool exhausted under load",
                    "Requests hang and then fail during traffic spikes because connections are held "
                            + "open longer than needed and the pool runs dry."),
            new Issue("mobile-keyboard-overlap",
                    "On-screen keyboard hides the input field",
                    "On Android the on-screen keyboard covers the focused input at the bottom of the "
                            + "form, so users cannot see what they type."),
            new Issue("recommendation-model-drift",
                    "Recommendation model accuracy drops after retraining",
                    "The nightly retraining job ships a model whose ranking accuracy is noticeably worse "
                            + "than the previous one, degrading recommendation quality for every user."),
            new Issue("dau-metric-mismatch",
                    "Daily active users disagree across dashboards",
                    "Two analytics dashboards report different daily-active-user counts for the same day "
                            + "because each applies a different sessionization rule to the event stream."),
            new Issue("feature-pipeline-drops-rows",
                    "Feature pipeline silently drops rows with missing values",
                    "The machine-learning feature pipeline discards any event with a null feature instead "
                            + "of imputing it, quietly shrinking the training set and biasing the model."),
            new Issue("ab-test-assignment-skew",
                    "A/B experiment traffic split is skewed",
                    "The experimentation framework assigns far more users to the control group than "
                            + "intended, so variant results never reach statistical significance."),
            new Issue("churn-model-stale-features",
                    "Churn model scores on stale features",
                    "The churn-prediction model scores users against features computed days earlier, so "
                            + "recent behaviour never influences the prediction and alerts fire late."),
            new Issue("funnel-double-counts",
                    "Conversion funnel double-counts repeat events",
                    "The analytics funnel counts a user who revisits a step twice as two separate "
                            + "conversions, inflating the reported conversion rate on every dashboard."));

    /** Returns a randomly picked issue from the dataset. */
    Issue next() {
        return ISSUES.get(ThreadLocalRandom.current().nextInt(ISSUES.size()));
    }
}
