import importlib.util
import io
from pathlib import Path
import unittest
import zipfile

SCRIPT = Path(__file__).resolve().parents[1] / 'collect_component_inventory.py'
spec = importlib.util.spec_from_file_location('component_inventory', SCRIPT)
inventory = importlib.util.module_from_spec(spec)
spec.loader.exec_module(inventory)


def archive(entries):
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w') as target:
        for name, data in entries.items():
            target.writestr(name, data)
    return buffer.getvalue()


class ComponentInventoryTests(unittest.TestCase):
    def test_nested_aar_classes_and_license_bytes_are_linked_without_extraction(self):
        notice = b'Synthetic upstream notice\n' * 8
        data = archive({'classes.jar': archive({'example/Widget.class': b'synthetic bytecode',
                                              'META-INF/LICENSE.txt': notice}),
                        'jni/armeabi-v7a/libexample.so': b'synthetic ELF'})
        classes, notices, native = inventory.inspect_archive(data)
        self.assertEqual({'Lexample/Widget;'}, classes)
        self.assertEqual('classes.jar!/META-INF/LICENSE.txt', notices[0]['entry'])
        self.assertEqual(notice.decode(), notices[0]['text'])
        self.assertEqual({'lib/armeabi-v7a/libexample.so'}, set(native))

    def test_license_named_class_is_not_mistaken_for_notice(self):
        classes, notices, native = inventory.inspect_archive(archive({'example/License.class': b'bytecode'}))
        self.assertEqual({'Lexample/License;'}, classes)
        self.assertEqual([], notices)
        self.assertEqual({}, native)

    def test_archive_paths_are_only_read_and_not_written(self):
        classes, notices, _ = inventory.inspect_archive(archive({'../../LICENSE': b'not extracted\n' * 20}))
        self.assertEqual(set(), classes)
        self.assertEqual('../../LICENSE', notices[0]['entry'])

    def test_malformed_dex_is_rejected_instead_of_empty_success(self):
        for data in [b'not a dex', b'dex\n' + b'\xff' * 108]:
            with self.assertRaises((ValueError, IndexError)):
                inventory.dex_classes(data)


if __name__ == '__main__':
    unittest.main()
