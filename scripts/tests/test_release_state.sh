#!/usr/bin/env bash

set -u

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd -P)"
HELPER="$SCRIPT_DIR/../release_state.sh"
TEST_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/stayleafy-release-state.XXXXXX")"
trap 'rm -rf "$TEST_ROOT"' EXIT HUP INT TERM

MAIN_SHA='aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
OTHER_SHA='bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb'
TAG_OBJECT_SHA='cccccccccccccccccccccccccccccccccccccccc'
TAG_NAME='v0.10.0'
APK_NAME='StayLeafy-0.10.0.apk'
CHECKSUM_NAME='StayLeafy-0.10.0.apk.sha256'

TAG_FILE="$TEST_ROOT/tag.json"
TAG_OBJECT_FILE="$TEST_ROOT/tag-object.json"
RELEASE_FILE="$TEST_ROOT/release.json"
OUTPUT_FILE="$TEST_ROOT/github-output"
STDOUT_FILE="$TEST_ROOT/stdout"
STDERR_FILE="$TEST_ROOT/stderr"

PASS_COUNT=0
FAIL_COUNT=0

write_text() {
  printf '%s\n' "$2" > "$1"
}

write_missing() {
  write_text "$1" '{"missing":true}'
}

write_lightweight_tag() {
  jq -cn --arg sha "$1" '{ref:"refs/tags/v0.10.0",object:{type:"commit",sha:$sha}}' > "$TAG_FILE"
}

write_annotated_tag() {
  jq -cn --arg sha "$TAG_OBJECT_SHA" '{ref:"refs/tags/v0.10.0",object:{type:"tag",sha:$sha}}' > "$TAG_FILE"
  jq -cn --arg sha "$1" '{object:{type:"commit",sha:$sha}}' > "$TAG_OBJECT_FILE"
}

write_draft_release() {
  jq -cn --arg tag "$TAG_NAME" --arg target "$2" --argjson id "$1" \
    '{id:$id,tag_name:$tag,target_commitish:$target,draft:true,assets:[]}' > "$RELEASE_FILE"
}

write_published_release() {
  jq -cn --arg tag "$TAG_NAME" --arg target "$MAIN_SHA" --argjson id 123 \
    --arg apk "$APK_NAME" --arg checksum "$CHECKSUM_NAME" --arg apk_state "$1" --arg checksum_state "$2" \
    --argjson apk_size "$3" --argjson checksum_size "$4" \
    '{id:$id,tag_name:$tag,target_commitish:$target,draft:false,assets:[
      {name:$apk,state:$apk_state,size:$apk_size},
      {name:$checksum,state:$checksum_state,size:$checksum_size}
    ]}' > "$RELEASE_FILE"
}

prepare_missing_state() {
  write_missing "$TAG_FILE"
  rm -f "$TAG_OBJECT_FILE"
  write_missing "$RELEASE_FILE"
  rm -f "$OUTPUT_FILE" "$STDOUT_FILE" "$STDERR_FILE"
}

run_helper() {
  rm -f "$STDOUT_FILE" "$STDERR_FILE"
  RUN_STATUS=0
  "$BASH" "$HELPER" "$@" > "$STDOUT_FILE" 2> "$STDERR_FILE" || RUN_STATUS=$?
}

fail_assertion() {
  printf '  assertion failed: %s\n' "$1" >&2
  FAIL_COUNT=$((FAIL_COUNT + 1))
}

assert_status() {
  if [[ "$RUN_STATUS" != "$1" ]]; then
    fail_assertion "expected exit $1, got $RUN_STATUS"
  fi
}

assert_stdout_empty() {
  if [[ -s "$STDOUT_FILE" ]]; then
    fail_assertion 'expected no stdout'
  fi
}

assert_stderr_contains() {
  if ! grep -Fq -e "$1" "$STDERR_FILE"; then
    fail_assertion "stderr did not contain: $1"
  fi
}

assert_json_field() {
  local filter="$1"
  local expected="$2"
  local actual
  if ! actual="$(jq -r "$filter" "$STDOUT_FILE" 2>/dev/null)"; then
    fail_assertion "stdout is not valid JSON for $filter"
    return
  fi
  if [[ "$actual" != "$expected" ]]; then
    fail_assertion "$filter expected '$expected', got '$actual'"
  fi
}

