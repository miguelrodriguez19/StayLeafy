# I-003 — Keep notification status readable in Settings

| Field      | Value      |
|------------|------------|
| Date Added | 2026-10-16 |
| Status     | Idea       |
| Target     | Post-MVP   |
| Type       | Bug        |

## Goal

Keep notification information, status, and the settings/permission action readable when permission
is revoked.

## Initial scope

- Reported visual bug: granting notification permission and later revoking it in Android Settings
  produces a long red status chip that squeezes the information column.
- Bound the chip's width and allow its text to wrap.
- Use the proposed two-thirds information / one-third status-and-settings-action split as the
  planning direction.
- Verify compact widths, large font sizes, and English/Spanish in both themes.

## Open questions

- Does the proposed weighted layout remain usable at the smallest supported width and largest font
  scale, or does it need a stacked fallback?
