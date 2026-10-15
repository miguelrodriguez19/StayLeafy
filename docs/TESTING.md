# Testing strategy

## Goals

Testing should catch regressions in Phase 1 persistence, Phase 2 notes and photo storage, the independent Room v4 and unified-screen correction, Phase 3 care history, Phase 4 interval reminders, Phase 5 seasonal care, the Phase 6 visual system, Phase 7 photo framing and personalization, and Phase 8A version/release quality without making a personal project expensive to change. Reminder verification covers persistent Notices, bottom navigation, and best-effort local notifications.

The strategy favors deterministic unit tests and a small number of high-value Android and Compose tests.

## Test layers

### Pure unit tests

Use plain JVM tests for Phase 1 logic without Android dependencies:

- Plant draft normalization: trimming the required name and converting blank optional fields to `null`.
- Required-name validation, including whitespace-only input.
- Fixed UUID and clock behavior for deterministic create/update tests.
- Search/filter state transformations, including loading, empty, filtered-empty, content, and error states.
- ViewModels using fake repositories and test dispatchers.
- Note draft trimming, blank-body validation, and fixed UUID/clock behavior.
- Photo filename/MIME handling, local crop normalization, replacement compensation, temporary-file cleanup, and orphan reconciliation. Phase 2 originally asserted byte-preserving imports; Phase 7 replaces that expectation prospectively with square JPEG output for newly accepted photos.
- Shared-screen staged photo and favorite state, absence of notes during creation, atomic save coordination, and file compensation after a failed Room write.
- Care-event normalization, optional-note trimming, fixed UUID and timestamp behavior, correction timestamp stability, and rejection of future completion date-times.

Phase 4 pure tests cover:

- Positive interval validation and explicit `LatestCare` versus selected-date sources.
- Next-due calculation across month/year boundaries and leap years.
- Past first due dates and upcoming/overdue boundary classification against a fixed local date.
- Most-urgent summary selection and union semantics when Upcoming and Overdue are both active.
- Retroactive care that does not move a rule backwards, plus due-date preservation when scoped history is cleared.
- Occurrence-key generation, one-way Notice read state, and `PENDING`/`DELIVERED`/`SUPPRESSED` transitions.
- Permission denied then granted without retroactive system delivery.
- ViewModels using fake reminder and Notice repositories, fixed clocks, explicit zones, and test dispatchers.

Phase 5 pure tests add same-year, one-day, cross-year, inclusive-boundary, next-occurrence, and February 29 calculations; exclusive mode switching; retained inactive configurations; and active/satisfied summary state.

Phase 6 adds deterministic contrast tests for the light/dark brand pairs, care-status containers, and unread accent. The tests enforce WCAG AA contrast for text and at least 3:1 for the non-text unread indicator.

These tests should be fast and make up most of the suite.

### Repository tests

Test repository behavior with fakes for narrow tests and an in-memory Room database for integration behavior:

- Plant create, read, update, and delete.
- Required-name validation and optional-field normalization.
- Favorite updates and persistence.
- Literal, case-insensitive substring search across name and scientific name only, including accented Spanish text.
- Favorites-only filtering and deterministic name ordering.
- Persistence after closing and reopening the database.

Phase 4 repository tests cover atomic three-rule saves, enabled/disabled retention, transactional care completion plus rescheduling, resolving replaced occurrences, Notice read operations, unread counts, and cascade-safe missing IDs.

Phase 5 repository tests cover atomic Off/Interval/Season saves, interval/season exclusivity, retained inactive values, matching-care satisfaction, and seasonal Notice cancellation.

Avoid mocking Room DAOs when the SQL query itself is the behavior under test.

### Room DAO and migration tests

Instrumented Room tests use the real DAO and an in-memory database. Phase 1 coverage includes:

