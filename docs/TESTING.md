# Testing strategy

## Goals

Testing should catch regressions in Phase 1 persistence, Phase 2 notes and photo storage, the independent Room v4 and unified-screen correction, and Phase 3 care history without making a personal project expensive to change. These flows are covered by local, Room, Compose, navigation, and connected-device checks.

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
- Photo filename/MIME normalization, original-byte copying, replacement compensation, temporary-file cleanup, and orphan reconciliation.
- Shared-screen staged photo and favorite state, absence of notes during creation, atomic save coordination, and file compensation after a failed Room write.
- Care-event normalization, optional-note trimming, fixed UUID and timestamp behavior, correction timestamp stability, and rejection of future completion date-times.

Phase 1 does not test interval, seasonal, notification, or WorkManager behavior; those belong to later phases.

These tests should be fast and make up most of the suite.

### Repository tests

Test repository behavior with fakes for narrow tests and an in-memory Room database for integration behavior:

- Plant create, read, update, and delete.
- Required-name validation and optional-field normalization.
- Favorite updates and persistence.
- Literal, case-insensitive substring search across name and scientific name only, including accented Spanish text.
- Favorites-only filtering and deterministic name ordering.
- Persistence after closing and reopening the database.

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

Phase 1 deletion tests verify Plant-row deletion only. Phase 2 deletion tests verify the notes cascade, primary-photo cleanup, replacement cleanup, failed-write compensation, and startup reconciliation.

### Phase 2 photo-flow tests

Keep platform contracts thin and test storage/repository behavior with temporary app files and fake content resolvers where possible. Instrumented or manual coverage must verify:

- `TakePicture` uses an app-owned `FileProvider` URI without requesting `CAMERA`.
- `PickVisualMedia(ImageOnly)` does not request broad media permission and falls back to the document picker when unavailable.
- Cancellation, no compatible handler, unreadable URI, unwritable destination, and process recreation leave Room and stable files consistent.
- Reconciliation waits for in-flight imports, failed camera completion removes its temporary file, a pending capture blocks reentrant launches, and failed temporary cleanup retains a retryable token.
- Original bytes and the persisted relative path survive restart.

### Later-phase WorkManager and notification tests

Keep date evaluation and occurrence selection outside the Worker so they are unit-testable. Android integration tests cover:

- Unique work configuration
- Enabled and disabled rules
- One notification per occurrence
- Notification channel creation
- PendingIntent destination and plant identifier
- Behavior when notification permission is unavailable

Do not write tests that wait for real wall-clock background execution.

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

Phase 2 Compose coverage adds:

- Primary-photo placeholder and image rendering in list and detail.
- Capture/picker callbacks, replacement/removal actions, disabled repeated actions, and photo-delete confirmation.
- Notes loading, error/retry, empty, content, editor validation, save, edit, delete confirmation, and localized date text.
- Staged photo and favorite rendering before the first save, notes hidden during creation, atomic-save errors, retry, and localized name-only labels.

### Later-phase end-to-end device tests

Later phases should maintain a small smoke suite for:

1. Launch the application.
2. Create a plant.
3. Add or capture its primary photo.
4. Add, edit, and delete a note.
5. Replace and remove the primary photo.
6. Restart the application and verify persistence and cleanup.
7. Record watering, pruning, and fertilizing for today and a prior date; verify each summary, filtered history, and plant-scoped clear action.
8. Continue with interval-reminder checks once those later phases exist.

The current smoke flow covers plant inventory, primary-photo, dated-note, and care-event behavior. Reminder steps remain future Phase 4 coverage.

Camera and photo-picker flows may require targeted manual verification because system UI behavior varies by device. The acceptance target is cancellation, handler unavailability, URI failure, and process recreation; a runtime permission-denial test is intentionally not required for the permissionless contracts.

## Commands

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
- Use original-byte fixtures and assert cleanup/reconciliation by relative path.
- Do not depend on network access.

## Coverage priorities

Highest priority:

- Data loss prevention during the v1-to-v2 migration and explicit v3-to-v4 preservation rules.
- Plant CRUD and persistence across database reopen.
- Search/filter query correctness and literal wildcard handling.
- Plant deletion confirmation and row deletion.
- Room v2-to-v3 and v3-to-v4 migrations, note cascade, original-byte photo persistence, replacement compensation, staged-create compensation, and orphan cleanup.
- Room v4-to-v5 and complete v1-to-v5 migrations, care-event cascade, ordering, scoped history clearing, summaries, and localized UI.

Medium priority:

- ViewModel state transitions
- Search, filters, and favorites
- Form validation
- Localization-sensitive formatting

Later-phase priorities:

- Reminder-rule deletion cleanup.
- Date and schedule calculations for Phase 4 reminders.
- Transactional reminder rescheduling.
- Notification deduplication.

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
