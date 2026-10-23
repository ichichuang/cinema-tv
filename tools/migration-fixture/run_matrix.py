#!/usr/bin/env python3
"""Install ONLY the disposable fixture on an explicitly selected API 30 emulator. No TV support."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import secrets
import subprocess
import time

from build_fixture import PACKAGE


SCENARIOS = {
    'migration': [0, 1, 2],
    'rotation-old-a': [0, 1, 3],
    'rotation-competing': [0, 1, 4],
    'followup-old-a': [0, 1, 2, 3],
    'followup-competing': [0, 1, 2, 4],
    'control-old-a': [0, 3],
    'control-competing': [0, 4],
}


def run(adb, serial, artifacts, *, scenario='migration'):
    if scenario not in SCENARIOS:
        raise ValueError('Unknown disposable experiment.')
    if not re.fullmatch(r'emulator-[0-9]+', serial):
        raise ValueError('Only an explicit local emulator serial is accepted.')

    def call(*arguments, allow_failure=False):
        result = subprocess.run([str(adb), '-s', serial, *arguments], capture_output=True, text=True, timeout=60)
        if result.returncode and not allow_failure:
            raise ValueError('Emulator fixture command failed: ' + arguments[0])
        return result

    def shell(*arguments):
        return call('shell', *arguments).stdout.strip()

    if call('get-state').stdout.strip() != 'device' or shell('getprop', 'ro.kernel.qemu') != '1':
        raise ValueError('Target is not a ready local emulator.')
    if shell('getprop', 'ro.build.version.sdk') != '30':
        raise ValueError('API 30 is required; another platform cannot stand in for this experiment.')
    installed = shell('pm', 'list', 'packages', '--user', '0', PACKAGE).splitlines()
    if 'package:' + PACKAGE in installed:
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
    results = dict(platform='Android 11 / API 30 emulator', serial=serial, scenario=scenario,
                   emulator_abi=shell('getprop', 'ro.product.cpu.abi'), production_abi='armeabi-v7a',
                   fingerprint=shell('getprop', 'ro.build.fingerprint'),
                   package=PACKAGE, results=[], television_evidence=False, status='IN_PROGRESS')
    output = artifacts / ('platform-report-' + scenario + '.json')

    def save():
        output.write_text(json.dumps(results, indent=2) + '\n')

    def observe():
        # This Activity finishes in onCreate: -W can wait indefinitely for a resumed Activity.
        # A nonce proves freshness without deleting observations or touching synthetic preferences.
        nonce = secrets.token_hex(16)
        call('shell', 'am', 'force-stop', PACKAGE)
        call('shell', 'am', 'start', '-n', PACKAGE + '/.FixtureActivity', '--es', 'observationNonce', nonce)
        deadline = time.monotonic() + 10
        while time.monotonic() < deadline:
            response = call('shell', 'run-as', PACKAGE, 'cat', 'files/observation.json', allow_failure=True)
            try:
                observation = json.loads(response.stdout)
                if response.returncode == 0 and observation.get('nonce') == nonce:
                    return observation
            except (ValueError, TypeError):
                pass
            time.sleep(0.1)
        raise ValueError('Fresh fixture observation did not arrive; do not reuse stale evidence.')

    baseline = None
    checkpoint = 1002 if scenario.startswith('rotation-') else 1003
    for index in SCENARIOS[scenario]:
        entry = entries[index]
        negative = index >= 3 and not scenario.startswith('control-')
        response = call('install', '-r', str(artifacts / entry['file']), allow_failure=True)
        text = response.stdout + response.stderr
        # APK path is never copied to the report. Record the actual PM result, not just process status.
        reason = re.search(r'Failure \[([^\r\n]+)\]', text)
        result = dict(file=entry['file'], version=entry['version'], exit=response.returncode,
                      package_manager_reason=reason.group(1) if reason else 'Success' if 'Success' in text else 'UNCLASSIFIED')
        results['results'].append(result)
        results['status'] = 'FAIL'
        save()
        if not negative and (response.returncode != 0 or 'Success' not in text):
            raise ValueError('Legitimate fixture update failed; inspect the saved PM reason.')
        # Observe even an unexpected acceptance before stopping, preserving the installed state.
        observation = observe()
        result['observation'] = observation
        save()
        if negative and (response.returncode == 0 or not reason
              or not reason.group(1).startswith('INSTALL_FAILED_UPDATE_INCOMPATIBLE:')):
            raise ValueError('Expected a signer-incompatibility rejection; another error is not evidence.')
        if baseline is None:
            baseline = observation
        expected_version = checkpoint if negative else entry['version']
        expected_signer = entries[1]['current_signer_api30'] if negative else entry['current_signer_api30']
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
    results['status'] = 'PASS'
    save()
    return results


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', required=True, type=Path)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--artifacts', required=True, type=Path)
    parser.add_argument('--scenario', choices=SCENARIOS, default='migration',
                        help='Each scenario requires a separate fresh disposable AVD.')
    args = parser.parse_args()
    try:
        print(json.dumps(run(args.adb, args.serial, args.artifacts, scenario=args.scenario), indent=2))
    except (ValueError, OSError, KeyError, subprocess.TimeoutExpired):
        print('Migration platform experiment stopped; inspect the local report. No production actions performed.')
        raise SystemExit(1)
