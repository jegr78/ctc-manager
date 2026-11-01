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
            'installation.json': b'{}', 'app.env': b'PRIVATE_FIXTURE=do-not-print\nSPRING_SECURITY_USER_NAME=owner\nSPRING_SECURITY_USER_PASSWORD=private-fixture\n',
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
        self.private_write('installation.json', json.dumps({'format': 1, 'project': 'ctc-manager',
            'installationId': self.manifest['installationId'], 'appImage': self.manifest['appImage'],
            'databaseImage': self.manifest['databaseImage']}).encode())
        self.private_write('release.json', json.dumps({'format': 1, 'version': self.manifest['releaseVersion'],
            'appImage': self.manifest['appImage'], 'databaseImage': self.manifest['databaseImage'],
            'files': {'ctc-manager': hashlib.sha256(b'trusted').hexdigest()}}).encode())
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

    def test_restored_file_inventory_compares_content_and_empty_directories(self):
        self.write_tar([('empty', b'', tarfile.DIRTYPE), ('uploads/logo.png', b'graphic', tarfile.REGTYPE)])
        actual = recovery.data_inventory(self.unit / 'data.tar')
        self.assertEqual({'type': 'directory'}, actual['empty'])
        self.assertEqual({'type': 'file', 'bytes': 7, 'sha256': hashlib.sha256(b'graphic').hexdigest()}, actual['uploads/logo.png'])

    def test_parent_file_collision_refuses_before_extraction(self):
        self.write_tar([('uploads', b'file', tarfile.REGTYPE), ('uploads/a', b'a', tarfile.REGTYPE)])
        self.seal()
        with self.assertRaisesRegex(ValueError, 'archive'):
            recovery.validate_unit(self.unit)

    def test_filesystem_hardlinked_artifact_refuses(self):
        os.link(self.unit / 'app.env', self.unit.parent / 'linked-secret')
        with self.assertRaisesRegex(ValueError, 'link'):
            recovery.validate_unit(self.unit)

    def test_same_size_database_change_refuses_checksum(self):
        original = (self.unit / 'database.sql').read_bytes()
        self.private_write('database.sql', b'X' + original[1:])
        with self.assertRaisesRegex(ValueError, 'checksum'):
            recovery.validate_unit(self.unit)

    def test_artifact_size_metadata_must_match_actual_file(self):
        self.manifest['files']['database.sql']['bytes'] += 1
        self.private_write('manifest.json', json.dumps(self.manifest).encode())
        with self.assertRaisesRegex(ValueError, 'size'):
            recovery.validate_unit(self.unit)

    def test_modified_database_refuses_before_import(self):
        self.private_write('database.sql', b'corrupt')
        with self.assertRaisesRegex(ValueError, 'checksum|size'):
            recovery.validate_unit(self.unit)

    def test_extra_unit_artifact_refuses_exact_inventory(self):
        self.private_write('unexpected.sh', b'echo unsafe')
        with self.assertRaisesRegex(ValueError, 'inventory'):
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
                self.private_write('google.json', b'{"private_key":"fixture"}')

    def test_unsafe_archive_types_and_paths_refuse(self):
        for name, kind in [('../outside', tarfile.REGTYPE), ('/outside', tarfile.REGTYPE),
                           ('uploads/link', tarfile.SYMTYPE), ('uploads/hard', tarfile.LNKTYPE),
                           ('uploads/fifo', tarfile.FIFOTYPE), ('uploads/contiguous', tarfile.CONTTYPE), ('uploads/./x', tarfile.REGTYPE)]:
            with self.subTest(name=name, kind=kind):
                self.write_tar([(name, b'x', kind)])
                self.seal()
                with self.assertRaisesRegex(ValueError, 'archive'):
                    recovery.validate_unit(self.unit)

    def test_oversized_extended_header_refuses_before_tarfile_metadata_allocation(self):
        with tarfile.open(self.unit / 'data.tar', 'w', format=tarfile.PAX_FORMAT) as archive:
            member = tarfile.TarInfo('uploads/file')
            member.size = 1
            member.pax_headers = {'comment': 'x' * (128 * 1024)}
            archive.addfile(member, io.BytesIO(b'x'))
        with self.assertRaisesRegex(ValueError, 'archive'):
            recovery.validate_data_archive(self.unit / 'data.tar')

    def test_archive_entry_and_expanded_size_limits_refuse(self):
        from unittest.mock import patch
        self.write_tar([('one', b'1', tarfile.REGTYPE), ('two', b'2', tarfile.REGTYPE)])
        for key in ['MAX_ENTRIES', 'MAX_DATA']:
            with self.subTest(limit=key), patch.object(recovery, key, 1):
                with self.assertRaisesRegex(ValueError, 'archive'):
                    recovery.validate_data_archive(self.unit / 'data.tar')

    def test_raw_archive_header_rejects_unsupported_type(self):
        self.write_tar([('contiguous', b'', tarfile.CONTTYPE)])
        with self.assertRaisesRegex(ValueError, 'archive'):
            recovery.check_tar_headers(self.unit / 'data.tar')

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

    def test_resealed_source_provenance_mismatch_refuses(self):
        source = json.loads((self.unit / 'release.json').read_text())
        source['appImage'] = 'ghcr.io/jegr78/ctc-manager@sha256:' + 'c' * 64
        self.private_write('release.json', json.dumps(source).encode())
        self.seal()
        with self.assertRaisesRegex(ValueError, 'provenance'):
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

    def test_invalid_exported_archive_is_never_published(self):
        self.runtime.fixture.write_tar([('uploads/link', b'', tarfile.SYMTYPE)])
        with self.assertRaisesRegex(ValueError, 'archive'):
            recovery.capture(self.runtime, self.destination)
        self.assertTrue(self.runtime.running)
        self.assertFalse(any(p.name.startswith('recovery-') for p in self.destination.iterdir()))

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
            with patch('subprocess.run') as command, patch('shutil.disk_usage') as usage:
                usage.return_value.free = 4 * 1024 ** 3
                command.return_value.stdout = json.dumps({'filesystems': [{'target': '/mnt/backup', 'uuid': '31d7af2e-b6de-4680-9dfb-692ffb692fc4'}]}).encode()
                command.return_value.returncode = 1
                with self.assertRaisesRegex(ValueError, 'mount'):
                    runtime.check_destination(Path('/mnt/backup/ctc-manager'))

    def test_effective_mount_lookup_failure_refuses_even_with_valid_stdout(self):
        from unittest.mock import patch
        from types import SimpleNamespace
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            (root / 'state').mkdir(mode=0o700)
            receipt = root / 'state/host.json'
            expected = '31d7af2e-b6de-4680-9dfb-692ffb692fc4'
            receipt.write_text(json.dumps({'backupUuid': expected}))
            receipt.chmod(0o600)
            runtime = recovery.NativeRuntime.__new__(recovery.NativeRuntime)
            runtime.root = root
            output = json.dumps({'filesystems': [{'target': '/mnt/backup', 'uuid': expected}]}).encode()
            with patch('subprocess.run') as command, patch('shutil.disk_usage') as usage:
                command.side_effect = [SimpleNamespace(returncode=0, stdout=output), SimpleNamespace(returncode=1, stdout=output)]
                usage.return_value.free = 4 * 1024 ** 3
                with self.assertRaisesRegex(ValueError, 'destination device cannot'):
                    runtime.check_destination(Path('/mnt/backup/ctc-manager'))

    def test_destination_outside_ctc_root_refuses_before_mount_lookup(self):
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            (root / 'state').mkdir(mode=0o700)
            receipt = root / 'state/host.json'
            expected = '31d7af2e-b6de-4680-9dfb-692ffb692fc4'
            receipt.write_text(json.dumps({'backupUuid': expected}))
            receipt.chmod(0o600)
            runtime = recovery.NativeRuntime.__new__(recovery.NativeRuntime)
            runtime.root = root
            with patch('subprocess.run') as command, patch('shutil.disk_usage') as usage:
                command.return_value.returncode = 0
                command.return_value.stdout = json.dumps({'filesystems': [{'target': '/mnt/backup', 'uuid': expected}]}).encode()
                usage.return_value.free = 4 * 1024 ** 3
                with self.assertRaisesRegex(ValueError, 'inside the CTC backup mount'):
                    runtime.check_destination(Path('/mnt/backup/courtside'))
                command.assert_not_called()

    def test_nested_mount_cannot_redirect_backup_to_another_device(self):
        from unittest.mock import patch
        from types import SimpleNamespace
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            (root / 'state').mkdir(mode=0o700)
            receipt = root / 'state/host.json'
            expected = '31d7af2e-b6de-4680-9dfb-692ffb692fc4'
            receipt.write_text(json.dumps({'backupUuid': expected}))
            receipt.chmod(0o600)
            runtime = recovery.NativeRuntime.__new__(recovery.NativeRuntime)
            runtime.root = root
            with patch('subprocess.run') as command, patch('shutil.disk_usage') as usage:
                command.side_effect = [SimpleNamespace(returncode=0, stdout=json.dumps({'filesystems': [{'target': '/mnt/backup', 'uuid': expected}]}).encode()),
                                       SimpleNamespace(returncode=0, stdout=json.dumps({'filesystems': [{'target': '/mnt/backup/ctc-manager/nested', 'uuid': 'another-device'}]}).encode())]
                usage.return_value.free = 4 * 1024 ** 3
                with self.assertRaisesRegex(ValueError, 'destination.*device'):
                    runtime.check_destination(Path('/mnt/backup/ctc-manager/nested'))

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
                    from types import SimpleNamespace
                    parent = json.dumps({'filesystems': [{'target': '/mnt/backup', 'uuid': device}]}).encode()
                    actual = json.dumps({'filesystems': [{'target': '/mnt/backup', 'uuid': '31d7af2e-b6de-4680-9dfb-692ffb692fc4'}]}).encode()
                    command.side_effect = [SimpleNamespace(returncode=0, stdout=parent), SimpleNamespace(returncode=0, stdout=actual)]
                    usage.return_value = namedtuple('Usage', 'total used free')(10 * 1024 ** 3, 1, 1 if device.startswith('31d7') else 4 * 1024 ** 3)
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

