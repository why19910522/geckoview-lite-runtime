#!/usr/bin/env python3
"""Inspect a real no-Glean Release AAR. No source-only switch can satisfy this check."""
import argparse
import hashlib
import io
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('aar', type=Path)
p.add_argument('--nm', type=Path, required=True)
p.add_argument('--javap', type=Path, required=True)
a = p.parse_args()
with zipfile.ZipFile(a.aar) as z, tempfile.TemporaryDirectory(prefix='gecko-release-check-') as td:
    td = Path(td)
    classes = td / 'classes.jar'
    classes.write_bytes(z.read('classes.jar'))
    with zipfile.ZipFile(classes) as c:
        assert not any(n.startswith(('mozilla/telemetry/glean/', 'org/mozilla/telemetry/glean/')) for n in c.namelist()), 'Java Glean SDK packaged'
        assert not any(n.endswith('.class') and any('/' + x in n for x in ('CrashReporter', 'CrashHandler', 'CrashPullController', 'CrashHelper', 'ICrashHelper')) for n in c.namelist()), 'Crash reporter packaged'
    debug = subprocess.check_output([str(a.javap), '-classpath', str(classes), '-constants', 'org.mozilla.geckoview.BuildConfig'], text=True)
    assert re.search(r'boolean DEBUG = false;', debug), 'Not a Release Java variant'
    native = [n for n in z.namelist() if n.endswith('.so')]
    assert native and all(n.startswith('jni/arm64-v8a/') for n in native)
    assert not any('libcrash' in n for n in native)
    xul = td / 'libxul.so'
    xul.write_bytes(z.read('jni/arm64-v8a/libxul.so'))
    symbols = subprocess.check_output([str(a.nm), '-D', '--defined-only', str(xul)], text=True)
    forbidden = [line for line in symbols.splitlines() if re.search(r'\b(?:uniffi_glean|glean_(?:initialize|enable_logging|set_upload|set_collection)|_fog_force_reexport)', line)]
    assert not forbidden, 'Glean SDK native exports remain: ' + str(forbidden[:5])
    assert b'crashhelper.CrashHelper' not in z.read('AndroidManifest.xml')
    metadata = z.read('META-INF/com/android/build/gradle/aar-metadata.properties').decode()
    assert 'minCompileSdk=35\n' in metadata
    with zipfile.ZipFile(io.BytesIO(z.read('assets/omni.ja'))) as omni:
        assert not any('aboutglean' in n.lower() for n in omni.namelist()), 'Glean diagnostic page or resources packaged'
        assert {n.split('/')[1] for n in omni.namelist() if n.startswith('localization/')} == {'zh-CN'}
        assert omni.read('default.locale').strip() == omni.read('res/multilocale.txt').strip() == b'zh-CN'
        locales = [line for n in omni.namelist() if n.endswith('.manifest') for line in omni.read(n).decode(errors='replace').splitlines() if line.startswith('locale ')]
        assert locales and all(line.split()[2] == 'zh-CN' for line in locales)
        assert not any('/en-US/' in n and n.startswith(('chrome/', 'localization/')) for n in omni.namelist())
        text = next(n for n in omni.namelist() if n.endswith('/global/commonDialogs.properties'))
        assert re.search('[\u4e00-\u9fff]', omni.read(text).decode())
with a.aar.open('rb') as f:
    h = hashlib.sha256()
    for chunk in iter(lambda: f.read(1048576), b''):
        h.update(chunk)
print('PASS: Release Java, no native/Java Glean SDK, no reporter, SDK35, zh-CN only')
print(a.aar, a.aar.stat().st_size, h.hexdigest())
