# Stay Leafy contributor instructions

These instructions apply to the entire repository.

## Product constraints

- Stay Leafy is a personal, single-user Android application.
- The application must remain fully functional offline.
- Do not add authentication, a backend, cloud synchronization, analytics, advertising, or paid services unless the product documentation is explicitly changed first.
- Camera and photo-library access are the only media or sensor capabilities currently required.
- Product behavior and documentation are written in English. User-facing strings must support English and Spanish through Android resources.

## Architecture constraints

- Keep the project single-module until a measured build or ownership problem justifies modularization.
- Preserve the simple `ui` and `data` layer split.
- Compose screens render immutable state and send events upward.
- ViewModels expose lifecycle-aware `StateFlow` UI state and call repositories.
- Repositories are the boundary to Room and other local platform data sources.
- Do not add a domain or use-case layer for one-off delegation. Add a use case only for non-trivial business logic reused by multiple ViewModels or when it materially simplifies a ViewModel.
- Room is the source of truth for plants, notes, care events, and reminder rules.
- Hilt is the dependency injection mechanism.
- Use WorkManager for deferrable reminder evaluation. Do not introduce exact alarms without recording a new decision in `docs/DECISIONS.md`.
- Store app-managed image files outside Room and persist only their stable relative path.

## Code conventions

- Package new code under `com.miguelrodriguez19.stayleafy`.
- Prefer immutable Kotlin models and explicit UI states.
- Use coroutines and Flow for asynchronous and observable data.
- Keep composables stateless where practical and pass state plus event lambdas.
- Use Android string resources for every user-visible string.
- Store timestamps as epoch milliseconds and care due dates as local epoch days unless the data model decision is revised.
- Avoid speculative abstractions and dependencies.

## Verification

Run the narrowest relevant checks while developing, then run the full local suite before handing off a completed change:

```bash
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

Run instrumented tests when a device or emulator is available:

```bash
./gradlew connectedDebugAndroidTest
```

Prefer fakes and in-memory Room databases over broad mocking. Test business rules as pure Kotlin whenever possible.

## Documentation maintenance

- Update `docs/REQUIREMENTS.md` when observable product behavior changes.
- Update `docs/DATA_MODEL.md` with every persisted schema change.
- Update `docs/REMINDERS.md` when scheduling, rescheduling, or notification behavior changes.
- Record durable architectural choices in `docs/DECISIONS.md`.
- Keep `README.md` focused on project orientation and verified setup commands.
- Do not describe planned behavior as implemented behavior.
