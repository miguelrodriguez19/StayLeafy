# Decision log

This file records durable decisions. Each entry remains in place when superseded; a later entry should reference and replace it.

## D-001 — Product name and package

- **Date:** 2026-10-09
- **Status:** Accepted

The product name is `Stay Leafy`. The Android namespace and application ID are `com.miguelrodriguez19.stayleafy`.

The root project is named `StayLeafy`, while repository naming should use `stay-leafy` where a lowercase slug is required.

## D-002 — Local-only personal application

- **Date:** 2026-10-09
- **Status:** Accepted

Stay Leafy is a single-user personal application. Core data remains on the device. The project has no authentication, backend, synchronization, analytics, ads, subscriptions, or paid service dependency.

This keeps setup and operation simple and makes offline behavior the default rather than a fallback.

## D-003 — Official Android architecture template

- **Date:** 2026-10-09
- **Status:** Accepted

The project starts from the `base` branch of `android/architecture-templates` and retains Compose, Material 3, Room, Hilt, Navigation 3, Coroutines, Flow, and the existing testing foundation.

The template was customized with model name `Plant`, application name `StayLeafy`, and the package in D-001.

## D-004 — Single-module pragmatic architecture

- **Date:** 2026-10-09
- **Status:** Accepted

The application remains in the `:app` module and uses clear `ui` and `data` packages. ViewModels may call repositories directly. A separate domain layer and multi-module structure are deferred until a concrete complexity, reuse, ownership, or build-performance problem exists.

## D-005 — Room and Hilt

- **Date:** 2026-10-09
- **Status:** Accepted

Room is the source of truth for structured application data. Hilt remains the dependency injection mechanism because it is already integrated with the template and provides useful test replacement support.

## D-006 — One app-managed primary photo

- **Date:** 2026-10-09
- **Status:** Accepted

Phase 2 adds support for one primary photo per plant. The user may capture it with the camera or choose it through the system photo picker. The image is copied to app-managed storage, and Room stores its relative path.

Multiple photos are postponed to avoid introducing a gallery model before it is needed.

## D-007 — Independent dated notes

- **Date:** 2026-10-09
- **Status:** Accepted

Phase 2 adds plant notes as independent records with creation and update timestamps rather than one mutable text field on Plant. This preserves observations over time.

## D-008 — Two reminder models

- **Date:** 2026-10-09
- **Status:** Accepted

Stay Leafy supports:

1. Interval rules, such as watering every 15 days, recalculated after matching care is completed.
2. Annual seasonal rules, such as the start of an ideal hydrangea pruning window.

Both rule types are user-configured. The application does not infer botanical recommendations automatically.

## D-009 — Deferrable local notifications

- **Date:** 2026-10-09
- **Status:** Accepted

When reminder rules are introduced in Phase 4, notifications will be local and evaluated with WorkManager. Delivery will be date-oriented and will not promise an exact clock time. Exact alarms and their special permission are excluded until a real need is demonstrated.

## D-010 — Language policy

- **Date:** 2026-10-09
- **Status:** Accepted

Code identifiers, source comments, commit-facing documentation, and project documentation are written in English. User-facing strings support English and Spanish using Android resources.

## D-011 — Testing scope

- **Date:** 2026-10-09
- **Status:** Accepted

Testing is risk-based rather than exhaustive. Date calculations, persistence, transactional rescheduling, and deduplication receive strong coverage. A smaller set of Compose and device tests protects principal user flows. Fakes and in-memory Room databases are preferred over broad mocking.

## D-012 — License

- **Date:** 2026-10-09
- **Status:** Accepted

Stay Leafy is open source under Apache License 2.0. Existing Android Open Source Project file headers remain intact. New code may use the project copyright holder where headers are useful, but per-file headers are not required.

## D-013 — Room schema v2 migration preserves the template database

- **Date:** 2026-10-10
- **Status:** Accepted

Phase 1 uses Room schema version 2 while keeping the existing database filename `Plant`. The migration from version 1 to version 2 is explicit and non-destructive; destructive migration fallback is not allowed.

Legacy rows are preserved. The legacy `name` value is copied to `displayName`; a blank or whitespace-only legacy name becomes `Plant <uid>`. Each migrated row receives a new UUID string identifier, and `createdAt` and `updatedAt` are initialized to the migration timestamp. Optional fields are `NULL`, and `isFavorite` is `false`.

Schema v2 also stores internal `Locale.ROOT` lowercase projections of the display, common, and scientific names. Room searches those projections so accented English and Spanish names match case-insensitively rather than relying on SQLite `NOCASE`, which is ASCII-only by default.

This policy protects data already stored by the template and keeps future schema changes reviewable through exported Room schemas and migration tests.

## D-014 — Plant deletion and care-status filtering are phased

- **Date:** 2026-10-10
- **Status:** Accepted

Phase 1 deletes the Plant row after confirmation. It does not claim cleanup of photos or child records because those tables and files do not exist yet.

Ownership is incremental: Phase 2 adds photo cleanup and the notes cascade; Phase 3 adds the care-event cascade; Phase 4 adds the reminder-rule cascade and the care-status filter. The final product may provide complete deletion cleanup, but each owning phase must implement and test its own data and file boundaries.

