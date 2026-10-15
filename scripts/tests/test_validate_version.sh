#!/usr/bin/env bash

# Bash-only integration tests for scripts/validate_version.sh.
# Compatible with the Bash 3.2 shipped by macOS.

LC_ALL=C
export LC_ALL

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
SCRIPT="$SCRIPT_DIR/validate_version.sh"
TEST_ROOT=$(mktemp -d "/tmp/stayleafy-version-test.XXXXXX")
TEST_COUNT=0
FAILURE_COUNT=0
LAST_STATUS=0
LAST_OUTPUT=

cleanup() {
    rm -rf "$TEST_ROOT"
}
trap cleanup EXIT HUP INT TERM

record_failure() {
    printf 'FAIL: %s\n' "$1" >&2
    FAILURE_COUNT=$((FAILURE_COUNT + 1))
}

assert_status() {
    local expected="$1"
    local description="$2"
    TEST_COUNT=$((TEST_COUNT + 1))
    if [ "$LAST_STATUS" -ne "$expected" ]; then
        record_failure "$description (expected status $expected, got $LAST_STATUS; output: $LAST_OUTPUT)"
        return 1
    fi
    return 0
}

assert_output_contains() {
    local needle="$1"
    local description="$2"
    TEST_COUNT=$((TEST_COUNT + 1))
    case "$LAST_OUTPUT" in
        *"$needle"*) return 0 ;;
        *)
            record_failure "$description (missing '$needle'; output: $LAST_OUTPUT)"
            return 1
            ;;
    esac
}

assert_file_equals() {
    local path="$1"
    local expected="$2"
    local description="$3"
    local actual=
    TEST_COUNT=$((TEST_COUNT + 1))
    if [ -f "$path" ]; then
        actual=$(< "$path")
    fi
    if [ "$actual" != "$expected" ]; then
        record_failure "$description (expected '$expected', got '$actual')"
        return 1
    fi
    return 0
}

run_validator() {
    local directory="$1"
    shift
    LAST_OUTPUT=
    if LAST_OUTPUT=$(cd "$directory" && "$BASH" "$SCRIPT" "$@" 2>&1); then
        LAST_STATUS=0
    else
        LAST_STATUS=$?
    fi
}

write_file() {
    local path="$1"
    local content="$2"
    mkdir -p "$(dirname "$path")"
    printf '%s' "$content" > "$path"
}

git_in() {
    local directory="$1"
    shift
    git -C "$directory" "$@" >/dev/null
}

commit_current() {
    local directory="$1"
    git_in "$directory" add .
    git -C "$directory" -c user.name=Test -c user.email=test@example.com commit -m base >/dev/null
    git -C "$directory" rev-parse HEAD
}

new_repo() {
    local directory="$1"
    mkdir -p "$directory"
    git_in "$directory" init
}

test_valid_current_version() {
    local root="$TEST_ROOT/current-valid"
    mkdir -p "$root"
    write_file "$root/version.properties" $'# release\nVERSION_NAME=0.10.0\nVERSION_CODE=26\n'
    run_validator "$root"
    assert_status 0 'valid version.properties' || true
    assert_output_contains 'version 0.10.0 (26) is valid' 'valid version output' || true

    root="$TEST_ROOT/max-code"
    mkdir -p "$root"
    write_file "$root/version.properties" $'VERSION_NAME=0.10.0\nVERSION_CODE=2100000000\n'
    run_validator "$root"
    assert_status 0 'accepts the maximum Android versionCode' || true
}