- CRUD against the v2 Plant table.
- Search terms containing `%` and `_` treated as literal text rather than SQL wildcards.
- Favorite filtering and stable ordering.
- Explicit migration from schema 1 to schema 2.
- Preservation of legacy rows and legacy names.
- Blank legacy names becoming `Plant <uid>`.
- Unique UUIDs, `false` favorite defaults, `NULL` optional fields, and migration-time `createdAt`/`updatedAt` values.
- Continued reads and writes after migration.

Phase 2 coverage adds:

- Explicit migration from schema 2 to schema 3 and the complete 1→2→3 path.
- `PlantNote` CRUD, newest-first ordering, stable creation timestamps, update timestamps, and invalid-plant foreign-key behavior.
- Notes cascading when a Plant row is deleted.
- Database reopen with notes and nullable `mainPhotoPath` values preserved.

The independent correction coverage adds:

- Explicit migration from schema 3 to schema 4 and the complete 1→2→3→4 path.
- Removal of common-name columns and deliberate discard of their values while preserving the remaining plant, note, favorite, photo, and timestamp data.
- Name/scientific-name search correctness, including `%` and `_` as literal characters.
- Shared create/view/edit screen behavior, including staged photo and favorite state, with notes excluded from creation.
- Successful atomic Room save, failed-save compensation, no partial rows, and retryable staged state.

Phase 3 coverage adds:

- Care-event draft validation for the three supported care types, trimmed optional legacy notes, and future timestamp rejection against a fixed clock.
- Repository create, observe, correct, individual-delete compatibility, and plant-and-type-scoped history clearing, including deterministic ordering and invalid-plant failures.
- Explicit migration `4→5` and the complete `1→2→3→4→5` path, exported schema v5, database reopen, care-event indexes, and Plant-delete cascade.

Phase 4 coverage adds:

- Explicit migration `5→6` and the complete `1→2→3→4→5→6` path, exported schema v6, and database reopen.
- Interval-rule validation, uniqueness by Plant/care/schedule type, enabled-and-due indexes, and Plant-delete cascade.
- Notice uniqueness by occurrence key, deterministic newest-first order, unread counts, one-way read updates, delivery states, and rule/Plant cascades.
- Upcoming and Overdue SQL boundaries, union when both are selected, and intersection with literal search and Favorites.
- Atomic three-rule persistence and care-event insertion plus reminder rescheduling.
- Repeated occurrence insertion returning one Notice rather than duplicate rows.

Phase 5 coverage adds:

- Explicit migration `6→7` and the complete `1→2→3→4→5→6→7` path, exported schema v7, and preservation of interval rules and Notices.
- Nullable modality-specific columns, seasonal rule uniqueness, enabled schedule-type indexing, Plant cascade, and database reopen.
- One seasonal Notice per annual occurrence, late in-window evaluation, matching-care suppression, and stale pending-delivery suppression after the window.
- Exact seasonal occurrence resolution when matching care is inserted.

Phase 1 deletion tests verify Plant-row deletion only. Phase 2 deletion tests verify the notes cascade, primary-photo cleanup, replacement cleanup, failed-write compensation, and startup reconciliation.

### Phase 2 photo-flow tests

Keep platform contracts thin and test storage/repository behavior with temporary app files and fake content resolvers where possible. Instrumented or manual coverage must verify:

- `TakePicture` uses an app-owned `FileProvider` URI without requesting `CAMERA`.
- `PickVisualMedia(ImageOnly)` does not request broad media permission and falls back to the document picker when unavailable.
- Cancellation, no compatible handler, unreadable URI, unwritable destination, and process recreation leave Room and stable files consistent.
- Reconciliation waits for in-flight imports, failed camera completion removes its temporary file, a pending capture blocks reentrant launches, and failed temporary cleanup retains a retryable token.
- The accepted photo file and persisted relative path survive restart. Phase 7 additionally verifies square JPEG output for new/replaced photos and confirms existing files remain unchanged.

### Phase 4 WorkManager and notification tests

Keep date evaluation and occurrence selection outside the Worker so they are unit-testable. Android integration tests cover:

