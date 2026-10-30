# I-010 — Stable Plants card layout

| Field      | Value      |
|------------|------------|
| Date Added | 2026-10-30 |
| Status     | Idea       |
| Target     | Post-MVP   |
| Type       | Improvement |

## Goal

Make the cards in the Plants inventory visually predictable when plants have different optional
fields, so the upcoming-care content does not move vertically from card to card.

## Initial scope

- Define a fixed or consistently constrained card structure for the Plants list.
- Reserve stable space for the plant identity, optional metadata, and next-care status even when a
  field is absent.
- Keep the next-care area aligned at the same vertical position across cards with and without
  descriptions, locations, scientific names, or other optional content.
- Handle long names and narrow screens without clipping, overlap, or an unbounded card height.
- Preserve existing navigation, favorites, search, filters, and due-state semantics.

## Open questions

- Which dimensions should be fixed, and which rows should be reserved versus collapsed when empty?
- Should missing fields use placeholders, remain blank, or be replaced by a compact metadata row?
- Should cards have one-line truncation, two-line wrapping, or another rule for long plant names and
  care labels?
- How should the layout adapt to accessibility font scaling, landscape, tablets, and Spanish strings?
- Is the desired result a fixed card height, a fixed next-care baseline with flexible content above,
  or a small set of approved card variants?

