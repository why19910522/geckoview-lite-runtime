#!/usr/bin/env python3
"""Verify PDF.js/Web Speech removal while preserving the DevTools server."""
import argparse
import io
import json
from pathlib import Path
import subprocess
import sys
import zipfile

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('aar', type=Path)
p.add_argument('--config', type=Path, required=True)
p.add_argument('--nm', type=Path, required=True)
p.add_argument('--javap', type=Path, required=True)
p.add_argument('--expected-opt', choices=['-O2', '-Os'], required=True)
a = p.parse_args()
subprocess.run([sys.executable, str(Path(__file__).with_name('verify-no-glean-aar.py')), str(a.aar), '--nm', str(a.nm), '--javap', str(a.javap)], check=True)
s = json.loads(a.config.read_text())['substs']
assert not s.get('MOZ_PDFJS') and not s.get('MOZ_WEBSPEECH')
assert s['MOZ_DEVTOOLS'] == 'server' and s['MOZ_WEBRTC'] and s['MOZ_JXL']
assert s['MOZ_OPTIMIZE_FLAGS'] == [a.expected_opt]
assert not s.get('MOZ_DEBUG') and not s.get('MOZ_GLEAN') and not s.get('MOZ_CRASHREPORTER')
assert s['ANDROID_COMPILE_SDK_MAJOR'] == '35' and s['ANDROID_TARGET_SDK'] == '30'
if a.expected_opt == '-Os':
    assert '-flto=thin' in s['MOZ_LTO_CFLAGS'] and '-flto=thin' in s['MOZ_LTO_LDFLAGS']
with zipfile.ZipFile(a.aar) as z:
    with zipfile.ZipFile(io.BytesIO(z.read('classes.jar'))) as c:
        assert not any('SpeechSynthesisService' in n for n in c.namelist())
    with zipfile.ZipFile(io.BytesIO(z.read('assets/omni.ja'))) as omni:
        names = omni.namelist()
        forbidden = ['/pdfjs/', '/pdfviewer/', 'PdfJsDefaultPrefs.js', 'modules/pdfjs.sys.mjs', 'AboutPDFChild.sys.mjs', 'AboutPDFParent.sys.mjs', '/aboutPDF.ftl']
        assert not any(any(x in n for x in forbidden) for n in names), 'PDF.js resources remain'
        # Existing print/save comparisons and the unsupported PDF reftest loader may
        # retain URI literals. Startup/actor/troubleshooting imports must be gone.
        for n in ['modules/GeckoViewStartup.sys.mjs', 'modules/GeckoViewContent.sys.mjs', 'modules/ActorManagerParent.sys.mjs', 'modules/Troubleshoot.sys.mjs']:
            assert b'resource://pdf.js/' not in omni.read(n), 'Dangling PDF.js module URI: ' + n
        assert any('devtools/server/' in n for n in names), 'DevTools server missing'
        assert any('devtools/shared/security/socket' in n for n in names), 'DevTools socket missing'
        assert any('GeckoViewRemoteDebugger.sys.mjs' in n for n in names)
        assert any(n.startswith('hyphenation/') for n in names)
print('PASS: no PDF.js / Web Speech, DevTools server/socket retained, expected C++ options')
