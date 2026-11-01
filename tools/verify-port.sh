#!/usr/bin/env bash
# Sequential, bounded-memory checks. JAVA_HOME must point to JDK 17; Android SDK 36 is required.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
shared_tasks=(
  :core:shared:jvmTest :shared:ui:jvmTest
  :shared:ui:compileCommonMainKotlinMetadata
)
android_tasks=(:app:assembleDebug :core:data:testDebugUnitTest :app:testDebugUnitTest)
# No selector keeps the original full local verification command.
case "${1:-}" in
  --android-only) tasks=("${android_tasks[@]}"); shift ;;
  --shared-only) tasks=("${shared_tasks[@]}"); shift ;;
  *) tasks=("${shared_tasks[@]}" "${android_tasks[@]}") ;;
esac
exec bash ./gradlew --no-daemon --max-workers=1 \
  -Dorg.gradle.jvmargs='-Xmx2304m -XX:MaxMetaspaceSize=768m -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8' \
  -Pkotlin.compiler.execution.strategy=in-process \
  "${tasks[@]}" "$@"
