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

Plant creation, viewing, and editing use one shared screen. A new plant stages its favorite state and primary photo before the first save; notes are intentionally available only after the Plant row exists. File materialization occurs with compensating cleanup if Room persistence fails, and a failed save must not leave a partial row or orphaned stable file.

This correction is intentionally separate from Phase 3. Care-event tables, care history, and their cascade remain planned and are not part of this decision.

## D-020 — Room v5 dated care events and correction workflow

- **Date:** 2026-10-10
- **Status:** Accepted

Phase 3 adds `CareEvent` as an independent Room table in schema v5. The complete migration chain becomes `1→2→3→4→5`, while the database filename remains `Plant`. A care event has a UUID, Plant foreign key with `ON DELETE CASCADE`, one of `WATERING`, `PRUNING`, or `FERTILIZING`, a completion date and time stored as UTC epoch milliseconds, an optional trimmed note, and `createdAt`/`updatedAt` audit timestamps. `createdAt` remains stable when an event is corrected.

The table is indexed by `(plantId, performedAt)` and `(plantId, careType, performedAt)`. Reads use `performedAt DESC, id DESC` so the history is deterministic, including ties. Optional blank notes become `NULL`, and a completion timestamp later than the injected current time is rejected by the repository.

The original Phase 3 interaction used a separate editor for type, date, time, and note corrections. D-021 supersedes that UI interaction with inline recording and scoped read-only histories; the schema and repository validation described above remain unchanged.

Phase 3 does not create reminder rules, calculate next-due dates, reschedule notifications, or implement the care-status filter. Those behaviors remain owned by Phase 4. Room v5 registration, migration, UI, tests, and local/device verification are complete.

## D-021 — Inline care actions and plant-scoped read-only history

- **Date:** 2026-10-10
- **Status:** Accepted

This decision supersedes only the UI workflow in D-020 and does not change schema v5. Plant detail keeps an always-visible Care history section with one cell for watering, pruning, and fertilizing. Each cell shows only its latest localized date. A short press on its action records the injected current time immediately; a long press opens a date picker and records the chosen local date. Reminder frequencies and the Configure care destination remain owned by Phase 4 and are not exposed as a non-functional button.

Pressing a cell opens a read-only bottom sheet containing only that care type for the current plant, ordered by `performedAt DESC, id DESC`. Individual events cannot be edited or deleted from this view. Its contextual menu can clear the complete selected-type history after confirmation. The delete query is constrained by both `plantId` and `careType`, so no other plant or care type is modified. Existing optional notes remain readable for compatibility, while new inline events store `NULL` notes.

## D-022 — Phase 4 interval rules, persistent Notices, and daily local delivery

- **Date:** 2026-10-11
- **Status:** Accepted

Phase 4 introduces interval scheduling only. One rule may exist for each Plant, care type, and schedule type; schema v6 accepts only `INTERVAL`. The user configures watering, pruning, and fertilizing together on a dedicated screen and saves all three atomically. Every enabled rule requires a positive interval and an explicit basis: the latest matching care event or a directly selected first due date. Existing manual due dates are never silently reinterpreted against care history.

Matching care advances the rule from the newest matching event in the same Room transaction. Older retroactive events cannot move the next due date backwards, and clearing the plant-and-type-scoped care history preserves the stored due date. Upcoming means today through seven local days ahead, inclusive; Overdue means before today. Their inventory chips are independent and use union semantics when both are selected, while Favorites and search continue to intersect with the result.

Each due occurrence creates one persistent in-app Notice, keyed uniquely as `interval:<ruleId>:<dueEpochDay>`, before Android delivery is attempted. Notices have one-way read state and `PENDING`, `DELIVERED`, or `SUPPRESSED` delivery state. Opening a Notice, marking all Notices read, satisfying care, disabling or rescheduling the rule, or deleting its Plant cancels any displayed notification associated with that occurrence. Individual Notices are not deleted or returned to unread; Plant deletion cascades through rules to Notices.

Plants and Notices are peer bottom-navigation roots with independent state, and the bottom bar remains visible on nested destinations. Switching tabs preserves the destination stack of the tab being left, while selecting the already active tab clears only that tab's nested destinations and returns to its root list. Notification taps open the matching Notice detail under the Notices root, superseding D-009's earlier unspecified destination and the earlier plan to open Plant detail directly. Notice detail marks the item read and offers an explicit action to navigate to the Plant. That cross-root action clears the source Notice detail, so selecting Notices after returning to the Plants root opens the inbox instead of restoring the previous detail.

WorkManager runs unique periodic evaluation approximately once per day around 09:00 local time. This is a best-effort morning cadence, not an exact-time promise. A rule saved after that day's evaluation waits until the next daily run. Stable occurrence-specific notification identifiers and the unique Notice key make retries idempotent.

On Android versions requiring notification permission, the app explains and requests it contextually after the first active rule is saved. The rule and in-app Notice behavior do not depend on permission. An occurrence evaluated without permission becomes `SUPPRESSED`; granting permission later does not deliver it retroactively. When rules are active but notifications are blocked, guidance and an application-settings action appear only in the Notices root.

This decision refines D-008 and D-009 without adding seasonal behavior. Annual windows, cross-year calculations, seasonal satisfaction, and seasonal notifications remain exclusively in Phase 5. Phase 4 implementation and local plus connected-emulator verification completed on 2026-10-11.

## D-023 — Room v7 annual seasonal care reuses the reminder pipeline

