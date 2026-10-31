import json
from pathlib import Path
import subprocess
import tarfile
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'scripts/ci/deployment-bundle.py'
DIGEST = 'sha256:' + 'a' * 64
DATABASE_DIGEST = (ROOT / 'deploy/database-image').read_text().strip().split('@')[1]


class BundleTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.output = Path(self.temp.name)

    def run_bundle(self, *args):
        return subprocess.run(['python3', str(SCRIPT), *map(str, args)], capture_output=True, text=True)

    def create(self, digest=DIGEST, version='1.21.0'):
        return self.run_bundle('create', '--version', version, '--app-digest', digest,
                               '--database-digest', DATABASE_DIGEST, '--output', self.output)

    def test_archive_has_exact_image_identity_and_no_runtime_secrets(self):
        result = self.create()
        self.assertEqual(0, result.returncode, result.stderr)
        archive = self.output / 'ctc-manager-deployment-1.21.0.tar.gz'
        with tarfile.open(archive) as bundle:
            manifest = json.load(bundle.extractfile('ctc-manager-deployment-1.21.0/manifest.json'))
            self.assertEqual('ghcr.io/jegr78/ctc-manager@' + DIGEST, manifest['appImage'])
            names = bundle.getnames()
            self.assertTrue(any(name.endswith('/ctc-manager') for name in names))
            self.assertFalse(any(name.endswith('.env') or '/data/' in name for name in names))
        verified = self.run_bundle('verify', archive)
        self.assertEqual(0, verified.returncode, verified.stderr)

    def test_invalid_digest_rejected_before_output(self):
        result = self.create('latest')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('digest', result.stderr)
        self.assertEqual([], list(self.output.iterdir()))

    def test_snapshot_or_path_version_rejected(self):
        for value in ['1.21.0-SNAPSHOT', '../escape', '1.0.0\nmalicious']:
            result = self.create(version=value)
            self.assertNotEqual(0, result.returncode)
            self.assertIn('version', result.stderr)

    def test_changed_archive_content_refuses(self):
        self.assertEqual(0, self.create().returncode)
        archive = self.output / 'ctc-manager-deployment-1.21.0.tar.gz'
        raw = bytearray(archive.read_bytes())
        raw[len(raw)//2] ^= 255
        archive.write_bytes(raw)
        result = self.run_bundle('verify', archive)
        self.assertNotEqual(0, result.returncode)

    def test_extra_archive_entry_refuses(self):
        self.assertEqual(0, self.create().returncode)
        source = self.output / 'ctc-manager-deployment-1.21.0.tar.gz'
        extra = self.output / 'extra.tar.gz'
        with tarfile.open(source) as original, tarfile.open(extra, 'w:gz') as changed:
            for member in original:
                changed.addfile(member, original.extractfile(member))
            entry = tarfile.TarInfo('../escape')
            entry.size = 0
            changed.addfile(entry)
        result = self.run_bundle('verify', extra)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('archive', result.stderr)

    def test_retry_produces_same_inventory(self):
        self.assertEqual(0, self.create().returncode)
        path = self.output / 'ctc-manager-deployment-1.21.0.tar.gz'
        first_bytes = path.read_bytes()
        with tarfile.open(path) as bundle:
            first = bundle.extractfile('ctc-manager-deployment-1.21.0/manifest.json').read()
        self.assertEqual(0, self.create().returncode)
        with tarfile.open(path) as bundle:
            second = bundle.extractfile('ctc-manager-deployment-1.21.0/manifest.json').read()
        self.assertEqual(first, second)
        self.assertEqual(first_bytes, path.read_bytes())
