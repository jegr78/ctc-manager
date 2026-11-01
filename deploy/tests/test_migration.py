import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import Mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import migration


class SourceContractTest(unittest.TestCase):
    def test_supported_native_source_does_not_invent_app_version(self):
        metadata = {'databaseVersion': '13.0.2-MariaDB', 'schemaHistory': [str(i) for i in range(1, 7)]}
        self.assertIsNone(migration.source_contract(metadata)['sourceApplicationVersion'])

    def test_incompatible_database_and_schema_are_refused(self):
        for field, value in [('databaseVersion', '11.8-MariaDB'), ('schemaHistory', ['1', '2']),
                             ('databaseVersion', '13.0.2-MySQL'), ('schemaHistory', ['1', '2', '3', '4', '5', '6', '9000'])]:
            metadata = {'databaseVersion': '13.0.2-MariaDB', 'schemaHistory': [str(i) for i in range(1, 7)]}
            metadata[field] = value
            with self.subTest(field=field, value=value), self.assertRaisesRegex(ValueError, 'unsupported native source'):
                migration.source_contract(metadata)

    def test_only_source_columns_are_compared_with_utc_stable_projection(self):
        query = Mock(return_value=['["41",null]', '["42","32"]'])
        actual = migration.projection(query, {'teams': ['id', 'name']})
        self.assertEqual(actual['teams'], hashlib.sha256(b'["41",null]\n["42","32"]\n').hexdigest())
        self.assertIn("SET time_zone='+00:00'", query.call_args.args[0])
        self.assertIn('HEX(`id`)', query.call_args.args[0])

    def test_projection_rejects_untrusted_sql_identifiers(self):
        query = Mock()
        for columns in [{'teams; DROP TABLE teams': ['id']}, {'teams': ['id`']}, {'teams': []}]:
            with self.subTest(columns=columns), self.assertRaisesRegex(ValueError, 'source columns'):
                migration.projection(query, columns)
        query.assert_not_called()

    def test_environment_overlay_is_literal_and_never_executes_shell(self):
        actual = migration.environment_values('DISCORD_BOT_TOKEN=first\nGOOGLE_CALENDAR_ID="calendar"\n',
                                            "DISCORD_BOT_TOKEN='$(touch /tmp/never-run-ctc)'\n")
        self.assertEqual(actual, {'DISCORD_BOT_TOKEN': '$(touch /tmp/never-run-ctc)', 'GOOGLE_CALENDAR_ID': 'calendar'})
        self.assertFalse(Path('/tmp/never-run-ctc').exists())

    def test_ambiguous_environment_is_refused(self):
        for value in ['DISCORD_BOT_TOKEN=${SECRET}', 'DISCORD_BOT_TOKEN="unterminated',
                      'DISCORD_BOT_TOKEN=a\nDISCORD_BOT_TOKEN=b', 'export DISCORD_BOT_TOKEN=secret']:
            with self.subTest(value=value), self.assertRaises(ValueError):
                migration.environment_values(value, '')

    def test_live_source_writer_blocks_export(self):
        for value in [1, 3]:
            source = Mock()
            source.query.side_effect = [['1'], [str(value)]]
            with self.subTest(value=value), self.assertRaisesRegex(ValueError, 'source writer'):
                migration.assert_source_idle(source.query)

    def test_authority_must_be_unique_and_after_exposure_requires_reverse_migration(self):
        for stage in ['writes-possible', 'authoritative', 'unknown']:
            with self.subTest(stage=stage), self.assertRaisesRegex(ValueError, 'reverse migration'):
                migration.before_write_rollback({'stage': stage})
        self.assertEqual(migration.before_write_rollback({'stage': 'rehearsed'}), 'unchanged-source')

    def test_archive_refuses_symlinks_and_wrong_source_paths(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / 'data/local/uploads').mkdir(parents=True)
            (root / 'data/local/uploads/bad').symlink_to(root / 'outside')
            with self.assertRaisesRegex(ValueError, 'source file'):
                migration.source_files(root, 'data/local/uploads')
            (root / 'data/dev/uploads').mkdir(parents=True)
            with self.assertRaisesRegex(ValueError, 'local upload'):
                migration.source_files(root, 'data/dev/uploads')


