from __future__ import annotations

import importlib.util
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT_PATH = Path(__file__).parents[1] / "validate_version.py"
SPEC = importlib.util.spec_from_file_location("validate_version", SCRIPT_PATH)
assert SPEC is not None and SPEC.loader is not None
validate_version = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = validate_version
SPEC.loader.exec_module(validate_version)


class VersionPropertiesTest(unittest.TestCase):
    def assertInvalid(self, text: str) -> None:
        with self.assertRaises(validate_version.ValidationError):
            validate_version.parse_version_properties(text)

    def test_valid_version_and_comments(self) -> None:
        version = validate_version.parse_version_properties(
            "# release\nVERSION_NAME=0.10.0\nVERSION_CODE=26\n"
        )
        self.assertEqual(validate_version.Version("0.10.0", 26), version)

    def test_rejects_missing_duplicate_unknown_and_malformed_fields(self) -> None:
        self.assertInvalid("VERSION_NAME=0.10.0\n")
        self.assertInvalid("VERSION_CODE=26\n")
        self.assertInvalid("VERSION_NAME=0.10.0\nVERSION_NAME=0.10.1\nVERSION_CODE=26\n")
        self.assertInvalid("VERSION_NAME=0.10.0\nVERSION_CODE=26\nVERSION_CODE=27\n")
        self.assertInvalid("VERSION_NAME=0.10.0\nVERSION_CODE=26\nOTHER=value\n")
        self.assertInvalid("VERSION_NAME = 0.10.0\nVERSION_CODE=26\n")
        self.assertInvalid("VERSION_NAME=0.10.0\nVERSION_CODE=26=27\n")
        self.assertInvalid("VERSION_NAME=0.10.0\nVERSION_CODE= 26\n")

    def test_rejects_non_simple_semver_and_bad_version_codes(self) -> None:
        for name in ("1.2", "1.2.3-alpha", "1.2.3+build", "01.2.3", "1.02.3", "1.2.03"):
            self.assertInvalid(f"VERSION_NAME={name}\nVERSION_CODE=26\n")
        for code in ("0", "026", "-1", "+1", "2100000001", "1.5"):
            self.assertInvalid(f"VERSION_NAME=0.10.0\nVERSION_CODE={code}\n")


class ConventionalCommitTest(unittest.TestCase):
    def test_allowed_types_and_scopes(self) -> None:
        for change_type in sorted(validate_version.CONVENTIONAL_TYPES - {"feat"}):
            self.assertEqual((change_type, False), validate_version.parse_conventional_title(f"{change_type}: change"))
        self.assertEqual(("feat", False), validate_version.parse_conventional_title("feat(ui): add plants"))
        self.assertEqual(("fix", True), validate_version.parse_conventional_title("fix(storage)!: migrate data"))

    def test_rejects_invalid_titles(self) -> None:
        for title in (
            "feature: add plants",
            "Feat: add plants",
            "feat(): add plants",
            "feat( ): add plants",
            "feat(ui) add plants",
            "feat:  add plants",
            "feat:",
            "feat: ",
            "feat(ui!!): add plants",
        ):
            with self.subTest(title=title):
                with self.assertRaises(validate_version.ValidationError):
                    validate_version.parse_conventional_title(title)