- Unique periodic work configured for approximately daily evaluation at the preferred local reminder time (09:00 default), without asserting exact execution.
- Enabled, disabled, due, upcoming, and overdue rules.
- A rule saved after the current day's evaluation waiting until the next configured daily run.
- One persistent Notice and at most one stable Android notification per occurrence across repeated runs.
- Recovery from a `PENDING` Notice by updating the same stable notification tag/ID.
- Notification channel creation and localized English/Spanish content.
- PendingIntent selection of the Notices root and matching Notice identifier.
- Permission granted, denied, and later granted without retroactively delivering `SUPPRESSED` occurrences.
- Read-on-open, Mark all as read, care completion, rule disable/reschedule, and Plant deletion cancelling displayed notifications.

Do not write tests that wait for real wall-clock background execution.

### Phase 7 photo and settings coverage

- Verify camera and picker results both launch the same fixed 1:1 uCrop flow; cover zoom/pan, EXIF orientation, JPEG/PNG inputs, output dimensions/encoding, and no upscaling beyond source dimensions.
- Cover crop cancel, empty/corrupt output, URI read failure, process recreation, and cleanup of source/output temporaries. Confirm failures preserve an existing or staged photo.
- Verify staged crop restoration through plant creation, database/file compensation on save or replacement failure, restart persistence, and no rewriting of legacy photo files.
- Cover Preferences DataStore defaults, persistence, malformed or unavailable preference recovery, immediate theme/language application, and locale fallback.
- Cover preferred-time calculation before, at, and after the selected local time, time-zone/DST changes, and WorkManager unique-work updates. Assert best-effort schedule parameters rather than wall-clock execution.
- Cover notification capability status and settings action by API level; assert that camera and picker capability rows do not request broad permissions.
- Cover About/privacy version and copy, Settings navigation/restoration, always-visible bottom navigation, English/Spanish text, both themes, and deterministic previews.

Phase 5 reuses this harness for seasonal occurrence keys, schedule-specific localized content, active-window evaluation, matching-care satisfaction, and deduplication. Inventory care filters remain covered as interval-only behavior.

### Phase 1 Compose tests

Compose tests use immutable screen state and event lambdas where possible. Cover:

- Loading, empty, filtered-empty, populated, and error list states.
- Shared plant-screen validation, staged create callbacks, edit callbacks, and save/cancel behavior.
- IME next/done focus traversal, notes excluded from creation, and today's default acquisition-date selection.
- Plant detail rendering and not-found behavior.
- Delete confirmation, cancellation, and confirmation callbacks.
- Favorite toggles and favorites-only filtering.
- Literal search input and clear-filter behavior.
- English and Spanish key inventory flows.
- Light and dark preview coverage for list, empty, error, detail, form, and confirmation states.

Care completion, reminder editor, and next-due-date assertions belong to their later phases.

Phase 3 Compose and navigation coverage adds:

- Watering, pruning, and fertilizing short presses recording today inline and long presses opening a date picker.
- Per-type latest-date summaries, read-only filtered bottom sheets, newest-first ordering, scoped clear confirmation, save/delete errors, and repeated-action protection.
- Empty/loading/error/content states plus English/Spanish strings in light and dark themes.

Phase 4 Compose and navigation coverage adds:

- Three-card care configuration, explicit source selection, positive-interval validation, atomic Save, missing Plant, retry, and mutation errors.
- Last-care, next-due, upcoming, and overdue rendering in Plant detail.
- Upcoming and Overdue chips independently and together, combined with search and Favorites, plus most-urgent `+N` summaries.
- `Plants | Notices` navigation with independent state and an always-visible bottom bar on root and nested destinations.
- Notices loading, error/retry, empty, content, unread indicators, `99+` badge, read-on-open, and Mark all as read.
- Notice detail not-found state, notification-to-Notice routing, and Notice-to-Plant routing.
- Notice-to-Plant routing clears the source Notice detail so selecting Notices later returns to the inbox.
- Reselecting the active Plants or Notices tab from a nested destination returns to that tab's root list.
- Contextual permission explanation and the Notices-only blocked-permission banner with application-settings callback.
- English/Spanish text and light/dark previews for reminder configuration, care status, Notices, details, permission guidance, and confirmations.

