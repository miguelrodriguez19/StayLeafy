# I-003 — Keep notification status readable in Settings

| Field      | Value       |
|------------|-------------|
| Date Added | 2026-10-16  |
| Status     | Done        |
| Target     | Post-MVP    |
| Type       | Bug         |

## Goal

Keep notification information, status, and the settings/permission action readable when permission
is revoked.

## Initial scope

- Reported visual bug: granting notification permission and later revoking it in Android Settings
  produces a long red status chip that squeezes the information column.
- Bound the chip's width and allow its text to wrap.
- Verify compact widths, large font sizes, and English/Spanish in both themes.

## Accepted design

- Every permission row keeps its icon and title together in a title header, with the icon vertically
  centered against the title line.
- Supporting text appears below the header, inset to the title start. Notification status and its
  action stay together beside the information in measured 2:1 information/trailing columns when
  the title fits and each trailing label fits within two lines. A second line in either the chip
  or the button does not change the layout. If either needs a third line, the complete status/action
  block moves below the full-width information and is centered; the pair is never split.
- The status pill wraps without truncation and the action keeps a minimum `48.dp` touch target
  while allowing its label to wrap. Camera and photo rows keep their existing content and behavior.
- The fit decision uses the actual constrained row width, density, locale, font scale, and measured
  `labelSmall`/`labelMedium` text layouts. Supporting body text may wrap without forcing the stacked
  layout; there is no arbitrary width or font-scale breakpoint.

## Acceptance coverage

- Exercise blocked notification status and system-settings action at 320.dp and 360.dp screen
  widths, plus a normal 411.dp width. Verify one-line chips with two-line buttons, two-line chips
  with two-line buttons, and the pair moving below when either would need a third line.
- Verify English and Spanish in light and dark themes at font scales `1.0`, `1.5`, and `2.0`.
- Verify title/icon alignment for notification, camera, and photo rows, allowed-to-blocked
  recomposition, the other actionable permission state, and 48.dp action semantics without overlap
  or clipped status text.

## Verification — 2026-10-26

- `ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest` passed all 150 connected tests
  on the Pixel 8a API 36 emulator, with 0 failures and 0 skips. This includes 19 Settings tests,
  the 24-case blocked-status matrix, and explicit one-/two-line chip and button combinations.
- `./gradlew test lint assembleDebug` passed with 179 JVM tests, 0 failures, 2 expected skips, and
  no lint errors.
- `bash scripts/tests/run.sh` passed 63 version tests and 19 release-state tests. The version
  validator accepted `origin/main` with `fix(settings): keep notification status readable` and
  `1.0.1`/`29`.
- Manual Android verification at 360.dp confirmed a two-line chip and two-line button beside the
  information, a one-word allowed chip with a two-line settings button beside the information,
  and both controls moving below after permission was revoked because the blocked chip would
  need a third line in the side column. Notification, camera, and photo icons align to their
  titles. Captures are available under `app/build/reports/i003/two-lines/` after this verification.
