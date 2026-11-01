"""Small App Store Connect client for this app's signing and internal TestFlight delivery.

Credentials are read from files/environment, never from command-line key contents.
The app record itself must first be created on the App Store Connect website.
"""

import argparse
import base64
import datetime as dt
import json
import os
from pathlib import Path
import re
import secrets
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec, rsa, utils
from cryptography.hazmat.primitives.serialization import pkcs12
from cryptography.x509.oid import NameOID


BUNDLE_ID = "com.kgs501.kgscalendar.portvalidation"
GROUP_NAME = "KGS iOS Development"
API = "https://api.appstoreconnect.apple.com"
TEST_NOTES = (
    "Component validation build for the iOS port, not a functional calendar yet. "
    "Please test the shared theme, menus, dialogs and animations on iPhone/iPad, "
    "including portrait and landscape. Calendar screens, persistence and sync are not included."
)


class AppleError(RuntimeError):
    pass


def b64url(value):
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode()


def api_token(key_path, key_id, issuer_id, now=None):
    if not re.fullmatch(r"[A-Z0-9]{10}", key_id):
        raise AppleError("Expected the 10-character App Store Connect Key ID")
    uuid.UUID(issuer_id)
    key = serialization.load_pem_private_key(Path(key_path).read_bytes(), password=None)
    if not isinstance(key, ec.EllipticCurvePrivateKey) or not isinstance(key.curve, ec.SECP256R1):
        raise AppleError("Expected an App Store Connect P-256 private key")
    now = int(time.time() if now is None else now)
    header = b64url(json.dumps({"alg": "ES256", "kid": key_id, "typ": "JWT"}).encode())
    payload = b64url(json.dumps({"iss": issuer_id, "iat": now, "exp": now + 600,
                                "aud": "appstoreconnect-v1"}).encode())
    message = f"{header}.{payload}".encode()
    r, s = utils.decode_dss_signature(key.sign(message, ec.ECDSA(hashes.SHA256())))
    return message.decode() + "." + b64url(r.to_bytes(32, "big") + s.to_bytes(32, "big"))


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise AppleError("Refusing to redirect an authenticated Apple API request")


class Client:
    def __init__(self, key_path, key_id, issuer_id):
        self.key_path, self.key_id, self.issuer_id = key_path, key_id, issuer_id
        self.opener = urllib.request.build_opener(NoRedirect())

    def request(self, method, path, data=None, query=None):
        url = API + path if path.startswith("/") else path
        parsed = urllib.parse.urlsplit(url)
        if parsed.scheme != "https" or parsed.netloc != "api.appstoreconnect.apple.com":
            raise AppleError("Refusing to send credentials outside App Store Connect")
        if query:
            url += "?" + urllib.parse.urlencode(query)
        body = None if data is None else json.dumps(data).encode()
        request = urllib.request.Request(url, data=body, method=method, headers={
            "Authorization": "Bearer " + api_token(self.key_path, self.key_id, self.issuer_id),
            "Content-Type": "application/json", "Accept": "application/json",
        })
        try:
            with self.opener.open(request, timeout=60) as response:
                content = response.read()
                return json.loads(content) if content else {}
        except urllib.error.HTTPError as error:
            try:
                errors = json.loads(error.read()).get("errors", [])
                detail = "; ".join(f"{item.get('code', '')}: {item.get('title', '')}" for item in errors)
            except (ValueError, AttributeError):
                detail = "Request rejected"
            # Do not dump request bodies, JWTs, private keys, or signing material.
            raise AppleError(f"Apple API {method} {parsed.path}: HTTP {error.code}: {detail}") from None

    def list(self, path, query=None):
        result = []
        for _ in range(100):
            page = self.request("GET", path, query=query)
            result.extend(page["data"])
            path = page.get("links", {}).get("next")
            if not path:
                return result
            query = None
        raise AppleError("Apple API pagination exceeded the expected limit")


