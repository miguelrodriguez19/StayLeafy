# I-001 — Preserve the Plants list position

| Field      | Value      |
|------------|------------|
| Date Added | 2026-10-16 |
| Status     | Done       |
| Target     | Post-MVP   |
| Type       | Bug        |

## Goal

Returning from plant detail after editing should preserve the user's position in the Plants list,
avoiding repeated scrolling through a large inventory.

## Initial scope

- Preserve the Plants list's first visible item index and scroll offset when the user opens a plant's
  detail or edit screen and returns with the app-bar back arrow or system Back.
- Preserve that position across device rotation and other configuration changes, and through
  transient list loading states.
- If editing changes the plant's sort order or filter membership, restore the same numeric list
  position rather than following the edited plant. Clamp the position to a valid item when the
  resulting list is shorter.
- A user change to the search query or filters, including clearing them, starts the list at the
  beginning.
- Keep this restoration in in-memory UI/navigation state. It does not persist across a future app
  launch and does not add a Room schema change or dependency.

## Acceptance criteria

1. Scroll the Plants list, open a plant's detail or edit screen, then return with either back
   mechanism; the same first visible item and offset are restored.
2. Rotate while on the list or while viewing a plant, and verify the saved list position is retained
   after the screen is recreated.
3. Return while list data is temporarily loading; loading completion does not reset the restored
   position.
4. Edit a plant so it moves to another numeric position or no longer matches the active filter;
   return to the list and retain the previous numeric position, clamped to the last valid position
   when fewer items remain.
5. Change the search query or a filter, or clear either selection; the list starts at its beginning.
6. Close and relaunch the app; list position is not restored from the prior launch.
7. The behavior works offline in English and Spanish and introduces no persisted schema change or
   dependency.

## Verification

- `./gradlew test`, `./gradlew lint`, and `./gradlew assembleDebug` passed on 2026-10-29.
- `./gradlew connectedDebugAndroidTest` passed all 166 tests on a Pixel 8a emulator with Android 16
  (API 36), including navigation return, numeric restoration after edits, filter resets, transient
  states, and activity recreation with a nonzero scroll offset.
- MainActivity remains portrait-only; activity recreation exercises saved-state restoration without
  changing the application's orientation policy.
