#!/usr/bin/env python3
"""Reject selected signing/credential hazards in the Git index without printing secrets.

This is a regression guard, not a complete secret scanner or a history audit.
Only staged/tracked blobs are read; ignored local signing material is never opened.
"""
from __future__ import annotations

import argparse
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys

KEY_SUFFIXES = {'.jks', '.keystore', '.p12', '.pfx', '.pk8', '.key'}
SECRET_NAMES = {'signing.properties', 'keystore.properties', '.github_token', 'github_token.txt', 'local.properties'}
CONTENT_RULES = (
    ('literal-signing-password', re.compile(r'''\b(?:storePassword|keyPassword)\s*(?:=|\()\s*["'][^"'\r\n]+["']''')),
    ('private-key-content', re.compile(r'-----BEGIN (?:[A-Z]+ )*PRIVATE KEY-----')),
    ('github-token', re.compile(r'\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{40,})\b')),
)


def scan_path(path: str, data: bytes) -> list[str]:
    """Return rule identifiers only, never matched credential values."""
    item = PurePosixPath(path)
    findings = []
    if item.suffix.lower() in KEY_SUFFIXES:
        findings.append('private-key-file')
    if item.suffix.lower() in {'.apk', '.aab'}:
        findings.append('release-binary')
    name = item.name.lower()
    if (name in SECRET_NAMES or '.local' in item.parts or
            ((name == '.env' or name.startswith('.env.')) and not name.endswith('.example'))):
        findings.append('local-secret-file')
    if b'\0' in data:
        return findings
    text = data.decode('utf-8', errors='replace')
    findings.extend(name for name, pattern in CONTENT_RULES if pattern.search(text))
    return findings


def check_index(root: Path) -> int:
    try:
        result = subprocess.run(['git', '-C', str(root), 'ls-files', '--stage', '-z'],
                                check=True, capture_output=True)
        count = 0
        failures = 0
        for entry in result.stdout.split(b'\0'):
            if not entry:
                continue
            metadata, path_bytes = entry.split(b'\t', 1)
            mode, sha, stage = metadata.decode('ascii').split()
            path = path_bytes.decode('utf-8', errors='replace')
            if stage != '0':
                print(f'{path}: unresolved-index-entry', file=sys.stderr)
                return 2
            count += 1
            if mode != '100644' and mode != '100755':
                findings = ['unsupported-index-entry']
            else:
                data = subprocess.run(['git', '-C', str(root), 'cat-file', 'blob', sha],
                                      check=True, capture_output=True).stdout
                findings = scan_path(path, data)
            for rule in findings:
                print(f'{path}: {rule}', file=sys.stderr)
                failures += 1
        if count == 0:
            print('No tracked files were checked.', file=sys.stderr)
            return 2
        print(f'Checked {count} tracked files; {failures} policy violations. History was not scanned.')
        return 1 if failures else 0
    except (OSError, ValueError, subprocess.CalledProcessError):
        print('Repository security check could not read the Git index.', file=sys.stderr)
        return 2


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    sys.exit(check_index(parser.parse_args().root))
