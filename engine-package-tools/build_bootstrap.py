#!/usr/bin/env python3
"""Build the small bridge bootstrap using an explicitly selected Android NDK."""
import argparse, pathlib, subprocess
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--ndk',type=pathlib.Path,required=True)
p.add_argument('--output',type=pathlib.Path,required=True)
a=p.parse_args()
prebuilts=list((a.ndk/'toolchains/llvm/prebuilt').glob('*'))
if len(prebuilts)!=1:p.error('Expected one NDK host toolchain')
clang=prebuilts[0]/'bin/aarch64-linux-android26-clang'
a.output.parent.mkdir(parents=True,exist_ok=True)
subprocess.run([str(clang),'-shared','-Os','-fvisibility=hidden','-ffunction-sections','-fdata-sections','-Wall','-Wextra','-Wno-unused-parameter','-Werror','-Wl,--gc-sections','-Wl,--build-id=sha1','-Wl,-z,relro,-z,now,-z,noexecstack,-z,max-page-size=16384','-Wl,-soname,libgeckolite_bootstrap.so','-Wl,--strip-all',str(pathlib.Path(__file__).with_name('native')/'bootstrap.c'),'-ldl','-o',str(a.output)],check=True)
print(a.output)
