# Stay Leafy release runbook

This runbook covers the Phase 8A signed-release contract. It is documentation for the user-operated signing ceremony and GitHub setup. It does not create a real key, configure secrets, publish a release, or mark remote acceptance as complete.

The first publishable merge is intended to produce `v0.10.0` from `VERSION_NAME=0.10.0` and `VERSION_CODE=26` in `version.properties`. The legacy `0.9.3`/`25` Gradle pair is the bootstrap base used for the first version comparison. `version.properties` remains the sole version source after that transition. Only simple `major.minor.patch` SemVer is accepted; prereleases and build metadata are rejected.

The Phase 8A implementation has exactly two workflows for this contract:

- `Pull Request Quality` runs for every PR targeting `main` and exposes the required check `quality / verify`. It runs `git diff --check`, version-validator tests, the SemVer/versionCode comparison against the PR base SHA, and `./gradlew --no-daemon test lint assembleDebug`. It uses JDK 17, Gradle caching, `contents: read`, PR concurrency cancellation, SHA-pinned actions, and no secrets or emulator tests. Test and lint reports are uploaded when the job fails.
- `Release` runs after each push to `main` and through `workflow_dispatch`. It has one protected environment `release` job, global concurrency without cancellation, and `contents: write`. It performs all preflight checks before changing tags or releases.

## One-time signing-key ceremony

Perform this on a trusted local machine outside the repository. Choose a long unique password and keep it in a password manager. The commands below prompt without putting the password in the command line, shell history, process listings, or logs. Do not paste a real password into this document, a commit, or a CI log.

Create a private directory outside the repository, then generate the key once. Replace the example path with a private backup location you control; do not use a repository path:

```bash
umask 077
mkdir -p "$HOME/private/stayleafy-signing"
chmod 700 "$HOME/private/stayleafy-signing"
keytool -genkeypair \
  -v \
  -keystore "$HOME/private/stayleafy-signing/stayleafy-release.keystore" \
  -alias stayleafy-release \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -storetype PKCS12 \
  -dname "CN=Stay Leafy Release, OU=Stay Leafy, O=Personal, C=ES"
chmod 600 "$HOME/private/stayleafy-signing/stayleafy-release.keystore"
```

With `PKCS12`, `keytool` normally uses the same password for the keystore and the private key; it does not provide a distinct key-password prompt in the usual flow. Set `STAYLEAFY_RELEASE_KEY_PASSWORD` equal to `STAYLEAFY_RELEASE_STORE_PASSWORD` for this keystore. If independent store and key passwords are required instead, change the example to `-storetype JKS` and follow its separate prompts; Gradle autodetects JKS from the keystore, so no separate Gradle configuration change is needed. Use a strong value, confirm the alias, and record it in the password manager. Back up the keystore and the password record using at least one independent secure backup. Retain the original and backups permanently: losing the key prevents future updates to the same Android application identity. Never commit the file or place it under the repository.

Before configuring GitHub, inspect the keystore locally without exposing passwords:

```bash
keytool -list -v \
  -keystore "$HOME/private/stayleafy-signing/stayleafy-release.keystore" \
  -alias stayleafy-release
```

The real keystore must be converted to Base64 without printing the encoded value in a terminal, transcript, or log. Use a private temporary file with mode `600`, then use that file as input to your secret-management command. The exact command differs by operating system; the following macOS example writes only to a restricted temporary file and leaves no Base64 output on stdout:

```bash
encoded_file="$(mktemp "$HOME/private/stayleafy-signing/stayleafy-release-base64.XXXXXX")"
chmod 600 "$encoded_file"
base64 < "$HOME/private/stayleafy-signing/stayleafy-release.keystore" | tr -d '\n' > "$encoded_file"
chmod 600 "$encoded_file"
```

Review the file destination and permissions, then remove the temporary Base64 file after the secret has been entered successfully. Do not print it, add it to shell history, paste it into chat, or include it in an issue, pull request, artifact, or log. If using `gh secret set`, pass the file through stdin only after manually selecting the correct repository and environment, and verify the command's target before execution:

```bash
gh secret set STAYLEAFY_RELEASE_KEYSTORE_BASE64 --env release < "$encoded_file"
```

The four GitHub secrets are environment-only secrets on the `release` environment:

- `STAYLEAFY_RELEASE_KEYSTORE_BASE64`
- `STAYLEAFY_RELEASE_STORE_PASSWORD`
- `STAYLEAFY_RELEASE_KEY_ALIAS`
- `STAYLEAFY_RELEASE_KEY_PASSWORD`

Enter the three text values through the GitHub UI or an equivalent secret-safe prompt. Keep the keystore secret and both passwords out of repository-level secrets. The Android build's local environment names are different for the file path: `STAYLEAFY_RELEASE_KEYSTORE_PATH`, `STAYLEAFY_RELEASE_STORE_PASSWORD`, `STAYLEAFY_RELEASE_KEY_ALIAS`, and `STAYLEAFY_RELEASE_KEY_PASSWORD`.

## GitHub environment setup

The strict repository ruleset already requires the literal check `quality / verify` for PRs into `main`, as confirmed by the repository ruleset read. The `release` environment does not exist yet. Manually create it in the repository settings, keep all four secrets on that environment, and configure deployment protection appropriate for the personal release process. The release workflow must be allowed to run only from `main`; the preflight also checks the selected ref and fails before tag or release mutation when it is not `main`. See GitHub's [required-check troubleshooting guidance](https://docs.github.com/en/pull-requests/how-tos/merge-and-close-pull-requests/troubleshooting-required-status-checks) and [secure use of Actions guidance](https://docs.github.com/en/actions/reference/security/secure-use).