class PullRequestValidationTest(unittest.TestCase):
    def test_expected_major_minor_and_patch_bumps(self) -> None:
        base = validate_version.Version("1.2.3", 7)
        self.assertEqual(validate_version.Version("2.0.0", 8), validate_version.expected_version(base, "fix!: break API"))
        self.assertEqual(validate_version.Version("1.3.0", 8), validate_version.expected_version(base, "feat: add feature"))
        self.assertEqual(validate_version.Version("1.2.4", 8), validate_version.expected_version(base, "refactor(core): tidy code"))

    def test_rejects_wrong_semver(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            self._git(repo, "init")
            self._write(repo / "app/build.gradle.kts", 'defaultConfig {\n    versionCode = 25\n    versionName = "0.9.3"\n}\n')
            self._git(repo, "add", ".")
            self._git(repo, "-c", "user.name=Test", "-c", "user.email=test@example.com", "commit", "-m", "base")
            base_ref = self._git(repo, "rev-parse", "HEAD").strip()
            self._write(repo / "version.properties", "VERSION_NAME=0.10.1\nVERSION_CODE=26\n")
            with self.assertRaises(validate_version.ValidationError):
                self._validate_from_repo(repo, base_ref, "feat: add feature")

    def test_rejects_unchanged_decremented_and_skipped_version_codes(self) -> None:
        for code in (25, 24, 27):
            with self.subTest(code=code), tempfile.TemporaryDirectory() as directory:
                repo = Path(directory)
                self._git(repo, "init")
                self._write(repo / "app/build.gradle.kts", 'defaultConfig {\n    versionCode = 25\n    versionName = "0.9.3"\n}\n')
                self._git(repo, "add", ".")
                self._git(repo, "-c", "user.name=Test", "-c", "user.email=test@example.com", "commit", "-m", "base")
                base_ref = self._git(repo, "rev-parse", "HEAD").strip()
                self._write(repo / "version.properties", f"VERSION_NAME=0.10.0\nVERSION_CODE={code}\n")
                with self.assertRaises(validate_version.ValidationError):
                    self._validate_from_repo(repo, base_ref, "feat: add feature")

    def test_bootstrap_fallback_reads_legacy_gradle_version(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            self._git(repo, "init")
            self._write(repo / "app/build.gradle.kts", 'defaultConfig {\n    versionCode = 25\n    versionName = "0.9.3"\n}\n')
            self._git(repo, "add", ".")
            self._git(repo, "-c", "user.name=Test", "-c", "user.email=test@example.com", "commit", "-m", "base")
            base_ref = self._git(repo, "rev-parse", "HEAD").strip()
            self._write(repo / "version.properties", "VERSION_NAME=0.10.0\nVERSION_CODE=26\n")
            self._validate_from_repo(repo, base_ref, "feat: add release version")

    def test_malformed_version_file_does_not_use_legacy_fallback(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            self._git(repo, "init")
            self._write(repo / "version.properties", "VERSION_NAME=0.9.3\nVERSION_NAME=0.9.4\nVERSION_CODE=25\n")
            self._write(repo / "app/build.gradle.kts", 'versionCode = 25\nversionName = "0.9.3"\n')
            self._git(repo, "add", ".")
            self._git(repo, "-c", "user.name=Test", "-c", "user.email=test@example.com", "commit", "-m", "base")
            base_ref = self._git(repo, "rev-parse", "HEAD").strip()
            self._write(repo / "version.properties", "VERSION_NAME=0.10.0\nVERSION_CODE=26\n")
            with self.assertRaises(validate_version.ValidationError):
                self._validate_from_repo(repo, base_ref, "feat: add release version")

    def _validate_from_repo(self, repo: Path, base_ref: str, title: str) -> None:
        old_cwd = Path.cwd()
        try:
            os.chdir(repo)
            current = validate_version.read_version_properties(repo / "version.properties")
            validate_version.validate_pull_request(current, base_ref, title)
        finally:
            os.chdir(old_cwd)

    @staticmethod
    def _write(path: Path, text: str) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")

    @staticmethod
    def _git(repo: Path, *args: str) -> str:
        result = subprocess.run(["git", *args], cwd=repo, check=True, capture_output=True, text=True)
        return result.stdout


class CommandLineTest(unittest.TestCase):
    def test_arguments_must_be_paired(self) -> None:
        result = self._run("--base-ref", "deadbeef")
        self.assertEqual(2, result.returncode)
        self.assertIn("must be provided together", result.stderr)

    def test_github_output_is_written_only_after_validation(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self._write(root / "version.properties", "VERSION_NAME=0.10.0\nVERSION_CODE=26\n")
            output = root / "github-output.txt"
            result = self._run("--github-output", str(output), cwd=root)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual("version_name=0.10.0\nversion_code=26\n", output.read_text(encoding="utf-8"))

    def test_cli_roundtrips_major_minor_and_patch_against_version_properties_base(self) -> None:
        cases = (
            ("1.2.3", 40, "2.0.0", 41, "fix!: break API"),
            ("1.2.3", 40, "1.3.0", 41, "feat(ui): add feature"),
            ("1.2.3", 40, "1.2.4", 41, "fix: fix bug"),
        )
        for base_name, base_code, current_name, current_code, title in cases:
            with self.subTest(title=title), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                self._git(root, "init")
                self._write(root / "version.properties", f"VERSION_NAME={base_name}\nVERSION_CODE={base_code}\n")
                self._git(root, "add", ".")
                self._git(root, "-c", "user.name=Test", "-c", "user.email=test@example.com", "commit", "-m", "base")
                base_ref = self._git(root, "rev-parse", "HEAD").strip()
                self._write(root / "version.properties", f"VERSION_NAME={current_name}\nVERSION_CODE={current_code}\n")
                result = self._run("--base-ref", base_ref, "--title", title, cwd=root)
                self.assertEqual(0, result.returncode, result.stderr)

    def test_cli_rejects_missing_current_or_base_version_file(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            missing_current = self._run(cwd=root)
            self.assertEqual(1, missing_current.returncode)
            self.assertIn("file is missing", missing_current.stderr)

            self._git(root, "init")
            self._write(root / "README", "base without version metadata\n")
            self._git(root, "add", ".")
            self._git(root, "-c", "user.name=Test", "-c", "user.email=test@example.com", "commit", "-m", "base")
            base_ref = self._git(root, "rev-parse", "HEAD").strip()
            self._write(root / "version.properties", "VERSION_NAME=0.10.0\nVERSION_CODE=26\n")
            missing_base = self._run("--base-ref", base_ref, "--title", "feat: add release version", cwd=root)
            self.assertEqual(1, missing_base.returncode)
            self.assertIn("fallback is unavailable", missing_base.stderr)

    def test_invalid_version_does_not_create_github_output(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self._write(root / "version.properties", "VERSION_NAME=0.10\nVERSION_CODE=26\n")
            output = root / "github-output.txt"
            result = self._run("--github-output", str(output), cwd=root)
            self.assertEqual(1, result.returncode)
            self.assertFalse(output.exists())

    @staticmethod
    def _write(path: Path, text: str) -> None:
        path.write_text(text, encoding="utf-8")

    @staticmethod
    def _git(repo: Path, *args: str) -> str:
        result = subprocess.run(["git", *args], cwd=repo, check=True, capture_output=True, text=True)
        return result.stdout

    @staticmethod
    def _run(*args: str, cwd: Path | None = None) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [sys.executable, str(SCRIPT_PATH), *args],
            cwd=cwd,
            capture_output=True,
            text=True,
        )


if __name__ == "__main__":
    unittest.main()
