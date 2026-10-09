# Architecture

## Goals

The architecture must keep Stay Leafy understandable for one developer, make persisted behavior testable, and allow the product to grow without introducing enterprise-scale ceremony.

## Baseline

The project is based on the `base` branch of the official Android `architecture-templates` repository. It is a single-module application using:

- Kotlin and Kotlin DSL
- Jetpack Compose with Material 3
- Navigation 3
- Room with KSP
- Hilt
- Coroutines and Flow
- JUnit and Compose testing

Current project coordinates:

- Root project: `StayLeafy`
- Application ID and namespace: `com.miguelrodriguez19.stayleafy`
- Module: `:app`
- Minimum SDK: 24
- Target and compile SDK: 37
- JDK: 17

## Architectural style

Stay Leafy uses a pragmatic layered architecture with unidirectional UI data flow.

```text
Compose UI
    ↓ events         ↑ immutable UI state
ViewModel / state holder
    ↓ commands       ↑ Flow/domain models
Repository
    ↓
Room DAO / local platform data source
```

The data layer owns persisted application data and the business rules directly related to changing that data. ViewModels coordinate screen behavior and transform repository streams into UI state. Compose screens do not access Room, files, or WorkManager directly.

A separate domain layer is intentionally omitted. Focused use-case classes may be introduced when logic is shared by multiple ViewModels or becomes difficult to test in its current owner.

## Planned package structure

```text
com.miguelrodriguez19.stayleafy/
├── StayLeafy.kt
├── data/
│   ├── local/
│   │   ├── database/
│   │   │   ├── dao/
│   │   │   ├── entity/
│   │   │   ├── converter/
│   │   │   └── AppDatabase.kt
│   │   ├── photo/
│   │   └── reminder/
│   ├── repository/
│   └── di/
├── model/
├── reminder/
│   ├── ReminderWorker.kt
│   ├── ReminderScheduler.kt
│   └── NotificationFactory.kt
└── ui/
    ├── navigation/
    ├── plants/
    │   ├── list/
    │   ├── detail/
    │   └── edit/
    ├── notes/
    ├── care/
    ├── settings/
    ├── components/
    └── theme/
```

The structure may be introduced incrementally. Packages should represent real responsibilities, not anticipated scale.

## State management

- A route obtains its ViewModel and collects state with lifecycle awareness.
- A screen composable receives immutable state and event lambdas.
- ViewModels expose `StateFlow<UiState>`.
- Durable outcomes are written to Room and observed back through Flow.
- One-off UI actions are minimized. Navigation and permission launchers stay at the UI boundary.
- Error states contain actionable, localizable information rather than infrastructure exceptions.

## Persistence

Room is the source of truth for plants, notes, care events, and reminder rules. Repositories own transactional updates, including care completion plus reminder rescheduling.

Images are stored as app-managed files. Room stores only a stable relative path. This keeps large binary content out of SQLite and makes later export behavior explicit.

Schema export remains enabled through KSP. Once real user data exists, destructive migration fallback must not be used.

## Reminder scheduling

Reminder rules and their next due occurrence live in Room. WorkManager performs deferrable local checks and notification delivery. Scheduling details are defined in [REMINDERS.md](REMINDERS.md).

The scheduler does not own business truth: it reads persisted rule state and can be recreated safely. Exact alarms are not part of the initial architecture.

## Dependency injection

Hilt provides application-scoped infrastructure and repository bindings. Constructor injection is preferred. Hilt modules are used only where constructor injection cannot express the binding, such as Room database creation or interface-to-implementation bindings.

## Navigation

Navigation 3 provides type-safe destination keys. Initial destinations are expected to include:

- Plant list
- Plant detail
- Plant editor
- Note editor
- Reminder editor
- Settings

Notification deep links must resolve to the related plant detail and remain valid when the process is recreated.

## Localization and themes

- English strings live in `res/values/strings.xml`.
- Spanish strings live in `res/values-es/strings.xml`.
- User-visible text does not live in Kotlin source.
- The application follows the system theme initially.
- Compose previews should cover light and dark appearances for important reusable components.

## Testing seams

- Repository interfaces allow fakes in ViewModel and navigation tests.
- Room DAOs are verified with in-memory database tests.
- Date and reminder calculations are implemented as deterministic functions where possible.
- WorkManager integration is kept behind `ReminderScheduler` so core behavior can be tested without waiting for actual background execution.

## Deliberate exclusions

- Multi-module build logic
- Networking clients
- Remote data sources
- Authentication
- Domain-object duplication across every layer
- Generic result wrappers without a demonstrated need
- Exact alarm permissions
