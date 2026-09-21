---
name: "Code style conventions"
description: "80 cols text / 120 code; latest stable deps; present-tense comments scoped to their subject"
type: feedback
---

# Code style conventions

Wrap text and Markdown at 80 columns and code at 120 columns. Always use the
latest stable versions of dependencies, and verify the version with context7
before adding a new dependency.

Do not leave a blank line immediately after a class declaration's opening
brace: the first member (e.g. the `LOGGER` field) follows directly on the next
line.

Write code comments in the present tense, describing how the code works as a
standing fact. Comments never narrate past decisions or changes: ban markers
like "now", "now lives", "no longer", "previously / used to", "was moved",
"changed to", "we decided", "originally". A comment read on its own must not
reveal what the code replaces or what just happened.

A comment describes its subject at that subject's own altitude. A comment on
infrastructure states what the infrastructure does in general, and names a
single concrete user of it only where the code itself is specific to that
user. In the metrics setup this draws a clear line: the `worker/pom.xml`
dependency comments and the `server.port` / `management.server.port` /
`management.endpoints.web.exposure` comments describe exposing **the
application's** metrics over Actuator and its OpenMetrics scrape endpoint,
while `management.metrics.distribution` — whose keys are literally
`"[triage.owner.selection]"` — and the `Timer.builder(...)` call sites in the
two selection engines name that meter and explain the `engine` tag.

**Why:** consistent line lengths keep diffs and docs readable; verified,
up-to-date dependencies avoid stale or insecure versions; a class body that
starts right after the brace reads more compactly; present-tense comments stay
accurate as the code evolves and do not decay into misleading history; a
comment pitched at its subject's altitude survives the arrival of a second
user of the same infrastructure, instead of describing the whole as if it
existed for one caller.

**How to apply:** wrap prose/Markdown at 80 cols and source at 120; before
adding any dependency, resolve its latest stable version through context7; when
writing or editing a class, ensure its first member follows the opening brace
with no intervening blank line; phrase every comment as present-tense current
behavior, rewriting any clause that only makes sense knowing the prior state;
before naming a specific feature in a comment, check whether the code under it
is specific to that feature — if it is not, describe the general capability.
