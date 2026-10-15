#!/usr/bin/env python3
"""Validate Stay Leafy's version file and pull-request version transitions."""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence


MAX_VERSION_CODE = 2_100_000_000
VERSION_KEYS = frozenset(("VERSION_NAME", "VERSION_CODE"))
CONVENTIONAL_TYPES = frozenset(
    ("feat", "fix", "refactor", "perf", "docs", "test", "build", "ci", "chore", "style", "revert")
)
SIMPLE_SEMVER = re.compile(r"^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$")
VERSION_CODE_TEXT = re.compile(r"^[1-9][0-9]*$")
PROPERTY_LINE = re.compile(r"^([A-Z][A-Z0-9_]*)=(.*)$")
CONVENTIONAL_TITLE = re.compile(
    r"^(?P<type>[a-z]+)(?:\((?P<scope>[^\s()!]+)\))?(?P<breaking>!)?: (?P<description>\S(?:.*\S)?)$"
)
LEGACY_VERSION_NAME = re.compile(r'^[ \t]*versionName[ \t]*=[ \t]*"([^"\r\n]+)"[ \t]*$', re.MULTILINE)
LEGACY_VERSION_CODE = re.compile(r"^[ \t]*versionCode[ \t]*=[ \t]*([0-9]+)[ \t]*$", re.MULTILINE)


class ValidationError(ValueError):
    """Raised when a version file or version transition is invalid."""


@dataclass(frozen=True)
class Version:
    name: str
    code: int

    @property
    def semver(self) -> tuple[int, int, int]:
        match = SIMPLE_SEMVER.fullmatch(self.name)
        if match is None:
            raise ValidationError(f"invalid VERSION_NAME {self.name!r}: expected simple SemVer")
        return tuple(int(part) for part in match.groups())  # type: ignore[return-value]


def _validate_values(values: dict[str, str], source: str) -> Version:
    missing = VERSION_KEYS - values.keys()
    if missing:
        raise ValidationError(f"{source}: missing field(s): {', '.join(sorted(missing))}")

    unknown = values.keys() - VERSION_KEYS
    if unknown:
        raise ValidationError(f"{source}: unknown field(s): {', '.join(sorted(unknown))}")

    version_name = values["VERSION_NAME"]
    if SIMPLE_SEMVER.fullmatch(version_name) is None:
        raise ValidationError(f"{source}: invalid VERSION_NAME {version_name!r}: expected simple SemVer")

    version_code = values["VERSION_CODE"]
    if VERSION_CODE_TEXT.fullmatch(version_code) is None:
        raise ValidationError(
            f"{source}: invalid VERSION_CODE {version_code!r}: expected a positive decimal integer"
        )
    parsed_code = int(version_code)
    if parsed_code > MAX_VERSION_CODE:
        raise ValidationError(
            f"{source}: VERSION_CODE {parsed_code} exceeds Android's maximum of {MAX_VERSION_CODE}"
        )

    return Version(name=version_name, code=parsed_code)


def parse_version_properties(text: str, source: str = "version.properties") -> Version:
    """Parse and validate the strict two-field version.properties format."""

    values: dict[str, str] = {}
    for line_number, raw_line in enumerate(text.splitlines(), start=1):
        if not raw_line.strip() or raw_line.lstrip().startswith("#"):
            continue

        match = PROPERTY_LINE.fullmatch(raw_line)
        if match is None:
            raise ValidationError(f"{source}:{line_number}: malformed property; expected KEY=value")

        key, value = match.groups()
        if key not in VERSION_KEYS:
            raise ValidationError(f"{source}:{line_number}: unknown field {key!r}")
        if key in values:
            raise ValidationError(f"{source}:{line_number}: duplicate field {key!r}")
        if value != value.strip():
            raise ValidationError(f"{source}:{line_number}: {key} must not have surrounding whitespace")
        values[key] = value

    return _validate_values(values, source)


def read_version_properties(path: Path) -> Version:
    try:
        text = path.read_text(encoding="utf-8")
    except FileNotFoundError as exc:
        raise ValidationError(f"{path}: file is missing") from exc
    except OSError as exc:
        raise ValidationError(f"{path}: cannot read file: {exc}") from exc
    return parse_version_properties(text, str(path))


