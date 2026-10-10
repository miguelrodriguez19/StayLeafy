# Product requirements

## Terminology

- **Plant** — A household plant owned by the user.
- **Care event** — A completed watering, pruning, or fertilizing action.
- **Interval rule** — A care rule due every configured number of days after completion.
- **Seasonal rule** — An annual care window defined by a start month/day and an optional end month/day.
- **Due date** — The local calendar date on which interval-based care becomes due.

## Functional requirements

Phase delivery note: Phase 1 implements the Plant inventory. Phase 2 implements notes plus one app-managed primary photo. The independent correction pass raises Room to schema v4, removes the redundant common-name field, and unifies plant creation, viewing, and editing. Phase 3 implements Room v5 care events, their history, UI, tests, and cascade. Reminder rules, due-date calculations, notifications, and care-status filtering remain planned for Phase 4.

### Plant inventory

- **FR-PLANT-001** — The user can create a plant.
- **FR-PLANT-002** — A plant requires a non-blank name.
- **FR-PLANT-003** — The user can optionally record scientific name, room or location, acquisition date, and general information.
- **FR-PLANT-004** — The user can view a list of all plants.
- **FR-PLANT-005** — The user can open a plant detail screen.
- **FR-PLANT-006** — The user can edit plant information.
- **FR-PLANT-007** — The user can delete a plant after confirmation.
- **FR-PLANT-008** — Deleting a plant ultimately removes its notes, care events, reminder rules, and app-managed photo. Delivery is incremental: Phase 1 removes the Plant row, Phase 2 adds photo cleanup and the notes cascade, Phase 3 adds the care-event cascade, and Phase 4 adds the reminder-rule cascade.
- **FR-PLANT-009** — The user can mark or unmark a plant as a favorite.
- **FR-PLANT-010** — The user can search plants by name or scientific name using literal, case-insensitive substring matching.
- **FR-PLANT-011** — The user can filter plants by favorites and care status. Phase 1 implements the favorites-only filter; the care-status filter is delivered with care and reminder data in Phase 4.
- **FR-PLANT-012** — Plant creation, viewing, and editing use one shared screen with the same plant information sections.
- **FR-PLANT-013** — Before a new plant is saved, the user can stage its favorite state and one primary photo. Notes are available only after the Plant row exists. Saving uses compensating cleanup if file or database persistence fails.
- **FR-PLANT-014** — The software keyboard advances through the editable plant text fields in form order and finishes from the final field.
- **FR-PLANT-015** — Opening an empty acquisition-date picker selects the device's current local date by default; an existing acquisition date remains selected while editing.

### Primary photo

- **FR-PHOTO-001** — Each plant supports at most one primary photo in the initial product.
- **FR-PHOTO-002** — The user can capture the photo with the camera through `ActivityResultContracts.TakePicture` and an app-owned `FileProvider` URI. The app does not request the `CAMERA` runtime permission for this external capture flow.
- **FR-PHOTO-003** — The user can select an image with `PickVisualMedia(ImageOnly)`, falling back to the platform document picker when necessary. The app does not request broad media-library permissions.
- **FR-PHOTO-004** — The selected image is copied byte-for-byte into app-managed local storage under a generated stable relative path; Room stores only that relative path.
- **FR-PHOTO-005** — The user can replace or remove the photo.
- **FR-PHOTO-006** — The app does not request broad media-library access when the system photo picker is available.
- **FR-PHOTO-007** — The primary photo is rendered in the plant list and detail screens, with a deterministic placeholder when absent or unavailable.
- **FR-PHOTO-008** — Replacing a photo removes the previous stable file after the new Room reference is committed; failed updates remove the new file instead.
- **FR-PHOTO-009** — Deleting a plant removes its primary photo after the database operation, and startup reconciliation removes stable files no longer referenced by Room.

### Dated notes

- **FR-NOTE-001** — The user can add multiple independent notes to a plant.
- **FR-NOTE-002** — Each note records its creation date and time.
- **FR-NOTE-003** — The user can edit or delete a note.
- **FR-NOTE-004** — Notes are displayed newest first.
- **FR-NOTE-005** — Notes are stored as independent rows with UUID, Plant foreign key, body, creation timestamp, and update timestamp; they remain present after the schema v3-to-v4 migration, and deleting a plant cascades to its notes.
- **FR-NOTE-006** — Note bodies are trimmed and must remain non-blank. Creation timestamps remain stable when a note is edited.

### Care history

- **FR-CARE-001** — The supported initial care types are watering, pruning, and fertilizing.
- **FR-CARE-002** — The user can record a completed care event for a plant.
- **FR-CARE-003** — A care event records the care type, completion time, and an optional note.
- **FR-CARE-004** — The user can review a plant's care history in reverse chronological order.
- **FR-CARE-005** — The user can correct or delete an incorrectly recorded event.
- **FR-CARE-006** — Plant-detail quick actions for watering, pruning, and fertilizing open a prefilled editor with the selected type and the current local date and time; the action is not persisted until the user saves.
- **FR-CARE-007** — The user can correct both the local date and time of a care event. The repository rejects a completion timestamp later than the injected current time.
- **FR-CARE-008** — Plant detail shows the latest recorded event for each supported care type and the complete reverse-chronological history.
- **FR-CARE-009** — Care-event notes are optional, trimmed, and stored as `NULL` when blank. Supported care types are limited to watering, pruning, and fertilizing.

### Interval reminders

