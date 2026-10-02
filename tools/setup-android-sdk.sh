#!/usr/bin/env bash
# Installs the Android SDK pieces this project builds against and points
# local.properties at them. Safe to re-run: finished steps are skipped.
#
#   tools/setup-android-sdk.sh            # installs into ~/android-sdk
#   ANDROID_HOME=/opt/sdk tools/setup-android-sdk.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/android-sdk}}"
TOOLS_ZIP="commandlinetools-linux-16111833_latest.zip"
PACKAGES=("platform-tools" "platforms;android-37.0" "build-tools;36.0.0")

if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "Installing Android command-line tools into $SDK"
  tmp="$(mktemp -d)"
  curl -fsSL "https://dl.google.com/android/repository/$TOOLS_ZIP" -o "$tmp/tools.zip"
  mkdir -p "$SDK/cmdline-tools"
  unzip -q "$tmp/tools.zip" -d "$tmp"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$tmp"
fi

SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
missing=()
for p in "${PACKAGES[@]}"; do
  dir="$SDK/${p//;//}"
  [ -d "$dir" ] || missing+=("$p")
done
if [ "${#missing[@]}" -gt 0 ]; then
  yes | "$SDKMANAGER" --sdk_root="$SDK" --licenses >/dev/null || true
  "$SDKMANAGER" --sdk_root="$SDK" "${missing[@]}"
fi

echo "sdk.dir=$SDK" > "$ROOT/local.properties"
echo "Android SDK ready at $SDK"
