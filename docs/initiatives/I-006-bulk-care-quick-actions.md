# I-006 — Bulk care quick actions

| Field      | Value      |
|------------|------------|
| Date Added | 2026-10-23 |
| Status     | Idea       |
| Target     | Post-MVP   |
| Type       | Feature    |

## Goal

Make it easier to record care for several plants at once. Multiple watering, pruning, or fertilizing
Notices often arrive together, and the user commonly cares for several plants in the same session.

## Initial scope

- Provide a quick-actions button that allows selecting multiple plants.
- Let the user mark the selected plants as watered, pruned, or fertilized together.
- Coordinate duplicate-day behavior with the proposed rule
  in [I-004](I-004-care-notice-inventory-improvements.md).

## Open questions

- Where should the button and plant-selection flow appear?
- How should selecting and deselecting plants work?
- Should the action always record today, or allow another care date?
- Is confirmation needed, and how should completion be communicated?
- What should happen when some selected plants already have that care recorded on the same day?
- How should partial failures and retries be handled?
