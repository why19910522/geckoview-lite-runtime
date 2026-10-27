#!/usr/bin/env python3
"""Independently verify delivered ZIP bytes, signatures, alignment and host APK SDKs."""
import argparse,base64,hashlib,io,json,re,subprocess,tempfile,zipfile
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
for name in ['bundled','pair','public-key','external-apk','bundled-apk','zipalign','aapt2']:p.add_argument('--'+name,type=Path,required=True)
a=p.parse_args()
def sha(data):return hashlib.sha256(data).hexdigest()
with zipfile.ZipFile(a.bundled) as original,zipfile.ZipFile(a.pair/'bridge.aar') as bridge,zipfile.ZipFile(a.pair/'core-package.zip') as packet:
 contract=json.loads(bridge.read('assets/geckolite/host-contract.json'))
 assert contract==json.loads((a.pair/'host-contract.json').read_bytes())
 assert set(packet.namelist())=={'manifest.json','manifest.sig','core.apk'}
 raw=packet.read('manifest.json');manifest=json.loads(raw);signature=json.loads(packet.read('manifest.sig'));core_data=packet.read('core.apk')
 assert sha(raw)==contract['manifestSha256'] and sha(core_data)==contract['coreSha256']==manifest['coreSha256']
 assert len(core_data)==contract['coreSize']
 assert 64<=contract['resourceFd']<=1023
 assert [n for n in bridge.namelist() if n.startswith('jni/')]==['jni/arm64-v8a/libgeckolite_bootstrap.so']
 assert 'assets/omni.ja' not in bridge.namelist()
 for name in original.namelist():
  if not name.startswith('jni/') and name!='assets/omni.ja' and not name.endswith('/'):assert original.read(name)==bridge.read(name),name
 with zipfile.ZipFile(io.BytesIO(core_data)) as core:
  assert len([n for n in core.namelist() if n.endswith('.so')])==11
  assert set(core.namelist())=={entry['path'] for entry in manifest['files']}
  for entry in manifest['files']:
   data=core.read(entry['path']);assert sha(data)==entry['sha256'] and len(data)==entry['size']
   source=entry['path'].replace('lib/','jni/',1) if entry['path'].startswith('lib/') else entry['path']
   assert data==original.read(source),source
 with tempfile.TemporaryDirectory() as t:
  t=Path(t);(t/'core.apk').write_bytes(core_data);(t/'manifest').write_bytes(raw);(t/'signature').write_bytes(base64.b64decode(signature['signature'],validate=True))
  subprocess.run(['openssl','pkey','-pubin','-inform','DER','-in',str(a.public_key),'-out',str(t/'public.pem')],check=True,capture_output=True)
  subprocess.run(['openssl','dgst','-sha256','-verify',str(t/'public.pem'),'-signature',str(t/'signature'),str(t/'manifest')],check=True,capture_output=True)
  subprocess.run([str(a.zipalign),'-c','-P','16','4',str(t/'core.apk')],check=True,capture_output=True)
 for apk,external in [(a.external_apk,True),(a.bundled_apk,False)]:
  with zipfile.ZipFile(apk) as z:
   native=[n for n in z.namelist() if n.endswith('.so')]
   if external:
    assert native==['lib/arm64-v8a/libgeckolite_bootstrap.so'] and 'assets/omni.ja' not in z.namelist()
    assert json.loads(z.read('assets/geckolite/host-contract.json'))==contract
    assert z.read(native[0])==bridge.read('jni/arm64-v8a/libgeckolite_bootstrap.so')
   else:
    assert len(native)==11 and z.read('assets/omni.ja')==original.read('assets/omni.ja')
    assert 'assets/geckolite/host-contract.json' not in z.namelist()
    for name in native:assert z.read(name)==original.read(name.replace('lib/','jni/',1)),name
  metadata=subprocess.check_output([str(a.aapt2),'dump','badging',str(apk)],text=True)
  assert "compileSdkVersion='35'" in metadata and "minSdkVersion:'26'" in metadata and "targetSdkVersion:'30'" in metadata
print('PASS: exact bridge/core payloads, P-256 signature, 11-library closure bytes, 16KB ZIP alignment, dual APK payloads and actual SDK35/30/26 metadata')
