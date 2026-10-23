import hashlib
import importlib.util
import json
from contextlib import nullcontext
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

DIRECTORY = Path(__file__).resolve().parents[2] / 'tools/migration-fixture'
sys.path.insert(0, str(DIRECTORY))
spec = importlib.util.spec_from_file_location('migration_runner', DIRECTORY / 'run_matrix.py')
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)
sys.path.remove(str(DIRECTORY))


class MigrationRunnerPreflightTests(unittest.TestCase):
    def test_fresh_target_reaches_install_without_pm_path_failure(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            entries = []
            for name, version in [('initial-a', 1001), ('a-to-b', 1002), ('next-b', 1003),
                                  ('old-a-only', 1004), ('competing-a-to-c', 1005)]:
                data = b'synthetic command-boundary input, not an Android package'
                (root / (name + '.apk')).write_bytes(data)
                entries.append(dict(file=name + '.apk', version=version, sha256=hashlib.sha256(data).hexdigest()))
            (root / 'artifact-report.json').write_text(json.dumps(dict(package=runner.PACKAGE, artifact_evidence=entries)))
            installs = []

            def command(args, **kwargs):
                tail = args[3:]
                stdout, stderr, status = '', '', 0
                if tail == ['get-state']: stdout = 'device\n'
                elif tail[:2] == ['shell', 'getprop']:
                    stdout = {'ro.kernel.qemu': '1', 'ro.build.version.sdk': '30', 'ro.product.cpu.abi': 'x86_64',
                              'ro.build.fingerprint': 'synthetic-api30'}[tail[2]]
                elif tail[:3] == ['shell', 'pm', 'path']: status = 1  # Actual API 30 absent-package contract.
                elif tail[:4] == ['shell', 'pm', 'list', 'packages']: pass  # Empty result is a successful query.
                elif tail[0] == 'install':
                    installs.append(tail)
                    status, stdout = 1, 'Failure [INSTALL_FAILED_INVALID_APK: synthetic stop]\n'
                else: raise AssertionError('Unexpected test command')
                return subprocess.CompletedProcess(args, status, stdout, stderr)

            with patch.object(runner.subprocess, 'run', side_effect=command):
                with self.assertRaisesRegex(ValueError, 'Legitimate fixture update failed'):
                    runner.run(Path('synthetic-adb'), 'emulator-5554', root)
            self.assertEqual(1, len(installs))

    def test_non_emulator_target_is_rejected_before_any_command(self):
        with patch.object(runner.subprocess, 'run') as command:
            with self.assertRaises(ValueError):
                runner.run(Path('unused-adb'), 'not-an-emulator', Path('unused'))
            command.assert_not_called()

    def test_existing_fixture_is_rejected_without_install_or_data_clear(self):
        def command(args, **kwargs):
            tail = args[3:]
            if tail == ['get-state']: value = 'device'
            elif tail[:2] == ['shell', 'getprop']: value = {'ro.kernel.qemu': '1', 'ro.build.version.sdk': '30'}[tail[2]]
            elif tail[:4] == ['shell', 'pm', 'list', 'packages']: value = 'package:' + runner.PACKAGE
            else: raise AssertionError('Existing package must not be mutated')
            return subprocess.CompletedProcess(args, 0, value, '')
        with patch.object(runner.subprocess, 'run', side_effect=command):
            with self.assertRaisesRegex(ValueError, 'Fixture already exists'):
                runner.run(Path('synthetic-adb'), 'emulator-5554', Path('unused'))

    def test_package_query_error_is_not_mistaken_for_absence(self):
        def command(args, **kwargs):
            tail = args[3:]
            if tail == ['get-state']: return subprocess.CompletedProcess(args, 0, 'device', '')
            if tail[:2] == ['shell', 'getprop']:
                return subprocess.CompletedProcess(args, 0, {'ro.kernel.qemu': '1', 'ro.build.version.sdk': '30'}[tail[2]], '')
            if tail[:4] == ['shell', 'pm', 'list', 'packages']:
                return subprocess.CompletedProcess(args, 1, '', 'Permission denied')
            raise AssertionError('Query failure must not authorize mutation')
        with patch.object(runner.subprocess, 'run', side_effect=command):
            with self.assertRaisesRegex(ValueError, 'Emulator fixture command failed'):
                runner.run(Path('synthetic-adb'), 'emulator-5554', Path('unused'))


class MigrationMatrixTests(unittest.TestCase):
    def experiment(self, scenario, rejection='INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match',
                   corrupt_data=False, stale_observation=False):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        entries = []
        for name, version, signer in [('initial-a', 1001, 'a'), ('a-to-b', 1002, 'b'),
                                      ('next-b', 1003, 'b'), ('old-a-only', 1004, 'a'),
                                      ('competing-a-to-c', 1005, 'c')]:
            data = name.encode()
            (root / (name + '.apk')).write_bytes(data)
            entries.append(dict(file=name + '.apk', version=version,
                                current_signer_api30=signer * 64, sha256=hashlib.sha256(data).hexdigest()))
        (root / 'artifact-report.json').write_text(json.dumps(dict(package=runner.PACKAGE, artifact_evidence=entries)))
        state = dict(version=0, current=[], history=[], uid=10123,
                     marker='migration-synthetic-data', progress=12345)
        installs = []

        def command(args, **kwargs):
            self.assertEqual(['-s', 'emulator-5554'], args[1:3])
            tail = args[3:]
            status, value = 0, ''
            if tail == ['get-state']: value = 'device'
            elif tail[:2] == ['shell', 'getprop']:
                value = {'ro.kernel.qemu': '1', 'ro.build.version.sdk': '30',
                         'ro.product.cpu.abi': 'arm64-v8a', 'ro.build.fingerprint': 'synthetic-api30'}[tail[2]]
            elif tail[:4] == ['shell', 'pm', 'list', 'packages']: pass
            elif tail[0] == 'install':
                entry = next(e for e in entries if e['file'] == Path(tail[-1]).name)
                installs.append(entry['file'])
                negative = entry['version'] >= 1004 and 'control' not in scenario
                if negative and rejection:
                    status, value = 1, 'Failure [' + rejection + ']'
                    if corrupt_data: state['marker'] = ''
                else:
                    state.update(version=entry['version'], current=[entry['current_signer_api30']])
                    state['history'] = (['a' * 64, entry['current_signer_api30']]
                                        if entry['version'] in (1002, 1005) else state['current'])
                    value = 'Success'
            elif tail[:3] == ['shell', 'am', 'start']:
                self.assertNotIn('-W', tail, 'Finishing Activity must not wait indefinitely for a resumed Activity')
                self.assertEqual('observationNonce', tail[-2])
                state['nonce'] = tail[-1]
                value = 'Status: ok'
            elif tail[:3] == ['shell', 'am', 'force-stop']: pass
            elif tail[:3] == ['shell', 'run-as', runner.PACKAGE]:
                value = json.dumps(dict(state, nonce='stale') if stale_observation else state)
            else: raise AssertionError('Unexpected mutation or device command: ' + str(tail))
            return subprocess.CompletedProcess(args, status, value, '')

        timer = patch.object(runner.time, 'monotonic', side_effect=[0, 0, 11]) if stale_observation else nullcontext()
        with patch.object(runner.subprocess, 'run', side_effect=command), timer:
            try:
                result = runner.run(Path('synthetic-adb'), 'emulator-5554', root, scenario=scenario)
            except TypeError as error:
                self.fail('Independent checkpoint experiment is unavailable: ' + str(error))
            except ValueError as error:
                result = error
        report = json.loads((root / ('platform-report-' + scenario + '.json')).read_text())
        return result, report, installs

    def test_each_negative_uses_only_its_requested_checkpoint_and_candidate(self):
        for scenario, files, version in [
            ('rotation-old-a', ['initial-a.apk', 'a-to-b.apk', 'old-a-only.apk'], 1002),
            ('rotation-competing', ['initial-a.apk', 'a-to-b.apk', 'competing-a-to-c.apk'], 1002),
            ('followup-old-a', ['initial-a.apk', 'a-to-b.apk', 'next-b.apk', 'old-a-only.apk'], 1003),
            ('followup-competing', ['initial-a.apk', 'a-to-b.apk', 'next-b.apk', 'competing-a-to-c.apk'], 1003),
        ]:
            with self.subTest(scenario=scenario):
                result, report, installed = self.experiment(scenario)
                self.assertNotIsInstance(result, Exception)
                self.assertEqual(files, installed)
                self.assertEqual('PASS', report['status'])
                self.assertEqual(version, report['results'][-1]['observation']['version'])
                self.assertTrue(report['results'][-1]['postconditions_verified'])

    def test_accepted_old_key_preserves_failure_evidence(self):
        result, report, installed = self.experiment('rotation-old-a', rejection=None)
        self.assertIsInstance(result, ValueError)
        self.assertEqual('FAIL', report['status'])
        self.assertEqual('Success', report['results'][-1]['package_manager_reason'])
        self.assertEqual(1004, report['results'][-1]['observation']['version'])
        self.assertEqual(3, len(installed))

    def test_downgrade_is_not_signature_rejection(self):
        result, report, _ = self.experiment('followup-old-a', rejection='INSTALL_FAILED_VERSION_DOWNGRADE')
        self.assertIsInstance(result, ValueError)
        self.assertEqual('FAIL', report['status'])
        self.assertFalse(report['results'][-1].get('postconditions_verified', False))

    def test_signature_error_with_changed_data_fails(self):
        result, report, _ = self.experiment('rotation-competing', corrupt_data=True)
        self.assertIsInstance(result, ValueError)
        self.assertEqual('FAIL', report['status'])
        self.assertEqual('', report['results'][-1]['observation']['marker'])

    def test_positive_controls_install_higher_version_candidates_from_a(self):
        for scenario, expected, signer in [('control-old-a', 'old-a-only.apk', 'a'),
                                           ('control-competing', 'competing-a-to-c.apk', 'c')]:
            with self.subTest(scenario=scenario):
                result, report, installed = self.experiment(scenario)
                self.assertNotIsInstance(result, Exception)
                self.assertEqual(['initial-a.apk', expected], installed)
                self.assertEqual('PASS', report['status'])
                self.assertEqual([signer * 64], report['results'][-1]['observation']['current'])

    def test_stale_observation_cannot_establish_a_successful_transition(self):
        result, report, installed = self.experiment('migration', stale_observation=True)
        self.assertIsInstance(result, ValueError)
        self.assertEqual('FAIL', report['status'])
        self.assertEqual(['initial-a.apk'], installed)
        self.assertNotIn('postconditions_verified', report['results'][0])


if __name__ == '__main__':
    unittest.main()