Phase 5 Compose coverage adds Off/Interval/Once-a-year controls, the single read-only day-or-period field, range-calendar opening, required-selection validation, cross-year guidance, annual-date rendering, upcoming/active/satisfied Plant-detail state, and seasonal Notice wording in English and Spanish.

Phase 2 Compose coverage adds:

- Primary-photo placeholder and image rendering in list and detail.
- Capture/picker callbacks, replacement/removal actions, disabled repeated actions, and photo-delete confirmation.
- Notes loading, error/retry, empty, content, editor validation, save, edit, delete confirmation, and localized date text.
- Staged photo and favorite rendering before the first save, notes hidden during creation, atomic-save errors, retry, and localized name-only labels.

### End-to-end device tests

Later phases should maintain a small smoke suite for:

1. Launch the application.
2. Create a plant.
3. Add or capture its primary photo.
4. Add, edit, and delete a note.
5. Replace and remove the primary photo.
6. Restart the application and verify persistence and cleanup.
7. Record watering, pruning, and fertilizing for today and a prior date; verify each summary, filtered history, and plant-scoped clear action.
8. Configure interval rules from both supported due sources and verify detail/list due states and filters.
9. Trigger deterministic due evaluation and verify one Notice, one system notification at most, and no duplicates on another worker run.
10. Open the notification into Notice detail, open its Plant, and verify read state, badge changes, and notification cancellation.
11. Deny notification permission, verify the in-app Notice and guidance, then grant permission and verify that the suppressed occurrence is not delivered retroactively.
12. Delete the Plant and verify reminder-rule and Notice cascades.

Automated equivalents of steps 1–12 are covered by the local and connected-emulator suites completed on 2026-10-11. Physical-device notification timing and vendor battery behavior remain manual Phase 8 hardening checks.

Camera and photo-picker flows may require targeted manual verification because system UI behavior varies by device. The existing acceptance target is cancellation, handler unavailability, URI failure, and process recreation; a runtime permission-denial test is intentionally not required for the permissionless contracts.

### Phase 6 visual verification matrix

Check the existing deterministic previews and an emulator/device in this matrix without changing product data or behavior:

| Surface | Light | Dark | English | Spanish | Compact / large text |
| --- | --- | --- | --- | --- | --- |
| Plants: loading, empty, filtered, content, error | Required | Required | Required | Required | Required |
| Shared plant create/view/edit, photo and confirmations | Required | Required | Required | Required | Required |
| Care history rows, history sheet and clear confirmation | Required | Required | Required | Required | Required |
| Notes list and note editor | Required | Required | Required | Required | Required |
| Care configuration, interval and annual calendar entry | Required | Required | Required | Required | Required |
| Notices list, warning, unread state and detail | Required | Required | Required | Required | Required |

Also inspect the launcher under circle, square, rounded-square, squircle, teardrop, and themed-icon masks. Verify photo and placeholder rendering, bottom-navigation selected states and badge, software-keyboard traversal, dialogs/sheets, edge-to-edge bars, and system-owned picker/calendar surfaces. Compose tests continue to assert localized semantics and callbacks instead of pixel output.

The V2 visual direction was manually reviewed on an emulator in light and dark themes against `design/StayLeafy_mockups_v2.svg`. The review covered the Plants and Notices roots, randomized empty illustrations, plant thumbnails, Plant detail, nested bottom navigation, care configuration start-date states, local-image placeholders, pinned actions, and long English/Spanish labels.

## Commands

### Phase 8A version and release-quality checks

