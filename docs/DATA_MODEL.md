# Data model

## Implementation status

The implemented database is Room schema version 2. Phase 1 persists the `Plant` entity described below. The database filename remains `Plant` for compatibility with the template's schema version 1 and its migration path.

`PlantNote`, `CareEvent`, and `ReminderRule` are planned future tables. They are documented here as the target model, but they are not part of schema version 2 and no child-table cascade is available yet.

## Principles

- Room is the source of truth for structured application data.
- Identifiers are stable UUID strings to support future export and restore without ID collisions.
- Timestamps are stored as UTC epoch milliseconds.
- Care due dates are stored as local epoch days because the product promises a calendar day, not an exact instant.
- Annual seasons are stored as explicit month and day values.
- When photos are introduced, images will remain app-managed files and the database will store only a relative path. Photo storage is not implemented in Phase 1.
- Entity classes remain persistence details. Repositories expose immutable application models when database and UI needs diverge.

## Relationships

```text
Planned: Plant 1 ─── 0..* PlantNote
Planned: Plant 1 ─── 0..* CareEvent
Planned: Plant 1 ─── 0..* ReminderRule
```

These relationships are not implemented in Phase 1. Phase 1 deletion removes only the `Plant` row. Photo cleanup and notes cascades are planned for Phase 2, care-event cascades for Phase 3, and reminder-rule cascades for Phase 4.

## Plant

Represents one physical household plant. This is the implemented Phase 1 schema-v2 entity.

| Field | Type | Required | Notes |
|---|---|---:|---|
| `id` | String UUID | Yes | Primary key |
| `displayName` | String | Yes | User-facing name, for example `Ficus Living Room` |
| `displayNameSearch` | String | Yes | Internal `Locale.ROOT` lowercase projection used for case-insensitive search in the supported English and Spanish text |
| `commonName` | String? | No | Common species name |
| `commonNameSearch` | String | Yes | Internal lowercase projection; empty when `commonName` is absent |
| `scientificName` | String? | No | Botanical name |
| `scientificNameSearch` | String | Yes | Internal lowercase projection; empty when `scientificName` is absent |
| `location` | String? | No | Room or household location |
| `acquiredEpochDay` | Long? | No | Local acquisition date |
| `description` | String? | No | General plant information |
| `mainPhotoPath` | String? | No | Reserved nullable relative app-storage path; photo management is planned for Phase 2 |
| `isFavorite` | Boolean | Yes | Defaults to `false` |
| `createdAt` | Long | Yes | UTC epoch milliseconds |
| `updatedAt` | Long | Yes | UTC epoch milliseconds |

Implemented indexes and search support:

- `displayName` is indexed for stable list ordering.
- `isFavorite` is indexed for the favorites-only filter.
- The three normalized search projections support case-insensitive English and Spanish matching; leading-wildcard substring queries intentionally do not rely on an index.

## Planned future tables

The following tables are not present in schema version 2. Their definitions describe the planned model for later phases.

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

## Planned future transaction boundaries

Phase 1's delete transaction removes the Plant row only. The following boundaries apply when their owning phases add the corresponding data:

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