class ProbeIsolationTest(unittest.TestCase):
    def test_probe_specs_have_no_host_ports_or_external_network(self):
        from types import SimpleNamespace
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            probe = recovery.Probe(SimpleNamespace(root=root), root, {}, 'sha256:' + 'a' * 64,
                                   'sha256:' + 'b' * 64)
            spec = probe.app_arguments({'SPRING_PROFILES_ACTIVE': 'recovery-check'})
            self.assertNotIn('--publish', spec)
            self.assertNotIn('-p', spec)
            self.assertIn('--read-only', spec)
            self.assertIn('/tmp:rw,exec,nosuid,nodev,size=512m', spec)
            self.assertIn('--cap-drop', spec)
            self.assertIn('ALL', spec)
            self.assertIn(probe.network, spec)
            self.assertIn('org.ctc.recovery.run=' + probe.id, spec)
            self.assertNotIn('/var/run/docker.sock', ' '.join(spec))
            self.assertNotIn('ctc-manager_data', ' '.join(spec))

    def test_cleanup_refuses_resource_without_exact_run_label(self):
        from types import SimpleNamespace
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            calls = []
            def docker(arguments, **kwargs):
                calls.append(arguments)
                if arguments[1] == 'ls':
                    return (probe.prefix + '-data\n').encode()
                return json.dumps([{'Labels': {'org.ctc.recovery.run': 'another-run'}}]).encode()
            probe = recovery.Probe(SimpleNamespace(root=root, docker=docker), root, {}, 'sha256:' + 'a' * 64,
                                   'sha256:' + 'b' * 64)
            probe.resources = [('volume', probe.prefix + '-data')]
            with self.assertRaisesRegex(ValueError, 'ownership'):
                probe.cleanup()
            self.assertFalse(any('rm' in call for call in calls))

