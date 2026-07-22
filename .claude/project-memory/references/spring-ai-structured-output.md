---
name: "Spring AI structured output over raw String"
description: "Prefer Spring AI structured output (call().entity(record)) instead of parsing a raw String LLM response"
type: feedback
---

# Spring AI structured output over raw String

When calling an LLM through Spring AI's `ChatClient`, constrain the model to a
JSON structure mapped to a Java record via `...call().entity(SomeRecord.class)`
rather than reading a free-text `...call().content()` String and parsing it by
hand. Spring AI's `BeanOutputConverter` derives a JSON schema from the record
and parses the response.

Prefer provider-native structured output when the provider supports it: the
`entity(Class, spec -> spec.useProviderStructuredOutput())` overload delivers the
derived JSON schema to the provider as an API-level constraint instead of
appending format instructions to the prompt text. The worker owner-selection
call uses this against the Anthropic provider.

Keep a validation step after parsing when the value must match a known,
runtime-provided set (e.g. the returned owner name against the loaded
[[demo-design-constraints]] owner profiles). When the model's value matches
nothing, throw a plain (retryable) exception so Temporal retries the activity,
rather than degrading to an arbitrary fallback.

**Why:** structured output makes the model's contract explicit and the response
robust to formatting noise, instead of relying on brittle prompt phrasing like
"reply with the name only".

**How to apply:** define a small record for the expected shape and call
`call().entity(TheRecord.class)`; drop "reply with X only" instructions from the
prompt since the format instructions are injected automatically. Records used
this way can stay package-private (Spring AI's converter handles them). See the
worker `TriageActivitiesImpl.selectOwner` for the reference usage.
