#!/usr/bin/env bash
# Decide whether the release workflow may create or recover a GitHub release.
#
# This script intentionally uses only Bash and jq.  All JSON is parsed with jq;
# no response file is sourced or evaluated as shell code.

set -Eeuo pipefail

usage() {
  printf '%s\n' \
    'Usage: release_state.sh --tag-name NAME --main-sha SHA --tag-file FILE [--tag-object-file FILE]' \
    '--release-file FILE --expected-asset NAME [--expected-asset NAME ...] [--github-output FILE]' >&2
}

error() {
  printf 'release state error: %s\n' "$1" >&2
  exit 2
}

require_option_value() {
  if [[ "$2" == --* || -z "$2" ]]; then
    error "$1 requires a non-empty value"
  fi
}

require_sha() {
  local value="$1"
  local description="$2"
  if [[ ! "$value" =~ ^[0-9a-f]{40}$ ]]; then
    error "$description is not a 40-character commit SHA"
  fi
}

require_json_object() {
  local path="$1"
  local description="$2"
  local jq_error

  if [[ ! -r "$path" ]]; then
    error "$description cannot be read: $path"
  fi
  if ! jq_error="$(jq -e -s 'length == 1 and (.[0] | type) == "object"' -- "$path" 2>&1 >/dev/null)"; then
    jq_error="${jq_error//$'\n'/ }"
    if [[ -n "$jq_error" ]]; then
      error "$description is not valid JSON: $jq_error"
    fi
    error "$description must contain a JSON object"
  fi
}

json_is_missing() {
  jq -e '.missing == true' -- "$1" >/dev/null 2>&1
}

read_object_type() {
  local path="$1"
  jq -r '
    if .missing == true then
      "missing"
    elif (.object | type) == "object" and (.object.type | type) == "string" then
      .object.type
    else
      ""
    end
  ' -- "$path"
}

read_object_sha() {
  local path="$1"
  jq -r 'if (.object | type) == "object" and (.object.sha | type) == "string" then .object.sha else "" end' -- "$path"
}

resolve_tag_sha() {
  local object_type
  local object_sha
  local nested_type
  local nested_sha

  TAG_SHA=""
  if json_is_missing "$TAG_FILE"; then
    return 0
  fi

  object_type="$(read_object_type "$TAG_FILE")"
  case "$object_type" in
    commit)
      object_sha="$(read_object_sha "$TAG_FILE")"
      require_sha "$object_sha" 'The tag commit'
      TAG_SHA="$object_sha"
      ;;
    tag)
      object_sha="$(read_object_sha "$TAG_FILE")"
      require_sha "$object_sha" 'The annotated tag object'
      if [[ -z "$TAG_OBJECT_FILE" ]]; then
        error 'The annotated tag object could not be resolved'
      fi
      if json_is_missing "$TAG_OBJECT_FILE"; then
        error 'The annotated tag object could not be resolved'
      fi
      nested_type="$(read_object_type "$TAG_OBJECT_FILE")"
      if [[ "$nested_type" != commit ]]; then
        if [[ -z "$nested_type" ]]; then
          error 'The annotated tag has no Git object'
        fi
        error "The annotated tag points to unsupported type $nested_type"
      fi
      nested_sha="$(read_object_sha "$TAG_OBJECT_FILE")"
      require_sha "$nested_sha" 'The annotated tag commit'
      TAG_SHA="$nested_sha"
      ;;
    missing)
      return 0
      ;;
    '')
      error 'The tag reference has no Git object'
      ;;
    *)
      error "The tag reference points to unsupported type $object_type"
      ;;
  esac
}

asset_is_complete() {
  local asset_name="$1"
  jq -e --arg name "$asset_name" '
    if (.assets | type) != "array" then
      false
    else
      ([.assets[]
        | select(type == "object" and (.name | type) == "string" and .name == $name)]
       | if length == 0 then
           false
         else
           .[-1]
           | (.state == "uploaded"
              and (.size | type) == "number"
              and .size > 0)
         end)
    end
  ' -- "$RELEASE_FILE" >/dev/null 2>&1
}

