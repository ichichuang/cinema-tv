import importlib.util
import pathlib
import unittest

SCRIPT = pathlib.Path(__file__).resolve().parents[1] / 'check_repository_security.py'
spec = importlib.util.spec_from_file_location('security_check', SCRIPT)
security = importlib.util.module_from_spec(spec)
spec.loader.exec_module(security)

class RepositorySecurityTests(unittest.TestCase):
    def test_keystores_are_rejected(self):
        for suffix in ('.jks', '.keystore', '.p12', '.pfx', '.pk8', '.key'):
            with self.subTest(suffix=suffix):
                self.assertIn('private-key-file', security.scan_path('keys/release' + suffix, b'opaque'))

    def test_secret_properties_are_rejected(self):
        self.assertIn('local-secret-file', security.scan_path('signing.properties', b''))

    def test_example_properties_are_allowed(self):
        self.assertEqual([], security.scan_path('signing.properties.example', b'storePassword=\n'))

    def test_environment_files_are_rejected(self):
        for path in ('.env', 'app/.env.production', 'nested/.github_token'):
            with self.subTest(path=path):
                self.assertIn('local-secret-file', security.scan_path(path, b''))

    def test_environment_template_is_allowed(self):
        self.assertEqual([], security.scan_path('.env.example', b'API_TOKEN=\n'))

    def test_literal_signing_password_is_rejected(self):
        text = ('store' + 'Password = "' + 'synthetic-test-value' + '"').encode()
        self.assertIn('literal-signing-password', security.scan_path('app/build.gradle.kts', text))

    def test_environment_signing_configuration_is_allowed(self):
        text = b'storePassword = providers.environmentVariable("SIGNING_STORE_PASSWORD").get()'
        self.assertEqual([], security.scan_path('app/build.gradle.kts', text))

    def test_private_key_content_is_rejected_even_with_an_innocent_name(self):
        text = ('-----BEGIN ' + 'PRIVATE KEY-----\nnot-a-real-key').encode()
        self.assertIn('private-key-content', security.scan_path('notes.txt', text))

    def test_github_token_is_rejected_without_echoing_it(self):
        token = ('gh' + 'p_' + 'A' * 36).encode()
        result = security.scan_path('config.txt', token)
        self.assertIn('github-token', result)
        self.assertNotIn(token.decode(), str(result))

    def test_release_binaries_are_rejected(self):
        self.assertIn('release-binary', security.scan_path('out/app.apk', b'zip'))
        self.assertIn('release-binary', security.scan_path('out/app.aab', b'zip'))

    def test_public_certificate_fingerprint_is_allowed(self):
        self.assertEqual([], security.scan_path('docs/RELEASE.md', b'SHA-256: ' + b'a' * 64))

    def test_nonsecret_binary_is_allowed(self):
        self.assertEqual([], security.scan_path('gradle/wrapper/gradle-wrapper.jar', b'\x00\xff'))

    def test_personal_filesystem_paths_are_rejected(self):
        for path in ('/' + 'Users/example/project', 'C:' + '\\Users\\example\\project'):
            with self.subTest(path=path):
                self.assertIn('personal-path', security.scan_path('script.txt', path.encode()))

    def test_environment_based_paths_are_allowed(self):
        self.assertEqual([], security.scan_path('script.ps1', b'$env:ANDROID_HOME; $env:USERPROFILE'))

    def test_credential_urls_are_rejected_without_echoing_values(self):
        url = ('https://example.invalid/?to' + 'ken=synthetic-private-value').encode()
        result = security.scan_path('config.txt', url)
        self.assertIn('credential-url', result)
        self.assertNotIn(url.decode(), str(result))

    def test_url_userinfo_is_rejected(self):
        url = ('https://' + 'member:synthetic-private-value@example.invalid/resource').encode()
        self.assertIn('credential-url', security.scan_path('config.txt', url))

    def test_public_urls_without_credentials_are_allowed(self):
        self.assertEqual([], security.scan_path('config.txt', b'https://example.invalid/resource?mode=public'))

if __name__ == '__main__':
    unittest.main()
