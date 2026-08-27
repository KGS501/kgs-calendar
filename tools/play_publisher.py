#!/usr/bin/env python3
"""Build and publish KGS Calendar to Google Play's internal test track."""

from __future__ import annotations

import argparse
import base64
import json
import os
from pathlib import Path
import shutil
import stat
import subprocess
import sys
import tempfile
import time
from typing import Any
from urllib import error, parse, request


PACKAGE_NAME = "com.kgs501.kgscalendar"
TRACK = "internal"
ANDROID_PUBLISHER_SCOPE = "https://www.googleapis.com/auth/androidpublisher"
API_ROOT = "https://androidpublisher.googleapis.com/androidpublisher/v3"
UPLOAD_ROOT = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3"
REPO_ROOT = Path(__file__).resolve().parents[1]


class PublisherError(RuntimeError):
    """A safe, user-facing publishing error."""


def default_config_path() -> Path:
    config_home = Path(os.environ.get("XDG_CONFIG_HOME", Path.home() / ".config"))
    return config_home / "kgs-calendar" / "publisher-config.json"


def _require_private_file(path: Path, label: str) -> None:
    if not path.is_file():
        raise PublisherError(f"{label} does not exist: {path}")
    if os.name != "nt":
        mode = stat.S_IMODE(path.stat().st_mode)
        if mode & 0o077:
            raise PublisherError(
                f"{label} must not be readable by group/others: {path} "
                f"(current mode {mode:o}; run chmod 600 on it)"
            )


def load_config(path: Path, require_signing: bool) -> dict[str, str]:
    _require_private_file(path, "Publisher config")
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise PublisherError(f"Cannot read publisher config {path}: {exc}") from exc

    required = ["package_name", "service_account_file"]
    if require_signing:
        required.extend(
            ["keystore_file", "keystore_password", "key_alias", "key_password"]
        )
    missing = [key for key in required if not isinstance(raw.get(key), str) or not raw[key]]
    if missing:
        raise PublisherError(f"Publisher config is missing: {', '.join(missing)}")
    if raw["package_name"] != PACKAGE_NAME:
        raise PublisherError(
            f"Refusing to publish unexpected package {raw['package_name']!r}; "
            f"expected {PACKAGE_NAME!r}"
        )

    credentials_path = Path(raw["service_account_file"]).expanduser().resolve()
    _require_private_file(credentials_path, "Service-account key")
    raw["service_account_file"] = str(credentials_path)
    if require_signing:
        keystore_path = Path(raw["keystore_file"]).expanduser().resolve()
        _require_private_file(keystore_path, "Upload keystore")
        raw["keystore_file"] = str(keystore_path)
    return raw


