#!/usr/bin/env python3
"""Ship KGS Calendar to the App Store: listing, screenshots, build and review submission.

The API key comes from a private config file, ~/.config/kgs-calendar/app-store-config.json, with
key_file, key_id, issuer_id and app_id. See docs/app-store/README.md.

    status                            what App Store Connect still misses
    push --version 2.0.1 --whats-new  create or update the draft version and its listing
    attach-build --version 2.0.1 --build 7.1
    submit --version 2.0.1 --yes      send the draft to App Review
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import stat
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

sys.path.insert(0, str(Path(__file__).resolve().parent))
from app_store import AppleError, Client  # noqa: E402

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_LISTING = REPO_ROOT / "docs/app-store/listing.en.json"
LOCALE = "en-US"
# App Store Connect files the 6.9-inch and 13-inch screenshots under these older names.
DISPLAY_TYPES = {"iphone": "APP_IPHONE_67", "ipad": "APP_IPAD_PRO_3GEN_129"}
EDITABLE_STATES = {
    "PREPARE_FOR_SUBMISSION", "DEVELOPER_REJECTED", "REJECTED", "METADATA_REJECTED", "INVALID_BINARY",
}
LIMITS = {
    "name": 30, "subtitle": 30, "promotionalText": 170, "keywords": 100, "description": 4000,
    "whatsNew": 4000, "reviewNotes": 4000,
}
REVIEW_CONTACT = ("contactFirstName", "contactLastName", "contactPhone", "contactEmail")
# The app has none of this content. Fields Apple adds later show up as unanswered in `status`.
AGE_NONE = {
    "alcoholTobaccoOrDrugUseOrReferences", "contests", "gamblingSimulated", "gunsOrOtherWeapons",
    "horrorOrFearThemes", "matureOrSuggestiveThemes", "medicalOrTreatmentInformation",
    "profanityOrCrudeHumor", "sexualContentGraphicAndNudity", "sexualContentOrNudity",
    "violenceCartoonOrFantasy", "violenceRealistic", "violenceRealisticProlongedGraphicOrSadistic",
}
AGE_FALSE = {
    "advertising", "ageAssurance", "gambling", "healthOrWellnessTopics", "lootBox", "messagingAndChat",
    "parentalControls", "socialMedia", "unrestrictedWebAccess", "userGeneratedContent",
}


def default_config_path():
    home = Path(os.environ.get("XDG_CONFIG_HOME", Path.home() / ".config"))
    return home / "kgs-calendar" / "app-store-config.json"


def load_config(path):
    if not path.is_file():
        raise AppleError(f"Config does not exist: {path}. See docs/app-store/README.md.")
    config = json.loads(path.read_text(encoding="utf-8"))
    missing = [key for key in ("key_file", "key_id", "issuer_id", "app_id") if not config.get(key)]
    if missing:
        raise AppleError(f"Config is missing: {', '.join(missing)}")
    for file in (path, Path(config["key_file"])):
        if not file.is_file():
            raise AppleError(f"Missing file: {file}")
        if os.name != "nt" and stat.S_IMODE(file.stat().st_mode) & 0o077:
            raise AppleError(f"{file} must not be readable by group/others; run chmod 600 on it")
    return config


def check_listing(listing):
    too_long = [
        f"{key} ({len(listing[key])}/{limit})"
        for key, limit in LIMITS.items() if len(listing.get(key) or "") > limit
    ]
    if too_long:
        raise AppleError("Listing fields over Apple's limit: " + ", ".join(too_long))


def present(attributes):
    """Apple refuses empty strings for URLs and for What's New on a first version."""
    return {key: value for key, value in attributes.items() if value not in (None, "")}


def patch(client, kind, identifier, attributes=None, relationships=None):
    data = {"type": kind, "id": identifier}
    if attributes:
        data["attributes"] = attributes
    if relationships:
        data["relationships"] = relationships
    return client.request("PATCH", f"/v1/{kind}/{identifier}", {"data": data})


def relation(kind, identifier):
    return {"data": {"type": kind, "id": identifier}}


def ios_versions(client, app_id):
    return client.list(f"/v1/apps/{app_id}/appStoreVersions", {"filter[platform]": "IOS"})


def editable_version(versions):
    return next((v for v in versions if v["attributes"]["appStoreState"] in EDITABLE_STATES), None)


