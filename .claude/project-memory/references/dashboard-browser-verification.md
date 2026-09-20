---
name: "Verify the dashboard in a painting browser panel"
description: "Card entry animations only advance in the visible Casper panel, so verify with `casper browser open` or wait for opacity to settle"
type: feedback
---

# Verify the dashboard in a painting browser panel

Check the dashboard in a browser context that actually paints: open it in the
visible Casper panel with `casper browser open <url>`, or, when driving it
headlessly, wait for the card entry animation to settle before asserting
anything:

```bash
casper browser wait --js \
  'getComputedStyle(document.querySelector("article")).opacity === "1"'
```

`casper browser load` and the off-screen `screenshot --width/--height` path
render in a background page that does not paint, where the
`.animate-card-in` animation on each issue `<article>` stays at its `from`
keyframe. An issue card then reports `opacity: 0`, clips to roughly 50px
through its `overflow-hidden`, and contributes no text to
`document.body.innerText` — while the card's markup, including the
`workflowUrl` anchor, is present in the DOM.

**Why:** that combination is indistinguishable from a dashboard whose cards
never render, so it invites a hunt for a frontend bug that does not exist.
The owner chips carry their own `.animate-owner-in` animation and mislead the
same way.

**How to apply:** assert against the visible panel, or gate every headless
assertion on the settle check above. Reading `opacity`, `offsetHeight`, or
`innerText` on a freshly loaded background page proves nothing about the
render. See [[frontend-tailwind-v4-browser]] for where these keyframes live.

## Two more traps in the same family

`casper browser wait --js` evaluates its argument as an **expression**. A
predicate opening with `const` raises `SyntaxError: Unexpected keyword
'const'` on every poll, so the wait runs to its full timeout and the page's
console fills with errors that read as application errors. Write predicates
expression-only; `casper browser eval` does accept statements, but it shares
one scope across calls, so a second `const a = …` fails with "Can't create
duplicate variable". Wrap `eval` bodies in an IIFE.

The Temporal Web UI's User Metadata tab renders its Markdown outside
`document.body.innerText` — asserting on page text reports the summary and
details as missing while the tab displays them correctly. Confirm that tab
with `casper browser screenshot` (see [[temporal-user-metadata-ui-only]]).
