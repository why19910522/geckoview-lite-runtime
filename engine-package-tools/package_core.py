#!/usr/bin/env python3
"""Split one matched GeckoView AAR; no network, download URLs, or stored signing keys."""
import argparse
import base64
import hashlib
import io
import json
from pathlib import Path
import re
import shutil
import struct
import subprocess
import tempfile
import zipfile

SYSTEM_LIBS = {'libc.so','libm.so','libdl.so','liblog.so','libandroid.so','libmediandk.so','libGLESv2.so','libGLESv3.so','libEGL.so','libjnigraphics.so','libsync.so','libz.so','libOpenSLES.so'}
MAX_CORE = 512 * 1024 * 1024

def sha(data):
    return hashlib.sha256(data).hexdigest()

def encoded(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), ensure_ascii=False).encode('utf-8')

def elf_info(data):
    if len(data) < 64 or data[:6] != b'\x7fELF\x02\x01' or struct.unpack_from('<H', data, 18)[0] != 183:
        raise ValueError('Only little-endian arm64 ELF64 is supported')
    phoff = struct.unpack_from('<Q', data, 32)[0]
    entsize, count = struct.unpack_from('<HH', data, 54)
    if entsize != 56 or count > 128 or phoff + count * 56 > len(data):
        raise ValueError('Invalid ELF program headers')
    headers = [struct.unpack_from('<IIQQQQQQ', data, phoff+i*56) for i in range(count)]
    loads = [h for h in headers if h[0] == 1]
    if not loads or any(h[7] < 16384 or h[2] + h[5] > len(data) for h in loads):
        raise ValueError('ELF must support 16KB pages')
    dynamic = next((h for h in headers if h[0] == 2), None)
    values = []
    if dynamic:
        if dynamic[2]+dynamic[5] > len(data) or dynamic[5] % 16:
            raise ValueError('Invalid ELF dynamic table')
        for offset in range(dynamic[2], dynamic[2]+dynamic[5], 16):
            tag, value = struct.unpack_from('<qQ', data, offset)
            if tag == 0: break
            values.append((tag, value))
    strings = next((value for tag, value in values if tag == 5), None)
    strsize = next((value for tag, value in values if tag == 10), None)
    def string(index):
        if strings is None or strsize is None or index >= strsize:
            raise ValueError('Missing ELF string table')
        load = next((h for h in loads if h[3] <= strings < h[3]+h[5]), None)
        if not load: raise ValueError('Invalid ELF strings')
        offset = load[2]+strings-load[3]+index
        end = data.find(b'\0', offset, min(len(data), offset+strsize-index))
        if end < 0: raise ValueError('Unterminated ELF string')
        return data[offset:end].decode('ascii')
    needed = [string(value) for tag, value in values if tag == 1]
    soname = next((string(value) for tag, value in values if tag == 14), None)
    build_id = None
    for h in headers:
        if h[0] != 4: continue
        off, end = h[2], h[2]+h[5]
        while off+12 <= end and end <= len(data):
            namesz, descsz, kind = struct.unpack_from('<III', data, off);off += 12
            name = data[off:off+namesz];off += (namesz+3)&~3
            desc = data[off:off+descsz];off += (descsz+3)&~3
            if kind == 3 and name == b'GNU\0':build_id = desc.hex()
    if not build_id: raise ValueError('ELF build-id required')
    # A defined public export lets the loader verify dlsym/dladdr provenance
    # using public NDK APIs; never rely on a SONAME-only dlopen result.
    shoff = struct.unpack_from('<Q', data, 40)[0]
    shsize, shcount = struct.unpack_from('<HH', data, 58)
    anchors = []
    if shcount:
        if shsize != 64 or shoff + shcount * shsize > len(data):
            raise ValueError('Invalid ELF section headers')
        sections = [struct.unpack_from('<IIQQQQIIQQ', data, shoff+i*shsize) for i in range(shcount)]
        for section in sections:
            if section[1] != 11:continue
            if section[9] != 24 or section[5] % 24 or section[4]+section[5] > len(data) or section[6] >= shcount:
                raise ValueError('Invalid ELF dynamic symbols')
            strings_section = sections[section[6]]
            start, length = strings_section[4:6]
            if start+length > len(data):raise ValueError('Invalid ELF symbol strings')
            for pos in range(section[4], section[4]+section[5], 24):
                index, info, visibility, defined, value, size = struct.unpack_from('<IBBHQQ', data, pos)
                if info >> 4 != 1 or info & 15 not in (1,2) or visibility & 3 not in (0,3) or not defined or not value or not size:continue
                if index >= length:raise ValueError('Invalid ELF symbol name')
                end = data.find(b'\0', start+index, start+length)
                if end < 0:raise ValueError('Unterminated ELF symbol')
                name = data[start+index:end].decode('ascii')
                if re.fullmatch('[A-Za-z_][A-Za-z0-9_]{0,200}', name):anchors.append(name)
    anchor = min(anchors, key=lambda n:(n.startswith('_'), n)) if anchors else None
    return {'anchor':anchor,'needed':needed,'soname':soname,'executable':any(h[0] == 3 for h in headers),'buildId':build_id,'minimumLoadAlignment':min(h[7] for h in loads)}