class RestoreCheckTest(unittest.TestCase):
    def setUp(self):
        from types import SimpleNamespace
        fixture = RecoveryUnitTest()
        fixture.setUp()
        self.addCleanup(fixture.temp.cleanup)
        self.fixture = fixture
        self.calls = []
        def docker(arguments, **kwargs):
            self.calls.append(arguments)
            return json.dumps([{'Id': 'sha256:' + 'a' * 64, 'Config': {'Labels': {}}}]).encode()
        self.runtime = SimpleNamespace(root=fixture.unit.parent, docker=docker,
                                       release=json.loads((fixture.unit / 'release.json').read_text()))

    def test_old_image_refuses_before_any_probe_resources(self):
        with self.assertRaisesRegex(ValueError, 'recovery capability'):
            recovery.restore_check(self.runtime, self.fixture.unit)
        self.assertFalse(any('create' in call or 'run' in call for call in self.calls))

    def test_matching_installed_release_is_required_before_creating_resources(self):
        def supported(arguments, **kwargs):
            self.calls.append(arguments)
            return json.dumps([{'Id': 'sha256:' + 'a' * 64, 'Config': {'Labels': {'org.ctc.recovery.version': '1'}}}]).encode()
        self.runtime.docker = supported
        self.runtime.release['version'] = '1.99.0'
        with self.assertRaisesRegex(ValueError, 'matching installed release'):
            recovery.restore_check(self.runtime, self.fixture.unit)
        self.assertFalse(any('create' in call or 'run' in call for call in self.calls))

    def test_corrupt_unit_refuses_before_any_docker_operation(self):
        self.fixture.private_write('database.sql', b'corrupt')
        with self.assertRaisesRegex(ValueError, 'size|checksum'):
            recovery.restore_check(self.runtime, self.fixture.unit)
        self.assertEqual([], self.calls)

