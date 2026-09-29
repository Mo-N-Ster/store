"""I01 smoke only on a dedicated synthetic emulator. Never targets STORE Desktop."""
from pathlib import Path
import json
import os
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
ADB = ROOT / '.tools/sdk/platform-tools/adb.exe'
SERIAL = os.environ.get('STORE_TEST_SERIAL', 'emulator-5554')
PACKAGE = 'com.vibe.store.debug'
OUT = ROOT / 'out'
EVIDENCE = ROOT / 'docs/evidence/I01R'
os.environ['ANDROID_USER_HOME'] = str(ROOT / '.android-home')

def adb(*args):
    p = subprocess.run([str(ADB), '-s', SERIAL, *args], capture_output=True, timeout=180)
    if p.returncode:
        raise RuntimeError(p.stderr.decode(errors='replace'))
    return p.stdout

def main():
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    assert adb('shell', 'getprop', 'sys.boot_completed').strip() == b'1'
    results = {'serial': SERIAL, 'api': adb('shell', 'getprop', 'ro.build.version.sdk').decode().strip(), 'runs': []}
    def healthy():
        events = adb('logcat', '-d', '-b', 'events', '-s', 'am_anr').decode(errors='replace')
        assert 'am_anr' not in events, 'ANR detected; do not dismiss or hide it: ' + events
        return events
    healthy()
    # No -r: do not overwrite an unexplained existing package/profile.
    assert not adb('shell', 'pm', 'list', 'packages', PACKAGE).strip(), 'Existing debug package; review before retry'
    assert b'Success' in adb('install', str(ROOT / 'app/build/outputs/apk/debug/app-debug.apk'))
    assert b'Success' in adb('install', str(ROOT / 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'))
    try:
        for width, size in [('COMPACT', '800x1800'), ('MEDIUM', '1280x1800'), ('EXPANDED', '1800x1400')]:
            adb('shell', 'cmd', 'uimode', 'night', 'yes' if width == 'MEDIUM' else 'no')
            adb('shell', 'settings', 'put', 'global', 'animator_duration_scale', '0' if width == 'EXPANDED' else '1')
            adb('shell', 'wm', 'density', '320')
            adb('shell', 'wm', 'size', size)
            adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
            adb('shell', 'wm', 'dismiss-keyguard')
            time.sleep(5)
            healthy()
            command = ['shell', 'am', 'instrument', '-w', '-e', 'expectedWidth', width,
                       PACKAGE + '.test/androidx.test.runner.AndroidJUnitRunner']
            text = adb(*command).decode(errors='replace')
            (OUT / f'instrumentation-{width}.log').write_text(text, encoding='utf-8')
            assert 'OK (3 tests)' in text and 'FAILURES' not in text, text
            adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/com.vibe.store.MainActivity')
            time.sleep(2)
            healthy()
            focus = adb('shell', 'dumpsys', 'window').decode(errors='replace')
            focused = [line for line in focus.splitlines() if 'mCurrentFocus=' in line]
            assert any(PACKAGE + '/com.vibe.store.MainActivity' in line for line in focused), focused
            assert 'Application Not Responding' not in focus
            (EVIDENCE / f'{width.lower()}.png').write_bytes(adb('exec-out', 'screencap', '-p'))
            results['runs'].append({'width': width, 'pixels': size, 'density': 320, 'tests': 3, 'fail': 0, 'skip': 0,
                                   'theme': 'dark' if width == 'MEDIUM' else 'light',
                                   'animator_scale': 0 if width == 'EXPANDED' else 1,
                                   'anr': False, 'app_focused': True, 'visual_review': 'required'})
            (EVIDENCE / 'native-results.json').write_text(json.dumps(results, indent=2), encoding='utf-8')
            adb('shell', 'am', 'force-stop', PACKAGE)
            print(width, 'PASS: 3 instrumentation tests and screenshot', flush=True)
    finally:
        adb('shell', 'wm', 'size', 'reset')
        adb('shell', 'wm', 'density', 'reset')
        adb('shell', 'cmd', 'uimode', 'night', 'no')
        adb('shell', 'settings', 'put', 'global', 'animator_duration_scale', '1')

if __name__ == '__main__':
    main()
