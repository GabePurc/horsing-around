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
if [ ! -d "$src" ]; then
	echo "No store shots in $src: film them with ./gradlew runClientGameTest -Ptests=store first (or set STORE_SHOTS)" >&2
	exit 1
fi
width=${WIDTH:-720}
quality=${QUALITY:-50}
mkdir -p "$out"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

clip() {
	name=$1
	rm -f "$tmp"/*.png
	ffmpeg -loglevel error -framerate 24 -start_number "${2:-0}" -i "$src/$name/%04d.png" ${3:+-frames:v $3} -vf "scale=$width:-2:flags=lanczos" "$tmp/%04d.png"
	# The test films 24 frames a second; WebP times frames in whole milliseconds, so 42, 42, 41 keeps the pace exact.
	# Every frame a whole picture (-kmax 1), not a patch drawn over the one before: browsers then decode each frame on its
	# own, cheaply, instead of falling behind on a chain of a few hundred and playing slow or stuttering. Barely bigger,
	# since the camera moves and nearly everything changes each frame anyway.
	set --
	i=0
	for f in "$tmp"/*.png; do
		if [ $((i % 3)) -eq 2 ]; then set -- "$@" -d 41 "$f"; else set -- "$@" -d 42 "$f"; fi
		i=$((i + 1))
	done
	img2webp -loop 0 -lossy -q $quality -m 6 -kmin 0 -kmax 1 "$@" -o "$out/$name.webp" > /dev/null
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
	set -- $(cd "$src" && for d in */; do if [ -d "$d" ]; then echo "${d%/}"; fi; done)
	if [ $# -eq 0 ]; then
		echo "No clips in $src (a clip is a folder of numbered frames)" >&2
		exit 1
	fi
fi
for name in "$@"; do
	clip "$name"
done
