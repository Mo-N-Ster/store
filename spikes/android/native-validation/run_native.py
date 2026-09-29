"""Disposable adb orchestrator: synthetic package only; no STORE access."""
import json
import os
from pathlib import Path
import subprocess
import time
import uuid

ROOT = Path(__file__).resolve().parent
ADB = ROOT / '.tools/sdk/platform-tools/adb.exe'
PACKAGE = 'test.store.spike'
os.environ['ANDROID_USER_HOME'] = str(ROOT / '.android-home')
SERIAL = os.environ.get('SPIKE_SERIAL', 'emulator-5554')

def adb(*args, check=True):
    p = subprocess.run([str(ADB), '-s', SERIAL, *args], capture_output=True, timeout=40)
    if check and p.returncode:
        raise RuntimeError(p.stderr.decode(errors='replace'))
    return p.stdout

def read(path):
    try:
        return json.loads(adb('exec-out', 'run-as', PACKAGE, 'cat', 'files/' + path, check=False))
    except (ValueError, UnicodeError):
        return {}

def wait_result(predicate, path='last-result.json'):
    end = time.monotonic() + 120
    while time.monotonic() < end:
        result = read(path)
        if result.get('status') == 'FAIL':
            raise RuntimeError(result)
        if predicate(result):
            return result
        time.sleep(.4)
    raise TimeoutError((path, result))

def stop():
    adb('shell', 'am', 'force-stop', PACKAGE)
    assert not adb('shell', 'pidof', PACKAGE, check=False).strip()

def start(action, case, *extra):
    adb('shell', 'am', 'start', '-n', PACKAGE + '/.SpikeActivity',
        '--es', 'action', action, '--es', 'case', case, *extra)

def main():
    output = ROOT / 'out'
    output.mkdir(exist_ok=True)
    result = {'target': SERIAL, 'api': adb('shell', 'getprop', 'ro.build.version.sdk').decode().strip(),
              'abi': adb('shell', 'getprop', 'ro.product.cpu.abi').decode().strip(), 'cases': []}
    run = uuid.uuid4().hex[:10]
    for mode in ('fault', 'pause'):
        for point in range(1, 8):
            case = f'{run}-{mode}-{point}'
            stop()
            start('prepare', case, '--ei', 'point', str(point), '--es', 'mode', mode)
            phase = wait_result(lambda r: r.get('point') == point, f'cases/{case}/phase.json')
            original_pid = phase['pid']
            assert int(adb('shell', 'pidof', PACKAGE).strip()) == original_pid
            if mode == 'fault':
                wait_result(lambda r: r.get('status') == 'INJECTED' and r.get('pid') == original_pid)
            stop()  # external termination after durable phase, never Activity recreation
            recoveries = []
            for repeat in range(3):
                start('recover', case)
                recovered = wait_result(lambda r: r.get('case') == case and r.get('action') == 'recover'
                                        and r.get('pid') != original_pid
                                        and r.get('pid') not in [x['pid'] for x in recoveries])
                assert recovered['status'] == 'PASS'
                d = recovered['detail']
                assert d['generation'] == ('old' if point <= 5 else 'new')
                assert d['integrity'] == d['fk'] == 'ok'
                assert d['schema'] == 1 and d['parents'] == 1 and d['media'] == 2
                recoveries.append(recovered)
                stop()
            result['cases'].append({'mode': mode, 'point': point, 'killed_pid': original_pid,
                                    'recoveries': recoveries})
            (output / 'native-results.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
            print(f'{mode} point={point} PASS 3 restarts', flush=True)
    start('pdf', run + '-pdf')
    result['pdf'] = wait_result(lambda r: r.get('action') == 'pdf' and r.get('case') == run + '-pdf')
    for name in ('android-visible.pdf', 'android-structured.pdf'):
        (output / name).write_bytes(adb('exec-out', 'run-as', PACKAGE, 'cat', 'files/pdf/' + name))
    stop()
    (output / 'native-results.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
    print('SP01 14 cases / 42 recoveries PASS; SP02', result['pdf']['detail']['count'], 'groups PASS')

if __name__ == '__main__':
    main()
