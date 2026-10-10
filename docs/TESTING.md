# Testing strategy

## Goals

Testing should catch regressions in Phase 1 persistence and principal plant-management flows without making a personal project expensive to change. Later phases extend the same strategy to photos, notes, care history, and reminders.

The strategy favors deterministic unit tests and a small number of high-value Android and Compose tests.

## Test layers

### Pure unit tests

Use plain JVM tests for Phase 1 logic without Android dependencies:

- Plant draft normalization: trimming the required display name and converting blank optional fields to `null`.
- Required display-name validation, including whitespace-only input.
- Fixed UUID and clock behavior for deterministic create/update tests.
- Search/filter state transformations, including loading, empty, filtered-empty, content, and error states.
- ViewModels using fake repositories and test dispatchers.

Phase 1 does not test interval, seasonal, notification, or WorkManager behavior; those belong to later phases.

These tests should be fast and make up most of the suite.

### Repository tests

Test repository behavior with fakes for narrow tests and an in-memory Room database for integration behavior:

- Plant create, read, update, and delete.
- Required-name validation and optional-field normalization.
- Favorite updates and persistence.
- Literal, case-insensitive substring search across display, common, and scientific names, including accented Spanish text.
- Favorites-only filtering and deterministic display-name ordering.
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

Phase 1 deletion tests verify Plant-row deletion only. Child-table cascades and photo cleanup are added to the test suite when their owning phases introduce those data sources.

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
- Plant form validation, create callbacks, and edit callbacks.
- Plant detail rendering and not-found behavior.
- Delete confirmation, cancellation, and confirmation callbacks.
- Favorite toggles and favorites-only filtering.
- Literal search input and clear-filter behavior.
- English and Spanish key inventory flows.
- Light and dark preview coverage for list, empty, error, detail, form, and confirmation states.

Notes, care completion, reminder editor, and next-due-date assertions belong to their later phases.

### Later-phase end-to-end device tests

Later phases should maintain a small smoke suite for:

1. Launch the application.
2. Create a plant.
3. Add or capture its primary photo.
4. Add a note.
5. Configure an interval reminder.
6. Record care and verify the next due date.
7. Restart the application and verify persistence.

The Phase 1 smoke flow is limited to creating, viewing, editing, favoriting, searching, deleting, and restarting with a Plant record. Photo, note, care, and reminder steps are future coverage.

Camera and photo-picker flows may require targeted manual verification because system UI behavior varies by device.

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
- Do not depend on network access.

## Coverage priorities

Highest priority:

- Data loss prevention during the v1-to-v2 migration.
- Plant CRUD and persistence across database reopen.
- Search/filter query correctness and literal wildcard handling.
- Plant deletion confirmation and row deletion.

Medium priority:

- ViewModel state transitions
- Search, filters, and favorites
- Form validation
- Localization-sensitive formatting

Later-phase priorities:

- Photo and child-record deletion cleanup.
- Date and schedule calculations.
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
