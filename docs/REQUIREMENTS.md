# Product requirements

## Terminology

- **Plant** — A household plant owned by the user.
- **Care event** — A completed watering, pruning, or fertilizing action.
- **Interval rule** — A care rule due every configured number of days after completion.
- **Seasonal rule** — An annual care window defined by a start month/day and an optional end month/day.
- **Due date** — The local calendar date on which interval-based care becomes due.
- **Notice** — A persistent in-app record that one reminder-rule occurrence became due, independently of whether Android displayed a system notification.
- **Occurrence** — One due instance of a rule, identified for interval rules by the rule ID and due epoch day.

## Functional requirements

Phase delivery note: Phase 1 implements the Plant inventory. Phase 2 implements notes plus one app-managed primary photo. The independent correction pass raises Room to schema v4, removes the redundant common-name field, and unifies plant creation, viewing, and editing. Phase 3 implements Room v5 care events. Phase 4 implements schema v6 interval rules, Notices, filtering, navigation, WorkManager, and local notifications. Phase 5 implements Room v7 annual seasonal rules and reuses that local delivery pipeline.

### Plant inventory

- **FR-PLANT-001** — The user can create a plant.
- **FR-PLANT-002** — A plant requires a non-blank name.
- **FR-PLANT-003** — The user can optionally record scientific name, room or location, acquisition date, and general information.
- **FR-PLANT-004** — The user can view a list of all plants.
- **FR-PLANT-005** — The user can open a plant detail screen.
- **FR-PLANT-006** — The user can edit plant information.
- **FR-PLANT-007** — The user can delete a plant after confirmation.
- **FR-PLANT-008** — Deleting a plant ultimately removes its notes, care events, reminder rules, Notices, and app-managed photo. Delivery is incremental: Phase 1 removes the Plant row, Phase 2 adds photo cleanup and the notes cascade, Phase 3 adds the care-event cascade, and Phase 4 adds the reminder-rule and Notice cascades.
- **FR-PLANT-009** — The user can mark or unmark a plant as a favorite.
- **FR-PLANT-010** — The user can search plants by name or scientific name using literal, case-insensitive substring matching.
- **FR-PLANT-011** — The user can combine search and favorites with independent Upcoming and Overdue care filters. Upcoming means today through seven local calendar days ahead, inclusive; Overdue means strictly before today. Selecting both care filters uses union semantics, while search and favorites continue to intersect with that result.
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
- **FR-CARE-004** — Pressing a care cell opens a read-only history for that plant and care type, ordered newest first.
- **FR-CARE-005** — The history menu can clear all events of the selected type for the current plant after confirmation; it must not affect another type or plant.
- **FR-CARE-006** — A short press on Water, Prune, or Fertilize records the current time immediately. A long press opens a date picker for recording forgotten care on another local date.
- **FR-CARE-007** — The repository rejects a completion timestamp later than the injected current time.
- **FR-CARE-008** — Plant detail always shows one cell per supported care type with only that type's latest localized date, or a never-recorded label.
- **FR-CARE-009** — Existing care-event notes remain optional, trimmed, and stored as `NULL` when blank. Inline Phase 3 actions create events without notes.

### Interval reminders

- **FR-INTERVAL-001** — The user can configure care to recur every `N` days, where `N` is a positive integer.
- **FR-INTERVAL-002** — An interval rule belongs to one plant and one care type.
- **FR-INTERVAL-003** — Completing matching care recalculates the next due date as the completion date plus `N` days.
- **FR-INTERVAL-004** — Example: if `Ficus Living Room` is configured for watering every 15 days and is watered on October 10, its next watering due date is October 25.
- **FR-INTERVAL-005** — Saving an enabled rule requires an explicit due-date source: either the latest matching care event or a directly selected first due date. The UI does not silently reinterpret a manual date when editing a rule.
- **FR-INTERVAL-006** — A directly selected first due date may be in the past, making the rule immediately overdue in the UI.
- **FR-INTERVAL-007** — Watering, pruning, and fertilizing rules are configured together on a dedicated screen and saved atomically with one Save action.
- **FR-INTERVAL-008** — Disabling a rule prevents future due-state evaluation, Notices, and system notifications without deleting its configuration or care history.
- **FR-INTERVAL-009** — A disabled care type that has never been configured does not require a persisted rule row.
- **FR-INTERVAL-010** — A retroactive care event older than the latest matching event does not move the next due date backwards.
- **FR-INTERVAL-011** — Clearing one care type's history for the current plant preserves that rule's currently stored next due date.
- **FR-INTERVAL-012** — Plant detail shows the last-care date, next due date, and due state for each supported type. Inventory rows show the most urgent active rule and `+N` when more active rules exist.