assert_output_equals() {
  local expected="$1"
  local actual=''
  if [[ -f "$OUTPUT_FILE" ]]; then
    actual="$(<"$OUTPUT_FILE")"
  fi
  if [[ "$actual" != "$expected" ]]; then
    fail_assertion 'GitHub output contents differ'
  fi
}

assert_output_absent_or_equals() {
  local expected="$1"
  assert_output_equals "$expected"
}

test_missing_tag_and_release_create_both() {
  prepare_missing_state
  run_helper --tag-name "$TAG_NAME" --main-sha "$MAIN_SHA" --tag-file "$TAG_FILE" \
    --release-file "$RELEASE_FILE" --expected-asset "$APK_NAME" --expected-asset "$CHECKSUM_NAME"
  assert_status 0
  assert_json_field '.tag_action' 'create-tag'
  assert_json_field '.release_action' 'create-draft'
  assert_json_field '.release_id' 'null'
  assert_json_field '.tag_sha' 'null'
}

test_matching_tag_and_draft_are_reused() {
  write_lightweight_tag "$MAIN_SHA"
  write_draft_release 123 "$MAIN_SHA"
  run_helper --tag-name "$TAG_NAME" --main-sha "$MAIN_SHA" --tag-file "$TAG_FILE" \
    --release-file "$RELEASE_FILE" --expected-asset "$APK_NAME" --expected-asset "$CHECKSUM_NAME"
  assert_status 0
  assert_json_field '.tag_action' 'reuse-tag'
  assert_json_field '.release_action' 'reuse-draft'
  assert_json_field '.release_id' '123'
  assert_json_field '.tag_sha' "$MAIN_SHA"
}

test_mismatch_rejected_without_outputs() {
  write_lightweight_tag "$OTHER_SHA"
  write_missing "$RELEASE_FILE"
  write_text "$OUTPUT_FILE" 'existing=1'
  run_helper --tag-name "$TAG_NAME" --main-sha "$MAIN_SHA" --tag-file "$TAG_FILE" \
    --release-file "$RELEASE_FILE" --expected-asset "$APK_NAME" --github-output "$OUTPUT_FILE"
  assert_status 2
  assert_stdout_empty
  assert_stderr_contains 'points to'
  assert_output_absent_or_equals 'existing=1'
}

test_annotated_tag_resolves_one_level() {
  write_annotated_tag "$MAIN_SHA"
  write_draft_release 123 "$MAIN_SHA"
  run_helper --tag-name "$TAG_NAME" --main-sha "$MAIN_SHA" --tag-file "$TAG_FILE" \
    --tag-object-file "$TAG_OBJECT_FILE" --release-file "$RELEASE_FILE" \
    --expected-asset "$APK_NAME" --expected-asset "$CHECKSUM_NAME"
  assert_status 0
  assert_json_field '.tag_action' 'reuse-tag'
  assert_json_field '.tag_sha' "$MAIN_SHA"
}

test_annotated_tag_requires_resolvable_commit() {
  write_annotated_tag "$MAIN_SHA"
  jq -n '{object:{type:"tag",sha:"dddddddddddddddddddddddddddddddddddddddd"}}' > "$TAG_OBJECT_FILE"
  write_missing "$RELEASE_FILE"
  run_helper --tag-name "$TAG_NAME" --main-sha "$MAIN_SHA" --tag-file "$TAG_FILE" \
    --tag-object-file "$TAG_OBJECT_FILE" --release-file "$RELEASE_FILE" \
    --expected-asset "$APK_NAME"
  assert_status 2
  assert_stdout_empty
  assert_stderr_contains 'unsupported type tag'
}

test_absent_tag_draft_requires_exact_target() {
  write_missing "$TAG_FILE"
  write_draft_release 123 "$OTHER_SHA"
  run_helper --tag-name "$TAG_NAME" --main-sha "$MAIN_SHA" --tag-file "$TAG_FILE" \
    --release-file "$RELEASE_FILE" --expected-asset "$APK_NAME"
  assert_status 2
  assert_stdout_empty
  assert_stderr_contains 'target the selected main SHA'
}