class LiveRecoveryTargetTest(unittest.TestCase):
    def test_confirmation_binds_installation_and_independent_generation(self):
        config = {'installationId': '8975b8e1-c388-4893-8832-039eb8d439cd', 'storageGeneration': 'active'}
        target = 'ctc-manager:' + config['installationId'] + ':recovery-20261101'
        self.assertEqual('recovery-20261101', recovery.recovery_target(config, target))
        for invalid in ['ctc-manager:another:recovery-new',
                        'ctc-manager:' + config['installationId'] + ':active',
                        'ctc-manager:' + config['installationId'] + ':../courtside']:
            with self.subTest(target=invalid), self.assertRaisesRegex(ValueError, 'target'):
                recovery.recovery_target(config, invalid)

    def test_restore_rejects_bad_confirmation_before_probe_or_active_changes(self):
        from types import SimpleNamespace
        from unittest.mock import Mock
        runtime = SimpleNamespace(config={'installationId': '8975b8e1-c388-4893-8832-039eb8d439cd'}, docker=Mock(), compose=Mock())
        with self.assertRaisesRegex(ValueError, 'target'):
            recovery.restore(runtime, Path('/does-not-exist'), 'wrong-target')
        runtime.docker.assert_not_called()
        runtime.compose.assert_not_called()

class LiveRecoveryActivationTest(unittest.TestCase):
    def setUp(self):
        from types import SimpleNamespace
        from unittest.mock import Mock
        fixture = RecoveryUnitTest()
        fixture.setUp()
        self.addCleanup(fixture.temp.cleanup)
        self.fixture = fixture
        root = fixture.unit.parent
        for name in ['config', 'secrets', 'state']:
            (root / name).mkdir(mode=0o700)
        config = json.loads((fixture.unit / 'installation.json').read_text())
        config.update(storageGeneration='active', dockerHost='unix:///fixture/docker.sock', outboundPaused=False)
        self.original = dict(config)
        self.runtime = SimpleNamespace(root=root, config=config, stop_app=Mock(), preflight=Mock(), compose=Mock(return_value=b'active-app'))
        self.runtime.docker = Mock(side_effect=lambda args: b'' if args[:2] == ['volume', 'ls'] else
                                   json.dumps([{'Id': 'sha256:' + 'a' * 64, 'Config': {'Labels': {'org.ctc.recovery.version': '1'}}}]).encode())
        self.confirmation = 'ctc-manager:' + config['installationId'] + ':recovery-qualified'

    def test_unexpected_active_writer_refuses_before_isolated_check_or_activation(self):
        from unittest.mock import patch
        self.runtime.preflight.side_effect = ValueError('unexpected writer')
        with patch('recovery.restore_check', return_value={'run': 'unexpected'}) as check, patch('live.RecoveryStorage', return_value=PreparedStorage()):
            with self.assertRaisesRegex(ValueError, 'unexpected writer'):
                recovery.restore(self.runtime, self.fixture.unit, self.confirmation)
        check.assert_not_called()
        self.runtime.stop_app.assert_not_called()
        self.assertEqual(self.original, self.runtime.config)

    def test_existing_target_volume_refuses_before_restore_or_active_changes(self):
        from unittest.mock import patch
        self.runtime.docker.return_value = b'ctc-manager_recovery-qualified_data\n'
        self.runtime.docker.side_effect = None
        with patch('recovery.restore_check') as check:
            with self.assertRaisesRegex(ValueError, 'already exists'):
                recovery.restore(self.runtime, self.fixture.unit, self.confirmation)
        check.assert_not_called()
        self.runtime.stop_app.assert_not_called()
        self.runtime.compose.assert_not_called()

    def test_failed_isolated_check_never_stops_or_reconfigures_active_app(self):
        from unittest.mock import patch
        with patch('recovery.restore_check', side_effect=ValueError('probe failed')):
            with self.assertRaisesRegex(ValueError, 'probe failed'):
                recovery.restore(self.runtime, self.fixture.unit, self.confirmation)
        self.runtime.stop_app.assert_not_called()
        self.assertFalse(any(call.args[0][0] != 'ps' for call in self.runtime.compose.call_args_list))
        self.assertEqual(self.original, self.runtime.config)
        self.assertFalse((self.runtime.root / 'secrets/recovery-qualified').exists())

    def test_verified_fresh_storage_activates_paused_and_preserves_previous_configuration(self):
        from unittest.mock import patch
        with patch('recovery.restore_check', return_value={'run': 'proven'}), patch('live.RecoveryStorage', return_value=PreparedStorage()):
            result = recovery.restore(self.runtime, self.fixture.unit, self.confirmation)
        self.assertEqual('active-integrations-paused', result['stage'])
        self.assertTrue(self.runtime.config['outboundPaused'])
        self.assertEqual('recovery-qualified', self.runtime.config['storageGeneration'])
        previous = json.loads((self.runtime.root / 'state/before-recovery-qualified.json').read_text())
        self.assertEqual(self.original, previous)
        self.assertEqual((self.fixture.unit / 'google.json').read_bytes(), Path(self.runtime.config['googleCredentialsFile']).read_bytes())
        self.assertNotIn('--volumes', str(self.runtime.compose.call_args_list))
        self.assertEqual(1, self.runtime.stop_app.call_count)

    def test_linked_secret_parent_cannot_create_a_directory_outside_target(self):
        from unittest.mock import patch
        secrets = self.runtime.root / 'secrets'
        secrets.rmdir()
        outside = self.runtime.root / 'outside-secrets'
        outside.mkdir(mode=0o700)
        secrets.symlink_to(outside, target_is_directory=True)
        with patch('recovery.restore_check', return_value={'run': 'proven'}), patch('live.RecoveryStorage', return_value=PreparedStorage()):
            with self.assertRaisesRegex(ValueError, 'symlink'):
                recovery.restore(self.runtime, self.fixture.unit, self.confirmation)
        self.assertFalse((outside / 'recovery-qualified').exists())
        self.runtime.stop_app.assert_not_called()

    def test_unflushed_restored_secrets_never_replace_active_configuration(self):
        from unittest.mock import patch
        original_flush = recovery.flush_directory
        def fail_secret_flush(path):
            if path.parent.name == 'secrets':
                raise OSError('secret flush failed')
            return original_flush(path)
        with patch('recovery.restore_check', return_value={'run': 'proven'}), patch('live.RecoveryStorage', return_value=PreparedStorage()), patch('recovery.flush_directory', side_effect=fail_secret_flush):
            with self.assertRaisesRegex(OSError, 'secret flush failed'):
                recovery.restore(self.runtime, self.fixture.unit, self.confirmation)
        self.runtime.stop_app.assert_not_called()
        self.assertFalse(any(call.args[0][0] != 'ps' for call in self.runtime.compose.call_args_list))
        self.assertEqual(self.original, self.runtime.config)

    def test_failed_activation_preserves_both_configurations_and_requires_review(self):
        from unittest.mock import patch
        self.runtime.compose.side_effect = lambda args: (_ for _ in ()).throw(ValueError('readiness failed')) if args[0] == 'up' else b''
        with patch('recovery.restore_check', return_value={'run': 'proven'}), patch('live.RecoveryStorage', return_value=PreparedStorage()):
            with self.assertRaisesRegex(ValueError, 'readiness failed'):
                recovery.restore(self.runtime, self.fixture.unit, self.confirmation)
        self.assertTrue(self.runtime.config['outboundPaused'])
        receipt = json.loads((self.runtime.root / 'state/live-recovery.json').read_text())
        self.assertEqual('failed-review-required', receipt['stage'])
        self.assertTrue(Path(receipt['previousConfiguration']).exists())
        self.assertNotIn('--volumes', str(self.runtime.compose.call_args_list))


