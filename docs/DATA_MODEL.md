# Data model

## Implementation status

The implemented database is Room schema version 4. Phase 1 persists the `Plant` entity, Phase 2 adds `PlantNote` and one primary photo, and the independent correction pass unifies the plant screen and removes the redundant common-name fields. The database filename remains `Plant` for compatibility with the template's schema version 1 and its migration path.

The explicit migration chain is `1→2→3→4`. Migration `3→4` preserves plant, note, photo, favorite, and timestamp data, discards stored common-name values, and leaves one required `name` field plus the scientific-name field for search.

`CareEvent` and `ReminderRule` remain planned future tables. They are not part of schema version 4. Care-event cleanup belongs to Phase 3 and reminder-rule cleanup belongs to Phase 4.

## Principles

- Room is the source of truth for structured application data.
- Identifiers are stable UUID strings to support future export and restore without ID collisions.
- Timestamps are stored as UTC epoch milliseconds.
- Care due dates are stored as local epoch days because the product promises a calendar day, not an exact instant.
- Annual seasons are stored as explicit month and day values.
- Images remain app-managed files and the database stores only a stable relative path. Phase 2 preserves original source bytes and does not store bitmaps or content URIs in Room.
- Entity classes remain persistence details. Repositories expose immutable application models when database and UI needs diverge.

## Relationships

```text
Implemented in schema v4: Plant 1 ─── 0..* PlantNote
Planned: Plant 1 ─── 0..* CareEvent
Planned: Plant 1 ─── 0..* ReminderRule
```

Phase 1 deletion removed only the `Plant` row. Phase 2 adds the notes cascade and primary-photo cleanup. Care-event cascades remain planned for Phase 3 and reminder-rule cascades remain planned for Phase 4.

## Plant

Represents one physical household plant. This is the implemented schema-v4 entity.

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

The database does not own image bytes. Confirmed images live under the app's internal files directory in `plant_photos/`, with a generated filename and extension derived from the source MIME type. Room stores only the stable relative path in `Plant.mainPhotoPath`.

On the shared plant creation screen, a selected or captured image remains staged in temporary storage until the plant is saved. The save materializes the stable path and writes the plant, favorite state, and staged notes in one Room transaction. If the Room write fails, the newly materialized file is deleted as compensation and the staged draft remains retryable; a successful write makes the temporary file eligible for immediate cleanup.

Camera capture uses `TakePicture` with a temporary app-owned `FileProvider` URI. Image selection uses `PickVisualMedia(ImageOnly)` and its platform document fallback. Neither flow requires `CAMERA`, `READ_MEDIA_IMAGES`, or legacy broad storage permission.

The repository writes the new original bytes before committing the new Room reference. It deletes the previous stable file only after the reference is committed, deletes the new file when the database update fails, removes the plant photo after plant deletion, and runs an IO-bound startup reconciliation that removes stable files not referenced by any Plant row. Photo mutations and reconciliation are serialized to protect the file-before-Room compensation window. Temporary capture files are removed on cancellation or failed import.

## CareEvent

Represents completed care and forms the historical record.

| Field | Type | Required | Notes |
|---|---|---:|---|
| `id` | String UUID | Yes | Primary key |
| `plantId` | String UUID | Yes | Foreign key to Plant |
| `careType` | String enum | Yes | `WATERING`, `PRUNING`, or `FERTILIZING` |
| `performedAt` | Long | Yes | UTC epoch milliseconds |
| `note` | String? | No | Optional event-specific observation |
| `createdAt` | Long | Yes | Audit timestamp |
| `updatedAt` | Long | Yes | Audit timestamp |

Recommended index: `(plantId, careType, performedAt)`.

## ReminderRule

Represents user-configured interval or seasonal care behavior.

| Field | Type | Required | Notes |
|---|---|---:|---|
| `id` | String UUID | Yes | Primary key |
| `plantId` | String UUID | Yes | Foreign key to Plant |
| `careType` | String enum | Yes | Watering, pruning, or fertilizing |
| `scheduleType` | String enum | Yes | `INTERVAL` or `SEASONAL` |
| `enabled` | Boolean | Yes | Defaults to `true` |
| `intervalDays` | Int? | Conditional | Positive for interval rules |
| `nextDueEpochDay` | Long? | Conditional | Current interval occurrence |
| `seasonStartMonth` | Int? | Conditional | `1..12` for seasonal rules |
| `seasonStartDay` | Int? | Conditional | Valid day for month |
| `seasonEndMonth` | Int? | No | Optional end of seasonal window |
| `seasonEndDay` | Int? | No | Required when end month is present |
| `lastNotifiedOccurrence` | String? | No | Deduplication key |
| `createdAt` | Long | Yes | UTC epoch milliseconds |
| `updatedAt` | Long | Yes | UTC epoch milliseconds |

Suggested uniqueness constraint: `(plantId, careType, scheduleType)` for the MVP. This can be relaxed later if multiple rules of the same type become useful.

The repository validates schedule-specific fields:

- `INTERVAL` requires `intervalDays > 0` and `nextDueEpochDay`.
- `SEASONAL` requires a valid start month/day.
- A seasonal end date must be complete and valid when present.
- Cross-year windows, such as November through February, are valid.

## Deduplication keys

An interval occurrence key can be `interval:<ruleId>:<nextDueEpochDay>`.

A seasonal occurrence key can be `season:<ruleId>:<seasonStartYear>`.

Persisting the last notified key makes reminder checks idempotent.

## Planned future transaction boundaries

The correction pass keeps plant, staged-note, and favorite creation within the shared plant-save boundary. The following boundaries apply when their owning phases add the corresponding data:

The following operations must be transactional:

1. Recording a care event and recalculating the matching interval rule.
2. Editing or deleting the latest matching care event and recomputing its next due date.
3. Deleting a plant and its structured child data. File cleanup happens immediately after the database transaction and is safe to retry.

For Phase 2, deleting a plant cascades `PlantNote` rows inside Room; deleting the associated primary-photo file is a compensating file operation after the Room transaction. Reconciliation is the recovery path for a failed or interrupted file cleanup.

## Future schema candidates

These are deliberately excluded from the initial schema:

- Multiple photos per plant
- User-defined care types
- Tags or plant groups
- Backup metadata
- Notification delivery history beyond the current deduplication key
- Remote identifiers or synchronization state
