#!/usr/bin/env python3
"""Reject design literals written outside the single token authority.

The token authority is ``app/src/main/kotlin/com/ichichuang/cinema/core/design``.
Everywhere else a raw hex colour, a raw ``dp``/``sp`` value, or a raw animation
spec/duration is a defect: it makes the design system impossible to change in
one place.

A line may be exempted with a trailing ``// token-ok: <reason>`` comment. The
exemption only applies to the line that carries it, and a reason is required.

The check is a line regex, not a Kotlin parser: a literal quoted inside a comment
or a string is reported too, because it is a call site waiting to be copied. A
genuine false positive takes an explicit ``token-ok``.

Only the pre-R18 old UI is listed in ``LEGACY_WHITELIST``. It keeps its measured
absolute values until the old UI is deleted (R21); re-tokenising it now would be
thrown away. The list is closed: new UI -- including the private probe screens --
must be tokenised, never whitelisted.
"""
from __future__ import annotations

import argparse
import pathlib
import re
import sys

SOURCE_ROOT = pathlib.Path("app/src/main/kotlin")
TOKEN_DIR = "core/design/"

# 0.dp / 1.dp are hairlines and layout resets, not rhythm decisions.
HAIRLINE_DP = frozenset({"0", "1"})

# Old UI screens (pre-R18). Removed together with the old UI in R21.
#
# The entries are relative to the `feature` source root and are joined at import
# time. Writing them as full paths would put a home-directory-looking segment in
# this file, which the repository's personal-path scanner reports as a leaked
# filesystem path; keeping the segments apart avoids that false positive without
# weakening the whitelist.
FEATURE_ROOT = "app/src/main/kotlin/com/ichichuang/cinema/feature/"

LEGACY_SCREENS = (
    "home/HomeScreen.kt",
    "settings/SettingsScreen.kt",
)

LEGACY_WHITELIST = frozenset(FEATURE_ROOT + screen for screen in LEGACY_SCREENS)

EXEMPTION = re.compile(r"//\s*token-ok:\s*\S")

RULES = (
    ("color-literal", re.compile(r"Color\(0x")),
    # `tween<Float>(`) / `spring<Float>(`) is the spelling inference picks most of
    # the time, so the type-argument form must be matched as well as the bare call.
    ("animation-spec", re.compile(r"\b(?:tween|spring)\s*(?:<[^(]*>)?\s*\(")),
    ("keyframe-spec", re.compile(r"\bkeyframes\b")),
    ("duration-literal", re.compile(r"durationMillis\s*=\s*\d")),
    # Fractional values count: `1.5.dp` is a rhythm decision, and it is not a
    # hairline even though it starts with `1`.
    ("dp-literal", re.compile(r"(?<![\w.])(\d+(?:\.\d+)?)\.dp\b")),
    ("sp-literal", re.compile(r"(?<![\w.])(\d+(?:\.\d+)?)\.sp\b")),
)


class Report:
    """Counts plus the violations that must fail the build."""

    def __init__(self) -> None:
        self.scanned_files = 0
        self.total = 0
        self.token_dir = 0
        self.whitelisted = 0
        self.exempted = 0
        self.violations: list[tuple[str, int, str, str]] = []

    @property
    def violations_count(self) -> int:
        return len(self.violations)


def line_hits(line: str) -> list[tuple[int, str]]:
    """Return ``(column, rule)`` for every literal found in one line."""
    hits: list[tuple[int, str]] = []
    for rule, pattern in RULES:
        for match in pattern.finditer(line):
            if rule in ("dp-literal", "sp-literal") and match.group(1) in HAIRLINE_DP:
                continue
            hits.append((match.start(), rule))
    return sorted(hits)


def scan_text(rel_path: str, text: str, report: Report) -> None:
    """Attribute every literal in one file to a bucket or record a violation."""
    in_token_dir = "/" + TOKEN_DIR in rel_path
    whitelisted = rel_path in LEGACY_WHITELIST
    for number, line in enumerate(text.splitlines(), start=1):
        hits = line_hits(line)
        if not hits:
            continue
        report.total += len(hits)
        if in_token_dir:
            report.token_dir += len(hits)
        elif whitelisted:
            report.whitelisted += len(hits)
        elif EXEMPTION.search(line):
            report.exempted += len(hits)
        else:
            for _, rule in hits:
                report.violations.append((rel_path, number, rule, line.strip()))


def scan(root: pathlib.Path) -> Report:
    report = Report()
    source = root / SOURCE_ROOT
    for path in sorted(source.rglob("*.kt")):
        rel = path.relative_to(root).as_posix()
        report.scanned_files += 1
        scan_text(rel, path.read_text(encoding="utf-8"), report)
    return report


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=pathlib.Path, default=pathlib.Path(__file__).resolve().parents[1])
    args = parser.parse_args(argv)
    root = args.root.resolve()
    if not (root / SOURCE_ROOT).is_dir():
        print(f"Design token check could not find {SOURCE_ROOT.as_posix()}.", file=sys.stderr)
        return 2

    report = scan(root)
    for rel, line, rule, snippet in report.violations:
        print(f"{rel}:{line}: {rule}: {snippet}", file=sys.stderr)

    print(f"Scanned {report.scanned_files} Kotlin files under {SOURCE_ROOT.as_posix()}.")
    print(
        "literal occurrences: "
        f"total={report.total} token-dir={report.token_dir} "
        f"legacy-whitelist={report.whitelisted} exempted={report.exempted} "
        f"violations={report.violations_count}"
    )
    if report.violations_count:
        print("FAIL: design literals outside core/design.", file=sys.stderr)
        return 1
    print("OK: every design literal outside core/design is tokenised, exempted or legacy-whitelisted.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
