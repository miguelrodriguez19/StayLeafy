# Roadmap

The roadmap orders work by dependency and user value. It is not a commitment to calendar dates.

## Current status at a glance

- [x] Phase 0 — Foundation
- [x] Phase 1 — Plant inventory
- [x] Phase 2 — Primary photo and dated notes
- [x] Independent correction — Plant identity and unified plant screen
- [x] Phase 3 — Care history
- [x] Phase 4 — Interval reminders
- [x] Phase 5 — Seasonal care
- [x] Phase 6 — Visual identity and UI redesign
- [x] Phase 7 — Photo framing and personalization
- [ ] Phase 8 — Personal-use hardening

Current milestone: Phase 7 is complete. Phase 8A implementation is complete and its remote acceptance remains unchecked; the strict `quality / verify` ruleset context is confirmed, while the `release` environment is not yet created. Phase 8 remains open.

## Phase 0 — Foundation

- [x] Select the official Android single-module architecture template.
- [x] Customize the package to `com.miguelrodriguez19.stayleafy`.
- [x] Verify the customized template with local unit tests.
- [x] Rename the remaining `MyApplicationTheme` symbol to `StayLeafyTheme`.
- [x] Add and review project documentation.
- [x] Initialize a clean Git repository after template customization.
- [x] Verify `assembleDebug`, unit tests, lint, and one device launch.
- [x] Establish English and Spanish resource files.

## Phase 1 — Plant inventory

- [x] Replace the template Plant sample with the real Room Plant schema v2.
- [x] Implement the non-destructive Room migration from schema 1 to schema 2, preserving legacy rows.
- [x] Build the plant list, empty state, and loading/error states.
- [x] Build create and edit flows with validation.
- [x] Build plant detail.
- [x] Implement deletion confirmation and deletion of the Plant row.
- [x] Add favorites, literal substring search, and the favorites-only filter.
- [x] Add English and Spanish resources.
- [x] Add light and dark previews.

Phase 1 boundary: child tables and app-managed photo files are owned by later phases. Phase 2 owns notes and primary-photo cleanup, care-event cleanup is owned by Phase 3, and reminder-rule cleanup plus the care-status filter are owned by Phase 4.

Exit condition: Plant records persist across restarts, the main CRUD flow is covered by focused tests, and the v1-to-v2 migration preserves existing Plant rows.

## Phase 2 — Primary photo and dated notes

- [x] Add Room schema v3 with `PlantNote`, the `2→3` migration, and an indexed Plant foreign key with delete cascade.
- [x] Add camera capture through permissionless `TakePicture` and an app-owned `FileProvider` URI.
- [x] Add `PickVisualMedia(ImageOnly)` with the platform document fallback and no broad media permission.
- [x] Store primary images as app-managed files under `files/plant_photos/` and persist only the relative path in Room.
- [x] Display the primary photo or a deterministic placeholder in the plant list and detail screens.
- [x] Support replacing and removing the primary photo with compensation for failed Room updates.
- [x] Clean up the old photo on replacement and the plant photo on deletion; reconcile unreferenced stable files at startup.
- [x] Add, edit, list, and delete dated notes in newest-first order.
- [x] Add the notes cascade when a Plant is deleted.
- [x] Test cancellation, unavailable capture/picker handlers, URI read/write failures, and process recreation around photo selection.

Exit condition met: one stable primary image and multiple independent notes can be managed fully offline, with local, instrumented, and migration tests covering the Phase 2 boundaries.

At delivery, the Phase 2 boundary excluded multiple photos, image editing or compression, note attachments, care history, reminder rules, WorkManager, network, and authentication. Phase 7 later introduces only mandatory square cropping and JPEG encoding for the existing single-photo flow. Phase 3 owns care events and their cascade; Phase 4 owns reminder rules, WorkManager, notifications, and the care-status filter.

Historical photo policy: Phase 2 initially stored selected image bytes unchanged. Phase 7 supersedes that intake policy for photos added or replaced after its implementation; already stored photo files are not migrated or rewritten.

## Independent correction — Plant identity and unified plant screen

- [x] Raise Room from schema v3 to schema v4 while preserving plants, notes, photos, favorites, and timestamps.
- [x] Replace the redundant display/common-name pair with one required `name` field and one normalized name-search projection.
- [x] Discard stored common-name values during the explicit `3→4` migration; scientific names and all other supported data remain preserved.
- [x] Search literally and case-insensitively across `name` and scientific name only.
- [x] Use one shared create/view/edit plant screen rather than a separate creation form and detail form.
- [x] Stage a new plant's photo and favorite state before saving, keep notes out of creation, and use compensating file cleanup on failure.

