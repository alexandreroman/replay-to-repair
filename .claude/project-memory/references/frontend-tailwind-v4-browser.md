---
name: "Frontend styling: Tailwind v4 browser build"
description: "index.html loads the pinned @tailwindcss/browser CDN build and declares theme tokens in a CSS-first @theme block"
type: project
---

# Frontend styling: Tailwind v4 browser build

`frontend/index.html` is a single static page with no frontend toolchain and no
build step. It loads Tailwind CSS from
`https://cdn.jsdelivr.net/npm/@tailwindcss/browser@<version>`, pinned to an
exact stable version rather than a floating major.

Theme tokens (`--font-display`, `--font-mono`, `--color-violet`,
`--color-lime`, `--color-slateb`) are declared CSS-first inside an
`@theme { … }` rule in a `<style type="text/tailwindcss">` element. The
separate plain `<style>` block holds ordinary CSS (backgrounds, `.icon` masks,
keyframes, `.grad-btn`, `[x-cloak]`) and stays unlayered.

**Why:** the v4 browser build has no JS configuration object, so theme
customization only exists as CSS. Keeping the hand-written CSS in a plain
`<style>` element keeps it out of Tailwind's cascade layers, where it would
lose against utilities.

**How to apply:** add or change theme tokens in the `@theme` block, never in a
JS config. Do not add `@import "tailwindcss";` — the browser build imports it
implicitly and a second import duplicates the stylesheet. Verify the latest
stable version against the npm registry `dist-tags.latest` before bumping.
Two v4 defaults are compensated explicitly and must stay: `backdrop-blur-sm`
is the 8px blur, and the generate button carries `cursor-pointer` because
Preflight sets `cursor: default` on `<button>`.
