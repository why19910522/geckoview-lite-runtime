#!/usr/bin/env python3
"""Gate the installed artifact rather than loader source text."""
import argparse,io,json,zipfile
p=argparse.ArgumentParser();p.add_argument('apk');a=p.parse_args()
with zipfile.ZipFile(a.apk) as z:
 assert [n for n in z.namelist() if n.endswith('.so')]==['lib/arm64-v8a/libgeckolite_bootstrap.so'], 'Unexpected embedded native payload'
 assert 'assets/omni.ja' not in z.namelist(), 'External APK still embeds Gecko resources'
 c=json.loads(z.read('assets/geckolite/host-contract.json'));assert c['schemaVersion']==1 and c['abi']=='arm64-v8a'
print('PASS: external APK has matching host contract and no embedded engine')