Exit condition met: the current plant model and creation flow are coherent with the detail experience, and the v3-to-v4 migration is explicit and tested.

This correction is a separate completed change before Phase 3. It does not add care history, reminders, WorkManager, or the care-status filter.

## Phase 3 — Care history

- [x] Raise Room from schema v4 to schema v5 with an explicit `4→5` migration and preserve the complete `1→2→3→4→5` chain.
- [x] Add `CareEvent` rows for watering, pruning, and fertilizing with UUID, Plant foreign key, completion timestamp, optional note, and audit timestamps.
- [x] Normalize optional notes to trimmed `NULL` values, reject future completion timestamps using the injected clock, and keep `createdAt` stable when an event is corrected.
- [x] Add indexes on `(plantId, performedAt)` and `(plantId, careType, performedAt)` and read events by `performedAt DESC, id DESC`.
- [x] Keep watering, pruning, and fertilizing as three always-visible cells in plant detail, each showing only its latest date.
- [x] Record today with a short press and choose another completion date with a long press, with repeated-action protection.
- [x] Open a read-only bottom sheet for the selected care type, ordered newest first, and allow clearing only that type's history for the current plant after confirmation.
- [x] Add the `ON DELETE CASCADE` from Plant to care events and verify it together with plant deletion.
- [x] Add localized English and Spanish inline UI, date picker, history sheet, confirmation, previews, and loading/error/empty/content states.
- [x] Add migration, Room, repository, ViewModel, Compose, navigation, and connected-flow tests.

Phase 3 status: complete. Room v5, the full migration chain, care history, localized UI, and local and connected-device verification are implemented.

Exit condition: the user can reliably answer when each care action last occurred, record today or a forgotten date, inspect one type's history, and clear that history without affecting another plant or care type.

## Phase 4 — Interval reminders

Phase 4 is complete. It owns interval rules, next-due calculations, care-status filtering, a persistent in-app Notices inbox, bottom navigation, WorkManager evaluation, system notifications, and reminder/notice cleanup. Phase 3 does not schedule reminders or recompute due dates.

- [x] Add Room schema v6 with interval reminder rules and persistent read/unread Notices, preserving the complete non-destructive migration chain.
- [x] Add a dedicated care-configuration destination with watering, pruning, and fertilizing cards plus one atomic Save action.
- [x] Require a positive interval and an explicit source: latest matching care or a directly selected first due date.
- [x] Recalculate the next due date transactionally after matching care without allowing an older retroactive event to move it backwards.
- [x] Keep the current due date when the plant-scoped care history is cleared.
- [x] Display next-due, upcoming, and overdue states in plant detail and a compact most-urgent summary in inventory rows.
- [x] Add independent, combinable Upcoming and Overdue inventory filters; Upcoming covers today through seven days ahead and both selected filters use union semantics.
- [x] Add the reminder-rule and notice cascades when a Plant is deleted.
- [x] Add `Plants | Notices` bottom navigation, preserving independent root state and showing the unread count on Notices.
- [x] Add the Notices list, Notice detail, read-on-open behavior, Mark all as read, and navigation from a notice to its plant.
- [x] Add unique periodic WorkManager evaluation approximately daily, initially around 09:00 local time, with one persistent Notice and at most one system notification per occurrence.
- [x] Add contextual Android notification-permission education after the first active rule is saved. Denial keeps rules and Notices active, and granting permission later does not deliver previously suppressed occurrences.
- [x] Route notification taps through the Notices root to the matching Notice detail.
- [x] Add migration, date calculation, transactional rescheduling, deduplication, permission, inbox, scheduler, navigation, localization, and connected-device checks.
- [x] Complete `./gradlew test lint assembleDebug` and `./gradlew connectedDebugAndroidTest`.

Exit condition met: interval care works fully offline end to end; due occurrences create exactly one persistent Notice, never duplicate a system notification, expose correct read state and filters, and pass the local and connected-emulator verification gates.

## Phase 5 — Seasonal care

Phase 5 extends the existing offline reminder system with mutually exclusive annual seasonal schedules while retaining interval configuration for later reuse.

