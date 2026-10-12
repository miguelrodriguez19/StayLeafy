# Stay Leafy

Stay Leafy is a personal, offline-first Android application for cataloguing household plants, photos, and observations.

The application is intended for a single user. It stores plant inventory records, one primary photo per plant, dated notes, care history, interval reminder rules, and persistent Notices locally. Best-effort Android notifications are evaluated on-device without introducing a backend. It has no accounts, cloud synchronization, analytics, advertising, or paid services.

## Project status

Phases 1 through 4 and the independent plant-screen correction are implemented. The app provides the complete offline plant inventory, Room schema v6 with dated notes, care events, interval reminder rules, persistent Notices, and one app-managed primary photo per plant. The v3-to-v4 migration removes the redundant common-name fields and discards their stored values. The current model has one `name` field, and search covers `name` and scientific name.

Plant creation, viewing, and editing use one shared screen. A new plant can stage its photo and favorite state before saving; notes are added only after the plant exists. The final Room write is atomic, with file compensation when persistence fails.

Phase 3 adds `CareEvent` rows for watering, pruning, and fertilizing, UTC epoch-millisecond timestamps, optional trimmed legacy notes, stable newest-first ordering, and a cascade from Plant deletion. Plant detail keeps three always-visible care cells: a short press records today, a long press selects another date, and pressing the cell opens a read-only, newest-first history for that type and plant. The history menu can clear that scoped history after confirmation.

Phase 4 adds interval care configuration, transactional next-due updates, Upcoming/Overdue inventory filters, `Plants | Notices` bottom navigation, and approximately daily WorkManager evaluation around 09:00 local time. Notices remain available when Android notification permission is unavailable, and suppressed occurrences are not delivered retroactively.

The project was created from the official Android `architecture-templates` single-module template and remains a personal, single-user application with no account, backend, synchronization, analytics, advertising, or paid-service dependency.

## Implemented capabilities

- Manage one primary photo per plant from permissionless camera capture or the system photo picker.
- Store original selected bytes in app-managed local storage and render the photo in plant list and detail surfaces.
- Replace or remove the primary photo, clean old files, and reconcile unreferenced files after restarts.
- Add, edit, list, and delete independent dated notes in Room schema v4, with a cascade when their plant is deleted.
- Create, view, and edit plants through the shared plant screen, including staged photo and favorite changes with atomic Room persistence and compensating cleanup.
- Record watering, pruning, and fertilizing inline from plant detail in Room schema v5.
- Show each type's latest date and a read-only, deterministic reverse-chronological history scoped to that plant and type.
- Configure interval rules for watering, pruning, and fertilizing atomically in Room schema v6.
- Show next-due state in plant detail and filter the inventory by Upcoming or Overdue care.
- Keep a persistent read/unread Notices inbox with notification-to-Notice and Notice-to-Plant navigation.
- Evaluate due rules with unique daily WorkManager work and deliver at most one Android notification per occurrence.

## Planned capabilities

- Configure annual seasonal windows in Phase 5, such as the ideal pruning season for hydrangeas.
- Continue following the system light or dark theme across future flows.

## Technology

- Kotlin
- Jetpack Compose and Material 3
- Navigation 3
- Room
- Hilt
- Kotlin Coroutines and Flow
- JUnit and Compose UI testing
- AndroidX Activity Result APIs with `TakePicture` and `PickVisualMedia`
- `FileProvider` for app-owned camera capture URIs
- Coil Compose for local image decoding and display
- WorkManager for unique, deferrable, approximately daily local reminder evaluation.

The application uses the package `com.miguelrodriguez19.stayleafy` and remains a single-module Android project until scale provides a concrete reason to split it. The plant and photo flows do not request `CAMERA`, `READ_MEDIA_IMAGES`, or legacy broad storage permissions: capture is delegated through `TakePicture`, and selection uses `PickVisualMedia` with the platform document fallback where required.

## Requirements

- Android Studio Quail 4 (`2026.1.4`) or newer for AGP 9.4 support
- JDK 17
- Android SDK 37

## Build and test

```bash
./gradlew assembleDebug
./gradlew test
./gradlew lint
```

Instrumented tests require a connected device or emulator:

```bash
./gradlew connectedDebugAndroidTest
```

## Documentation

- [Product vision](docs/PRODUCT.md)
- [Requirements](docs/REQUIREMENTS.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Data model](docs/DATA_MODEL.md)
- [Reminder behavior](docs/REMINDERS.md)
- [Roadmap](docs/ROADMAP.md)
- [Testing strategy](docs/TESTING.md)
- [Decision log](docs/DECISIONS.md)

## License

Stay Leafy is licensed under the [Apache License 2.0](LICENSE).
