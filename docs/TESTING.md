# Testing strategy

## Goals

Testing should catch regressions in persistence, date calculation, reminder behavior, and principal user flows without making a personal project expensive to change.

The strategy favors deterministic unit tests and a small number of high-value Android and Compose tests.

## Test layers

### Pure unit tests

Use plain JVM tests for logic without Android dependencies:

- Interval validation and next-due-date calculation
- Leap years, month boundaries, and timezone conversion boundaries
- Seasonal-window activation, including cross-year windows
- Notification occurrence-key generation
- UI state transformations
- ViewModels using fake repositories and test dispatchers

These tests should be fast and make up most of the suite.

### Repository tests

Test repository behavior with fakes for narrow tests and an in-memory Room database for integration behavior:

- Plant CRUD
- Cascade deletion
- Note ordering
- Care-event ordering
- Transactional care completion and reminder recalculation
- Recalculation after editing or deleting the latest event
- Search and filter queries

Avoid mocking Room DAOs when the SQL query itself is the behavior under test.

### WorkManager and notification tests

Keep date evaluation and occurrence selection outside the Worker so they are unit-testable. Android integration tests cover:

- Unique work configuration
- Enabled and disabled rules
- One notification per occurrence
- Notification channel creation
- PendingIntent destination and plant identifier
- Behavior when notification permission is unavailable

Do not write tests that wait for real wall-clock background execution.

### Compose tests

Compose tests use immutable screen state and event lambdas where possible. Cover:

- Empty and populated plant lists
- Plant form validation
- Plant creation and edit callbacks
- Favorite and filter behavior
- Dated-note ordering
- Care completion confirmation and displayed next date
- Reminder editor validation
- English and Spanish key flows

### End-to-end device tests

Maintain a small smoke suite:

1. Launch the application.
2. Create a plant.
3. Add or capture its primary photo.
4. Add a note.
5. Configure an interval reminder.
6. Record care and verify the next due date.
7. Restart the application and verify persistence.

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

- Data loss prevention
- Date and schedule calculations
- Transactional reminder rescheduling
- Notification deduplication
- Plant deletion cleanup

Medium priority:

- ViewModel state transitions
- Search, filters, and favorites
- Form validation
- Localization-sensitive formatting

Lower priority:

- Trivial data classes
- Framework wiring already exercised by higher-value tests
- Purely decorative composables

No numeric coverage target is required initially. New non-trivial behavior should include tests proportional to its failure risk.

## Definition of done

A change is complete when:

- Its acceptance behavior is implemented.
- Relevant unit or integration tests exist and pass.
- Existing local tests pass.
- The debug build succeeds.
- Lint has no newly introduced actionable errors.
- Relevant product, data, reminder, or decision documentation is updated.
