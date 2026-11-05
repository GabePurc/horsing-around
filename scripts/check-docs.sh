#!/usr/bin/env bash
# Keeps the project docs cheap to read (AGENTS.md, "Keeping docs cheap"): only the listed files, each under its budget,
# and every repo path, class and -P flag they name still exists. Run from anywhere; CI runs it on every pull request.
set -u
cd "$(dirname "$0")/.." || exit 2

# Budgets in KB. Over budget: cut something first. Raise one only when what's there is all worth its cost.
budgets="AGENTS.md:5.5 docs/design.md:12 docs/design-log.md:14 docs/plan.md:4 docs/testing.md:9 docs/porting.md:9.5 docs/releasing.md:2"
# Not budgeted: player-facing, and not read for development.
public="README.md CHANGELOG.md"

problems=0
problem() { echo "$1"; problems=$((problems + 1)); }

tracked=$(git ls-files -co --exclude-standard)

for doc in $(grep -E '\.md$' <<<"$tracked"); do
	[ -f "$doc" ] || continue
	case " $budgets $public " in
	*" $doc:"* | *" $doc "*) ;;
	*) problem "$doc: not a known doc. Put it in an existing doc, or add it to this script if it earns its own file." ;;
	esac
done

for entry in $budgets; do
	doc=${entry%:*}
	budget=${entry#*:}
	[ -f "$doc" ] || {
		problem "$doc: missing. Remove it from this script if it was deleted on purpose."
		continue
	}
	bytes=$(wc -c <"$doc")
	if awk -v b="$bytes" -v k="$budget" 'BEGIN { exit !(b > k * 1024) }'; then
		problem "$doc: $((bytes / 1024)) KB, over its $budget KB budget. Cut what stopped being useful."
	fi

	while IFS= read -r name; do
		case "$name" in
		*' '* | *'...'* | *'<'* | *'*'* | '~'* | /* | ./* | -* | build/* | run/*) ;;
		*/* | *.md | *.java | *.gradle | *.yml | *.sh | *.py | *.json | *.toml | *.properties | *.bbmodel)
			grep -qF -- "${name%/}" <<<"$tracked" || problem "$doc: names \`$name\`, which isn't in the repo."
			;;
		[A-Z]*)
			if [[ $name =~ ^[A-Z][a-zA-Z]+(\.[a-zA-Z]+)?$ ]]; then
				git grep -qw -- "${name%%.*}" -- src || problem "$doc: names \`$name\`, which isn't in src/."
			fi
			;;
		esac
	done < <(grep -oE '`[^`]+`' "$doc" | tr -d '`' | sort -u)

	while IFS= read -r flag; do
		git grep -qE "[\"']$flag[\"']" -- '*.gradle' || problem "$doc: names the flag -P$flag, which no build file reads."
	done < <(grep -oE '(^|[^a-zA-Z])-P[a-zA-Z]+' "$doc" | sed -E 's/.*-P//' | sort -u)
done

if [ "$problems" -gt 0 ]; then
	echo "$problems doc problem(s)."
	exit 1
fi
echo "Docs OK."
