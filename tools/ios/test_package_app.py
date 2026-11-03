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

    def prepared_identity(self):
        """Environment of a valid signing identity, and a decoder for its two profiles."""
        key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "Test Distribution")])
        now = dt.datetime.now(dt.timezone.utc)
        cert = (x509.CertificateBuilder().subject_name(name).issuer_name(name)
                .public_key(key.public_key()).serial_number(1).not_valid_before(now - dt.timedelta(days=1))
                .not_valid_after(now + dt.timedelta(days=30)).sign(key, hashes.SHA256()))
        p12 = pkcs12.serialize_key_and_certificates(b"test", key, cert, None,
                                                  serialization.BestAvailableEncryption(b"test-password"))
        certificate = cert.public_bytes(serialization.Encoding.DER)
        profile = ProfileTest().profile()
        profile.update(Name="KGS Calendar TestFlight", DeveloperCertificates=[certificate])
        share_profile = ProfileTest().profile(asc.SHARE_BUNDLE_ID)
        share_profile.update(Name="KGS Calendar Share TestFlight", DeveloperCertificates=[certificate],
                             UUID="87654321-4321-4321-4321-210987654321")
        env = {**self.environment, "APPLE_TEAM_ID": "TEAM123456", "APPLE_P12_PASSWORD": "test-password",
               "APPLE_DISTRIBUTION_P12_BASE64": base64.b64encode(p12).decode(),
               "APPLE_PROFILE_BASE64": base64.b64encode(b"test-cms-profile").decode(),
               "APPLE_SHARE_PROFILE_BASE64": base64.b64encode(b"test-cms-share-profile").decode()}

        def decode(command, stderr=None):
            return plistlib.dumps(share_profile if command[-1].endswith("share-profile.mobileprovision") else profile)

        return env, decode, profile, share_profile

    def test_prepared_identity_exports_locally_with_manual_profile(self):
        env, decode, profile, share_profile = self.prepared_identity()
        with tempfile.TemporaryDirectory() as directory, mock.patch.dict(os.environ, env), \
                mock.patch.object(package.subprocess, "check_output", side_effect=decode):
            package.prepare_inputs(directory)
            options = plistlib.loads((Path(directory) / "ExportOptions.plist").read_bytes())
            self.assertEqual(options["destination"], "export")
            self.assertEqual(options["signingStyle"], "manual")
            self.assertEqual(options["provisioningProfiles"],
                             {asc.BUNDLE_ID: profile["Name"], asc.SHARE_BUNDLE_ID: share_profile["Name"]})
            self.assertFalse(options["manageAppVersionAndBuildNumber"])

    def test_missing_share_profile_secret_is_fetched_from_app_store_connect(self):
        env, decode, _, _ = self.prepared_identity()
        env["APPLE_SHARE_PROFILE_BASE64"] = ""
        with tempfile.TemporaryDirectory() as directory, mock.patch.dict(os.environ, env), \
                mock.patch.object(package, "Client"), \
                mock.patch.object(package, "share_profile_for", return_value=b"test-cms-share-profile") as fetch, \
                mock.patch.object(package.subprocess, "check_output", side_effect=decode):
            package.prepare_inputs(directory)
            fetch.assert_called_once()
            self.assertEqual((Path(directory) / "share-profile.mobileprovision").read_bytes(), b"test-cms-share-profile")

    def test_share_certificate_lookup_matches_the_serial_number(self):
        client = mock.Mock()
        certificate = mock.Mock(serial_number=0x0A1B)
        client.list.side_effect = lambda path, query=None: (
            [{"id": "other", "attributes": {"serialNumber": "FFFF"}},
             {"id": "cert", "attributes": {"serialNumber": "0A1B"}}] if path == "/v1/certificates"
            else [{"id": asc.SHARE_BUNDLE_ID, "attributes": {"identifier": asc.SHARE_BUNDLE_ID}}] if path == "/v1/bundleIds"
            else [{"id": "p", "attributes": {"profileState": "ACTIVE", "name": "KGS Calendar Share TestFlight cert",
                                             "profileType": "IOS_APP_STORE"}}])
        client.request.return_value = {"data": {"attributes": {"profileContent": base64.b64encode(b"share").decode()}}}
        self.assertEqual(asc.share_profile_for(client, certificate), b"share")
        client.request.assert_called_once_with("GET", "/v1/profiles/p")

    def test_app_profile_cannot_sign_the_share_extension(self):
        profile = ProfileTest().profile()
        with self.assertRaises(ValueError):
            package.validate_profile(profile, b"certificate", "TEAM123456", bundle_id=asc.SHARE_BUNDLE_ID)


class ProfileTest(unittest.TestCase):
    def profile(self, bundle_id=asc.BUNDLE_ID):
        return {"UUID": "12345678-1234-1234-1234-123456789012", "TeamIdentifier": ["TEAM123456"],
                "ExpirationDate": dt.datetime(2099, 1, 1), "DeveloperCertificates": [b"certificate"],
                "Entitlements": {"application-identifier": "TEAM123456." + bundle_id,
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
        share = app / "PlugIns/KgsShare.appex"
        share.mkdir(parents=True)
        (share / "Info.plist").write_bytes(plistlib.dumps(self.share_info(info)))
        (share / "KgsShare").touch()
        return app, info

    def share_info(self, info):
        return {"CFBundleIdentifier": asc.SHARE_BUNDLE_ID, "CFBundleExecutable": "KgsShare",
                "CFBundleVersion": info["CFBundleVersion"],
                "CFBundleShortVersionString": info["CFBundleShortVersionString"],
                "NSExtension": {"NSExtensionPointIdentifier": "com.apple.share-services"}}

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

    def test_share_extension_identity_type_and_version_are_checked(self):
        for field, value in [("CFBundleIdentifier", asc.BUNDLE_ID), ("CFBundleVersion", "9.9"),
                             ("NSExtension", {"NSExtensionPointIdentifier": "com.apple.widgetkit-extension"})]:
            with self.subTest(field=field), tempfile.TemporaryDirectory() as directory:
                app, info = self.make_archive(directory)
                share_info = self.share_info(info)
                share_info[field] = value
                (app / "PlugIns/KgsShare.appex/Info.plist").write_bytes(plistlib.dumps(share_info))
                with self.assertRaises(ValueError):
                    package.validate_archive(directory, "1.1")

    def test_missing_share_extension_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            app, _ = self.make_archive(directory)
            (app / "PlugIns/KgsShare.appex/Info.plist").unlink()
            with self.assertRaises(FileNotFoundError):
                package.validate_archive(directory)

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

    def test_share_extension_and_its_url_scheme(self):
        root = Path(__file__).resolve().parents[2]
        info = plistlib.loads((root / "iosApp/KgsCalendar/Info.plist").read_bytes())
        schemes = [scheme for url_type in info["CFBundleURLTypes"] for scheme in url_type["CFBundleURLSchemes"]]
        self.assertIn("kgscalendar", schemes)
        share = plistlib.loads((root / "iosApp/KgsShare/Info.plist").read_bytes())
        self.assertEqual(share["NSExtension"]["NSExtensionPointIdentifier"], "com.apple.share-services")
        self.assertEqual(share["CFBundleShortVersionString"], "$(MARKETING_VERSION)")


if __name__ == "__main__":
    unittest.main()
