#!/usr/bin/env python3
"""Run integration tests against a disposable local Nextcloud, then remove it and its data."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import secrets
import subprocess
import tempfile
import time
from urllib.request import urlopen
import uuid

ROOT = Path(__file__).resolve().parents[1]


def docker_command() -> list[str]:
    for command in (["docker"], ["sudo", "-n", "docker"]):
        try:
            if subprocess.run(command + ["info"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0:
                return command
        except FileNotFoundError:
            pass
    raise RuntimeError("Docker is unavailable. Start Docker or configure local Docker access first.")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image", default="nextcloud:34.0.0-apache", help="Version-pinned Nextcloud Apache image")
    parser.add_argument("--all-tests", action="store_true", help="Run every debug unit test with live integration enabled")
    args = parser.parse_args()
    docker = docker_command()
    name = "kgs-calendar-test-" + uuid.uuid4().hex[:12]
    owner = uuid.uuid4().hex
    with tempfile.TemporaryDirectory(prefix="kgs-nextcloud-test-") as temporary:
        root = Path(temporary)
        password = secrets.token_urlsafe(32)
        env_file = root / "server.env"
        fd = os.open(env_file, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, "w") as stream:
            stream.write("NEXTCLOUD_ADMIN_USER=kgs-test\nNEXTCLOUD_ADMIN_PASSWORD=" + password +
                         "\nSQLITE_DATABASE=nextcloud\nNEXTCLOUD_TRUSTED_DOMAINS=127.0.0.1 localhost\n")
        try:
            subprocess.run(docker + ["run", "-d", "--name", name, "--label", "kgs.calendar.test-owner=" + owner,
                "--memory", "1g", "--cpus", "1", "--env-file", str(env_file),
                "-p", "127.0.0.1::80", args.image], check=True, stdout=subprocess.DEVNULL)
            address = subprocess.check_output(docker + ["port", name, "80/tcp"], text=True).strip()
            if not address.startswith("127.0.0.1:") or "\n" in address:
                raise RuntimeError("Test server must be bound only to a local ephemeral port")
            base = "http://" + address
            deadline = time.monotonic() + 240
            while time.monotonic() < deadline:
                try:
                    with urlopen(base + "/status.php", timeout=3) as response:
                        status = json.load(response)
                    if status.get("installed") and not status.get("maintenance"):
                        print("Disposable Nextcloud ready: " + status["versionstring"], flush=True)
                        break
                except (OSError, ValueError):
                    pass
                time.sleep(2)
            else:
                raise RuntimeError("Nextcloud installation did not finish within four minutes")
            env = os.environ.copy()
            env.update(KGS_TEST_NEXTCLOUD_URL=base, KGS_TEST_NEXTCLOUD_USER="kgs-test", KGS_TEST_NEXTCLOUD_PASSWORD=password)
            wrapper = ROOT / ("gradlew.bat" if os.name == "nt" else ".local-dev/gradlew-local.sh")
            # A new disposable server must be exercised even when compiled code is unchanged.
            # Only rerun test tasks; retain compilation caches.
            tasks = ["testDebugUnitTest", "--rerun"] if args.all_tests else [":core:data:testDebugUnitTest", "--rerun", "--tests", "*LiveNextcloud*Test"]
            return subprocess.run([str(wrapper)] + tasks + ["--max-workers=1", "--console=plain",
                "-Dorg.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8", "-Pkotlin.daemon.jvmargs=-Xmx2g"],
                cwd=ROOT, env=env).returncode
        finally:
            # Remove only this invocation's container and its anonymous test-data volume.
            found = subprocess.run(docker + ["inspect", "--format", '{{index .Config.Labels "kgs.calendar.test-owner"}}', name],
                                   text=True, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
            if found.returncode == 0 and found.stdout.strip() == owner:
                subprocess.run(docker + ["rm", "-f", "-v", name], check=True, stdout=subprocess.DEVNULL)
                print("Removed disposable Nextcloud and test data.", flush=True)


if __name__ == "__main__":
    raise SystemExit(main())
