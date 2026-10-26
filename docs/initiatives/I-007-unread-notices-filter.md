# I-007 — Unread-only Notices filter

| Field      | Value      |
|------------|------------|
| Date Added | 2026-10-23 |
| Status     | Idea       |
| Target     | Post-MVP   |
| Type       | Feature    |

## Goal

Help users focus on Notices they have not read yet.

## Initial scope

- Add a simple filter in the Notices screen that hides read Notices and shows only unread Notices.
- Treat filtering as inbox presentation: it must not change delivery state or relaunch system
  notifications.

## Open questions

- Which visual control should enable and disable the filter?
- Should the selection persist across navigation or app restarts, and what should its default be?
- What should happen when a Notice is read while the filter is active, including when returning from
  detail?
- How should the filtered-empty state appear, and how should the filter interact with the
  proposed [mark-unread action](I-004-care-notice-inventory-improvements.md)?
