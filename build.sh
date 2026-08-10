#!/usr/bin/env bash
# Builds the Compass app APK or Android App Bundle.
#
# Usage:
#   ./build.sh              # debug APK
#   ./build.sh --release    # release APK, signed if keystore.properties exists
#   ./build.sh --bundle     # release .aab (App Bundle) for Play Store upload, signed
#   ./build.sh --clean      # clean before building
#   ./build.sh --install    # build debug and install on a connected device/emulator via adb
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

VARIANT="Debug"
CLEAN=false
INSTALL=false
BUNDLE=false

for arg in "$@"; do
    case "$arg" in
        --release) VARIANT="Release" ;;
        --bundle) BUNDLE=true; VARIANT="Release" ;;
        --clean) CLEAN=true ;;
        --install) INSTALL=true ;;
        *)
            echo "Unknown option: $arg" >&2
            echo "Usage: $0 [--release] [--bundle] [--clean] [--install]" >&2
            exit 1
            ;;
    esac
done

if [ "$CLEAN" = true ]; then
    ./gradlew clean
fi

if [ "$BUNDLE" = true ]; then
    ./gradlew "bundle${VARIANT}"
    OUT_PATH=$(find "app/build/outputs/bundle" -iname "*.aab" | head -n 1)
else
    ./gradlew "assemble${VARIANT}"
    OUT_PATH=$(find "app/build/outputs/apk" -iname "*${VARIANT,,}*.apk" | head -n 1)
fi

if [ -z "$OUT_PATH" ]; then
    echo "Build finished but no output file was found." >&2
    exit 1
fi

echo ""
echo "Built: $OUT_PATH"

if [ "$VARIANT" = "Release" ] && [ ! -f keystore.properties ]; then
    echo "Note: no keystore.properties found — this release build is unsigned." >&2
fi

if [ "$INSTALL" = true ]; then
    if [ "$VARIANT" != "Debug" ]; then
        echo "Note: --install only supported for debug builds." >&2
        exit 1
    fi
    if ! command -v adb >/dev/null 2>&1; then
        echo "adb not found on PATH; cannot install." >&2
        exit 1
    fi
    adb install -r "$OUT_PATH"
fi
