#!/usr/bin/env bash
# Downloads the Nothing Glyph Matrix SDK aar into app/libs/.
#
# The SDK is Nothing's closed-source library and its licence forbids
# redistribution, so it is not committed to this repository. Gradle downloads it
# automatically at build time; run this script only if you want to fetch it
# ahead of time or your build machine is offline during the build.
set -euo pipefail

DEST_DIR="$(cd "$(dirname "$0")/.." && pwd)/app/libs"
DEST="$DEST_DIR/glyph-matrix-sdk-2.0.aar"
URL="https://raw.githubusercontent.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit/main/glyph-matrix-sdk-2.0.aar"

mkdir -p "$DEST_DIR"
if [ -f "$DEST" ]; then
  echo "Already present: $DEST"
  exit 0
fi

echo "Downloading Glyph Matrix SDK -> $DEST"
if command -v curl >/dev/null 2>&1; then
  curl -fsSL "$URL" -o "$DEST"
elif command -v wget >/dev/null 2>&1; then
  wget -qO "$DEST" "$URL"
else
  echo "Need curl or wget to download the SDK." >&2
  exit 1
fi
echo "Done."
