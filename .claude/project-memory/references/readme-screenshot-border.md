---
name: "README screenshot border treatment"
description: "Every README screenshot carries the same rounded-corner #828892 border, sized proportionally to the image width"
type: project
---

# README screenshot border treatment

Every screenshot referenced from `README.md`
carries the same frame: a solid `#828892` stroke
inset from the edge, with the outer corners
rounded and fully transparent (no drop shadow,
no background fill).

The geometry is proportional to the image width,
so all screenshots look identical once GitHub
scales them to the README column. Taking
`app.png` (1448 px wide) as the reference,
with `k = width / 1448`:

- corner radius `28.5 * k`
- stroke inset `2 * k` from the image edge
- stroke width `4 * k`

A macOS window grab is first cropped to its
opaque window box (`magick in.png -alpha extract
-threshold 50% -format %@ info:`) so the system
drop shadow is gone before the frame is drawn.

**Why:** the screenshots sit next to each other
in the README and are all downscaled to the same
column width; a fixed pixel border would render
thinner on the higher-resolution grabs and the
set would look inconsistent.

**How to apply:** render the mask and the stroke
ring at 4x and downsample for clean antialiasing
— build the ring as a filled rounded rectangle at
the inset minus a filled rounded rectangle at
inset + stroke width, colorize it, composite it
over the source, then copy a full-bleed rounded
rectangle into the alpha channel.
