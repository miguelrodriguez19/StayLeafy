# Reminder behavior

## Scope and delivery status

Stay Leafy reminders are generated entirely on the device. They do not use Firebase Cloud Messaging, a backend, or any other push-notification service.

Phase 4 implements interval rules, due-state filtering, persistent in-app Notices, and best-effort Android notifications. Phase 5 adds annual seasonal windows to the same offline Room, Notice, WorkManager, and notification pipeline.

## Interval rules

An interval rule expresses care every `N` local calendar days. There is at most one interval rule for each Plant and care type.

```text
Plant: Ficus Living Room
Care: Watering
Interval: 15 days
Completed: October 10
Next due date: October 25
```

### Configuration

Plant detail opens one dedicated configuration screen containing watering, pruning, and fertilizing cards. Each card selects Off, Interval, or Season, and one Save action persists all three cards atomically. Interval and seasonal modes cannot both be active for the same Plant/care pair, but the inactive configuration is retained.

An enabled card requires a positive interval and an explicit source for its next occurrence:

- **Latest care** — use the newest matching `CareEvent` local date plus the interval. This choice is unavailable if no matching event exists.
- **Selected date** — use a directly selected first due date. Past dates are allowed and make the rule immediately overdue.

The UI never silently treats a manually selected due date as if it came from care history. When an existing rule is edited, its stored due date remains the default source unless the user explicitly selects Latest care.

A disabled existing rule retains its interval and due date but contributes no due state and creates no new Notice. A care type that has never been configured and remains disabled does not require a database row.

### Care completion and history clearing

Recording matching care performs one Room transaction:

1. Insert the `CareEvent`.
2. Find the newest matching completion after insertion.
3. Calculate `completionLocalDate + intervalDays`.
4. Store the resulting `nextDueEpochDay`.
5. Resolve the replaced occurrence by marking its Notice read, if present.

A retroactive event older than the latest matching event remains in history but never moves the next due date backwards. The UI reflects the committed Room state and does not generate an immediate notification for care just completed.

Clearing history remains constrained to the selected care type and current Plant. It deliberately preserves the current rule and `nextDueEpochDay`; no other Plant or care type is affected.

### Due states and plant filters

Enabled rules use the current local epoch day:

- **Overdue** — `nextDueEpochDay < today`.
- **Due today / Upcoming** — today through seven days after today, inclusive.
- **Later** — more than seven days after today.

Plant inventory exposes independent Upcoming and Overdue chips. Selecting both uses union semantics; search and Favorites continue to intersect with the selected care-state result. A plant row shows its most urgent active rule and `+N` when additional active rules exist.

## Persistent Notices

Every due interval occurrence creates one persistent in-app Notice before Android system delivery is attempted. The occurrence key is:

```text
interval:<ruleId>:<dueEpochDay>
```

The key is unique, so repeated evaluation returns the same Notice rather than creating duplicates. Notices store care type and dates, not translated titles or bodies; UI text is generated from the current Plant name and active locale.

An unsatisfied seasonal occurrence likewise creates one persistent Notice, with `dueEpochDay` representing its window start and schedule-specific localized wording. Interval and seasonal Notices share the same read, delivery, navigation, permission, and cascade behavior.

The Notices inbox is a root destination beside Plants in bottom navigation:

- Each root preserves its own navigation state.
- Bottom navigation remains visible on roots and nested detail, editor, and configuration destinations.
- Selecting the active root again clears only its nested destinations and returns to the inventory or Notices inbox; switching roots preserves the other root's stack.
- The Notices tab shows the unread count, visually capped at `99+`.
- Notices are ordered by `createdAt DESC, id DESC`; unread rows have a distinct indicator.
- Loading, error/retry, empty, and content states are localized.

Opening a Notice marks it read and cancels its displayed Android notification. The detail shows care type, Plant, due date, and generation time, and provides a separate action to select the Plants root and open that Plant. A missing Notice produces a localized not-found state.

Opening the Plant from Notice detail also removes that detail from the Notices stack. After returning to the Plants root, selecting Notices opens the inbox rather than restoring the previous Notice detail.

The inbox offers Mark all as read when unread Notices exist. This is a one-way operation and cancels associated displayed Android notifications. Individual Notices cannot be deleted or returned to unread.

Completing matching care, disabling a rule, or replacing its current occurrence also marks the previous Notice read and cancels its displayed notification. Deleting a Plant cascades through its rules to its Notices.

## Background evaluation

Unique periodic WorkManager work evaluates interval and seasonal reminders approximately once per day around 09:00 in the device's current local time.

