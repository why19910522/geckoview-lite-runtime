#!/usr/bin/env python3
"""Own test host only: reject foreign SONAMEs before selecting an installed core."""
import argparse,json,subprocess,time
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--ndk',type=Path,required=True)
p.add_argument('--adb',type=Path,required=True)
p.add_argument('--device',required=True)
p.add_argument('--output',type=Path,required=True)
a=p.parse_args();a.output.mkdir(parents=True,exist_ok=True)
app='dev.geckolite.smoke.external';base=[str(a.adb),'-s',a.device]
def cmd(args,**kw):return subprocess.check_output(base+args,text=True,**kw)
prebuilt=list((a.ndk/'toolchains/llvm/prebuilt').glob('*'))
if len(prebuilt)!=1:p.error('Select one NDK host toolchain')
source=a.output/'foreign.c';source.write_text('int geckolite_foreign_fixture(void) { return 731; }\n')
for name in ['libnss3.so','libmozglue.so','libfreebl3.so']:
 fixture=a.output/name
 subprocess.run([str(prebuilt[0]/'bin/aarch64-linux-android26-clang'),'-shared','-Os','-Wl,--build-id=sha1','-Wl,-z,max-page-size=16384','-Wl,-soname,'+name,str(source),'-o',str(fixture)],check=True)
 cmd(['shell','am','force-stop',app])
 cmd(['shell','run-as',app,'mkdir','-p','files/collision'])
 cmd(['shell','run-as',app,'rm','-f','files/collision/'+name,'files/probe-evidence.json'])
 with fixture.open('rb') as stream:
  subprocess.run(base+['exec-in','run-as',app,'dd','of=files/collision/'+name,'bs=65536'],stdin=stream,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,check=True)
 cmd(['shell','run-as',app,'chmod','444','files/collision/'+name])
 cmd(['shell','am','start','-n',app+'/dev.geckolite.smoke.SmokeActivity','--es','collisionLibrary',name])
 evidence={};until=time.monotonic()+15
 while time.monotonic()<until:
  try:evidence=json.loads(cmd(['exec-out','run-as',app,'cat','files/probe-evidence.json'],stderr=subprocess.DEVNULL))
  except (subprocess.CalledProcessError,json.JSONDecodeError):pass
  if 'prepareFailureCode' in evidence or 'prepareChecks' in evidence:break
  time.sleep(.25)
 (a.output/(name+'.json')).write_text(json.dumps(evidence,indent=2)+'\n')
 assert evidence.get('prepareFailureCode')=='CORE_LOAD_UNSUPPORTED' and evidence.get('core')=={},evidence
 pid=cmd(['shell','pidof',app]).strip()
 log=cmd(['logcat','-d','--pid='+pid,'-s','GeckoExternalProbe:E'])
 (a.output/(name+'.log')).write_text(log)
 assert 'Engine SONAME already loaded in host namespace' in log,log
 print('PASS:',name,'foreign fixture rejected by whole-closure preflight before selection',flush=True)
cmd(['shell','am','force-stop',app])
cmd(['shell','run-as',app,'rm','-rf','files/collision'])