The workflow has no path filters, so the required check is created for every eligible PR and does not remain waiting because a changed path skipped the workflow. Remote acceptance of the workflow run, the new environment, its secrets, and the first publishable merge remains unchecked.

## Version and PR title policy

The validator accepts Conventional Commit titles with an optional scope, for example `feat(settings): add language choice`. The type mapping is:

- `feat` increments the minor component.
- Any allowed type followed by `!` increments the major component.
- `fix`, `refactor`, `perf`, `docs`, `test`, `build`, `ci`, `chore`, `style`, and `revert` increment the patch component.

The title must be valid, the new version must be exactly the expected SemVer increment from the base SHA, and `versionCode` must be exactly one greater. Missing, duplicated, malformed, prerelease, or metadata-bearing version values fail. The first comparison uses the documented legacy base `0.9.3`/`25`; subsequent comparisons read `version.properties` from the base and PR commits.

Local validator checks are:

```bash
python3 -m unittest discover -s scripts/tests -v
python3 scripts/validate_version.py --base-ref SHA --title 'feat: ...'
```

Use the actual PR base SHA in place of `SHA`. The normal local application gate remains:

```bash
./gradlew test lint assembleDebug
```

## Release preflight and idempotency

The `Release` workflow selects one exact SHA from `main`, validates `version.properties`, verifies that all four environment secrets exist, decodes the Base64 keystore into `$RUNNER_TEMP` with restricted permissions, and verifies the keystore and alias. It must remove the temporary keystore in a cleanup step even after a failure. No release mutation occurs before these checks pass.

Tag and release handling follows these cases:

- If `v<VERSION_NAME>` is absent, create the lightweight tag at the selected `main` SHA.
- If it exists at that same SHA and the release is absent or a draft, continue recovery.
- If it exists at a different SHA, fail without moving or overwriting it.
- If the release is published and contains both expected artifacts for that version, finish successfully without republishing.
- If a published release is missing either expected artifact, fail for manual repair rather than altering the published release.
- A draft release is reusable only when it belongs to the same selected `main` SHA; a different-SHA draft fails.
- `workflow_dispatch` is recovery for the same `main` SHA and follows the same checks and idempotency rules.

The workflow creates or reuses a draft titled `Stay Leafy v<version>` with GitHub-generated notes. It runs the signed build with `--no-configuration-cache` and without Gradle cache restore/save, requires exactly one APK, verifies its certificate with `apksigner`, computes SHA-256, and names the artifacts `StayLeafy-<version>.apk` and `StayLeafy-<version>.apk.sha256`. It uploads both to the draft and publishes only after every verification succeeds. A missing or invalid signing configuration fails before a tag or release is created. The workflow follows GitHub's [secure use of Actions guidance](https://docs.github.com/en/actions/reference/security/secure-use).

## Local signed-build verification

Use an ephemeral keystore in a temporary directory to test the release build wiring. This key is for testing only and must never be reused as the real release key. Keep passwords in prompts or a secret-safe environment and delete the temporary directory after verification:

```bash
temporary_keystore_dir="$(mktemp -d)"
chmod 700 "$temporary_keystore_dir"
keytool -genkeypair \
  -keystore "$temporary_keystore_dir/test-release.keystore" \
  -alias test-release \
  -keyalg RSA \
  -keysize 2048 \
  -validity 1 \
  -storetype PKCS12 \
  -dname "CN=Stay Leafy Test, O=Stay Leafy, C=ES"
```

For this temporary `PKCS12` key, use the same prompted test password for the store and key password. Configure the local build only with the temporary path and that test password, then run the signed build with configuration cache disabled. Secrets used by signing must not be captured in the project configuration cache:

```bash
./gradlew --no-configuration-cache assembleRelease
```

Locate exactly one APK and verify the ephemeral build against the ephemeral test keystore's own certificate identity:

```bash
apksigner verify --verbose --print-certs path/to/StayLeafy-release.apk
keytool -list -v \
  -keystore "$temporary_keystore_dir/test-release.keystore" \
  -alias test-release
```

For a real APK downloaded from a GitHub Release, compare the `Signer #1 certificate DN` and certificate SHA-256 digest printed by `apksigner --print-certs` with the trusted certificate identity retained from the real release-key ceremony. A valid checksum alone does not prove that the APK was signed by the retained release key. Verify the real sidecar against the exact renamed APK with `shasum -a 256 -c StayLeafy-<version>.apk.sha256`; do not only compute a new digest. On Linux, `sha256sum -c StayLeafy-<version>.apk.sha256` is equivalent. Remove the temporary keystore directory and test APK after the ephemeral check. Never use the ephemeral certificate for a real release.

## Recovery and acceptance

If preflight fails because the environment is incomplete, configure the four `release` environment secrets manually and rerun `workflow_dispatch` on `main`. Before the first merge, create the environment and configure its secrets. The first publishable merge must confirm `v0.10.0`, the signed APK, checksum, and published release. A test PR must confirm the exact `quality / verify` check and that an invalid SemVer jump blocks merging. See [the testing record](TESTING.md) for completed checks; real-key publication and recovery remain pending manual acceptance.
