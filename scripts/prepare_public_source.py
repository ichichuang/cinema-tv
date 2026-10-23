#!/usr/bin/env python3
"""Export a checked, attributed source snapshot for local review. Never publish or rewrite refs."""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
from pathlib import Path, PurePosixPath
import subprocess

spec = importlib.util.spec_from_file_location(
    'repository_guard', Path(__file__).with_name('check_repository_security.py'))
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)

# These exact files belong to the private incident archive, not to public source.
# Attribution, dependency inventories and disposable platform fixtures remain public.
PRIVATE_EVIDENCE = {
    'docs/evidence/actions-cache-metadata.json': 'private incident cache inventory',
    'docs/evidence/scanner-classification.json': 'classification of the private historical scan',
}


def git(root: Path, *args: str) -> bytes:
    return subprocess.run(['git', '-C', str(root), *args], check=True, capture_output=True).stdout


def prepare(root: Path, output: Path, ref: str) -> dict:
    root = Path(git(root.resolve(), 'rev-parse', '--show-toplevel').decode().strip()).resolve()
    output = output.resolve()
    if output == root or root in output.parents or output.exists():
        raise ValueError('Destination must be a new directory outside the source repository.')
    if git(root, 'status', '--porcelain', '-z', '--untracked-files=all'):
        raise ValueError('Commit and validate the intended source before exporting; worktree is dirty.')
    revision = git(root, 'rev-parse', '--verify', '--end-of-options', ref + '^{commit}').decode().strip()
    entries = []
    omitted = []
    for entry in git(root, 'ls-tree', '-rz', '--full-tree', revision).split(b'\0'):
        if not entry:
            continue
        metadata, name = entry.split(b'\t', 1)
        mode, kind, sha = metadata.decode('ascii').split()
        path = name.decode('utf-8')
        item = PurePosixPath(path)
        if (mode not in {'100644', '100755'} or kind != 'blob' or item.is_absolute()
                or '..' in item.parts or any(part.casefold() == '.git' for part in item.parts)
                or item.parts[0].casefold() == 'source_history.md'):
            raise ValueError('Unsupported entry in source snapshot.')
        data = git(root, 'cat-file', 'blob', sha)
        if path in PRIVATE_EVIDENCE:
            omitted.append({'path': path, 'reason': PRIVATE_EVIDENCE[path],
                            'sha256': hashlib.sha256(data).hexdigest()})
            continue
        rules = guard.scan_path(path, data)
        if rules:
            raise ValueError(f'Source export blocked: {path}: {", ".join(rules)}')
        entries.append((path, mode, data))
    if not entries:
        raise ValueError('No source files were selected.')
    authors = sorted(set(git(root, 'log', revision, '--format=%aN').decode('utf-8').splitlines()))
    attribution = (
        '# Source provenance\n\n'
        'Origin: ichichuang/cinema-tv.\n\n'
        'Historical Git objects and private incident evidence are intentionally absent.\n'
        'The source mapping is retained in the owner-controlled export manifest.\n\n'
        'Recorded contributors (this records attribution, not a license grant or ownership clearance):\n\n'
        + ''.join(f'- {name}\n' for name in authors)
        + '\nThis snapshot is for local review. Publication, license clearance, and signing migration remain gated.\n'
    ).encode('utf-8')
    if guard.scan_path('SOURCE_HISTORY.md', attribution):
        raise ValueError('Contributor attribution requires private review before export.')
    # Exclusive directory creation prevents overwriting an existing review or owner files.
    output.mkdir(mode=0o700)
    for path, mode, data in entries:
        target = output / path
        target.parent.mkdir(parents=True, exist_ok=True)
        with target.open('xb') as stream:
            stream.write(data)
        target.chmod(0o755 if mode == '100755' else 0o644)
        if hashlib.sha256(target.read_bytes()).digest() != hashlib.sha256(data).digest():
            raise ValueError('Export content verification failed; do not publish the partial snapshot.')
    with (output / 'SOURCE_HISTORY.md').open('xb') as stream:
        stream.write(attribution)
    return {'status': 'local-review-only', 'source_commit': revision,
            'source_files': len(entries), 'attributed_contributors': len(authors),
            'files': [{'path': path, 'mode': mode, 'sha256': hashlib.sha256(data).hexdigest()}
                      for path, mode, data in entries],
            'omitted_files': omitted,
            'historical_objects_exported': False, 'remote_changes': False}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--ref', default='HEAD')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    try:
        print(json.dumps(prepare(args.root, args.output, args.ref)))
    except (OSError, UnicodeError, ValueError, subprocess.CalledProcessError) as error:
        # Never echo subprocess stderr, credentials, or personal destination paths.
        print(str(error) if isinstance(error, ValueError) else 'Source export failed; review locally.')
        raise SystemExit(1)
