import unittest

from scripts.release_state import ReleaseStateError, decide_release, resolve_tag_sha


MAIN_SHA = "a" * 40
OTHER_SHA = "b" * 40
TAG_NAME = "v0.10.0"
APK = "StayLeafy-0.10.0.apk"
CHECKSUM = "StayLeafy-0.10.0.apk.sha256"
EXPECTED = {APK, CHECKSUM}


def lightweight_tag(sha=MAIN_SHA):
    return {"ref": f"refs/tags/{TAG_NAME}", "object": {"type": "commit", "sha": sha}}


def draft_release(release_id=123, assets=None):
    return {
        "id": release_id,
        "tag_name": TAG_NAME,
        "target_commitish": MAIN_SHA,
        "draft": True,
        "assets": assets or [],
    }


def published_release(assets):
    release = draft_release(assets=assets)
    release["draft"] = False
    return release


class ReleaseStateTest(unittest.TestCase):
    def test_missing_tag_and_release_create_both(self):
        plan = decide_release(
            tag_name=TAG_NAME,
            main_sha=MAIN_SHA,
            tag_sha=None,
            release={"missing": True},
            expected_assets=EXPECTED,
        )

        self.assertEqual(plan.tag_action, "create-tag")
        self.assertEqual(plan.release_action, "create-draft")
        self.assertIsNone(plan.release_id)

    def test_existing_matching_tag_and_draft_are_reused(self):
        plan = decide_release(
            tag_name=TAG_NAME,
            main_sha=MAIN_SHA,
            tag_sha=MAIN_SHA,
            release=draft_release(),
            expected_assets=EXPECTED,
        )

        self.assertEqual(plan.tag_action, "reuse-tag")
        self.assertEqual(plan.release_action, "reuse-draft")
        self.assertEqual(plan.release_id, "123")

    def test_tag_mismatch_is_rejected_before_any_release_mutation(self):
        with self.assertRaisesRegex(ReleaseStateError, "points to"):
            decide_release(
                tag_name=TAG_NAME,
                main_sha=MAIN_SHA,
                tag_sha=OTHER_SHA,
                release={"missing": True},
                expected_assets=EXPECTED,
            )

    def test_published_complete_release_is_already_done(self):
        assets = [{"name": name, "size": 10, "state": "uploaded"} for name in EXPECTED]
        plan = decide_release(
            tag_name=TAG_NAME,
            main_sha=MAIN_SHA,
            tag_sha=MAIN_SHA,
            release=published_release(assets),
            expected_assets=EXPECTED,
        )

        self.assertEqual(plan.release_action, "already-published")

    def test_published_incomplete_release_is_rejected(self):
        with self.assertRaisesRegex(ReleaseStateError, "missing"):
            decide_release(
                tag_name=TAG_NAME,
                main_sha=MAIN_SHA,
                tag_sha=MAIN_SHA,
                release=published_release([{"name": APK, "size": 10, "state": "uploaded"}]),
                expected_assets=EXPECTED,
            )

    def test_zero_length_published_asset_is_incomplete(self):
        assets = [
            {"name": APK, "size": 10, "state": "uploaded"},
            {"name": CHECKSUM, "size": 0, "state": "uploaded"},
        ]
        with self.assertRaisesRegex(ReleaseStateError, "missing"):
            decide_release(
                tag_name=TAG_NAME,
                main_sha=MAIN_SHA,
                tag_sha=MAIN_SHA,
                release=published_release(assets),
                expected_assets=EXPECTED,
            )

    def test_draft_without_tag_must_target_selected_sha(self):
        release = draft_release()
        release["target_commitish"] = OTHER_SHA
        with self.assertRaisesRegex(ReleaseStateError, "target"):
            decide_release(
                tag_name=TAG_NAME,
                main_sha=MAIN_SHA,
                tag_sha=None,
                release=release,
                expected_assets=EXPECTED,
            )

    def test_unuploaded_asset_is_not_treated_as_complete(self):
        assets = [
            {"name": APK, "size": 10, "state": "uploaded"},
            {"name": CHECKSUM, "size": 10, "state": "starter"},
        ]
        with self.assertRaisesRegex(ReleaseStateError, "missing"):
            decide_release(
                tag_name=TAG_NAME,
                main_sha=MAIN_SHA,
                tag_sha=MAIN_SHA,
                release=published_release(assets),
                expected_assets=EXPECTED,
            )

    def test_annotated_tag_resolves_to_commit(self):
        tag_ref = {"object": {"type": "tag", "sha": "c" * 40}}
        tag_object = {"object": {"type": "commit", "sha": MAIN_SHA}}
        self.assertEqual(resolve_tag_sha(tag_ref, tag_object), MAIN_SHA)

    def test_unresolved_annotated_tag_is_rejected(self):
        with self.assertRaisesRegex(ReleaseStateError, "could not be resolved"):
            resolve_tag_sha({"object": {"type": "tag", "sha": "c" * 40}})


if __name__ == "__main__":
    unittest.main()
