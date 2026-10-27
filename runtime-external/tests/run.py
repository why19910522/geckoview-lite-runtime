#!/usr/bin/env python3
"""Compile and exercise the platform-independent installer against a real signed pair."""
import argparse,subprocess,tempfile
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--pair',type=Path,required=True);p.add_argument('--public-key',type=Path,required=True);p.add_argument('--java-home',type=Path,required=True);a=p.parse_args()
root=Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix='core-install-test-') as temp:
 d=Path(temp);sources=[root/'src/main/java/dev/geckolite/runtime'/n for n in ['CoreException.java','StrictJson.java','CorePackages.java']]+[root/'tests/RuntimeTest.java',root/'tests/InstallWorker.java']
 subprocess.run([str(a.java_home/'bin/javac'),'-d',str(d/'classes'),*map(str,sources)],check=True)
 subprocess.run([str(a.java_home/'bin/java'),'-cp',str(d/'classes'),'dev.geckolite.runtime.RuntimeTest',str(a.pair.resolve()),str(a.public_key.resolve()),str(d/'store')],check=True)

 # Concurrent JVMs must converge to one complete installed version.
 command=[str(a.java_home/'bin/java'),'-cp',str(d/'classes'),'dev.geckolite.runtime.InstallWorker',str(a.pair.resolve()),str(a.public_key.resolve()),str(d/'parallel-store')]
 workers=[subprocess.Popen(command,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True) for _ in range(2)]
 results=[worker.communicate(timeout=120) for worker in workers]
 assert all(worker.returncode==0 for worker in workers),results
 assert results[0][0]==results[1][0],results
 subprocess.run(command+['validate-only'],check=True,stdout=subprocess.DEVNULL)
 print('PASS: two concurrent JVM installers converge to one verified package with no staging leftovers')