- **FR-INTERVAL-001** — The user can configure care to recur every `N` days, where `N` is a positive integer.
- **FR-INTERVAL-002** — An interval rule belongs to one plant and one care type.
- **FR-INTERVAL-003** — Completing matching care recalculates the next due date as the completion date plus `N` days.
- **FR-INTERVAL-004** — Example: if `Ficus Living Room` is configured for watering every 15 days and is watered on October 10, its next watering due date is October 25.
- **FR-INTERVAL-005** — Changing an interval recalculates the next due date from the latest matching care event, or from a user-selected starting date when no event exists.
- **FR-INTERVAL-006** — Disabling a rule prevents future notifications without deleting its care history.

### Seasonal reminders

- **FR-SEASON-001** — The user can configure an annually recurring care season.
- **FR-SEASON-002** — A seasonal rule has a start month/day and may have an end month/day.
- **FR-SEASON-003** — A seasonal rule belongs to one plant and one care type.
- **FR-SEASON-004** — The app notifies the user when the seasonal window begins.
- **FR-SEASON-005** — Example: a hydrangea pruning rule can announce the beginning of its ideal pruning season.
- **FR-SEASON-006** — Completing matching care within the active window suppresses further notifications for that occurrence of the season.
- **FR-SEASON-007** — A disabled seasonal rule produces no notification.

### Notifications

- **FR-NOTIFICATION-001** — Notifications are generated locally; no push service is used.
- **FR-NOTIFICATION-002** — Android notification permission is requested contextually on versions that require it.
- **FR-NOTIFICATION-003** — A notification identifies the plant and the care action.
- **FR-NOTIFICATION-004** — Tapping a notification opens the related plant detail.
- **FR-NOTIFICATION-005** — The scheduler avoids duplicate notifications for the same rule occurrence.
- **FR-NOTIFICATION-006** — Reminders survive normal application restarts and device reboots through WorkManager persistence.
- **FR-NOTIFICATION-007** — The MVP does not promise an exact clock time for delivery.

### Preferences and localization

- **FR-PREF-001** — The UI supports English and Spanish.
- **FR-PREF-002** — The app initially follows the device language.
- **FR-PREF-003** — The app follows the system light or dark theme.

## Non-functional requirements

- **NFR-001** — All core data and media remain on the device.
- **NFR-002** — The application remains usable without network access.
- **NFR-003** — No feature requires an external API key or paid account.
- **NFR-004** — Room is the source of truth for structured data.
- **NFR-005** — User-visible operations must not block the main thread.
- **NFR-006** — Database changes include explicit migrations once a production schema exists.
- **NFR-007** — Core scheduling and date-calculation rules have unit tests.
- **NFR-008** — Key plant-management flows have focused Compose or instrumented tests.
- **NFR-009** — The app targets the current device and does not optimize prematurely for broad legacy support.

## Schema v3-to-v4 migration requirements

- The database filename remains `Plant` and the explicit migration chain is `1→2→3→4`.
- The current Plant model has one required `name` field and no common-name fields.
- Existing common-name values are deliberately discarded during `3→4`; plant names, scientific names, location, dates, descriptions, favorites, timestamps, photo paths, and notes are preserved.
- Reads and writes continue to work after migration, and searches use only name and scientific name.

## Schema v4-to-v5 migration requirements

- The database filename remains `Plant`, and the complete explicit migration chain becomes `1→2→3→4→5`.
- Room v5 adds `care_events` with UUID `id`, Plant foreign key `plantId`, `careType`, `performedAt`, nullable `note`, `createdAt`, and `updatedAt`.
- The foreign key uses `ON DELETE CASCADE`. The table indexes `(plantId, performedAt)` and `(plantId, careType, performedAt)`.
- Reads order events by `performedAt DESC, id DESC`, and all timestamps use UTC epoch milliseconds.
- The migration is non-destructive for existing plants, notes, favorites, photos, and timestamps; existing rows simply receive no care events.

## Phase 2 and correction acceptance scenarios

1. The user creates a plant named `Ficus Living Room`, captures or selects one photo, and later sees the original image after restarting the application.
2. The user sees the primary photo or placeholder in both the plant list and detail screen.
3. The user replaces and removes the photo; failed or cancelled imports do not change the stored reference.
4. The user adds two dated notes, edits one, and sees both independently in newest-first order.
5. Deleting the plant removes its notes and leaves no referenced primary-photo file; startup reconciliation removes any orphaned stable file.
6. Cancellation, missing camera/picker handlers, URI read/write failures, and process recreation leave the database and stable files consistent.
7. The Phase 2 flows work offline in English and Spanish and remain usable in light and dark themes.
8. The user can create a plant on the shared plant screen with a favorite state and photo staged before the first save; notes are not shown during creation.
9. A successful save persists the plant; a failed save removes the newly materialized photo and leaves no partial Room data.
10. The v3-to-v4 migration discards common-name values while preserving the remaining plant, note, and photo data.

## Later-phase acceptance scenarios

### Phase 3 care history

1. From plant detail, tapping watering, pruning, or fertilizing opens an editor prefilled with that type and the current date and time.
2. Saving a care event stores its type, date and time, optional trimmed note, and audit timestamps; blank notes are stored as `NULL`.
3. Editing can change the type, date, time, and note, but a future completion timestamp is rejected and the previous event remains intact.
4. Plant detail shows the latest event for each type and all events newest first, with localized date and time.
5. Deleting an event requires confirmation; deleting a plant cascades its care events and leaves no child rows.

### Phase 4 reminders

1. The user configures watering every 15 days, records watering, and sees the calculated next due date.
2. A due interval rule produces one local notification and does not duplicate it during subsequent checks.
3. The user configures an annual pruning season and receives a notification when the window begins.
4. The user can complete the care and reminder flows while the device is offline.

## Open product questions

- Whether the user can choose a preferred notification time in the MVP.
- Whether an incomplete seasonal action should receive a second reminder before the season ends.
- Whether care-event editing should automatically recompute reminder dates or require confirmation.
- Whether export and restore should be included before the first personally used release.
