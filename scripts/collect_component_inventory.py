#!/usr/bin/env python3
"""Link resolved archives to classes/native/notice bytes observed in an unsigned APK. Never execute archives."""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import struct
import xml.etree.ElementTree as ET
import zipfile


def sha(data):
    return hashlib.sha256(data).hexdigest()


def dex_classes(data):
    if not data.startswith(b'dex\n') or len(data) < 112:
        raise ValueError('Invalid DEX inventory input.')
    def word(offset):
        return struct.unpack_from('<I', data, offset)[0]
    strings_count, strings = word(56), word(60)
    types_count, types = word(64), word(68)
    classes_count, classes = word(96), word(100)
    if (strings + strings_count * 4 > len(data) or types + types_count * 4 > len(data)
            or classes + classes_count * 32 > len(data)):
        raise ValueError('Invalid DEX tables.')
    result = set()
    for offset in range(classes, classes + classes_count * 32, 32):
        type_index = word(offset)
        if type_index >= types_count:
            raise ValueError('Invalid DEX type.')
        string_index = word(types + type_index * 4)
        if string_index >= strings_count:
            raise ValueError('Invalid DEX string.')
        start = word(strings + string_index * 4)
        for _ in range(5):
            value = data[start]
            start += 1
            if value < 128:
                break
        else:
            raise ValueError('Invalid DEX string length.')
        end = data.index(b'\0', start)
        result.add(data[start:end].decode('utf-8'))
    return result


def inspect_archive(data):
    classes, notices, native = set(), [], {}
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        for name in archive.namelist():
            base = name.rsplit('/', 1)[-1].lower()
            if name.endswith('.class') and not name.startswith('META-INF/') and name != 'module-info.class':
                classes.add('L' + name[:-6] + ';')
            elif name == 'classes.jar' or (name.startswith('libs/') and name.endswith('.jar')):
                nested_classes, nested_notices, _ = inspect_archive(archive.read(name))
                classes |= nested_classes
                notices.extend(dict(n, entry=name + '!/' + n['entry']) for n in nested_notices)
            elif any(token in base for token in ['license', 'notice', 'copying', 'copyright']) and not name.endswith('/'):
                content = archive.read(name)
                if len(content) > 100 and not name.endswith(('.class', '.kotlin_metadata')):
                    notices.append(dict(entry=name, sha256=sha(content), text=content.decode('utf-8', 'replace')))
            elif name.startswith('jni/') and name.endswith('.so'):
                native[name.replace('jni/', 'lib/', 1)] = sha(archive.read(name))
    return classes, notices, native


def pom_licenses(cache, coordinate, seen=None):
    seen = set() if seen is None else seen
    if coordinate in seen:
        return []
    seen.add(coordinate)
    group, artifact, version = coordinate.split(':')
    paths = list((cache / group / artifact / version).glob('*/*.pom'))
    if not paths:
        return []
    root = ET.fromstring(paths[0].read_bytes())
    ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
    licenses = [dict(name=n.findtext('m:name', '', ns), url=n.findtext('m:url', '', ns))
                for n in root.findall('m:licenses/m:license', ns)]
    if licenses:
        return licenses
    parent = root.find('m:parent', ns)
    if parent is not None:
        return pom_licenses(cache, ':'.join(parent.findtext('m:' + key, '', ns)
                                          for key in ['groupId', 'artifactId', 'version']), seen)
    return []


def collect(inputs, cache, apk):
    with zipfile.ZipFile(apk) as archive:
        dex = set().union(*(dex_classes(archive.read(n)) for n in archive.namelist() if n.endswith('.dex')))
        apk_notices = {n: sha(archive.read(n)) for n in archive.namelist()
                       if any(t in n.lower().rsplit('/', 1)[-1] for t in ['license', 'notice', 'copying', 'copyright'])
                       and not n.endswith('/')}
        apk_native = {n: sha(archive.read(n)) for n in archive.namelist() if n.endswith('.so')}
    components = {}
    for item in inputs:
        path = (cache / item['cache_path']).resolve()
        if cache.resolve() not in path.parents:
            raise ValueError('Artifact must be in the existing module cache.')
        content = path.read_bytes()
        digest = sha(content)
        key = (item['coordinate'], digest)
        if key in components:
            components[key]['scopes'].append(item['scope'])
            continue
        classes, notices, native = inspect_archive(content)
        group, artifact, version = item['coordinate'].split(':')
        base = 'https://dl.google.com/dl/android/maven2' if group.startswith(('androidx.', 'com.android')) else 'https://repo.maven.apache.org/maven2'
        filename = artifact + '-' + version + ('-' + item['classifier'] if item.get('classifier') else '') + '.' + item['type']
        components[key] = dict(coordinate=item['coordinate'], scopes=[item['scope']], file=item['file'],
            artifact_url=f'{base}/{group.replace(".", "/")}/{artifact}/{version}/{filename}',
            sha256=digest, licenses_declared_in_pom=pom_licenses(cache, item['coordinate']),
            archive_class_count=len(classes), apk_class_matches=len(classes & dex),
            apk_match_examples=sorted(classes & dex)[:3],
            native=[dict(entry=n, archive_sha256=h, apk_sha256=apk_native.get(n),
                         exact_bytes_in_apk=apk_native.get(n) == h) for n, h in sorted(native.items())],
            notices=[dict(entry=n['entry'], sha256=n['sha256'],
                          apk_entries_with_same_bytes=sorted(k for k, v in apk_notices.items() if v == n['sha256']))
                     for n in notices])
    for component in components.values():
        component['scopes'] = sorted(set(component['scopes']))
    return dict(format='artifact-linked component inventory', apk_sha256=sha(apk.read_bytes()),
                apk_dex_classes=len(dex), apk_notices=apk_notices, apk_native=apk_native,
                limits='Class intersection proves presence, not exclusive attribution or a complete SBOM. Build/test scopes do not imply shipment. No archives executed.',
                components=sorted(components.values(), key=lambda c: (c['coordinate'], c['sha256'])))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inputs', type=Path, nargs='+', required=True)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--cache', type=Path, default=Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle')) / 'caches/modules-2/files-2.1')
    args = parser.parse_args()
    try:
        result = collect([entry for file in args.inputs for entry in json.loads(file.read_text())], args.cache, args.apk)
        args.output.write_text(json.dumps(result, indent=2) + '\n')
        print('Inventoried components:', len(result['components']))
    except (OSError, ValueError, KeyError, zipfile.BadZipFile, ET.ParseError, struct.error, IndexError):
        print('Component inventory failed; inspect inputs locally.')
        raise SystemExit(1)