class PreparedStorage:
    prefix = 'isolated-fixture'
    resources = []
    def create_resources(self): pass
    def record(self, *args): pass
    def docker(self, args): return b''
    def database_arguments(self): return []
    def wait_database(self): pass
    def import_database(self): pass
    def verify_database(self): pass
    def restore_files(self): pass
    def verify_files(self): pass
    def cleanup(self, **kwargs): pass

class ProbeCreationOwnershipTest(unittest.TestCase):
    def test_existing_volume_cannot_be_adopted_or_used_by_probe(self):
        from types import SimpleNamespace
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            calls = []
            def docker(arguments, **kwargs):
                calls.append(arguments)
                if arguments[:2] == ['network', 'inspect']:
                    return json.dumps([{'Labels': {'org.ctc.recovery.run': probe.id}, 'Internal': True}]).encode()
                if arguments[:2] == ['volume', 'inspect']:
                    return json.dumps([{'Labels': {'org.ctc.recovery.run': 'other-run'}}]).encode()
                return b'created'
            probe = recovery.Probe(SimpleNamespace(root=root, docker=docker), root, {}, 'sha256:' + 'a' * 64,
                                   'sha256:' + 'b' * 64)
            with self.assertRaisesRegex(ValueError, 'ownership'):
                probe.create_resources()
            self.assertFalse(any(call[0] == 'run' for call in calls))

