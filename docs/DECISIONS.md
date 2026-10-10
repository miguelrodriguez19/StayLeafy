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

Phase 2 will add support for one primary photo per plant. The user may capture it with the camera or choose it through the system photo picker. The image will be copied to app-managed storage, and Room will store its relative path.

Multiple photos are postponed to avoid introducing a gallery model before it is needed.

## D-007 — Independent dated notes

- **Date:** 2026-10-09
- **Status:** Accepted

Phase 2 will add plant notes as independent records with creation and update timestamps rather than one mutable text field on Plant. This preserves observations over time.

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
