"""Validate device archives and prepare signing inputs without printing credentials."""

import argparse
import base64
import datetime as dt
import json
import os
from pathlib import Path
import plistlib
import re
import subprocess
import uuid

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.serialization import pkcs12

from app_store import BUNDLE_ID, private_write


def check_build_number(value):
    if not re.fullmatch(r"[1-9][0-9]{0,3}(?:\.[0-9]{1,2}){0,2}", value):
        raise ValueError("Build number must follow Apple's numeric CFBundleVersion format")
    return value


def validate_profile(profile, certificate_der, team_id, now=None):
    if not re.fullmatch(r"[A-Z0-9]{10}", team_id):
        raise ValueError("Invalid Apple Team ID")
    uuid.UUID(profile["UUID"])
    now = now or dt.datetime.now(dt.timezone.utc).replace(tzinfo=None)
    if profile["ExpirationDate"] <= now:
        raise ValueError("Provisioning profile has expired")
    if profile.get("TeamIdentifier") != [team_id]:
        raise ValueError("Provisioning profile belongs to another team")
    if profile["Entitlements"].get("application-identifier") != team_id + "." + BUNDLE_ID:
        raise ValueError("Provisioning profile belongs to another app")
    if profile["Entitlements"].get("get-task-allow", False):
        raise ValueError("A development profile cannot be used for TestFlight")
    if profile.get("ProvisionedDevices") or profile.get("ProvisionsAllDevices"):
        raise ValueError("Use an App Store distribution profile, not ad hoc or enterprise")
    if certificate_der not in profile["DeveloperCertificates"]:
        raise ValueError("Provisioning profile does not include the signing certificate")


def prepare_inputs(directory):
    check_build_number(os.environ["KGS_BUILD_NUMBER"])
    directory = Path(directory)
    directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    directory.chmod(0o700)
    for variable, filename in [("APPLE_DISTRIBUTION_P12_BASE64", "distribution.p12"),
                                ("APPLE_PROFILE_BASE64", "profile.mobileprovision")]:
        private_write(directory / filename, base64.b64decode(os.environ[variable], validate=True))
    private_write(directory / "AuthKey.p8", os.environ["ASC_PRIVATE_KEY"].encode())
    key, cert, _ = pkcs12.load_key_and_certificates((directory / "distribution.p12").read_bytes(),
                                                os.environ["APPLE_P12_PASSWORD"].encode())
    if key is None or cert is None:
        raise ValueError("Distribution identity requires a certificate and its private key")
    if key.public_key().public_numbers() != cert.public_key().public_numbers():
        raise ValueError("Distribution identity's private key does not match its certificate")
    if cert.not_valid_after_utc <= dt.datetime.now(dt.timezone.utc):
        raise ValueError("Distribution certificate has expired")
    profile_bytes = subprocess.check_output(
        ["security", "cms", "-D", "-i", str(directory / "profile.mobileprovision")], stderr=subprocess.PIPE)
    profile = plistlib.loads(profile_bytes)
    team_id = os.environ["APPLE_TEAM_ID"]
    validate_profile(profile, cert.public_bytes(serialization.Encoding.DER), team_id)
    private_write(directory / "settings.json", json.dumps({
        "uuid": profile["UUID"], "profile_name": profile["Name"], "team_id": team_id,
    }).encode())
    private_write(directory / "ExportOptions.plist", plistlib.dumps({
        "method": "app-store-connect", "destination": "upload", "teamID": team_id,
        "signingStyle": "manual", "signingCertificate": "Apple Distribution",
        "provisioningProfiles": {BUNDLE_ID: profile["Name"]},
        "manageAppVersionAndBuildNumber": False, "uploadSymbols": True,
        # Keep a tested release candidate eligible for a later App Store submission.
        # Uploading does not submit for review; tester access is configured separately.
        "stripSwiftSymbols": True, "testFlightInternalTestingOnly": False,
    }))


def validate_archive(archive, build_number=None):
    app = Path(archive) / "Products/Applications/KgsCalendar.app"
    info = plistlib.loads((app / "Info.plist").read_bytes())
    if info["CFBundleIdentifier"] != BUNDLE_ID:
        raise ValueError("Archive has the wrong bundle identifier")
    if info.get("CFBundleSupportedPlatforms") != ["iPhoneOS"]:
        raise ValueError("Archive is not built for physical iOS devices")
    if set(info.get("UIDeviceFamily", [])) != {1, 2}:
        raise ValueError("Archive must support both iPhone and iPad")
    if build_number and info["CFBundleVersion"] != check_build_number(build_number):
        raise ValueError("Archive has an unexpected build number")
    if not info.get("CFBundleIcons", {}).get("CFBundlePrimaryIcon"):
        raise ValueError("Archive does not contain the app icon metadata")
    if not (app / "Assets.car").is_file() or not (app / info["CFBundleExecutable"]).is_file():
        raise ValueError("Archive is missing its assets or executable")
    privacy = plistlib.loads((app / "PrivacyInfo.xcprivacy").read_bytes())
    if privacy.get("NSPrivacyTracking") is not False:
        raise ValueError("Unexpected tracking declaration in the validation app")
    if info.get("ITSAppUsesNonExemptEncryption") is not False:
        raise ValueError("Missing export-compliance declaration for this validation app")
    print(f"Validated device archive {info['CFBundleShortVersionString']} ({info['CFBundleVersion']})")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    prepare = commands.add_parser("prepare")
    prepare.add_argument("directory")
    validate = commands.add_parser("validate")
    validate.add_argument("archive")
    validate.add_argument("--build-number")
    args = parser.parse_args()
    if args.command == "prepare":
        prepare_inputs(args.directory)
    else:
        validate_archive(args.archive, args.build_number)


if __name__ == "__main__":
    main()
