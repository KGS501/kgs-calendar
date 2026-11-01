#!/usr/bin/env bash
# Run on a disposable macOS runner with the testflight environment's signing secrets.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."
[[ "$(uname -s)" == Darwin ]] || { echo 'TestFlight upload requires macOS and Xcode.' >&2; exit 1; }
: "${RUNNER_TEMP:?}" "${APPLE_TEAM_ID:?}" "${ASC_KEY_ID:?}" "${ASC_ISSUER_ID:?}"
: "${APPLE_DISTRIBUTION_P12_BASE64:?}" "${APPLE_PROFILE_BASE64:?}" "${APPLE_P12_PASSWORD:?}"
: "${ASC_PRIVATE_KEY:?}" "${KGS_BUILD_NUMBER:?}"

signing_dir=$(mktemp -d "$RUNNER_TEMP/kgs-signing.XXXXXX")
keychain_path="$signing_dir/signing.keychain-db"
profile_path=''
legacy_profile_path=''
cleanup() {
  security delete-keychain "$keychain_path" >/dev/null 2>&1 || true
  if [[ -n "$profile_path" ]]; then rm -f "$profile_path"; fi
  if [[ -n "$legacy_profile_path" ]]; then rm -f "$legacy_profile_path"; fi
  rm -rf "$signing_dir"
}
trap cleanup EXIT

python3 tools/ios/package_app.py prepare "$signing_dir"
profile_uuid=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["uuid"])' "$signing_dir/settings.json")
profile_name=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["profile_name"])' "$signing_dir/settings.json")
keychain_password=$(openssl rand -base64 32)
security create-keychain -p "$keychain_password" "$keychain_path"
security set-keychain-settings -lut 21600 "$keychain_path"
security unlock-keychain -p "$keychain_password" "$keychain_path"
security import "$signing_dir/distribution.p12" -k "$keychain_path" -P "$APPLE_P12_PASSWORD" \
  -T /usr/bin/codesign -T /usr/bin/security
curl --fail --silent --show-error https://www.apple.com/certificateauthority/AppleWWDRCAG3.cer \
  -o "$signing_dir/AppleWWDRCAG3.cer"
security import "$signing_dir/AppleWWDRCAG3.cer" -k "$keychain_path"
security set-key-partition-list -S apple-tool:,apple:,codesign: -s -k "$keychain_password" "$keychain_path" >/dev/null
security list-keychains -d user -s "$keychain_path"

# Xcode 16+ uses the Developer directory; retain compatibility with the older location.
profile_path="$HOME/Library/Developer/Xcode/UserData/Provisioning Profiles/$profile_uuid.mobileprovision"
legacy_profile_path="$HOME/Library/MobileDevice/Provisioning Profiles/$profile_uuid.mobileprovision"
mkdir -p "$(dirname "$profile_path")" "$(dirname "$legacy_profile_path")"
cp "$signing_dir/profile.mobileprovision" "$profile_path"
cp "$signing_dir/profile.mobileprovision" "$legacy_profile_path"
unset APPLE_DISTRIBUTION_P12_BASE64 APPLE_PROFILE_BASE64 APPLE_P12_PASSWORD ASC_PRIVATE_KEY keychain_password

archive_path="$RUNNER_TEMP/kgs-testflight/KgsCalendar.xcarchive"
mkdir -p "$RUNNER_TEMP/kgs-testflight"
xcodebuild -project iosApp/KgsCalendar.xcodeproj -scheme KgsCalendar \
  -configuration Release -sdk iphoneos -destination 'generic/platform=iOS' \
  -archivePath "$archive_path" -derivedDataPath "$RUNNER_TEMP/kgs-derived" -jobs 1 \
  DEVELOPMENT_TEAM="$APPLE_TEAM_ID" CODE_SIGN_STYLE=Manual \
  CODE_SIGN_IDENTITY='Apple Distribution' PROVISIONING_PROFILE_SPECIFIER="$profile_name" \
  CURRENT_PROJECT_VERSION="$KGS_BUILD_NUMBER" \
  archive 2>&1 | tee "$RUNNER_TEMP/kgs-testflight/archive.log"
python3 tools/ios/package_app.py validate "$archive_path" --build-number "$KGS_BUILD_NUMBER"
codesign --verify --strict "$archive_path/Products/Applications/KgsCalendar.app"

# Xcode exports, signs for App Store Connect, and uploads using the API key.
xcodebuild -exportArchive -archivePath "$archive_path" \
  -exportPath "$RUNNER_TEMP/kgs-testflight/export" -exportOptionsPlist "$signing_dir/ExportOptions.plist" \
  -authenticationKeyPath "$signing_dir/AuthKey.p8" -authenticationKeyID "$ASC_KEY_ID" \
  -authenticationKeyIssuerID "$ASC_ISSUER_ID" \
  -allowProvisioningUpdates 2>&1 | tee "$RUNNER_TEMP/kgs-testflight/upload.log"
