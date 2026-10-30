# Post-MVP initiatives

Work after the `v1.0.0` production release is organized as initiatives and tasks rather than more
numbered phases. The [MVP roadmap](../ROADMAP-MVP.md) retains the remaining Phase 8 acceptance and
release steps.

The initiatives below track completed work, active work, and post-MVP ideas. Their identifiers and
order do not establish priority. Resolve open questions and define acceptance criteria before
starting new implementation.

## Index

| ID    | Initiative                                                                                    | Status | Type        |
|-------|-----------------------------------------------------------------------------------------------|--------|-------------|
| I-001 | [Preserve the Plants list position](I-001-preserve-plants-list-position.md)                   | Done   | Bug         |
| I-002 | [Configurable quick filters and advanced filtering](I-002-configurable-plant-filters.md)      | Idea   | Feature     |
| I-003 | [Keep notification status readable in Settings](I-003-settings-notification-status-layout.md) | Done   | Bug         |
| I-004 | [Small care, Notice, and inventory improvements](I-004-care-notice-inventory-improvements.md) | Idea   | Improvement |
| I-005 | [AI writing-assistance spike](I-005-ai-writing-assistance-spike.md)                           | Idea   | Spike       |
| I-006 | [Bulk care quick actions](I-006-bulk-care-quick-actions.md)                                   | Done   | Feature     |
| I-007 | [Unread-only Notices filter](I-007-unread-notices-filter.md)                                  | Done   | Feature     |
| I-008 | [Care interval recommendations from history](I-008-care-interval-recommendations.md)         | Idea   | Feature     |
| I-009 | [Plant shortcuts and inline care Notices](I-009-plant-shortcuts-inline-care-notices.md)       | Idea   | Feature     |
| I-010 | [Stable Plants card layout](I-010-stable-plants-card-layout.md)                               | Idea   | Improvement |
| I-011 | [Location suggestions and filtering](I-011-location-suggestions-and-filtering.md)             | Idea   | Feature     |

## Creating and maintaining an initiative

- Copy [TEMPLATE.md](TEMPLATE.md) and use an English filename in the form
  `I-NNN-short-description.md`.
- Set `Date Added` to the original proposal date in `yyyy-mm-dd` format. Preserve that date when
  moving or updating an initiative rather than replacing it with the migration or commit date.
- Allocate the next identifier after the highest existing number. The next identifier is currently
  `I-008`. Never reuse an identifier or renumber existing initiatives, including dropped ones.
- Preserve I-001 through I-005 and the existing group of small improvements in I-004.
- Start with `Status: Idea` and `Target: Post-MVP`. Choose one type: `Bug`, `Feature`,
  `Improvement`, or `Spike`.
- Allowed statuses are `Idea`, `Planned`, `In progress`, `Done`, and `Dropped`. Keep each document's
  header and this index in sync.
- Use `Planned` only after scope, acceptance criteria, and relevant UX or persistence decisions are
  defined. Expand the minimal template as needed then.
- Recording an idea does not approve an architectural or product-constraint change. Keep application
  behavior and documentation in English, with English/Spanish resources for user-facing strings when
  implemented.

## Unpromoted ideas

- Multiple photos per plant
- User-defined care types
- Plant groups or rooms
- Calendar overview
- Home-screen widget

These possibilities have no initiative identifiers yet. Explicitly prioritize and scope an idea
before promoting it to an initiative document.
