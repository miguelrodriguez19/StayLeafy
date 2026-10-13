# Roadmap

The roadmap orders work by dependency and user value. It is not a commitment to calendar dates.

## Current status at a glance

- [x] Phase 0 — Foundation
- [x] Phase 1 — Plant inventory
- [x] Phase 2 — Primary photo and dated notes
- [x] Independent correction — Plant identity and unified plant screen
- [x] Phase 3 — Care history
- [x] Phase 4 — Interval reminders
- [x] Phase 5 — Seasonal care
- [ ] Phase 6 — Personal-use hardening

Current milestone: Phase 5 is complete. Phase 6 personal-use hardening is next.

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
- [x] Keep watering, pruning, and fertilizing as three always-visible cells in plant detail, each showing only its latest date.
- [x] Record today with a short press and choose another completion date with a long press, with repeated-action protection.
- [x] Open a read-only bottom sheet for the selected care type, ordered newest first, and allow clearing only that type's history for the current plant after confirmation.
- [x] Add the `ON DELETE CASCADE` from Plant to care events and verify it together with plant deletion.
- [x] Add localized English and Spanish inline UI, date picker, history sheet, confirmation, previews, and loading/error/empty/content states.
- [x] Add migration, Room, repository, ViewModel, Compose, navigation, and connected-flow tests.

Phase 3 status: complete. Room v5, the full migration chain, care history, localized UI, and local and connected-device verification are implemented.

Exit condition: the user can reliably answer when each care action last occurred, record today or a forgotten date, inspect one type's history, and clear that history without affecting another plant or care type.

## Phase 4 — Interval reminders

Phase 4 is complete. It owns interval rules, next-due calculations, care-status filtering, a persistent in-app Notices inbox, bottom navigation, WorkManager evaluation, system notifications, and reminder/notice cleanup. Phase 3 does not schedule reminders or recompute due dates.

- [x] Add Room schema v6 with interval reminder rules and persistent read/unread Notices, preserving the complete non-destructive migration chain.
- [x] Add a dedicated care-configuration destination with watering, pruning, and fertilizing cards plus one atomic Save action.
- [x] Require a positive interval and an explicit source: latest matching care or a directly selected first due date.
- [x] Recalculate the next due date transactionally after matching care without allowing an older retroactive event to move it backwards.
- [x] Keep the current due date when the plant-scoped care history is cleared.
- [x] Display next-due, upcoming, and overdue states in plant detail and a compact most-urgent summary in inventory rows.
- [x] Add independent, combinable Upcoming and Overdue inventory filters; Upcoming covers today through seven days ahead and both selected filters use union semantics.
- [x] Add the reminder-rule and notice cascades when a Plant is deleted.
- [x] Add `Plants | Notices` bottom navigation, preserving independent root state and showing the unread count on Notices.
- [x] Add the Notices list, Notice detail, read-on-open behavior, Mark all as read, and navigation from a notice to its plant.
- [x] Add unique periodic WorkManager evaluation approximately daily around 09:00 local time, with one persistent Notice and at most one system notification per occurrence.
- [x] Add contextual Android notification-permission education after the first active rule is saved. Denial keeps rules and Notices active, and granting permission later does not deliver previously suppressed occurrences.
- [x] Route notification taps through the Notices root to the matching Notice detail.
- [x] Add migration, date calculation, transactional rescheduling, deduplication, permission, inbox, scheduler, navigation, localization, and connected-device checks.
- [x] Complete `./gradlew test lint assembleDebug` and `./gradlew connectedDebugAndroidTest`.

Exit condition met: interval care works fully offline end to end; due occurrences create exactly one persistent Notice, never duplicate a system notification, expose correct read state and filters, and pass the local and connected-emulator verification gates.

## Phase 5 — Seasonal care

Phase 5 extends the existing offline reminder system with mutually exclusive annual seasonal schedules while retaining interval configuration for later reuse.

- [x] Add Room schema v7 with annual seasonal start and optional end dates, preserving the complete migration chain.
- [x] Support inclusive single-day, same-year, and cross-year seasons, with February 29 clamped to February 28 in non-leap years.
- [x] Keep interval and seasonal schedules mutually exclusive per Plant and care type while retaining the inactive configuration.
- [x] Mark a season occurrence satisfied by matching care recorded inside its window.
- [x] Deliver one persistent Notice and at most one localized system notification per seasonal occurrence, including late evaluation inside the active window.
- [x] Show upcoming, active, and satisfied seasonal state in Plant detail without changing the interval-only inventory filters.
- [x] Add the seasonal rule editor, English/Spanish resources, deterministic previews, migration/unit/Room/ViewModel/Compose tests, and connected-device verification.

Exit condition met: annual rules such as a hydrangea pruning season recur correctly, deduplicate by occurrence, and are satisfied only by matching care inside the active window.

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
