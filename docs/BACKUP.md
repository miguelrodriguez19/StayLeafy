# Local backup and restore

## User workflow

Open **Settings → Backup**. Export creates an unencrypted ZIP through Android's document picker. Choose a location you control and retain an independent copy outside the phone. Stay Leafy does not upload the archive itself; a document provider selected by the user may have its own network behavior. No storage permission, account, backend, scheduled backup, or cloud synchronization is added.

Restore first copies and validates the selected ZIP in private storage. The preview shows the source version, export date, and counts. Nothing in the current collection changes until the user explicitly confirms replacement. Restoring is **replace all**, not merge: plants, primary photos, notes, care history, reminder rules, Notices, and theme/language/reminder-time preferences are replaced together. Export the current collection first if it might be needed later.

The archive contains personal photos and text in readable form. Protect it like the original data. Checksums detect corruption; they do not authenticate an archive or provide encryption. Open only backups you trust. Android notification permission and other system settings are not restored.

Android Auto Backup and system device-to-device transfer are explicitly excluded. The manual ZIP is the supported transfer/recovery mechanism. Uninstalling or clearing app storage without an external backup loses the local collection.

## Archive format 1

The ZIP contains `manifest.json`, `data.json`, and zero or more `photos/<filename>` entries. The manifest records `formatVersion=1`, source app version/name and code, `roomSchemaVersion=7`, export epoch milliseconds, collection counts, and each data/photo entry's byte size and SHA-256. The JSON snapshot contains all five persisted collections and the three preferences. It is a logical snapshot, not a copied SQLite database or WorkManager database.

Stable IDs, favorites, dates, read/unread state, disabled schedules, and future interval/annual rules are retained. Search projections are rebuilt with the app's normalizer. Photos preserve their original bytes, including legacy non-square files; restore creates fresh unique managed filenames and updates relative references. It does not crop or re-encode photos.

Limits are 2 GiB for input and actual total uncompressed data, 10,002 ZIP entries, 8 MiB for the manifest, 32 MiB for data JSON, and 100 MiB per photo. Export/restore also require available private storage. Invalid paths, duplicate or unexpected entries, unsupported format/schema, invalid relationships/enums/schedules, missing photos, inconsistent counts, and checksum mismatches are rejected before replacement.

The archive excludes pending photo/crop files, cache, document URIs, permissions, Android notification instances, WorkManager internals, and navigation state.

## Replacement and interruption safety

Repository writes, photo reconciliation, preference updates, and reminder delivery share one operation gate. Export holds it while capturing a consistent private archive. Validation and the user confirmation step do not lock editing. The confirmed replacement is a non-cancellable critical operation; navigation is blocked until it completes or recovery is required.

Before mutation, an atomic recovery journal in `noBackupFilesDir` records the old logical dataset/preferences and the planned new photo paths. Existing photos are never overwritten or deleted before commit. Room replaces all five tables in one transaction, preferences are replaced in one DataStore update, and only then is the journal marked committed. A process restart rolls back an unfinished prepared journal, or finishes cleanup for a committed one. Recovery runs before normal screens, workers, and orphan cleanup. If recovery cannot finish, the app fails closed and offers retry rather than exposing a mixed collection.

After commit the app applies the restored language, cancels this installation's old Android notifications, and schedules unique daily evaluation at the restored preferred time. Imported `DELIVERED` and `SUPPRESSED` states remain terminal. Imported `PENDING` Notices due on or before the restore date become `SUPPRESSED`, without changing their read/unread state. Future pending Notices wait until due. Retained annual and interval rules continue to generate normal future occurrences; historical Android notifications are not replayed.

## Manual acceptance before v1.0.0

1. Export a collection with a photo, note, care history, favorite, interval rule, next-year fertilizing rule, and read/unread Notices. Keep the ZIP outside app storage.
2. Change the collection and preferences, prepare restore, and cancel. Confirm nothing changed.
3. Restore and confirm every collection, photo, inactive/future rule, theme, language, and reminder time. Confirm old Android notifications are absent.
4. Repeat with malformed/truncated/modified ZIPs and insufficient storage. Confirm existing data remains usable, or recovery blocks access until safely retried.
5. Check English/Spanish, light/dark, font scales 1.0/1.5/2.0, touch targets, TalkBack labels/actions, and the system document picker on the owner's device.
6. Complete the 24–48 hour device reminder/battery checklist in [TESTING.md](TESTING.md). Emulator tests do not establish vendor-specific background reliability.

Only after these owner-operated checks pass should a separate major-version PR publish `v1.0.0`. The Phase 8 implementation candidate uses `0.11.0`/`27`; no real signing material is needed for local backup tests.