- [x] Add Room schema v7 with annual seasonal start and optional end dates, preserving the complete migration chain.
- [x] Support inclusive single-day, same-year, and cross-year seasons, with February 29 clamped to February 28 in non-leap years.
- [x] Keep interval and seasonal schedules mutually exclusive per Plant and care type while retaining the inactive configuration.
- [x] Mark a season occurrence satisfied by matching care recorded inside its window.
- [x] Deliver one persistent Notice and at most one localized system notification per seasonal occurrence, including late evaluation inside the active window.
- [x] Show upcoming, active, and satisfied seasonal state in Plant detail without changing the interval-only inventory filters.
- [x] Add the annual rule editor with one day-or-period calendar field, English/Spanish resources, deterministic previews, migration/unit/Room/ViewModel/Compose tests, and connected-device verification.

Exit condition met: annual rules such as a hydrangea pruning season recur correctly, deduplicate by occurrence, and are satisfied only by matching care inside the active window.

## Phase 6 — Visual identity and UI redesign

Phase 6 applies the approved Stay Leafy visual identity to every existing screen without changing product behavior, navigation topology, persistence, or reminder rules. The root navigation remains `Plants | Notices`, and the existing ViewModels, immutable UI states, repositories, and Room data remain the behavioral source of truth.

### Visual design reference

- [x] Use [`design/references/stay_leafy_ui_direction.png`](../design/references/stay_leafy_ui_direction.png) as the primary visual-direction reference for Phase 6.
- [x] Apply the V2 screen specification in [`design/StayLeafy_mockups_v2.svg`](../design/StayLeafy_mockups_v2.svg) as the current visual source for Plants, Notices, Plant details, Notice details, Configure care, Add note, and randomized empty states.
- [x] Treat the reference as guidance for hierarchy, botanical palette, warm neutral surfaces, typography character, spacing, card treatment, photography, icon weight, controls, and the overall light/dark visual atmosphere.
- [x] Use its six compositions—Plants, Notices, Plant details, Notice details, Configure care, and Add note—to guide the redesign of the corresponding implemented destinations and states.
- [x] Reconcile every referenced composition against the current application before implementation. Existing navigation, fields, actions, scheduling rules, state ownership, accessibility semantics, and localized copy take precedence over illustrative content in the mockup.
- [x] Do not infer unimplemented functionality from illustrative details such as sample filters, badges, dates, plant data, care summaries, buttons, or decorative copy. Any observable behavior not already documented must be proposed separately before it is built.
- [x] Preserve the reference image unchanged as a historical design input. Record later explorations or production-ready screen specifications as separate, clearly named files rather than overwriting it.

### Brand assets and launcher identity

- [x] Preserve the approved SVG master under `design/brand/stay_leafy_master.svg` as a design source, not as a packaged Android resource.
- [x] Clean the automatically traced SVG by removing residual micro-paths, simplifying redundant points, and preserving the approved cactus, flowers, pot, proportions, and colors.
- [x] Keep the unmodified master available for comparison and create derived, clearly named production artwork instead of overwriting the source.
- [x] Record the canonical brand palette and asset derivation rules so later changes remain reproducible.
- [x] Convert the cleaned artwork into an Android VectorDrawable foreground with an explicit adaptive-icon safe zone.
- [x] Replace the template launcher background with a flat branded background that preserves contrast across launcher masks.
- [x] Add adaptive and round launcher icons plus a monochrome Android themed icon.
- [x] Regenerate legacy density-specific launcher resources for devices below Android 8 without packaging the raw SVG in the application.
- [x] Verify the launcher icon under circle, square, rounded-square, squircle, and teardrop masks, including Android themed-icon rendering.

### Material 3 visual system

- [x] Replace the template purple palette with explicit Stay Leafy light and dark color schemes based on dark botanical green, warm neutral surfaces, sage accents, and restrained terracotta status accents.
- [x] Decide and record whether Material You dynamic color is disabled by default or offered as an intentional opt-in; it must not silently erase the approved brand identity.
- [x] Define semantic colors for primary actions, containers, borders, disabled states, unread Notices, upcoming care, due-today care, overdue care, success, warnings, and errors with sufficient contrast in both themes.
- [x] Define the typography scale, font weights, line heights, and truncation behavior for app bars, plant names, scientific names, metadata, statuses, labels, body text, and supporting text.
- [x] Define shared shape, spacing, elevation, divider, and motion conventions instead of scattering visual constants through screens.
- [x] Keep the system light/dark setting as the theme source and avoid a settings screen or per-app theme selector in this phase.

### Shared Compose components

