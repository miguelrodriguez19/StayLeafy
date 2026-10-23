# Data model

## Implementation status

The implemented baseline is Room schema version 7. Phase 1 persists the `Plant` entity, Phase 2 adds `PlantNote` and one primary photo, the independent correction pass unifies the plant screen and removes the redundant common-name fields, Phase 3 adds `CareEvent`, Phase 4 adds interval `ReminderRule` and persistent `Notice` rows, and Phase 5 extends rules with annual seasonal windows. The database filename remains `Plant` for compatibility with the template's schema version 1 and its migration path.

The complete registered migration chain is `1→2→3→4→5→6→7`. The non-destructive `6→7` migration preserves existing interval rules and Notices, makes interval-only columns nullable, and adds nullable annual seasonal fields.

`CareEvent`, interval and seasonal `ReminderRule`, and persistent `Notice` tables are implemented. Care-event cleanup belongs to Phase 3; reminder-rule and Notice cleanup belongs to Phase 4, with seasonal occurrence behavior added by Phase 5.

Phase 7 does not change Room schema: app preferences use Preferences DataStore, while the existing `Plant.mainPhotoPath` continues to reference an app-managed file.

## Principles

- Room is the source of truth for structured application data.
- Identifiers are stable UUID strings to support future export and restore without ID collisions.
- Timestamps are stored as UTC epoch milliseconds.
- Care due dates are stored as local epoch days because the product promises a calendar day, not an exact instant.
- Annual seasons are stored as explicit month and day values.
- Images remain app-managed files and the database stores only a stable relative path; bitmaps and content URIs are never persisted in Room. Phase 2 originally retained source bytes unchanged. Phase 7 imports instead store newly captured or selected crops as square JPEGs; existing files remain untouched.
- Entity classes remain persistence details. Repositories expose immutable application models when database and UI needs diverge.

## Relationships

```text
Implemented in schema v7: Plant 1 ─── 0..* PlantNote
Implemented in schema v7: Plant 1 ─── 0..* CareEvent
Implemented in schema v7: Plant 1 ─── 0..* ReminderRule
Implemented in schema v7: ReminderRule 1 ─── 0..* Notice
```

Phase 1 deletion removed only the `Plant` row. Phase 2 adds the notes cascade and primary-photo cleanup. Phase 3 adds the care-event cascade; Phase 4 cascades Plant deletion through reminder rules to Notices.

## Plant

Represents one physical household plant. This entity is retained in implemented schema v7.

| Field | Type | Required | Notes |
|---|---|---:|---|
| `id` | String UUID | Yes | Primary key |
| `name` | String | Yes | User-facing non-blank name, for example `Ficus Living Room` |
| `nameSearch` | String | Yes | Internal `Locale.ROOT` lowercase projection used for literal, case-insensitive name search |
| `scientificName` | String? | No | Botanical name |
| `scientificNameSearch` | String | Yes | Internal lowercase projection; empty when `scientificName` is absent |
| `location` | String? | No | Room or household location |
| `acquiredEpochDay` | Long? | No | Local acquisition date |
| `description` | String? | No | General plant information |
| `mainPhotoPath` | String? | No | Nullable relative path under app-managed `plant_photos/`; only one primary photo is supported |
| `isFavorite` | Boolean | Yes | Defaults to `false` |
| `createdAt` | Long | Yes | UTC epoch milliseconds |
| `updatedAt` | Long | Yes | UTC epoch milliseconds |

Implemented indexes and search support:

- `name` is indexed for stable list ordering.
- `isFavorite` is indexed for the favorites-only filter.
- The two normalized search projections support case-insensitive English and Spanish matching; leading-wildcard substring queries intentionally do not rely on an index.

## PlantNote

Represents one independently dated observation. It is implemented in Room schema v4; the table was introduced in schema v3 and is preserved by `3→4`.

| Field | Type | Required | Notes |
|---|---|---:|---|
| `id` | String UUID | Yes | Primary key |
| `plantId` | String UUID | Yes | Foreign key to Plant |
| `body` | String | Yes | Trimmed, non-blank note content |
| `createdAt` | Long | Yes | UTC epoch milliseconds |
| `updatedAt` | Long | Yes | UTC epoch milliseconds |

The foreign key references `Plant.id` with `ON DELETE CASCADE`. The table has an index on `(plantId, createdAt)`, and repository reads order by `createdAt DESC, id DESC`.

## Photo storage

The database does not own image bytes. Confirmed images live under the app's internal files directory in `plant_photos/`; Room stores only the stable relative path in `Plant.mainPhotoPath`. Phase 2 derived the extension from source MIME type. Phase 7 normalizes new and replacement images to square JPEG output while retaining the same stable-path contract.

On the shared plant creation screen, a selected or captured image remains staged in temporary storage until the plant is saved. The save materializes the stable path and writes the plant and favorite state; notes are created only after the Plant row exists. If the Room write fails, the newly materialized file is deleted as compensation and the staged draft remains retryable; a successful write makes the temporary file eligible for immediate cleanup.

