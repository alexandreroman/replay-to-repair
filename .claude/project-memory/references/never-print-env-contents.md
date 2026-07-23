---
name: "Never print .env contents"
description: "Never display .env contents — it holds real secrets (live ANTHROPIC_API_KEY)"
type: feedback
---

# Never print .env contents

The project uses a single git-ignored `.env` file for all local configuration,
including secrets; there is no `.env.local`. Never display, `cat`, `echo`, or
otherwise print the contents of `.env`. It holds real secrets, including a live
`ANTHROPIC_API_KEY`. To confirm a variable is set, test for its presence
without revealing the value (for example `grep -q ANTHROPIC_API_KEY .env &&
echo set`).

**Why:** printing this file leaks usable credentials into the conversation
transcript and any command logs; the Anthropic key in `.env` is a real,
working secret.

**How to apply:** when a target needs values from `.env`, rely on the Makefile
loading it, or check presence with a non-revealing test; never print the file,
quote its contents, or include them in output or commits. If a secret is ever
exposed, recommend rotating it.
