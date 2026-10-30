# I-006 — Bulk care quick actions

| Field      | Value      |
|------------|------------|
| Date Added | 2026-10-23 |
| Status     | Done       |
| Target     | Post-MVP   |
| Type       | Feature    |

## Goal

Make it easier to record care for several plants at once. Multiple watering, pruning, or fertilizing
Notices often arrive together, and the user commonly cares for several plants in the same session.

## Accepted scope

- Provide a quick-actions button that allows selecting multiple plants.
- Let the user mark the selected plants as watered, pruned, or fertilized together.
- Coordinate duplicate-day behavior with the proposed rule
  in [I-004](I-004-care-notice-inventory-improvements.md).

## Interaction and acceptance criteria

1. A checklist icon in the Plants app bar starts multi-selection, with a localized accessible action label. Rows toggle checkboxes rather than opening details; adding plants and changing favorites are unavailable during selection.
2. Search and filters remain usable. Selections hidden by a filter persist, with only a selected count displayed. Select visible adds all current results, including off-screen rows; Clear selection removes all selections.
3. Continue requires at least one plant. Record care uses the shared Settings section-card/segmented-control design to require one care type and a non-future local date, initially today. A selected-plants card previews all selected names and 40dp thumbnails, including hidden plants. Register opens a confirmation dialog showing type, date, and count, rather than a separate review screen.
4. Confirming closes the dialog and disables repeated submissions and form edits while saving. Success leaves selection mode and reports care and recorded/skipped counts in a dismissible card under the Plants filters, including empty filtered results. Omitted duplicates are mentioned only when present; all duplicates produce an explanatory no-op result. The result has no timeout and clears on dismiss, starting another selection, or leaving Plants. Failure retains the form draft for retry and commits nothing.
5. Closing the confirmation dialog retains the form. Returning from the form retains selection, care type, and date. Cancelling selection or leaving Plants discards the draft without writes. Rotation retains the draft/dialog; process death restores the normal screen without automatically recording care. Existing filters and I-001 scroll behavior are preserved.
6. Both individual and bulk creation skip an existing same-plant, same-type event on the chosen day in the current timezone. Individual duplicates show a localized toast. Existing historical and restored duplicates remain intact.
7. The entire batch, including plant-existence validation and matching reminder/Notice effects, is atomic. Notification cancellation follows commit. A missing selected plant fails the whole batch for review and retry.

## Implementation boundaries

- Keep the UI/data split, Hilt ViewModels, lifecycle-aware StateFlow, and Room as source of truth.
- Use the existing operation coordinator and transactional reminder completion logic; do not implement bulk as separately committed single-plant calls.
- Keep Room schema v7 and the logical backup format unchanged. Compute local-day bounds from consecutive starts of day to handle timezone/DST differences.
- Provide all new UI strings in English and Spanish; no service, permission, or dependency is added.
- Only the duplicate-prevention part of I-004 is included. History-entry deletion, Notice unread actions, and inventory counts remain outside this initiative.

## Verification

- Repository and Room coverage for mixed/all duplicates, repeat attempts, empty/future input, missing plants, rollback, concurrency, local-day boundaries, and reminder/Notice effects.
- ViewModel and UI coverage for filtered selections, thumbnails/placeholders, dates, confirmation/cancellation, guarded submission, result-card variants, failures, rotation/exit, shared Settings controls, and existing filter/position behavior.
- Run `./gradlew test`, `./gradlew lint`, and `./gradlew assembleDebug`; run `./gradlew connectedDebugAndroidTest` when a device is connected.

### Local verification — 2026-10-29

- `./gradlew test lint assembleDebug compileDebugAndroidTestKotlin` passed. The unit suite contains 193 tests, with two existing photo-platform tests skipped.
- `bash scripts/tests/run.sh` passed (63 version-validation checks and 19 release-state tests).
- Room and Compose instrumented tests compile but were not executed: no Android device or emulator was connected. Device-level verification, including actual rotation and compact-screen rendering, remains pending.
- Independent integrated-code review found no actionable issues.

### Redesign verification — 2026-10-30

- Replaced the text action with a checklist icon, retained only the selected count, and reused the Settings section-card/segmented-control components. The Record care form now includes photo/name previews, a responsive calendar row, and a confirmation dialog. The separate review screen and snackbar are removed.
- Completion is a persistent dismissible card below the Plants filters, with localized plurals and explicit duplicate-only feedback. Long preview names and narrow-screen date rows wrap instead of clipping.
- `./gradlew test lint assembleDebug compileDebugAndroidTestKotlin --max-workers=1` passed (193 unit tests: 191 passed and two existing photo-platform cases skipped).
- `./gradlew connectedDebugAndroidTest --max-workers=1` passed all 181 tests on the existing Android 11/API 30 emulator, including Settings regression, confirmation/cancellation, real-photo/placeholder previews, filtered-empty/mixed/duplicate-only results, and Spanish dark-theme large-text layout coverage.
- The existing `Medium_Phone_API_36.1` AVD (Android 16) passed 63 data/non-Compose tests, but all 119 Compose tests failed before rendering because the current Espresso runner reflects on the unavailable `InputManager.getInstance()` API. The UI suite was therefore rerun successfully on API 30; this does not establish automated UI compatibility on the newer AVD. Manual Spanish-form visual verification was performed on that AVD.
- Run lint separately from instrumented packaging, or use a single Gradle worker: parallel lint/Hilt generation can race on generated Android-test component files. No lint checks were disabled.
- Independent review findings about noun labels, long names, and repeated duplicate-message context were addressed.
