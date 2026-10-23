#!/usr/bin/env python3
"""Offline APK/manifest/AUTHUPD1 preflight. This tool cannot sign, publish or activate production.

Trust inputs must be obtained independently: complete APK signer fingerprints, publisher
SPKI DER SHA-256/key ID, and the previously approved versionCode and publisher sequence.
The public PEM is an input, never a trust root learned from update metadata or a network.
"""
from __future__ import annotations

import argparse
from contextlib import nullcontext
import hashlib
import json
from pathlib import Path
import re
import struct
import subprocess
import sys
import tempfile
import time


PUBLISHING_FROZEN = True
PACKAGE = 'com.ichichuang.cinema'
COMPROMISED_SIGNER = '5c3ae14663ca57023e33e9775255aa2906c712e849fa0c542f799bd4edfa6abd'
HEX = re.compile(r'[0-9a-f]{64}')
FIELDS = ('key', 'package', 'sequence', 'version', 'size', 'sha256', 'signers',
          'min-sdk', 'max-sdk', 'abis', 'not-before', 'expires', 'url')


def require(condition: bool, reason: str) -> None:
    if not condition:
        raise ValueError(reason)


def integer(value, minimum: int, maximum: int, reason: str) -> int:
    require(type(value) is int and minimum <= value <= maximum, reason)
    return value


def fingerprints(values) -> list[str]:
    require(isinstance(values, (list, tuple)) and 1 <= len(values) <= 8,
            'A complete independently approved signer set is required.')
    require(all(isinstance(value, str) and HEX.fullmatch(value) for value in values)
            and len(set(values)) == len(values), 'Invalid or duplicate signer fingerprint.')
    require(COMPROMISED_SIGNER not in values, 'The compromised APK signing identity is forbidden.')
    return sorted(values)


def read_bounded(path: Path, maximum: int) -> bytes:
    try:
        with Path(path).open('rb') as stream:
            data = stream.read(maximum + 1)
        require(len(data) <= maximum, 'Input exceeds its size limit.')
        return data
    except OSError:
        raise ValueError('Required local input is unavailable.') from None


def run(tool, *arguments) -> bytes:
    try:
        return subprocess.run([str(tool), *map(str, arguments)], check=True, capture_output=True,
                              timeout=60).stdout
    except (OSError, subprocess.SubprocessError):
        # Never emit tool stderr, input contents, credentials or local paths.
        raise ValueError('Required local verification tool failed.') from None


def parse_envelope(wire: bytes) -> tuple[bytes, bytes, dict]:
    require(14 <= len(wire) <= 16384 and wire[:8] == b'AUTHUPD1', 'Invalid AUTHUPD1 envelope.')
    length = struct.unpack('>I', wire[8:12])[0]
    require(1 <= length <= 8192 and len(wire) >= 14 + length, 'Invalid payload size.')
    signature_length = struct.unpack('>H', wire[12 + length:14 + length])[0]
    require(384 <= signature_length <= 512 and len(wire) == 14 + length + signature_length,
            'Invalid signature length or trailing envelope data.')
    payload = wire[12:12 + length]
    require(all(value == 10 or 32 <= value <= 126 for value in payload), 'Noncanonical payload bytes.')
    lines = payload.decode('ascii').split('\n')
    require(len(lines) == len(FIELDS) + 2 and lines[0] == 'cinema-update-auth/1' and lines[-1] == '',
            'Invalid payload framing.')
    values = {}
    for key, line in zip(FIELDS, lines[1:-1]):
        require(line.startswith(key + '='), 'Unknown, duplicate or unordered payload field.')
        values[key] = line[len(key) + 1:]
    for key in ('sequence', 'version', 'size', 'min-sdk', 'max-sdk', 'not-before', 'expires'):
        require(re.fullmatch(r'0|[1-9][0-9]{0,18}', values[key]) is not None,
                'Noncanonical numeric payload field.')
        values[key] = int(values[key])
        integer(values[key], 0, 2**63 - 1, 'Payload integer overflow.')
    require(re.fullmatch(r'[A-Za-z0-9_-]{1,64}', values['key']) is not None, 'Invalid publisher key ID.')
    require(values['package'] == PACKAGE, 'Wrong authenticated package identity.')
    integer(values['sequence'], 1, 2**63 - 1, 'Invalid publisher sequence.')
    integer(values['version'], 1, 2**31 - 1, 'Invalid authenticated version.')
    integer(values['size'], 1, 536870912, 'Invalid authenticated artifact size.')
    require(HEX.fullmatch(values['sha256']) is not None, 'Invalid authenticated SHA-256.')
    signers = values['signers'].split(',')
    require(signers == fingerprints(signers), 'Noncanonical authenticated signer set.')
    values['signers'] = signers
    minimum, maximum = values['min-sdk'], values['max-sdk']
    require(24 <= minimum <= 30 <= maximum <= 100, 'Authenticated release does not cover API 30.')
    abis = values['abis'].split(',')
    require(1 <= len(abis) <= 8 and abis == sorted(set(abis))
            and set(abis) <= {'armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64'}, 'Invalid ABI set.')
    start, end = values['not-before'], values['expires']
    require(start >= 1704067200 and 0 < end - start <= 31 * 86400,
            'Invalid authenticated validity interval.')
    require(start <= int(time.time()) <= end, 'Authenticated release is expired or not yet valid.')
    return payload, wire[14 + length:], values


