#!/usr/bin/env bash

# Validate Stay Leafy's version file and pull-request version transitions.
# Keep this script compatible with the Bash 3.2 shipped by macOS.

LC_ALL=C
export LC_ALL

MAX_VERSION_CODE=2100000000
PROPERTY_RE='^([A-Z][A-Z0-9_]*)=(.*)$'
SEMVER_RE='^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$'
VERSION_CODE_RE='^[1-9][0-9]*$'
LEGACY_NAME_RE='^[[:blank:]]*versionName[[:blank:]]*=[[:blank:]]*"([^"]+)"[[:blank:]]*$'
LEGACY_CODE_RE='^[[:blank:]]*versionCode[[:blank:]]*=[[:blank:]]*([0-9]+)[[:blank:]]*$'
TITLE_RE='^([a-z]+)(\([^[:space:]()!]+\))?(!)?: ([^[:space:]].*[^[:space:]]|[^[:space:]])$'

CURRENT_VERSION_NAME=
CURRENT_VERSION_CODE=
CURRENT_MAJOR=
CURRENT_MINOR=
CURRENT_PATCH=
BASE_VERSION_NAME=
BASE_VERSION_CODE=
BASE_MAJOR=
BASE_MINOR=
BASE_PATCH=
TITLE_TYPE=
TITLE_BREAKING=0
INCREMENTED_DECIMAL=

validation_error() {
    printf 'version validation failed: %s\n' "$1" >&2
    return 1
}

usage() {
    printf 'usage: %s [--base-ref SHA --title TEXT] [--github-output PATH]\n' "${0##*/}"
}

validate_values() {
    local source="$1"
    local version_name="$2"
    local version_code="$3"

    if ! [[ "$version_name" =~ $SEMVER_RE ]]; then
        validation_error "$source: invalid VERSION_NAME '$version_name': expected simple SemVer"
        return 1
    fi
    CURRENT_MAJOR=${BASH_REMATCH[1]}
    CURRENT_MINOR=${BASH_REMATCH[2]}
    CURRENT_PATCH=${BASH_REMATCH[3]}

    if ! [[ "$version_code" =~ $VERSION_CODE_RE ]]; then
        validation_error "$source: invalid VERSION_CODE '$version_code': expected a positive decimal integer"
        return 1
    fi
    if [ "${#version_code}" -gt "${#MAX_VERSION_CODE}" ] || {
        [ "${#version_code}" -eq "${#MAX_VERSION_CODE}" ] && [[ "$version_code" > "$MAX_VERSION_CODE" ]]
    }; then
        validation_error "$source: VERSION_CODE '$version_code' exceeds Android's maximum of $MAX_VERSION_CODE"
        return 1
    fi

    CURRENT_VERSION_NAME=$version_name
    CURRENT_VERSION_CODE=$version_code
    return 0
}

parse_properties_stream() {
    local source="$1"
    local line key value
    local have_name=0
    local have_code=0
    local version_name=
    local version_code=
    local line_number=0

    while IFS= read -r line || [ -n "$line" ]; do
        line_number=$((line_number + 1))
        case "$line" in
            *$'\r') line=${line%$'\r'} ;;
        esac

        if [[ "$line" =~ ^[[:space:]]*$ ]] || [[ "$line" =~ ^[[:space:]]*# ]]; then
            continue
        fi
        if ! [[ "$line" =~ $PROPERTY_RE ]]; then
            validation_error "$source:$line_number: malformed property; expected KEY=value"
            return 1
        fi

        key=${BASH_REMATCH[1]}
        value=${BASH_REMATCH[2]}
        if [ "$key" != VERSION_NAME ] && [ "$key" != VERSION_CODE ]; then
            validation_error "$source:$line_number: unknown field '$key'"
            return 1
        fi
        if [[ "$value" =~ ^[[:space:]] ]] || [[ "$value" =~ [[:space:]]$ ]]; then
            validation_error "$source:$line_number: $key must not have surrounding whitespace"
            return 1
        fi

        if [ "$key" = VERSION_NAME ]; then
            if [ "$have_name" -eq 1 ]; then
                validation_error "$source:$line_number: duplicate field '$key'"
                return 1
            fi
            version_name=$value
            have_name=1
        else
            if [ "$have_code" -eq 1 ]; then
                validation_error "$source:$line_number: duplicate field '$key'"
                return 1
            fi
            version_code=$value
            have_code=1
        fi
    done

    if [ "$have_name" -eq 0 ] || [ "$have_code" -eq 0 ]; then
        local missing=
        if [ "$have_name" -eq 0 ]; then missing=VERSION_NAME; fi
        if [ "$have_code" -eq 0 ]; then
            if [ -n "$missing" ]; then missing="$missing, "; fi
            missing="${missing}VERSION_CODE"
        fi
        validation_error "$source: missing field(s): $missing"
        return 1
    fi

    validate_values "$source" "$version_name" "$version_code" || return 1
    return 0
}

parse_properties_file() {
    local path="$1"
    if [ ! -e "$path" ]; then
        validation_error "$path: file is missing"
        return 1
    fi
    if [ -d "$path" ] || [ ! -r "$path" ]; then
        validation_error "$path: cannot read file"
        return 1
    fi
    parse_properties_stream "$path" < "$path"
}

parse_properties_text() {
    local source="$1"
    local text="$2"
    parse_properties_stream "$source" <<EOF
$text
EOF
}

parse_legacy_gradle_text() {
    local source="$1"
    local text="$2"
    local line
    local name_count=0
    local code_count=0
    local legacy_name=
    local legacy_code=

    while IFS= read -r line || [ -n "$line" ]; do
        case "$line" in
            *$'\r') line=${line%$'\r'} ;;
        esac
        if [[ "$line" =~ $LEGACY_NAME_RE ]]; then
            name_count=$((name_count + 1))
            legacy_name=${BASH_REMATCH[1]}
        fi
        if [[ "$line" =~ $LEGACY_CODE_RE ]]; then
            code_count=$((code_count + 1))
            legacy_code=${BASH_REMATCH[1]}
        fi
    done <<EOF
$text
EOF

    if [ "$name_count" -ne 1 ]; then
        validation_error "$source: expected exactly one literal versionName declaration, found $name_count"
        return 1
    fi
    if [ "$code_count" -ne 1 ]; then
        validation_error "$source: expected exactly one literal versionCode declaration, found $code_count"
        return 1
    fi

    validate_values "$source" "$legacy_name" "$legacy_code"
}

