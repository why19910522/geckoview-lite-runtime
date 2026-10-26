"""Real-file tests: catch duplicate engines, unaligned ZIP mappings and untrusted packages."""
import hashlib, io, json, os, struct, subprocess, sys, tempfile, unittest, zipfile
from pathlib import Path
TOOL=Path(__file__).resolve().parents[1]/'package_core.py'
CLANG=Path(os.environ['GECKO_CLANG'])
ZIPALIGN=Path(os.environ['GECKO_ZIPALIGN'])
class Packages(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.temp=tempfile.TemporaryDirectory();cls.root=Path(cls.temp.name)
  cls.key=cls.root/'key.pem';subprocess.run(['openssl','ecparam','-name','prime256v1','-genkey','-noout','-out',str(cls.key)],check=True,capture_output=True)
  src=cls.root/'a.c';src.write_text('int fixture(void){return 7;}')
  cls.glue=cls.root/'libmozglue.so'
  subprocess.run([str(CLANG),'--target=aarch64-linux-android26','-fuse-ld=lld','-nostdlib','-shared','-Wl,-soname,libmozglue.so','-Wl,--build-id=sha1','-Wl,-z,max-page-size=16384',str(src),'-o',str(cls.glue)],check=True,capture_output=True)
  src.write_text('extern int fixture(void); int second(void){return fixture();}')
  cls.xul=cls.root/'libxul.so'
  subprocess.run([str(CLANG),'--target=aarch64-linux-android26','-fuse-ld=lld','-nostdlib','-shared','-Wl,-soname,libxul.so','-Wl,--build-id=sha1','-Wl,-z,max-page-size=16384',str(src),'-L'+str(cls.root),'-lmozglue','-o',str(cls.xul)],check=True,capture_output=True)
 @classmethod
 def tearDownClass(cls):cls.temp.cleanup()
 def fixture(self,root,missing=False,duplicate=False):
  p=root/'input.aar'
  with zipfile.ZipFile(p,'w') as z:
   z.writestr('AndroidManifest.xml',b'<manifest package="org.mozilla.geckoview"/>');z.writestr('classes.jar',b'bridge classes');z.writestr('R.txt',b'')
   z.writestr('assets/omni.ja',b'gecko resource bytes')
   if not missing:z.write(self.glue,'jni/arm64-v8a/libmozglue.so')
   z.write(self.xul,'jni/arm64-v8a/libxul.so')
   if duplicate:z.writestr('classes.jar',b'ambiguous')
  return p
 def pack(self,p,out):
  return subprocess.run([sys.executable,str(TOOL),'--aar',str(p),'--output',str(out),'--private-key',str(self.key),'--key-id','local-test','--revision','d6fd7a4e69b3c2d4550582eb38d2cdbd9cbad79a','--profile','lite-zh-CN-no-glean-no-pdf-speech','--sequence','1'],capture_output=True,text=True)
 def test_split_bytes_signature_and_16k_mapping(self):
  with tempfile.TemporaryDirectory() as t:
   root=Path(t);p=self.fixture(root);out=root/'out';r=self.pack(p,out);self.assertEqual(r.returncode,0,r.stderr)
   with zipfile.ZipFile(out/'bridge.aar') as bridge:
    self.assertFalse(any(n.startswith('jni/') or n=='assets/omni.ja' for n in bridge.namelist()))
    self.assertEqual(bridge.read('classes.jar'),b'bridge classes')
    contract=json.loads(bridge.read('assets/geckolite/host-contract.json'))
   with zipfile.ZipFile(out/'core-package.zip') as package:
    self.assertEqual(set(package.namelist()),{'manifest.json','manifest.sig','core.apk'})
    manifest=package.read('manifest.json');m=json.loads(manifest);sig=json.loads(package.read('manifest.sig'))
    core=package.read('core.apk');self.assertEqual(hashlib.sha256(core).hexdigest(),contract['coreSha256']);self.assertEqual(m['bridgeFingerprint'],contract['bridgeFingerprint'])
    import base64
    (root/'manifest').write_bytes(manifest);(root/'sig').write_bytes(base64.b64decode(sig['signature']))
    subprocess.run(['openssl','pkey','-in',str(self.key),'-pubout','-out',str(root/'pub.pem')],check=True,capture_output=True)
    r=subprocess.run(['openssl','dgst','-sha256','-verify',str(root/'pub.pem'),'-signature',str(root/'sig'),str(root/'manifest')],capture_output=True);self.assertEqual(r.returncode,0,r.stderr)
    (root/'core.apk').write_bytes(core)
    subprocess.run([str(ZIPALIGN),'-c','-P','16','4',str(root/'core.apk')],check=True,capture_output=True)
    with zipfile.ZipFile(io.BytesIO(core)) as z:
     self.assertEqual(z.read('assets/omni.ja'),b'gecko resource bytes');self.assertEqual(z.read('lib/arm64-v8a/libxul.so'),self.xul.read_bytes())
     for info in z.infolist():
      if info.filename.endswith('.so'):
       header=core[info.header_offset:info.header_offset+30];nl,el=struct.unpack_from('<HH',header,26)
       self.assertEqual((info.header_offset+30+nl+el)%16384,0);self.assertEqual(info.compress_type,zipfile.ZIP_STORED)
 def test_missing_native_dependency_does_not_publish(self):
  with tempfile.TemporaryDirectory() as t:
   root=Path(t);out=root/'out';r=self.pack(self.fixture(root,missing=True),out);self.assertNotEqual(r.returncode,0);self.assertIn('dependency',r.stderr.lower());self.assertFalse((out/'bridge.aar').exists())
 def test_duplicate_aar_entry_rejected(self):
  with tempfile.TemporaryDirectory() as t:
   root=Path(t);r=self.pack(self.fixture(root,duplicate=True),root/'out');self.assertNotEqual(r.returncode,0);self.assertIn('duplicate',r.stderr.lower())
if __name__=='__main__':unittest.main()
