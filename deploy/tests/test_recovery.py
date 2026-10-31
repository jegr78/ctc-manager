import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import sys
import tarfile
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'deploy/lib'))
import recovery


class RecoveryUnitTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.unit = Path(self.temp.name).resolve() / 'unit'
        self.unit.mkdir(mode=0o700)
        self.write_tar([('uploads/logo.png', b'fixture graphic', tarfile.REGTYPE)])
        for name, value in {
            'database.sql': b'CREATE TABLE fixture(id INT); INSERT INTO fixture VALUES(7);',
            'installation.json': b'{}', 'app.env': b'PRIVATE_FIXTURE=do-not-print\n',
            'db-password': b'private-fixture', 'db-root-password': b'private-root',
            'google.json': b'{"private_key":"fixture"}', 'release.json': b'{}',
        }.items():
            self.private_write(name, value)
        self.manifest = {
            'format': 1, 'id': 'b3b63f3e-9e4f-4eb9-b7cf-1917cc22e90b',
            'installationId': '8975b8e1-c388-4893-8832-039eb8d439cd',
            'createdAt': '2026-11-01T00:00:00Z', 'releaseVersion': '1.22.0',
            'appImage': 'ghcr.io/jegr78/ctc-manager@sha256:' + 'a' * 64,
            'databaseImage': 'mariadb@sha256:' + 'b' * 64,
            'databaseVersion': '13.0.2-MariaDB',
            'schemaHistory': [{'rank': 1, 'version': '1', 'checksum': 123}],
            'records': {'fixture': 1}, 'files': {},
        }
        self.seal()

    def private_write(self, name, value):
        path = self.unit / name
        path.write_bytes(value)
        path.chmod(0o600)

    def write_tar(self, entries):
        path = self.unit / 'data.tar'
        with tarfile.open(path, 'w') as archive:
            for name, content, kind in entries:
                member = tarfile.TarInfo(name)
                member.type = kind
                member.mode = 0o600
                if kind == tarfile.REGTYPE:
                    member.size = len(content)
                    archive.addfile(member, io.BytesIO(content))
                else:
                    member.linkname = '/outside'
                    archive.addfile(member)
        path.chmod(0o600)

    def seal(self):
        self.manifest['files'] = {p.name: {'sha256': hashlib.sha256(p.read_bytes()).hexdigest(), 'bytes': p.stat().st_size}
                                  for p in self.unit.iterdir() if p.name != 'manifest.json'}
        self.private_write('manifest.json', (json.dumps(self.manifest) + '\n').encode())

    def test_complete_unit_preserves_provenance_and_inventory(self):
        result = recovery.validate_unit(self.unit)
        self.assertEqual(self.manifest, result)

    def test_modified_database_refuses_before_import(self):
        self.private_write('database.sql', b'corrupt')
        with self.assertRaisesRegex(ValueError, 'checksum|size'):
            recovery.validate_unit(self.unit)

    def test_incomplete_or_extra_unit_refuses(self):
        for change in ['missing', 'extra']:
            with self.subTest(change=change):
                if change == 'missing':
                    (self.unit / 'google.json').unlink()
                else:
                    self.private_write('unexpected.sh', b'echo unsafe')
                with self.assertRaisesRegex(ValueError, 'inventory'):
                    recovery.validate_unit(self.unit)
                self.private_write('google.json', b'{}')

    def test_unsafe_archive_types_and_paths_refuse(self):
        for name, kind in [('../outside', tarfile.REGTYPE), ('/outside', tarfile.REGTYPE),
                           ('uploads/link', tarfile.SYMTYPE), ('uploads/hard', tarfile.LNKTYPE),
                           ('uploads/fifo', tarfile.FIFOTYPE), ('uploads/./x', tarfile.REGTYPE)]:
            with self.subTest(name=name, kind=kind):
                self.write_tar([(name, b'x', kind)])
                self.seal()
                with self.assertRaisesRegex(ValueError, 'archive'):
                    recovery.validate_unit(self.unit)

    def test_duplicate_archive_entries_refuse(self):
        self.write_tar([('uploads/a', b'a', tarfile.REGTYPE)] * 2)
        self.seal()
        with self.assertRaisesRegex(ValueError, 'archive'):
            recovery.validate_unit(self.unit)

    def test_symlink_artifact_refuses_even_when_hash_matches(self):
        original = self.unit / 'app.env'
        target = self.unit.parent / 'secret'
        original.rename(target)
        original.symlink_to(target)
        with self.assertRaisesRegex(ValueError, 'symlink|type'):
            recovery.validate_unit(self.unit)

    def test_shared_readable_secret_refuses_without_secret_output(self):
        (self.unit / 'app.env').chmod(0o644)
        with self.assertRaisesRegex(ValueError, 'private') as error:
            recovery.validate_unit(self.unit)
        self.assertNotIn('do-not-print', str(error.exception))

    def test_manifest_does_not_accept_unknown_fields_or_duplicate_keys(self):
        self.manifest['runScript'] = '/tmp/unsafe'
        self.seal()
        with self.assertRaisesRegex(ValueError, 'manifest'):
            recovery.validate_unit(self.unit)
        self.private_write('manifest.json', b'{"format":1,"format":2}')
        with self.assertRaisesRegex(ValueError, 'duplicate'):
            recovery.validate_unit(self.unit)

    def test_source_identity_and_schema_must_be_explicit(self):
        for key, invalid in [('installationId', 'source'), ('appImage', 'app:latest'),
                             ('releaseVersion', 'latest'), ('schemaHistory', []),
                             ('records', {'bad;DROP DATABASE ctcdb': 0})]:
            with self.subTest(key=key):
                saved = self.manifest[key]
                self.manifest[key] = invalid
                self.seal()
                with self.assertRaisesRegex(ValueError, 'manifest'):
                    recovery.validate_unit(self.unit)
                self.manifest[key] = saved


