import base64
import datetime as dt
import os
from pathlib import Path
import plistlib
import struct
import tempfile
import unittest
from unittest import mock

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec, rsa
from cryptography.hazmat.primitives.serialization import pkcs12
from cryptography.x509.oid import NameOID

import app_store as asc
import package_app as package


class SigningInputsTest(unittest.TestCase):
    def setUp(self):
        key = ec.generate_private_key(ec.SECP256R1())
        self.api_pem = key.private_bytes(serialization.Encoding.PEM,
                                       serialization.PrivateFormat.PKCS8, serialization.NoEncryption())
        self.environment = {
            "ASC_KEY_ID": "ABCDEFGHIJ", "ASC_ISSUER_ID": "12345678-1234-1234-1234-123456789012",
            "ASC_PRIVATE_KEY": self.api_pem.decode(), "KGS_BUILD_NUMBER": "2.1",
        }

    def test_upload_key_uses_apple_search_directory_and_filename_with_private_permissions(self):
        with tempfile.TemporaryDirectory() as directory, mock.patch.dict(os.environ, self.environment):
            path = package.prepare_api_key(directory)
            self.assertEqual(path, Path(directory) / "private_keys/AuthKey_ABCDEFGHIJ.p8")
            self.assertEqual(path.read_bytes(), self.api_pem)
            self.assertEqual(path.stat().st_mode & 0o777, 0o600)
            self.assertEqual(path.parent.stat().st_mode & 0o777, 0o700)

    def test_invalid_key_id_or_malformed_secret_is_rejected(self):
        for variable, value in [("ASC_KEY_ID", "../outside"), ("ASC_PRIVATE_KEY", "not a PEM key"),
                                ("ASC_ISSUER_ID", "not-an-issuer")]:
            with self.subTest(variable=variable), tempfile.TemporaryDirectory() as directory, \
                    mock.patch.dict(os.environ, {**self.environment, variable: value}):
                with self.assertRaises((ValueError, asc.AppleError)):
                    package.prepare_api_key(directory)

    def test_prepared_identity_exports_locally_with_manual_profile(self):
        key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "Test Distribution")])
        now = dt.datetime.now(dt.timezone.utc)
        cert = (x509.CertificateBuilder().subject_name(name).issuer_name(name)
                .public_key(key.public_key()).serial_number(1).not_valid_before(now - dt.timedelta(days=1))
                .not_valid_after(now + dt.timedelta(days=30)).sign(key, hashes.SHA256()))
        p12 = pkcs12.serialize_key_and_certificates(b"test", key, cert, None,
                                                  serialization.BestAvailableEncryption(b"test-password"))
        profile = ProfileTest().profile()
        profile.update(Name="KGS Calendar TestFlight", DeveloperCertificates=[cert.public_bytes(serialization.Encoding.DER)])
        env = {**self.environment, "APPLE_TEAM_ID": "TEAM123456", "APPLE_P12_PASSWORD": "test-password",
               "APPLE_DISTRIBUTION_P12_BASE64": base64.b64encode(p12).decode(),
               "APPLE_PROFILE_BASE64": base64.b64encode(b"test-cms-profile").decode()}
        with tempfile.TemporaryDirectory() as directory, mock.patch.dict(os.environ, env), \
                mock.patch.object(package.subprocess, "check_output", return_value=plistlib.dumps(profile)):
            package.prepare_inputs(directory)
            options = plistlib.loads((Path(directory) / "ExportOptions.plist").read_bytes())
            self.assertEqual(options["destination"], "export")
            self.assertEqual(options["signingStyle"], "manual")
            self.assertEqual(options["provisioningProfiles"], {asc.BUNDLE_ID: profile["Name"]})
            self.assertFalse(options["manageAppVersionAndBuildNumber"])