test_property_rejections() {
    local case_name
    local content
    local root
    for case_name in missing-name missing-code duplicate-name duplicate-code unknown malformed spacing-name spacing-code bad-semver prerelease leading-zero-name bad-code zero-code oversized-code; do
        case "$case_name" in
            missing-name) content=$'VERSION_CODE=26\n' ;;
            missing-code) content=$'VERSION_NAME=0.10.0\n' ;;
            duplicate-name) content=$'VERSION_NAME=0.10.0\nVERSION_NAME=0.10.1\nVERSION_CODE=26\n' ;;
            duplicate-code) content=$'VERSION_NAME=0.10.0\nVERSION_CODE=26\nVERSION_CODE=27\n' ;;
            unknown) content=$'VERSION_NAME=0.10.0\nVERSION_CODE=26\nOTHER=value\n' ;;
            malformed) content=$'VERSION_NAME 0.10.0\nVERSION_CODE=26\n' ;;
            spacing-name) content=$'VERSION_NAME= 0.10.0\nVERSION_CODE=26\n' ;;
            spacing-code) content=$'VERSION_NAME=0.10.0\nVERSION_CODE= 26\n' ;;
            bad-semver) content=$'VERSION_NAME=1.2.3-alpha\nVERSION_CODE=26\n' ;;
            prerelease) content=$'VERSION_NAME=1.2.3+build\nVERSION_CODE=26\n' ;;
            leading-zero-name) content=$'VERSION_NAME=01.2.3\nVERSION_CODE=26\n' ;;
            bad-code) content=$'VERSION_NAME=0.10.0\nVERSION_CODE=1.5\n' ;;
            zero-code) content=$'VERSION_NAME=0.10.0\nVERSION_CODE=0\n' ;;
            oversized-code) content=$'VERSION_NAME=0.10.0\nVERSION_CODE=2100000001\n' ;;
        esac
        root="$TEST_ROOT/property-$case_name"
        mkdir -p "$root"
        write_file "$root/version.properties" "$content"
        run_validator "$root"
        assert_status 1 "rejects $case_name" || true
    done
}

test_cli_arguments_and_output() {
    local root="$TEST_ROOT/arguments"
    local output="$root/github-output.txt"
    local invalid_root="$TEST_ROOT/output-invalid"
    local invalid_output="$invalid_root/output.txt"
    mkdir -p "$root"
    write_file "$root/version.properties" $'VERSION_NAME=0.10.0\nVERSION_CODE=26\n'

    run_validator "$root" --base-ref deadbeef
    assert_status 2 'requires paired base-ref and title' || true
    assert_output_contains 'must be provided together' 'paired argument error' || true

    run_validator "$root" --title 'feat: add plants'
    assert_status 2 'rejects title without base-ref' || true

    run_validator "$root" --unknown
    assert_status 2 'rejects unknown arguments' || true

    run_validator "$root" --github-output "$output"
    assert_status 0 'writes GitHub output after successful validation' || true
    assert_file_equals "$output" $'version_name=0.10.0\nversion_code=26' 'GitHub output contents' || true

    mkdir -p "$invalid_root"
    write_file "$invalid_root/version.properties" $'VERSION_NAME=0.10\nVERSION_CODE=26\n'
    run_validator "$invalid_root" --github-output "$invalid_output"
    assert_status 1 'rejects invalid version before output' || true
    TEST_COUNT=$((TEST_COUNT + 1))
    if [ -e "$invalid_output" ]; then
        record_failure 'invalid validation created GitHub output'
    fi
}

