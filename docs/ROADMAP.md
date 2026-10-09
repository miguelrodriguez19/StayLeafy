# Roadmap

The roadmap orders work by dependency and user value. It is not a commitment to calendar dates.

## Phase 0 — Foundation

- [x] Select the official Android single-module architecture template.
- [x] Customize the package to `com.miguelrodriguez19.stayleafy`.
- [x] Verify the customized template with local unit tests.
- [x] Rename the remaining `MyApplicationTheme` symbol to `StayLeafyTheme`.
- [x] Add and review project documentation.
- [x] Initialize a clean Git repository after template customization.
- [x] Verify `assembleDebug`, unit tests, lint, and one device launch.
- [x] Establish English and Spanish resource files.

## Phase 1 — Plant inventory

- [ ] Replace the template Plant sample with the real Plant schema.
- [ ] Implement Room migration policy and schema tests.
- [ ] Build plant list, empty state, and loading/error states.
- [ ] Build create and edit flows with validation.
- [ ] Build plant detail.
- [ ] Implement deletion confirmation and cascade cleanup.
- [ ] Add favorites, search, and basic filters.
- [ ] Add light and dark previews.

Exit condition: plants persist across restarts and the main CRUD flow is covered by focused tests.

## Phase 2 — Primary photo and dated notes

- [ ] Add camera capture with app-managed files.
- [ ] Add system photo picker selection.
- [ ] Support replacing and removing the primary photo.
- [ ] Clean up orphaned image files.
- [ ] Add, edit, list, and delete dated notes.
- [ ] Test permission denial and process recreation around photo selection.

Exit condition: one stable primary image and multiple independent notes can be managed fully offline.

## Phase 3 — Care history

- [ ] Add watering, pruning, and fertilizing event models.
- [ ] Add quick actions from plant detail.
- [ ] Display reverse-chronological care history.
- [ ] Support correcting and deleting care events.
- [ ] Add tests around event ordering and transactional updates.

Exit condition: the user can reliably answer when each care action last occurred.

## Phase 4 — Interval reminders

- [ ] Add interval reminder rules.
- [ ] Calculate the next due date after completion.
- [ ] Display upcoming and overdue states.
- [ ] Add WorkManager reminder evaluation.
- [ ] Add notification permission education.
- [ ] Deep link notifications to plant detail.
- [ ] Add deduplication and scheduler tests.

Exit condition: watering every `N` days works end to end without duplicate notifications.

## Phase 5 — Seasonal care

- [ ] Add annual seasonal start and optional end dates.
- [ ] Support seasons crossing the year boundary.
- [ ] Mark a season occurrence satisfied by matching care.
- [ ] Deliver a single notification at season start.
- [ ] Add seasonal rule editor and tests.

Exit condition: rules such as hydrangea pruning season recur correctly each year.

## Phase 6 — Personal-use hardening

- [ ] Complete Spanish localization.
- [ ] Review accessibility basics despite the personal-use scope.
- [ ] Add export and restore design.
- [ ] Add database migration tests before schema version changes.
- [ ] Review battery behavior and notification reliability on the owner's device.
- [ ] Produce a signed personal release build.

## Later possibilities

- Multiple photos per plant
- User-defined care types
- Plant groups or rooms
- Local export and restore including images
- Calendar overview
- Home-screen widget
- Optional per-app language selector

These items require explicit prioritization before implementation.