### Seasonal reminders

The following Phase 5 requirements are implemented.

- **FR-SEASON-001** — The user can configure an annually recurring care season.
- **FR-SEASON-002** — A seasonal rule has a start month/day and may have an end month/day. Configuration presents these values as one read-only “day or period” field; its calendar accepts either a single day or a range and does not expose separate start/end inputs.
- **FR-SEASON-003** — A seasonal rule belongs to one plant and one care type.
- **FR-SEASON-004** — The app notifies the user when the seasonal window begins.
- **FR-SEASON-005** — Example: a hydrangea pruning rule can announce the beginning of its ideal pruning season.
- **FR-SEASON-006** — Completing matching care within the active window suppresses further notifications for that occurrence of the season.
- **FR-SEASON-007** — A disabled seasonal rule produces no notification.
- **FR-SEASON-008** — Interval and seasonal schedules are mutually exclusive for one Plant and care type; switching modes retains the inactive configuration.
- **FR-SEASON-009** — A missing end date creates a one-day annual window. An end earlier than the start creates a cross-year window, and both bounds are inclusive.
- **FR-SEASON-010** — February 29 is represented as an annual date and resolves to February 28 in non-leap years.
- **FR-SEASON-011** — Late daily evaluation during an unsatisfied active window still creates that occurrence's Notice. Evaluation outside the window does not deliver it.
- **FR-SEASON-012** — Plant detail shows upcoming, active, or satisfied seasonal state. Upcoming and Overdue inventory filters remain interval-only.

### Notifications

- **FR-NOTIFICATION-001** — Notifications are generated locally; no push service is used.
- **FR-NOTIFICATION-002** — On Android versions requiring runtime notification permission, the app explains the benefit and requests permission contextually after the first active reminder rule is saved. Saving the rule does not depend on the permission result.
- **FR-NOTIFICATION-003** — A notification identifies the plant and the care action.
- **FR-NOTIFICATION-004** — Tapping a notification selects the Notices root and opens the matching Notice detail; the detail provides a separate action to open the plant.
- **FR-NOTIFICATION-005** — The scheduler avoids duplicate notifications for the same rule occurrence.
- **FR-NOTIFICATION-006** — Reminders survive normal application restarts and device reboots through WorkManager persistence.
- **FR-NOTIFICATION-007** — Unique periodic WorkManager evaluation runs approximately once per day around 09:00 in the device's local time. Delivery is best effort and does not promise an exact clock time.
- **FR-NOTIFICATION-008** — Saving a rule that is already due after the day's approximate 09:00 evaluation does not trigger an immediate system notification; it is eligible at the next daily evaluation.
- **FR-NOTIFICATION-009** — If permission is unavailable, the due occurrence still creates a Notice and records system delivery as suppressed. Granting permission later does not retroactively notify suppressed occurrences.
- **FR-NOTIFICATION-010** — When at least one rule is enabled but Android notifications are blocked, the Notices screen shows a localized explanation and an action to open application notification settings.

### Notices and navigation

- **FR-NOTICE-001** — The application has `Plants` and `Notices` bottom-navigation roots with independent navigation state. Bottom navigation remains visible on root, detail, editor, and configuration destinations.
- **FR-NOTICE-002** — The Notices root lists persistent due occurrences newest first and shows loading, error/retry, empty, and content states.
- **FR-NOTICE-003** — The Notices tab shows the unread count, visually capped at `99+`, and unread rows have a distinct indicator.
- **FR-NOTICE-004** — Opening a Notice marks it read. A read Notice cannot be manually returned to unread.
- **FR-NOTICE-005** — The user can mark all unread Notices as read. This action and read-on-open cancel any associated displayed Android notifications.
- **FR-NOTICE-006** — Notices cannot be deleted individually. Deleting their plant removes them through the reminder-rule cascade.
- **FR-NOTICE-007** — A Notice detail shows its care type, plant, due date, and generation time, handles a missing Notice with localized state, and can navigate to the current plant detail.
- **FR-NOTICE-008** — Completing matching care, disabling its rule, or replacing its due occurrence resolves the previous Notice by marking it read and cancelling its displayed Android notification.
- **FR-NOTICE-009** — Notice text is derived from stored semantic values and current localized resources; translated display strings are not persisted.
- **FR-NOTICE-010** — Opening a Plant from Notice detail removes that Notice detail from the Notices stack. Returning to the Plants root and selecting Notices therefore opens the Notices inbox rather than the previous detail.
- **FR-NOTICE-011** — Selecting the already active Plants or Notices bottom-navigation tab returns that tab to its root list. Selecting the other tab preserves the other tab's independent navigation state.

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

