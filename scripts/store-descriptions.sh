#!/bin/sh
# The store descriptions, made from the README for pasting into each site's project description.
# Modrinth: each clip as a looping video (docs/media/<clip>.mp4) in place of the animated WebP that GitHub shows (GitHub
# drops <video> tags; Modrinth plays them, and video stays smooth where large animated images stutter).
# CurseForge: drops <video> tags too, so the clips stay WebP. It also drops align="center", so the centred blocks centre
# by style instead. (Buttons are sized by width everywhere: CurseForge's height:auto overrides a height attribute.)
# Usage: scripts/store-descriptions.sh   (writes build/modrinth-description.md and build/curseforge-description.md)
set -eu
cd "$(dirname "$0")/.."
mkdir -p build
media='https://raw\.githubusercontent\.com/GabePurc/horsing-around/main/docs/media'
sed -E "s#<img src=\"($media/[a-z]+)\.webp\" alt=\"[^\"]*\" width=\"100%\">#<video src=\"\1.mp4\" autoplay loop muted playsinline width=\"100%\"></video>#" \
	README.md > build/modrinth-description.md
sed 's#<div align="center">#<div style="text-align: center;">#' README.md > build/curseforge-description.md
echo "build/modrinth-description.md: $(grep -c '<video' build/modrinth-description.md) clips as video"
echo "build/curseforge-description.md: $(grep -c '\.webp"' build/curseforge-description.md) clips as WebP"
