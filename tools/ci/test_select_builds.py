import contextlib
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest import mock

from tools.ci import select_builds as ci


class SelectionTest(unittest.TestCase):
    def test_platform_boundaries(self):
        cases = {
            "app/src/main/java/com/kgs/calendar/MainActivity.kt": {"android"},
            "core/data/schemas/Database/1.json": {"android"},
            "app/src/test/WidgetTest.kt": {"android"},
            "iosApp/KgsCalendar/ContentView.swift": {"ios"},
            "iosApp/KgsCalendar.xcodeproj/project.pbxproj": {"ios"},
            "tools/build-apple-framework.sh": {"ios"},
            "tools/ios/upload-testflight.sh": {"ios"},
            "tools/verify-port.sh": {"android", "shared"},
        }
        for path, expected in cases.items():
            with self.subTest(path=path):
                self.assertEqual(ci.select_builds([path]), expected)

    def test_shared_source_sets(self):
        cases = {
            "commonMain": ci.ALL,
            "commonTest": {"shared"},
            "jvmMain": {"shared"},
            "jvmTest": {"shared"},
            "androidMain": {"android"},
            "iosMain": {"ios"},
            "appleMain": {"ios"},
            "nativeMain": {"ios"},
            "iosArm64Main": {"ios"},
            "iosSimulatorArm64Main": {"ios"},
            "futureMain": ci.ALL,
        }
        for module in ci.SHARED_MODULES:
            for source, expected in cases.items():
                with self.subTest(module=module, source=source):
                    self.assertEqual(ci.select_builds([f"{module}src/{source}/Example.kt"]), expected)

    def test_all_build_configuration_including_android_modules_runs_both_platforms(self):
        for path in ["build.gradle.kts", "settings.gradle.kts", "gradle.properties",
                     "gradle/libs.versions.toml", "gradle/wrapper/gradle-wrapper.jar", "gradlew",
                     "gradlew.bat", "app/build.gradle.kts", "core/data/build.gradle.kts",
                     "core/shared/build.gradle.kts", "shared/ui/build.gradle.kts",
                     ".github/workflows/ci.yml", "tools/ci/select_builds.py"]:
            with self.subTest(path=path):
                self.assertEqual(ci.select_builds([path]), ci.ALL)

    def test_non_build_changes_need_no_platform_runner(self):
        self.assertEqual(ci.select_builds([*ci.NON_BUILD_FILES, "docs/testing.md", "logo/logo.png"]), set())

    def test_unknown_paths_run_everything(self):
        self.assertEqual(ci.select_builds(["new-module/src/new.kt"]), ci.ALL)
        self.assertEqual(ci.select_builds(["tools/new-build-helper.py"]), ci.ALL)

    def test_mixed_changes_are_unioned(self):
        self.assertEqual(ci.select_builds(["README.md", "app/src/main/Test.kt",
                                           "iosApp/App.swift"]), {"android", "ios"})

    def test_empty_diff_runs_no_build(self):
        self.assertEqual(ci.select_builds([]), set())

    def test_manual_dispatch_does_not_need_a_diff(self):
        with mock.patch.object(ci, "changed_paths", side_effect=AssertionError("Should not diff")):
            for target, expected in [("all", ci.ALL), ("android", {"android"}),
                                     ("shared", {"shared"}), ("ios", {"ios"})]:
                self.assertEqual(ci.select_event("workflow_dispatch", "", "", target), expected)

    @mock.patch("builtins.print")
    def test_unavailable_diff_runs_everything(self, log):
        with mock.patch.object(ci, "changed_paths", side_effect=subprocess.CalledProcessError(1, "git")):
            self.assertEqual(ci.select_event("pull_request", "a" * 40, "b" * 40), ci.ALL)
        self.assertEqual(ci.select_event("push", "0" * 40, "a" * 40), ci.ALL)
        self.assertEqual(ci.select_event("merge_group", "", ""), ci.ALL)
        self.assertEqual(log.call_count, 2)

    @mock.patch("builtins.print")
    def test_invalid_revisions_are_not_passed_to_git(self, log):
        with mock.patch.object(ci.subprocess, "run") as run:
            self.assertEqual(ci.select_event("push", "--output=/tmp/file", "b" * 40), ci.ALL)
            run.assert_not_called()
        log.assert_called_once()

    def test_github_outputs_and_summary(self):
        with tempfile.TemporaryDirectory() as directory:
            output, summary = Path(directory) / "output", Path(directory) / "summary"
            with mock.patch.dict(os.environ, {
                "GITHUB_EVENT_NAME": "workflow_dispatch", "GITHUB_SHA": "a" * 40,
                "CI_TARGET": "ios", "GITHUB_OUTPUT": str(output), "GITHUB_STEP_SUMMARY": str(summary),
            }):
                ci.main()
            self.assertEqual(output.read_text(), "android=false\nios=true\nshared=false\n")
            self.assertIn("Selected builds: ios", summary.read_text())


class GitDiffTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.git("init", "-q", "-b", "main")
        self.git("config", "user.name", "CI Test")
        self.git("config", "user.email", "ci@example.invalid")
        self.git("config", "commit.gpgsign", "false")
        self.write("README.md")
        self.commit()

    def git(self, *args):
        return subprocess.check_output(["git", "-C", str(self.root), *args], stderr=subprocess.PIPE).decode().strip()

    def write(self, path, content="test\n"):
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content)

    def commit(self):
        self.git("add", ".")
        self.git("commit", "-qm", "test snapshot")
        return self.git("rev-parse", "HEAD")

    def paths(self, base, head):
        with contextlib.chdir(self.root):
            return ci.changed_paths(base, head)

    def test_whole_push_includes_earlier_commits_and_more_than_300_files(self):
        base = self.git("rev-parse", "HEAD")
        self.write("app/src/main/Changed.kt")
        self.commit()
        for i in range(310):
            self.write(f"docs/page-{i}.md")
        head = self.commit()
        paths = self.paths(base, head)
        self.assertEqual(len(paths), 311)
        self.assertEqual(ci.select_builds(paths), {"android"})

    def test_rename_to_docs_preserves_the_removed_source_path(self):
        self.write("app/src/main/Old.kt")
        base = self.commit()
        (self.root / "docs").mkdir()
        self.git("mv", "app/src/main/Old.kt", "docs/Old.kt")
        head = self.commit()
        self.assertEqual(set(self.paths(base, head)), {"app/src/main/Old.kt", "docs/Old.kt"})
        self.assertEqual(ci.select_builds(self.paths(base, head)), {"android"})

    def test_deleted_and_unusual_filenames_are_preserved(self):
        path = "iosApp/Space and\nnewline.swift"
        self.write(path)
        base = self.commit()
        (self.root / path).unlink()
        head = self.commit()
        self.assertEqual(self.paths(base, head), [path])
        self.assertEqual(ci.select_builds(self.paths(base, head)), {"ios"})

    def test_pr_merge_compares_against_current_base_not_stale_branch_point(self):
        self.git("checkout", "-qb", "feature")
        self.write("app/src/main/Changed.kt")
        self.commit()
        self.git("checkout", "-q", "main")
        self.write("iosApp/Unrelated.swift")
        base = self.commit()
        self.git("merge", "--no-ff", "-m", "PR merge", "feature")
        head = self.git("rev-parse", "HEAD")
        self.assertEqual(self.paths(base, head), ["app/src/main/Changed.kt"])

    def test_shallow_checkout_fetches_only_missing_base_snapshot(self):
        base = self.git("rev-parse", "HEAD")
        self.write("iosApp/Changed.swift")
        head = self.commit()
        with tempfile.TemporaryDirectory() as clone:
            subprocess.run(["git", "clone", "--quiet", "--depth=1", self.root.as_uri(), clone], check=True)
            with contextlib.chdir(clone):
                self.assertEqual(ci.changed_paths(base, head), ["iosApp/Changed.swift"])


class VerificationCommandTest(unittest.TestCase):
    def test_task_selection_and_gradle_argument_passthrough(self):
        script = (Path(__file__).resolve().parents[1] / "verify-port.sh").read_text()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "tools").mkdir()
            (root / "tools/verify-port.sh").write_text(script)
            (root / "gradlew").write_text('printf "%s\\n" "$@"\n')
            for selector, shared, android in [(None, True, True), ("--android-only", False, True),
                                               ("--shared-only", True, False)]:
                with self.subTest(selector=selector):
                    command = ["bash", str(root / "tools/verify-port.sh")]
                    if selector:
                        command.append(selector)
                    command.extend(["--console=plain", "-Pexample=two words"])
                    args = subprocess.check_output(command, text=True).splitlines()
                    self.assertEqual(":core:shared:jvmTest" in args, shared)
                    self.assertEqual(":shared:ui:testAndroidHostTest" in args, shared)
                    self.assertEqual(":shared:ui:compileCommonMainKotlinMetadata" in args, shared)
                    self.assertEqual(":app:assembleDebug" in args, android)
                    self.assertEqual(":core:data:testDebugUnitTest" in args, android)
                    self.assertEqual(":app:testDebugUnitTest" in args, android)
                    self.assertIn("--max-workers=1", args)
                    self.assertIn("--console=plain", args)
                    self.assertIn("-Pexample=two words", args)


if __name__ == "__main__":
    unittest.main()
