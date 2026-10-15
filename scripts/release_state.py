#!/usr/bin/env python3
"""Decide whether a versioned GitHub release can be created or recovered.

The GitHub workflow performs the API calls and writes their JSON responses to
temporary files.  This module keeps the state transition rules independent of
the network so they can be tested with ordinary ``unittest`` cases.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any


SHA_RE = re.compile(r"^[0-9a-f]{40}$")


class ReleaseStateError(ValueError):
    """Raised when GitHub reports an unsafe or inconsistent release state."""


@dataclass(frozen=True)
class ReleasePlan:
    """The safe mutations that remain after preflight and state inspection."""

    tag_action: str
    release_action: str
    release_id: str | None
    tag_sha: str | None


def _require_sha(value: Any, description: str) -> str:
    if not isinstance(value, str) or not SHA_RE.fullmatch(value):
        raise ReleaseStateError(f"{description} is not a 40-character commit SHA")
    return value


def _is_missing(payload: dict[str, Any]) -> bool:
    return payload.get("missing") is True


def _object_sha(payload: dict[str, Any], description: str) -> tuple[str, str]:
    obj = payload.get("object")
    if not isinstance(obj, dict):
        raise ReleaseStateError(f"{description} has no Git object")
    object_type = obj.get("type")
    object_sha = obj.get("sha")
    if not isinstance(object_type, str) or not isinstance(object_sha, str):
        raise ReleaseStateError(f"{description} has an invalid Git object")
    return object_type, object_sha


def resolve_tag_sha(
    tag_ref: dict[str, Any], tag_object: dict[str, Any] | None = None
) -> str | None:
    """Resolve a lightweight or annotated tag response to its commit SHA."""

    if _is_missing(tag_ref):
        return None

    object_type, object_sha = _object_sha(tag_ref, "The tag reference")
    if object_type == "commit":
        return _require_sha(object_sha, "The tag commit")
    if object_type != "tag":
        raise ReleaseStateError(f"The tag reference points to unsupported type {object_type!r}")
    if tag_object is None or _is_missing(tag_object):
        raise ReleaseStateError("The annotated tag object could not be resolved")

    nested_type, nested_sha = _object_sha(tag_object, "The annotated tag")
    if nested_type != "commit":
        raise ReleaseStateError(f"The annotated tag points to unsupported type {nested_type!r}")
    return _require_sha(nested_sha, "The annotated tag commit")


def _release_assets_complete(release: dict[str, Any], expected_assets: set[str]) -> bool:
    assets = release.get("assets")
    if not isinstance(assets, list):
        return False

    by_name: dict[str, dict[str, Any]] = {}
    for asset in assets:
        if isinstance(asset, dict) and isinstance(asset.get("name"), str):
            by_name[asset["name"]] = asset

    for name in expected_assets:
        asset = by_name.get(name)
        if asset is None:
            return False
        if asset.get("state") != "uploaded":
            return False
        try:
            if int(asset.get("size", 0)) <= 0:
                return False
        except (TypeError, ValueError):
            return False
    return True


def decide_release(
    *,
    tag_name: str,
    main_sha: str,
    tag_sha: str | None,
    release: dict[str, Any],
    expected_assets: set[str],
) -> ReleasePlan:
    """Return the only safe next state transition for a release.

    A published release is immutable from this workflow's perspective.  It is
    successful only when the tag resolves to the selected commit and both
    expected non-empty assets are already present.
    """

    selected_sha = _require_sha(main_sha, "The selected main SHA")
    if tag_sha is not None:
        resolved_tag_sha = _require_sha(tag_sha, "The existing tag commit")
        if resolved_tag_sha != selected_sha:
            raise ReleaseStateError(
                f"Tag {tag_name} points to {resolved_tag_sha}, expected {selected_sha}"
            )
    else:
        resolved_tag_sha = None

    release_missing = _is_missing(release)
    release_id: str | None = None
    release_action = "create-draft"

    if not release_missing:
        if release.get("tag_name") != tag_name:
            raise ReleaseStateError("The release returned for the tag has a different tag_name")

        raw_release_id = release.get("id")
        if raw_release_id is None:
            raise ReleaseStateError("The existing release has no id")
        release_id = str(raw_release_id)

        if resolved_tag_sha is None and release.get("target_commitish") != selected_sha:
            raise ReleaseStateError(
                "A draft release without its tag must target the selected main SHA"
            )

        if release.get("draft") is not True:
            if resolved_tag_sha is None:
                raise ReleaseStateError("A published release exists but its tag reference is missing")
            if not _release_assets_complete(release, expected_assets):
                raise ReleaseStateError("The published release is missing an expected non-empty artifact")
            return ReleasePlan(
                tag_action="reuse-tag",
                release_action="already-published",
                release_id=release_id,
                tag_sha=resolved_tag_sha,
            )
        release_action = "reuse-draft"

    return ReleasePlan(
        tag_action="create-tag" if resolved_tag_sha is None else "reuse-tag",
        release_action=release_action,
        release_id=release_id,
        tag_sha=resolved_tag_sha,
    )


def _read_json(path: Path) -> dict[str, Any]:
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ReleaseStateError(f"Could not read GitHub response {path}: {exc}") from exc
    if not isinstance(payload, dict):
        raise ReleaseStateError(f"GitHub response {path} is not a JSON object")
    return payload


def _write_github_output(path: Path, plan: ReleasePlan) -> None:
    values = {
        "tag_action": plan.tag_action,
        "release_action": plan.release_action,
        "release_id": plan.release_id or "",
        "tag_sha": plan.tag_sha or "",
    }
    with path.open("a", encoding="utf-8") as output:
        for key, value in values.items():
            output.write(f"{key}={value}\n")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag-name", required=True)
    parser.add_argument("--main-sha", required=True)
    parser.add_argument("--tag-file", type=Path, required=True)
    parser.add_argument("--tag-object-file", type=Path)
    parser.add_argument("--release-file", type=Path, required=True)
    parser.add_argument("--expected-asset", action="append", required=True)
    parser.add_argument("--github-output", type=Path)
    args = parser.parse_args(argv)

    try:
        tag_ref = _read_json(args.tag_file)
        tag_object = _read_json(args.tag_object_file) if args.tag_object_file else None
        release = _read_json(args.release_file)
        plan = decide_release(
            tag_name=args.tag_name,
            main_sha=args.main_sha,
            tag_sha=resolve_tag_sha(tag_ref, tag_object),
            release=release,
            expected_assets=set(args.expected_asset),
        )
        if args.github_output:
            _write_github_output(args.github_output, plan)
        print(json.dumps(plan.__dict__, sort_keys=True))
        return 0
    except ReleaseStateError as exc:
        print(f"release state error: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
