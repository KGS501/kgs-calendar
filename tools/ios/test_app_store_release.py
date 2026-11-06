import json
from pathlib import Path
import tempfile
import unittest

import app_store_release as release
from app_store import AppleError


def version(number, state):
    return {"id": number, "attributes": {"versionString": number, "appStoreState": state}}


class FakeClient:
    def __init__(self, versions=()):
        self.versions, self.calls = list(versions), []

    def list(self, path, query=None):
        return self.versions

    def request(self, method, path, data=None, query=None):
        self.calls.append((method, path, data))
        if method == "POST":
            return {"data": {"id": "new", "attributes": {}}}
        return {"data": None}


class ListingTest(unittest.TestCase):
    def test_listing_in_the_repo_fits_apples_limits(self):
        listing = json.loads(release.DEFAULT_LISTING.read_text(encoding="utf-8"))
        release.check_listing(listing)

    def test_field_over_the_limit_is_refused(self):
        with self.assertRaisesRegex(AppleError, "subtitle"):
            release.check_listing({"subtitle": "x" * 31})

    def test_empty_values_are_left_out(self):
        self.assertEqual(release.present({"a": "text", "b": "", "c": None}), {"a": "text"})


class DraftTest(unittest.TestCase):
    def test_new_version_is_created_after_a_released_one(self):
        client = FakeClient([version("2.0.0", "READY_FOR_SALE")])
        self.assertEqual(release.draft_version(client, "app", "2.0.1")["id"], "new")

    def test_open_draft_is_renumbered_instead_of_duplicated(self):
        client = FakeClient([version("2.0.1", "PREPARE_FOR_SUBMISSION")])
        release.draft_version(client, "app", "2.0.2")
        self.assertEqual([call[0] for call in client.calls], ["PATCH"])

    def test_released_version_is_not_reused(self):
        client = FakeClient([version("2.0.0", "READY_FOR_SALE")])
        with self.assertRaisesRegex(AppleError, "can't be edited"):
            release.draft_version(client, "app", "2.0.0")

    def test_submit_needs_a_build(self):
        client = FakeClient([version("2.0.0", "PREPARE_FOR_SUBMISSION")])
        with self.assertRaisesRegex(AppleError, "no build"):
            release.submit(client, "app", "2.0.0")
        self.assertFalse([call for call in client.calls if call[0] == "POST"])


class ReviewContactTest(unittest.TestCase):
    def test_incomplete_contact_writes_nothing(self):
        client = FakeClient()
        release.push_review_detail(client, "version", {"reviewNotes": "n"}, {"contactEmail": "a@b.c"})
        self.assertEqual(client.calls, [])


class ConfigTest(unittest.TestCase):
    def test_readable_key_is_refused(self):
        with tempfile.TemporaryDirectory() as directory:
            key, config = Path(directory) / "key.p8", Path(directory) / "config.json"
            key.write_text("k")
            key.chmod(0o644)
            config.write_text(json.dumps({"key_file": str(key), "key_id": "i", "issuer_id": "i", "app_id": "a"}))
            config.chmod(0o600)
            with self.assertRaisesRegex(AppleError, "chmod 600"):
                release.load_config(config)


if __name__ == "__main__":
    unittest.main()