class ProbeCleanupFailureTest(unittest.TestCase):
    def test_missing_failed_container_does_not_leave_owned_database_or_volume(self):
        from types import SimpleNamespace
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            calls = []
            def docker(arguments, **kwargs):
                calls.append(arguments)
                if arguments[1] == 'ls':
                    return ('\n'.join([probe.prefix + '-db', probe.prefix + '-data']) + '\n').encode()
                return json.dumps([{'Config': {'Labels': {'org.ctc.recovery.run': probe.id}},
                                    'Labels': {'org.ctc.recovery.run': probe.id}}]).encode()
            probe = recovery.Probe(SimpleNamespace(root=root, docker=docker), root, {}, 'sha256:' + 'a' * 64,
                                   'sha256:' + 'b' * 64)
            probe.resources = [('volume', probe.prefix + '-data'), ('container', probe.prefix + '-db'),
                               ('container', probe.prefix + '-app')]
            probe.cleanup()
            removed = [name for call in calls if 'rm' in call for name in call[-1:]]
            self.assertIn(probe.prefix + '-db', removed)
            self.assertIn(probe.prefix + '-data', removed)
            self.assertNotIn(probe.prefix + '-app', removed)
            self.assertEqual([], probe.resources)

    def test_inspect_failure_does_not_prevent_other_independent_cleanup(self):
        from types import SimpleNamespace
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory).resolve()
            calls = []
            def docker(arguments, **kwargs):
                calls.append(arguments)
                if arguments[1] == 'ls':
                    return ('\n'.join([probe.prefix + '-db', probe.prefix + '-data']) + '\n').encode()
                if 'inspect' in arguments and arguments[-1].endswith('-db'):
                    raise ValueError('daemon inspection failed')
                return json.dumps([{'Labels': {'org.ctc.recovery.run': probe.id}}]).encode()
            probe = recovery.Probe(SimpleNamespace(root=root, docker=docker), root, {}, 'sha256:' + 'a' * 64,
                                   'sha256:' + 'b' * 64)
            probe.resources = [('volume', probe.prefix + '-data'), ('container', probe.prefix + '-db')]
            with self.assertRaisesRegex(ValueError, 'cleanup'):
                probe.cleanup()
            self.assertIn(['volume', 'rm', probe.prefix + '-data'], calls)
            self.assertEqual([('container', probe.prefix + '-db')], probe.resources)

    def test_cleanup_failure_cannot_leave_passed_result(self):
        from types import SimpleNamespace
        from unittest.mock import Mock
        fixture = RecoveryUnitTest()
        fixture.setUp()
        self.addCleanup(fixture.temp.cleanup)
        runtime = SimpleNamespace(root=fixture.unit.parent, docker=Mock(return_value=b''))
        probe = recovery.Probe(runtime, fixture.unit, fixture.manifest, 'sha256:' + 'a' * 64, 'sha256:' + 'b' * 64)
        for method in ['create_resources', 'wait_database', 'import_database', 'verify_database', 'restore_files', 'verify_files', 'verify_application']:
            setattr(probe, method, Mock())
        probe.database_arguments = Mock(return_value=[])
        probe.app_arguments = Mock(return_value=[])
        probe.verify_graphic = Mock(return_value='fixture')
        probe.cleanup = Mock(side_effect=ValueError('cleanup failed'))
        with self.assertRaisesRegex(ValueError, 'cleanup failed'):
            probe.run()
        result = json.loads((probe.directory / 'result.json').read_text())
        self.assertEqual('failed', result['status'])

