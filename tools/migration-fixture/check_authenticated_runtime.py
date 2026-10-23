#!/usr/bin/env python3
"""Test-only Android harness using existing candidate classes, disposable keys and loopback HTTPS."""
import argparse
from collections import Counter
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import secrets
import ssl
import subprocess
import threading
import time
import zipfile

from build_fixture import PACKAGE, ROOT, destination

HARNESS = 'com.ichichuang.cinema.authruntime'


def check(sdk, output, artifacts, adb, serial, avd_name):
    if not __import__('re').fullmatch(r'emulator-[0-9]+', serial):
        raise ValueError('Only an explicitly owned emulator is permitted.')
    output = destination(output)
    output.mkdir(mode=0o700)
    env = {k: v for k, v in os.environ.items() if not k.startswith(('SIGNING_', 'CINEMA_SIGNING_'))}
    java = Path(env['JAVA_HOME']) / 'bin'
    tools = sdk / 'build-tools/36.0.0'
    android = sdk / 'platforms/android-37.0/android.jar'
    log = output / 'commands.log'

    def command(*args, input=None, allow_failure=False):
        result = subprocess.run([str(a) for a in args], input=input, env=env, capture_output=True, timeout=180)
        with log.open('ab') as stream:
            stream.write(result.stdout + result.stderr)
        if result.returncode and not allow_failure:
            raise ValueError('Runtime check command failed: ' + Path(str(args[0])).name)
        return result

    def device(*args, **kwargs):
        return command(adb, '-s', serial, *args, **kwargs)

    for prop, expected in [('ro.kernel.qemu', '1'), ('ro.build.version.sdk', '30'),
                           ('ro.product.cpu.abi', 'arm64-v8a')]:
        if device('shell', 'getprop', prop).stdout.decode().strip() != expected:
            raise ValueError('Runtime harness requires the explicitly selected API 30 ARM64 emulator.')
    if device('emu', 'avd', 'name').stdout.decode().splitlines()[0] != avd_name:
        raise ValueError('Owned AVD identity mismatch.')
    for package in [PACKAGE, HARNESS]:
        if ('package:' + package) in device('shell', 'pm', 'list', 'packages', '--user', '0', package).stdout.decode().splitlines():
            raise ValueError('Use a fresh disposable AVD; no uninstall or data reset is permitted.')
    evidence = json.loads((artifacts / 'artifact-report.json').read_text())
    if evidence['package'] != PACKAGE:
        raise ValueError('Wrong fixture package.')
    for entry in evidence['artifact_evidence']:
        if hashlib.sha256((artifacts / entry['file']).read_bytes()).hexdigest() != entry['sha256']:
            raise ValueError('Fixture artifact changed.')

    # Reuse the actual app compiler output and stdlib; no substitute implementation or new dependency.
    compiled = ROOT / 'app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes'
    base = compiled / 'com/ichichuang/cinema'
    selected = sorted((base / 'data/update/auth').glob('*.class')) + [
        base / 'data/update/UpdatePackageIdentity.class', base / 'data/update/UpdateDownloadFiles.class',
        base / 'data/remote/SourceException.class',
    ]
    if not selected or not all(p.is_file() for p in selected):
        raise ValueError('Run the existing :app:compileDebugKotlin task first.')
    gradle = Path(env.get('GRADLE_USER_HOME', str(Path.home() / '.gradle')))
    stdlib, = (gradle / 'caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.4.20').glob('*/kotlin-stdlib-2.4.20.jar')
    candidate = output / 'candidate.jar'
    with zipfile.ZipFile(candidate, 'w') as archive:
        for file in selected:
            archive.write(file, file.relative_to(compiled))
    provenance = {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in selected}
    for file in [Path(__file__), Path(__file__).with_name('AuthenticatedRuntimeActivity.java')]:
        provenance[str(file.relative_to(ROOT))] = hashlib.sha256(file.read_bytes()).hexdigest()
    for file in (ROOT / 'app/src/main/kotlin/com/ichichuang/cinema/data/update/auth').glob('*.kt'):
        provenance[str(file.relative_to(ROOT))] = hashlib.sha256(file.read_bytes()).hexdigest()
    (output / 'candidate-hashes.json').write_text(json.dumps(provenance, indent=2) + '\n')
    assets = output / 'assets'
    assets.mkdir(mode=0o700)
    # Disposable TLS identity, trusted only by this test APK; hostname verification stays enabled.
    command('openssl', 'req', '-x509', '-newkey', 'rsa:3072', '-noenc', '-keyout', output / 'server.key',
            '-out', assets / 'server.crt', '-days', '1', '-subj', '/CN=localhost',
            '-addext', 'subjectAltName=DNS:localhost,IP:127.0.0.1')
    (output / 'server.key').chmod(0o600)
    classes = output / 'classes'
    classes.mkdir()
    command(java / 'javac', '-source', '17', '-target', '17', '-classpath',
            os.pathsep.join(map(str, [android, candidate, stdlib])), '-d', classes,
            Path(__file__).with_name('AuthenticatedRuntimeActivity.java'))
    dex = output / 'dex'
    dex.mkdir()
    command(tools / 'd8', '--min-api', '30', '--lib', android, '--output', dex,
            candidate, stdlib, *sorted(classes.rglob('*.class')))
    manifest = output / 'AndroidManifest.xml'
    manifest.write_text(f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{HARNESS}"
 android:versionCode="1" android:versionName="test-only">
 <uses-sdk android:minSdkVersion="30" android:targetSdkVersion="28"/>
 <uses-permission android:name="android.permission.INTERNET"/>
 <queries><package android:name="{PACKAGE}"/></queries>
 <application android:debuggable="true" android:allowBackup="false" android:label="Isolated auth runtime">
 <activity android:name=".AuthenticatedRuntimeActivity" android:exported="true"/>
 </application></manifest>''')
    unsigned = output / 'unsigned.apk'
    command(tools / 'aapt2', 'link', '-I', android, '--manifest', manifest, '-A', assets, '-o', unsigned)
    with zipfile.ZipFile(unsigned, 'a') as archive:
        for file in dex.glob('*.dex'):
            archive.write(file, file.name)
    aligned = output / 'aligned.apk'
    command(tools / 'zipalign', '4', unsigned, aligned)
    env['RUNTIME_TEST_PASSWORD'] = secrets.token_urlsafe(48)
    key = output / 'test.p12'
    command(java / 'keytool', '-genkeypair', '-noprompt', '-alias', 'test', '-keyalg', 'RSA', '-keysize', '3072',
            '-validity', '2', '-dname', 'CN=Disposable Android Auth Harness', '-storetype', 'PKCS12',
            '-keystore', key, '-storepass:env', 'RUNTIME_TEST_PASSWORD', '-keypass:env', 'RUNTIME_TEST_PASSWORD')
    key.chmod(0o600)
    apk = output / 'auth-runtime.apk'
    command(tools / 'apksigner', 'sign', '--ks', key, '--ks-pass', 'env:RUNTIME_TEST_PASSWORD',
            '--v4-signing-enabled', 'false', '--out', apk, aligned)
    command(tools / 'apksigner', 'verify', '--min-sdk-version', '30', '--max-sdk-version', '30', apk)

    counts = Counter()
    state = dict(envelope=b'')
    fixture = (artifacts / 'a-to-b.apk').read_bytes()

    class Endpoint(BaseHTTPRequestHandler):
        protocol_version = 'HTTP/1.0'

        def log_message(self, *_):
            pass  # Never log request bodies, keys or user configuration.

        def do_POST(self):
            counts[self.path + ':POST'] += 1
            size = int(self.headers.get('Content-Length', '-1'))
            if self.path != '/envelope' or not 0 < size <= 16384:
                self.send_error(400)
                return
            state['envelope'] = self.rfile.read(size)
            self.send_response(204)
            self.end_headers()

        def do_GET(self):
            counts[self.path] += 1
            body = {'/envelope': state['envelope'], '/tampered': state['envelope'][:-1] +
                    bytes([state['envelope'][-1] ^ 1]) if state['envelope'] else b'',
                    '/oversized': b'x' * 16385, '/oversized-stream': b'x' * 16385,
                    '/fixture.apk': fixture, '/fixture.apk?inspect': fixture}.get(self.path)
            if body is None:
                self.send_error(404)
                return
            self.send_response(200)
            if self.path != '/oversized-stream':
                self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            if self.path == '/fixture.apk' and counts[self.path] == 1:
                body = body[:len(body) // 2]  # Controlled interruption, never disk exhaustion.
            try:
                self.wfile.write(body)
            except (BrokenPipeError, ConnectionResetError, ssl.SSLError):
                pass

    server = ThreadingHTTPServer(('127.0.0.1', 9443), Endpoint)
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.load_cert_chain(assets / 'server.crt', output / 'server.key')
    server.socket = context.wrap_socket(server.socket, server_side=True)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    reports = []
    try:
        device('reverse', 'tcp:9443', 'tcp:9443')
        for file in [artifacts / 'initial-a.apk', apk]:
            result = device('install', str(file))
            if b'Success' not in result.stdout:
                raise ValueError('PackageManager did not establish a successful test install.')
        for phase in ['prepare', 'restart']:
            device('shell', 'am', 'force-stop', HARNESS)
            device('shell', 'am', 'start', '-n', HARNESS + '/.AuthenticatedRuntimeActivity', '--es', 'phase', phase)
            for _ in range(90):
                result = device('shell', 'run-as', HARNESS, 'cat', 'files/auth-' + phase + '.json', allow_failure=True)
                if result.returncode == 0:
                    report = json.loads(result.stdout)
                    reports.append(report)
                    (output / ('auth-' + phase + '.json')).write_text(json.dumps(report, indent=2) + '\n')
                    if report['status'] != 'PASS':
                        raise ValueError('Android runtime assertion failed; inspect the phase report.')
                    break
                time.sleep(1)
            else:
                raise ValueError('Runtime phase did not produce evidence within the bounded wait.')
        if reports[0]['pid'] == reports[1]['pid'] or counts['/fixture.apk'] != 3:
            raise ValueError('Expected actual process restart and controlled retry/negative download.')
        if any('legacy' in path for path in counts):
            raise ValueError('Authentication failure reached a legacy endpoint.')
        result = dict(status='PASS', runtime_checks=sum(len(r['checks']) for r in reports), reports=reports,
                      endpoints=dict(counts), candidate_classes_sha256=hashlib.sha256(candidate.read_bytes()).hexdigest(),
                      serial=serial, avd_name=avd_name, production_protection=False,
                      fingerprint=device('shell', 'getprop', 'ro.build.fingerprint').stdout.decode().strip())
        (output / 'auth-runtime-report.json').write_text(json.dumps(result, indent=2) + '\n')
        return result
    finally:
        device('reverse', '--remove', 'tcp:9443', allow_failure=True)
        server.shutdown()
        server.server_close()


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ['sdk', 'output', 'artifacts', 'adb']:
        parser.add_argument('--' + name, required=True, type=Path)
    for name in ['serial', 'avd-name']:
        parser.add_argument('--' + name, required=True)
    args = parser.parse_args()
    try:
        result = check(args.sdk.resolve(), args.output, args.artifacts.resolve(), args.adb.resolve(), args.serial, args.avd_name)
        print('Android authentication runtime checks:', result['runtime_checks'], result['status'])
    except (ValueError, OSError, KeyError, subprocess.TimeoutExpired):
        print('Android authentication experiment stopped; inspect task-local reports. Production remains disabled.')
        raise SystemExit(1)
