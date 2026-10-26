# I-004 — Small care, Notice, and inventory improvements

| Field      | Value       |
|------------|-------------|
| Date Added | 2026-10-16  |
| Status     | Idea        |
| Target     | Post-MVP    |
| Type       | Improvement |

## Goal

Make everyday care recording, history corrections, inbox organization, and inventory size easier to
manage.

## Initial scope

- Record at most one care event per plant, care type, and day. Repeating the action should show a
  localized toast explaining that the care is already recorded, without adding another row.
- Allow an individual care-history entry to be deleted without clearing the entire history.
- Allow a read Notice to be marked unread as an inbox-only visual change. It must not relaunch
  system notifications or restart delivery.
- Show a plant count.

## Open questions

- How should local-day interpretation, retroactive entries, and existing duplicate care events be
  handled?
- Which per-row interaction should delete an event, and how should deletion affect reminder state?
- Where should the mark-unread action appear, and how should it interact with the
  proposed [unread-only filter](I-007-unread-notices-filter.md)?
- Where should the plant count appear, and should filtered results have a separate count?
