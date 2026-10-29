# I-007 — Unread-only Notices filter

| Field      | Value      |
|------------|------------|
| Date Added | 2026-10-23 |
| Status     | Done       |
| Target     | Post-MVP   |
| Type       | Feature    |

## Goal

Help users focus on Notices they have not read yet.

## Scope and decisions

- Add an eye toggle in the Notices app bar immediately left of overflow. Unread-only filtering is on by default; the closed-eye state hides read Notices and the open-eye state shows all Notices.
- Keep the selection in an activity-scoped in-memory ViewModel. It survives tab changes, Notice detail navigation, and configuration changes, then defaults to unread-only after process recreation. Do not persist it as a user preference or in Room.
- Keep the existing read-on-open and Mark all as read behavior. A Notice opened while unread-only filtering is active disappears reactively from the inbox when the user returns from detail.
- When no unread Notices remain, reuse the existing empty illustration and show localized “No unread notices” / “No tienes avisos sin leer” copy without a call to action. Keep the eye toggle available so the user can show all Notices.
- Treat filtering as inbox presentation. Keep the global unread badge, delivery state, and Android notification behavior unchanged. Do not add a mark-unread action or Room schema change.

## Acceptance criteria

- The eye toggle is directly left of overflow and exposes the agreed closed-eye/open-eye states.
- The default list contains unread Notices only; switching to the open-eye state includes read and unread Notices.
- Filter selection survives navigation among tabs, Notice detail, and activity configuration changes, and a fresh process starts with unread-only filtering.
- Opening a Notice marks it read under the existing behavior and the unread-only inbox no longer displays it after returning.
- The filtered-empty view uses the existing illustration and localized English/Spanish copy (“No unread notices” / “No tienes avisos sin leer”), has no call to action, and still allows the toggle to show all Notices.
- Existing badge, Mark all as read, delivery, and system-notification behavior remains intact. No mark-unread action, preference persistence, or Room migration is introduced.

## Verification

- `./gradlew test lint assembleDebug` passed on 2026-10-29 (182 unit tests: 180 passed and 2 skipped).
- `./gradlew connectedDebugAndroidTest` passed on the Pixel_8a API 36 emulator (155 tests, no failures or skips).
- Instrumented coverage includes filter semantics and English/Spanish empty copy, dark theme, read-on-open, Mark all as read, and selection retention across tabs, detail navigation, and activity recreation.
- After the final empty-filter accent adjustment, the full local suite and all 10 Notice screen instrumented tests passed again. Manual emulator inspection verified the English/light and Spanish/dark filtered-empty screens.
