# Reminder behavior

## Scope

Stay Leafy provides local, device-generated reminders. It does not use Firebase Cloud Messaging or any other push-notification service.

Two scheduling models are supported: interval rules and annual seasonal rules.

## Interval rules

An interval rule expresses care every `N` days after the most recent completion.

Example:

```text
Plant: Ficus Living Room
Care: Watering
Interval: 15 days
Completed: October 10
Next due date: October 25
```

### Creation

When creating a rule, the user chooses a positive interval and either:

- a starting due date, or
- a previously recorded matching care event from which the next date is derived.

The UI must not silently guess a historical completion date.

### Completion

Marking matching care as complete performs one repository transaction:

1. Insert the `CareEvent`.
2. Calculate `completionLocalDate + intervalDays`.
3. Store the resulting `nextDueEpochDay`.
4. Clear the previous occurrence's notification deduplication key.

The UI immediately reflects the next due date from Room. A lightweight confirmation may state the next date, but the app does not show an immediate system notification for the action just completed.

### Interval changes

When the user changes `N`, the next due date is recalculated from the latest matching care event. If no matching event exists, the user selects a new starting date.

Editing or deleting the latest matching care event requires recomputing the rule. The user should receive confirmation when this changes a due date.

## Seasonal rules

A seasonal rule represents an annual window in which a care action is appropriate.

Example:

```text
Plant: Hydrangea
Care: Pruning
Season: February 1 through March 31
Notification: "The ideal pruning season for Hydrangea has started."
```

The end date is optional. Without an end date, the rule is a yearly start reminder rather than a bounded window.

Seasonal windows may cross the year boundary. The active occurrence is identified by the year in which its start date occurs.

Completing matching care while the window is active marks that seasonal occurrence as satisfied. The MVP sends one notification at season start and does not repeatedly notify during the same occurrence.

## Background execution

The initial scheduler uses unique periodic WorkManager work to evaluate reminders approximately once per day.

Properties:

- Room remains the source of truth.
- The worker can be recreated safely and must not create rules.
- Work is unique to avoid parallel duplicate evaluators.
- The worker queries enabled rules that are due for the device's current local date.
- Delivery is best effort and not guaranteed at an exact clock time.
- WorkManager persists scheduled work across normal process death and device reboots.

A user-configurable preferred notification time is an open product decision. Until decided, the implementation may run in a broad morning window rather than promise an exact time.

## Notification permission

On Android versions requiring runtime notification permission:

- Do not request permission on first launch without context.
- Explain the benefit when the user enables their first reminder.
- A denied permission leaves rules enabled and visible, but the UI communicates that notifications cannot be displayed.
- The application remains fully usable without permission.

## Notification content

Interval example:

```text
Watering due
Ficus Living Room is due for watering today.
```

Seasonal example:

```text
Pruning season has started
The ideal pruning season for Hydrangea has started.
```

Notification text is localized. The title identifies the action; the body identifies the plant and timing.

Tapping a notification opens the related plant detail. Notification identifiers must be stable enough that updating one rule does not overwrite an unrelated plant's notification.

## Deduplication

Before notifying, the worker derives an occurrence key:

- Interval: rule ID plus due epoch day.
- Seasonal: rule ID plus start year.

If the key equals `lastNotifiedOccurrence`, no new notification is shown. After successful delivery, the key is persisted. Rescheduling or satisfying an occurrence updates or clears the key as appropriate.

The worker must remain safe when executed more than once for the same date.

## Overdue behavior

An interval rule remains overdue until matching care is recorded, the due date is edited, or the rule is disabled. The MVP sends one system notification per due occurrence rather than a notification every day. The UI may continue showing the overdue state.

## Time and timezone behavior

- Due care is date-based, not instant-based.
- Care completion records an exact timestamp and derives a local date using the current device timezone.
- Changing timezone does not rewrite historical event timestamps.
- The next daily worker evaluation uses the new local date.
- Seasonal month/day values remain unchanged across timezone changes.

## Exact alarms

Exact alarms are intentionally excluded. Plant-care reminders tolerate delivery within a broader window, and exact alarms would introduce additional permissions and battery considerations. Introducing exact delivery requires a recorded architecture decision and a demonstrated user need.

## Test cases

At minimum, tests cover:

- Positive interval validation.
- Completion date plus interval calculation.
- Month and year boundaries.
- Leap-year behavior.
- Editing and deleting the latest care event.
- Seasonal start and end dates.
- Cross-year seasonal windows.
- One notification per occurrence.
- Disabled rules.
- Process-safe repeated worker execution.
- English and Spanish notification text.