def ensure_bundle(client):
    bundles = client.list("/v1/bundleIds", {"filter[identifier]": BUNDLE_ID})
    if bundles:
        return bundles[0]
    return client.request("POST", "/v1/bundleIds", {"data": {
        "type": "bundleIds", "attributes": {
            "identifier": BUNDLE_ID, "name": "KGS Port Validation", "platform": "IOS",
        },
    }})["data"]


def find_app(client):
    apps = client.list("/v1/apps", {"filter[bundleId]": BUNDLE_ID})
    if len(apps) != 1:
        raise AppleError(f"Create the iOS app record at https://appstoreconnect.apple.com/apps "
                         f"with bundle ID {BUNDLE_ID}, then retry. No App Store release is needed.")
    return apps[0]


def private_write(path, content):
    path = Path(path)
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "wb") as stream:
        os.fchmod(stream.fileno(), 0o600)
        stream.write(content)


def prepare_signing(client, directory):
    directory = Path(directory)
    directory.mkdir(mode=0o700, parents=True, exist_ok=True)
    directory.chmod(0o700)
    bundle = ensure_bundle(client)
    key_path, certificate_path = directory / "distribution.key", directory / "distribution.cer"
    state_path = directory / "signing.json"
    if state_path.exists():
        state = json.loads(state_path.read_text())
        client.request("GET", "/v1/certificates/" + state["certificate_id"])
        key = serialization.load_pem_private_key(key_path.read_bytes(), password=None)
        cert = x509.load_der_x509_certificate(certificate_path.read_bytes())
    else:
        if key_path.exists():
            raise AppleError("An unfinished signing setup exists; inspect it before creating another certificate")
        key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        private_write(key_path, key.private_bytes(serialization.Encoding.PEM,
                      serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
        csr = x509.CertificateSigningRequestBuilder().subject_name(x509.Name([
            x509.NameAttribute(NameOID.COMMON_NAME, "KGS TestFlight"),
        ])).sign(key, hashes.SHA256()).public_bytes(serialization.Encoding.PEM).decode()
        certificate = client.request("POST", "/v1/certificates", {"data": {
            "type": "certificates", "attributes": {"certificateType": "DISTRIBUTION", "csrContent": csr},
        }})["data"]
        cert_bytes = base64.b64decode(certificate["attributes"]["certificateContent"], validate=True)
        private_write(certificate_path, cert_bytes)
        cert = x509.load_der_x509_certificate(cert_bytes)
        state = {"certificate_id": certificate["id"]}
        private_write(state_path, json.dumps(state).encode())
    if key.public_key().public_numbers() != cert.public_key().public_numbers():
        raise AppleError("Stored signing key does not match the certificate")
    if cert.not_valid_after_utc <= dt.datetime.now(dt.timezone.utc) + dt.timedelta(days=1):
        raise AppleError("Signing certificate is expired or expires within one day; renew signing setup")
    team_id = cert.subject.get_attributes_for_oid(NameOID.ORGANIZATIONAL_UNIT_NAME)[0].value
    if not re.fullmatch(r"[A-Z0-9]{10}", team_id):
        raise AppleError("Could not identify the Apple developer team from its certificate")
    profile_name = "KGS Port Validation TestFlight " + state["certificate_id"]
    profiles = client.list("/v1/profiles", {"filter[name]": profile_name})
    active = [p for p in profiles if p["attributes"]["profileState"] == "ACTIVE"]
    if active:
        profile = client.request("GET", "/v1/profiles/" + active[0]["id"])["data"]
    else:
        profile = client.request("POST", "/v1/profiles", {"data": {
            "type": "profiles", "attributes": {"name": profile_name, "profileType": "IOS_APP_STORE"},
            "relationships": {
                "bundleId": {"data": {"type": "bundleIds", "id": bundle["id"]}},
                "certificates": {"data": [{"type": "certificates", "id": state["certificate_id"]}]},
            },
        }})["data"]
    private_write(directory / "profile.mobileprovision",
                  base64.b64decode(profile["attributes"]["profileContent"], validate=True))
    password = secrets.token_urlsafe(32)
    private_write(directory / "p12-password", password.encode())
    # macOS security import needs the compatible PKCS#12 format documented by cryptography.
    # The private files and GitHub environment secrets protect this legacy-format container.
    encryption = (serialization.PrivateFormat.PKCS12.encryption_builder().kdf_rounds(50000)
                  .key_cert_algorithm(pkcs12.PBES.PBESv1SHA1And3KeyTripleDESCBC)
                  .hmac_hash(hashes.SHA1()).build(password.encode()))
    private_write(directory / "distribution.p12", pkcs12.serialize_key_and_certificates(
        b"KGS TestFlight", key, cert, None, encryption))
    state.update(team_id=team_id, profile_id=profile["id"], profile_name=profile_name, bundle_id=BUNDLE_ID)
    private_write(state_path, json.dumps(state, indent=2).encode())
    print(f"Signing prepared for team {team_id}; private files are stored in {directory}")
    return state


def configure_testflight(client, email=None):
    app = find_app(client)
    users = client.list("/v1/users", {"limit": 200})
    if email:
        users = [u for u in users if u["attributes"]["username"].casefold() == email.casefold()]
    else:
        users = [u for u in users if "ACCOUNT_HOLDER" in u["attributes"]["roles"]]
    if len(users) != 1:
        raise AppleError("Specify the email of the existing App Store Connect user to add as an internal tester")
    selected_user = users[0]
    user = selected_user["attributes"]
    eligible = {"ACCOUNT_HOLDER", "ADMIN", "APP_MANAGER", "DEVELOPER", "MARKETING"}
    if not eligible.intersection(user["roles"]):
        raise AppleError("The selected App Store Connect user is not eligible for internal testing")
    if not user.get("allAppsVisible"):
        visible = client.list(f"/v1/users/{selected_user['id']}/visibleApps")
        if app["id"] not in {item["id"] for item in visible}:
            raise AppleError("The selected tester needs access to this app in App Store Connect")
    email = user["username"]
    groups = client.list("/v1/betaGroups", {"filter[app]": app["id"], "filter[name]": GROUP_NAME})
    if groups:
        if len(groups) != 1:
            raise AppleError("Multiple development groups match; inspect App Store Connect")
        group = groups[0]
        if not group["attributes"]["isInternalGroup"]:
            raise AppleError("The existing development group is external; refusing to distribute to it")
    else:
        group = client.request("POST", "/v1/betaGroups", {"data": {
            "type": "betaGroups", "attributes": {"name": GROUP_NAME, "isInternalGroup": True,
                "hasAccessToAllBuilds": True, "publicLinkEnabled": False},
            "relationships": {"app": {"data": {"type": "apps", "id": app["id"]}}},
        }})["data"]
    testers = client.list("/v1/betaTesters", {"filter[email]": email})
    if testers:
        tester = testers[0]
        members = client.list(f"/v1/betaGroups/{group['id']}/betaTesters")
        if tester["id"] not in {member["id"] for member in members}:
            client.request("POST", f"/v1/betaGroups/{group['id']}/relationships/betaTesters",
                           {"data": [{"type": "betaTesters", "id": tester["id"]}]})
    else:
        client.request("POST", "/v1/betaTesters", {"data": {
            "type": "betaTesters", "attributes": {"email": email, "firstName": user.get("firstName"),
                "lastName": user.get("lastName")},
            "relationships": {"betaGroups": {"data": [{"type": "betaGroups", "id": group["id"]}]}},
        }})
    return {"app_id": app["id"], "group_id": group["id"], "tester_email": email}


def check_build(client, app_id, group_id, version):
    app = find_app(client)
    if app["id"] != app_id:
        raise AppleError("Configured App Store Connect app does not match this bundle ID")
    group = client.request("GET", f"/v1/betaGroups/{group_id}")["data"]
    if not group["attributes"]["isInternalGroup"]:
        raise AppleError("Only internal TestFlight delivery is supported")
    group_app = client.request("GET", f"/v1/betaGroups/{group_id}/app")["data"]
    if group_app["id"] != app_id:
        raise AppleError("TestFlight group belongs to another app")
    builds = client.list("/v1/builds", {"filter[app]": app_id, "filter[version]": version})
    if not builds:
        return False
    if len(builds) != 1:
        raise AppleError("Build number is ambiguous; inspect App Store Connect")
    build = builds[0]
    state = build["attributes"]["processingState"]
    if state in {"FAILED", "INVALID"}:
        raise AppleError(f"Apple rejected build {version}: {state}; inspect App Store Connect processing details")
    if state != "VALID":
        return False
    detail = client.request("GET", f"/v1/builds/{build['id']}/buildBetaDetail")["data"]["attributes"]
    beta_state = detail["internalBuildState"]
    if beta_state in {"EXPIRED", "PROCESSING_EXCEPTION", "MISSING_EXPORT_COMPLIANCE"}:
        raise AppleError(f"Build {version} needs attention in App Store Connect: {beta_state}")
    if beta_state not in {"READY_FOR_BETA_TESTING", "IN_BETA_TESTING"}:
        print("Apple internal build state:", beta_state)
        return False
    notes = client.list("/v1/betaBuildLocalizations", {"filter[build]": build["id"], "filter[locale]": "en-US"})
    if not notes:
        client.request("POST", "/v1/betaBuildLocalizations", {"data": {
            "type": "betaBuildLocalizations", "attributes": {"locale": "en-US", "whatsNew": TEST_NOTES},
            "relationships": {"build": {"data": {"type": "builds", "id": build["id"]}}},
        }})
    grouped = client.list(f"/v1/betaGroups/{group_id}/relationships/builds")
    if build["id"] not in {item["id"] for item in grouped}:
        client.request("POST", f"/v1/betaGroups/{group_id}/relationships/builds",
                       {"data": [{"type": "builds", "id": build["id"]}]})
    if beta_state != "IN_BETA_TESTING":
        print("Build added to the internal group; waiting for Apple to enable testing.")
        return False
    print(f"Build {version} is available for internal TestFlight testing.")
    return True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--key-file", default=os.environ.get("ASC_KEY_PATH"))
    parser.add_argument("--key-id", default=os.environ.get("ASC_KEY_ID"))
    parser.add_argument("--issuer-id", default=os.environ.get("ASC_ISSUER_ID"))
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("register-bundle")
    signing = commands.add_parser("prepare-signing")
    signing.add_argument("--directory", required=True)
    configure = commands.add_parser("configure-testflight")
    configure.add_argument("--email")
    check = commands.add_parser("check-build")
    check.add_argument("--app-id", required=True)
    check.add_argument("--group-id", required=True)
    check.add_argument("--version", required=True)
    args = parser.parse_args()
    if not all((args.key_file, args.key_id, args.issuer_id)):
        parser.error("Provide the key file, Key ID and Issuer ID (or ASC_KEY_PATH/ASC_KEY_ID/ASC_ISSUER_ID)")
    client = Client(args.key_file, args.key_id, args.issuer_id)
    try:
        if args.command == "register-bundle":
            bundle = ensure_bundle(client)
            print(f"Registered bundle: {bundle['attributes']['identifier']}")
        elif args.command == "prepare-signing":
            prepare_signing(client, args.directory)
        elif args.command == "configure-testflight":
            print(json.dumps(configure_testflight(client, args.email), indent=2))
        elif args.command == "check-build":
            return 0 if check_build(client, args.app_id, args.group_id, args.version) else 2
    except (AppleError, ValueError) as error:
        print(str(error))
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
