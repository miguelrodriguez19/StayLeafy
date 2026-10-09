# Data model

## Principles

- Room is the source of truth for structured application data.
- Identifiers are stable UUID strings to support future export and restore without ID collisions.
- Timestamps are stored as UTC epoch milliseconds.
- Care due dates are stored as local epoch days because the product promises a calendar day, not an exact instant.
- Annual seasons are stored as explicit month and day values.
- Images remain app-managed files; the database stores a relative path.
- Entity classes remain persistence details. Repositories expose immutable application models when database and UI needs diverge.

## Relationships

```text
Plant 1 ─── 0..* PlantNote
Plant 1 ─── 0..* CareEvent
Plant 1 ─── 0..* ReminderRule
```

Child rows use a foreign key to `Plant` with cascade deletion. Deleting a plant also triggers cleanup of its image file through the repository.

## Plant

Represents one physical household plant.

| Field | Type | Required | Notes |
|---|---|---:|---|
| `id` | String UUID | Yes | Primary key |
| `displayName` | String | Yes | User-facing name, for example `Ficus Living Room` |
| `commonName` | String? | No | Common species name |
| `scientificName` | String? | No | Botanical name |
| `location` | String? | No | Room or household location |
| `acquiredEpochDay` | Long? | No | Local acquisition date |
| `description` | String? | No | General plant information |
| `mainPhotoPath` | String? | No | Relative app-storage path |
| `isFavorite` | Boolean | Yes | Defaults to `false` |
| `createdAt` | Long | Yes | UTC epoch milliseconds |
| `updatedAt` | Long | Yes | UTC epoch milliseconds |

Recommended indexes:

- Case-insensitive index or normalized search field for `displayName`.
- Index on `isFavorite` only if query measurements justify it.

## PlantNote

Represents one independently dated observation.

| Field | Type | Required | Notes |
|---|---|---:|---|
| `id` | String UUID | Yes | Primary key |
| `plantId` | String UUID | Yes | Foreign key to Plant |
| `body` | String | Yes | Non-blank note content |
| `createdAt` | Long | Yes | UTC epoch milliseconds |
| `updatedAt` | Long | Yes | UTC epoch milliseconds |

Recommended index: `(plantId, createdAt)`.

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

## Transaction boundaries

The following operations must be transactional:

1. Recording a care event and recalculating the matching interval rule.
2. Editing or deleting the latest matching care event and recomputing its next due date.
3. Deleting a plant and its structured child data. File cleanup happens immediately after the database transaction and is safe to retry.

## Future schema candidates

These are deliberately excluded from the initial schema:

- Multiple photos per plant
- User-defined care types
- Tags or plant groups
- Backup metadata
- Notification delivery history beyond the current deduplication key
- Remote identifiers or synchronization state
