# I-001 — Preserve the Plants list position

| Field      | Value      |
|------------|------------|
| Date Added | 2026-10-16 |
| Status     | Idea       |
| Target     | Post-MVP   |
| Type       | Bug        |

## Goal

Returning from plant detail after editing should preserve the user's position in the Plants list,
avoiding repeated scrolling through a large inventory.

## Initial scope

- Reported bug: opening a plant, modifying it, and returning with the back arrow resets the Plants
  list to its beginning.
- Restore the list position when returning from that flow.

## Open questions

- How should restoration behave if the edit changes the plant's ordering or filter membership?
