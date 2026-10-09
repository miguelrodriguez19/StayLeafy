# Stay Leafy

Stay Leafy is a personal, offline-first Android application for cataloguing household plants and keeping their care on schedule.

The application is intended for a single user. It stores plant records, one primary photo per plant, dated notes, care history, and local reminders for watering, pruning, and fertilizing. It has no accounts, backend, cloud synchronization, analytics, advertising, or paid services.

## Project status

Stay Leafy is in its foundation stage. The project was created from the official Android `architecture-templates` single-module template and currently contains the template's sample Plant flow. Product-specific data models and screens are described in the documentation but are not yet fully implemented.

## Planned capabilities

- Create, edit, view, search, filter, favorite, and delete plants.
- Attach one primary photo from the camera or photo library.
- Add independent, dated notes to each plant.
- Record watering, pruning, and fertilizing events.
- Configure interval-based care, such as watering every 15 days.
- Recalculate the next due date when care is marked complete.
- Configure annual seasonal windows, such as the ideal pruning season for hydrangeas.
- Deliver local notifications without a remote service.
- Work fully offline in English and Spanish.
- Follow the system light or dark theme.

## Technology

- Kotlin
- Jetpack Compose and Material 3
- Navigation 3
- Room
- Hilt
- Kotlin Coroutines and Flow
- WorkManager for deferrable local reminder checks
- JUnit and Compose UI testing

The application uses the package `com.miguelrodriguez19.stayleafy` and remains a single-module Android project until scale provides a concrete reason to split it.

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