def draft_version(client, app_id, version):
    """The draft for this version. An editable draft is renumbered, as an app has only one."""
    versions = ios_versions(client, app_id)
    draft = editable_version(versions)
    if draft:
        if draft["attributes"]["versionString"] != version:
            patch(client, "appStoreVersions", draft["id"], {"versionString": version})
        return draft
    if any(v["attributes"]["versionString"] == version for v in versions):
        raise AppleError(f"Version {version} exists and can't be edited any more")
    return client.request("POST", "/v1/appStoreVersions", {"data": {
        "type": "appStoreVersions",
        "attributes": {"platform": "IOS", "versionString": version},
        "relationships": {"app": relation("apps", app_id)},
    }})["data"]


def find_draft(client, app_id, version):
    draft = editable_version(ios_versions(client, app_id))
    if not draft or draft["attributes"]["versionString"] != version:
        raise AppleError(f"No editable draft of version {version}; run push first")
    return draft


def localization(client, path):
    items = client.list(path)
    match = next((item for item in items if item["attributes"]["locale"] == LOCALE), None)
    if not match:
        raise AppleError(f"No {LOCALE} localization at {path}")
    return match


def app_info(client, app_id):
    infos = client.list(f"/v1/apps/{app_id}/appInfos")
    return next((i for i in infos if i["attributes"].get("appStoreState") != "READY_FOR_SALE"), infos[0])


def push_app_info(client, app_id, listing):
    info = app_info(client, app_id)
    loc = localization(client, f"/v1/appInfos/{info['id']}/appInfoLocalizations")
    patch(client, "appInfoLocalizations", loc["id"], present({
        "name": listing["name"], "subtitle": listing["subtitle"],
        "privacyPolicyUrl": listing["privacyPolicyUrl"],
    }))
    patch(client, "appInfos", info["id"], relationships={
        "primaryCategory": relation("appCategories", listing["primaryCategory"]),
        "secondaryCategory": relation("appCategories", listing["secondaryCategory"]),
    })
    patch(client, "apps", app_id, {"contentRightsDeclaration": listing["contentRightsDeclaration"]})
    return info


def push_age_rating(client, info):
    declaration = client.request("GET", f"/v1/appInfos/{info['id']}/ageRatingDeclaration")["data"]
    attributes = {}
    for key in declaration["attributes"]:
        if key in AGE_NONE:
            attributes[key] = "NONE"
        elif key in AGE_FALSE:
            attributes[key] = False
    patch(client, "ageRatingDeclarations", declaration["id"], attributes)


def push_review_detail(client, version_id, listing, contact):
    """Apple only stores the notes together with a full contact, which stays out of this public repo."""
    missing = [key for key in REVIEW_CONTACT if not contact.get(key)]
    if missing:
        print(f"Skipped the review notes: review_contact in the config lacks {', '.join(missing)}.")
        return
    attributes = {"notes": listing["reviewNotes"], **{key: contact[key] for key in REVIEW_CONTACT}}
    existing = client.request("GET", f"/v1/appStoreVersions/{version_id}/appStoreReviewDetail").get("data")
    if existing:
        patch(client, "appStoreReviewDetails", existing["id"], attributes)
        return
    client.request("POST", "/v1/appStoreReviewDetails", {"data": {
        "type": "appStoreReviewDetails", "attributes": attributes,
        "relationships": {"appStoreVersion": relation("appStoreVersions", version_id)},
    }})


def push_version(client, version_id, listing, whats_new, release_type):
    attributes = {"copyright": listing["copyright"]}
    if release_type:
        attributes["releaseType"] = release_type
    patch(client, "appStoreVersions", version_id, attributes)
    loc = localization(client, f"/v1/appStoreVersions/{version_id}/appStoreVersionLocalizations")
    patch(client, "appStoreVersionLocalizations", loc["id"], present({
        "description": listing["description"], "keywords": listing["keywords"],
        "promotionalText": listing["promotionalText"], "supportUrl": listing["supportUrl"],
        "marketingUrl": listing.get("marketingUrl"), "whatsNew": whats_new,
    }))
    return loc


def upload_file(operations, content):
    for operation in operations:
        offset, length = operation["offset"], operation["length"]
        if urllib.parse.urlsplit(operation["url"]).scheme != "https":
            raise AppleError("Refusing a screenshot upload that isn't https")
        request = urllib.request.Request(
            operation["url"], data=content[offset:offset + length], method=operation["method"],
            headers={header["name"]: header["value"] for header in operation["requestHeaders"]},
        )
        with urllib.request.urlopen(request, timeout=120):
            pass


