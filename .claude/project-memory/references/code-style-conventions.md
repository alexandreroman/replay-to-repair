---
name: "Code style conventions"
description: "80 cols text / 120 code; latest stable deps; no blank line after a class declaration; present-tense comments"
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

**Why:** consistent line lengths keep diffs and docs readable; verified,
up-to-date dependencies avoid stale or insecure versions; a class body that
starts right after the brace reads more compactly; present-tense comments stay
accurate as the code evolves and do not decay into misleading history.

**How to apply:** wrap prose/Markdown at 80 cols and source at 120; before
adding any dependency, resolve its latest stable version through context7; when
writing or editing a class, ensure its first member follows the opening brace
with no intervening blank line; phrase every comment as present-tense current
behavior, rewriting any clause that only makes sense knowing the prior state.
