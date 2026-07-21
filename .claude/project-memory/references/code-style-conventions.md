---
name: "Code style conventions"
description: "Line length 80 for text/Markdown and 120 for code; always latest stable dependency versions"
type: feedback
---

# Code style conventions

Wrap text and Markdown at 80 columns and code at 120 columns. Always use the
latest stable versions of dependencies, and verify the version with context7
before adding a new dependency.

**Why:** consistent line lengths keep diffs and docs readable; verified,
up-to-date dependencies avoid stale or insecure versions.

**How to apply:** wrap prose/Markdown at 80 cols and source at 120; before
adding any dependency, resolve its latest stable version through context7.
