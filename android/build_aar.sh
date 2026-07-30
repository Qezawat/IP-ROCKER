#!/usr/bin/env bash
# Builds the Go scanner core into an Android AAR consumed by the Gradle project.
#
# Requires: Go, the Android SDK with NDK, and gomobile on PATH.
# ANDROID_HOME (or ANDROID_SDK_ROOT) must point at the SDK.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="$REPO_ROOT/android/app/libs"
OUT_AAR="$OUT_DIR/iprocker.aar"
VERSION="${IPROCKER_VERSION:-dev}"

cd "$REPO_ROOT"

if ! command -v gomobile >/dev/null 2>&1; then
  echo "gomobile not found; installing…" >&2
  go install golang.org/x/mobile/cmd/gomobile@latest
  go install golang.org/x/mobile/cmd/gobind@latest
  export PATH="$PATH:$(go env GOPATH)/bin"
fi

if [[ -z "${ANDROID_HOME:-}" && -z "${ANDROID_SDK_ROOT:-}" ]]; then
  echo "error: set ANDROID_HOME or ANDROID_SDK_ROOT to your Android SDK" >&2
  exit 1
fi

# gomobile needs the bind dependencies present in the module graph.
go get golang.org/x/mobile/bind
gomobile init

mkdir -p "$OUT_DIR"

# Only the mobile package is bound; the internal packages travel with it but
# stay unexported, keeping the Java surface small. No -javapkg is passed, so the
# generated classes land in the `mobile` Java package, matching the Kotlin
# imports in ScannerBridge.kt.
gomobile bind \
  -target=android/arm64,android/arm,android/amd64 \
  -androidapi 24 \
  -ldflags "-s -w -X github.com/Qezawat/IP-ROCKER/mobile.Version=$VERSION" \
  -o "$OUT_AAR" \
  ./mobile

echo "Wrote $OUT_AAR"
ls -lh "$OUT_AAR"