- **Date:** 2026-10-13
- **Status:** Accepted

Phase 5 adds `SEASONAL` reminder rows in Room schema v7. An annual season has a required month/day start and an optional inclusive month/day end. No end means one day; an end earlier than the start crosses into the following year. February 29 remains a valid stored annual date and resolves to February 28 in non-leap years. The non-destructive `6→7` migration preserves interval rules and Notices while making interval fields nullable and adding nullable seasonal fields.

For one Plant and care type, Off, Interval, and Season are mutually exclusive active modes. Interval and seasonal rows remain separate under `(plantId, careType, scheduleType)`, and switching modes retains the inactive configuration. The three care cards still save atomically. Plant inventory Upcoming and Overdue filters remain interval-only; Plant detail shows the current or next seasonal occurrence as upcoming, active, or satisfied.

Seasonal delivery deliberately reuses the existing daily WorkManager and persistent Notice pipeline. The unique key is `seasonal:<ruleId>:<startEpochDay>`. A late evaluation during an active unsatisfied window may create the one occurrence Notice; there is no end-of-window or repeat reminder. Matching care anywhere inside the inclusive window satisfies that occurrence and resolves its Notice. A pending delivery is suppressed rather than posted after its window ends. Notification permission, navigation, read state, cancellation, and Plant cascades remain shared with interval Notices.

This phase remains fully offline and does not add exact alarms, network services, authentication, analytics, or another architectural layer.

## D-024 — Annual care uses one day-or-period calendar field

- **Date:** 2026-10-13
- **Status:** Accepted

The configuration UI names the seasonal mode “Once a year” and presents its persisted start and optional end as one read-only Day or period field. Pressing the field or its calendar icon opens a range calendar: accepting only a start represents one annual day, while accepting start and end represents an inclusive annual period. The localized field value shows the resulting day or range without asking the user to reason about separate start/end controls.

This is a presentation refinement to D-023, not a data-model change. Room remains at schema v7, stores only month/day components, and continues to interpret an end before the start as a period crossing into the following year. Existing annual rules therefore require no migration.

## D-025 — Fixed Stay Leafy visual identity and local assets

- **Date:** 2026-10-14
- **Status:** Accepted

Phase 6 replaces the Android template palette with explicit light and dark Stay Leafy Material 3 schemes. The system light/dark setting remains authoritative, but Material You dynamic color is disabled: botanical green `#115129`, terracotta `#DF6645`, cream `#FBF8F1`, and dark botanical `#0D1712` must remain stable identity anchors. Upcoming, due-today, overdue, unread, error, and container roles use named semantic colors with non-color labels and WCAG AA text contrast.

Lora is bundled under the SIL Open Font License and used only for display headings; body text and controls retain the platform sans-serif. Shared Compose primitives provide care icons and state presentation, while Material 3 owns standard interaction behavior, dialogs, menus, sheets, calendar pickers, and motion. Screen state and callbacks remain outside these visual components.

The approved `design/brand/stay_leafy_master.svg` remains unchanged. `stay_leafy_clean.svg` is its nine-path production derivative. Android packages a VectorDrawable foreground, a flat cream background, an Android 13 monochrome layer, and generated density-specific legacy icons. UI icons are local vectors rather than emoji or an extended icon dependency. These decisions introduce no database, navigation-topology, reminder, or network change.

## D-026 — Reference-first screen composition

- **Date:** 2026-10-14
- **Status:** Accepted

The Phase 6 reference at `design/references/stay_leafy_ui_direction.png` is the primary composition specification, not merely a palette or mood board. Production screens therefore use compact image-led root rows, restrained zero-elevation tonal containers, a wide Plant-detail hero, grouped icon metadata, three flat care rows, contextual secondary actions, visible Plant identity in child editors, and bottom-pinned primary actions. Existing product behavior, localized content, accessibility semantics, root navigation, and immutable UI-state contracts still take precedence where illustrative mock content differs from the implemented application.

Lora remains reserved for the distinctive Plants root display heading. Screen app bars, entity names, form controls, and dense reading surfaces use the platform sans-serif hierarchy to preserve the compact character shown in the reference. The interval frequency is adjusted with a localized compact stepper while scheduling validation and persistence remain unchanged.

## D-027 — Botanical Calm V2 visual system and explicit interval start states

- **Date:** 2026-10-14
- **Status:** Accepted

The V2 screen specification in `design/StayLeafy_mockups_v2.svg` supersedes the earlier reference for production composition. The app uses the Botanical Calm pine, sage, clay, cream, paper, ink, and dark-surface tokens in explicit light and dark Material 3 schemes. Shared Compose tokens cover semantic colors, spacing, radii, controls, thumbnails, and the compact 64dp root navigation bar. Empty Plants and Notices states use reusable vector illustrations selected once per state appearance and retained across recompositions.

Plants keeps the existing two-root navigation and behavior while presenting creation through a FAB. The Settings icon is a visual affordance only; no Settings destination is introduced until its product behavior is defined. Notices derive the plant thumbnail from the existing Plants row through Room joins, without changing the persisted notice schema.

The care configuration UI models interval origin explicitly as `LatestCare` or `CustomDate`. Newly initialized interval forms use latest care when available. Stored manual due dates remain custom until the user selects `Use latest care instead`, preserving the scheduling rule that editing does not silently reinterpret persisted due dates. Add note keeps only back navigation, content, and Save.