if __name__ == '__main__':
    unittest.main()

class InstalledReleaseTest(unittest.TestCase):
    def test_changed_installed_tool_is_rejected_before_execution(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            (root / 'ctc-manager').write_text('trusted')
            manifest = {'format': 1, 'version': '1.22.0',
                        'appImage': 'ghcr.io/jegr78/ctc-manager@sha256:' + 'a' * 64,
                        'databaseImage': 'mariadb@sha256:' + 'b' * 64,
                        'files': {'ctc-manager': hashlib.sha256(b'trusted').hexdigest()}}
            (root / 'manifest.json').write_text(json.dumps(manifest))
            (root / 'ctc-manager').write_text('changed')
            with self.assertRaisesRegex(ValueError, 'checksum'):
                recovery.installed_release(root)

class CaptureTest(unittest.TestCase):
    def setUp(self):
        fixture = RecoveryUnitTest()
        fixture.setUp()
        self.addCleanup(fixture.temp.cleanup)
        self.destination = fixture.unit.parent / 'backups'
        self.destination.mkdir(mode=0o700)
        self.runtime = CaptureRuntime(fixture)

    def test_completed_capture_restores_original_running_state(self):
        unit = recovery.capture(self.runtime, self.destination)
        self.assertTrue(self.runtime.running)
        self.assertEqual(['stopped'], self.runtime.capture_states)
        self.assertEqual(1, len(list(self.destination.iterdir())))
        self.assertEqual('13.0.2-MariaDB', recovery.validate_unit(unit)['databaseVersion'])
        self.assertEqual(b'private-fixture', (unit / 'db-password').read_bytes())

    def test_originally_stopped_app_remains_stopped(self):
        self.runtime.running = False
        recovery.capture(self.runtime, self.destination)
        self.assertFalse(self.runtime.running)
        self.assertEqual(0, self.runtime.starts)

    def test_failed_capture_restores_app_without_publishing_partial_unit(self):
        self.runtime.failure = 'dump'
        with self.assertRaisesRegex(RuntimeError, 'dump failed'):
            recovery.capture(self.runtime, self.destination)
        self.assertTrue(self.runtime.running)
        self.assertFalse(any(p.name.startswith('recovery-') for p in self.destination.iterdir()))

    def test_hold_stopped_mode_never_restarts_between_capture_and_update(self):
        recovery.capture(self.runtime, self.destination, hold_stopped=True)
        self.assertFalse(self.runtime.running)
        self.assertEqual(0, self.runtime.starts)

    def test_missing_or_full_mount_refuses_before_stopping_app(self):
        for problem in ['missing backup mount', 'backup volume full']:
            with self.subTest(problem=problem):
                self.runtime.failure = problem
                with self.assertRaisesRegex(RuntimeError, problem):
                    recovery.capture(self.runtime, self.destination)
                self.assertTrue(self.runtime.running)
                self.assertEqual([], self.runtime.capture_states)

    def test_unexpected_source_writer_refuses_before_capture(self):
        self.runtime.failure = 'unexpected writer'
        with self.assertRaisesRegex(RuntimeError, 'unexpected writer'):
            recovery.capture(self.runtime, self.destination)
        self.assertTrue(self.runtime.running)
        self.assertEqual([], self.runtime.capture_states)


class CaptureRuntime:
    def __init__(self, fixture):
        self.fixture = fixture
        self.running = True
        self.starts = 0
        self.failure = None
        self.capture_states = []

    def check_destination(self, destination):
        if self.failure in ['missing backup mount', 'backup volume full']:
            raise RuntimeError(self.failure)

    def preflight(self):
        if self.failure == 'unexpected writer':
            raise RuntimeError(self.failure)

    def is_app_running(self):
        return self.running

    def stop_app(self):
        self.running = False

    def start_app(self):
        self.starts += 1
        self.running = True

    def export(self, staging):
        import shutil
        self.capture_states.append('running' if self.running else 'stopped')
        if self.failure == 'dump':
            raise RuntimeError('dump failed')
        for name in recovery.ARTIFACTS:
            shutil.copyfile(self.fixture.unit / name, staging / name)
            (staging / name).chmod(0o600)
        return {k:v for k,v in self.fixture.manifest.items() if k not in ['format', 'id', 'createdAt', 'files']}

class BackupDestinationTest(unittest.TestCase):
    def test_missing_mount_refuses_without_falling_back_to_root_disk(self):
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            (root / 'state').mkdir(mode=0o700)
            receipt = root / 'state/host.json'
            receipt.write_text(json.dumps({'backupUuid': '31d7af2e-b6de-4680-9dfb-692ffb692fc4'}))
            receipt.chmod(0o600)
            runtime = recovery.NativeRuntime.__new__(recovery.NativeRuntime)
            runtime.root = root
            with patch('subprocess.run') as command:
                command.return_value.returncode = 1
                with self.assertRaisesRegex(ValueError, 'mount'):
                    runtime.check_destination(Path('/mnt/backup/ctc-manager'))

    def test_wrong_device_or_full_mount_refuses(self):
        from unittest.mock import patch
        from collections import namedtuple
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            (root / 'state').mkdir(mode=0o700)
            receipt = root / 'state/host.json'
            receipt.write_text(json.dumps({'backupUuid': '31d7af2e-b6de-4680-9dfb-692ffb692fc4'}))
            receipt.chmod(0o600)
            runtime = recovery.NativeRuntime.__new__(recovery.NativeRuntime)
            runtime.root = root
            for device in ['wrong-device', '31d7af2e-b6de-4680-9dfb-692ffb692fc4']:
                with self.subTest(device=device), patch('subprocess.run') as command, patch('shutil.disk_usage') as usage:
                    command.return_value.returncode = 0
                    command.return_value.stdout = json.dumps({'filesystems': [{'target': '/mnt/backup', 'uuid': device}]}).encode()
                    usage.return_value = namedtuple('Usage', 'total used free')(100, 99, 1)
                    with self.assertRaisesRegex(ValueError, 'device|space'):
                        runtime.check_destination(Path('/mnt/backup/ctc-manager'))

class ProbeAdmissionTest(unittest.TestCase):
    def test_historical_image_without_policy_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'recovery capability'):
            recovery.admit_recovery_image({'Config': {'Labels': {}}})

    def test_supported_image_has_explicit_capability(self):
        recovery.admit_recovery_image({'Config': {'Labels': {'org.ctc.recovery.version': '1'}}})

    def test_restored_environment_cannot_override_runtime_or_run_code(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'app.env'
            path.write_text('SPRING_SECURITY_USER_NAME=owner\nSPRING_SECURITY_USER_PASSWORD=private-fixture\n'
                            'DISCORD_BOT_TOKEN=real-shaped-token\nGOOGLE_CALENDAR_ID=calendar@example.test\n'
                            'JAVA_TOOL_OPTIONS=-javaagent:/app/data/evil.jar\n'
                            'SPRING_CONFIG_LOCATION=/app/data/evil.yaml\n'
                            'DATABASE_URL=jdbc:mariadb://production/ctcdb\n'
                            'CTC_OUTBOUND_ENABLED=true\n')
            actual = recovery.probe_environment(path, 'new-db-password')
            self.assertEqual('owner', actual['SPRING_SECURITY_USER_NAME'])
            self.assertEqual('private-fixture', actual['SPRING_SECURITY_USER_PASSWORD'])
            self.assertEqual('real-shaped-token', actual['DISCORD_BOT_TOKEN'])
            self.assertEqual('recovery-check', actual['SPRING_PROFILES_ACTIVE'])
            self.assertEqual('false', actual['CTC_OUTBOUND_ENABLED'])
            self.assertEqual('new-db-password', actual['DATABASE_PASSWORD'])
            self.assertEqual('jdbc:mariadb://db:3306/ctcdb?rewriteBatchedStatements=true', actual['DATABASE_URL'])
            self.assertNotIn('javaagent', actual['JAVA_TOOL_OPTIONS'])
            self.assertNotIn('SPRING_CONFIG_LOCATION', actual)
