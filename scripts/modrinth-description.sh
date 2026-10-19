#!/bin/sh
# The Modrinth description: the README with each clip as a looping video (docs/media/<clip>.mp4) in place of the
# animated WebP that GitHub shows (GitHub drops <video> tags; Modrinth plays them, and video stays smooth where five
# large animated images stutter). Paste the result into the project's description on Modrinth.
# Usage: scripts/modrinth-description.sh   (writes build/modrinth-description.md)
set -eu
cd "$(dirname "$0")/.."
mkdir -p build
media='https://raw\.githubusercontent\.com/GabePurc/horsing-around/main/docs/media'
sed -E "s#<img src=\"($media/[a-z]+)\.webp\" alt=\"[^\"]*\" width=\"100%\">#<video src=\"\1.mp4\" autoplay loop muted playsinline width=\"100%\"></video>#" \
	README.md > build/modrinth-description.md
echo "build/modrinth-description.md: $(grep -c '<video' build/modrinth-description.md) clips as video"