test_absent_tag_matching_draft_creates_tag_and_reuses_draft() {
  write_missing "$TAG_FILE"
  write_draft_release 123 "$MAIN_SHA"
  run_helper --tag-name "$TAG_NAME" --main-sha "$MAIN_SHA" --tag-file "$TAG_FILE" \
    --release-file "$RELEASE_FILE" --expected-asset "$APK_NAME"
  assert_status 0
  assert_json_field '.tag_action' 'create-tag'
  assert_json_field '.release_action' 'reuse-draft'
  assert_json_field '.release_id' '123'
  assert_json_field '.tag_sha' 'null'
}

test_published_tag_missing_is_rejected() {
  write_missing "$TAG_FILE"
  write_published_release uploaded uploaded 10 10
  run_helper --tag-name "$TAG_NAME" --main-sha "$MAIN_SHA" --tag-file "$TAG_FILE" \
    --release-file "$RELEASE_FILE" --expected-asset "$APK_NAME" --expected-asset "$CHECKSUM_NAME"
  assert_status 2
  assert_stdout_empty
  assert_stderr_contains 'published release exists but its tag reference is missing'
}

test_published_complete_release_is_already_published() {
  write_lightweight_tag "$MAIN_SHA"
  write_published_release uploaded uploaded 10 10
  run_helper --tag-name "$TAG_NAME" --main-sha "$MAIN_SHA" --tag-file "$TAG_FILE" \
    --release-file "$RELEASE_FILE" --expected-asset "$APK_NAME" --expected-asset "$CHECKSUM_NAME"
  assert_status 0
  assert_json_field '.tag_action' 'reuse-tag'
  assert_json_field '.release_action' 'already-published'
  assert_json_field '.release_id' '123'
}

test_published_incomplete_release_is_rejected() {
  write_lightweight_tag "$MAIN_SHA"
  write_published_release uploaded uploaded 10 0
  write_text "$OUTPUT_FILE" 'existing=1'
  run_helper --tag-name "$TAG_NAME" --main-sha "$MAIN_SHA" --tag-file "$TAG_FILE" \
    --release-file "$RELEASE_FILE" --expected-asset "$APK_NAME" --expected-asset "$CHECKSUM_NAME" \
    --github-output "$OUTPUT_FILE"
  assert_status 2
  assert_stdout_empty
  assert_stderr_contains 'missing an expected non-empty artifact'
  assert_output_absent_or_equals 'existing=1'
}

test_published_unuploaded_asset_is_rejected() {
  write_lightweight_tag "$MAIN_SHA"
  write_published_release uploaded starter 10 10
  run_helper --tag-name "$TAG_NAME" --main-sha "$MAIN_SHA" --tag-file "$TAG_FILE" \
    --release-file "$RELEASE_FILE" --expected-asset "$APK_NAME" --expected-asset "$CHECKSUM_NAME"
  assert_status 2
  assert_stdout_empty
  assert_stderr_contains 'missing an expected non-empty artifact'
}

test_published_release_requires_both_expected_assets() {
  write_lightweight_tag "$MAIN_SHA"
  jq -cn --arg tag "$TAG_NAME" --arg sha "$MAIN_SHA" --arg apk "$APK_NAME" \
    '{id:123,tag_name:$tag,target_commitish:$sha,draft:false,assets:[{name:$apk,state:"uploaded",size:10}]}' > "$RELEASE_FILE"
  run_helper --tag-name "$TAG_NAME" --main-sha "$MAIN_SHA" --tag-file "$TAG_FILE" \
    --release-file "$RELEASE_FILE" --expected-asset "$APK_NAME" --expected-asset "$CHECKSUM_NAME"
  assert_status 2
  assert_stdout_empty
  assert_stderr_contains 'missing an expected non-empty artifact'
}

