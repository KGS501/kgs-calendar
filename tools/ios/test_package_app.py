import datetime as dt
from pathlib import Path
import plistlib
import struct
import tempfile
import unittest
from unittest import mock

import app_store as asc
import package_app as package


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
