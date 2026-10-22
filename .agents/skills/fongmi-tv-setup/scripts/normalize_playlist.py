#!/usr/bin/env python3
"""Offline, metadata-preserving normalization of FongMi external Group[] JSON."""

import argparse
import copy
import json
import os
from pathlib import Path
import re
import sys
import tempfile

GROUPS = ("常看", "央视", "卫视", "地方台", "少儿教育", "其他")
URL = re.compile(r"^[A-Za-z][A-Za-z0-9+.-]*://\S+$")
CCTV = re.compile(r"^CCTV[- ]?(\d+)(\+)?(?:\s|$)", re.IGNORECASE)


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, allow_nan=False)


def nonempty(value, location):
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{location}: expected nonempty string")


def normalize(data, organize=False, renumber=False):
    if not isinstance(data, list):
        raise ValueError("Expected external Group[] array, not a LiveConfig object or M3U")
    out = []
    seen_groups = set()
    input_entries = input_lines = duplicates = merged = 0
    conflicts = []
    for gi, raw_group in enumerate(data):
        loc = f"group[{gi}]"
        if not isinstance(raw_group, dict):
            raise ValueError(f"{loc}: expected object")
        nonempty(raw_group.get("name"), loc + ".name")
        name = raw_group["name"]
        if name in seen_groups:
            raise ValueError(f"Duplicate group name {name!r}; curate groups explicitly")
        seen_groups.add(name)
        if not isinstance(raw_group.get("channel"), list):
            raise ValueError(f"{loc}.channel: expected array")
        group = copy.deepcopy(raw_group)
        group["channel"] = []
        compatible = {}
        name_keys = {}
        for ci, raw in enumerate(raw_group["channel"]):
            where = f"{loc}.channel[{ci}]"
            if not isinstance(raw, dict):
                raise ValueError(f"{where}: expected object")
            nonempty(raw.get("name"), where + ".name")
            urls = raw.get("urls")
            if not isinstance(urls, list) or not urls:
                raise ValueError(f"{where}.urls: expected nonempty array")
            for url in urls:
                if (not isinstance(url, str) or not URL.fullmatch(url)
                        or any(ord(c) < 32 or ord(c) == 127 for c in url)):
                    raise ValueError(f"{where}.urls: malformed URL; input preserved")
            input_entries += 1
            input_lines += len(urls)
            channel = copy.deepcopy(raw)
            channel["urls"] = list(dict.fromkeys(urls))
            duplicates += len(urls) - len(channel["urls"])
            identity = {k: v for k, v in channel.items()
                        if k != "urls" and not (renumber and k == "number")}
            key = canonical(identity)
            if raw["name"] in name_keys and key not in name_keys[raw["name"]]:
                conflicts.append({"group": name, "name": raw["name"]})
            name_keys.setdefault(raw["name"], set()).add(key)
            if key in compatible:
                target = compatible[key]
                for url in channel["urls"]:
                    if url in target["urls"]:
                        duplicates += 1
                    else:
                        target["urls"].append(url)
                merged += 1
            else:
                compatible[key] = channel
                group["channel"].append(channel)
        if group["channel"]:
            if organize:
                def order(channel):
                    match = CCTV.match(channel["name"])
                    return ((0, int(match[1]), bool(match[2])) if match
                            else (1, 0, False))
                group["channel"].sort(key=order)
            out.append(group)
    if organize:
        out.sort(key=lambda g: GROUPS.index(g["name"]) if g["name"] in GROUPS else len(GROUPS))
    channels = [c for g in out for c in g["channel"]]
    if renumber:
        for number, channel in enumerate(channels, 1):
            channel["number"] = str(number)
    lines = sum(len(c["urls"]) for c in channels)
    return out, {"input_entries": input_entries, "input_lines": input_lines,
                 "output_entries": len(channels), "playback_lines": lines,
                 "alternative_lines": lines - len(channels),
                 "exact_duplicates_removed": duplicates, "merged_entries": merged,
                 "unmerged_name_conflicts": conflicts,
                 "groups": {g["name"]: len(g["channel"]) for g in out},
                 "verification": "syntax_only"}


def reject_duplicate_keys(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"Duplicate JSON key {key!r}; refusing lossy parsing")
        result[key] = value
    return result


def write_new(path, text):
    """Publish complete UTF-8 output without overwriting an existing path."""
    fd, tmp = tempfile.mkstemp(prefix=".playlist-", dir=path.parent)
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as handle:
            handle.write(text)
            handle.flush()
            os.fsync(handle.fileno())
        os.link(tmp, path)
    finally:
        os.unlink(tmp)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--organize", action="store_true")
    parser.add_argument("--renumber", action="store_true")
    args = parser.parse_args()
    if not args.dry_run and args.output is None:
        parser.error("--output is required unless --dry-run is used")
    try:
        if args.output is not None and args.output.resolve() == args.input.resolve():
            raise ValueError("Input and output must be different paths")
        data = json.loads(args.input.read_text(encoding="utf-8-sig"),
                          object_pairs_hook=reject_duplicate_keys)
        out, summary = normalize(data, args.organize, args.renumber)
        encoded = json.dumps(out, ensure_ascii=False, indent=2, allow_nan=False) + "\n"
        if not args.dry_run:
            write_new(args.output, encoded)
        summary["dry_run"] = args.dry_run
        print(json.dumps(summary, ensure_ascii=False, indent=2))
        return 0
    except (OSError, ValueError, TypeError) as error:
        print(f"Error: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
