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

- [x] Replace the template Plant sample with the real Room Plant schema v2.
- [x] Implement the non-destructive Room migration from schema 1 to schema 2, preserving legacy rows.
- [x] Build the plant list, empty state, and loading/error states.
- [x] Build create and edit flows with validation.
- [x] Build plant detail.
- [x] Implement deletion confirmation and deletion of the Plant row.
- [x] Add favorites, literal substring search, and the favorites-only filter.
- [x] Add English and Spanish resources.
- [x] Add light and dark previews.

Phase 1 boundary: child tables and app-managed photo files are owned by later phases. Phase 2 owns notes and primary-photo cleanup, care-event cleanup is owned by Phase 3, and reminder-rule cleanup plus the care-status filter are owned by Phase 4.

Exit condition: Plant records persist across restarts, the main CRUD flow is covered by focused tests, and the v1-to-v2 migration preserves existing Plant rows.

## Phase 2 — Primary photo and dated notes

- [x] Add Room schema v3 with `PlantNote`, the `2→3` migration, and an indexed Plant foreign key with delete cascade.
- [x] Add camera capture through permissionless `TakePicture` and an app-owned `FileProvider` URI.
- [x] Add `PickVisualMedia(ImageOnly)` with the platform document fallback and no broad media permission.
- [x] Store original image bytes under app-managed `files/plant_photos/` paths and persist only the relative path in Room.
- [x] Display the primary photo or a deterministic placeholder in the plant list and detail screens.
- [x] Support replacing and removing the primary photo with compensation for failed Room updates.
- [x] Clean up the old photo on replacement and the plant photo on deletion; reconcile unreferenced stable files at startup.
- [x] Add, edit, list, and delete dated notes in newest-first order.
- [x] Add the notes cascade when a Plant is deleted.
- [x] Test cancellation, unavailable capture/picker handlers, URI read/write failures, and process recreation around photo selection.

Exit condition met: one stable primary image and multiple independent notes can be managed fully offline, with local, instrumented, and migration tests covering the Phase 2 boundaries.

Phase 2 boundary: it intentionally excludes multiple photos, image editing or compression, note attachments, care history, reminder rules, WorkManager, network, and authentication. Phase 3 owns care events and their cascade; Phase 4 owns reminder rules, WorkManager, notifications, and the care-status filter.

## Independent correction — Plant identity and unified plant screen

- [x] Raise Room from schema v3 to schema v4 while preserving plants, notes, photos, favorites, and timestamps.
- [x] Replace the redundant display/common-name pair with one required `name` field and one normalized name-search projection.
- [x] Discard stored common-name values during the explicit `3→4` migration; scientific names and all other supported data remain preserved.
- [x] Search literally and case-insensitively across `name` and scientific name only.
- [x] Use one shared create/view/edit plant screen rather than a separate creation form and detail form.
- [x] Stage a new plant's photo and favorite state before saving, keep notes out of creation, and use compensating file cleanup on failure.

Exit condition met: the current plant model and creation flow are coherent with the detail experience, and the v3-to-v4 migration is explicit and tested.

This correction is a separate completed change before Phase 3. It does not add care history, reminders, WorkManager, or the care-status filter.

## Phase 3 — Care history

- [x] Raise Room from schema v4 to schema v5 with an explicit `4→5` migration and preserve the complete `1→2→3→4→5` chain.
- [x] Add `CareEvent` rows for watering, pruning, and fertilizing with UUID, Plant foreign key, completion timestamp, optional note, and audit timestamps.
- [x] Normalize optional notes to trimmed `NULL` values, reject future completion timestamps using the injected clock, and keep `createdAt` stable when an event is corrected.
- [x] Add indexes on `(plantId, performedAt)` and `(plantId, careType, performedAt)` and read events by `performedAt DESC, id DESC`.
- [x] Add quick actions from plant detail that open a date-and-time editor prefilled with the selected care type and the current time.
- [x] Display per-type last-care summaries and the complete reverse-chronological care history.
- [x] Support correcting care type, date, time, and optional note, plus deletion with confirmation and repeated-action protection.
- [x] Add the `ON DELETE CASCADE` from Plant to care events and verify it together with plant deletion.
- [x] Add localized English and Spanish UI states, editor/confirmation screens, previews, and loading/error/empty/content states.
- [x] Add migration, Room, repository, ViewModel, Compose, navigation, and connected-flow tests.

Phase 3 status: complete. Room v5, the full migration chain, care history, localized UI, and local and connected-device verification are implemented.

Exit condition: the user can reliably answer when each care action last occurred, correct an event's date and time, and delete it without leaving child rows.

## Phase 4 — Interval reminders

Phase 4 remains planned. It owns reminder rules, next-due calculations, the care-status filter, WorkManager evaluation, notifications, and reminder-rule cleanup; Phase 3 does not schedule reminders or recompute due dates.

- [ ] Add interval reminder rules.
- [ ] Calculate the next due date after completion.
- [ ] Display upcoming and overdue states.
- [ ] Add the care-status filter to the plant inventory.
- [ ] Add the reminder-rule cascade when a Plant is deleted.
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
