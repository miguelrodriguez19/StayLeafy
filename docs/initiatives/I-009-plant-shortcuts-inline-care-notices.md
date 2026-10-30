# I-009 — Plant shortcuts and inline care Notices

| Field      | Value      |
|------------|------------|
| Date Added | 2026-10-30 |
| Status     | Idea       |
| Target     | Post-MVP   |
| Type       | Feature    |

## Goal

Make Notices a direct shortcut to the affected plant and keep its outstanding care visible in the
plant context, without requiring a separate Notice preview screen.

## Initial scope

- Selecting a Notice opens the corresponding Plant detail destination directly.
- When a plant has one or more outstanding care Notices, show an inline banner below the plant name
  and above the description.
- Show multiple outstanding Notices in a horizontal carousel. Order cards by due date ascending so
  the oldest pending care appears first; for example, a watering Notice from October 15 precedes
  fertilizing from October 25 and pruning from October 30.
- Use the existing semantic urgency language and colors: overdue care is shown as urgent/red and
  care due today is shown as due/yellow, with localized text and accessible labels.
- Keep the banner scoped to unresolved Notices and ensure it updates when care is recorded or a
  Notice is otherwise resolved.

## Open questions

- Should opening a Notice mark only the selected Notice as read, mark all Notices shown for that
  plant as read, or leave read state unchanged until an explicit action?
- Can the user dismiss a banner with `X`, and if so does that hide one occurrence, all occurrences
  for the plant, or only the banner until the next evaluation?
- Should each card offer Snooze, Record care, or another action? If snooze is supported, what local
  schedule and persistence rules should it use?
- Should a card tap open care configuration, record the care immediately, or only focus the relevant
  care type?
- Are future Notices ever shown in the banner, or only overdue and today-due occurrences?
- What is the compact-screen behavior for the carousel, and how are multiple care types announced to
  TalkBack?
- How should deep links from an Android system notification behave after the preview destination is
  removed, and what navigation/read-state changes are required in the existing Notice pipeline?