def checked_entries(z):
    seen = set()
    for e in z.infolist():
        if e.filename in seen: raise ValueError('Duplicate ZIP entry')
        seen.add(e.filename)
        if e.filename.startswith('/') or '\\' in e.filename or '..' in e.filename.split('/'):
            raise ValueError('Unsafe ZIP path')
        if (e.external_attr >> 16) & 0o170000 == 0o120000: raise ValueError('ZIP symlink rejected')
        if e.flag_bits & 1: raise ValueError('Encrypted ZIP rejected')
        if e.file_size > MAX_CORE: raise ValueError('ZIP entry too large')
    if sum(e.file_size for e in z.infolist()) > MAX_CORE: raise ValueError('ZIP too large')

def add_aligned(z, name, data, alignment=4):
    info = zipfile.ZipInfo(name, (2026,1,1,0,0,0))
    info.compress_type = zipfile.ZIP_STORED
    info.external_attr = 0o100444 << 16
    offset = z.fp.tell()+30+len(name.encode('utf-8'))
    pad = (-offset) % alignment
    if pad and pad < 4:pad += alignment
    if pad:info.extra = struct.pack('<HH', 0xffff, pad-4) + b'\0'*(pad-4)
    z.writestr(info, data)

def split(aar, output, key, key_id, revision, profile, sequence, pom=None, bootstrap=None, resource_fd=900):
    if not 64 <= resource_fd <= 1023:raise ValueError('Resource FD must be between 64 and 1023')
    if not re.fullmatch('[A-Za-z0-9._-]{1,32}', key_id):raise ValueError('Invalid key ID')
    if not re.fullmatch('[a-f0-9]{40}', revision):raise ValueError('Invalid revision')
    if not re.fullmatch('[A-Za-z0-9-]{1,80}', profile):raise ValueError('Invalid profile')
    if not 1 <= sequence <= 2147483647:raise ValueError('Invalid release sequence')
    curve = subprocess.check_output(['openssl','pkey','-in',str(key),'-text','-noout'],stderr=subprocess.DEVNULL)
    if b'prime256v1' not in curve:raise ValueError('P-256 signing key required')
    if output.exists():raise ValueError('Output must be a new directory')
    with zipfile.ZipFile(aar) as z:
        checked_entries(z)
        if 'assets/geckolite/host-contract.json' in z.namelist():raise ValueError('Input is already a bridge')
        if 'classes.jar' not in z.namelist() or 'AndroidManifest.xml' not in z.namelist():raise ValueError('AAR bridge entries missing')
        bridge = {e.filename:z.read(e) for e in z.infolist() if not e.is_dir() and not e.filename.startswith('jni/') and e.filename != 'assets/omni.ja'}
        native = {}
        for e in z.infolist():
            if e.is_dir() or not e.filename.startswith('jni/'):continue
            if not re.fullmatch(r'jni/arm64-v8a/lib[a-zA-Z0-9_-]+\.so', e.filename):raise ValueError('Unsupported native entry/ABI')
            native[e.filename.replace('jni/','lib/',1)] = z.read(e)
        if not {'lib/arm64-v8a/libmozglue.so','lib/arm64-v8a/libxul.so'} <= native.keys():raise ValueError('Native loader dependency missing')
        payload = {**native, 'assets/omni.ja':z.read('assets/omni.ja')}
    libraries = {name:elf_info(data) for name, data in native.items()}
    names = {Path(n).name for n in libraries}
    for name, info in libraries.items():
        if info['soname'] != Path(name).name and not (Path(name).name == 'libplugin-container.so' and info['soname'] is None and info['executable']):raise ValueError('ELF SONAME mismatch')
        missing = set(info['needed']) - names - SYSTEM_LIBS
        if missing:raise ValueError('Missing native dependency: '+','.join(sorted(missing)))
    if bootstrap is None:raise ValueError('Bridge bootstrap required')
    bootstrap_data=bootstrap.read_bytes();bootstrap_info=elf_info(bootstrap_data)
    if bootstrap_info['soname'] != 'libgeckolite_bootstrap.so' or set(bootstrap_info['needed'])-SYSTEM_LIBS:
        raise ValueError('Invalid bridge bootstrap')
    bridge['jni/arm64-v8a/libgeckolite_bootstrap.so']=bootstrap_data
    # Hash all non-engine entries as a length-framed contract; includes classes,
    # generated BuildConfig/JNI signatures, Android resources and service manifest.
    h = hashlib.sha256()
    for name, data in sorted(bridge.items()):
        nb=name.encode();h.update(struct.pack('>I',len(nb)));h.update(nb);h.update(hashlib.sha256(data).digest())
    fingerprint=h.hexdigest()
    # The app ClassLoader has no core ZIP search directory. Explicitly load the
    # dependency closure before XUL; never fall back to a second copy in the APK.
    native_order=[];visiting=set();done=set()
    by_name={Path(n).name:info for n,info in libraries.items()}
    def visit(name):
        if name in done:return
        if name in visiting:raise ValueError('Cyclic native dependency')
        visiting.add(name)
        for dep in by_name[name]['needed']:
            if dep in by_name:visit(dep)
        visiting.remove(name);done.add(name)
        if not by_name[name]['executable']:native_order.append(name)
    for name in sorted(by_name):visit(name)
    if any(not by_name[n]['anchor'] for n in native_order):raise ValueError('Native provenance export required')
    with tempfile.TemporaryDirectory(prefix='geckolite-pack-') as td:
        td=Path(td);core=td/'core.apk'
        with zipfile.ZipFile(core,'w',allowZip64=False) as z:
            for name, data in sorted(payload.items()):add_aligned(z,name,data,16384 if name.endswith('.so') else 4)
        core_data=core.read_bytes()
        if len(core_data)>MAX_CORE:raise ValueError('Core container too large')
        digest=sha(core_data)
        manifest={'schemaVersion':1,'abi':'arm64-v8a','minApi':26,'pageAlignment':16384,'revision':revision,'profile':profile,'releaseSequence':sequence,'bridgeFingerprint':fingerprint,'coreSha256':digest,'coreSize':len(core_data),'buildId':libraries['lib/arm64-v8a/libxul.so']['buildId'],'files':[{'path':name,'size':len(data),'sha256':sha(data),**({'elf':libraries[name]} if name in libraries else {})} for name,data in sorted(payload.items())]}
        raw=encoded(manifest)
        if len(raw)>65536:raise ValueError('Manifest too large')
        (td/'manifest.json').write_bytes(raw)
        subprocess.run(['openssl','dgst','-sha256','-sign',str(key),'-out',str(td/'signature'),str(td/'manifest.json')],check=True,capture_output=True)
        signature=encoded({'keyId':key_id,'signature':base64.b64encode((td/'signature').read_bytes()).decode()})
        contract={k:manifest[k] for k in ['schemaVersion','abi','minApi','pageAlignment','revision','profile','releaseSequence','bridgeFingerprint','coreSha256','coreSize','buildId']}
        contract['manifestSha256']=sha(raw)
        contract['resourceFd']=resource_fd
        contract['nativeLoadOrder']=native_order
        contract['nativeAnchors']={n:by_name[n]['anchor'] for n in native_order}
        with zipfile.ZipFile(core) as packed:
            contract['nativeOffsets']={Path(e.filename).name:e.header_offset+30+len(e.filename.encode())+len(e.extra) for e in packed.infolist() if e.filename.endswith('.so')}
        with zipfile.ZipFile(td/'bridge.aar','w',zipfile.ZIP_DEFLATED) as z:
            for name,data in sorted(bridge.items()):z.writestr(name,data)
            z.writestr('assets/geckolite/host-contract.json',encoded(contract))
        with zipfile.ZipFile(td/'core-package.zip','w',zipfile.ZIP_DEFLATED) as z:
            z.writestr('manifest.json',raw);z.writestr('manifest.sig',signature);z.write(core,'core.apk')
        (td/'host-contract.json').write_bytes(encoded(contract)+b'\n')
        output.mkdir(parents=True)
        for name in ['bridge.aar','core-package.zip','host-contract.json']:shutil.copyfile(td/name,output/name)
    if pom is not None:
        import xml.etree.ElementTree as ET
        ns='http://maven.apache.org/POM/4.0.0'; ET.register_namespace('',ns)
        tree=ET.parse(pom);root=tree.getroot()
        group=root.find('{'+ns+'}groupId').text
        artifact=root.find('{'+ns+'}artifactId');artifact.text += '-bridge'
        version=root.find('{'+ns+'}version').text
        if not all(re.fullmatch('[A-Za-z0-9_.-]+',x) for x in [group,artifact.text,version]):raise ValueError('Invalid Maven identity')
        destination=output/'maven'/Path(*group.split('.'))/artifact.text/version;destination.mkdir(parents=True)
        prefix=artifact.text+'-'+version
        tree.write(destination/(prefix+'.pom'),encoding='utf-8',xml_declaration=True)
        shutil.copyfile(output/'bridge.aar',destination/(prefix+'.aar'))
    return {'packageId':sha(raw),'bridgeFingerprint':fingerprint,'coreSha256':digest,'nativeLibraries':len(libraries),'inputAarSha256':sha(aar.read_bytes())}

def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['aar','output','private-key']:p.add_argument('--'+name,type=Path,required=True)
    for name in ['key-id','revision','profile']:p.add_argument('--'+name,required=True)
    p.add_argument('--sequence',type=int,required=True);p.add_argument('--pom',type=Path);p.add_argument('--bootstrap',type=Path,required=True);p.add_argument('--resource-fd',type=int,default=900);a=p.parse_args()
    try:result=split(a.aar,a.output,a.private_key,a.key_id,a.revision,a.profile,a.sequence,a.pom,a.bootstrap,a.resource_fd)
    except (ValueError,KeyError,zipfile.BadZipFile,subprocess.CalledProcessError) as error:p.exit(1,'package rejected: '+str(error)+'\n')
    print(json.dumps(result,sort_keys=True))
if __name__=='__main__':main()
