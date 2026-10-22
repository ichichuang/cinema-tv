#!/usr/bin/env python3
"""Build disposable test artifacts outside the source tree; never use a production APK/key."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import zipfile

PACKAGE = 'com.ichichuang.cinema.migrationfixture'
ROOT = Path(__file__).resolve().parents[2]


def destination(path):
    path = path.resolve()
    if path == ROOT or ROOT in path.parents or path.exists():
        raise ValueError('A new destination outside the repository is required.')
    return path


def build(sdk, output):
    output = destination(output)
    sdk = sdk.resolve()
    tools = sdk / 'build-tools/36.0.0'
    android = sdk / 'platforms/android-37.0/android.jar'
    if not android.is_file() or not all((tools / tool).is_file() for tool in ['aapt2', 'd8', 'apksigner', 'zipalign']):
        raise ValueError('Existing Android 37 platform and Build Tools 36.0.0 are required.')
    java = Path(os.environ['JAVA_HOME']) / 'bin'
    output.mkdir(mode=0o700)
    env = {k: v for k, v in os.environ.items() if not k.startswith(('SIGNING_', 'CINEMA_SIGNING_'))}
    env['MIGRATION_FIXTURE_PASSWORD'] = secrets.token_urlsafe(48)

    def run(*command):
        result = subprocess.run([str(c) for c in command], env=env, capture_output=True, text=True)
        if result.returncode:
            # No paths or passwords go to a caller/CI log.
            raise ValueError('Disposable fixture command failed: ' + Path(str(command[0])).name)
        return result.stdout + result.stderr

    keys = {}
    for label in ['a', 'b', 'c']:
        key = output / (label + '.p12')
        run(java / 'keytool', '-genkeypair', '-noprompt', '-alias', 'fixture', '-keyalg', 'RSA',
            '-keysize', '3072', '-validity', '30', '-dname', 'CN=Disposable Migration Fixture ' + label.upper(),
            '-storetype', 'PKCS12', '-keystore', key, '-storepass:env', 'MIGRATION_FIXTURE_PASSWORD',
            '-keypass:env', 'MIGRATION_FIXTURE_PASSWORD')
        key.chmod(0o600)
        keys[label] = ['--ks', key, '--ks-key-alias', 'fixture', '--ks-pass', 'env:MIGRATION_FIXTURE_PASSWORD']
    # Explicitly do not grant the old key rollback, shared-UID, permission or auth capabilities.
    for newer in ['b', 'c']:
        run(tools / 'apksigner', 'rotate', '--out', output / ('a-to-' + newer + '.lineage'),
            '--old-signer', *keys['a'], '--set-installed-data', 'true', '--set-rollback', 'false',
            '--set-shared-uid', 'false', '--set-permission', 'false', '--set-auth', 'false',
            '--new-signer', *keys[newer])
    classes = output / 'classes'
    classes.mkdir()
    run(java / 'javac', '-source', '17', '-target', '17', '-classpath', android, '-d', classes,
        Path(__file__).with_name('FixtureActivity.java'))
    dex = output / 'dex'
    dex.mkdir()
    run(tools / 'd8', '--min-api', '24', '--lib', android, '--output', dex, *sorted(classes.rglob('*.class')))
    scenarios = [('initial-a', 1001, 'a', None), ('a-to-b', 1002, 'b', 'b'),
                 ('next-b', 1003, 'b', None), ('old-a-only', 1004, 'a', None),
                 ('competing-a-to-c', 1005, 'c', 'c')]
    artifacts = []
    for name, version, signer, lineage in scenarios:
        manifest = output / 'AndroidManifest.xml'
        manifest.write_text(f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android"
 package="{PACKAGE}" android:versionCode="{version}" android:versionName="fixture-{version}">
 <uses-sdk android:minSdkVersion="24" android:targetSdkVersion="28"/>
 <application android:label="Disposable migration fixture" android:debuggable="true" android:allowBackup="false">
 <activity android:name=".FixtureActivity" android:exported="true"/>
 </application></manifest>''')
        unaligned = output / (name + '-unaligned.apk')
        run(tools / 'aapt2', 'link', '-I', android, '--manifest', manifest, '-o', unaligned)
        with zipfile.ZipFile(unaligned, 'a') as archive:
            archive.write(dex / 'classes.dex', 'classes.dex')
        unsigned = output / (name + '-unsigned.apk')
        run(tools / 'zipalign', '-f', '4', unaligned, unsigned)
        signed = output / (name + '.apk')
        signers = [*keys[signer]]
        if lineage:
            signers = [*keys['a'], '--next-signer', *keys[signer], '--lineage',
                       output / ('a-to-' + lineage + '.lineage'), '--rotation-min-sdk-version', '28']
        run(tools / 'apksigner', 'sign', *signers, '--v4-signing-enabled', 'false', '--out', signed, unsigned)
        verify = run(tools / 'apksigner', 'verify', '--min-sdk-version', '30', '--max-sdk-version', '30',
                     '--verbose', '--print-certs', signed)
        hashes = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)$', verify, re.M)
        if len(hashes) != 1:
            raise ValueError('Cannot identify the API 30 artifact signer.')
        run(tools / 'apksigner', 'verify', '--min-sdk-version', '24', '--max-sdk-version', '30', signed)
        artifacts.append(dict(file=signed.name, version=version, current_signer_api30=hashes[0],
                              sha256=hashlib.sha256(signed.read_bytes()).hexdigest(),
                              verified_api24_to30=True, native_libraries=False))
    if not (artifacts[0]['current_signer_api30'] == artifacts[3]['current_signer_api30']
            and artifacts[1]['current_signer_api30'] == artifacts[2]['current_signer_api30']
            and len({item['current_signer_api30'] for item in artifacts}) == 3):
        raise ValueError('Unexpected current artifact signer matrix.')
    report = dict(package=PACKAGE, compile_sdk=37, min_sdk=24, target_sdk=28, java_target=17,
                  production_abi='armeabi-v7a', fixture_abi='DEX only; emulator ABI independent',
                  rotation_min_sdk=28, old_key_rollback=False, artifact_evidence=artifacts,
                  platform_evidence='BLOCKED until explicitly targeted API 30 emulator run',
                  keys='disposable, outside repository; no production key used')
    (output / 'artifact-report.json').write_text(json.dumps(report, indent=2) + '\n')
    # Do not save the disposable keystore password, copy keys into Git or emit a download artifact.
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    try:
        print(json.dumps(build(args.sdk, args.output), indent=2))
    except (ValueError, OSError, KeyError):
        print('Fixture build failed; inspect tool availability locally. No production actions performed.')
        raise SystemExit(1)