def _base64url(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode("ascii")


def _http_json(
    url: str,
    *,
    method: str = "GET",
    data: bytes | None = None,
    headers: dict[str, str] | None = None,
    timeout: int = 120,
) -> dict[str, Any]:
    http_request = request.Request(url, data=data, method=method, headers=headers or {})
    try:
        with request.urlopen(http_request, timeout=timeout) as response:
            body = response.read()
    except error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        try:
            details = json.loads(body).get("error", {}).get("message", body)
        except json.JSONDecodeError:
            details = body
        raise PublisherError(f"Google API returned HTTP {exc.code}: {details}") from exc
    except error.URLError as exc:
        raise PublisherError(f"Cannot reach Google API: {exc.reason}") from exc
    if not body:
        return {}
    try:
        return json.loads(body)
    except json.JSONDecodeError as exc:
        raise PublisherError(f"Google API returned invalid JSON from {url}") from exc


def service_account_access_token(credentials_path: Path) -> str:
    try:
        credentials = json.loads(credentials_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise PublisherError(f"Cannot read service-account key: {exc}") from exc
    required = ("client_email", "private_key", "token_uri")
    missing = [key for key in required if not credentials.get(key)]
    if credentials.get("type") != "service_account" or missing:
        raise PublisherError(
            "The Google credential must be a service-account JSON key; missing: "
            + ", ".join(missing or ["type=service_account"])
        )
    if shutil.which("openssl") is None:
        raise PublisherError("OpenSSL is required to authenticate the service account")

    now = int(time.time())
    header = _base64url(json.dumps({"alg": "RS256", "typ": "JWT"}, separators=(",", ":")).encode())
    claims = {
        "iss": credentials["client_email"],
        "scope": ANDROID_PUBLISHER_SCOPE,
        "aud": credentials["token_uri"],
        "iat": now - 30,
        "exp": now + 3600,
    }
    payload = _base64url(json.dumps(claims, separators=(",", ":")).encode())
    unsigned_token = f"{header}.{payload}".encode("ascii")

    key_path: Path | None = None
    try:
        with tempfile.NamedTemporaryFile("w", encoding="utf-8", delete=False) as key_file:
            key_file.write(credentials["private_key"])
            key_path = Path(key_file.name)
        if os.name != "nt":
            key_path.chmod(0o600)
        signed = subprocess.run(
            ["openssl", "dgst", "-sha256", "-sign", str(key_path)],
            input=unsigned_token,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
        if signed.returncode != 0:
            raise PublisherError(
                "OpenSSL could not sign the Google authentication request: "
                + signed.stderr.decode("utf-8", errors="replace").strip()
            )
    finally:
        if key_path is not None:
            key_path.unlink(missing_ok=True)

    assertion = f"{unsigned_token.decode('ascii')}.{_base64url(signed.stdout)}"
    token_body = parse.urlencode(
        {
            "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
            "assertion": assertion,
        }
    ).encode("ascii")
    response = _http_json(
        credentials["token_uri"],
        method="POST",
        data=token_body,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
    )
    access_token = response.get("access_token")
    if not isinstance(access_token, str) or not access_token:
        raise PublisherError("Google OAuth response did not contain an access token")
    return access_token


class PlayEdit:
    def __init__(self, access_token: str) -> None:
        self._headers = {"Authorization": f"Bearer {access_token}"}
        self.edit_id: str | None = None
        self.committed = False

    def _url(self, suffix: str = "") -> str:
        package = parse.quote(PACKAGE_NAME, safe="")
        base = f"{API_ROOT}/applications/{package}/edits"
        return f"{base}/{self.edit_id}{suffix}" if self.edit_id else base

    def create(self) -> str:
        response = _http_json(
            self._url(),
            method="POST",
            data=b"{}",
            headers={**self._headers, "Content-Type": "application/json"},
        )
        self.edit_id = response.get("id")
        if not self.edit_id:
            raise PublisherError("Google Play did not return an edit ID")
        return self.edit_id

    def upload_bundle(self, bundle_path: Path) -> int:
        if not self.edit_id:
            raise PublisherError("Cannot upload a bundle before creating an edit")
        package = parse.quote(PACKAGE_NAME, safe="")
        edit = parse.quote(self.edit_id, safe="")
        url = f"{UPLOAD_ROOT}/applications/{package}/edits/{edit}/bundles?uploadType=media"
        response = _http_json(
            url,
            method="POST",
            data=bundle_path.read_bytes(),
            headers={**self._headers, "Content-Type": "application/octet-stream"},
            timeout=300,
        )
        version_code = response.get("versionCode")
        try:
            return int(version_code)
        except (TypeError, ValueError) as exc:
            raise PublisherError("Bundle upload did not return a valid version code") from exc

    def update_internal_track(
        self,
        version_code: int,
        notes: str,
        language: str,
        localized_notes: list[tuple[str, str]] | None = None,
    ) -> None:
        release_notes = [{"language": language, "text": notes}]
        release_notes.extend(
            {"language": localized_language, "text": localized_text}
            for localized_language, localized_text in localized_notes or []
        )
        body = {
            "track": TRACK,
            "releases": [
                {
                    "name": f"Version code {version_code}",
                    "versionCodes": [str(version_code)],
                    "status": "completed",
                    "releaseNotes": release_notes,
                }
            ],
        }
        track = parse.quote(TRACK, safe="")
        _http_json(
            self._url(f"/tracks/{track}"),
            method="PUT",
            data=json.dumps(body, separators=(",", ":")).encode("utf-8"),
            headers={**self._headers, "Content-Type": "application/json"},
        )

    def validate(self) -> None:
        _http_json(self._url(":validate"), method="POST", data=b"", headers=self._headers)

    def commit(self) -> None:
        url = self._url(":commit") + "?changesInReviewBehavior=ERROR_IF_IN_REVIEW"
        _http_json(url, method="POST", data=b"", headers=self._headers)
        self.committed = True

    def delete(self) -> None:
        if self.edit_id and not self.committed:
            _http_json(self._url(), method="DELETE", headers=self._headers)
            self.edit_id = None

    def __enter__(self) -> "PlayEdit":
        self.create()
        return self

    def __exit__(self, exc_type: object, exc: object, traceback: object) -> None:
        if not self.committed and self.edit_id:
            try:
                self.delete()
            except PublisherError as cleanup_error:
                print(f"Warning: could not delete uncommitted Play edit: {cleanup_error}", file=sys.stderr)


def _gradle_command() -> list[str]:
    if os.name == "nt":
        return [str(REPO_ROOT / "gradlew.bat")]
    return ["bash", str(REPO_ROOT / "gradlew")]


def build_bundle(config: dict[str, str], skip_tests: bool) -> Path:
    env = os.environ.copy()
    env.update(
        {
            "KGS_RELEASE_STORE_FILE": config["keystore_file"],
            "KGS_RELEASE_STORE_PASSWORD": config["keystore_password"],
            "KGS_RELEASE_KEY_ALIAS": config["key_alias"],
            "KGS_RELEASE_KEY_PASSWORD": config["key_password"],
        }
    )
    gradle = _gradle_command()
    if not skip_tests:
        print("Running debug unit tests...", flush=True)
        subprocess.run(gradle + [":app:testDebugUnitTest"], cwd=REPO_ROOT, check=True)
    print("Building signed release app bundle...", flush=True)
    subprocess.run(
        gradle + [":app:bundleRelease", "--no-configuration-cache", "--no-daemon"],
        cwd=REPO_ROOT,
        env=env,
        check=True,
    )
    bundle = REPO_ROOT / "app/build/outputs/bundle/release/app-release.aab"
    if not bundle.is_file():
        raise PublisherError(f"Gradle succeeded but the bundle is missing: {bundle}")
    return bundle


def verify_bundle(bundle: Path) -> None:
    if not bundle.is_file():
        raise PublisherError(f"App bundle does not exist: {bundle}")
    if bundle.suffix.lower() != ".aab":
        raise PublisherError(f"Expected an .aab app bundle, got: {bundle}")
    if shutil.which("jarsigner") is None:
        raise PublisherError("jarsigner is required to verify the app bundle signature")
    result = subprocess.run(
        ["jarsigner", "-verify", str(bundle)],
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=False,
    )
    if result.returncode != 0:
        raise PublisherError(f"App bundle signature verification failed:\n{result.stdout.strip()}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, default=default_config_path())
    subparsers = parser.add_subparsers(dest="command", required=True)

    verify = subparsers.add_parser("verify", help="build and verify access without publishing")
    verify.add_argument("--skip-tests", action="store_true")

    publish = subparsers.add_parser("publish", help="publish a completed internal-test release")
    publish.add_argument("--aab", type=Path, help="upload this ready AAB instead of building")
    publish.add_argument("--skip-tests", action="store_true", help="skip tests when building")
    publish.add_argument("--release-notes", required=True)
    publish.add_argument("--language", default="en-US")
    publish.add_argument(
        "--localized-release-note",
        action="append",
        default=[],
        metavar="LANGUAGE=TEXT",
        help="add another localized release note; may be repeated",
    )
    publish.add_argument(
        "--yes",
        action="store_true",
        help="confirm the external release; required for publishing",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.command == "publish" and not args.yes:
        raise PublisherError("Publishing requires --yes after reviewing the release notes and AAB")
    require_signing = args.command == "verify" or getattr(args, "aab", None) is None
    config = load_config(args.config.expanduser().resolve(), require_signing=require_signing)

    if args.command == "verify":
        bundle = build_bundle(config, skip_tests=args.skip_tests)
        verify_bundle(bundle)
        print("Authenticating with Google Play...", flush=True)
        token = service_account_access_token(Path(config["service_account_file"]))
        with PlayEdit(token) as edit:
            edit.validate()
        print("Verification succeeded. No bundle was uploaded and nothing was published.")
        return 0

    bundle = args.aab.expanduser().resolve() if args.aab else build_bundle(config, args.skip_tests)
    verify_bundle(bundle)
    notes = args.release_notes.strip()
    if not notes:
        raise PublisherError("Release notes cannot be empty")
    localized_notes: list[tuple[str, str]] = []
    seen_languages = {args.language}
    for localized_note in args.localized_release_note:
        language, separator, text = localized_note.partition("=")
        language = language.strip()
        text = text.strip()
        if not separator or not language or not text:
            raise PublisherError(
                "Localized release notes must use LANGUAGE=TEXT with both values present"
            )
        if language in seen_languages:
            raise PublisherError(f"Duplicate release-note language: {language}")
        seen_languages.add(language)
        localized_notes.append((language, text))
    print(f"Uploading {bundle} to Google Play internal testing...", flush=True)
    token = service_account_access_token(Path(config["service_account_file"]))
    with PlayEdit(token) as edit:
        version_code = edit.upload_bundle(bundle)
        edit.update_internal_track(
            version_code,
            notes,
            args.language,
            localized_notes=localized_notes,
        )
        edit.validate()
        edit.commit()
    print(f"Published version code {version_code} to the Google Play internal track.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except PublisherError as exc:
        print(f"Error: {exc}", file=sys.stderr)
        raise SystemExit(1)
    except subprocess.CalledProcessError as exc:
        print(f"Error: command failed with exit code {exc.returncode}", file=sys.stderr)
        raise SystemExit(exc.returncode or 1)
