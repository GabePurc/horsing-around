#!/usr/bin/env python3
"""Adds a player to supporters.json or removes one; run by .github/workflows/supporters.yml.

Usage: update-supporters.py add|remove <uuid> <name>   (run from the repo root)
Changing nothing (adding someone already there, removing someone who isn't) isn't an error.
"""
import json
import re
import sys

LIST = "supporters.json"
UUID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
NAME = re.compile(r"[A-Za-z0-9_]{1,16}")


def same_player(entry, uuid):
    return entry.get("uuid", "").replace("-", "").lower() == uuid.replace("-", "")


def main(operation, uuid, name):
    uuid = uuid.lower()
    if operation not in ("add", "remove") or not UUID.fullmatch(uuid) or not NAME.fullmatch(name):
        sys.exit("Not a valid supporter change")
    with open(LIST, encoding="utf-8") as file:
        data = json.load(file)
    supporters = data["supporters"]
    if operation == "add":
        if any(same_player(entry, uuid) for entry in supporters):
            return
        supporters.append({"name": name, "uuid": uuid})
    else:
        kept = [entry for entry in supporters if not same_player(entry, uuid)]
        if len(kept) == len(supporters):
            return
        data["supporters"] = kept
    with open(LIST, "w", encoding="utf-8", newline="\n") as file:
        json.dump(data, file, indent="\t", ensure_ascii=False)
        file.write("\n")


if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2], sys.argv[3])
