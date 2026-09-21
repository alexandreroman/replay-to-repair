---
name: "Jev's decision-model wire format"
description: "POST /v1/systemone; choice/score/noul questions batch in one call; the spring-ai-typesafe SDK owns the format"
type: reference
---

# Jev's decision-model wire format

Jev, TypeSafe's decision model, is reached at
`POST https://api.typesafe.ai/v1/systemone`, authenticated with a Bearer
`TYPESAFE_API_KEY` (see [[engine-env-var-naming]]). The request body is
`{model, state, questions}`: `state` is a free-form map of the facts to
judge, and `questions` is a map of question id to question. The response is
`{model, answers, usage}`, with `answers` keyed by the same question ids.

The authority for the format is TypeSafe's own documentation
(<https://docs.typesafe.ai>) and the SDK that implements it; what follows is
the shape this project relies on. There are three question types. Each has its own `criteria` shape and its own
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
`ChatClient` cannot address it. The format belongs to the spring-ai-community
TypeSafe SDK — `org.springaicommunity:spring-ai-starter-typesafe` (version
property `typesafe.version` in `worker/pom.xml`) — which auto-configures a
`TypeSafeClient` bean from `spring.ai.typesafe.*` as soon as `api-key` holds a
value. An application that declares no such property starts without the bean;
`application-jev.yaml` declares it as `${TYPESAFE_API_KEY:}`, so a `jev`
worker started without the variable aborts at context refresh with "No API key
configured. Set spring.ai.typesafe.api-key." Its `base-url`
defaults to `https://api.typesafe.ai`, without the `/v1` segment the API paths
carry, so the endpoint needs no configuration.

A question is assembled with
`Choice.builder().instructions(...).option(label, description).build()`, whose
builder preserves insertion order — for a `choice` that order is the order the
options reach the model. The call is
`typeSafeClient.systemOne(state, Map.of(questionId, question))` and the answer
is read as `response.choice(questionId)`: `value()` for the selected label,
`probabilities()`, and a primitive `double confidence()`. Failures are typed
subclasses of `TypeSafeException`: `TypeSafeMissingAnswerException` for an
answer name the response does not carry, `TypeSafeAnswerTypeException` for a
type mismatch, `TypeSafeApiResponseValidationException` for a response whose
`answers` map is empty, and one exception per HTTP status. The SDK applies its
own `RetryPolicy`, which `spring.ai.typesafe.retry.max-retries: 0` disables so
Temporal owns every attempt (see [[temporal-owns-every-retry]]).

**Why:** a decision model answers typed questions with a calibrated
probability distribution rather than generating text, so it needs its own
endpoint and its own request/response shape — it is not a drop-in
chat-completions model. The SDK is the community integration for that API, so
the wire format, its typed exceptions and the tests pinning them live upstream
rather than in this repository; layering a second retry underneath Temporal's
would only obscure how many attempts a triage actually took.

**How to apply:** pick the question type that matches the decision — a
`choice` over named options, a `score` over ordered levels, a `noul` for a
yes/no probability — and send every question a single decision needs in one
call rather than one call each. Owner selection builds its `choice` criteria
from the roster (see [[skills-tool-owner-roster]]) and reads
`choice` plus `confidence`. Keep `spring.ai.typesafe.retry.max-retries` at
`0`, and build a hand-made `TypeSafeClient` in a test with
`RetryPolicy.noRetry()` so a mocked HTTP failure reaches the assertion once.
