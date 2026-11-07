import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from zipfile import ZipFile

SCRIPT = Path(__file__).resolve().parents[1] / 'ci/browser-cache.py'


class BrowserCacheTests(unittest.TestCase):
    def setUp(self):
        spec = importlib.util.spec_from_file_location('browser_cache', SCRIPT)
        self.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.module)

    def test_actual_resolved_dependency_metadata_controls_version(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); jar = root / 'unrelated-filename.jar'
            with ZipFile(jar, 'w') as archive:
                for artifact in ['playwright', 'driver', 'driver-bundle']:
                    archive.writestr('META-INF/maven/com.microsoft.playwright/'+artifact+'/pom.properties',
                                     'artifactId='+artifact+'\ngroupId=com.microsoft.playwright\nversion=1.64.0\n')
            self.assertEqual(self.module.version(str(jar)), '1.64.0')
            with self.assertRaises(ValueError): self.module.version('')
            with self.assertRaises(ValueError): self.module.version(str(jar)+':'+str(jar))

    def test_missing_or_unpinned_dependency_is_rejected(self):
        for value in ['1.64.0-SNAPSHOT', '${playwright.version}', '../1.64.0', '1.64.0\nother=secret']:
            with self.subTest(value=value), tempfile.TemporaryDirectory() as directory:
                jar = Path(directory) / 'playwright.jar'
                with ZipFile(jar, 'w') as archive:
                    for artifact in ['playwright', 'driver', 'driver-bundle']:
                        archive.writestr('META-INF/maven/com.microsoft.playwright/'+artifact+'/pom.properties',
                                         'artifactId='+artifact+'\ngroupId=com.microsoft.playwright\nversion='+value+'\n')
                if value.endswith('other=secret'):
                    # Other Maven metadata is allowed and never part of the output key.
                    self.assertEqual(self.module.version(str(jar)), '1.64.0')
                else:
                    with self.assertRaises(ValueError): self.module.version(str(jar))

    def test_mismatched_or_missing_driver_metadata_is_rejected(self):
        for missing in [True, False]:
            with self.subTest(missing=missing), tempfile.TemporaryDirectory() as directory:
                jar = Path(directory) / 'fixture.jar'
                with ZipFile(jar, 'w') as archive:
                    for artifact in ['playwright', 'driver', 'driver-bundle']:
                        if missing and artifact == 'driver-bundle': continue
                        version = '1.63.0' if artifact == 'driver-bundle' else '1.64.0'
                        archive.writestr('META-INF/maven/com.microsoft.playwright/'+artifact+'/pom.properties',
                                         'artifactId='+artifact+'\ngroupId=com.microsoft.playwright\nversion='+version+'\n')
                with self.assertRaises(ValueError): self.module.version(str(jar))

    def test_distribution_identity_is_read_as_data_for_versioned_and_rolling_platforms(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(self.module.platform, 'system', return_value='Linux'):
            path = Path(directory) / 'os-release'
            path.write_text('ID=ubuntu\nVERSION_ID="24.04"\n')
            self.assertEqual(self.module.os_release(path), 'ubuntu-24.04')
            path.write_text('ID=cachyos\nBUILD_ID=rolling\n')
            self.assertEqual(self.module.os_release(path), 'cachyos-rolling')
            path.write_text('ID=unknown\n')
            with self.assertRaises(ValueError): self.module.os_release(path)

    def test_platform_os_release_architecture_and_browser_changes_separate_keys(self):
        baseline = self.module.cache_key('1.64.0', 'linux', 'x86_64', 'ubuntu-24.04')
        for values in [('1.65.0', 'linux', 'x86_64', 'ubuntu-24.04'),
                       ('1.64.0', 'linux', 'aarch64', 'ubuntu-24.04'),
                       ('1.64.0', 'linux', 'x86_64', 'ubuntu-26.04'),
                       ('1.64.0', 'darwin', 'x86_64', 'macos-15')]:
            with self.subTest(values=values): self.assertNotEqual(baseline, self.module.cache_key(*values))
        self.assertEqual(baseline, self.module.cache_key('1.64.0', 'linux', 'x86_64', 'ubuntu-24.04'))
        with self.assertRaises(ValueError): self.module.cache_key('1.64.0', 'linux', 'x86_64', '../../unsafe')


if __name__ == '__main__':
    unittest.main()