Camera capture uses `TakePicture` with a temporary app-owned `FileProvider` URI. Image selection uses `PickVisualMedia(ImageOnly)` and its platform document fallback. Neither flow requires `CAMERA`, `READ_MEDIA_IMAGES`, or legacy broad storage permission.

The repository writes the new image file before committing the new Room reference. It deletes the previous stable file only after the reference is committed, deletes the new file when the database update fails, removes the plant photo after plant deletion, and runs an IO-bound startup reconciliation that removes stable files not referenced by any Plant row. Photo mutations and reconciliation are serialized to protect the file-before-Room compensation window. Temporary capture, crop-source, and crop-output files are removed on cancellation or failed import. Crop failure preserves the previous stable or staged image, and already stored files remain unchanged.

Phase 7 output is JPEG, 1:1, quality 92, and at most 2048×2048 pixels without upscaling. Preferences such as theme, app language, and preferred reminder time use Preferences DataStore; they do not add Room entities, columns, or migrations.

## CareEvent — Phase 3 / schema v5

Represents completed care and forms the historical record. The immutable model, repository, Room v5 entity, `4→5` migration, UI, and test coverage are implemented.

| Field | Type | Required | Notes |
|---|---|---:|---|
| `id` | String UUID | Yes | Primary key |
| `plantId` | String UUID | Yes | Foreign key to Plant |
| `careType` | String enum | Yes | `WATERING`, `PRUNING`, or `FERTILIZING` |
| `performedAt` | Long | Yes | Completion date and time as UTC epoch milliseconds; future values are rejected against the injected clock |
| `note` | String? | No | Optional event-specific observation; trim before storage and use `NULL` when blank |
| `createdAt` | Long | Yes | UTC epoch milliseconds; stable after correction |
| `updatedAt` | Long | Yes | UTC epoch milliseconds; changes when the event is corrected |

The foreign key references `Plant.id` with `ON DELETE CASCADE`. Required indexes are `(plantId, performedAt)` for history reads and `(plantId, careType, performedAt)` for per-type summaries and future care queries. Repository reads order by `performedAt DESC, id DESC`, giving deterministic newest-first history even when events share a timestamp.

Supported `careType` values are `WATERING`, `PRUNING`, and `FERTILIZING`. A short press records the injected current time, while a long press records the selected local date using the current local time. New inline events have no note; the nullable column remains compatible with events created before this interaction was simplified. Type history is queried from the plant-scoped newest-first stream, and clearing uses both `plantId` and `careType`. Phase 4 adds transactional interval rescheduling while preserving the stored due date when this scoped history is cleared.

## ReminderRule — implemented in schema v7

Represents user-configured interval or annual seasonal care. There is at most one row for each Plant, care type, and schedule type. Exactly one schedule type may be enabled for a Plant/care pair; switching modes retains the inactive row's settings. A card that has never been configured and remains disabled does not require a row.

| Field | Type | Required | Notes |
|---|---|---:|---|
| `id` | String UUID | Yes | Primary key |
| `plantId` | String UUID | Yes | Foreign key to Plant |
| `careType` | String enum | Yes | `WATERING`, `PRUNING`, or `FERTILIZING` |
| `scheduleType` | String enum | Yes | `INTERVAL` or `SEASONAL` |
| `enabled` | Boolean | Yes | Disabled rows retain their configuration but do not contribute due state |
| `intervalDays` | Int? | For interval | Positive number of local calendar days; `NULL` for seasonal rows |
| `nextDueEpochDay` | Long? | For interval | Current interval occurrence as a local epoch day; `NULL` for seasonal rows |
| `seasonStartMonth` | Int? | For seasonal | Annual start month, 1–12 |
| `seasonStartDay` | Int? | For seasonal | Valid annual start day for its month |
| `seasonEndMonth` | Int? | No | Optional inclusive annual end month |
| `seasonEndDay` | Int? | No | Optional inclusive annual end day; present with `seasonEndMonth` |
| `createdAt` | Long | Yes | UTC epoch milliseconds |
| `updatedAt` | Long | Yes | UTC epoch milliseconds |

The Plant foreign key uses `ON DELETE CASCADE`. A unique index on `(plantId, careType, scheduleType)` prevents duplicate rules. `(enabled, nextDueEpochDay)` supports interval evaluation and filtering; `(enabled, scheduleType)` supports seasonal evaluation.

The repository validates `intervalDays > 0`, a valid Plant and care type, `scheduleType=INTERVAL`, and a required next due date. The configuration draft also contains an explicit, non-persisted due source:

- `LatestCare` calculates the due date from the newest matching `CareEvent` plus the interval and is unavailable when no matching event exists.
- `Date(epochDay)` stores the selected first due date directly and may be in the past.

On edit, the draft starts from the stored due date rather than silently reinterpreting it against history. The user must explicitly choose `LatestCare` to recalculate from an event.

