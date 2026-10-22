#!/usr/bin/env python3
"""Install ONLY the disposable fixture on an explicitly selected API 30 emulator. No TV support."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

from build_fixture import PACKAGE


def run(adb, serial, artifacts):
    if not re.fullmatch(r'emulator-[0-9]+', serial):
        raise ValueError('Only an explicit local emulator serial is accepted.')

    def call(*arguments, allow_failure=False):
        result = subprocess.run([str(adb), '-s', serial, *arguments], capture_output=True, text=True)
        if result.returncode and not allow_failure:
            raise ValueError('Emulator fixture command failed: ' + arguments[0])
        return result

    def shell(*arguments):
        return call('shell', *arguments).stdout.strip()

    if call('get-state').stdout.strip() != 'device' or shell('getprop', 'ro.kernel.qemu') != '1':
        raise ValueError('Target is not a ready local emulator.')
    if shell('getprop', 'ro.build.version.sdk') != '30':
        raise ValueError('API 30 is required; another platform cannot stand in for this experiment.')
    if shell('pm', 'path', PACKAGE):
        raise ValueError('Fixture already exists. Use a fresh explicitly selected emulator; do not clear data.')
    report = json.loads((artifacts / 'artifact-report.json').read_text())
    if report['package'] != PACKAGE:
        raise ValueError('Wrong fixture identity.')
    expected = [('initial-a.apk', 1001), ('a-to-b.apk', 1002), ('next-b.apk', 1003),
                ('old-a-only.apk', 1004), ('competing-a-to-c.apk', 1005)]
    entries = report['artifact_evidence']
    if [(e['file'], e['version']) for e in entries] != expected:
        raise ValueError('Unexpected fixture matrix.')
    for entry in entries:
        if hashlib.sha256((artifacts / entry['file']).read_bytes()).hexdigest() != entry['sha256']:
            raise ValueError('Artifact differs from the verified fixture report.')
    results = dict(platform='Android 11 / API 30 emulator',
                   emulator_abi=shell('getprop', 'ro.product.cpu.abi'), production_abi='armeabi-v7a',
                   package=PACKAGE, results=[], television_evidence=False)
    output = artifacts / 'platform-report.json'

    def save():
        output.write_text(json.dumps(results, indent=2) + '\n')

    def observe():
        call('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.FixtureActivity')
        return json.loads(shell('run-as', PACKAGE, 'cat', 'files/observation.json'))

    baseline = None
    for index, entry in enumerate(entries):
        response = call('install', '-r', str(artifacts / entry['file']), allow_failure=True)
        text = response.stdout + response.stderr
        # APK path is never copied to the report. Record the actual PM result, not just process status.
        reason = re.search(r'Failure \[([^\r\n]+)\]', text)
        result = dict(file=entry['file'], version=entry['version'], exit=response.returncode,
                      package_manager_reason=reason.group(1) if reason else 'Success' if 'Success' in text else 'UNCLASSIFIED')
        results['results'].append(result)
        save()
        if index < 3:
            if response.returncode != 0 or 'Success' not in text:
                raise ValueError('Legitimate fixture update failed; inspect the saved PM reason.')
        elif not reason or not reason.group(1).startswith('INSTALL_FAILED_UPDATE_INCOMPATIBLE'):
            raise ValueError('Expected a signer-incompatibility rejection; another error is not evidence.')
        observation = observe()
        result['observation'] = observation
        if baseline is None:
            baseline = observation
        expected_version = entry['version'] if index < 3 else 1003
        expected_signer = entries[0 if index == 0 else 1]['current_signer_api30']
        if (observation['uid'] != baseline['uid'] or observation['marker'] != 'migration-synthetic-data'
                or observation['progress'] != 12345 or observation['version'] != expected_version
                or observation['current'] != [expected_signer]):
            save()
            raise ValueError('UID, synthetic data, version or current signer postcondition failed.')
        if index == 1 and set(observation['history']) != {
                entries[0]['current_signer_api30'], entries[1]['current_signer_api30']}:
            raise ValueError('Expected A/B historical certificates separately from current signer B.')
        result['postconditions_verified'] = True
        save()
    return results


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', required=True, type=Path)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--artifacts', required=True, type=Path)
    args = parser.parse_args()
    try:
        print(json.dumps(run(args.adb, args.serial, args.artifacts), indent=2))
    except (ValueError, OSError, KeyError):
        print('Migration platform experiment stopped; inspect the local report. No production actions performed.')
        raise SystemExit(1)