- [x] Create reusable branded app bars, navigation items, cards, buttons, icon buttons, filter chips, badges, status labels, form fields, photo containers, list rows, section headers, and dividers.
- [x] Replace emoji navigation symbols with deterministic vector icons and preserve localized accessibility labels.
- [x] Create consistent loading, empty, filtered-empty, not-found, inline-error, retry, and disabled states.
- [x] Create consistent confirmation dialogs, date-picker entry points, menus, bottom sheets, and permission-education surfaces.
- [x] Keep composables stateless where practical and continue passing immutable state plus event lambdas upward.

### Plants flow

- [x] Redesign the Plants root while preserving literal search, Favorites, Upcoming, Overdue, Clear filters, and their existing combination semantics; creation now uses the visual-direction FAB and Settings remains a pending destination.
- [x] Redesign plant rows around the primary photo or deterministic placeholder, plant name, optional scientific name, location, favorite action, and compact most-urgent interval-care summary.
- [x] Redesign loading, empty inventory, filtered-empty, content, and error states in both themes.
- [x] Redesign the shared create/view/edit plant destination without splitting it into separate screens.
- [x] Redesign photo capture, photo selection, replacement, removal, staged-photo, busy, error, and retry states while preserving current platform contracts.
- [x] Redesign plant metadata fields, acquisition-date selection, favorite state, save/cancel behavior, unsaved-create confirmation, edit actions, and delete confirmation.
- [x] Redesign the Care history section with always-visible Watering, Pruning, and Fertilizing actions, latest dates, interval due states, seasonal states, long-press date behavior, histories, and scoped clearing.
- [x] Redesign Notes content, empty/error states, dated note cards, add/edit/delete actions, and the note editor destination.
- [x] Preserve the current order, semantics, and offline behavior of all plant interactions while improving hierarchy and scanability.

### Care configuration and Notices flow

- [x] Redesign care configuration for Watering, Pruning, and Fertilizing while preserving the mutually exclusive Off, Interval, and Once-a-year modes and the single atomic Save action.
- [x] Redesign interval source selection, validation, latest-care context, direct due-date selection, and the annual day-or-period calendar field without changing scheduling rules. The UI explicitly switches between latest care and a custom start date.
- [x] Redesign notification-permission education while preserving the rule that denial never disables in-app reminders or Notices.
- [x] Redesign the Notices root list, unread markers, unread badge, blocked-notification warning, Mark all as read action, loading, empty, error, and content states.
- [x] Redesign Notice detail while preserving read-on-open behavior, schedule metadata, and View plant navigation.
- [x] Preserve independent back stacks and state for the `Plants` and `Notices` root tabs.

### Localization, accessibility, and visual verification

- [x] Keep every user-visible string in Android resources and verify each redesigned surface in English and Spanish.
- [x] Add or update deterministic light and dark previews for all important content, loading, empty, error, not-found, dialog, sheet, and form states.
- [x] Verify layouts on compact and normal phone widths, long Spanish text, large font scales, edge-to-edge system bars, and software-keyboard interaction.
- [x] Verify minimum touch targets, contrast, focus order, screen-reader labels, selected states, icon semantics, and non-color status cues as part of the redesign; Phase 7 retains the final whole-app accessibility review.
- [x] Preserve or update Compose UI tests around semantics and behavior without coupling them unnecessarily to implementation-specific layout details.
- [x] Perform manual visual checks for launcher icon masks, light/dark themes, English/Spanish, photo and placeholder rendering, dialogs, sheets, and system-owned picker/calendar UI.

### Documentation and completion gates

- [x] Update `docs/PRODUCT.md` with the finalized palette, icon usage, and visual principles once implemented.
- [x] Record durable theme, dynamic-color, icon-resource, and reusable-component decisions in `docs/DECISIONS.md`.
- [x] Update `docs/TESTING.md` with the final visual verification matrix and any new deterministic UI coverage.
- [x] Update `docs/REQUIREMENTS.md` only if the redesign changes observable behavior; do not describe visual plans as implemented behavior.
- [x] Keep `README.md` limited to project status, verified setup, and links to the detailed design documentation.
- [x] Complete `./gradlew test`, `./gradlew lint`, and `./gradlew assembleDebug`.
- [x] Complete `./gradlew connectedDebugAndroidTest` when an emulator or device is available.

Phase 6 boundary: this phase does not add root tabs, a dashboard, a calendar overview, a settings destination, new persisted fields, care types, reminder behavior, database migrations, authentication, network services, or cloud functionality. It does not move state or business logic into composables.

