---
name: "Code style conventions"
description: "80 cols text / 120 code; latest stable deps; no blank line after a class declaration"
type: feedback
---

# Code style conventions

Wrap text and Markdown at 80 columns and code at 120 columns. Always use the
latest stable versions of dependencies, and verify the version with context7
before adding a new dependency.

Do not leave a blank line immediately after a class declaration's opening
brace: the first member (e.g. the `LOGGER` field) follows directly on the next
line.

**Why:** consistent line lengths keep diffs and docs readable; verified,
up-to-date dependencies avoid stale or insecure versions; a class body that
starts right after the brace reads more compactly.

**How to apply:** wrap prose/Markdown at 80 cols and source at 120; before
adding any dependency, resolve its latest stable version through context7; when
writing or editing a class, ensure its first member follows the opening brace
with no intervening blank line.