def authenticate(wire: bytes, public_key: Path, expected_sha256: str, openssl) -> dict:
    require(isinstance(expected_sha256, str) and HEX.fullmatch(expected_sha256) is not None,
            'An independently approved SPKI DER SHA-256 is required.')
    payload, signature, values = parse_envelope(wire)
    pem = read_bounded(public_key, 16384)
    require(re.fullmatch(rb'-----BEGIN PUBLIC KEY-----\s+[A-Za-z0-9+/=\r\n]+-----END PUBLIC KEY-----\s*', pem)
            is not None, 'A single public SPKI PEM is required; private material is not accepted.')
    # Snapshot the public input so a later file replacement cannot change the pinned verifier key.
    with tempfile.TemporaryDirectory(prefix='cinema-preflight-') as temporary:
        directory = Path(temporary)
        key, source, signed = (directory / name for name in ('publisher.pem', 'payload', 'signature'))
        key.write_bytes(pem)
        source.write_bytes(payload)
        signed.write_bytes(signature)
        der = run(openssl, 'pkey', '-pubin', '-in', key, '-outform', 'DER')
        require(hashlib.sha256(der).hexdigest() == expected_sha256, 'Publisher public-key pin mismatch.')
        description = run(openssl, 'rsa', '-pubin', '-in', key, '-text', '-noout').decode('ascii')
        bits = re.search(r'Public-Key:\s*\((\d+) bit\)', description)
        exponent = re.search(r'Exponent:\s*(\d+)\b', description)
        require(bits is not None and 3072 <= int(bits[1]) <= 4096
                and exponent is not None and exponent[1] == '65537', 'Unsupported publisher RSA key.')
        run(openssl, 'dgst', '-sha256', '-verify', key, '-signature', signed, source)
    return values


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, 'Duplicate manifest field.')
        result[key] = value
    return result


def artifact_digest(apk: Path, size: int, snapshot: Path | None = None) -> str:
    digest = hashlib.sha256()
    length = 0
    try:
        with Path(apk).open('rb') as stream, (snapshot.open('xb') if snapshot else nullcontext()) as target:
            if snapshot:
                snapshot.chmod(0o600)
            while chunk := stream.read(65536):
                length += len(chunk)
                require(length <= size, 'APK exceeds the declared exact size.')
                digest.update(chunk)
                if target is not None:
                    target.write(chunk)
    except OSError:
        raise ValueError('Required local APK is unavailable.') from None
    require(length == size, 'APK does not match the declared exact size.')
    return digest.hexdigest()


