import json
import hashlib
import io
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

    def test_changed_file_with_valid_tar_refuses_checksum(self):
        self.assertEqual(0, self.create().returncode)
        source = self.output / 'ctc-manager-deployment-1.21.0.tar.gz'
        changed = self.output / 'changed.tar.gz'
        import io
        with tarfile.open(source) as original, tarfile.open(changed, 'w:gz') as target:
            for member in original:
                content = original.extractfile(member).read()
                if member.name.endswith('/config.example'):
                    content += b'\n'
                    member.size = len(content)
                target.addfile(member,io.BytesIO(content))
        result = self.run_bundle('verify',changed)
        self.assertNotEqual(0,result.returncode)
        self.assertIn('checksum',result.stderr)

    def test_previously_untagged_database_reference_remains_verifiable(self):
        self.assertEqual(0, self.create().returncode)
        archive = self.output / 'ctc-manager-deployment-1.21.0.tar.gz'
        with tarfile.open(archive) as bundle:
            contents = {member.name: bundle.extractfile(member).read() for member in bundle}
        prefix = 'ctc-manager-deployment-1.21.0/'
        contents[prefix+'database-image'] = ('mariadb@'+DATABASE_DIGEST+'\n').encode()
        manifest = json.loads(contents[prefix+'manifest.json'])
        manifest['files']['database-image'] = hashlib.sha256(contents[prefix+'database-image']).hexdigest()
        contents[prefix+'manifest.json'] = json.dumps(manifest).encode()
        legacy = self.output / 'legacy.tar.gz'
        with tarfile.open(legacy, 'w:gz') as bundle:
            for name, content in contents.items():
                entry = tarfile.TarInfo(name)
                entry.size = len(content)
                bundle.addfile(entry,io.BytesIO(content))
        result = self.run_bundle('verify',legacy)
        self.assertEqual(0,result.returncode,result.stderr)

    def test_private_maintenance_tools_and_all_user_units_are_packaged(self):
        self.assertEqual(0, self.create().returncode)
        with tarfile.open(self.output/'ctc-manager-deployment-1.21.0.tar.gz') as bundle:
            names=set(bundle.getnames())
            for name in ['lib/maintenance.py','lib/maintenance.sh','guides/backups.md',
                         'systemd/ctc-manager-backup.service','systemd/ctc-manager-backup.timer',
                         'systemd/ctc-manager-backup-check.service','systemd/ctc-manager-backup-check.timer',
                         'systemd/ctc-manager-backup-alert@.service']:
                self.assertIn('ctc-manager-deployment-1.21.0/'+name,names)

    def test_host_setup_and_guide_are_packaged(self):
        self.assertEqual(0,self.create().returncode)
        with tarfile.open(self.output/'ctc-manager-deployment-1.21.0.tar.gz') as bundle:
            prefix='ctc-manager-deployment-1.21.0/'
            for name in ['setup-host.sh','lib/host.py','systemd/docker-rootless.service','guides/host.md']:
                self.assertIn(prefix+name,bundle.getnames())
            self.assertTrue(bundle.getmember(prefix+'setup-host.sh').mode & 0o100)

    def test_legacy_bundle_inventory_remains_verifiable(self):
        self.assertEqual(0,self.create().returncode)
        prefix='ctc-manager-deployment-1.21.0/'
        with tarfile.open(self.output/'ctc-manager-deployment-1.21.0.tar.gz') as bundle:
            contents={member.name:bundle.extractfile(member).read() for member in bundle}
        manifest=json.loads(contents[prefix+'manifest.json'])
        core = {'ctc-manager','compose.yaml','config.example','lib/common.sh','lib/config.py','README.md',
                'database-image','release-manifest.schema.json'}
        for name in set(manifest['files']) - core:
            contents.pop(prefix+name,None)
            manifest['files'].pop(name,None)
        contents[prefix+'manifest.json']=json.dumps(manifest).encode()
        archive=self.output/'legacy-inventory.tar.gz'
        with tarfile.open(archive,'w:gz') as bundle:
            for name,content in contents.items():
                entry=tarfile.TarInfo(name);entry.size=len(content)
                bundle.addfile(entry,io.BytesIO(content))
        result=self.run_bundle('verify',archive)
        self.assertEqual(0,result.returncode,result.stderr)

    def test_partial_host_inventory_is_rejected_even_with_matching_checksums(self):
        self.assertEqual(0,self.create().returncode)
        prefix='ctc-manager-deployment-1.21.0/'
        with tarfile.open(self.output/'ctc-manager-deployment-1.21.0.tar.gz') as bundle:
            contents={member.name:bundle.extractfile(member).read() for member in bundle}
        manifest=json.loads(contents[prefix+'manifest.json'])
        contents.pop(prefix+'guides/host.md')
        manifest['files'].pop('guides/host.md')
        contents[prefix+'manifest.json']=json.dumps(manifest).encode()
        archive=self.output/'partial-inventory.tar.gz'
        with tarfile.open(archive,'w:gz') as bundle:
            for name,content in contents.items():
                entry=tarfile.TarInfo(name);entry.size=len(content)
                bundle.addfile(entry,io.BytesIO(content))
        result=self.run_bundle('verify',archive)
        self.assertNotEqual(0,result.returncode)
        self.assertIn('inventory',result.stderr)

    def test_recovery_tools_and_operator_guide_are_packaged(self):
        self.assertEqual(0, self.create().returncode)
        with tarfile.open(self.output / 'ctc-manager-deployment-1.21.0.tar.gz') as bundle:
            names = set(bundle.getnames())
        for name in ['lib/runtime.py', 'lib/release.py', 'lib/recovery.py', 'lib/recovery.sh',
                     'lib/probe.py', 'lib/live.py', 'recovery-manifest.schema.json', 'guides/recovery.md']:
            self.assertIn('ctc-manager-deployment-1.21.0/' + name, names)

    def test_update_tools_and_upgrade_contract_are_packaged(self):
        self.assertEqual(0, self.create().returncode)
        with tarfile.open(self.output / 'ctc-manager-deployment-1.21.0.tar.gz') as bundle:
            prefix = 'ctc-manager-deployment-1.21.0/'
            names = set(bundle.getnames())
            for name in ['lib/update.py', 'lib/update.sh', 'upgrade.json', 'guides/updates.md']:
                self.assertIn(prefix + name, names)
            contract = json.load(bundle.extractfile(prefix + 'upgrade.json'))
            self.assertEqual(1, contract['recoveryCapability'])
            self.assertEqual('guides/updates.md', contract['notes'])
