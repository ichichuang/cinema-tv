import hashlib
import importlib.util
import json
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
                    stdout = {'ro.kernel.qemu': '1', 'ro.build.version.sdk': '30', 'ro.product.cpu.abi': 'x86_64'}[tail[2]]
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


if __name__ == '__main__':
    unittest.main()