class SourceRuntimeGuardTest(unittest.TestCase):
    def setUp(self):
        from unittest.mock import Mock
        self.runtime = recovery.NativeRuntime.__new__(recovery.NativeRuntime)
        self.runtime.root = Path('/fixture')
        self.runtime.deploy_root = Path('/release')
        self.runtime.config = {'dockerHost': 'unix:///fixture/socket', 'appImage': 'ghcr.io/jegr78/ctc-manager@sha256:' + 'a' * 64,
                               'databaseImage': 'mariadb@sha256:' + 'b' * 64,
                               **{key: '/fixture/secret' for key in ['appEnvFile', 'databasePasswordFile', 'databaseRootPasswordFile', 'googleCredentialsFile']}}
        self.db = {'Id': 'db', 'State': {'Running': True},
                   'Config': {'Image': self.runtime.config['databaseImage']}, 'Image': 'sha256:database',
                   'Mounts': [{'Destination': '/var/lib/mysql', 'Name': 'ctc-manager_database'}],
                   'NetworkSettings': {'Networks': {'private': {'NetworkID': 'network'}}}}
        self.app = {'Id': 'app', 'State': {'Running': True},
                    'Config': {'Image': self.runtime.config['appImage']}, 'Image': 'sha256:app',
                    'Mounts': [{'Destination': '/app/data', 'Name': 'ctc-manager_data'}]}
        self.runtime.db = self.db
        self.runtime.app = self.app
        self.containers = [self.db, self.app]
        self.network = {'Internal': True, 'Containers': {'db': {}, 'app': {}}}
        self.info = {'SecurityOptions': ['name=rootless'], 'MemoryLimit': True}
        self.runtime.container = Mock(side_effect=lambda role: self.db if role == 'db' else self.app)
        self.runtime.compose = Mock(return_value=b'')
        def docker(arguments, **kwargs):
            if arguments[0] == 'info':
                return json.dumps(self.info).encode()
            if arguments[:2] == ['ps', '--quiet']:
                return ' '.join(c['Id'] for c in self.containers).encode()
            if arguments[0] == 'inspect':
                return json.dumps(self.containers).encode()
            if arguments[:2] == ['image', 'inspect']:
                image = self.db if arguments[-1] == self.runtime.config['databaseImage'] else self.app
                return json.dumps([{'Id': 'sha256:database' if image is self.db else 'sha256:app'}]).encode()
            if arguments[:2] == ['network', 'inspect']:
                return json.dumps([self.network]).encode()
            raise AssertionError(arguments)
        self.runtime.docker = Mock(side_effect=docker)

    def test_exact_running_source_passes_without_starting_any_service(self):
        self.runtime.preflight()
        self.runtime.compose.assert_not_called()

    def test_recovery_can_inspect_running_database_after_app_container_loss(self):
        self.containers = [self.db]
        self.network['Containers'] = {'db': {}}
        self.runtime.preflight(allow_missing_app=True)
        self.assertIsNone(self.runtime.app)
        self.runtime.container.assert_called_once_with('db')
        self.containers.append({'Id': 'foreign', 'Mounts': [{'Name': 'ctc-manager_data'}]})
        with self.assertRaisesRegex(ValueError, 'unexpected writer'):
            self.runtime.preflight(allow_missing_app=True)

    def test_unexpected_volume_writer_refuses(self):
        self.containers.append({'Id': 'foreign', 'Mounts': [{'Name': 'ctc-manager_data'}]})
        with self.assertRaisesRegex(ValueError, 'unexpected writer'):
            self.runtime.preflight()

    def test_unexpected_network_writer_refuses(self):
        self.network['Containers']['foreign'] = {}
        with self.assertRaisesRegex(ValueError, 'unexpected writer'):
            self.runtime.preflight()

    def test_non_private_database_network_refuses(self):
        self.network['Internal'] = False
        with self.assertRaisesRegex(ValueError, 'private database network'):
            self.runtime.preflight()

    def test_wrong_selected_storage_refuses(self):
        self.app['Mounts'][0]['Name'] = 'courtside_data'
        with self.assertRaisesRegex(ValueError, 'storage'):
            self.runtime.preflight()

    def test_wrong_declared_or_actual_image_refuses(self):
        for field in ['Image', 'Config']:
            with self.subTest(field=field):
                previous = self.app[field]
                self.app[field] = 'sha256:foreign' if field == 'Image' else {'Image': 'app:latest'}
                with self.assertRaisesRegex(ValueError, 'immutable release'):
                    self.runtime.preflight()
                self.app[field] = previous

    def test_stopped_database_refuses_without_starting_it(self):
        self.db['State']['Running'] = False
        with self.assertRaisesRegex(ValueError, 'already be running'):
            self.runtime.preflight()
        self.runtime.compose.assert_not_called()

    def test_rootful_or_unlimited_daemon_refuses(self):
        for key, invalid in [('SecurityOptions', []), ('MemoryLimit', False)]:
            with self.subTest(key=key):
                previous = self.info[key]
                self.info[key] = invalid
                with self.assertRaisesRegex(ValueError, 'rootless Docker'):
                    self.runtime.preflight()
                self.info[key] = previous

    def test_forced_or_failed_stop_refuses_capture(self):
        for state in [{'Running': True}, {'Running': False, 'OOMKilled': True},
                      {'Running': False, 'ExitCode': 137}, {'Running': False, 'ExitCode': 139}]:
            with self.subTest(state=state):
                self.app['State'] = state
                with self.assertRaisesRegex(ValueError, 'stop cleanly'):
                    self.runtime.stop_app()