test_cli_integration() {
    local case_name base_name base_code current_name current_code title root base_ref
    for case_name in major minor patch; do
        case "$case_name" in
            major) base_name=1.2.3; base_code=40; current_name=2.0.0; title='fix(storage)!: break API' ;;
            minor) base_name=1.2.3; base_code=40; current_name=1.3.0; title='feat(ui): add feature' ;;
            patch) base_name=1.2.3; base_code=40; current_name=1.2.4; title='refactor(core): tidy code' ;;
        esac
        current_code=41
        root="$TEST_ROOT/integration-$case_name"
        new_repo "$root"
        write_file "$root/version.properties" "$(printf 'VERSION_NAME=%s\nVERSION_CODE=%s\n' "$base_name" "$base_code")"
        base_ref=$(commit_current "$root")
        write_file "$root/version.properties" "$(printf 'VERSION_NAME=%s\nVERSION_CODE=%s\n' "$current_name" "$current_code")"
        run_validator "$root" --base-ref "$base_ref" --title "$title"
        assert_status 0 "validates $case_name SemVer transition" || true
        if [ "$case_name" = major ]; then
            run_validator "$root" --base-ref "$base_ref" --title "$title" --github-output "$root/github-output.txt"
            assert_status 0 'writes the current version after base validation' || true
            assert_output_contains 'version 2.0.0 (41) is valid' 'current version success message after base validation' || true
            assert_file_equals "$root/github-output.txt" $'version_name=2.0.0\nversion_code=41' 'current version GitHub output after base validation' || true
        fi
    done

    local all_type
    for all_type in fix refactor perf docs test build ci chore style revert; do
        root="$TEST_ROOT/type-$all_type"
        new_repo "$root"
        write_file "$root/version.properties" $'VERSION_NAME=1.2.3\nVERSION_CODE=7\n'
        base_ref=$(commit_current "$root")
        write_file "$root/version.properties" $'VERSION_NAME=1.2.4\nVERSION_CODE=8\n'
        run_validator "$root" --base-ref "$base_ref" --title "$all_type(scope): update"
        assert_status 0 "accepts Conventional type $all_type" || true
    done

    local invalid_title
    root="$TEST_ROOT/invalid-title"
    new_repo "$root"
    write_file "$root/version.properties" $'VERSION_NAME=1.2.3\nVERSION_CODE=7\n'
    base_ref=$(commit_current "$root")
    write_file "$root/version.properties" $'VERSION_NAME=1.2.4\nVERSION_CODE=8\n'
    for invalid_title in 'feature: update' 'Feat: update' 'feat(): update' 'feat(ui) update' 'feat:  update' 'feat:'; do
        run_validator "$root" --base-ref "$base_ref" --title "$invalid_title"
        assert_status 1 "rejects invalid title $invalid_title" || true
    done

    root="$TEST_ROOT/wrong-semver-jump"
    new_repo "$root"
    write_file "$root/version.properties" $'VERSION_NAME=1.2.3\nVERSION_CODE=7\n'
    base_ref=$(commit_current "$root")
    write_file "$root/version.properties" $'VERSION_NAME=1.3.0\nVERSION_CODE=8\n'
    run_validator "$root" --base-ref "$base_ref" --title 'fix: update'
    assert_status 1 'rejects a wrong SemVer jump with the correct versionCode' || true

    local injection_marker="$TEST_ROOT/title-injection-marker"
    local injection_title="fix: update \$(touch $injection_marker)"
    run_validator "$root" --base-ref "$base_ref" --title "$injection_title"
    assert_status 1 'rejects command substitution text in a title' || true
    TEST_COUNT=$((TEST_COUNT + 1))
    if [ -e "$injection_marker" ]; then
        record_failure 'title command substitution was executed'
    fi

    local wrong_code
    for wrong_code in 7 6 9; do
        root="$TEST_ROOT/wrong-code-$wrong_code"
        new_repo "$root"
        write_file "$root/version.properties" $'VERSION_NAME=1.2.3\nVERSION_CODE=7\n'
        base_ref=$(commit_current "$root")
        write_file "$root/version.properties" "$(printf 'VERSION_NAME=1.2.4\nVERSION_CODE=%s\n' "$wrong_code")"
        run_validator "$root" --base-ref "$base_ref" --title 'fix: update'
        assert_status 1 "rejects independent wrong versionCode $wrong_code" || true
    done
}