class ProfileTest(unittest.TestCase):
    def profile(self):
        return {"UUID": "12345678-1234-1234-1234-123456789012", "TeamIdentifier": ["TEAM123456"],
                "ExpirationDate": dt.datetime(2099, 1, 1), "DeveloperCertificates": [b"certificate"],
                "Entitlements": {"application-identifier": "TEAM123456." + asc.BUNDLE_ID,
                                 "get-task-allow": False}}

    def test_matching_app_store_profile_is_accepted(self):
        package.validate_profile(self.profile(), b"certificate", "TEAM123456")

    def test_wrong_app_team_identity_expiry_and_profile_types_are_rejected(self):
        variants = []
        for field, value in [("TeamIdentifier", ["OTHER12345"]), ("DeveloperCertificates", [b"other"]),
                             ("ExpirationDate", dt.datetime(2000, 1, 1)),
                             ("ProvisionedDevices", ["device"]), ("ProvisionsAllDevices", True),
                             ("UUID", "../outside")]:
            profile = self.profile()
            profile[field] = value
            variants.append(profile)
        for field, value in [("application-identifier", "TEAM123456.other.app"), ("get-task-allow", True)]:
            profile = self.profile()
            profile["Entitlements"][field] = value
            variants.append(profile)
        for profile in variants:
            with self.subTest(profile=profile), self.assertRaises(ValueError):
                package.validate_profile(profile, b"certificate", "TEAM123456")

    def test_build_number_restrictions(self):
        for value in ("1", "1.1", "99.2", "9999.99.99"):
            self.assertEqual(package.check_build_number(value), value)
        for value in ("0", "1.100", "10000", "1.2.3.4", "202611010000", "$(bad)"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                package.check_build_number(value)


class ArchiveTest(unittest.TestCase):
    def make_archive(self, directory):
        app = Path(directory) / "Products/Applications/KgsCalendar.app"
        app.mkdir(parents=True)
        info = {"CFBundleIdentifier": asc.BUNDLE_ID, "CFBundleExecutable": "KgsCalendar",
                "CFBundleVersion": "1.1", "CFBundleShortVersionString": "0.1.0",
                "CFBundleSupportedPlatforms": ["iPhoneOS"], "UIDeviceFamily": [1, 2],
                "CFBundleIcons": {"CFBundlePrimaryIcon": {"CFBundleIconName": "AppIcon"}},
                "ITSAppUsesNonExemptEncryption": False}
        (app / "Info.plist").write_bytes(plistlib.dumps(info))
        (app / "Assets.car").touch()
        (app / "KgsCalendar").touch()
        (app / "PrivacyInfo.xcprivacy").write_bytes(plistlib.dumps({"NSPrivacyTracking": False}))
        return app, info

    @mock.patch("builtins.print")
    def test_complete_device_archive_is_accepted(self, log):
        with tempfile.TemporaryDirectory() as directory:
            self.make_archive(directory)
            package.validate_archive(directory, "1.1")

    def test_simulator_wrong_app_missing_ipad_icon_and_wrong_build_are_rejected(self):
        cases = [("CFBundleSupportedPlatforms", ["iPhoneSimulator"]),
                 ("CFBundleIdentifier", "other.app"), ("UIDeviceFamily", [1]),
                 ("CFBundleIcons", {}), ("CFBundleVersion", "2.1")]
        for field, value in cases:
            with self.subTest(field=field), tempfile.TemporaryDirectory() as directory:
                app, info = self.make_archive(directory)
                info[field] = value
                (app / "Info.plist").write_bytes(plistlib.dumps(info))
                with self.assertRaises(ValueError):
                    package.validate_archive(directory, "1.1")

    def test_missing_privacy_manifest_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            app, _ = self.make_archive(directory)
            (app / "PrivacyInfo.xcprivacy").unlink()
            with self.assertRaises(FileNotFoundError):
                package.validate_archive(directory)


class ProjectPackagingTest(unittest.TestCase):
    def test_project_icon_is_opaque_1024_pixel_png(self):
        root = Path(__file__).resolve().parents[2]
        icon = root / "iosApp/KgsCalendar/Assets.xcassets/AppIcon.appiconset/AppIcon.png"
        data = icon.read_bytes()
        self.assertEqual(data[:8], b"\x89PNG\r\n\x1a\n")
        width, height, depth, color_type = struct.unpack(">IIBB", data[16:26])
        self.assertEqual((width, height, depth, color_type), (1024, 1024, 8, 2))
        self.assertGreater(len(data), 1000)

    def test_ipad_orientations_and_version_substitution(self):
        root = Path(__file__).resolve().parents[2]
        info = plistlib.loads((root / "iosApp/KgsCalendar/Info.plist").read_bytes())
        self.assertEqual(len(info["UISupportedInterfaceOrientations~ipad"]), 4)
        self.assertEqual(info["CFBundleVersion"], "$(CURRENT_PROJECT_VERSION)")
        self.assertEqual(info["CFBundleShortVersionString"], "$(MARKETING_VERSION)")


if __name__ == "__main__":
    unittest.main()
