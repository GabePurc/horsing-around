#!/bin/sh
# Turns the store shots filmed by `./gradlew runClientGameTest -Ptests=store` into the README's media in docs/media/:
# clips into looping animated WebP, stills into JPEG. Needs ffmpeg, ImageMagick and img2webp (libwebp) on the PATH.
# Usage: scripts/store-media.sh [clip ...]   (all filmed clips when none are named)
#        scripts/store-media.sh --clip <clip> <first frame> <frames>   (part of a clip)
#        scripts/store-media.sh --still <shot> <name>   (a still, e.g. hat_2, as docs/media/<name>.jpg)
set -eu
cd "$(dirname "$0")/.."
src=${STORE_SHOTS:-build/run/clientGameTest/store}
out=docs/media
width=${WIDTH:-640}
quality=${QUALITY:-50}
mkdir -p "$out"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

clip() {
	name=$1
	# One frame a game tick: 20 a second.
	rm -f "$tmp"/*.png
	ffmpeg -loglevel error -framerate 20 -start_number "${2:-0}" -i "$src/$name/%04d.png" ${3:+-frames:v $3} -vf "scale=$width:-2:flags=lanczos" "$tmp/%04d.png"
	img2webp -loop 0 -lossy -q $quality -m 6 -d 50 "$tmp"/*.png -o "$out/$name.webp" > /dev/null
	echo "$out/$name.webp $(du -h "$out/$name.webp" | cut -f1)"
}

if [ "${1:-}" = "--still" ]; then
	magick "$src/$2.png" -resize 1600x -quality 86 -strip "$out/$3.jpg"
	echo "$out/$3.jpg $(du -h "$out/$3.jpg" | cut -f1)"
	exit 0
fi
if [ "${1:-}" = "--clip" ]; then
	clip "$2" "$3" "$4"
	exit 0
fi
if [ $# -eq 0 ]; then
	set -- $(cd "$src" && for d in */; do echo "${d%/}"; done)
fi
for name in "$@"; do
	clip "$name"
done