test_github_output_appends_only_after_success() {
  prepare_missing_state
  write_text "$OUTPUT_FILE" 'existing=1'
  run_helper --tag-name "$TAG_NAME" --main-sha "$MAIN_SHA" --tag-file "$TAG_FILE" \
    --release-file "$RELEASE_FILE" --expected-asset "$APK_NAME" --github-output "$OUTPUT_FILE"
  assert_status 0
  assert_output_equals $'existing=1\ntag_action=create-tag\nrelease_action=create-draft\nrelease_id=\ntag_sha='
}

test_github_output_failure_emits_no_plan() {
  prepare_missing_state
  run_helper --tag-name "$TAG_NAME" --main-sha "$MAIN_SHA" --tag-file "$TAG_FILE" \
    --release-file "$RELEASE_FILE" --expected-asset "$APK_NAME" \
    --github-output "$TEST_ROOT/missing-directory/github-output"
  assert_status 2
  assert_stdout_empty
  assert_stderr_contains 'Could not append GitHub outputs'
}

test_malformed_json_and_non_object_are_rejected_clearly() {
  write_text "$TAG_FILE" '{not-json'
  write_missing "$RELEASE_FILE"
  run_helper --tag-name "$TAG_NAME" --main-sha "$MAIN_SHA" --tag-file "$TAG_FILE" \
    --release-file "$RELEASE_FILE" --expected-asset "$APK_NAME"
  assert_status 2
  assert_stdout_empty
  assert_stderr_contains 'tag reference is not valid JSON'

  write_text "$TAG_FILE" '[]'
  run_helper --tag-name "$TAG_NAME" --main-sha "$MAIN_SHA" --tag-file "$TAG_FILE" \
    --release-file "$RELEASE_FILE" --expected-asset "$APK_NAME"
  assert_status 2
  assert_stdout_empty
  assert_stderr_contains 'tag reference must contain a JSON object'

  write_text "$TAG_FILE" '{}{}'
  run_helper --tag-name "$TAG_NAME" --main-sha "$MAIN_SHA" --tag-file "$TAG_FILE" \
    --release-file "$RELEASE_FILE" --expected-asset "$APK_NAME"
  assert_status 2
  assert_stdout_empty
  assert_stderr_contains 'tag reference must contain a JSON object'
}

test_each_response_file_is_validated_before_planning() {
  write_missing "$TAG_FILE"
  write_text "$RELEASE_FILE" '{"missing":'
  run_helper --tag-name "$TAG_NAME" --main-sha "$MAIN_SHA" --tag-file "$TAG_FILE" \
    --release-file "$RELEASE_FILE" --expected-asset "$APK_NAME"
  assert_status 2
  assert_stdout_empty
  assert_stderr_contains 'release response is not valid JSON'

  write_annotated_tag "$MAIN_SHA"
  write_text "$TAG_OBJECT_FILE" '[]'
  write_missing "$RELEASE_FILE"
  run_helper --tag-name "$TAG_NAME" --main-sha "$MAIN_SHA" --tag-file "$TAG_FILE" \
    --tag-object-file "$TAG_OBJECT_FILE" --release-file "$RELEASE_FILE" \
    --expected-asset "$APK_NAME"
  assert_status 2
  assert_stdout_empty
  assert_stderr_contains 'annotated tag must contain a JSON object'
}

test_invalid_sha_values_are_rejected() {
  write_missing "$TAG_FILE"
  write_missing "$RELEASE_FILE"
  run_helper --tag-name "$TAG_NAME" --main-sha 'Aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' \
    --tag-file "$TAG_FILE" --release-file "$RELEASE_FILE" --expected-asset "$APK_NAME"
  assert_status 2
  assert_stdout_empty
  assert_stderr_contains 'selected main SHA is not a 40-character commit SHA'

  write_text "$TAG_FILE" '{"object":{"type":"commit","sha":"short"}}'
  run_helper --tag-name "$TAG_NAME" --main-sha "$MAIN_SHA" --tag-file "$TAG_FILE" \
    --release-file "$RELEASE_FILE" --expected-asset "$APK_NAME"
  assert_status 2
  assert_stdout_empty
  assert_stderr_contains 'tag commit is not a 40-character commit SHA'
}