## Schema v5-to-v6 migration requirements

- The database filename remains `Plant`, and the complete explicit migration chain becomes `1→2→3→4→5→6`.
- Room v6 adds interval-only `reminder_rules` and persistent `notices`; existing plants, notes, photos, favorites, timestamps, and care events remain unchanged.
- A reminder rule has a Plant foreign key with `ON DELETE CASCADE` and is unique for `(plantId, careType, scheduleType)`. Phase 4 accepts only `scheduleType=INTERVAL`.
- A Notice has a reminder-rule foreign key with `ON DELETE CASCADE`, a globally unique occurrence key, semantic care and due-date fields, read state, and Android delivery state.
- Deleting a Plant cascades to its rules and then to their Notices.
- Seasonal scheduling fields and data are not introduced by schema v6; Phase 5 owns that migration and behavior.

## Schema v6-to-v7 migration requirements

- The database filename remains `Plant`, and the complete explicit migration chain becomes `1→2→3→4→5→6→7`.
- Room v7 makes interval fields nullable and adds nullable annual start/end month/day fields so `INTERVAL` and `SEASONAL` rows share `reminder_rules` without invalid placeholder values.
- Existing interval rules and Notices are preserved unchanged; their seasonal columns are `NULL`.
- Seasonal rows are unique under the existing `(plantId, careType, scheduleType)` key and use the existing Plant cascade.
- An index on `(enabled, scheduleType)` supports daily seasonal evaluation.

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

### Phase 3 care history

1. From plant detail, a short press on watering, pruning, or fertilizing records that care immediately with the injected current time.
2. A long press opens a date picker and records the selected non-future local date for that care type.
3. Each care cell shows its latest localized date; pressing the cell opens only that type's read-only history, newest first.
4. The history menu clears all events of that type for the current plant only after confirmation.
5. Deleting a plant cascades all of its care events and leaves no child rows.

### Phase 4 reminders

These verified scenarios define the implemented Phase 4 behavior.

1. The user atomically configures watering every 15 days from the latest watering or a selected first due date and sees the calculated due state.
2. Matching care advances the date, while an older retroactive event and clearing the scoped history do not move the stored date backwards.
3. Upcoming and Overdue filters combine correctly with each other, search, and favorites.
4. A due occurrence creates one Notice and at most one system notification across repeated worker runs.
5. Opening a Notice or marking all read updates the badge and cancels related displayed notifications.
6. Denied permission keeps the rule and Notice usable, shows guidance in Notices, and does not cause retroactive delivery after permission is granted.
7. A notification tap opens the matching Notice detail, and its plant action opens the related plant detail.
8. Deleting a plant cascades its interval rules and Notices.
9. The complete interval reminder flow works offline in English and Spanish and in light and dark themes.

### Phase 5 seasonal reminders

1. The user selects Off, Interval, or Season independently for watering, pruning, and fertilizing and saves all cards atomically.
2. A same-year, one-day, or cross-year annual window resolves correctly, including February 29 in non-leap years.
3. Matching care inside the active window marks that occurrence satisfied and resolves its Notice; care outside the window does not.
4. The daily worker creates one seasonal Notice per occurrence while the window is active, including a late first evaluation, and repeated runs do not duplicate it.
5. Plant detail shows upcoming, active, and satisfied seasonal state; inventory Upcoming and Overdue filters remain based on interval rules.
6. Seasonal configuration, Notice, and notification text work offline in English and Spanish and in light and dark themes.

## Open product questions

- Whether export and restore should be included before the first personally used release.