class IntegrationResumeTest(unittest.TestCase):
    def setUp(self):
        from types import SimpleNamespace
        from unittest.mock import Mock
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        root = Path(self.temp.name).resolve()
        (root / 'config').mkdir(mode=0o700)
        self.identity = '8975b8e1-c388-4893-8832-039eb8d439cd'
        self.runtime = SimpleNamespace(root=root, config={'installationId': self.identity, 'outboundPaused': True, 'appImage': 'fixture'},
                                       preflight=Mock(), docker=Mock(return_value=json.dumps([{'Config': {'Labels': {'org.ctc.recovery.version': '1'}}}]).encode()), compose=Mock())

    def test_invalid_confirmation_cannot_resume_integrations(self):
        with self.assertRaisesRegex(ValueError, 'confirm the paused installation'):
            recovery.resume_integrations(self.runtime, 'another-installation')
        self.runtime.preflight.assert_not_called()
        self.runtime.compose.assert_not_called()
        self.assertTrue(self.runtime.config['outboundPaused'])

    def test_explicit_confirmation_recreates_only_the_verified_app(self):
        result = recovery.resume_integrations(self.runtime, self.identity)
        self.assertEqual('integrations-resumed', result['status'])
        self.assertFalse(self.runtime.config['outboundPaused'])
        self.runtime.preflight.assert_called_once()
        self.runtime.compose.assert_called_once_with(['up', '-d', '--wait', '--wait-timeout', '180', 'app'])


class ProbeRuntimeProofTest(unittest.TestCase):
    def test_enabled_policy_or_missing_authentication_cannot_pass(self):
        from types import SimpleNamespace
        from unittest.mock import Mock
        with tempfile.TemporaryDirectory() as directory:
            runtime = SimpleNamespace(root=Path(directory).resolve(), docker=Mock(return_value=b'[{"State":{"Running":true}}]'))
            probe = recovery.Probe(runtime, runtime.root, {}, 'sha256:' + 'a' * 64, 'sha256:' + 'b' * 64)
            for failure in ['policy', 'anonymous', 'authenticated']:
                def curl(path, environment, authenticated=True, data=None):
                    if path == '/actuator/info':
                        return 200, json.dumps({'ctcRecovery': {'version': 1, 'outboundEnabled': failure == 'policy'}}).encode()
                    if path == '/admin/teams' and not authenticated:
                        return (200 if failure == 'anonymous' else 401), b''
                    if path.startswith('/admin/') and failure == 'authenticated':
                        return 403, b''
                    return 200, b''
                probe.curl = curl
                with self.subTest(failure=failure), self.assertRaisesRegex(ValueError, 'policy|authenticated|recovery read'):
                    probe.verify_application({})

class ProbeCredentialProofTest(unittest.TestCase):
    def setUp(self):
        from types import SimpleNamespace
        from unittest.mock import Mock
        fixture = RecoveryUnitTest()
        fixture.setUp()
        self.addCleanup(fixture.temp.cleanup)
        self.failure = None
        runtime = SimpleNamespace(root=fixture.unit.parent)
        self.probe = recovery.Probe(runtime, fixture.unit, fixture.manifest, 'sha256:' + 'a' * 64, 'sha256:' + 'b' * 64)
        def docker(arguments, **kwargs):
            if arguments[0] == 'inspect':
                return b'[{"State":{"Running":true}}]'
            if arguments[:3] == ['exec', self.probe.prefix + '-app', 'sha256sum']:
                if self.failure == 'google-unreadable':
                    raise ValueError('private Docker diagnostic')
                checksum = '0' * 64 if self.failure == 'google-mismatch' else recovery.sha256(fixture.unit / 'google.json')
                return (checksum + '  /run/ctc/google-credentials.json\n').encode()
            if arguments[:2] == ['exec', self.probe.prefix + '-db']:
                if self.failure == 'root-socket-bypass':
                    if '--protocol=TCP' in arguments[-1]:
                        raise ValueError('private Docker diagnostic')
                    return b'1\n'
                if self.failure == 'root-login':
                    raise ValueError('private Docker diagnostic')
                return b'0\n' if self.failure == 'root-result' else b'1\n'
            raise AssertionError(arguments)
        runtime.docker = Mock(side_effect=docker)
        def curl(path, environment, authenticated=True, data=None):
            if path == '/actuator/info':
                return 200, b'{"ctcRecovery":{"version":1,"outboundEnabled":false}}'
            if path == '/admin/teams' and not authenticated:
                return 401, b''
            return 200, b''
        self.probe.curl = curl

    def test_healthy_authenticated_app_still_requires_restored_credentials(self):
        for failure in ['google-unreadable', 'google-mismatch', 'root-login', 'root-result', 'root-socket-bypass']:
            self.failure = failure
            with self.subTest(failure=failure), self.assertRaisesRegex(ValueError, 'credential'):
                self.probe.verify_application({})

    def test_exact_google_bytes_and_database_root_login_complete_application_proof(self):
        self.probe.verify_application({})
        calls = [call.args[0] for call in self.probe.runtime.docker.call_args_list]
        self.assertIn(['exec', self.probe.prefix + '-app', 'sha256sum', '/run/ctc/google-credentials.json'], calls)
        self.assertTrue(any(call[:2] == ['exec', self.probe.prefix + '-db'] for call in calls))