read_base_version() {
    local base_ref="$1"
    local base_commit
    local version_text
    local legacy_text

    if ! base_commit=$(git rev-parse --verify "$base_ref^{commit}" 2>&1); then
        validation_error "base ref '$base_ref' is not a valid commit: $base_commit"
        return 1
    fi

    if version_text=$(git show "$base_commit:version.properties" 2>/dev/null); then
        parse_properties_text "$base_ref:version.properties" "$version_text" || return 1
    else
        if ! legacy_text=$(git show "$base_commit:app/build.gradle.kts" 2>/dev/null); then
            validation_error "$base_ref:version.properties is missing and the legacy app/build.gradle.kts fallback is unavailable"
            return 1
        fi
        parse_legacy_gradle_text "$base_ref:app/build.gradle.kts" "$legacy_text" || return 1
    fi

    BASE_VERSION_NAME=$CURRENT_VERSION_NAME
    BASE_VERSION_CODE=$CURRENT_VERSION_CODE
    BASE_MAJOR=$CURRENT_MAJOR
    BASE_MINOR=$CURRENT_MINOR
    BASE_PATCH=$CURRENT_PATCH
    return 0
}

parse_conventional_title() {
    local title="$1"
    local change_type

    case "$title" in
        *$'\r'*|*$'\n'*)
            validation_error "invalid pull-request title: expected type[(scope)][!]: description with one space after ':'"
            return 1
            ;;
    esac
    if ! [[ "$title" =~ $TITLE_RE ]]; then
        validation_error "invalid pull-request title: expected type[(scope)][!]: description with one space after ':'"
        return 1
    fi

    change_type=${BASH_REMATCH[1]}
    case "$change_type" in
        feat|fix|refactor|perf|docs|test|build|ci|chore|style|revert) ;;
        *)
            validation_error "invalid pull-request title type '$change_type'"
            return 1
            ;;
    esac

    TITLE_TYPE=$change_type
    if [ -n "${BASH_REMATCH[3]}" ]; then
        TITLE_BREAKING=1
    else
        TITLE_BREAKING=0
    fi
    return 0
}

increment_decimal() {
    local result="$1"
    local index
    local digit
    local next
    local prefix
    local suffix
    local carry=1

    index=$((${#result} - 1))
    while [ "$index" -ge 0 ] && [ "$carry" -eq 1 ]; do
        digit=${result:$index:1}
        case "$digit" in
            0) next=1; carry=0 ;;
            1) next=2; carry=0 ;;
            2) next=3; carry=0 ;;
            3) next=4; carry=0 ;;
            4) next=5; carry=0 ;;
            5) next=6; carry=0 ;;
            6) next=7; carry=0 ;;
            7) next=8; carry=0 ;;
            8) next=9; carry=0 ;;
            9) next=0 ;;
            *) return 1 ;;
        esac
        prefix=${result:0:$index}
        suffix=${result:$((index + 1))}
        result="${prefix}${next}${suffix}"
        index=$((index - 1))
    done
    if [ "$carry" -eq 1 ]; then
        result="1$result"
    fi
    INCREMENTED_DECIMAL=$result
}

