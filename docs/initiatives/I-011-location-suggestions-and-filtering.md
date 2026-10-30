# I-011 — Location suggestions and filtering

| Field      | Value      |
|------------|------------|
| Date Added | 2026-10-30 |
| Status     | Idea       |
| Target     | Post-MVP   |
| Type       | Feature    |

## Goal

Make location entry consistent and allow the Plants inventory to be narrowed by the places where
plants are kept.

## Dependency

This initiative is intentionally scheduled after [I-002](I-002-configurable-plant-filters.md) and
should use the filter model, combination rules, and persistence decisions established there.

## Initial scope

- When creating or editing a plant, suggest previously used non-empty locations from the local plant
  inventory, while still allowing a new location to be entered.
- Extend the Plants filters with a location filter populated from the known locations in the local
  database (conceptually `SELECT DISTINCT location FROM plants`).
- Keep suggestions and filter values offline and derive them from current Room data; do not add a
  separate location table unless a later decision requires stable location metadata.
- Define deterministic ordering and duplicate handling for location suggestions and filter values.
- Preserve the existing free-form location value and behavior for plants whose location is blank.

## Open questions

- Should matching be case-insensitive and whitespace-normalized, and should existing values be
  migrated or only normalized when they are read?
- Should suggestions be ordered alphabetically, by recent use, or by frequency?
- How should a location filter combine with the configurable quick filters and advanced filters from
  I-002, and should the selected value persist across launches?
- Should the filter support one location or multiple locations at once?
- How should renamed or deleted locations affect existing plants and an active filter?
- Should blank or whitespace-only locations be available as an explicit “No location” filter?