Phase 6 status: complete. The fixed light/dark brand system, launcher assets, vector iconography, redesigned screens, localized semantics, visual checks, and automated regression coverage are implemented.

Reference-fidelity correction: complete. The production compositions now follow the V2 specification directly: compact image-led inventory rows, botanical light/dark surfaces, randomized reusable empty illustrations, a photo-first Plant detail with flat grouped sections, plant thumbnails in Notices, Plant context in care/note editors, compact interval controls, and bottom-pinned primary actions.

Exit condition met: every currently implemented Stay Leafy flow uses the approved visual identity consistently in English and Spanish and in light and dark themes; the launcher icon renders correctly across supported Android icon modes; existing behavior remains covered; and the full local verification suite passes.

## Phase 7 — Photo framing and personalization

Status: complete. No Room schema change was required.

### Photo framing

- [x] Require an interactive 1:1 crop after every new camera capture or photo-picker selection, using local uCrop with zoom and pan.
- [x] Store each newly accepted crop as a square JPEG (quality 92, up to 2048×2048 without upscaling) in the existing app-managed photo directory; Room continues to store only its stable relative path.
- [x] Keep create-flow crops staged until plant save. Cancellation, invalid crop output, and storage/database failures leave the prior stable or staged photo intact and clean up temporary files.
- [x] Apply this policy only to photos added or replaced after the feature ships. Leave existing files and Room references untouched.

### Settings and preferences

- [x] Replace the Plants gear placeholder with a Settings destination while retaining the existing Plants/Notices roots and bottom navigation.
- [x] Offer theme choices System, Light, and Dark, defaulting to System while preserving the fixed Stay Leafy palette and disabled dynamic color.
- [x] Offer app language choices System, English, and Spanish; System is the default.
- [x] Offer a configurable local reminder time, defaulting to 09:00. WorkManager continues to evaluate approximately daily; changing the preferred time reschedules the next occurrence without promising exact delivery or introducing exact alarms.
- [x] Show a capability-permissions summary: notification capability and its system settings action, plus clear information that camera capture and photo selection use system UI and need no broad app permission.
- [x] Show the installed app version and the product's local-only privacy facts (offline use, no account, cloud sync, or analytics).
- [x] Persist small app preferences outside Room with Preferences DataStore; do not add settings columns or tables to Room.
- [x] Localize the destination, preference labels, states, and capability summary in English and Spanish; cover light/dark themes and navigation restoration.

Phase 7 verification completed on 2026-10-14: the JVM suite, lint, debug assembly, focused photo and Settings tests, and the connected-emulator suite cover crop validation and cleanup, preference persistence, theme/language choices, configurable reminder scheduling, Settings navigation, localization, and both theme previews. Physical-device reminder timing remains a Phase 8 reliability check because WorkManager delivery is deliberately best effort.

Exit condition met: newly accepted photos use the square crop contract without rewriting legacy files, personalization persists and applies locally, reminder work is rescheduled around the preferred time, and the feature remains offline and single-module.

## Phase 8 — Personal-use hardening

- [x] Phase 8A — Implement exactly two GitHub Actions workflows named `Pull Request Quality` and `Release`, with the required `quality / verify` job, version policy, signed-build preflight, idempotent release recovery, and local verification contract. See [`docs/RELEASING.md`](RELEASING.md).
- [ ] Phase 8A acceptance — Open a test PR and confirm the exact `quality / verify` check, including a failing SemVer/versionCode jump.
- [ ] Phase 8A — Configure the `release` environment and its four secrets before the first publishable merge.
- [ ] Phase 8A — Confirm the first publishable merge creates `v0.10.0` with a signed APK, SHA-256 sidecar, and published GitHub Release; confirm `workflow_dispatch` recovers the same main SHA without overwriting a published release.
- [ ] Complete Spanish localization.
- [ ] Review accessibility basics despite the personal-use scope.
- [ ] Add export and restore design.
- [ ] Add database migration tests before schema version changes.
- [ ] Review battery behavior and notification reliability on the owner's device.
- [ ] Produce a signed personal release build.

Phase 8 begins after Phase 7 is verified complete. Phase 8A implementation is complete, but its remote acceptance, environment configuration, and first publishable merge remain unchecked user-operated validation; this does not complete Phase 8. Physical-device reminder reliability remains a Phase 8 check; the configurable Phase 7 time remains best effort.

## Later possibilities

- Multiple photos per plant
- User-defined care types
- Plant groups or rooms
- Calendar overview
- Home-screen widget

These items require explicit prioritization before implementation.
