"""Check actual merged debug/release surfaces, not just the source manifest."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
A = '{http://schemas.android.com/apk/res/android}'

for variant, package in [('debug', 'com.vibe.store.debug'), ('release', 'com.vibe.store')]:
    path = ROOT / f'app/build/intermediates/merged_manifests/{variant}/process{variant.title()}Manifest/AndroidManifest.xml'
    manifest = ET.parse(path).getroot()
    assert manifest.get('package') == package
    sdk = manifest.find('uses-sdk')
    assert sdk.get(A + 'minSdkVersion') == '23'
    assert sdk.get(A + 'targetSdkVersion') == '36'
    app = manifest.find('application')
    assert app.get(A + 'allowBackup') == 'false'
    assert app.get(A + 'usesCleartextTraffic') == 'false'
    assert app.get(A + 'fullBackupContent') == '@xml/backup_rules'
    assert app.get(A + 'dataExtractionRules') == '@xml/data_extraction_rules'
    assert (app.get(A + 'debuggable') == 'true') == (variant == 'debug')
    exported = {(e.tag, e.get(A + 'name'), e.get(A + 'permission'))
                for e in app if e.get(A + 'exported') == 'true'}
    # AndroidX's known profiling endpoint is shell/system protected, not a
    # STORE command endpoint; no third-party caller receives this privilege.
    assert exported == {
        ('activity', 'com.vibe.store.MainActivity', None),
        ('receiver', 'androidx.profileinstaller.ProfileInstallReceiver', 'android.permission.DUMP'),
    }, exported
    permission = package + '.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'
    assert {e.get(A + 'name') for e in manifest.findall('uses-permission')} == {permission}
    assert [(e.get(A + 'name'), e.get(A + 'protectionLevel'))
            for e in manifest.findall('permission')] == [(permission, 'signature')]
    print(variant, 'PASS: identity, SDK, backup, cleartext, debuggability, exported surface')
