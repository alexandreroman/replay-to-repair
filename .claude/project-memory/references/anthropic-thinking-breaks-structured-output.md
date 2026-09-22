---
name: "Extended thinking breaks Spring AI structured output"
description: "A thinking content block lands ahead of the answer generation, so entity() parses it instead of the JSON; the LLM engine runs with thinking disabled"
type: project
---

# Extended thinking breaks Spring AI structured output

The LLM owner-selection engine runs with Anthropic's extended thinking
disabled, set as an `AnthropicChatOptions` bean in `ChatClientConfiguration`
and pinned by `ChatClientSettingsTest`. It is a workaround for a defect in
Spring AI 2.0.1, not a tuning choice.

Three upstream behaviours combine:

- `AnthropicChatModel#buildGenerations` appends one `Generation` per
  `thinking` and `redacted_thinking` content block **from inside** its
  content-block loop, and appends the aggregate generation holding the
  accumulated text and tool calls only **after** the loop. A thinking block
  therefore lands ahead of the answer.
- `ChatResponse#getResult()` returns the first generation.
- `DefaultChatClient`'s content extraction reads
  `getResult().getOutput().getText()`, and that is the text `entity(...)`
  hands to the `BeanOutputConverter`.

The converter parses the thinking text instead of the JSON, in one of three
shapes: an empty thinking text (what Claude returns in practice, carrying only
a signature) fails with `MismatchedInputException: No content to map due to
end-of-input`; a non-empty one fails as malformed JSON; a
`redacted_thinking` block carries no content at all, so `entity(...)` returns
`null`. Tool calling is unaffected, because `ChatResponse#hasToolCalls()`
scans every generation rather than the first.

Measured with a live probe of 8 to 12 calls per variant: a thinking block
appears in about 1 call in 20 with the issue-triage skill attached, and in 7
of 7 without it — the roster in front of the model is what saves it from
deliberating. Calls that produced one billed 243 to 445 completion tokens
against 118 to 153 for the same answer, so the reasoning is billed and never
returned.

Spring AI only sends the `thinking` field when the options carry one
(`AnthropicChatModel.createRequest`), so leaving it unset hands the decision
to the model's own adaptive default. The setting cannot live in
`application.yaml`: `spring.ai.anthropic.chat.thinking` exists but is typed as
the Anthropic SDK's `ThinkingConfigParam`, a Stainless-generated union with
private constructors and no setters, which Boot's binder can neither
instantiate nor convert from a scalar.

`ChatClient.Builder#defaultOptions` merges rather than replaces in Spring AI
2.0.1: `DefaultChatClientUtils` starts from `chatModel.getOptions().mutate()`
and applies the customizer over it with `combineWith`. The options bean is
therefore a pure delta carrying only the thinking setting — everything bound
from `spring.ai.anthropic.*` still reaches the request. Keep it a delta:
`combineWith` concatenates list and map fields, so options that duplicated the
model's own would double `stopSequences`, `citationDocuments` or custom
headers if any were ever configured.

**Why:** the failure is intermittent and Temporal's Activity retry hides it —
the triage succeeds on the next attempt, so only the extra attempt in the
event history betrays it, in the very artefact this demo replays. Owner
selection asks for one JSON object drawn from a roster and gains nothing from
extended reasoning, which is why suppressing the trigger beats reordering the
response: an advisor that rearranged the generations would be three times the
code and would key on internal property names.

**How to apply:** leave thinking disabled while the upstream defect stands,
and remove the bean and its test once Spring AI reads the aggregate
generation rather than the first. An engine that asks a chat model for
structured output should either disable thinking the same way or read the
last generation itself. See [[spring-ai-structured-output]] and
[[temporal-owns-every-retry]].
