import importlib.util
import hashlib
import pathlib
import subprocess
import tempfile
import unittest

SCRIPT = pathlib.Path(__file__).resolve().parents[1] / 'prepare_public_source.py'


class PublicSourcePreparationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = pathlib.Path(self.temp.name) / 'source'
        self.root.mkdir()
        self.output = pathlib.Path(self.temp.name) / 'review'
        self.git('init', '-q')
        self.git('config', 'user.name', 'Synthetic Contributor')
        self.git('config', 'user.email', 'contributor@example.invalid')

    def git(self, *args):
        return subprocess.run(['git', '-C', str(self.root), *args], check=True, capture_output=True)

    def commit(self):
        self.git('add', '--', 'sample.txt')
        self.git('commit', '-qm', 'synthetic fixture')

    def prepare(self, root=None):
        spec = importlib.util.spec_from_file_location('public_source', SCRIPT)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module.prepare(root or self.root, self.output, 'HEAD')

    def test_snapshot_excludes_old_history_and_preserves_content_and_attribution(self):
        (self.root / 'sample.txt').write_text('historical private text')
        self.commit()
        (self.root / 'sample.txt').write_text('reviewable source')
        self.commit()
        result = self.prepare()
        self.assertEqual('local-review-only', result['status'])
        self.assertEqual('reviewable source', (self.output / 'sample.txt').read_text())
        self.assertFalse((self.output / '.git').exists())
        self.assertIn('Synthetic Contributor', (self.output / 'SOURCE_HISTORY.md').read_text())
        self.assertNotIn('historical private text', ''.join(p.read_text() for p in self.output.iterdir()))

    def test_private_incident_evidence_is_omitted_without_losing_notices(self):
        paths = {
            'sample.txt': b'reviewable source',
            'docs/evidence/actions-cache-metadata.json': b'{"private_cache_id": 123}',
            'docs/evidence/scanner-classification.json': b'{"historical_findings": 88}',
            'docs/evidence/component-inventory.json': b'{"license": "Apache-2.0"}',
            'app/src/main/assets/third_party/NOTICE.txt': b'Upstream copyright notice',
        }
        for path, data in paths.items():
            target = self.root / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
        self.git('add', '--', *paths)
        self.git('commit', '-qm', 'synthetic publication evidence')
        result = self.prepare()
        omitted = {item['path'] for item in result.get('omitted_files', [])}
        self.assertEqual({
            'docs/evidence/actions-cache-metadata.json',
            'docs/evidence/scanner-classification.json',
        }, omitted)
        actual = {str(path.relative_to(self.output)) for path in self.output.rglob('*') if path.is_file()}
        self.assertEqual((set(paths) - omitted) | {'SOURCE_HISTORY.md'}, actual)
        for path in set(paths) - omitted:
            self.assertEqual(paths[path], (self.output / path).read_bytes())

    def test_private_source_mapping_stays_in_export_manifest(self):
        (self.root / 'sample.txt').write_text('reviewable source')
        (self.root / 'sample.txt').chmod(0o755)
        self.commit()
        revision = self.git('rev-parse', 'HEAD').stdout.decode().strip()
        result = self.prepare()
        self.assertNotIn(revision, (self.output / 'SOURCE_HISTORY.md').read_text())
        self.assertEqual(revision, result['source_commit'])
        self.assertEqual([{
            'path': 'sample.txt', 'mode': '100755',
            'sha256': hashlib.sha256(b'reviewable source').hexdigest(),
        }], result['files'])
        self.assertEqual(0o755, (self.output / 'sample.txt').stat().st_mode & 0o777)

    def test_current_signing_material_blocks_export(self):
        (self.root / 'sample.txt').write_text('safe')
        self.commit()
        (self.root / 'release.jks').write_bytes(b'opaque')
        self.git('add', '--', 'release.jks')
        self.git('commit', '-qm', 'synthetic fixture')
        with self.assertRaises(ValueError):
            self.prepare()
        self.assertFalse(self.output.exists())

    def test_attribution_filename_alias_cannot_overwrite_tracked_source(self):
        (self.root / 'sample.txt').write_text('safe')
        self.commit()
        (self.root / 'source_history.md').write_text('original contributor evidence')
        self.git('add', '--', 'source_history.md')
        self.git('commit', '-qm', 'synthetic attribution alias')
        with self.assertRaises(ValueError):
            self.prepare()
        self.assertFalse(self.output.exists())

    def test_dirty_worktree_blocks_export(self):
        (self.root / 'sample.txt').write_text('safe')
        self.commit()
        (self.root / 'sample.txt').write_text('uncommitted')
        with self.assertRaises(ValueError):
            self.prepare()

    def test_nested_root_cannot_export_into_real_repository(self):
        (self.root / 'sample.txt').write_text('safe')
        self.commit()
        nested = self.root / 'nested'
        nested.mkdir()
        self.output = self.root / 'review'
        with self.assertRaises(ValueError):
            self.prepare(nested)
        self.assertFalse(self.output.exists())
        self.assertEqual(b'', self.git('status', '--porcelain').stdout)

    def test_existing_destination_is_never_overwritten(self):
        (self.root / 'sample.txt').write_text('safe')
        self.commit()
        self.output.mkdir()
        (self.output / 'keep.txt').write_text('owner file')
        with self.assertRaises(ValueError):
            self.prepare()
        self.assertEqual('owner file', (self.output / 'keep.txt').read_text())

    def test_symlink_cannot_escape_export(self):
        (self.root / 'sample.txt').symlink_to('../outside')
        self.commit()
        with self.assertRaises(ValueError):
            self.prepare()


if __name__ == '__main__':
    unittest.main()