`version.properties` is the sole version source for the release-quality contract. The first publishable merge uses `VERSION_NAME=0.10.0` and `VERSION_CODE=26`; the bootstrap base is the legacy Gradle version `0.9.3`/`25`. The validator accepts only simple `major.minor.patch` SemVer with no prerelease or build metadata and checks that the PR title and version change agree:

- `feat` increments `minor`.
- A Conventional Commit type followed by `!` increments `major`.
- `fix`, `refactor`, `perf`, `docs`, `test`, `build`, `ci`, `chore`, `style`, and `revert` increment `patch`.
- `versionCode` increases exactly by one.
- Scopes are allowed, for example `feat(settings): ...` and `fix!: ...`; malformed or duplicate version fields fail.

The Bash test suite covers valid major/minor/patch increments, scoped titles, invalid titles, missing or duplicate versions, incorrect jumps, incorrect `versionCode` values, and release recovery states. It uses Bash, Git, and jq without an additional language runtime or test framework:

```bash
bash scripts/tests/run.sh
bash scripts/validate_version.sh --base-ref SHA --title 'feat: ...'
```

The PR workflow contract has exactly one job named `quality / verify` and runs `git diff --check`, the validator tests, the base-SHA SemVer/versionCode comparison, and the standard Gradle verification. It must use JDK 17, Gradle caching, `contents: read`, PR concurrency cancellation, SHA-pinned external actions, and no secrets or emulator tests. Test and lint reports are uploaded only when this job fails.

The release workflow runs after pushes to `main` and through `workflow_dispatch`, with one protected `release` environment job, global non-cancelling concurrency, `contents: write`, no Gradle cache restore/save, and `--no-configuration-cache` for the signed build. Its preflight must fail before tag or release mutation when the selected ref is not `main`, `version.properties` is invalid, or any signing secret is missing. The workflow has no emulator step. The strict repository ruleset already requires `quality / verify`; remote workflow execution, environment/secret configuration, and first-merge verification remain pending. Completed local checks are recorded below.

Run local unit tests:

```bash
./gradlew test
```

Build the debug application:

```bash
./gradlew assembleDebug
```

Run Android lint:

```bash
./gradlew lint
```

Run instrumented and Compose tests with a connected device or emulator:

```bash
./gradlew connectedDebugAndroidTest
```

Run the standard local verification set:

```bash
./gradlew test lint assembleDebug
```

## Test data and doubles

- Prefer small, readable fixture builders.
- Use fake repository implementations for ViewModel and navigation tests.
- Use fixed clocks or explicitly supplied dates for reminder tests.
- Do not read the real system clock from pure business logic.
- Use temporary app files for image-storage tests and remove them afterward.
- Use local image fixtures and assert the Phase 7 square-JPEG policy for new imports, legacy-file preservation, and cleanup/reconciliation by relative path.
- Do not depend on network access.

## Coverage priorities

Highest priority:

- Data loss prevention during the v1-to-v2 migration and explicit v3-to-v4 preservation rules.
- Plant CRUD and persistence across database reopen.
- Search/filter query correctness and literal wildcard handling.
- Plant deletion confirmation and row deletion.
- Room v2-to-v3 and v3-to-v4 migrations, note cascade, photo persistence, Phase 7 crop normalization for new imports, legacy-file preservation, replacement compensation, staged-create compensation, and orphan cleanup.
- Room v4-to-v5 and complete v1-to-v5 migrations, care-event cascade, ordering, scoped history clearing, summaries, and localized UI.
- Phase 4 schema v5-to-v6 migration, interval-rule and Notice cascades, transactional rescheduling, occurrence deduplication, and no-retroactive-delivery permission behavior.

Medium priority:

- ViewModel state transitions
- Search, filters, and favorites
- Form validation
- Localization-sensitive formatting
- Bottom-navigation state, Notice read behavior, care filters, and localized reminder surfaces

Phase 4 completion priorities:

- Approximately daily unique WorkManager scheduling around a configurable preferred time with a 09:00 default, without exact-time assumptions.
- Stable notification cancellation and deep links through Notice detail.
- Connected-device permission and notification behavior.

