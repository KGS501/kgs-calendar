"""Select CI work from the complete Git diff; uncertainty always runs every build."""

import os
from pathlib import Path
import re
import subprocess


ALL = frozenset({"android", "shared", "ios"})
SHARED_MODULES = ("core/shared/", "shared/ui/")
NON_BUILD_FILES = {
    "README.md", "LICENSE.md", "NOTICE.md", "AGENTS.md", "index.html",
    ".github/FUNDING.yml", ".gitignore",
    "tools/generate_play_store_feature_graphic.py",
    "tools/play_publisher.py", "tools/test_play_publisher.py", "tools/test_nextcloud.py",
}


def builds_for_path(path):
    if path in NON_BUILD_FILES or path.startswith(("docs/", "logo/")):
        return set()
    # Every module is configured even when only an Apple task is requested.
    if path.endswith((".gradle", ".gradle.kts")):
        return ALL
    if path.startswith(("app/", "core/data/")):
        return {"android"}
    if path.startswith(("iosApp/", "tools/ios/")) or path == "tools/build-apple-framework.sh":
        return {"ios"}
    if path == "tools/verify-port.sh":
        return {"android", "shared"}
    for module in SHARED_MODULES:
        if path.startswith(module):
            source = path.removeprefix(module)
            if source.startswith(("src/commonTest/", "src/jvmMain/", "src/jvmTest/")):
                return {"shared"}
            if source.startswith("src/androidMain/"):
                return {"android"}
            if source.startswith(("src/iosMain/", "src/appleMain/", "src/nativeMain/",
                                  "src/iosArm64Main/", "src/iosSimulatorArm64Main/")):
                return {"ios"}
            return ALL
    # Includes Gradle settings/catalog/wrapper, CI configuration, and new modules.
    return ALL


def select_builds(paths):
    selected = set()
    for path in paths:
        selected.update(builds_for_path(path))
    return selected


def changed_paths(base, head):
    for revision in (base, head):
        if not re.fullmatch(r"[0-9a-f]{40}", revision) or revision == "0" * 40:
            raise ValueError("Missing or invalid comparison revision")
    # Checkout is shallow. Fetch only the old snapshot if it is not present locally.
    if subprocess.run(["git", "cat-file", "-e", base + "^{commit}"],
                      stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode:
        subprocess.run(["git", "fetch", "--no-tags", "--depth=1", "origin", base],
                       check=True, timeout=120)
    # No rename detection: both the deleted and added paths must influence selection.
    # NUL separators handle spaces/newlines; there is no API pagination/file-count cap.
    result = subprocess.check_output(
        ["git", "diff", "--name-only", "--no-renames", "-z", base, head, "--"], timeout=120)
    return [os.fsdecode(path) for path in result.split(b"\0") if path]


def select_event(event, base, head, target="all"):
    if event == "workflow_dispatch":
        return {"all": ALL, "android": {"android"}, "shared": {"shared"},
                "ios": {"ios"}}[target]
    if event not in {"pull_request", "push"}:
        return ALL
    try:
        return select_builds(changed_paths(base, head))
    except (OSError, ValueError, subprocess.SubprocessError):
        print("::warning::Could not determine the complete diff; running all builds.")
        return ALL


def main():
    # For PRs, HEAD is GitHub's tested merge commit and BASE_SHA is its base snapshot.
    # For pushes, BASE_SHA is event.before, covering the whole push, not just HEAD^.
    selected = select_event(os.environ["GITHUB_EVENT_NAME"], os.environ.get("BASE_SHA", ""),
                            os.environ["GITHUB_SHA"], os.environ.get("CI_TARGET") or "all")
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
        for name in sorted(ALL):
            output.write(f"{name}={str(name in selected).lower()}\n")
    summary = "Selected builds: " + (", ".join(sorted(selected)) or "none (non-build changes)")
    print(summary)
    if "GITHUB_STEP_SUMMARY" in os.environ:
        with Path(os.environ["GITHUB_STEP_SUMMARY"]).open("a", encoding="utf-8") as output:
            output.write(summary + "\n")


if __name__ == "__main__":
    main()