validate_pull_request() {
    local current_name="$CURRENT_VERSION_NAME"
    local current_code="$CURRENT_VERSION_CODE"
    local expected_name
    local expected_code
    local next_major
    local next_minor
    local next_patch

    read_base_version "$1" || return 1
    parse_conventional_title "$2" || return 1

    expected_code=$((BASE_VERSION_CODE + 1))
    if [ "$TITLE_BREAKING" -eq 1 ]; then
        increment_decimal "$BASE_MAJOR"
        next_major=$INCREMENTED_DECIMAL
        expected_name="${next_major}.0.0"
    elif [ "$TITLE_TYPE" = feat ]; then
        increment_decimal "$BASE_MINOR"
        next_minor=$INCREMENTED_DECIMAL
        expected_name="${BASE_MAJOR}.${next_minor}.0"
    else
        increment_decimal "$BASE_PATCH"
        next_patch=$INCREMENTED_DECIMAL
        expected_name="${BASE_MAJOR}.${BASE_MINOR}.${next_patch}"
    fi

    if [ "$current_name" != "$expected_name" ]; then
        validation_error "VERSION_NAME must be $expected_name for base $BASE_VERSION_NAME and title '$2'; found $current_name"
        return 1
    fi
    if [ "$current_code" != "$expected_code" ]; then
        validation_error "VERSION_CODE must increase exactly by one to $expected_code; found $current_code"
        return 1
    fi
    CURRENT_VERSION_NAME=$current_name
    CURRENT_VERSION_CODE=$current_code
    return 0
}

parse_arguments() {
    BASE_REF_SET=0
    TITLE_SET=0
    GITHUB_OUTPUT_SET=0
    BASE_REF=
    TITLE=
    GITHUB_OUTPUT=

    while [ "$#" -gt 0 ]; do
        case "$1" in
            --help|-h)
                usage
                exit 0
                ;;
            --base-ref)
                if [ "$#" -lt 2 ] || [[ "$2" == --* ]]; then
                    printf 'version validation failed: argument --base-ref requires a value\n' >&2
                    return 2
                fi
                BASE_REF=$2
                BASE_REF_SET=1
                shift 2
                ;;
            --base-ref=*)
                BASE_REF=${1#--base-ref=}
                BASE_REF_SET=1
                shift
                ;;
            --title)
                if [ "$#" -lt 2 ] || [[ "$2" == --* ]]; then
                    printf 'version validation failed: argument --title requires a value\n' >&2
                    return 2
                fi
                TITLE=$2
                TITLE_SET=1
                shift 2
                ;;
            --title=*)
                TITLE=${1#--title=}
                TITLE_SET=1
                shift
                ;;
            --github-output)
                if [ "$#" -lt 2 ] || [[ "$2" == --* ]]; then
                    printf 'version validation failed: argument --github-output requires a value\n' >&2
                    return 2
                fi
                GITHUB_OUTPUT=$2
                GITHUB_OUTPUT_SET=1
                shift 2
                ;;
            --github-output=*)
                GITHUB_OUTPUT=${1#--github-output=}
                GITHUB_OUTPUT_SET=1
                shift
                ;;
            *)
                printf 'version validation failed: unrecognized argument %s\n' "$1" >&2
                return 2
                ;;
        esac
    done
    return 0
}

main() {
    local current_path

    parse_arguments "$@"
    case "$?" in
        0) ;;
        *) return 2 ;;
    esac

    if [ "$BASE_REF_SET" -ne "$TITLE_SET" ]; then
        printf 'version validation failed: --base-ref and --title must be provided together\n' >&2
        return 2
    fi

    current_path="$(pwd)/version.properties"
    parse_properties_file "$current_path" || return 1

    if [ "$BASE_REF_SET" -eq 1 ]; then
        validate_pull_request "$BASE_REF" "$TITLE" || return 1
    fi
    if [ "$GITHUB_OUTPUT_SET" -eq 1 ]; then
        if ! printf 'version_name=%s\nversion_code=%s\n' "$CURRENT_VERSION_NAME" "$CURRENT_VERSION_CODE" >> "$GITHUB_OUTPUT"; then
            validation_error "$GITHUB_OUTPUT: cannot append GitHub outputs"
            return 1
        fi
    fi

    printf 'version %s (%s) is valid\n' "$CURRENT_VERSION_NAME" "$CURRENT_VERSION_CODE"
    return 0
}

main "$@"
exit $?