def wait_for_screenshots(client, set_id, attempts=40):
    for _ in range(attempts):
        shots = client.list(f"/v1/appScreenshotSets/{set_id}/appScreenshots")
        states = [(s["attributes"].get("assetDeliveryState") or {}) for s in shots]
        failed = [s for s in states if s.get("state") == "FAILED"]
        if failed:
            raise AppleError(f"Apple refused a screenshot: {failed[0].get('errors')}")
        if all(s.get("state") == "COMPLETE" for s in states):
            return
        time.sleep(5)
    raise AppleError("Apple is still processing the screenshots; check again with status")


def push_screenshots(client, localization_id, display_type, files):
    """Replaces the set, unless it already holds these files in this order."""
    sets = client.list(f"/v1/appStoreVersionLocalizations/{localization_id}/appScreenshotSets")
    current = next((s for s in sets if s["attributes"]["screenshotDisplayType"] == display_type), None)
    if not current:
        current = client.request("POST", "/v1/appScreenshotSets", {"data": {
            "type": "appScreenshotSets", "attributes": {"screenshotDisplayType": display_type},
            "relationships": {"appStoreVersionLocalization": relation("appStoreVersionLocalizations", localization_id)},
        }})["data"]
    existing = client.list(f"/v1/appScreenshotSets/{current['id']}/appScreenshots")
    wanted = [(file.name, file.stat().st_size) for file in files]
    if [(s["attributes"]["fileName"], s["attributes"]["fileSize"]) for s in existing] == wanted:
        print(f"{display_type}: already up to date")
        return
    for shot in existing:
        client.request("DELETE", f"/v1/appScreenshots/{shot['id']}")
    order = []
    for file in files:
        content = file.read_bytes()
        shot = client.request("POST", "/v1/appScreenshots", {"data": {
            "type": "appScreenshots", "attributes": {"fileName": file.name, "fileSize": len(content)},
            "relationships": {"appScreenshotSet": relation("appScreenshotSets", current["id"])},
        }})["data"]
        upload_file(shot["attributes"]["uploadOperations"], content)
        patch(client, "appScreenshots", shot["id"], {
            "uploaded": True, "sourceFileChecksum": hashlib.md5(content).hexdigest(),
        })
        order.append({"type": "appScreenshots", "id": shot["id"]})
    client.request("PATCH", f"/v1/appScreenshotSets/{current['id']}/relationships/appScreenshots", {"data": order})
    wait_for_screenshots(client, current["id"])
    print(f"{display_type}: uploaded {len(files)} screenshots")


def attach_build(client, app_id, version, build_number):
    draft = find_draft(client, app_id, version)
    builds = client.list("/v1/builds", {
        "filter[app]": app_id, "filter[version]": build_number, "filter[preReleaseVersion.version]": version,
    })
    if not builds:
        raise AppleError(f"Build {build_number} of version {version} doesn't exist")
    build = builds[0]
    if build["attributes"]["processingState"] != "VALID":
        raise AppleError(f"Build {build_number} is {build['attributes']['processingState']}, not VALID")
    client.request("PATCH", f"/v1/appStoreVersions/{draft['id']}/relationships/build",
                   relation("builds", build["id"]))
    print(f"Attached build {build_number} to version {version}.")


def status(client, app_id):
    """What the API can see is missing. App Privacy and agreements aren't visible to it."""
    draft = editable_version(ios_versions(client, app_id))
    if not draft:
        print("No draft version.")
        return
    attributes = draft["attributes"]
    print(f"Version {attributes['versionString']}: {attributes['appStoreState']}, release {attributes['releaseType']}")
    loc = localization(client, f"/v1/appStoreVersions/{draft['id']}/appStoreVersionLocalizations")
    for item in client.list(f"/v1/appStoreVersionLocalizations/{loc['id']}/appScreenshotSets"):
        count = len(client.list(f"/v1/appScreenshotSets/{item['id']}/appScreenshots"))
        print(f"{item['attributes']['screenshotDisplayType']}: {count} screenshots")
    build = client.request("GET", f"/v1/appStoreVersions/{draft['id']}/build").get("data")
    print("Build:", build["attributes"]["version"] if build else "none")
    detail = client.request("GET", f"/v1/appStoreVersions/{draft['id']}/appStoreReviewDetail").get("data")
    contact = (detail or {}).get("attributes", {})
    missing = [key for key in REVIEW_CONTACT if not contact.get(key)]
    print("Review contact missing:", ", ".join(missing) or "nothing")
    info = app_info(client, app_id)
    rating = client.request("GET", f"/v1/appInfos/{info['id']}/ageRatingDeclaration")["data"]["attributes"]
    open_answers = [k for k in AGE_NONE | AGE_FALSE if k in rating and rating[k] is None]
    print("Age rating unanswered:", ", ".join(sorted(open_answers)) or "nothing")
    print("Check on the website: App Privacy, agreements.")