test_bootstrap_and_base_failures() {
    local root base_ref

    root="$TEST_ROOT/bootstrap"
    new_repo "$root"
    write_file "$root/app/build.gradle.kts" $'defaultConfig {\n    versionCode = 25\n    versionName = "0.9.3"\n}\n'
    base_ref=$(commit_current "$root")
    write_file "$root/version.properties" $'VERSION_NAME=0.10.0\nVERSION_CODE=26\n'
    run_validator "$root" --base-ref "$base_ref" --title 'feat: add release'
    assert_status 0 'uses strict legacy Gradle fallback for missing base properties' || true

    root="$TEST_ROOT/base-malformed"
    new_repo "$root"
    write_file "$root/version.properties" $'VERSION_NAME=0.9.3\nVERSION_NAME=0.9.4\nVERSION_CODE=25\n'
    write_file "$root/app/build.gradle.kts" $'versionCode = 25\nversionName = "0.9.3"\n'
    base_ref=$(commit_current "$root")
    write_file "$root/version.properties" $'VERSION_NAME=0.10.0\nVERSION_CODE=26\n'
    run_validator "$root" --base-ref "$base_ref" --title 'feat: add release'
    assert_status 1 'does not fall back when base properties are malformed' || true

    root="$TEST_ROOT/base-missing"
    new_repo "$root"
    write_file "$root/README" 'base without version metadata
'
    base_ref=$(commit_current "$root")
    write_file "$root/version.properties" $'VERSION_NAME=0.10.0\nVERSION_CODE=26\n'
    run_validator "$root" --base-ref "$base_ref" --title 'feat: add release'
    assert_status 1 'rejects missing base metadata without fallback' || true

    root="$TEST_ROOT/current-missing"
    mkdir -p "$root"
    run_validator "$root"
    assert_status 1 'rejects missing current version.properties' || true
}

test_crlf_and_large_semver() {
    local root base_ref
    root="$TEST_ROOT/crlf"
    new_repo "$root"
    write_file "$root/app/build.gradle.kts" $'defaultConfig {\r\n    versionCode = 25\r\n    versionName = "0.9.3"\r\n}\r\n'
    base_ref=$(commit_current "$root")
    write_file "$root/version.properties" $'VERSION_NAME=0.10.0\r\nVERSION_CODE=26\r\n'
    run_validator "$root" --base-ref "$base_ref" --title 'feat: add release'
    assert_status 0 'accepts CRLF accepted by Gradle readLines' || true

    root="$TEST_ROOT/large-semver"
    new_repo "$root"
    write_file "$root/version.properties" $'VERSION_NAME=999999999999999999999999999999.9.9\nVERSION_CODE=7\n'
    base_ref=$(commit_current "$root")
    write_file "$root/version.properties" $'VERSION_NAME=1000000000000000000000000000000.0.0\nVERSION_CODE=8\n'
    run_validator "$root" --base-ref "$base_ref" --title 'fix!: break API'
    assert_status 0 'increments large SemVer components without numeric overflow' || true
}

test_malicious_values_are_data() {
    local root output marker base_ref
    root="$TEST_ROOT/malicious"
    output="$root/output.txt"
    marker="$TEST_ROOT/should-not-exist"
    mkdir -p "$root"
    write_file "$root/version.properties" $'VERSION_NAME=0.10.0\nVERSION_CODE=26\n'
    printf '%s\n' "\$(touch $marker)" >> "$root/version.properties"
    run_validator "$root" --github-output "$output"
    assert_status 1 'rejects malicious property text' || true
    TEST_COUNT=$((TEST_COUNT + 1))
    if [ -e "$marker" ]; then
        record_failure 'malicious property text was executed'
    fi

    root="$TEST_ROOT/malicious-base"
    new_repo "$root"
    write_file "$root/version.properties" $'VERSION_NAME=0.9.3\nVERSION_CODE=25\n'
    printf '%s\n' "\$(touch $marker)" >> "$root/version.properties"
    base_ref=$(commit_current "$root")
    write_file "$root/version.properties" $'VERSION_NAME=0.10.0\nVERSION_CODE=26\n'
    run_validator "$root" --base-ref "$base_ref" --title 'feat: add release'
    assert_status 1 'rejects malicious base property text' || true
    TEST_COUNT=$((TEST_COUNT + 1))
    if [ -e "$marker" ]; then
        record_failure 'malicious base property text was executed'
    fi
}

test_valid_current_version
test_property_rejections
test_cli_arguments_and_output
test_cli_integration
test_bootstrap_and_base_failures
test_crlf_and_large_semver
test_malicious_values_are_data

if [ "$FAILURE_COUNT" -ne 0 ]; then
    printf '%s tests run, %s failures\n' "$TEST_COUNT" "$FAILURE_COUNT" >&2
    exit 1
fi
printf '%s tests passed\n' "$TEST_COUNT"