def parse_legacy_gradle_version(text: str, source: str = "app/build.gradle.kts") -> Version:
    """Parse only the old literal version declarations used by the bootstrap commit."""

    name_matches = LEGACY_VERSION_NAME.findall(text)
    code_matches = LEGACY_VERSION_CODE.findall(text)
    if len(name_matches) != 1:
        raise ValidationError(
            f"{source}: expected exactly one literal versionName declaration, found {len(name_matches)}"
        )
    if len(code_matches) != 1:
        raise ValidationError(
            f"{source}: expected exactly one literal versionCode declaration, found {len(code_matches)}"
        )
    return _validate_values(
        {"VERSION_NAME": name_matches[0], "VERSION_CODE": code_matches[0]},
        source,
    )


def _git_show(base_ref: str, path: str) -> str | None:
    result = subprocess.run(
        ["git", "show", f"{base_ref}:{path}"],
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode == 0:
        return result.stdout
    return None


def _require_git_ref(base_ref: str) -> None:
    result = subprocess.run(
        ["git", "rev-parse", "--verify", f"{base_ref}^{{commit}}"],
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        detail = result.stderr.strip() or "unknown git error"
        raise ValidationError(f"base ref {base_ref!r} is not a valid commit: {detail}")


def read_base_version(base_ref: str) -> Version:
    """Read version.properties, with the deliberately narrow first-PR fallback."""

    _require_git_ref(base_ref)
    version_text = _git_show(base_ref, "version.properties")
    if version_text is not None:
        return parse_version_properties(version_text, f"{base_ref}:version.properties")

    legacy_text = _git_show(base_ref, "app/build.gradle.kts")
    if legacy_text is None:
        raise ValidationError(
            f"{base_ref}:version.properties is missing and the legacy app/build.gradle.kts fallback is unavailable"
        )
    return parse_legacy_gradle_version(legacy_text, f"{base_ref}:app/build.gradle.kts")


def parse_conventional_title(title: str) -> tuple[str, bool]:
    match = CONVENTIONAL_TITLE.fullmatch(title)
    if match is None:
        raise ValidationError(
            "invalid pull-request title: expected type[(scope)][!]: description with one space after ':'"
        )
    change_type = match.group("type")
    if change_type not in CONVENTIONAL_TYPES:
        raise ValidationError(f"invalid pull-request title type {change_type!r}")
    return change_type, match.group("breaking") == "!"


def expected_version(base: Version, title: str) -> Version:
    change_type, breaking = parse_conventional_title(title)
    major, minor, patch = base.semver
    if breaking:
        next_semver = (major + 1, 0, 0)
    elif change_type == "feat":
        next_semver = (major, minor + 1, 0)
    else:
        next_semver = (major, minor, patch + 1)
    return Version(name=".".join(str(part) for part in next_semver), code=base.code + 1)


def validate_pull_request(current: Version, base_ref: str, title: str) -> None:
    base = read_base_version(base_ref)
    expected = expected_version(base, title)
    if current.name != expected.name:
        raise ValidationError(
            f"VERSION_NAME must be {expected.name} for base {base.name} and title {title!r}; found {current.name}"
        )
    if current.code != expected.code:
        raise ValidationError(
            f"VERSION_CODE must increase exactly by one to {expected.code}; found {current.code}"
        )


def _write_github_output(path: Path, version: Version) -> None:
    try:
        with path.open("a", encoding="utf-8") as output:
            output.write(f"version_name={version.name}\n")
            output.write(f"version_code={version.code}\n")
    except OSError as exc:
        raise ValidationError(f"{path}: cannot append GitHub outputs: {exc}") from exc


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-ref", help="exact git base commit used for pull-request comparison")
    parser.add_argument("--title", help="pull-request title in Conventional Commits format")
    parser.add_argument("--github-output", type=Path, help="append validated version outputs to this file")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    if (args.base_ref is None) != (args.title is None):
        print("version validation failed: --base-ref and --title must be provided together", file=sys.stderr)
        return 2

    try:
        current = read_version_properties(Path.cwd() / "version.properties")
        if args.base_ref is not None:
            validate_pull_request(current, args.base_ref, args.title)
        if args.github_output is not None:
            _write_github_output(args.github_output, current)
    except ValidationError as exc:
        print(f"version validation failed: {exc}", file=sys.stderr)
        return 1

    print(f"version {current.name} ({current.code}) is valid")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
