# Stay Leafy

Stay Leafy is a personal, offline-first Android application for cataloguing household plants, photos, and observations.

The application is intended for a single user. It stores plant inventory records, one primary photo per plant, and dated notes locally. Care history and local reminders for watering, pruning, and fertilizing are planned for later phases. It has no accounts, backend, cloud synchronization, analytics, advertising, or paid services.

## Project status

Phases 1 and 2 are implemented. The app provides the complete offline plant inventory, Room schema v3 with dated notes, and one app-managed primary photo per plant. It includes create/edit/list/detail/delete flows, favorites, literal substring search, a favorites-only filter, English and Spanish resources, and light and dark theme support.

The project was created from the official Android `architecture-templates` single-module template and remains a personal, single-user application with no account, backend, synchronization, analytics, advertising, or paid-service dependency.

## Implemented capabilities

- Manage one primary photo per plant from permissionless camera capture or the system photo picker.
- Store original selected bytes in app-managed local storage and render the photo in plant list and detail surfaces.
- Replace or remove the primary photo, clean old files, and reconcile unreferenced files after restarts.
- Add, edit, list, and delete independent dated notes in Room schema v3, with a cascade when their plant is deleted.

## Planned capabilities

- Record watering, pruning, and fertilizing events.
- Configure interval-based care, such as watering every 15 days.
- Recalculate the next due date when care is marked complete.
- Configure annual seasonal windows, such as the ideal pruning season for hydrangeas.
- Deliver local notifications without a remote service.
- Extend the implemented offline English and Spanish experience to later care and reminder flows.
- Continue following the system light or dark theme across later flows.

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

Planned technology:

- WorkManager for deferrable local reminder checks once reminder rules are implemented.

The application uses the package `com.miguelrodriguez19.stayleafy` and remains a single-module Android project until scale provides a concrete reason to split it. Phase 2 does not request `CAMERA`, `READ_MEDIA_IMAGES`, or legacy broad storage permissions: capture is delegated through `TakePicture`, and selection uses `PickVisualMedia` with the platform document fallback where required.

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