test_bad_release_metadata_is_rejected() {
  write_lightweight_tag "$MAIN_SHA"
  jq -cn --arg tag "$TAG_NAME" --arg sha "$MAIN_SHA" \
    '{id:"123\nunsafe",tag_name:$tag,target_commitish:$sha,draft:true,assets:[]}' > "$RELEASE_FILE"
  run_helper --tag-name "$TAG_NAME" --main-sha "$MAIN_SHA" --tag-file "$TAG_FILE" \
    --release-file "$RELEASE_FILE" --expected-asset "$APK_NAME"
  assert_status 2
  assert_stdout_empty
  assert_stderr_contains 'safe numeric id'

  jq -cn --arg tag 'v0.11.0' --arg sha "$MAIN_SHA" \
    '{id:123,tag_name:$tag,target_commitish:$sha,draft:true,assets:[]}' > "$RELEASE_FILE"
  run_helper --tag-name "$TAG_NAME" --main-sha "$MAIN_SHA" --tag-file "$TAG_FILE" \
    --release-file "$RELEASE_FILE" --expected-asset "$APK_NAME"
  assert_status 2
  assert_stdout_empty
  assert_stderr_contains 'different tag_name'
}

test_required_cli_arguments_are_rejected_without_output() {
  run_helper --tag-name "$TAG_NAME" --tag-file "$TAG_FILE" --release-file "$RELEASE_FILE" \
    --expected-asset "$APK_NAME"
  assert_status 2
  assert_stdout_empty
  assert_stderr_contains '--main-sha is required'

  run_helper --tag-name "$TAG_NAME" --main-sha "$MAIN_SHA" --tag-file "$TAG_FILE" \
    --release-file "$RELEASE_FILE"
  assert_status 2
  assert_stdout_empty
  assert_stderr_contains '--expected-asset is required'

  run_helper --tag-name
  assert_status 2
  assert_stdout_empty
  assert_stderr_contains '--tag-name requires a non-empty value'
}

run_test() {
  local name="$1"
  local failures_before="$FAIL_COUNT"
  shift
  printf 'Running %s...\n' "$name"
  "$@"
  if [[ "$FAIL_COUNT" == "$failures_before" ]]; then
    PASS_COUNT=$((PASS_COUNT + 1))
    printf 'PASS %s\n' "$name"
  else
    printf 'FAIL %s\n' "$name" >&2
  fi
}

run_test 'missing tag and release create both' test_missing_tag_and_release_create_both
run_test 'matching tag and draft are reused' test_matching_tag_and_draft_are_reused
run_test 'mismatch rejected without outputs' test_mismatch_rejected_without_outputs
run_test 'annotated tag resolves one level' test_annotated_tag_resolves_one_level
run_test 'annotated tag requires resolvable commit' test_annotated_tag_requires_resolvable_commit
run_test 'absent tag draft requires exact target' test_absent_tag_draft_requires_exact_target
run_test 'absent tag matching draft creates tag and reuses draft' test_absent_tag_matching_draft_creates_tag_and_reuses_draft
run_test 'published tag missing is rejected' test_published_tag_missing_is_rejected
run_test 'published complete release is already published' test_published_complete_release_is_already_published
run_test 'published incomplete release is rejected' test_published_incomplete_release_is_rejected
run_test 'published unuploaded asset is rejected' test_published_unuploaded_asset_is_rejected
run_test 'published release requires both expected assets' test_published_release_requires_both_expected_assets
run_test 'GitHub output appends only after success' test_github_output_appends_only_after_success
run_test 'GitHub output failure emits no plan' test_github_output_failure_emits_no_plan
run_test 'malformed JSON and non-object are rejected clearly' test_malformed_json_and_non_object_are_rejected_clearly
run_test 'each response file is validated before planning' test_each_response_file_is_validated_before_planning
run_test 'invalid SHA values are rejected' test_invalid_sha_values_are_rejected
run_test 'bad release metadata is rejected' test_bad_release_metadata_is_rejected
run_test 'required CLI arguments are rejected without output' test_required_cli_arguments_are_rejected_without_output

printf '%s\n' "Tests: $PASS_COUNT passed, $FAIL_COUNT failed"
if [[ "$FAIL_COUNT" -ne 0 ]]; then
  exit 1
fi
