---
name: "Never print .env contents"
description: "Never display .env or .env.local contents — they hold real secrets (live ANTHROPIC_API_KEY)"
type: feedback
---

# Never print .env contents

Never display, `cat`, `echo`, or otherwise print the contents of `.env` or
`.env.local`. These files hold real secrets, including a live
`ANTHROPIC_API_KEY`. To confirm a variable is set, test for its presence
without revealing the value (for example `grep -q ANTHROPIC_API_KEY .env &&
echo set`).

**Why:** printing these files leaks usable credentials into the conversation
transcript and any command logs; the Anthropic key in `.env` is a real,
working secret.

**How to apply:** when a target needs values from `.env`/`.env.local`, rely on
the Makefile loading them, or check presence with a non-revealing test; never
print the file, quote its contents, or include them in output or commits. If a
secret is ever exposed, recommend rotating it.