def submit(client, app_id, version):
    draft = find_draft(client, app_id, version)
    if not client.request("GET", f"/v1/appStoreVersions/{draft['id']}/build").get("data"):
        raise AppleError("The draft has no build; run attach-build first")
    submission = client.request("POST", "/v1/reviewSubmissions", {"data": {
        "type": "reviewSubmissions", "attributes": {"platform": "IOS"},
        "relationships": {"app": relation("apps", app_id)},
    }})["data"]
    client.request("POST", "/v1/reviewSubmissionItems", {"data": {
        "type": "reviewSubmissionItems",
        "relationships": {
            "reviewSubmission": relation("reviewSubmissions", submission["id"]),
            "appStoreVersion": relation("appStoreVersions", draft["id"]),
        },
    }})
    patch(client, "reviewSubmissions", submission["id"], {"submitted": True})
    print(f"Submitted version {version} for review.")


def screenshot_files(directory, device):
    folder = directory / device
    files = sorted(file for file in folder.iterdir() if file.suffix.lower() in (".png", ".jpg", ".jpeg")) \
        if folder.is_dir() else []
    if not files:
        raise AppleError(f"No screenshots in {folder}")
    if len(files) > 10:
        raise AppleError(f"{folder} has more than Apple's 10 screenshots")
    return files


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--config", type=Path, default=default_config_path())
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("status")
    push = commands.add_parser("push")
    push.add_argument("--version", required=True)
    push.add_argument("--listing", type=Path, default=DEFAULT_LISTING)
    push.add_argument("--whats-new", default="", help="not accepted on the first version")
    push.add_argument("--release-type", choices=["MANUAL", "AFTER_APPROVAL"], help="default: keep the current one")
    push.add_argument("--screenshots", type=Path, help="folder with iphone/ and ipad/, in file name order; "
                                                       "without it a new version keeps the previous images")
    attach = commands.add_parser("attach-build")
    attach.add_argument("--version", required=True)
    attach.add_argument("--build", required=True)
    send = commands.add_parser("submit")
    send.add_argument("--version", required=True)
    send.add_argument("--yes", action="store_true", help="confirm sending the version to App Review")
    args = parser.parse_args()

    if args.command == "submit" and not args.yes:
        parser.error("Submitting requires --yes after reviewing the draft")
    config = load_config(args.config.expanduser())
    client = Client(config["key_file"], config["key_id"], config["issuer_id"])
    app_id = config["app_id"]
    try:
        if args.command == "status":
            status(client, app_id)
        elif args.command == "attach-build":
            attach_build(client, app_id, args.version, args.build)
        elif args.command == "submit":
            submit(client, app_id, args.version)
        else:
            listing = json.loads(args.listing.read_text(encoding="utf-8"))
            check_listing({**listing, "whatsNew": args.whats_new})
            shots = {device: screenshot_files(args.screenshots, device) for device in DISPLAY_TYPES} \
                if args.screenshots else {}
            draft = draft_version(client, app_id, args.version)
            info = push_app_info(client, app_id, listing)
            push_age_rating(client, info)
            loc = push_version(client, draft["id"], listing, args.whats_new, args.release_type)
            push_review_detail(client, draft["id"], listing, config.get("review_contact", {}))
            for device, files in shots.items():
                push_screenshots(client, loc["id"], DISPLAY_TYPES[device], files)
            print(f"Version {args.version} is up to date as a draft. Nothing was submitted.")
    except (AppleError, urllib.error.URLError) as error:
        raise SystemExit(f"error: {error}") from None


if __name__ == "__main__":
    main()
