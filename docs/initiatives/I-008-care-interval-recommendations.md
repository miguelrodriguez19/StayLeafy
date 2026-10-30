# I-008 — Care interval recommendations from history

| Field      | Value      |
|------------|------------|
| Date Added | 2026-10-30 |
| Status     | Idea       |
| Target     | Post-MVP   |
| Type       | Feature    |

## Goal

Help the user configure a realistic care interval from what they actually did, including cases
where they intentionally watered, pruned, or fertilized earlier or later than the reminder.

## Initial scope

- Add a `Calculate interval` action to the care-configuration flow for a selected care type.
- Use recent matching care history as the input for a recommendation. For example, two care events
  separated by 11 days should be able to inform a recommendation even when the existing configured
  interval was different.
- Present the calculated value as a recommendation that the user can review and accept before it
  changes the draft; calculating must not silently save the rule.
- Keep the current explicit interval and due-date behavior when the user does not accept a
  recommendation or when there is not enough history.
- Include care recorded from both individual and bulk actions, using the existing local-date
  interpretation.

## Open questions

- Should the recommendation be the arithmetic mean, median, or a robust statistic that limits the
  effect of one unusually early or late event?
- How many matching events are required, and which history window counts as “recent”?
- Should the calculation use consecutive care-event gaps, the gap from the configured due date, or
  both? How should historical duplicate events affect the result?
- Should the result be rounded, bounded by a minimum/maximum, or shown with an explanation of the
  source events and gaps?
- After accepting the recommendation, should the next due date be recalculated from the latest care
  event, preserve the current due date, or require a separate choice?
- Should the action be available for seasonal schedules, or only for interval schedules?