def verify_candidate(*, apk, manifest, previous_version_code, expected_signers, aapt2, apksigner,
                     publisher_envelope, publisher_public_key, publisher_public_key_sha256,
                     publisher_key_id, previous_sequence, openssl='openssl') -> dict:
    previous = integer(previous_version_code, 0, 2**31 - 1, 'Invalid independently approved previous versionCode.')
    floor = integer(previous_sequence, 0, 2**63 - 1, 'Invalid independently approved previous sequence.')
    expected = fingerprints(expected_signers)
    try:
        metadata = json.loads(read_bounded(manifest, 65536), object_pairs_hook=unique_object)
    except (UnicodeError, json.JSONDecodeError):
        raise ValueError('Invalid version manifest.') from None
    require(isinstance(metadata, dict), 'Version manifest must be an object.')
    version = integer(metadata.get('versionCode'), previous + 1, 2**31 - 1, 'VersionCode must strictly increase.')
    size = integer(metadata.get('sizeBytes'), 1, 536870912, 'Invalid manifest sizeBytes.')
    sequence = integer(metadata.get('sequence'), floor + 1, 2**63 - 1, 'Publisher sequence must strictly increase.')
    name, digest = metadata.get('versionName'), metadata.get('sha256')
    require(isinstance(name, str) and re.fullmatch(r'(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)', name)
            is not None, 'VersionName must be a canonical X.Y.Z release version.')
    require(isinstance(digest, str) and HEX.fullmatch(digest) is not None, 'An exact manifest SHA-256 is required.')
    url = f'https://github.com/ichichuang/cinema-tv/releases/download/v{name}/cinema-tv-{name}.apk'
    download_fields = ('apkUrl', 'apk_url', 'url', 'download_url', 'mirrorUrl', 'mirror_url')
    require(metadata.get('apkUrl') == url
            and all(metadata[field] == url for field in download_fields if field in metadata),
            'Only the immutable versioned cinema-tv GitHub Releases asset URL is accepted.')
    require(metadata.get('publisherKeyId') == publisher_key_id, 'Independent publisher key ID mismatch.')
    require(metadata.get('signers') == expected, 'Manifest signer set differs from independent approval.')
    values = authenticate(read_bounded(publisher_envelope, 16384), publisher_public_key,
                          publisher_public_key_sha256, openssl)
    for field, value in {'key': publisher_key_id, 'version': version, 'size': size, 'sha256': digest,
                         'url': url, 'signers': expected, 'sequence': sequence}.items():
        require(values[field] == value, 'Authenticated publisher payload does not match release metadata.')
    # Both Android tools read one checked byte snapshot, not a mutable caller path.
    # This binds identity/signature evidence without pretending to constrain malicious local tools.
    with tempfile.TemporaryDirectory(prefix='cinema-apk-preflight-') as temporary:
        snapshot = Path(temporary) / 'candidate.apk'
        require(artifact_digest(apk, size, snapshot) == digest, 'APK SHA-256 mismatch.')
        badging = run(aapt2, 'dump', 'badging', snapshot).decode('utf-8')
        package_lines = re.findall(r'^package: (.*)$', badging, re.M)
        require(len(package_lines) == 1, 'Cannot identify exactly one APK package.')
        identity = unique_object(re.findall(r"([A-Za-z]+)='([^']*)'", package_lines[0]))
        require(identity.get('name') == PACKAGE and identity.get('versionCode') == str(version)
                and identity.get('versionName') == name and identity.get('versionCodeMajor', '0') == '0',
                'Actual APK package/version differs from the approved release.')
        verification = run(apksigner, 'verify', '--min-sdk-version', '30', '--max-sdk-version', '30',
                           '--verbose', '--print-certs', snapshot).decode('utf-8')
        certs = re.findall(r'^Signer #(\d+) certificate SHA-256 digest: ([0-9a-f]{64})$', verification, re.M)
        counts = re.findall(r'^Number of signers: (\d+)$', verification, re.M)
        require(len(counts) == 1 and int(counts[0]) == len(certs)
                and [int(index) for index, _ in certs] == list(range(1, len(certs) + 1)),
                'Cannot identify the complete API 30 current signer set.')
        actual = fingerprints([value for _, value in certs])
        require(actual == expected, 'APK current signers differ from independent approval.')
    require(artifact_digest(apk, size) == digest, 'APK changed during verification.')
    require(values['not-before'] <= int(time.time()) <= values['expires'],
            'Authenticated release expired during local verification.')
    return {'status': 'offline-preflight-verified', 'publisherAuthentication': True,
            'publisherPublicKeySha256': publisher_public_key_sha256, 'publisherKeyId': publisher_key_id,
            'package': PACKAGE, 'versionCode': version, 'versionName': name, 'tag': 'v' + name,
            'sizeBytes': size, 'sha256': digest, 'apkUrl': url, 'sequence': sequence, 'signers': actual,
            'platformValidation': 'API30-tools-only', 'publishingFrozen': PUBLISHING_FROZEN,
            'publicationAuthorized': False, 'networkAccess': False}


def main(argv=None) -> int:
    arguments = list(sys.argv[1:] if argv is None else argv)
    # Must run before parsing inputs, reading files, invoking tools or examining credentials.
    if '--check-publishing-gate' in arguments:
        print(json.dumps({'publishingFrozen': PUBLISHING_FROZEN, 'publicationAuthorized': False,
                          'reason': 'publishing-frozen'}))
        return 1
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check-publishing-gate', action='store_true', help='Always fails while publication is frozen.')
    for name in ('apk', 'manifest', 'aapt2', 'apksigner', 'publisher-envelope', 'publisher-public-key'):
        parser.add_argument('--' + name, type=Path, required=True)
    for name in ('previous-version-code', 'previous-sequence'):
        parser.add_argument('--' + name, type=int, required=True)
    parser.add_argument('--expected-signer', action='append', required=True, dest='expected_signers',
                        help='Independent lowercase certificate SHA-256; repeat for every current signer.')
    parser.add_argument('--publisher-public-key-sha256', required=True, help='Independent SHA-256 of SPKI DER.')
    parser.add_argument('--publisher-key-id', required=True)
    parser.add_argument('--openssl', default='openssl')
    options = vars(parser.parse_args(arguments))
    options.pop('check_publishing_gate')
    try:
        print(json.dumps(verify_candidate(**options), sort_keys=True))
        return 0
    except (ValueError, UnicodeError):
        print(json.dumps({'status': 'rejected', 'publisherAuthentication': False,
                          'publishingFrozen': PUBLISHING_FROZEN, 'publicationAuthorized': False}))
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
