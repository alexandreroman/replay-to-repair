---
name: "Engine credentials and model ids are environment variables"
description: "TYPESAFE_API_KEY follows TypeSafe's official SDK; TYPESAFE_MODEL mirrors ANTHROPIC_MODEL and defaults to jev-latest"
type: project
---

# Engine credentials and model ids are environment variables

Each owner-selection engine reads its credential and its model id from the
environment, the model with a default in the profile's Spring config:

- Spring AI — `ANTHROPIC_API_KEY` and `ANTHROPIC_MODEL` (default
  `claude-sonnet-5`), in `application.yaml`.
- Jev — `TYPESAFE_API_KEY` and `TYPESAFE_MODEL` (default `jev-latest`), in
  `application-jev.yaml`.

`TYPESAFE_API_KEY` is the variable name TypeSafe's own SDKs read.
`TYPESAFE_MODEL` mirrors `ANTHROPIC_MODEL` rather than the SDK's
`TYPESAFE_DEFAULT_MODEL`.

**Why:** the credential is what someone is most likely to already have
exported for the official SDK, so it matches upstream and works unchanged;
the model id is a project knob that sits next to `ANTHROPIC_MODEL` in the
README configuration table, where symmetry between the two engines carries
more weight than matching upstream.

**How to apply:** name a new engine's variables `<PROVIDER>_API_KEY` and
`<PROVIDER>_MODEL`, matching the provider SDK's own name for the credential.
Give the model a default in the profile's YAML so the engine runs with only
a key set, and add both to the README configuration table. See
[[jev-wire-format]].
