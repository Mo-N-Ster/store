"""I01-only source/platform checks; no production data or Android DB access."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = '{http://schemas.android.com/apk/res/android}'

class FoundationContract(unittest.TestCase):
    def test_presentation_has_no_privileged_imports(self):
        for file in (ROOT / 'presentation/src').rglob('*.kt'):
            imports = re.findall(r'^import (.+)$', file.read_text(encoding='utf-8'), re.M)
            forbidden = ('androidx.room', 'androidx.sqlite', 'java.sql', 'java.security',
                         'android.security', 'com.vibe.store.infrastructure',
                         'com.vibe.store.application.', 'com.vibe.store.domain')
            self.assertFalse([x for x in imports if x.startswith(forbidden)], str(file))

    def test_no_business_database_schema(self):
        for module in ('app', 'infrastructure', 'domain', 'application'):
            for file in (ROOT / module / 'src/main').rglob('*.kt'):
                text = file.read_text(encoding='utf-8')
                self.assertNotRegex(text, r'@(Database|Entity|Dao)\b|fallbackToDestructiveMigration|CREATE TABLE')
        wiring = (ROOT / 'infrastructure/src/main/kotlin/com/vibe/store/infrastructure/RoomDriverWiring.kt').read_text()
        self.assertIn('builder.setDriver(driver)', wiring)
        self.assertIn('BundledSQLiteDriver()', wiring)
        self.assertNotIn('.open(', wiring)
        gradle = (ROOT / 'infrastructure/build.gradle.kts').read_text()
        self.assertIn('ksp(libs.room.compiler)', gradle)
        self.assertNotIn('annotationProcessor', gradle)

    def test_backup_transfer_excludes_every_domain(self):
        directory = ROOT / 'app/src/main/res/xml'
        expected = {'root', 'file', 'database', 'sharedpref', 'external', 'device_root',
                    'device_file', 'device_database', 'device_sharedpref'}
        legacy = ET.parse(directory / 'backup_rules.xml').getroot()
        modern = ET.parse(directory / 'data_extraction_rules.xml').getroot()
        for rules in (legacy, modern.find('cloud-backup'), modern.find('device-transfer')):
            self.assertIsNotNone(rules)
            self.assertEqual(expected, {e.attrib['domain'] for e in rules.findall('exclude')})
            self.assertTrue(all(e.attrib['path'] == '.' for e in rules))

    def test_manifest_has_only_nonprivileged_launcher(self):
        manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot()
        self.assertFalse(manifest.findall('uses-permission'))
        app = manifest.find('application')
        self.assertEqual('false', app.get(ANDROID + 'allowBackup'))
        self.assertEqual('false', app.get(ANDROID + 'usesCleartextTraffic'))
        self.assertEqual('@xml/backup_rules', app.get(ANDROID + 'fullBackupContent'))
        self.assertEqual('@xml/data_extraction_rules', app.get(ANDROID + 'dataExtractionRules'))
        self.assertEqual(['activity'], [e.tag for e in app])
        self.assertEqual('.MainActivity', app[0].get(ANDROID + 'name'))
        self.assertEqual('true', app[0].get(ANDROID + 'exported'))
        activity = (ROOT / 'app/src/main/kotlin/com/vibe/store/MainActivity.kt').read_text()
        self.assertNotRegex(activity, r'get[A-Za-z]*Extra|intent\.data|openDatabase|execSQL')

    def test_locale_key_parity(self):
        directory = ROOT / 'presentation/src/main/res'
        en = ET.parse(directory / 'values/strings.xml').getroot()
        fr = ET.parse(directory / 'values-fr/strings.xml').getroot()
        self.assertEqual({e.get('name') for e in en}, {e.get('name') for e in fr})
        self.assertTrue(all(e.text and e.text.strip() for e in en))
        self.assertTrue(all(e.text and e.text.strip() for e in fr))

if __name__ == '__main__':
    unittest.main(verbosity=2)
