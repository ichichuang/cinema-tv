#!/usr/bin/env python3
"""Run fixture seeding-policy checks with existing JDK/SDK, without keys, APKs or devices."""
import os
from pathlib import Path
import subprocess
import tempfile


def check():
    sdk = Path(os.environ['ANDROID_HOME'])
    java = Path(os.environ['JAVA_HOME']) / 'bin'
    source = Path(__file__).resolve().parent
    android = sdk / 'platforms/android-37.0/android.jar'
    with tempfile.TemporaryDirectory() as temporary:
        commands = [[str(java / 'javac'), '-source', '17', '-target', '17', '-classpath', str(android),
                     '-d', temporary, str(source / 'FixtureActivity.java'), str(source / 'FixtureStateTest.java')],
                    [str(java / 'java'), '-cp', os.pathsep.join([temporary, str(android)]),
                     'com.ichichuang.cinema.migrationfixture.FixtureStateTest']]
        for command in commands:
            result = subprocess.run(command, capture_output=True, text=True)
            for line in (result.stdout + result.stderr).splitlines():
                if line.startswith('Fixture state policy:') or 'AssertionError:' in line:
                    print(line)
            if result.returncode:
                raise ValueError('Fixture policy checks failed; inspect existing SDK/JDK and assertions locally.')


if __name__ == '__main__':
    try:
        check()
    except (ValueError, OSError, KeyError):
        print('Fixture JVM policy verification failed; this is not a platform experiment.')
        raise SystemExit(1)
