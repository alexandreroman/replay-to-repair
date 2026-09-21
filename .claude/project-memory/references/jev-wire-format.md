---
name: "Jev's decision-model wire format"
description: "POST /v1/systemone; choice/score/noul questions batch in one call; no client-side retry"
type: reference
---

# Jev's decision-model wire format

Jev, TypeSafe's decision model, is reached at
`POST https://api.typesafe.ai/v1/systemone`, authenticated with a Bearer
`TYPESAFE_API_KEY` (see [[engine-env-var-naming]]). The request body is
`{model, state, questions}`: `state` is a free-form map of the facts to
judge, and `questions` is a map of question id to question. The response is
`{model, answers, usage}`, with `answers` keyed by the same question ids.

There are three question types. Each has its own `criteria` shape and its own
answer shape, and both the question and its answer carry a `type`
discriminator:

| Type     | `criteria`                             | Answer fields                                    |
|----------|----------------------------------------|--------------------------------------------------|
| `choice` | map of option to description, 255 max  | `choice`, `probabilities`, `confidence`          |
| `score`  | ordered array of 2 to 10 levels        | `score`, `legend`, `probabilities`, `confidence` |
| `noul`   | optional `{"true": …, "false": …}` map | `noul` alone                                     |

A `score` answer is a continuous position on a zero-based scale: `legend`
maps the stringified level index to its label, and `probabilities` is keyed
by those same indices. A `noul` answer carries no `confidence` — the `noul`
value is itself the probability that the answer is yes.

Questions of mixed types batch into a single call. TypeSafe evaluates them
independently and in parallel, so one answer never becomes context for
another, and documents batching as 12.2x cheaper and 10x faster than the
equivalent sequential calls. `state` and all questions share a budget of
about 64k tokens, and `state` plus the longest single question must fit in
about 32k.

Jev is a decision model, not a chat model: it answers typed questions with a
calibrated probability distribution instead of generating text, so a
`ChatClient` cannot address it. `JevClient` (package `triage.jev.client`)
owns this format — typed questions in, typed answers out, one question or a
batch — along with its own connection settings: it takes the auto-configured
`RestClient.Builder`, the base URL, the API key and the model id, builds the
`RestClient` itself and rejects a missing key at construction. It clones that
builder before configuring it: a builder configures itself in place and hands
itself back, so the base URL and the `Authorization` header would otherwise be
imprinted on the builder every other client in the application shares. A
question copies the criteria it is handed into an order-preserving map
(`LinkedHashMap`, never `Map.copyOf`), because for a `choice` that iteration
order is the order the options reach the model.
`JevClientOfflineTest` pins the format against JSON captured from the live
API, and `JevClientTest` checks the three answer shapes against the real one.
The client carries no retry of its own: Temporal's Activity retry policy is
the only retry in this system, so a failed call surfaces as an exception the
Activity propagates.

**Why:** a decision model answers typed questions with a calibrated
probability distribution rather than generating text, so it needs its own
endpoint and its own request/response shape — it is not a drop-in
chat-completions model; layering a second retry underneath Temporal's would
only obscure how many attempts a triage actually took.

**How to apply:** pick the question type that matches the decision — a
`choice` over named options, a `score` over ordered levels, a `noul` for a
yes/no probability — and send every question a single decision needs in one
call rather than one call each. Owner selection builds its `choice` criteria
from the roster (see [[skills-tool-owner-roster]]) and reads
`choice` plus `confidence`. Do not add client-side retry to `JevClient`, the
`RestClient` bean, or its configuration.