class TransferContractTest(unittest.TestCase):
    def test_changed_source_manifest_is_refused_before_reading_payload(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve()
            root.chmod(0o700)
            (root / 'source.json').write_text('{}')
            (root / 'source.json').chmod(0o600)
            with self.assertRaisesRegex(ValueError, 'transfer checksum'):
                migration.verify_export(root, '0' * 64)

    def test_unknown_transfer_file_is_refused(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve()
            root.chmod(0o700)
            value = {'databaseVersion': '13.0.2-MariaDB', 'schemaHistory': [str(i) for i in range(1, 7)],
                     'kind': 'ctc-native-mac', 'sourceApplicationVersion': None, 'format': 1, 'files': {}}
            (root / 'source.json').write_text(json.dumps(value))
            (root / 'source.json').chmod(0o600)
            (root / 'unknown').write_text('unknown')
            checksum = hashlib.sha256((root / 'source.json').read_bytes()).hexdigest()
            with self.assertRaisesRegex(ValueError, 'export inventory'):
                migration.verify_export(root, checksum)

    def test_sealed_native_payload_survives_transfer_and_detects_changed_secret(self):
        import tarfile
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve()
            root.chmod(0o700)
            names = ['database.sql', 'data.tar', 'application-local.yml', '.env', '.env.local', 'google.json']
            for name in names:
                (root / name).write_text('private fixture')
                (root / name).chmod(0o600)
            with tarfile.open(root / 'data.tar', 'w:') as archive:
                entry = tarfile.TarInfo('uploads'); entry.type = tarfile.DIRTYPE; entry.mode = 0o700
                archive.addfile(entry)
            value = migration.source_contract({'databaseVersion': '13.0.2-MariaDB', 'schemaHistory': [str(i) for i in range(1, 7)]})
            value['files'] = {name: {'bytes': (root / name).stat().st_size,
                                     'sha256': hashlib.sha256((root / name).read_bytes()).hexdigest()} for name in names}
            (root / 'source.json').write_text(json.dumps(value))
            (root / 'source.json').chmod(0o600)
            checksum = hashlib.sha256((root / 'source.json').read_bytes()).hexdigest()
            self.assertEqual(migration.verify_export(root, checksum), value)
            (root / 'google.json').write_text('changed private fixture')
            with self.assertRaisesRegex(ValueError, 'artifact changed'):
                migration.verify_export(root, checksum)

    def test_source_link_refusal_preserves_data_and_creates_no_export(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve()
            (root / 'data/local/uploads').mkdir(parents=True)
            (root / 'data/local/uploads/original').write_text('unchanged')
            (root / 'data/local/uploads/link').symlink_to(root / 'data/local/uploads/original')
            with self.assertRaisesRegex(ValueError, 'source file link'):
                migration.source_files(root, 'data/local/uploads')
            self.assertEqual((root / 'data/local/uploads/original').read_text(), 'unchanged')

    def test_unpaused_target_is_refused_without_starting_or_pulling_containers(self):
        from unittest.mock import patch
        runtime = Mock()
        runtime.config = {'outboundPaused': False}
        with patch('migration.verify_export', return_value={}), self.assertRaisesRegex(ValueError, 'integrations paused'):
            migration.rehearse(runtime, '/unread-source', '0' * 64, '/unused-destination')
        runtime.docker.assert_not_called()

    def test_active_source_refusal_creates_no_partial_export(self):
        with tempfile.TemporaryDirectory() as temporary:
            destination = Path(temporary).resolve() / 'native-export'
            source = object.__new__(migration.MacSource)
            source.idle = Mock(side_effect=ValueError('source writer active'))
            with self.assertRaisesRegex(ValueError, 'source writer'):
                source.export(destination)
            self.assertFalse(destination.exists())

    def test_enabled_database_events_block_source_freeze(self):
        source = Mock()
        source.query.side_effect = [['1'], ['0'], ['1']]
        with self.assertRaisesRegex(ValueError, 'source writer'):
            migration.assert_source_idle(source.query)

    def test_missing_google_credentials_prevent_native_inventory(self):
        from types import SimpleNamespace
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve()
            (root / 'data/local/uploads').mkdir(parents=True)
            (root / 'src/main/resources').mkdir(parents=True)
            (root / 'src/main/resources/application-local.yml').write_text(json.dumps({
                'spring': {'datasource': {'url': 'jdbc:mariadb://localhost:3306/ctcdb', 'username': 'fixture', 'password': 'fixture'}},
                'app': {'upload-dir': 'data/local/uploads'}, 'server': {'port': 9091}}))
            (root / '.env').write_text('DISCORD_BOT_TOKEN=fixture\n')
            (root / '.env.local').write_text('GOOGLE_CALENDAR_ID=fixture\n')
            with patch.dict(sys.modules, {'yaml': SimpleNamespace(safe_load=json.loads)}), self.assertRaisesRegex(ValueError, 'missing or unsafe native configuration'):
                migration.MacSource(root)

    def test_source_observer_without_global_process_visibility_is_refused(self):
        source = Mock()
        source.query.return_value = ['0']
        with self.assertRaisesRegex(ValueError, 'process visibility'):
            migration.assert_source_idle(source.query)

    def test_source_observer_must_connect_to_the_same_native_database(self):
        source = object.__new__(migration.MacSource)
        source.subprocess = Mock()
        source.subprocess.run.return_value.returncode = 1
        source.query = Mock(side_effect=[['3306\t/source\tctcdb'], ['3307\t/other\tctcdb']])
        with self.assertRaisesRegex(ValueError, 'another database instance'):
            source.idle()

    def test_verified_source_observer_sees_all_users_without_changing_privileges(self):
        source = object.__new__(migration.MacSource)
        source.subprocess = Mock()
        source.subprocess.run.return_value.returncode = 1
        source.query = Mock(side_effect=[['3306\t/source\tctcdb'], ['3306\t/source\tctcdb'], ['1'], ['0'], ['0']])
        source.idle()
        self.assertTrue(all(call.kwargs.get('observer') for call in source.query.call_args_list[1:]))
        self.assertTrue(all('GRANT ' not in call.args[0] for call in source.query.call_args_list))


class CutoverBoundaryTest(unittest.TestCase):
    def setUp(self):
        import uuid
        from unittest.mock import patch
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
        self.root.chmod(0o700)
        (self.root / 'state').mkdir(mode=0o700)
        self.unit = self.root / 'unit'; self.unit.mkdir(mode=0o700)
        (self.unit / 'manifest.json').write_text('{}'); (self.unit / 'manifest.json').chmod(0o600)
        self.identity = str(uuid.uuid4()); self.unit_id = str(uuid.uuid4())
        self.unit_hash = hashlib.sha256(b'{}').hexdigest()
        self.runtime = Mock()
        self.runtime.root = self.root
        self.runtime.config = {'outboundPaused': True, 'installationId': self.identity,
                               'storageGeneration': 'recovery-real-cutover'}
        self.runtime.release = {'version': '1.26.0'}
        self.runtime.database_metadata.return_value = {'records': {'ctc_runtime_qualification': 1,
                                        'teams': 0, 'drivers': 0, 'seasons': 0, 'races': 0}}
        self.manifest = {'id': self.unit_id, 'installationId': self.identity, 'releaseVersion': '1.26.0'}
        proof = {'stage': 'rehearsed', 'sourceManifestSha256': 'a' * 64, 'unit': str(self.unit), 'manifestSha256': self.unit_hash}
        self.proof = self.root / 'state' / ('migration-' + self.unit_id + '.json')
        self.proof.write_text(json.dumps(proof)); self.proof.chmod(0o600)
        self.authority = self.root / 'state/production-authority.json'
        self.patchers = [patch('migration.verify_export', return_value={'columns': {'teams': ['id']}, 'projections': {'teams': 'verified'}}),
                         patch('recovery.validate_unit', return_value=self.manifest),
                         patch('migration.projection', return_value={'teams': 'verified'})]
        for patcher in self.patchers:
            patcher.start(); self.addCleanup(patcher.stop)

    def execute(self):
        return migration.cutover(self.runtime, self.root / 'unchanged-native-export', 'a' * 64, self.unit,
                                 'ctc-manager:' + self.identity + ':recovery-real-cutover')

    def test_pre_write_failure_keeps_unchanged_source_eligible(self):
        from unittest.mock import patch
        with patch('live.restore', side_effect=ValueError('before activation')), self.assertRaisesRegex(ValueError, 'before activation'):
            self.execute()
        receipt = json.loads(self.authority.read_text())
        self.assertEqual(receipt['stage'], 'failed-before-writes')
        self.runtime.stop_app.assert_not_called()
        self.assertEqual(migration.before_write_rollback(receipt), 'unchanged-source')

    def test_post_write_failure_requires_complete_reverse_migration(self):
        from unittest.mock import patch
        def fail_after_activation(*args, before_activation):
            before_activation()
            self.assertEqual(json.loads(self.authority.read_text())['stage'], 'writes-possible')
            raise ValueError('after activation')
        with patch('live.restore', side_effect=fail_after_activation), self.assertRaisesRegex(ValueError, 'after activation'):
            self.execute()
        receipt = json.loads(self.authority.read_text())
        self.assertEqual(receipt['stage'], 'failed-after-writes')
        self.runtime.stop_app.assert_called_once_with()
        with self.assertRaisesRegex(ValueError, 'reverse migration'):
            migration.before_write_rollback(receipt)

    def test_success_stays_paused_until_separate_backup_and_acceptance(self):
        from unittest.mock import patch
        def activate(*args, before_activation):
            before_activation()
            self.assertEqual(json.loads(self.authority.read_text())['stage'], 'writes-possible')
            return {'stage': 'active-integrations-paused'}
        with patch('live.restore', side_effect=activate):
            result = self.execute()
        self.assertEqual(result['stage'], 'active-awaiting-acceptance')
        self.assertTrue(result['outboundPaused'])

    def test_existing_authority_prevents_a_second_cutover(self):
        from unittest.mock import patch
        self.authority.write_text('{"stage":"authoritative"}')
        with patch('live.restore') as restore, self.assertRaisesRegex(ValueError, 'authority already recorded'):
            self.execute()
        restore.assert_not_called()
        self.runtime.preflight.assert_not_called()

    def test_changed_proof_prevents_target_activation(self):
        from unittest.mock import patch
        proof = json.loads(self.proof.read_text()); proof['sourceManifestSha256'] = 'b' * 64
        self.proof.write_text(json.dumps(proof))
        with patch('live.restore') as restore, self.assertRaisesRegex(ValueError, 'exact source'):
            self.execute()
        restore.assert_not_called()
        self.assertFalse(self.authority.exists())

    def test_existing_real_target_data_prevents_initial_migration(self):
        from unittest.mock import patch
        self.runtime.database_metadata.return_value['records']['teams'] = 1
        with patch('live.restore') as restore, self.assertRaisesRegex(ValueError, 'empty paused qualification'):
            self.execute()
        restore.assert_not_called()
        self.assertFalse(self.authority.exists())


class RehearsalGuardTest(unittest.TestCase):
    def setUp(self):
        import uuid
        from unittest.mock import patch
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
        self.root.chmod(0o700)
        (self.root / 'state').mkdir(mode=0o700)
        self.source = self.root / 'source'; self.source.mkdir(mode=0o700)
        self.destination = self.root / 'backups'; self.destination.mkdir(mode=0o700)
        self.release = self.root / 'release'; self.release.mkdir(mode=0o700)
        (self.release / 'manifest.json').write_text('{}')
        for name, value in {'database.sql': 'source SQL', 'data.tar': 'source files', 'google.json': '{}',
                            '.env': 'DISCORD_BOT_TOKEN=fixture\nGOOGLE_CALENDAR_ID=fixture\n', '.env.local': ''}.items():
            (self.source / name).write_text(value)
        for name, value in {'db-password': 'fixture', 'db-root-password': 'fixture',
                            'app.env': 'SPRING_SECURITY_USER_NAME=owner\nSPRING_SECURITY_USER_PASSWORD=fixture\n'}.items():
            (self.root / name).write_text(value); (self.root / name).chmod(0o600)
        self.runtime = Mock(root=self.root, deploy_root=self.release)
        self.runtime.config = {'outboundPaused': True, 'installationId': str(uuid.uuid4()),
                               'databasePasswordFile': str(self.root / 'db-password'),
                               'databaseRootPasswordFile': str(self.root / 'db-root-password'),
                               'appEnvFile': str(self.root / 'app.env')}
        self.runtime.release = {'version': '1.26.0', 'appImage': 'ghcr.io/jegr78/ctc-manager@sha256:' + 'a' * 64,
                                'databaseImage': 'mariadb@sha256:' + 'b' * 64}
        self.runtime.docker.return_value = json.dumps([{'Config': {'Labels': {'org.ctc.recovery.version': '1'}}}]).encode()
        self.metadata = {'databaseVersion': '13.0.2-MariaDB', 'columns': {'teams': ['id']}, 'projections': {'teams': 'original'}}
        self.probe = Mock()
        self.probe.prefix = 'ctc-recovery-fixture'
        self.version = '13.0.2-MariaDB'
        self.history = [json.dumps({'rank': i, 'version': str(i), 'checksum': None}) for i in range(1, 22)]
        def query(sql):
            if sql == 'SELECT VERSION();': return [self.version]
            if 'JSON_OBJECT' in sql: return self.history
            if 'information_schema.TABLES' in sql: return ['teams', 'flyway_schema_history']
            if 'COUNT(*)' in sql: return ['0']
            raise AssertionError('unexpected rehearsal SQL')
        self.probe.query.side_effect = query
        def docker(arguments, **kwargs):
            if kwargs.get('stdout'):
                kwargs['stdout'].write(b'migrated SQL')
            return b''
        self.probe.docker.side_effect = docker
        self.projection = patch('migration.projection', return_value={'teams': 'original'})
        self.project = self.projection.start(); self.addCleanup(self.projection.stop)
        patchers = [patch('migration.verify_export', return_value=self.metadata), patch('probe.Probe', return_value=self.probe),
                    patch('recovery.validate_unit', return_value={}), patch('recovery.restore_check', return_value={'run': 'fixture-proof'})]
        for patcher in patchers:
            patcher.start(); self.addCleanup(patcher.stop)

    def execute(self):
        return migration.rehearse(self.runtime, self.source, 'a' * 64, self.destination)

    def test_import_must_preserve_all_source_column_projections_before_application_start(self):
        self.project.side_effect = [{'teams': 'changed'}, {'teams': 'original'}]
        with self.assertRaisesRegex(ValueError, 'import changed source rows'):
            self.execute()
        self.probe.app_arguments.assert_not_called()
        self.probe.cleanup.assert_called_once_with()
        self.assertFalse(list(self.destination.glob('recovery-*')))

    def test_target_migrations_must_preserve_all_original_columns(self):
        self.project.side_effect = [{'teams': 'original'}, {'teams': 'changed'}]
        with self.assertRaisesRegex(ValueError, 'migration changed source rows'):
            self.execute()
        self.probe.verify_application.assert_called_once()
        self.probe.cleanup.assert_called_once_with()
        self.assertFalse(list(self.destination.glob('recovery-*')))

    def test_incompatible_target_database_fails_before_import(self):
        self.version = '13.1.0-MariaDB'
        with self.assertRaisesRegex(ValueError, 'qualified native major'):
            self.execute()
        self.probe.import_database.assert_not_called()
        self.probe.cleanup.assert_called_once_with()

    def test_unknown_target_schema_history_fails_before_unit_publication(self):
        self.history = self.history[:-1]
        with self.assertRaisesRegex(ValueError, 'target migration history'):
            self.execute()
        self.probe.cleanup.assert_called_once_with()
        self.assertFalse(list(self.destination.glob('recovery-*')))

    def test_matching_data_is_sealed_then_proven_without_live_target_mutation(self):
        unit = self.execute()
        self.assertTrue((unit / 'manifest.json').is_file())
        self.probe.cleanup.assert_called_once_with()
        self.runtime.compose.assert_not_called()
        self.assertTrue(self.runtime.config['outboundPaused'])


class NativeSourceFixture(unittest.TestCase):
    def setUp(self):
        from types import SimpleNamespace
        from unittest.mock import patch
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
        self.root.chmod(0o700)
        (self.root / 'data/local/uploads').mkdir(parents=True)
        (self.root / 'src/main/resources').mkdir(parents=True)
        self.settings = {'spring': {'datasource': {'url': 'jdbc:mariadb://localhost:3306/ctcdb', 'username': 'fixture', 'password': 'fixture'}},
                         'app': {'upload-dir': 'data/local/uploads'}, 'server': {'port': 9091}}
        self.profile = self.root / 'src/main/resources/application-local.yml'
        (self.root / '.env').write_text('DISCORD_BOT_TOKEN=fixture\n')
        (self.root / '.env.local').write_text('GOOGLE_CALENDAR_ID=fixture\n')
        self.credential = {'type': 'service_account', 'client_email': 'fixture@example.invalid', 'private_key': 'not-a-real-key'}
        self.yaml = patch.dict(sys.modules, {'yaml': SimpleNamespace(safe_load=json.loads)})
        self.yaml.start(); self.addCleanup(self.yaml.stop)

    def construct(self, **kwargs):
        self.profile.write_text(json.dumps(self.settings))
        (self.root / 'google-credentials.json').write_text(json.dumps(self.credential))
        return migration.MacSource(self.root, **kwargs)


class NativeInputGuardTest(NativeSourceFixture):
    def test_native_source_requires_explicit_loopback_ctc_database(self):
        for url in ['jdbc:mysql://localhost/ctcdb', 'jdbc:mariadb://outside.invalid/ctcdb',
                    'jdbc:mariadb://localhost/other', 'jdbc:mariadb://user:password@localhost/ctcdb',
                    'jdbc:mariadb://localhost/ctcdb?localSocket=/tmp/other.sock']:
            self.settings['spring']['datasource']['url'] = url
            with self.subTest(url=url), self.assertRaisesRegex(ValueError, 'loopback database'):
                self.construct()

    def test_native_source_port_and_credentials_must_match_supported_layout(self):
        self.settings['server']['port'] = 9090
        with self.assertRaisesRegex(ValueError, 'source port'):
            self.construct()
        self.settings['server']['port'] = 9091
        for field in ['username', 'password']:
            original = self.settings['spring']['datasource'][field]
            self.settings['spring']['datasource'][field] = ''
            with self.subTest(field=field), self.assertRaisesRegex(ValueError, 'explicit native database credentials'):
                self.construct()
            self.settings['spring']['datasource'][field] = original

    def test_google_credential_requires_complete_service_account_material(self):
        for field in ['type', 'client_email', 'private_key']:
            original = self.credential[field]
            self.credential[field] = ''
            with self.subTest(field=field), self.assertRaisesRegex(ValueError, 'incomplete native Google'):
                self.construct()
            self.credential[field] = original

    def test_observer_name_is_fixed_native_account_name(self):
        with self.assertRaisesRegex(ValueError, 'observer name'):
            self.construct(observer_user='$(not-a-command)')

    def test_source_file_hardlinks_and_wrong_owners_are_refused(self):
        import os
        from unittest.mock import patch
        uploads = self.root / 'data/local/uploads'
        original = uploads / 'original'; original.write_text('unchanged')
        link = uploads / 'hardlink'; os.link(original, link)
        with self.assertRaisesRegex(ValueError, 'hardlink'):
            migration.source_files(self.root, 'data/local/uploads')
        link.unlink()
        with patch('migration.os.getuid', return_value=os.getuid()+1), self.assertRaisesRegex(ValueError, 'owner or type'):
            migration.source_files(self.root, 'data/local/uploads')
        self.assertEqual(original.read_text(), 'unchanged')

    def test_absent_upload_root_is_not_a_successful_empty_export(self):
        (self.root / 'data/local/uploads').rmdir()
        with self.assertRaisesRegex(ValueError, 'upload directory is missing'):
            migration.source_files(self.root, 'data/local/uploads')

    def test_native_http_listener_prevents_freeze_before_database_reads(self):
        source = object.__new__(migration.MacSource)
        source.subprocess = Mock()
        source.subprocess.run.return_value.returncode = 0
        source.query = Mock()
        with self.assertRaisesRegex(ValueError, 'source writer may still listen'):
            source.idle()
        source.query.assert_not_called()

    def test_mutating_cli_needs_its_launcher_parent_lock(self):
        import contextlib, io, os
        from unittest.mock import patch
        (self.root / '.ctc-manager.lock').mkdir(mode=0o700)
        owner = self.root / '.ctc-manager.lock/owner'
        owner.write_text(str(os.getppid()+1)); owner.chmod(0o600)
        unit = self.root / 'unit'; unit.mkdir(mode=0o700)
        (unit / 'manifest.json').write_text('{}')
        runtime = Mock(root=self.root)
        arguments = ['migration.py', 'rehearse', '--directory', str(self.root), '--source-export', str(self.root),
                     '--source-sha256', 'a'*64, '--destination', str(self.root)]
        with patch('sys.argv', arguments), patch('recovery.NativeRuntime', return_value=runtime), \
                patch('migration.rehearse', return_value=unit) as rehearse, contextlib.redirect_stdout(io.StringIO()):
            with self.assertRaises(SystemExit) as error:
                migration.main()
        self.assertIn('refused', str(error.exception))
        rehearse.assert_not_called()


class FrozenCaptureTest(NativeSourceFixture):
    def setUp(self):
        super().setUp()
        from types import SimpleNamespace
        from unittest.mock import patch
        self.source = self.construct()
        self.asset = self.root / 'data/local/uploads/asset.bin'
        self.asset.write_bytes(b'original')
        self.source.idle = Mock()
        self.metadata = {'databaseVersion': '13.0.2-MariaDB', 'schemaHistory': [str(i) for i in range(1, 7)], 'records': {'teams': 33}}
        self.source.metadata = Mock(return_value=self.metadata)
        def dump(arguments, **kwargs):
            kwargs['stdout'].write(b'-- private synthetic SQL\n')
            return SimpleNamespace(returncode=0)
        self.source.subprocess = SimpleNamespace(run=dump, PIPE=-1)
        self.destination = self.root / 'export'

    def test_database_drift_never_seals_a_successful_source_export(self):
        self.source.metadata.side_effect = [self.metadata, {**self.metadata, 'records': {'teams': 34}}]
        with self.assertRaisesRegex(ValueError, 'source changed during export'):
            self.source.export(self.destination)
        self.assertFalse((self.destination / 'source.json').exists())
        self.assertEqual(self.asset.read_bytes(), b'original')

    def test_changed_archive_bytes_are_refused_even_when_source_is_restored(self):
        import tarfile
        from unittest.mock import patch
        original = tarfile.TarFile.add
        def changing_add(archive, path, **kwargs):
            if Path(path) != self.asset:
                return original(archive, path, **kwargs)
            self.asset.write_bytes(b'changed during capture')
            try:
                return original(archive, path, **kwargs)
            finally:
                self.asset.write_bytes(b'original')
        with patch('tarfile.TarFile.add', changing_add), self.assertRaisesRegex(ValueError, 'files changed while archiving'):
            self.source.export(self.destination)
        self.assertFalse((self.destination / 'source.json').exists())
        self.assertEqual(self.asset.read_bytes(), b'original')

    def test_changed_configuration_copy_is_refused_without_touching_source(self):
        import shutil
        from unittest.mock import patch
        original = shutil.copyfile
        def corrupt_copy(source, destination):
            result = original(source, destination)
            if destination.name == 'google.json':
                destination.write_text('corrupted copy')
            return result
        with patch('shutil.copyfile', corrupt_copy), self.assertRaisesRegex(ValueError, 'configuration changed while copying'):
            self.source.export(self.destination)
        self.assertFalse((self.destination / 'source.json').exists())
        self.assertEqual(self.asset.read_bytes(), b'original')

    def changed_after_copy(self, change):
        import shutil
        from unittest.mock import patch
        original = shutil.copyfile
        def copy_then_change(source, destination):
            result = original(source, destination)
            if destination.name == 'google.json':
                change()
            return result
        return patch('shutil.copyfile', copy_then_change)

    def test_new_source_file_after_archive_prevents_false_freeze(self):
        with self.changed_after_copy(lambda: (self.asset.parent / 'new-file').write_text('new write')), \
                self.assertRaisesRegex(ValueError, 'source changed during export'):
            self.source.export(self.destination)
        self.assertFalse((self.destination / 'source.json').exists())

    def test_new_source_bytes_after_archive_prevent_false_freeze(self):
        with self.changed_after_copy(lambda: self.asset.write_bytes(b'new write')), \
                self.assertRaisesRegex(ValueError, 'source changed during export'):
            self.source.export(self.destination)
        self.assertFalse((self.destination / 'source.json').exists())

    def test_reappearing_writer_prevents_sealing_after_dump(self):
        self.source.idle.side_effect = [None, ValueError('source writer returned')]
        with self.assertRaisesRegex(ValueError, 'source writer returned'):
            self.source.export(self.destination)
        self.assertFalse((self.destination / 'source.json').exists())
