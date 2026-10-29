#!/usr/bin/env python3
"""Host probes over actual no-Glean source fragments; Android ABI is checked by the full build."""
import argparse
from pathlib import Path
import re
import subprocess
import tempfile

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--source-root', type=Path, required=True)
p.add_argument('--rustc', default='rustc')
p.add_argument('--cxx', default='c++')
a = p.parse_args()

def function(source, signature):
    start = source.index(signature)
    brace = source.index('{', start)
    depth = 0
    for end in range(brace, len(source)):
        if source[end] == '{':
            depth += 1
        elif source[end] == '}':
            depth -= 1
            if depth == 0:
                return source[start:brace], source[brace:end + 1]
    raise AssertionError('Unclosed source function: ' + signature)

with tempfile.TemporaryDirectory(prefix='no-glean-shims-') as td:
    td = Path(td)
    api = (a.source_root / 'toolkit/components/glean/api/src/lib.rs').read_text()
    rust = td / 'metrics.rs'
    rust.write_text(api[:api.index('pub mod metrics {')] + '#[test] fn null_metrics_have_no_storage() {\n use private::*;\n assert_eq!(std::mem::size_of::<NullMetric>(), 0);\n assert_eq!(std::mem::size_of::<NullLabeledMetric>(), 0);\n assert_eq!(std::mem::size_of::<NullDualLabeledMetric>(), 0);\n assert_eq!(std::mem::size_of::<LocalDistribution>(), 0);\n assert_eq!(std::mem::size_of::<TimerId>(), 0);\n let m = NullMetric;\n m.add(123); m.set("private value");\n m.accumulate(u64::MAX); m.accumulate_single_sample_signed(-1);\n m.accumulate_raw_duration(std::time::Duration::MAX);\n m.stop_and_accumulate(m.start());m.cancel(m.start());\n let mut local=m.start_buffer();local.accumulate(42);local.accumulate_single_sample_signed(-1);\n NullLabeledMetric.get("private-label").add(1);\n NullDualLabeledMetric.get("key","category").add(1);\n NullPing.submit(Some("test"));\n}\n')
    subprocess.run([a.rustc, '--edition=2021', '--test', str(rust), '-o', str(td / 'metrics')], check=True)
    subprocess.run([str(td / 'metrics')], check=True)
    fog = (a.source_root / 'toolkit/components/glean/xpcom/FOG.cpp').read_text()
    _, body = function(fog, 'FOG::InitializeFOG(')
    p = td / 'init.cpp'
    p.write_text('''#include <string>
#include <cassert>
using nsACString=std::string;
using nsresult=int;
constexpr int NS_OK=0;
#define MOZ_ASSERT(x) assert(x)
#define NS_ENSURE_SUCCESS(value, ret) do { if(value) return ret; } while(0)
bool XRE_IsParentProcess(){return true;}
bool gInitializeCalled=false;
int registeredCallbacks=0;
enum class ShutdownPhase { AppShutdownConfirmed };
struct AppShutdown { static bool IsInOrBeyond(ShutdownPhase){return false;} };
template<class F> void RunOnShutdown(F,ShutdownPhase){++registeredCallbacks;}
struct Preferences { static bool GetBool(const char*,bool){return false;} };
namespace glean::impl {
 void fog_internal_glean_handle_client_inactive(){}
 int fog_init(const nsACString*,const nsACString*,bool){return NS_OK;}
}
namespace xpc { bool IsInAutomation(){return false;} }
bool ApplyInterestingServerKnobs(){return true;}
nsresult InitializeFOG(const nsACString& aDataPathOverride,const nsACString& aAppIdOverride,const bool aDisableInternalPings)
'''+body+'''
int main(){
 assert(InitializeFOG("private-profile", "private-app",false)==NS_OK);
 assert(registeredCallbacks==0 && "Glean-disabled initialization registered a lifecycle callback");
 assert(!gInitializeCalled && "Glean-disabled service claims backend initialized");
}
''')
    subprocess.run([a.cxx, '-std=c++17', str(p), '-o', str(td / 'init')], check=True)
    subprocess.run([str(td / 'init')], check=True)
    power = (a.source_root / 'toolkit/components/glean/ipc/FOGIPC.cpp').read_text()
    signature, body = function(power, 'void RecordPowerMetrics()')
    output = subprocess.check_output([a.cxx, '-E', '-P', '-x', 'c++', '-'], input=signature + body, text=True)
    assert re.fullmatch(r'void\s+RecordPowerMetrics\(\)\s*\{\s*\}', output.strip()), 'Power collection still active'
print('PASS: zero-state Rust metrics; no FOG init/callback; no Glean CPU/GPU collection')
