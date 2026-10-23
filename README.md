# Stay Leafy

Stay Leafy is a personal, offline-first Android application for cataloguing household plants, photos, and observations.

The application is intended for a single user. It stores plant inventory records, one primary photo per plant, dated notes, care history, interval reminder rules, and persistent Notices locally. Best-effort Android notifications are evaluated on-device without introducing a backend. It has no accounts, cloud synchronization, analytics, advertising, or paid services.

## Project status

Phases 1 through 7 and the independent plant-screen correction are implemented. The app provides the complete offline plant inventory, Room schema v7 with dated notes, care events, interval and annual seasonal reminder rules, persistent Notices, one app-managed primary photo per plant, square photo framing, personalization settings, and the Stay Leafy light/dark visual identity. The current plant model has one `name` field, and search covers `name` and scientific name.

Plant creation, viewing, and editing use one shared screen. A new plant can stage its photo and favorite state before saving; notes are added only after the plant exists. The final Room write is atomic, with file compensation when persistence fails.

Phase 3 adds `CareEvent` rows for watering, pruning, and fertilizing, UTC epoch-millisecond timestamps, optional trimmed legacy notes, stable newest-first ordering, and a cascade from Plant deletion. Plant detail keeps three always-visible care cells: a short press records today, a long press selects another date, and pressing the cell opens a read-only, newest-first history for that type and plant. The history menu can clear that scoped history after confirmation.

Phase 4 adds interval care configuration, transactional next-due updates, Upcoming/Overdue inventory filters, `Plants | Notices` bottom navigation, and approximately daily WorkManager evaluation. Phase 7 makes the preferred local time configurable, defaulting to 09:00, while keeping delivery approximate. Notices remain available when Android notification permission is unavailable, and suppressed occurrences are not delivered retroactively.

Phase 5 adds mutually exclusive once-a-year schedules selected through one day-or-period calendar, with optional inclusive end dates, cross-year windows, matching-care satisfaction, and one deduplicated Notice/notification per occurrence. Annual state appears in Plant detail while inventory due filters remain interval-only.

The project was created from the official Android `architecture-templates` single-module template and remains a personal, single-user application with no account, backend, synchronization, analytics, advertising, or paid-service dependency.

## Implemented capabilities

- Manage one primary photo per plant from permissionless camera capture or the system photo picker.
- Crop every newly added or replaced primary photo interactively to 1:1, store a bounded square JPEG in app-managed local storage, and leave previously stored files untouched.
- Replace or remove the primary photo, clean old files, and reconcile unreferenced files after restarts.
- Add, edit, list, and delete independent dated notes in Room schema v4, with a cascade when their plant is deleted.
- Create, view, and edit plants through the shared plant screen, including staged photo and favorite changes with atomic Room persistence and compensating cleanup.
- Record watering, pruning, and fertilizing inline from plant detail in Room schema v5.
- Show each type's latest date and a read-only, deterministic reverse-chronological history scoped to that plant and type.
- Configure interval rules for watering, pruning, and fertilizing atomically in Room schema v6.
- Configure annual seasonal windows in Room schema v7 while retaining inactive interval settings.
- Show upcoming, active, and satisfied seasonal state and deliver one localized Notice per unsatisfied occurrence.
- Show next-due state in plant detail and filter the inventory by Upcoming or Overdue care.
- Keep a persistent read/unread Notices inbox with notification-to-Notice and Notice-to-Plant navigation.
- Evaluate due rules with unique daily WorkManager work and deliver at most one Android notification per occurrence.
- Export an unencrypted local ZIP and restore the complete collection after validation, preview, and explicit replace-all confirmation from Settings → Backup. See [backup and recovery](docs/BACKUP.md).

Phase 6 adds the fixed botanical palette, local Lora display typography, deterministic vector iconography, accessible care-status colors, cactus launcher artwork, and reference-led compact screen compositions while preserving every existing flow and its offline behavior.

## Personalization

- Settings offers System/Light/Dark theme, System/English/Spanish app language, a configurable approximate reminder time (09:00 default), permission capabilities, and About/privacy information.
- Preferences apply immediately, persist locally through Preferences DataStore, and do not change the Room schema.

Phase 8A workflows, signing setup, and remote release acceptance are complete, as confirmed by the owner. Phase 8 adds manual local backup/recovery and localization/accessibility regression checks without changing Room schema v7. Phase 8 remains open for owner-operated accessibility, restore, and physical-device reminder/battery acceptance. Its completion leads to a separate `v1.0.0` MVP release PR; this implementation candidate is `0.11.0`/`27`. Later work remains in the [roadmap](docs/ROADMAP.md).

Android Auto Backup and system device transfer are disabled. Keep manual ZIP backups outside app storage; the archives are not encrypted.

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
bash scripts/tests/run.sh
./gradlew test lint assembleDebug
```

The release runbook covers the one-time signing-key ceremony, GitHub `release` environment setup, recovery dispatch, and local APK signature/checksum verification: [release runbook](docs/RELEASING.md).

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
- [Release runbook](docs/RELEASING.md)
- [Local backup and recovery](docs/BACKUP.md)

## License

Stay Leafy is licensed under the [Apache License 2.0](LICENSE).