- Room remains the source of truth.
- Work is unique so parallel evaluators are not scheduled.
- The worker reads enabled interval rules due on or before the current local date and enabled seasonal rules whose current annual window contains that date.
- WorkManager persists work across normal process death and device reboot.
- Delivery is best effort. The application does not promise an exact clock time or use exact alarms.
- A rule saved after the day's approximate 09:00 run is evaluated the following morning, even when its selected date is already due or overdue.
- Date evaluation is isolated from the Worker and accepts an injected clock and local zone for deterministic tests.

For each due occurrence, the worker inserts or obtains its unique Notice with delivery state `PENDING`, then resolves Android delivery:

- With notification capability available, post using a stable tag/ID and record `DELIVERED`.
- Without permission, record `SUPPRESSED` while keeping the Notice unread and visible.
- A retry of `PENDING` uses the same stable tag/ID, so an interruption after posting updates the same system notification rather than creating another one.

An overdue rule retains one occurrence and remains visible as overdue until matching care is recorded, its due date is changed, or the rule is disabled. It does not produce another Notice or another system notification on each daily run.

## Notification permission

On Android versions requiring runtime notification permission:

- Do not request permission on first launch.
- After the first active reminder rule is saved, show a contextual explanation and then the system prompt.
- Remember that the contextual explanation was shown so subsequent saves do not automatically repeat it.
- The saved rule remains active regardless of the permission result.
- When permission is blocked and at least one rule is active, show guidance only in the Notices root, with an action to open the application's notification settings.
- The application and in-app Notices remain fully usable without permission.

A due occurrence evaluated without permission is `SUPPRESSED`. Granting permission later applies only to future occurrences; the app never delivers suppressed notifications retroactively.

## Notification content and navigation

Example:

```text
Watering due
Ficus Living Room is due for watering.
```

Notification text is localized. The title identifies the care action, and the body identifies the Plant. Tapping it selects the Notices root and opens the matching Notice detail. Back navigation returns to the Notices inbox; opening the Plant requires the explicit action in Notice detail.

Stable occurrence-specific notification identifiers prevent one Plant or care type from replacing an unrelated notification. Marking the Notice read, resolving the occurrence, or deleting the Plant cancels that identifier.

## Time and timezone behavior

- Due care is date-based, not instant-based.
- Care completion records an exact timestamp and derives a local date using the current device timezone.
- Changing timezone does not rewrite historical event timestamps or stored due epoch days.
- The next worker evaluation uses the new current local date and aims for the next local morning window.
- Timestamps for rule audits, Notice generation, and Notice reads remain UTC epoch milliseconds.

## Seasonal rules

A seasonal rule defines an annual start month/day and an optional inclusive end month/day:

- Without an end, the occurrence lasts only on the start date.
- An end on or after the start creates a same-year inclusive window.
- An end before the start creates a window that crosses into the following year.
- February 29 resolves to February 28 in non-leap years.

The worker evaluates a seasonal rule only while its current window is active. A late first run during that window still creates the occurrence; no second reminder is generated before the end. Matching care already present anywhere inside the window satisfies it, and recording matching care later resolves the exact occurrence Notice and cancels its displayed notification. A stale `PENDING` delivery is suppressed rather than posted after its window closes.

The seasonal occurrence key is:

```text
seasonal:<ruleId>:<startEpochDay>
```

Plant detail shows the current or next window as upcoming, active, or satisfied. Inventory Upcoming and Overdue filters intentionally remain interval-only.

## Verification requirements

Reminder verification must cover at least:

- Positive interval validation and explicit due-source selection.
- Past first due dates, month/year boundaries, and leap years.
- Transactional completion-date calculation and retroactive-event protection.
- Preservation of the due date when scoped care history is cleared.
- Upcoming and Overdue boundaries and combined filter semantics.
- One Notice and at most one Android notification per occurrence across repeated workers.
- Recovery from `PENDING` delivery without duplicate system notifications.
- Permission granted, denied, and later granted without retroactive delivery.
- Read-on-open, Mark all as read, badge updates, notification cancellation, and Plant deletion cascades.
- Notification-to-Notice and Notice-to-Plant navigation.
- Approximately 09:00 scheduling without tests that wait for real wall-clock execution.
- English and Spanish text plus light and dark rendering.
- Same-year, one-day, cross-year, inclusive-boundary, and February 29 seasonal calculations.
- Seasonal satisfaction by matching care, late in-window evaluation, occurrence deduplication, and no delivery outside the window.
- Atomic and mutually exclusive Off/Interval/Season configuration with retained inactive settings.

These checks are covered by the Phase 4 and Phase 5 JVM, Room, Compose, navigation, and connected-emulator suites. Reliability on the owner's physical device remains a Phase 6 hardening item.
