import importlib.util
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

    def prepare(self):
        spec = importlib.util.spec_from_file_location('public_source', SCRIPT)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module.prepare(self.root, self.output, 'HEAD')

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

    def test_current_signing_material_blocks_export(self):
        (self.root / 'sample.txt').write_text('safe')
        self.commit()
        (self.root / 'release.jks').write_bytes(b'opaque')
        self.git('add', '--', 'release.jks')
        self.git('commit', '-qm', 'synthetic fixture')
        with self.assertRaises(ValueError):
            self.prepare()
        self.assertFalse(self.output.exists())

    def test_dirty_worktree_blocks_export(self):
        (self.root / 'sample.txt').write_text('safe')
        self.commit()
        (self.root / 'sample.txt').write_text('uncommitted')
        with self.assertRaises(ValueError):
            self.prepare()

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
