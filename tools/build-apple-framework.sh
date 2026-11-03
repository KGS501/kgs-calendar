#!/usr/bin/env bash
# Called by Xcode with SDK_NAME, ARCHS, CONFIGURATION and signing settings in the environment.
set -euo pipefail
if [[ "$(uname -s)" != Darwin ]]; then
  echo 'Building the Apple framework requires macOS with Xcode.' >&2
  exit 1
fi
if [[ -z "${JAVA_HOME:-}" ]]; then
  export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
fi
cd "$(dirname "${BASH_SOURCE[0]}")/.."
# Native Release optimization of the whole app needs more heap than the JVM/Android checks:
# 3 GiB ran out of memory once the full app was shared. 5 GiB leaves ~2 GB of the 7 GB macOS
# runner for Xcode; one worker and two processors. KGS_APPLE_FRAMEWORK_HEAP overrides it.
heap="${KGS_APPLE_FRAMEWORK_HEAP:-5120m}"
exec bash ./gradlew :shared:ui:embedAndSignAppleFrameworkForXcode --no-daemon --max-workers=1 \
  -Pkotlin.compiler.execution.strategy=in-process \
  -Dorg.gradle.jvmargs="-Xmx$heap -XX:MaxMetaspaceSize=768m -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8"
