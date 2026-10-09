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

The initial product supports one primary photo per plant. The user may capture it with the camera or choose it through the system photo picker. The image is copied to app-managed storage, and Room stores its relative path.

Multiple photos are postponed to avoid introducing a gallery model before it is needed.

## D-007 — Independent dated notes

- **Date:** 2026-10-09
- **Status:** Accepted

Plant notes are independent records with creation and update timestamps rather than one mutable text field on Plant. This preserves observations over time.

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

Notifications are local and evaluated with WorkManager. Delivery is date-oriented and does not promise an exact clock time. Exact alarms and their special permission are excluded until a real need is demonstrated.

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
