#!/usr/bin/env bash
# Sequential, bounded-memory checks. JAVA_HOME must point to JDK 17; Android SDK 36 is required.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
exec bash ./gradlew --no-daemon --max-workers=1 \
  -Dorg.gradle.jvmargs='-Xmx2304m -XX:MaxMetaspaceSize=768m -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8' \
  -Pkotlin.compiler.execution.strategy=in-process \
  :core:shared:jvmTest :shared:ui:jvmTest \
  :shared:ui:compileCommonMainKotlinMetadata \
  :app:assembleDebug :core:data:testDebugUnitTest :app:testDebugUnitTest "$@"
