---
name: "Jev's decision-model wire format"
description: "POST https://api.typesafe.ai/v1/systemone; {model, state, questions} in, {answers} out; no client-side retry"
type: reference
---

# Jev's decision-model wire format

Jev, TypeSafe's decision model, is reached at
`POST https://api.typesafe.ai/v1/systemone`, authenticated with a Bearer
`TYPESAFE_AI_API_KEY`. The model id is `jev-latest`. The request body is
`{model, state, questions}`: `state` is a free-form map of the facts to judge,
and `questions` is a map of question id to question. A `choice` question
carries `instructions` (the natural-language question) plus a `criteria` map
of option name to option description — the options offered to the model.

The response body is `{answers: {<question-id>: {choice, probabilities,
confidence}}}` plus a root-level `model` field and a `usage` field with
`input_tokens`/`output_tokens`. `choice` is the winning option,
`probabilities` is the calibrated distribution over every offered option, and
`confidence` is Jev's confidence in `choice`.

Jev is a decision model, not a chat model: it answers typed questions with a
calibrated probability distribution instead of generating text, so a
`ChatClient` cannot address it. The Jev owner-selection engine
(`JevOwnerSelector`) calls `/v1/systemone` through a plain `RestClient`
instead.

The `RestClient` carries no retry of its own: Temporal's Activity retry
policy is the only retry in this system, so a failed call surfaces as an
exception the Activity propagates.

**Why:** a decision model answers typed questions with a calibrated
probability distribution rather than generating text, so it needs its own
endpoint and its own request/response shape — it is not a drop-in
chat-completions model; layering a second retry underneath Temporal's would
only obscure how many attempts a triage actually took.

**How to apply:** build `questions` from the roster (see
[[skills-tool-owner-roster]]) as a single `choice` question per decision;
read `answers.<question-id>.choice` for the pick and `.confidence` for a
calibrated score. Do not add client-side retry to the `RestClient` bean or
its configuration.
