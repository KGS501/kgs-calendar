import base64
import datetime as dt
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock
import urllib.error

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec, rsa, utils
from cryptography.hazmat.primitives.serialization import pkcs12
from cryptography.x509.oid import NameOID

import app_store as asc


class TokenTest(unittest.TestCase):
    def test_team_token_signature_and_ten_minute_expiry(self):
        key = ec.generate_private_key(ec.SECP256R1())
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "test.p8"
            asc.private_write(path, key.private_bytes(serialization.Encoding.PEM,
                              serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
            token = asc.api_token(path, "ABCDEFGHIJ", "12345678-1234-1234-1234-123456789012", now=1000)
        header, payload, signature = token.split(".")
        decode = lambda value: base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))
        self.assertEqual(json.loads(decode(header))["kid"], "ABCDEFGHIJ")
        self.assertEqual(json.loads(decode(payload))["exp"], 1600)
        self.assertEqual(json.loads(decode(payload))["aud"], "appstoreconnect-v1")
        raw = decode(signature)
        self.assertEqual(len(raw), 64)
        der = utils.encode_dss_signature(int.from_bytes(raw[:32], "big"), int.from_bytes(raw[32:], "big"))
        key.public_key().verify(der, f"{header}.{payload}".encode(), ec.ECDSA(hashes.SHA256()))

    def test_other_key_types_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "test.key"
            key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
            asc.private_write(path, key.private_bytes(serialization.Encoding.PEM,
                              serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
            with self.assertRaisesRegex(asc.AppleError, "P-256"):
                asc.api_token(path, "ABCDEFGHIJ", "12345678-1234-1234-1234-123456789012")

    def test_foreign_host_never_receives_credentials(self):
        client = asc.Client("unused", "unused", "unused")
        with mock.patch.object(asc, "api_token") as token:
            with self.assertRaises(asc.AppleError):
                client.request("GET", "https://example.test/v1/apps")
            token.assert_not_called()

    def test_pagination_rejects_foreign_next_link(self):
        client = asc.Client("unused", "unused", "unused")
        response = mock.MagicMock()
        response.__enter__.return_value.read.return_value = json.dumps({
            "data": [], "links": {"next": "https://example.test/stolen"},
        }).encode()
        with mock.patch.object(asc, "api_token", return_value="test-token"), \
             mock.patch.object(client.opener, "open", return_value=response) as opened:
            with self.assertRaises(asc.AppleError):
                client.list("/v1/apps")
            self.assertEqual(opened.call_count, 1)

    def test_errors_do_not_dump_private_response_content(self):
        client = asc.Client("unused", "unused", "unused")
        body = json.dumps({"errors": [{"code": "FORBIDDEN", "title": "Access denied",
                                      "detail": "private-response-content"}]}).encode()
        error = urllib.error.HTTPError(asc.API + "/v1/apps", 403, "Forbidden", {}, io.BytesIO(body))
        with mock.patch.object(asc, "api_token", return_value="test-token"), \
             mock.patch.object(client.opener, "open", side_effect=error):
            with self.assertRaises(asc.AppleError) as caught:
                client.request("GET", "/v1/apps")
            self.assertNotIn("private-response-content", str(caught.exception))
            self.assertNotIn("test-token", str(caught.exception))


class AppIdentityTest(unittest.TestCase):
    def test_prefix_bundle_match_does_not_reuse_the_validation_identifier(self):
        client = mock.Mock()
        client.list.return_value = [{"id": "old", "attributes": {"identifier": asc.BUNDLE_ID + ".portvalidation"}}]
        client.request.return_value = {"data": {"id": "new"}}
        self.assertEqual(asc.ensure_bundle(client)["id"], "new")
        body = client.request.call_args.args[2]
        self.assertEqual(body["data"]["attributes"]["identifier"], asc.BUNDLE_ID)

    def test_exact_bundle_match_is_reused_alongside_prefix_matches(self):
        client = mock.Mock()
        client.list.return_value = [
            {"id": "old", "attributes": {"identifier": asc.BUNDLE_ID + ".portvalidation"}},
            {"id": "new", "attributes": {"identifier": asc.BUNDLE_ID}},
        ]
        self.assertEqual(asc.ensure_bundle(client)["id"], "new")
        client.request.assert_not_called()

    def test_app_lookup_requires_the_exact_bundle_id(self):
        client = mock.Mock()
        client.list.return_value = [{"id": "old", "attributes": {"bundleId": asc.BUNDLE_ID + ".portvalidation"}}]
        with self.assertRaises(asc.AppleError):
            asc.find_app(client)


class SigningTest(unittest.TestCase):
    @mock.patch("builtins.print")
    def test_signing_identity_is_reused_without_revoking_or_creating_another_certificate(self, log):
        client = mock.Mock()
        certificates = {}
        profile = {"id": "profile", "attributes": {"profileState": "ACTIVE",
                   "name": "KGS Calendar TestFlight cert", "profileType": "IOS_APP_STORE",
                   "profileContent": base64.b64encode(b"test-profile").decode()}}
        client.list.side_effect = lambda path, query=None: (
            [{"id": "bundle", "attributes": {"identifier": asc.BUNDLE_ID}}] if path == "/v1/bundleIds" else [profile])
        def request(method, path, body=None):
            if method == "POST" and path == "/v1/certificates":
                csr = x509.load_pem_x509_csr(body["data"]["attributes"]["csrContent"].encode())
                ca = rsa.generate_private_key(public_exponent=65537, key_size=2048)
                name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "Test Distribution"),
                                 x509.NameAttribute(NameOID.ORGANIZATIONAL_UNIT_NAME, "TEAM123456")])
                now = dt.datetime.now(dt.timezone.utc)
                cert = (x509.CertificateBuilder().subject_name(name).issuer_name(name)
                        .public_key(csr.public_key()).serial_number(1)
                        .not_valid_before(now - dt.timedelta(days=1))
                        .not_valid_after(now + dt.timedelta(days=365)).sign(ca, hashes.SHA256()))
                certificates["cert"] = {"id": "cert", "attributes": {"certificateContent":
                    base64.b64encode(cert.public_bytes(serialization.Encoding.DER)).decode()}}
                return {"data": certificates["cert"]}
            if method == "GET" and path == "/v1/certificates/cert":
                return {"data": certificates["cert"]}
            if method == "GET" and path == "/v1/profiles/profile":
                return {"data": profile}
            raise AssertionError((method, path))
        client.request.side_effect = request
        with tempfile.TemporaryDirectory() as directory:
            first = asc.prepare_signing(client, directory)
            original_key = (Path(directory) / "distribution.key").read_bytes()
            second = asc.prepare_signing(client, directory)
            self.assertEqual(first["team_id"], "TEAM123456")
            self.assertEqual(first, second)
            self.assertEqual(original_key, (Path(directory) / "distribution.key").read_bytes())
            password = (Path(directory) / "p12-password").read_bytes()
            key, cert, _ = pkcs12.load_key_and_certificates((Path(directory) / "distribution.p12").read_bytes(), password)
            self.assertEqual(key.public_key().public_numbers(), cert.public_key().public_numbers())
            for file in Path(directory).iterdir():
                self.assertEqual(file.stat().st_mode & 0o777, 0o600)
        mutations = [call for call in client.request.call_args_list if call.args[0] != "GET"]
        self.assertEqual(len(mutations), 1)
        self.assertEqual(mutations[0].args[:2], ("POST", "/v1/certificates"))

    def test_interrupted_setup_does_not_discard_an_existing_private_key(self):
        client = mock.Mock()
        client.list.return_value = [{"id": "bundle", "attributes": {"identifier": asc.BUNDLE_ID}}]
        with tempfile.TemporaryDirectory() as directory:
            key = Path(directory) / "distribution.key"
            key.write_text("keep me")
            with self.assertRaisesRegex(asc.AppleError, "unfinished"):
                asc.prepare_signing(client, directory)
            self.assertEqual(key.read_text(), "keep me")
            client.request.assert_not_called()


