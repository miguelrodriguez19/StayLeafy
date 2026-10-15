#!/usr/bin/env bash
set -euo pipefail

test_directory="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
"$BASH" "$test_directory/test_validate_version.sh"
"$BASH" "$test_directory/test_release_state.sh"