parse_arguments() {
  TAG_NAME=""
  MAIN_SHA=""
  TAG_FILE=""
  TAG_OBJECT_FILE=""
  RELEASE_FILE=""
  GITHUB_OUTPUT=""
  EXPECTED_ASSETS=()

  while (($# > 0)); do
    case "$1" in
      --tag-name|--main-sha|--tag-file|--tag-object-file|--release-file|--expected-asset|--github-output)
        option="$1"
        shift
        if (($# == 0)); then
          error "$option requires a non-empty value"
        fi
        require_option_value "$option" "$1"
        case "$option" in
          --tag-name) TAG_NAME="$1" ;;
          --main-sha) MAIN_SHA="$1" ;;
          --tag-file) TAG_FILE="$1" ;;
          --tag-object-file) TAG_OBJECT_FILE="$1" ;;
          --release-file) RELEASE_FILE="$1" ;;
          --expected-asset) EXPECTED_ASSETS[${#EXPECTED_ASSETS[@]}]="$1" ;;
          --github-output) GITHUB_OUTPUT="$1" ;;
        esac
        shift
        ;;
      --help|-h)
        usage
        exit 0
        ;;
      *)
        error "unknown option: $1"
        ;;
    esac
  done

  [[ -n "$TAG_NAME" ]] || error '--tag-name is required'
  [[ -n "$MAIN_SHA" ]] || error '--main-sha is required'
  [[ -n "$TAG_FILE" ]] || error '--tag-file is required'
  [[ -n "$RELEASE_FILE" ]] || error '--release-file is required'
  ((${#EXPECTED_ASSETS[@]} > 0)) || error '--expected-asset is required'
}

parse_arguments "$@"
require_sha "$MAIN_SHA" 'The selected main SHA'

require_json_object "$TAG_FILE" 'The tag reference'
if [[ -n "$TAG_OBJECT_FILE" ]]; then
  require_json_object "$TAG_OBJECT_FILE" 'The annotated tag'
fi
require_json_object "$RELEASE_FILE" 'The release response'
resolve_tag_sha

if [[ -n "$TAG_SHA" && "$TAG_SHA" != "$MAIN_SHA" ]]; then
  error "Tag $TAG_NAME points to $TAG_SHA, expected $MAIN_SHA"
fi

RELEASE_ID=""
RELEASE_ACTION='create-draft'

if ! json_is_missing "$RELEASE_FILE"; then
  release_tag_name="$(jq -r 'if (.tag_name | type) == "string" then .tag_name else "" end' -- "$RELEASE_FILE")"
  if [[ "$release_tag_name" != "$TAG_NAME" ]]; then
    error 'The release returned for the tag has a different tag_name'
  fi

  if ! RELEASE_ID="$(jq -er '
    if (.id | type) == "number" and (.id == (.id | floor)) and .id >= 0 then
      (.id | tostring)
    else
      empty
    end
  ' -- "$RELEASE_FILE" 2>/dev/null)"; then
    error 'The existing release has no safe numeric id'
  fi
  if [[ ! "$RELEASE_ID" =~ ^[0-9]+$ ]]; then
    error 'The existing release has no safe numeric id'
  fi

  if jq -e '.draft == true' -- "$RELEASE_FILE" >/dev/null 2>&1; then
    release_is_draft=true
  else
    release_is_draft=false
  fi

  if [[ -z "$TAG_SHA" ]]; then
    if [[ "$release_is_draft" != true ]]; then
      error 'A published release exists but its tag reference is missing'
    fi
    release_target="$(jq -r 'if (.target_commitish | type) == "string" then .target_commitish else "" end' -- "$RELEASE_FILE")"
    if [[ "$release_target" != "$MAIN_SHA" ]]; then
      error 'A draft release without its tag must target the selected main SHA'
    fi
  fi

  if [[ "$release_is_draft" == true ]]; then
    RELEASE_ACTION='reuse-draft'
  else
    for expected_asset in "${EXPECTED_ASSETS[@]}"; do
      if ! asset_is_complete "$expected_asset"; then
        error 'The published release is missing an expected non-empty artifact'
      fi
    done
    RELEASE_ACTION='already-published'
  fi
fi

if [[ -n "$TAG_SHA" ]]; then
  TAG_ACTION='reuse-tag'
else
  TAG_ACTION='create-tag'
fi

if ! PLAN_JSON="$(jq -cn \
  --arg tag_action "$TAG_ACTION" \
  --arg release_action "$RELEASE_ACTION" \
  --arg release_id "$RELEASE_ID" \
  --arg tag_sha "$TAG_SHA" \
  '{tag_action: $tag_action, release_action: $release_action,
    release_id: (if $release_id == "" then null else $release_id end),
    tag_sha: (if $tag_sha == "" then null else $tag_sha end)}')"; then
  error 'Could not serialize the release plan'
fi

if [[ -n "$GITHUB_OUTPUT" ]]; then
  if ! printf 'tag_action=%s\nrelease_action=%s\nrelease_id=%s\ntag_sha=%s\n' \
    "$TAG_ACTION" "$RELEASE_ACTION" "$RELEASE_ID" "$TAG_SHA" >> "$GITHUB_OUTPUT"; then
    error "Could not append GitHub outputs: $GITHUB_OUTPUT"
  fi
fi

printf '%s\n' "$PLAN_JSON"
