import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest import mock

from tools import play_publisher


class PlayPublisherTest(unittest.TestCase):
    def test_base64url_omits_padding(self) -> None:
        self.assertEqual(play_publisher._base64url(b"test"), "dGVzdA")

    def test_load_config_rejects_another_package(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            credentials = root / "credentials.json"
            credentials.write_text("{}", encoding="utf-8")
            credentials.chmod(0o600)
            config = root / "publisher-config.json"
            config.write_text(
                json.dumps(
                    {
                        "package_name": "com.example.wrong",
                        "service_account_file": str(credentials),
                    }
                ),
                encoding="utf-8",
            )
            config.chmod(0o600)

            with self.assertRaisesRegex(play_publisher.PublisherError, "unexpected package"):
                play_publisher.load_config(config, require_signing=False)

    @unittest.skipUnless(shutil.which("openssl"), "OpenSSL is not installed")
    @mock.patch("tools.play_publisher._http_json", return_value={"access_token": "access-token"})
    def test_service_account_jwt_is_signed(self, http_json: mock.Mock) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            private_key = root / "private.pem"
            subprocess.run(
                ["openssl", "genpkey", "-algorithm", "RSA", "-out", str(private_key)],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                check=True,
            )
            credentials = root / "credentials.json"
            credentials.write_text(
                json.dumps(
                    {
                        "type": "service_account",
                        "client_email": "publisher@example.iam.gserviceaccount.com",
                        "private_key": private_key.read_text(encoding="utf-8"),
                        "token_uri": "https://oauth2.googleapis.test/token",
                    }
                ),
                encoding="utf-8",
            )

            token = play_publisher.service_account_access_token(credentials)

        self.assertEqual(token, "access-token")
        _, kwargs = http_json.call_args
        token_request = kwargs["data"].decode("ascii")
        self.assertIn("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer", token_request)
        self.assertIn("assertion=", token_request)

    @mock.patch("tools.play_publisher._http_json")
    def test_internal_track_is_completed(self, http_json: mock.Mock) -> None:
        edit = play_publisher.PlayEdit("token")
        edit.edit_id = "edit-1"

        edit.update_internal_track(
            24,
            "English release notes",
            "en-US",
            localized_notes=[("de-DE", "Deutsche Versionshinweise")],
        )

        _, kwargs = http_json.call_args
        payload = json.loads(kwargs["data"])
        self.assertEqual(payload["track"], "internal")
        self.assertEqual(payload["releases"][0]["status"], "completed")
        self.assertEqual(payload["releases"][0]["versionCodes"], ["24"])
        self.assertEqual(
            payload["releases"][0]["releaseNotes"],
            [
                {"language": "en-US", "text": "English release notes"},
                {"language": "de-DE", "text": "Deutsche Versionshinweise"},
            ],
        )

    @mock.patch("tools.play_publisher._http_json")
    def test_commit_refuses_to_cancel_an_existing_review(self, http_json: mock.Mock) -> None:
        edit = play_publisher.PlayEdit("token")
        edit.edit_id = "edit-1"

        edit.commit()

        url = http_json.call_args.args[0]
        self.assertIn("changesInReviewBehavior=ERROR_IF_IN_REVIEW", url)
        self.assertTrue(edit.committed)


if __name__ == "__main__":
    unittest.main()
