#!/usr/bin/env bash
# Install the Android SDK command-line tools + platform/build-tools on the
# remote build host. Idempotent: safe to re-run.
#
# Usage (on parasite or any Linux host with JDK 17+):
#   ./scripts/provision-sdk.sh
#
# Environment:
#   ANDROID_SDK_ROOT   default: $HOME/android-sdk
#   CMDLINE_TOOLS      default: 16111833 (commandlinetools-linux-*_latest.zip)
set -euo pipefail

SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
CMDLINE_TOOLS="${CMDLINE_TOOLS:-16111833}"
PLATFORM="${PLATFORM:-android-37.1}"
BUILD_TOOLS="${BUILD_TOOLS:-36.0.0}"
TOOLS_DIR="$SDK_ROOT/cmdline-tools/latest"

need() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "missing required command: $1" >&2
    exit 1
  }
}

need java
need javac
need curl
need unzip

java -version 2>&1 | head -1
javac -version

mkdir -p "$SDK_ROOT/cmdline-tools"

if [[ ! -x "$TOOLS_DIR/bin/sdkmanager" ]]; then
  echo "installing command-line tools $CMDLINE_TOOLS into $TOOLS_DIR"
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' EXIT
  curl -fL --retry 3 -o "$tmp/tools.zip" \
    "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS}_latest.zip"
  unzip -q "$tmp/tools.zip" -d "$tmp"
  rm -rf "$TOOLS_DIR"
  mv "$tmp/cmdline-tools" "$TOOLS_DIR"
else
  echo "command-line tools already installed: $TOOLS_DIR"
fi

export ANDROID_SDK_ROOT="$SDK_ROOT"
export PATH="$TOOLS_DIR/bin:$SDK_ROOT/platform-tools:$PATH"

# Accept licenses non-interactively. `yes` exits 141 when the pipe closes;
# that is expected and must not fail the script.
yes | sdkmanager --licenses >/dev/null 2>&1 || true

echo "installing platform $PLATFORM + build-tools $BUILD_TOOLS"
# shellcheck disable=SC2086
sdkmanager "platform-tools" "platforms;$PLATFORM" "build-tools;$BUILD_TOOLS"

echo
echo "SDK ready:"
echo "  ANDROID_SDK_ROOT=$SDK_ROOT"
echo "  sdkmanager=$(command -v sdkmanager)"
sdkmanager --list_installed | sed -n '1,12p'