A seasonal configuration stores a required annual start and an optional annual end. No end means a one-day window. An end before the start crosses into the following year; bounds are inclusive. February 29 remains representable and is clamped to February 28 in non-leap years. Seasonal state and satisfaction are derived from the current occurrence and matching care-event timestamps rather than persisted as duplicate state.

## Notice — implemented in schema v6

Represents one due rule occurrence in the in-app Notices inbox. It exists independently of Android notification permission, and it stores semantic values rather than localized display text.

| Field | Type | Required | Notes |
|---|---|---:|---|
| `id` | String UUID | Yes | Primary key |
| `ruleId` | String UUID | Yes | Foreign key to `ReminderRule` with `ON DELETE CASCADE` |
| `occurrenceKey` | String | Yes | Globally unique interval or seasonal occurrence key |
| `careType` | String enum | Yes | Semantic action used for localized rendering |
| `dueEpochDay` | Long | Yes | Due local calendar date captured for this occurrence |
| `createdAt` | Long | Yes | UTC epoch milliseconds used for deterministic newest-first ordering |
| `readAt` | Long? | No | `NULL` while unread; set once when opened, resolved, or marked read |
| `deliveryState` | String enum | Yes | `PENDING`, `DELIVERED`, or `SUPPRESSED` |

The unique `occurrenceKey` is the primary deduplication boundary. Indexes support `createdAt DESC, id DESC`, unread counting, and rule-scoped resolution. Plant name is resolved through the Notice's rule and Plant so renamed plants use their current localized presentation.

Delivery-state meaning:

- `PENDING` means the Notice exists and system-delivery resolution has not completed.
- `DELIVERED` means the stable Android notification tag/ID was posted.
- `SUPPRESSED` means Android permission was unavailable for that occurrence. It is never replayed retroactively if permission is later granted.

Opening a Notice, marking all Notices read, completing matching care, disabling a rule, or replacing its occurrence sets `readAt` when necessary and causes any displayed notification for that occurrence to be cancelled. Read state is one-way; individual Notices are neither deleted nor returned to unread.

## Deduplication keys

An interval occurrence key is `interval:<ruleId>:<nextDueEpochDay>`. A seasonal occurrence key is `seasonal:<ruleId>:<startEpochDay>`. Persisting either key as a unique Notice makes repeated worker checks idempotent. A stable Android notification tag/ID derived from the Notice ensures that a retry after posting but before updating `deliveryState` replaces the same system notification rather than creating another one.

## Transaction boundaries

The correction pass keeps plant and favorite creation within the shared plant-save boundary, with compensating cleanup for a staged photo. Phase 4 adds these Room transaction requirements:

1. Saving the three care-schedule cards for one Plant is atomic, including mode switches between retained interval and seasonal rows.
2. Recording matching care inserts the `CareEvent`, calculates from the newest matching event, updates `nextDueEpochDay`, and resolves the old occurrence together. An older retroactive event cannot move the date backwards.
3. Disabling or rescheduling a rule resolves the replaced occurrence in the same transaction. Android notification cancellation follows from the committed database state and is safe to retry.
4. Worker evaluation inserts or retrieves the Notice by its unique occurrence key before attempting system delivery, then records `DELIVERED` or `SUPPRESSED`.
5. Deleting a Plant cascades notes, care events, reminder rules, and Notices. Primary-photo file cleanup happens immediately after the database transaction and remains safe to retry.
6. Recording matching care inside an enabled seasonal occurrence inserts the event and resolves that exact occurrence's Notice together; Android cancellation follows the commit.

Clearing a plant-and-type-scoped care history intentionally does not recalculate or clear the current `nextDueEpochDay`.

For Phase 2, deleting a plant cascades `PlantNote` rows inside Room; deleting the associated primary-photo file is a compensating file operation after the Room transaction. Reconciliation is the recovery path for a failed or interrupted file cleanup.

## Phase 8 logical backup boundary

Manual archive format 1 exports all five schema-v7 collections and Preferences DataStore values, not a raw database file. No Room entity/column/schema migration is introduced. A dedicated DAO captures a transactionally consistent snapshot and replaces rows in foreign-key order in a single transaction. IDs and historical semantic values remain stable; search projections are regenerated and photo references are remapped to newly allocated files containing the original bytes.

The restore journal is private operational state in `noBackupFilesDir`, not an entity or part of the export. It spans the Room/DataStore/file boundary with rollback before commit and cleanup after commit. Existing files are retained until the durable commit marker. Imported current/past pending Notices become suppressed without changing `readAt`; future rules and future pending Notices remain intact. See [BACKUP.md](BACKUP.md).

## Future schema candidates

These are deliberately excluded from the initial schema:

- Multiple photos per plant
- User-defined care types
- Tags or plant groups
- Backup metadata
- Notification delivery history beyond persistent due Notices
- Remote identifiers or synchronization state
