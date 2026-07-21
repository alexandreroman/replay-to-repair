---
name: "Generated text must be in English"
description: "All generated code and documentation is written in English regardless of the conversation language"
type: feedback
---

# Generated text must be in English

All generated text — source code (identifiers, comments, strings, log
messages) and documentation (README, Markdown, code comments) — is written in
English, regardless of the language used in the conversation.

**Why:** the project is a public-facing Temporal demo; a single language keeps
the codebase and docs consistent and shareable with a broad audience.

**How to apply:** write and review all code and docs in English even when the
user communicates in another language (e.g. French). Conversational replies may
match the user's language, but committed artifacts stay English.
