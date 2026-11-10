#!/usr/bin/env bash
# Posts a GitHub release's notes to the Discord server: scripts/discord-release.sh <tag>. publish.yml runs it once a
# release's jars are up; the discord workflow runs it by hand. Needs gh (signed in, or GH_TOKEN) and jq. Without
# DISCORD_RELEASES_WEBHOOK it prints the message instead, as a preview.
set -euo pipefail

tag=${1:?usage: scripts/discord-release.sh <tag>}
download=https://www.curseforge.com/minecraft/mc-mods/horsing-around-horse-riding-rework
icon=https://raw.githubusercontent.com/GabePurc/horsing-around/main/src/main/resources/assets/horsingaround/icon.png

# (Embeds don't show headings, so they become bold lines. An embed's text holds 4,096 characters: longer notes are cut
# at a line, with a link to the rest.)
message=$(gh release view "$tag" --json name,body,url,isPrerelease | jq --arg download "$download" --arg icon "$icon" '
	(.body | gsub("\r"; "") | split("\n") | map(sub("^#{1,6} +(?<h>.+)$"; "**\(.h)**")) | join("\n") | sub("\\s+$"; "")) as $notes
	| "\n\n**[Download on CurseForge](\($download))**" as $tail
	| (if ($notes | length) + ($tail | length) <= 4096 then $notes
		else ($notes[:3900] | sub("\n[^\n]*$"; "")) + "\n…\n[All the notes](\(.url))" end) as $text
	| {
		username: "Horsing Around",
		avatar_url: $icon,
		allowed_mentions: {parse: []},
		embeds: [{
			title: (.name + (if .isPrerelease then " (beta)" else "" end)),
			url: .url,
			description: ($text + $tail),
			color: 9132587
		}]
	}')

if [ -z "${DISCORD_RELEASES_WEBHOOK:-}" ]; then
	echo "No DISCORD_RELEASES_WEBHOOK, so nothing was posted. The message would be:"
	echo "$message"
	exit 0
fi
curl -fsS -H 'content-type: application/json' -d "$message" "$DISCORD_RELEASES_WEBHOOK"
echo "Posted $tag to Discord."
