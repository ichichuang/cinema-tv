"""Offline preflight contracts; Android tool boundaries use explicit synthetic output.

RSA keys and all generated inputs live in a temporary directory outside the repository.
Actual Android tool integration is a separate local check, not an implicit device test.
"""
import contextlib
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import tempfile
import time
import unittest
from unittest import mock


SCRIPT = Path(__file__).resolve().parents[1] / 'verify_release_candidate.py'
COMPROMISED = '5c3ae14663ca57023e33e9775255aa2906c712e849fa0c542f799bd4edfa6abd'
SIGNER = 'a' * 64
URL = 'https://github.com/ichichuang/cinema-tv/releases/download/v0.9.9/cinema-tv-0.9.9.apk'


class ReleaseCandidateTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workspace = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.workspace.cleanup)
        cls.base = Path(cls.workspace.name)
        cls.openssl = shutil.which('openssl')
        if not cls.openssl:
            raise RuntimeError('Existing OpenSSL is required for real publisher-signature tests.')
        cls.private = cls.base / 'disposable.pem'
        cls.public = cls.base / 'publisher.pem'
        cls.command(cls.openssl, 'genpkey', '-algorithm', 'RSA', '-pkeyopt', 'rsa_keygen_bits:3072',
                    '-out', cls.private)
        cls.private.chmod(0o600)
        cls.command(cls.openssl, 'pkey', '-in', cls.private, '-pubout', '-out', cls.public)
        der = cls.command(cls.openssl, 'pkey', '-pubin', '-in', cls.public, '-outform', 'DER')
        cls.root_hash = hashlib.sha256(der).hexdigest()

    @staticmethod
    def command(*arguments):
        return subprocess.run([str(value) for value in arguments], check=True,
                              capture_output=True, timeout=60).stdout

    def setUp(self):
        self.assertTrue(SCRIPT.is_file(), 'Missing offline release candidate preflight.')
        spec = importlib.util.spec_from_file_location('release_candidate', SCRIPT)
        self.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.module)
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.apk = self.root / 'candidate.apk'
        self.apk.write_bytes(b'disposable synthetic package bytes')
        self.manifest = self.root / 'version.json'
        self.envelope = self.root / 'publisher.envelope'
        self.aapt2 = self.root / 'aapt2'
        self.apksigner = self.root / 'apksigner'
        self.set_android_outputs()
        self.metadata = {
            'versionCode': 19, 'versionName': '0.9.9', 'apkUrl': URL,
            'sizeBytes': self.apk.stat().st_size, 'sha256': hashlib.sha256(self.apk.read_bytes()).hexdigest(),
            'sequence': 7, 'publisherKeyId': 'owner-release', 'signers': [SIGNER],
        }
        now = int(time.time())
        self.payload = {
            'key': 'owner-release', 'package': 'com.ichichuang.cinema', 'sequence': '7',
            'version': '19', 'size': str(self.metadata['sizeBytes']), 'sha256': self.metadata['sha256'],
            'signers': SIGNER, 'min-sdk': '24', 'max-sdk': '30', 'abis': 'armeabi-v7a',
            'not-before': str(now - 60), 'expires': str(now + 3600), 'url': URL,
        }
        self.write_inputs()

    def tool(self, path, arguments, output, exit_code=0):
        path.write_text('#!' + sys.executable + '\nimport sys\n'
                        'from pathlib import Path\n'
                        'if sys.argv[1:-1] != ' + repr(arguments[:-1])
                        + ' or not Path(sys.argv[-1]).is_file(): raise SystemExit(95)\n'
                        'sys.stdout.write(' + repr(output) + ')\nraise SystemExit(' + str(exit_code) + ')\n')
        path.chmod(0o700)

    def set_android_outputs(self, package='com.ichichuang.cinema', version='19', name='0.9.9',
                            signers=(SIGNER,), verify_exit=0):
        self.tool(self.aapt2, ['dump', 'badging', str(self.apk)],
                  "package: name='" + package + "' versionCode='" + version + "' versionName='" + name + "'\n"
                  "sdkVersion:'24'\ntargetSdkVersion:'28'\n")
        output = 'Verifies\nNumber of signers: ' + str(len(signers)) + '\n'
        output += ''.join('Signer #' + str(i) + ' certificate SHA-256 digest: ' + signer + '\n'
                          for i, signer in enumerate(signers, 1))
        self.tool(self.apksigner, ['verify', '--min-sdk-version', '30', '--max-sdk-version', '30',
                                 '--verbose', '--print-certs', str(self.apk)], output, verify_exit)

    def write_inputs(self, payload_bytes=None):
        self.manifest.write_text(json.dumps(self.metadata))
        payload = payload_bytes if payload_bytes is not None else (
            'cinema-update-auth/1\n' + ''.join(key + '=' + value + '\n' for key, value in self.payload.items())
        ).encode('ascii')
        source = self.root / 'payload'
        source.write_bytes(payload)
        signature = self.command(self.openssl, 'dgst', '-sha256', '-sign', self.private, source)
        self.envelope.write_bytes(b'AUTHUPD1' + struct.pack('>I', len(payload)) + payload
                                  + struct.pack('>H', len(signature)) + signature)

    def verify(self, **overrides):
        options = dict(apk=self.apk, manifest=self.manifest, previous_version_code=18,
                       expected_signers=[SIGNER], aapt2=self.aapt2, apksigner=self.apksigner,
                       publisher_envelope=self.envelope, publisher_public_key=self.public,
                       publisher_public_key_sha256=self.root_hash, publisher_key_id='owner-release',
                       previous_sequence=6, openssl=self.openssl)
        options.update(overrides)
        return self.module.verify_candidate(**options)

    def test_publishing_gate_fails_before_reading_inputs_or_running_tools(self):
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            code = self.module.main(['--check-publishing-gate', '--apk', str(self.root / 'missing')])
        self.assertNotEqual(0, code)
        self.assertTrue(json.loads(output.getvalue())['publishingFrozen'])
        self.assertEqual('publishing-frozen', json.loads(output.getvalue())['reason'])

    def test_verified_candidate_has_no_publication_authority(self):
        result = self.verify()
        self.assertTrue(result['publisherAuthentication'])
        self.assertTrue(result['publishingFrozen'])
        self.assertFalse(result['publicationAuthorized'])
        self.assertEqual([SIGNER], result['signers'])
        self.assertEqual('API30-tools-only', result['platformValidation'])

    def test_exact_artifact_size_is_required(self):
        self.metadata['sizeBytes'] += 1
        self.write_inputs()
        with self.assertRaises(ValueError):
            self.verify()

    def test_exact_artifact_digest_is_required(self):
        self.apk.write_bytes(b'x' * self.metadata['sizeBytes'])
        with self.assertRaises(ValueError):
            self.verify()

    def test_package_and_signature_evidence_cannot_come_from_a_replaced_path(self):
        # A is the authenticated byte stream, but it has the wrong identity/signature.
        # An external writer swaps in B only while the Android tools open the path.
        original, replacement = self.apk.read_bytes(), b'temporary other artifact'
        original_run = self.module.run

        def observed_tool(tool, *arguments):
            if Path(tool) == self.aapt2:
                self.apk.write_bytes(replacement)
                if Path(arguments[-1]).read_bytes() == replacement:
                    return b"package: name='com.ichichuang.cinema' versionCode='19' versionName='0.9.9'\n"
                return b"package: name='wrong.package' versionCode='19' versionName='0.9.9'\n"
            if Path(tool) == self.apksigner:
                observed = Path(arguments[-1]).read_bytes()
                self.apk.write_bytes(original)
                if observed != replacement:
                    raise ValueError('The authenticated A bytes are not correctly signed.')
                return ('Number of signers: 1\nSigner #1 certificate SHA-256 digest: ' + SIGNER + '\n').encode()
            return original_run(tool, *arguments)

        try:
            with mock.patch.object(self.module, 'run', side_effect=observed_tool):
                with self.assertRaises(ValueError):
                    self.verify()
        finally:
            self.apk.write_bytes(original)

    def test_release_url_is_fixed_to_the_single_repository_and_version(self):
        for url in [URL.replace('/cinema-tv/', '/cinema-tv-releases/'), URL + '?download=1',
                    URL.replace('/v0.9.9/', '/main/'), URL.replace('0.9.9.apk', '0.9.8.apk')]:
            with self.subTest(url=url):
                self.metadata['apkUrl'] = url
                self.write_inputs()
                with self.assertRaises(ValueError):
                    self.verify()

    def test_version_code_must_increase_and_be_an_integer(self):
        for version in [18, 17, True, '19', 2147483648]:
            with self.subTest(version=version):
                self.metadata['versionCode'] = version
                self.write_inputs()
                with self.assertRaises(ValueError):
                    self.verify()

    def test_actual_apk_identity_must_match_manifest_and_package(self):
        for options in [dict(package='com.ichichuang.cinema.debug'), dict(version='20'), dict(name='0.9.8')]:
            with self.subTest(options=options):
                self.set_android_outputs(**options)
                with self.assertRaises(ValueError):
                    self.verify()

    def test_all_current_apk_signers_must_equal_independent_expected_set(self):
        for signers in [('b' * 64,), (SIGNER, 'b' * 64), ()]:
            with self.subTest(signers=signers):
                self.set_android_outputs(signers=signers)
                with self.assertRaises(ValueError):
                    self.verify()

    def test_compromised_signer_is_rejected_even_when_operator_lists_it(self):
        self.metadata['signers'] = [COMPROMISED]
        self.payload['signers'] = COMPROMISED
        self.set_android_outputs(signers=(COMPROMISED,))
        self.write_inputs()
        with self.assertRaises(ValueError):
            self.verify(expected_signers=[COMPROMISED])

    def test_apksigner_failure_never_accepts_an_unsigned_package(self):
        self.set_android_outputs(verify_exit=1)
        with self.assertRaises(ValueError):
            self.verify()

    def test_wrong_or_missing_publisher_evidence_is_rejected(self):
        for overrides in [dict(publisher_envelope=self.root / 'missing'),
                          dict(publisher_public_key_sha256='b' * 64),
                          dict(publisher_key_id='untrusted-key')]:
            with self.subTest(overrides=overrides):
                with self.assertRaises(ValueError):
                    self.verify(**overrides)

    def test_real_publisher_signature_must_validate(self):
        data = bytearray(self.envelope.read_bytes())
        data[-1] ^= 1
        self.envelope.write_bytes(data)
        with self.assertRaises(ValueError):
            self.verify()

    def test_signed_payload_must_match_every_release_security_field(self):
        changes = {'version': '20', 'size': str(self.metadata['sizeBytes'] + 1), 'sha256': 'b' * 64,
                   'url': URL.replace('0.9.9.apk', '0.9.8.apk'), 'signers': 'b' * 64,
                   'sequence': '6', 'key': 'wrong-key', 'package': 'wrong.package'}
        original = self.payload.copy()
        for field, value in changes.items():
            with self.subTest(field=field):
                self.payload = {**original, field: value}
                self.write_inputs()
                with self.assertRaises(ValueError):
                    self.verify()

    def test_publisher_sequence_must_exceed_independent_previous_sequence(self):
        with self.assertRaises(ValueError):
            self.verify(previous_sequence=7)

    def test_expired_or_future_payload_is_rejected(self):
        now = int(time.time())
        for start, end in [(now - 120, now - 60), (now + 60, now + 120)]:
            with self.subTest(start=start):
                self.payload['not-before'], self.payload['expires'] = str(start), str(end)
                self.write_inputs()
                with self.assertRaises(ValueError):
                    self.verify()

    def test_expiry_is_rechecked_after_android_tool_verification(self):
        now = int(time.time())
        with mock.patch.object(self.module.time, 'time', side_effect=[now, now + 7200]):
            with self.assertRaises(ValueError):
                self.verify()

    def test_noncanonical_or_oversized_envelope_is_rejected(self):
        original = self.envelope.read_bytes()
        for wire in [original + b'x', b'AUTHUPD1' + b'\xff' * 4, b'x' * 16385]:
            with self.subTest(length=len(wire)):
                self.envelope.write_bytes(wire)
                with self.assertRaises(ValueError):
                    self.verify()
        self.write_inputs(b'cinema-update-auth/1\r\n')
        with self.assertRaises(ValueError):
            self.verify()

    def test_duplicate_manifest_keys_are_rejected(self):
        original = self.manifest.read_text()
        self.manifest.write_text(original[:-1] + ', "versionCode": 20}')
        with self.assertRaises(ValueError):
            self.verify()

    def test_all_legacy_download_aliases_must_equal_the_authenticated_release_url(self):
        for alias in ('apk_url', 'url', 'download_url', 'mirror_url'):
            with self.subTest(alias=alias):
                self.metadata[alias] = 'https://updates.example.invalid/other.apk'
                self.write_inputs()
                with self.assertRaises(ValueError):
                    self.verify()
                del self.metadata[alias]
        self.metadata['mirrorUrl'] = 'https://example.invalid/uncontrolled.apk'
        self.write_inputs()
        with self.assertRaises(ValueError):
            self.verify()


if __name__ == '__main__':
    unittest.main()