## D-015 — Room schema v3 adds independent notes

- **Date:** 2026-10-10
- **Status:** Accepted

Phase 2 raises the database from schema version 2 to version 3 with an explicit `2→3` migration. `PlantNote` is an independent table with a UUID primary key, a Plant foreign key, non-blank body, `createdAt`, and `updatedAt`. The foreign key uses `ON DELETE CASCADE`, and `(plantId, createdAt)` is indexed. Notes are read newest first using `createdAt DESC, id DESC`.

The existing database filename remains `Plant`, and the complete migration chain remains explicit and non-destructive: `1→2→3`. Creation timestamps remain stable when a note is edited.

## D-016 — Permissionless primary-photo acquisition

- **Date:** 2026-10-10
- **Status:** Accepted

The app supports one primary photo per Plant. Camera capture uses `ActivityResultContracts.TakePicture` with an app-owned `FileProvider` URI and does not declare or request the `CAMERA` runtime permission. Image selection uses `PickVisualMedia(ImageOnly)`, with the platform document picker fallback when the Photo Picker is unavailable, and does not request `READ_MEDIA_IMAGES` or legacy broad storage permissions.

This keeps the offline flow least-privilege while still allowing system-owned camera and picker UI. Tests cover cancellation, unavailable handlers, URI failures, and process recreation instead of permission denial.

## D-017 — Original-byte app-managed photo storage

- **Date:** 2026-10-10
- **Status:** Accepted

Selected or captured image bytes are copied unchanged into the app's internal `plant_photos/` directory under a generated filename. Room stores only the stable relative path in `Plant.mainPhotoPath`; content URIs and bitmap data are not persisted.

Replacement writes the new file before updating Room, removes the previous file after a successful reference update, and removes the new file if the update fails. Plant deletion removes the Room row and notes in a transaction, then deletes the associated file. Photo mutations and the IO-bound startup reconciliation are serialized so reconciliation cannot delete a new file before Room references it. Reconciliation removes stable files that are no longer referenced. Temporary capture files are removed after cancellation or failed import.

Multiple photos, transformations, compression, cropping, captions, and note attachments remain out of scope.

## D-018 — Phase 2 completion gate

- **Date:** 2026-10-10
- **Status:** Accepted

Phase 2 is complete after Room v3 notes, one app-managed primary photo, list/detail rendering, cleanup/reconciliation, and offline English/Spanish behavior have passed implementation, migration, repository, ViewModel, Compose, and connected-device checks.

Phase 3 remains the owner of care-event data and cleanup. Phase 4 remains the owner of reminder rules, WorkManager evaluation, notification permission, and the care-status filter. No phase may add authentication, network synchronization, analytics, advertising, or a new architectural layer for this work.

## D-019 — Room v4 removes common name and unifies plant creation

- **Date:** 2026-10-10
- **Status:** Accepted

The current plant model has one required user-facing `name` field. The redundant `commonName` and `commonNameSearch` fields are removed in the explicit Room `3→4` migration. Existing common-name values are deliberately discarded; plant names, scientific names, location, acquisition date, description, favorite state, timestamps, primary-photo paths, and `PlantNote` rows are preserved. The complete migration chain is `1→2→3→4` and the database filename remains `Plant`.

Search is literal, partial, and case-insensitive across `name` and `scientificName` only. The old common-name search surface is not retained as hidden legacy data or a compatibility alias.

Plant creation, viewing, and editing use one shared screen so the information shown in detail is also available during creation. A new plant stages its favorite state, primary photo, and notes before the first save. The final operation writes the plant and staged notes in one Room transaction; file materialization occurs with compensating cleanup if Room persistence fails, and a failed save must not leave partial rows or an orphaned stable file.

This correction is intentionally separate from Phase 3. Care-event tables, care history, and their cascade remain planned and are not part of this decision.

## D-020 — Room v5 dated care events and correction workflow

- **Date:** 2026-10-10
- **Status:** Accepted

Phase 3 adds `CareEvent` as an independent Room table in schema v5. The complete migration chain becomes `1→2→3→4→5`, while the database filename remains `Plant`. A care event has a UUID, Plant foreign key with `ON DELETE CASCADE`, one of `WATERING`, `PRUNING`, or `FERTILIZING`, a completion date and time stored as UTC epoch milliseconds, an optional trimmed note, and `createdAt`/`updatedAt` audit timestamps. `createdAt` remains stable when an event is corrected.

The table is indexed by `(plantId, performedAt)` and `(plantId, careType, performedAt)`. Reads use `performedAt DESC, id DESC` so the history is deterministic, including ties. Optional blank notes become `NULL`, and a completion timestamp later than the injected current time is rejected by the repository.

Plant-detail quick actions open a prefilled editor with the selected care type and current local date and time. The event is not persisted until the user saves. The editor can correct type, date, time, and note; deletion requires confirmation. Plant detail presents the latest event for each care type and the complete reverse-chronological history.

Phase 3 does not create reminder rules, calculate next-due dates, reschedule notifications, or implement the care-status filter. Those behaviors remain owned by Phase 4. Room v5 registration, migration, UI, tests, and local/device verification are complete.
