#!/usr/bin/env bash
# Build filehub-android on the remote build host and fetch the APK back.
#
# Usage:
#   ./scripts/build-remote.sh            # debug APK
#   ./scripts/build-remote.sh check      # lint + unit tests
#   ./scripts/build-remote.sh release    # release APK (unsigned unless keystore is configured)
#   ./scripts/build-remote.sh wrapper    # generate/refresh the Gradle wrapper only
#
# Environment overrides:
#   FILEHUB_ANDROID_BUILD_HOST  SSH alias (default: build-host)
#   FILEHUB_ANDROID_BUILD_DIR   remote workspace (default: ~/build/filehub-android)
#   ANDROID_SDK_ROOT            remote SDK root (default: $HOME/android-sdk)
#   GRADLE_VERSION              default: 9.8.2
set -euo pipefail

MODE="${1:-debug}"
HOST="${FILEHUB_ANDROID_BUILD_HOST:-build-host}"
REMOTE_DIR="${FILEHUB_ANDROID_BUILD_DIR:-~/build/filehub-android}"
SDK_ROOT="${ANDROID_SDK_ROOT:-\$HOME/android-sdk}"
GRADLE_VERSION="${GRADLE_VERSION:-9.8.2}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SSH_CMD=(ssh -o BatchMode=yes)
RSYNC_SSH="ssh -o BatchMode=yes"

case "$MODE" in
  debug)   TASK=":app:assembleDebug" ;;
  release) TASK=":app:assembleRelease" ;;
  check)   TASK=":app:lintDebug :app:testDebugUnitTest" ;;
  wrapper) TASK="" ;;
  *) echo "usage: $0 [debug|release|check|wrapper]" >&2; exit 2 ;;
esac

echo "syncing sources to $HOST:$REMOTE_DIR"
"${SSH_CMD[@]}" "$HOST" "mkdir -p '$REMOTE_DIR'"
rsync -a --delete -e "$RSYNC_SSH" \
  --exclude .git \
  --exclude .gradle \
  --exclude app/build \
  --exclude build \
  --exclude dist \
  --exclude local.properties \
  "$ROOT/" "$HOST:$REMOTE_DIR/"

# Ensure SDK + Gradle wrapper exist on the remote host.
"${SSH_CMD[@]}" "$HOST" bash -s <<REMOTE
set -euo pipefail
export ANDROID_SDK_ROOT="$SDK_ROOT"
SDK_TOOLS="\$ANDROID_SDK_ROOT/cmdline-tools/latest/bin"
if [[ ! -x "\$SDK_TOOLS/sdkmanager" ]]; then
  echo "Android SDK missing; run scripts/provision-sdk.sh on $HOST" >&2
  exit 1
fi
export PATH="\$SDK_TOOLS:\$ANDROID_SDK_ROOT/platform-tools:\$PATH"

cd "$REMOTE_DIR"
if [[ ! -f gradlew || ! -f gradle/wrapper/gradle-wrapper.jar ]]; then
  echo "generating Gradle wrapper $GRADLE_VERSION"
  GRADLE_HOME="\$HOME/.local/gradle-$GRADLE_VERSION"
  if [[ ! -x "\$GRADLE_HOME/bin/gradle" ]]; then
    mkdir -p "\$HOME/.local"
    curl -fL --retry 3 -o /tmp/gradle-$GRADLE_VERSION-bin.zip \
      "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
    rm -rf "\$GRADLE_HOME"
    mkdir -p "\$HOME/.local"
    unzip -q /tmp/gradle-$GRADLE_VERSION-bin.zip -d "\$HOME/.local"
    rm -f /tmp/gradle-$GRADLE_VERSION-bin.zip
  fi
  "\$GRADLE_HOME/bin/gradle" wrapper --gradle-version "$GRADLE_VERSION" --distribution-type bin
fi

if [[ "$MODE" == "wrapper" ]]; then
  echo "wrapper ready"
  exit 0
fi

echo "building $MODE on $HOST"
GRADLE_BIN="./gradlew"
if [[ -x "\$HOME/.local/gradle-$GRADLE_VERSION/bin/gradle" ]]; then
  GRADLE_BIN="\$HOME/.local/gradle-$GRADLE_VERSION/bin/gradle"
fi
"\$GRADLE_BIN" $TASK --stacktrace --console=plain
REMOTE

if [[ "$MODE" == "wrapper" || "$MODE" == "check" ]]; then
  if [[ "$MODE" == "wrapper" ]]; then
    echo "copying wrapper back"
    mkdir -p "$ROOT/gradle/wrapper"
    rsync -a -e "$RSYNC_SSH" \
      "$HOST:$REMOTE_DIR/gradlew" "$HOST:$REMOTE_DIR/gradlew.bat" "$ROOT/" 2>/dev/null || \
      rsync -a -e "$RSYNC_SSH" "$HOST:$REMOTE_DIR/gradlew" "$ROOT/"
    rsync -a -e "$RSYNC_SSH" "$HOST:$REMOTE_DIR/gradle/wrapper/" "$ROOT/gradle/wrapper/"
    chmod +x "$ROOT/gradlew"
  fi
  echo "done"
  exit 0
fi

mkdir -p "$ROOT/dist"
case "$MODE" in
  debug)
    REMOTE_APK="$REMOTE_DIR/app/build/outputs/apk/debug/app-debug.apk"
    LOCAL_APK="$ROOT/dist/filehub-android-debug.apk"
    ;;
  release)
    REMOTE_APK="$REMOTE_DIR/app/build/outputs/apk/release/app-release-unsigned.apk"
    LOCAL_APK="$ROOT/dist/filehub-android-release-unsigned.apk"
    # If a signed APK exists, prefer it.
    if "${SSH_CMD[@]}" "$HOST" "test -f '$REMOTE_DIR/app/build/outputs/apk/release/app-release.apk'"; then
      REMOTE_APK="$REMOTE_DIR/app/build/outputs/apk/release/app-release.apk"
      LOCAL_APK="$ROOT/dist/filehub-android-release.apk"
    fi
    ;;
esac

rsync -a -e "$RSYNC_SSH" "$HOST:$REMOTE_APK" "$LOCAL_APK"
echo "artifact: $LOCAL_APK"
ls -lh "$LOCAL_APK"
sha256sum "$LOCAL_APK"