class TesterSetupTest(unittest.TestCase):
    def client(self, roles=None, member=False, internal=True, visible=True):
        client = mock.Mock()
        def listing(path, query=None):
            return {
                "/v1/apps": [{"id": "app", "attributes": {"bundleId": asc.BUNDLE_ID}}],
                "/v1/users": [{"id": "owner", "attributes": {"username": "owner@example.test",
                              "roles": roles or ["ACCOUNT_HOLDER"], "allAppsVisible": visible}}],
                "/v1/users/owner/visibleApps": [],
                "/v1/betaGroups": [{"id": "group", "attributes": {"name": asc.GROUP_NAME, "isInternalGroup": internal}}],
                "/v1/betaTesters": [{"id": "tester", "attributes": {"email": "owner@example.test"}}],
                "/v1/betaGroups/group/betaTesters": [{"id": "tester"}] if member else [],
            }[path]
        client.list.side_effect = listing
        return client

    def test_account_holder_is_selected_without_inviting_unrelated_users(self):
        client = self.client()
        result = asc.configure_testflight(client)
        self.assertEqual(result, {"app_id": "app", "group_id": "group", "tester_email": "owner@example.test"})
        client.request.assert_called_once_with("POST", "/v1/betaGroups/group/relationships/betaTesters",
                                               {"data": [{"type": "betaTesters", "id": "tester"}]})

    def test_existing_membership_is_reused(self):
        client = self.client(member=True)
        asc.configure_testflight(client, "OWNER@example.test")
        client.request.assert_not_called()

    def test_ineligible_or_inaccessible_user_is_rejected_before_mutation(self):
        for client in (self.client(roles=["FINANCE"]), self.client(visible=False)):
            with self.assertRaises(asc.AppleError):
                asc.configure_testflight(client, "owner@example.test")
            client.request.assert_not_called()

    def test_external_group_is_rejected_before_inviting(self):
        client = self.client(internal=False)
        with self.assertRaises(asc.AppleError):
            asc.configure_testflight(client)
        client.request.assert_not_called()

    def test_new_internal_group_and_tester_use_the_same_app(self):
        client = self.client()
        existing = client.list.side_effect
        client.list.side_effect = lambda path, query=None: (
            [] if path in {"/v1/betaGroups", "/v1/betaTesters"} else existing(path, query))
        client.request.return_value = {"data": {"id": "new-group"}}
        result = asc.configure_testflight(client)
        group_call, tester_call = client.request.call_args_list
        self.assertTrue(group_call.args[2]["data"]["attributes"]["isInternalGroup"])
        self.assertFalse(group_call.args[2]["data"]["attributes"]["publicLinkEnabled"])
        self.assertEqual(group_call.args[2]["data"]["relationships"]["app"]["data"]["id"], "app")
        tester = tester_call.args[2]["data"]
        self.assertEqual(tester["attributes"]["email"], "owner@example.test")
        self.assertEqual(tester["relationships"]["betaGroups"]["data"][0]["id"], result["group_id"])