Phase 5 completion priorities:

- Seasonal date/window calculations and cross-year occurrences.
- Seasonal editor, satisfaction, and notification deduplication.

Lower priority:

- Trivial data classes
- Framework wiring already exercised by higher-value tests
- Purely decorative composables

No numeric coverage target is required initially. New non-trivial behavior should include tests proportional to its failure risk.

## Definition of done

A change is complete when:

- Its acceptance behavior is implemented.
- Relevant unit, Room, migration, persistence, ViewModel, and Compose tests exist and pass.
- Existing local tests pass.
- The debug build succeeds.
- Lint has no newly introduced actionable errors.
- Relevant product, data, reminder, or decision documentation is updated.
- Phase 2 and correction-pass photo/note checks pass on a connected emulator or device.
- Phase 3 Room v5, care-event, UI, cascade, and connected-device checks pass before the Phase 3 roadmap item is marked complete.
- Phase 4 Room v6 migration, interval rules, care filtering, Notices, WorkManager, permission, navigation, localization, and notification checks pass `./gradlew test`, `./gradlew lint`, `./gradlew assembleDebug`, and `./gradlew connectedDebugAndroidTest` on 2026-10-11.
- Phase 5 Room v7 migration, seasonal calculations, editor, satisfaction, detail state, Notice delivery, localization, and deduplication pass the same local and connected-device gates before Phase 5 is marked complete.
- Phase 5 passed `./gradlew test lint assembleDebug` and all 97 connected debug tests on the `Pixel_8a` API 36 emulator on 2026-10-13.
- Phase 6 passed `./gradlew test lint assembleDebug`, deterministic theme-contrast tests, and manual light/dark visual review on 2026-10-14. Android Gradle Plugin classified the running local emulator as an external Gradle-managed device, so `connectedDebugAndroidTest` completed without selecting it; installing the same debug and test APKs and invoking `HiltTestRunner` directly passed all 97 instrumented tests.
- Phase 7 passed `./gradlew test lint assembleDebug`, focused crop and Settings instrumentation, and the connected debug suite on the `Pixel_8a` Android 16 emulator on 2026-10-14. Coverage includes square crop validation/cleanup, preference persistence, configurable scheduling, navigation, English/Spanish resources, and light/dark previews. Physical-device reminder timing remains a Phase 8 check.
- Phase 8A local checks on 2026-10-15 passed all 25 validator/release-state tests, `git diff --check`, workflow validation with actionlint 1.7.12, and `./gradlew --no-daemon test lint assembleDebug connectedDebugAndroidTest`: 152 JVM cases (two skipped, no failures) and all 115 instrumented tests on the Pixel 8a API 36 emulator passed.
- Phase 8A signing verification on 2026-10-15 passed `assembleRelease` with an ephemeral JKS keystore and distinct store/key passwords. `apksigner` verified the APK and its certificate against that test key; `aapt2` confirmed version `0.10.0` and versionCode `26`. The key and test APK were removed. Missing and partial signing configurations failed clearly without producing an unsigned release APK. Preflight smoke checks also rejected a missing secret, invalid alias, incorrect key password, and corrupt Base64. Real signing-key setup and remote publication/recovery remain pending manual acceptance.
- Phase 8A helpers and tests now run in Bash, with jq for release-state JSON. The replacement suites passed under macOS Bash 3.2 and Bash 5; syntax checks and actionlint passed. The local Gradle gate and all 115 connected tests were rechecked successfully after the migration. The workflows and repository scripts have no dependency on an additional language runtime or test framework.
- Phase 8A remote acceptance is blocked at PR creation: the GitHub connector returned HTTP 403, `Resource not accessible by integration`. The implementation branch is pushed, but neither the actual required-check run nor a deliberately invalid version-jump run has been verified remotely. The `release` environment is absent and its four secrets must be configured manually before the first merge. No real release tag or GitHub Release was created during local validation.