class DistributionTest(unittest.TestCase):
    def client(self, state="VALID", internal=True, app="app", grouped=False, beta_state="IN_BETA_TESTING",
               notes=True):
        client = mock.Mock()
        def listing(path, query=None):
            if path == "/v1/apps":
                return [{"id": "app", "attributes": {"bundleId": asc.BUNDLE_ID}}]
            if path == "/v1/builds":
                return [{"id": "build", "attributes": {"processingState": state}}]
            if path.endswith("/relationships/builds"):
                return [{"id": "build"}] if grouped else []
            if path == "/v1/betaBuildLocalizations":
                return [{"id": "notes"}] if notes else []
            raise AssertionError(path)
        def request(method, path, body=None):
            if method == "POST":
                return {}
            if path.endswith("/app"):
                return {"data": {"id": app}}
            if path.endswith("/buildBetaDetail"):
                return {"data": {"attributes": {"internalBuildState": beta_state}}}
            return {"data": {"attributes": {"isInternalGroup": internal}}}
        client.list.side_effect = listing
        client.request.side_effect = request
        return client

    @mock.patch("builtins.print")
    def test_ready_build_is_added_only_to_the_expected_internal_group(self, log):
        client = self.client()
        self.assertTrue(asc.check_build(client, "app", "group", "1.1"))
        client.request.assert_any_call("POST", "/v1/betaGroups/group/relationships/builds",
                                       {"data": [{"type": "builds", "id": "build"}]})

    @mock.patch("builtins.print")
    def test_already_grouped_build_is_not_added_twice(self, log):
        client = self.client(grouped=True)
        self.assertTrue(asc.check_build(client, "app", "group", "1.1"))
        self.assertFalse(any(c.args[0] == "POST" for c in client.request.call_args_list))

    def test_processing_build_does_not_claim_availability(self):
        self.assertFalse(asc.check_build(self.client(state="PROCESSING"), "app", "group", "1.1"))

    @mock.patch("builtins.print")
    def test_ready_but_not_enabled_build_has_notes_and_waits_for_availability(self, log):
        client = self.client(beta_state="READY_FOR_BETA_TESTING", notes=False)
        self.assertFalse(asc.check_build(client, "app", "group", "1.1"))
        note_call = next(c for c in client.request.call_args_list if c.args[:2] == ("POST", "/v1/betaBuildLocalizations"))
        self.assertIn("not a functional calendar", note_call.args[2]["data"]["attributes"]["whatsNew"])

    def test_permanent_beta_errors_fail_instead_of_waiting(self):
        for state in ("EXPIRED", "PROCESSING_EXCEPTION", "MISSING_EXPORT_COMPLIANCE"):
            client = self.client(beta_state=state)
            with self.subTest(state=state), self.assertRaises(asc.AppleError):
                asc.check_build(client, "app", "group", "1.1")
            self.assertFalse(any(c.args[0] == "POST" for c in client.request.call_args_list))

    def test_rejected_builds_stop_processing_wait(self):
        for state in ("FAILED", "INVALID"):
            with self.subTest(state=state), self.assertRaises(asc.AppleError):
                asc.check_build(self.client(state=state), "app", "group", "1.1")

    def test_external_or_other_app_group_is_rejected_before_distribution(self):
        for client in (self.client(internal=False), self.client(app="another-app")):
            with self.assertRaises(asc.AppleError):
                asc.check_build(client, "app", "group", "1.1")
            self.assertFalse(any(c.args[0] == "POST" for c in client.request.call_args_list))

    def test_missing_app_record_needs_the_apple_website(self):
        client = mock.Mock()
        client.list.return_value = []
        with self.assertRaisesRegex(asc.AppleError, "appstoreconnect.apple.com/apps"):
            asc.find_app(client)
        client.request.assert_not_called()


if __name__ == "__main__":
    unittest.main()
