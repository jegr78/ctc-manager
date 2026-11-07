import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import rollout


class PhpMyAdminTest(unittest.TestCase):
    def fake(self, state, fail_start=False):
        calls = []
        def run(command, **kwargs):
            calls.append(command[2])
            if command[2] == 'start' and fail_start:
                raise subprocess.CalledProcessError(1, command)
            return SimpleNamespace(stdout=state + '\n', returncode=0)
        return calls, run

    def test_phpmyadmin_restarts_only_if_it_ran(self):
        for state, expected in [('inactive', ['show']), ('active', ['show', 'stop', 'start'])]:
            with self.subTest(state=state):
                calls, run = self.fake(state)
                with self.assertRaisesRegex(RuntimeError, 'body'):
                    with rollout.phpmyadmin_stopped(run):
                        raise RuntimeError('body')
                self.assertEqual(expected, calls)

    def test_failed_start_after_a_failure_keeps_the_original_error(self):
        calls, run = self.fake('active', fail_start=True)
        with self.assertRaisesRegex(RuntimeError, 'body') as caught:
            with rollout.phpmyadmin_stopped(run):
                raise RuntimeError('body')
        self.assertTrue(any('phpMyAdmin' in note for note in caught.exception.__notes__))

    def test_failed_start_after_success_only_warns(self):
        import contextlib, io
        calls, run = self.fake('active', fail_start=True)
        stderr = io.StringIO()
        with contextlib.redirect_stderr(stderr):
            with rollout.phpmyadmin_stopped(run):
                pass
        self.assertIn('phpMyAdmin', stderr.getvalue())


ROOT = Path(__file__).resolve().parents[2]
DIGEST = 'sha256:' + 'a' * 64
DATABASE = (ROOT / 'deploy/database-image').read_text().strip()


class FakeRuntime:
    """Records Docker-level calls of one installation and release."""
    def __init__(self, case, root, deploy_root):
        self.case, self.root, self.deploy_root = case, Path(root), Path(deploy_root).resolve()
        self.config = json.loads((self.root / 'config/installation.json').read_text())
        self.version = self.deploy_root.name
        self.release = json.loads((self.deploy_root / 'manifest.json').read_text())
        self.diagnostic = None

    def record(self, name, *details):
        self.case.log.append((name, self.version, *details))
        if name in self.case.failing or (name, self.version) in self.case.failing:
            raise ValueError(name + ' failed')

    def docker(self, arguments, **kwargs):
        self.record('docker', *arguments[:2])
        if arguments[:2] == ['image', 'inspect']:
            image = arguments[2]
            version = self.case.database_versions.get(image, '11.8.2') if image.startswith('mariadb') else ''
            return json.dumps([{'Config': {'User': 'ctc', 'Labels': {'org.ctc.recovery.version': '1',
                                'org.opencontainers.image.version': version}}, 'Id': 'sha256:' + 'c' * 64}]).encode()
        if arguments[:1] == ['run'] and arguments[-1] == '-u':
            return (self.case.uids.get(arguments[-2], '1000') + '\n').encode()
        return b''

    def compose(self, arguments):
        self.record('compose', *arguments)
        return b''

    def container(self, service):
        return {'Id': service + '-id', 'State': {'Running': True}}

    def stop_app(self): self.record('stop_app')
    def start_app(self): self.record('start_app')
    def is_app_running(self): return True

    def database_metadata(self):
        return {'databaseVersion': '11.8.2-MariaDB'}
    def preflight(self, allow_missing_app=False):
        self.record('preflight')
        if self.case.phpmyadmin_running:
            raise ValueError('unexpected writer on private database network')
        self.db = self.container('db')
    def assert_no_other_writers(self): self.record('writers')
    def check_destination(self, destination): self.record('check_destination')

    def query(self, sql):
        self.db['Id']
        self.record('query')
        return self.case.history.get(self.version, [])

    def systemctl(self, command, **kwargs):
        self.record('systemctl', *command[2:3])
        return SimpleNamespace(returncode=0, stdout='active\n' if self.case.phpmyadmin_active else 'inactive\n')


class RolloutCase(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name).resolve()
        self.root = self.base / 'installation'
        for name in ['', 'config', 'secrets', 'state', 'releases']:
            (self.root / name).mkdir(mode=0o700, exist_ok=True)
        self.config = {'format': 1, 'project': 'ctc-manager', 'dockerHost': 'unix:///run/user/1002/docker.sock',
                       'storageGeneration': 'recovery-20261101', 'outboundPaused': False,
                       'installationId': '11111111-1111-1111-1111-111111111111',
                       'appEnvFile': str(self.root / 'secrets/app.env'),
                       'databasePasswordFile': str(self.root / 'secrets/db-password'),
                       'databaseRootPasswordFile': str(self.root / 'secrets/db-root-password'),
                       'googleCredentialsFile': str(self.root / 'secrets/google.json')}
        for name in ['app.env', 'db-password', 'db-root-password', 'google.json']:
            (self.root / 'secrets' / name).write_text('current ' + name)
            (self.root / 'secrets' / name).chmod(0o600)
        self.log, self.failing, self.history = [], set(), {}
        self.database_versions, self.uids, self.captured = {}, {}, 0
        self.phpmyadmin_active = True
        self.phpmyadmin_running = False
        home = patch.object(Path, 'home', return_value=self.base / 'home')
        home.start()
        self.addCleanup(home.stop)
        self.write_config()
        self.old = self.release('1.51.0')
        rollout.switch_release(self.root, self.old, outbound_paused=False)

    def release(self, version, app='a'):
        output = self.base / ('dist-' + version)
        subprocess.run(['python3', str(ROOT / 'scripts/ci/deployment-bundle.py'), 'create', '--version', version,
                        '--app-digest', 'sha256:' + app * 64, '--database-digest', DATABASE.split('@')[1],
                        '--output', str(output)], check=True, capture_output=True)
        target = self.root / 'releases' / version
        prefix = 'ctc-manager-deployment-' + version + '/'
        with tarfile.open(output / (prefix[:-1] + '.tar.gz')) as archive:
            for member in archive:
                path = target / member.name.removeprefix(prefix)
                path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
                path.write_bytes(archive.extractfile(member).read())
                path.chmod(0o755 if member.mode & 0o100 else 0o644)
        target.chmod(0o700)
        return target

    def write_config(self):
        rollout.atomic_json(self.root / 'config/installation.json', self.config)

    def runtime_for(self, root, deploy_root):
        return FakeRuntime(self, root, deploy_root)

    def unit(self, release):
        unit = self.base / 'units' / ('recovery-update-' + release.name)
        unit.mkdir(parents=True)
        for name in ['app.env', 'db-password', 'db-root-password', 'google.json']:
            (unit / name).write_text('unit ' + name)
        (unit / 'database.sql').write_text('-- dump')
        (unit / 'data.tar').write_bytes(b'')
        shutil.copyfile(release / 'manifest.json', unit / 'release.json')
        return unit

    def names(self):
        return [entry[0] if entry[0] not in ('compose', 'docker') else entry[0] + ' ' + entry[2] for entry in self.log]

    def config_now(self):
        return json.loads((self.root / 'config/installation.json').read_text())


class RestoreTest(RolloutCase):
    def restore(self, unit):
        with patch.object(rollout, 'validate_unit', return_value={'installationId': self.config['installationId']}):
            return rollout.restore(self.root, unit, runtime_for=self.runtime_for)

    def test_switch_release_updates_symlink_images_and_config_together(self):
        newer = self.release('1.52.0', app='b')
        rollout.switch_release(self.root, newer, outbound_paused=True)
        config = self.config_now()
        self.assertEqual(newer.resolve(), (self.root / 'current').resolve())
        self.assertEqual('ghcr.io/jegr78/ctc-manager@sha256:' + 'b' * 64, config['appImage'])
        self.assertTrue(config['outboundPaused'])
        self.assertEqual('recovery-20261101', config['storageGeneration'], 'the generation stays the permanent prefix')

    def test_restore_recreates_volumes_and_restores_dump_data_and_secrets(self):
        self.restore(self.unit(self.old))
        self.assertEqual(['compose rm', 'docker volume', 'compose up', 'docker exec', 'docker run',
                          'docker run', 'compose up'], self.names())
        removed = next(entry for entry in self.log if entry[:3] == ('docker', '1.51.0', 'volume'))
        self.assertIsNotNone(removed)
        self.assertEqual('unit app.env', (self.root / 'secrets/app.env').read_text())

    def test_restore_switches_to_the_units_release_and_keeps_outbound_paused(self):
        newer = self.release('1.52.0', app='b')
        rollout.switch_release(self.root, newer, outbound_paused=False)
        self.restore(self.unit(self.old))
        self.assertEqual(self.old.resolve(), (self.root / 'current').resolve())
        self.assertTrue(self.config_now()['outboundPaused'], 'a restore must not post to Discord before review')

    def test_restore_refuses_unit_without_matching_release(self):
        unit = self.unit(self.old)
        shutil.rmtree(self.old)
        with self.assertRaises((ValueError, OSError)):
            self.restore(unit)
        self.assertEqual([], self.log)

    def test_restore_works_without_an_app_container(self):
        self.failing.add('stop_app')
        self.restore(self.unit(self.old))
        self.assertEqual('1.51.0', (self.root / 'current').resolve().name)

    def test_legacy_release_restores_only_on_request(self):
        legacy = DeployTest.legacy_release(self)
        with patch.object(rollout, 'validate_unit', return_value={'installationId': self.config['installationId']}):
            rollout.restore(self.root, self.unit(legacy), runtime_for=self.runtime_for, allow_legacy=True)
        self.assertEqual('1.50.0', (self.root / 'current').resolve().name)

    def test_restore_refuses_release_without_rollout(self):
        legacy = self.release('1.50.0')
        manifest = json.loads((legacy / 'manifest.json').read_text())
        del manifest['files']['lib/rollout.py']
        (legacy / 'lib/rollout.py').unlink()
        (legacy / 'manifest.json').write_text(json.dumps(manifest))
        unit = self.unit(legacy)
        with self.assertRaisesRegex(ValueError, '--legacy-release'):
            self.restore(unit)
        self.assertEqual([], self.log)


class DeployTest(RolloutCase):
    def capture(self, runtime, destination, hold_stopped=False):
        runtime.record('capture', hold_stopped)
        self.captured += 1
        unit = self.base / 'units' / ('recovery-2026-' + str(self.captured))
        shutil.rmtree(unit, ignore_errors=True)
        self.unit(runtime.deploy_root)
        shutil.move(self.base / 'units' / ('recovery-update-' + runtime.deploy_root.name), unit)
        return unit

    def systemctl(self, command, **kwargs):
        self.log.append(('systemctl', command[2]))
        if ('systemctl', command[2]) in self.failing:
            raise subprocess.CalledProcessError(1, command)
        if command[2] in ('stop', 'start'):
            self.phpmyadmin_running = command[2] == 'start'
        return SimpleNamespace(returncode=0, stdout='active\n' if self.phpmyadmin_active else 'inactive\n')

    def deploy(self, version, **options):
        options.setdefault('fetch', lambda url: json.dumps([{'tag_name': 'v1.52.0', 'draft': False, 'prerelease': False}]).encode())
        with patch.object(rollout, 'capture', self.capture), \
                patch.object(rollout, 'validate_unit', return_value={'installationId': self.config['installationId']}), \
                patch.object(rollout, 'UNIT_ROOT', self.base / 'units'):
            (self.base / 'units').mkdir(exist_ok=True)
            return rollout.deploy(self.root, version, runtime_for=self.runtime_for, run=self.systemctl,
                                  home=self.base / 'home', **options)

    def current(self):
        return (self.root / 'current').resolve().name

    def test_deploy_success_switches_release_and_restores_outbound(self):
        self.release('1.52.0', app='b')
        result = self.deploy('1.52.0')
        self.assertEqual('1.52.0', self.current())
        self.assertFalse(self.config_now()['outboundPaused'], 'the previous outbound setting returns after success')
        self.assertIn(('capture', '1.51.0', True), self.log)
        self.assertEqual(('systemctl', 'start'), self.log[-1], 'phpMyAdmin starts again last')
        self.assertTrue(result['unit'].split('/')[-1].startswith('recovery-update-'))

    def test_older_or_equal_version_is_refused_without_stopping(self):
        self.release('1.50.0')
        for version in ['1.50.0', '1.51.0']:
            with self.subTest(version=version), self.assertRaisesRegex(rollout.DeployFailed, 'nothing was changed'):
                self.deploy(version)
        self.assertNotIn('capture', self.names())

    def test_database_major_change_needs_the_flag(self):
        self.release('1.52.0', app='b')
        self.database_versions[json.loads((self.root / 'releases/1.52.0/manifest.json').read_text())['databaseImage']] = '12.0.1'
        with self.assertRaisesRegex(rollout.DeployFailed, 'allow-database-upgrade'):
            self.deploy('1.52.0')
        self.deploy('1.52.0', allow_database_upgrade=True)
        self.assertEqual('1.52.0', self.current())

    def test_uid_change_is_refused(self):
        self.release('1.52.0', app='b')
        self.uids['ghcr.io/jegr78/ctc-manager@sha256:' + 'b' * 64] = '1001'
        with self.assertRaisesRegex(rollout.DeployFailed, 'UID'):
            self.deploy('1.52.0')

    def test_missing_backup_device_keeps_the_app_running(self):
        self.release('1.52.0', app='b')
        self.failing.add('check_destination')
        with self.assertRaisesRegex(rollout.DeployFailed, 'nothing was changed'):
            self.deploy('1.52.0')
        self.assertNotIn('stop_app', self.names())

    def test_history_change_restores_unit(self):
        self.release('1.52.0', app='b')
        self.history['1.52.0'] = ['{"rank": 37}']
        self.drill_like_health_failure()
        self.assertEqual('1.51.0', self.current())
        self.assertIn(('docker', '1.51.0', 'exec', '-i'), self.log, 'a migrated database comes back from the update unit')
        self.assertTrue(self.config_now()['outboundPaused'], 'integrations stay paused after a rollback')

    def test_failed_migration_counts_as_migrated(self):
        self.release('1.52.0', app='b')
        self.history['1.52.0'] = ['{"rank": 37, "success": 0}']
        self.drill_like_health_failure()
        self.assertIn(('docker', '1.51.0', 'exec', '-i'), self.log)

    def drill_like_health_failure(self):
        original = FakeRuntime.compose
        def compose(runtime, arguments):
            original(runtime, arguments)
            if runtime.version == '1.52.0' and arguments[:1] == ['up']:
                raise ValueError('health failed')
            return b''
        with patch.object(FakeRuntime, 'compose', compose), self.assertRaises(rollout.DeployFailed):
            self.deploy('1.52.0')

    def test_failure_without_history_change_switches_back(self):
        self.release('1.52.0', app='b')
        self.drill_like_health_failure()
        self.assertEqual('1.51.0', self.current())
        self.assertNotIn(('docker', '1.51.0', 'exec', '-i'), self.log)

    def test_failed_rollback_leaves_the_app_stopped(self):
        self.release('1.52.0', app='b')
        self.failing.update({('compose', '1.52.0'), ('compose', '1.51.0')})
        with self.assertRaises(rollout.DeployFailed) as caught:
            self.deploy('1.52.0')
        self.assertFalse(caught.exception.rolled_back)
        self.assertIn(str(self.root / 'releases/1.52.0/ctc-manager') + ' --directory ' + str(self.root)
                      + ' restore --unit ' + str(self.base / 'units/recovery-update-2026-1'), str(caught.exception))
        self.assertNotIn('--legacy-release', str(caught.exception))

    def test_failed_rollback_to_a_legacy_release_names_the_legacy_restore(self):
        legacy = self.legacy_release()
        rollout.switch_release(self.root, legacy, outbound_paused=False)
        self.release('1.52.0', app='b')
        self.failing.update({('compose', '1.52.0'), ('compose', '1.50.0')})
        with self.assertRaises(rollout.DeployFailed) as caught:
            self.deploy('1.52.0')
        self.assertIn('restore --unit ' + str(self.base / 'units/recovery-update-2026-1') + ' --legacy-release',
                      str(caught.exception))

    def test_running_phpmyadmin_does_not_block_the_deploy(self):
        self.phpmyadmin_running = True
        self.release('1.52.0', app='b')
        self.deploy('1.52.0')
        self.assertEqual('1.52.0', self.current())
        self.assertTrue(self.phpmyadmin_running, 'phpMyAdmin runs again afterwards')

    def legacy_release(self):
        legacy = self.release('1.50.0')
        manifest = json.loads((legacy / 'manifest.json').read_text())
        for name in [n for n in manifest['files'] if n.startswith('phpmyadmin/') or n in ('lib/rollout.py', 'ctc-manager-launcher')]:
            del manifest['files'][name]
            (legacy / name).unlink()
        (legacy / 'manifest.json').write_text(json.dumps(manifest))
        return legacy

    def test_interrupt_rolls_back(self):
        self.release('1.52.0', app='b')
        original = rollout.switch_release
        calls = []
        def interrupted(*args, **kwargs):
            calls.append(args)
            if len(calls) == 1:
                raise KeyboardInterrupt()
            return original(*args, **kwargs)
        with patch.object(rollout, 'switch_release', interrupted), self.assertRaises(rollout.DeployFailed):
            self.deploy('1.52.0')
        self.assertEqual('1.51.0', self.current())

    def test_drill_only_on_the_running_version(self):
        self.release('1.52.0', app='b')
        with self.assertRaisesRegex(rollout.DeployFailed, 'running'):
            self.deploy('1.52.0', drill=True)
        with self.assertRaisesRegex(rollout.DeployFailed, 'rollback drill'):
            self.deploy('1.51.0', drill=True)
        self.assertEqual('1.51.0', self.current())

    def test_latest_picks_the_highest_published_version(self):
        self.release('1.52.0', app='b')
        listing = [{'tag_name': 'v1.9.0', 'draft': False, 'prerelease': False},
                   {'tag_name': 'v1.52.0', 'draft': False, 'prerelease': False},
                   {'tag_name': 'v1.60.0', 'draft': True, 'prerelease': False}]
        self.deploy('latest', fetch=lambda url: json.dumps(listing).encode())
        self.assertEqual('1.52.0', self.current())

    def test_phpmyadmin_stays_stopped_when_it_was_stopped(self):
        self.release('1.52.0', app='b')
        self.phpmyadmin_active = False
        self.deploy('1.52.0')
        self.assertEqual([('systemctl', 'show')], [e for e in self.log if e[0] == 'systemctl' and e[1] != 'daemon-reload'])

    def test_rollback_to_legacy_release_keeps_state_files(self):
        legacy = self.release('1.50.0')
        manifest = json.loads((legacy / 'manifest.json').read_text())
        for name in [n for n in manifest['files'] if n.startswith('phpmyadmin/') or n in ('lib/rollout.py', 'ctc-manager-launcher')]:
            del manifest['files'][name]
            (legacy / name).unlink()
        (legacy / 'manifest.json').write_text(json.dumps(manifest))
        rollout.switch_release(self.root, legacy, outbound_paused=False)
        (self.root / 'state/update.json').write_text('{"stage": "completed"}')
        self.release('1.52.0', app='b')
        self.history['1.52.0'] = ['{"rank": 37}']
        self.drill_like_health_failure()
        self.assertEqual('1.50.0', self.current())
        self.assertTrue((self.root / 'state/update.json').exists(), 'a failed first deploy keeps the legacy state')


if __name__ == '__main__':
    unittest.main()


class DockerListing:
    def __init__(self, config, volumes, users=None, failing=False):
        self.config, self.volumes, self.users, self.failing, self.calls = config, volumes, users or {}, failing, []

    def docker(self, arguments, **kwargs):
        self.calls.append(arguments)
        if self.failing and arguments[:2] == ['volume', 'rm']:
            raise ValueError('volume in use')
        if arguments[:2] == ['volume', 'ls']:
            return ('\n'.join(self.volumes) + '\n').encode()
        if arguments[:1] == ['ps']:
            return ('\n'.join(self.users.get(arguments[-1].removeprefix('volume='), [])) + '\n').encode()
        return b''


class HostFilesTest(RolloutCase):
    def setUp(self):
        super().setUp()
        self.home = self.base / 'home'
        self.units = self.home / '.config/systemd/user'
        self.new = self.release('1.52.0', app='b')

    def test_units_and_launcher_are_installed_privately(self):
        rollout.install_host_files(self.new)
        installed = sorted(path.name for path in self.units.iterdir())
        self.assertEqual(['ctc-manager-backup.service', 'ctc-manager-backup.timer', 'ctc-manager-phpmyadmin.service',
                          'ctc-manager-site-publisher.service', 'ctc-manager-site-publisher.timer'], installed)
        self.assertEqual(0o600, (self.units / 'ctc-manager-phpmyadmin.service').stat().st_mode & 0o777)
        launcher = self.home / '.local/bin/ctc-manager'
        self.assertEqual((self.new / 'ctc-manager-launcher').read_bytes(), launcher.read_bytes())
        self.assertEqual(0o700, launcher.stat().st_mode & 0o777)

    def test_symlink_target_is_refused_before_anything_is_written(self):
        self.units.mkdir(parents=True)
        (self.units / 'ctc-manager-backup.timer').symlink_to(self.base / 'elsewhere')
        with self.assertRaisesRegex(ValueError, 'symlink'):
            rollout.install_host_files(self.new)
        self.assertFalse((self.units / 'ctc-manager-backup.service').exists())

    def test_backup_drop_ins_are_removed(self):
        dropins = self.units / 'ctc-manager-backup.service.d'
        dropins.mkdir(parents=True)
        for name in ['phpmyadmin.conf', 'ordering.conf']:
            (dropins / name).write_text('[Unit]\n')
        rollout.install_host_files(self.new)
        self.assertFalse(dropins.exists(), 'the unit itself orders after the publisher now')

    def test_restore_host_files_brings_back_the_previous_files(self):
        dropins = self.units / 'ctc-manager-backup.service.d'
        dropins.mkdir(parents=True)
        (dropins / 'phpmyadmin.conf').write_text('old drop-in')
        (self.units / 'ctc-manager-backup.service').write_text('old unit')
        snapshot = rollout.snapshot_host_files(self.new)
        rollout.install_host_files(self.new)
        rollout.restore_host_files(snapshot)
        self.assertEqual('old unit', (self.units / 'ctc-manager-backup.service').read_text())
        self.assertEqual('old drop-in', (dropins / 'phpmyadmin.conf').read_text())
        self.assertFalse((self.home / '.local/bin/ctc-manager').exists(), 'a launcher that did not exist goes again')
        self.assertFalse((self.units / 'ctc-manager-phpmyadmin.service').exists())

    def legacy_tree(self):
        for name in ['old-a', 'recovery-20261101']:
            (self.root / 'secrets' / name).mkdir()
            (self.root / 'secrets' / name / 'app.env').write_text('x')
        self.config.update({key: str(self.root / 'secrets/recovery-20261101' / name) for key, name in
                            [('appEnvFile', 'app.env'), ('databasePasswordFile', 'db-password'),
                             ('databaseRootPasswordFile', 'db-root-password'), ('googleCredentialsFile', 'google.json')]})
        for name in ['operation.json', 'update.json', 'update-before-1.json', 'protected-recovery.json',
                     'before-old-a.json', 'live-recovery.json', 'production-authority.json',
                     'phpmyadmin-backup-resume', 'backups.json']:
            (self.root / 'state' / name).write_text('{}')
        (self.root / 'phpmyadmin').mkdir()
        (self.root / 'phpmyadmin/service.py').write_text('')

    def test_remove_legacy_deletes_other_generations_and_legacy_state(self):
        self.legacy_tree()
        current = [f'ctc-manager_recovery-20261101_{kind}' for kind in ['database', 'data', 'logs', 'credentials']]
        old = ['ctc-manager_old-a_data', 'ctc-manager_old-a_database', 'ctc-manager_data', 'ctc-manager_logs']
        docker = DockerListing(self.config, current + old + ['ctc-recovery-check_data', 'other_data'],
                               users={'ctc-manager_old-a_data': ['dead1']})
        rollout.remove_legacy(docker, self.root)
        self.assertIn(['rm', '--force', 'dead1'], docker.calls)
        removed = next(call for call in docker.calls if call[:2] == ['volume', 'rm'])
        self.assertEqual(sorted(old), sorted(removed[2:]))
        self.assertLess(docker.calls.index(['rm', '--force', 'dead1']), docker.calls.index(removed))
        self.assertEqual(['backups.json'], sorted(path.name for path in (self.root / 'state').iterdir()))
        self.assertEqual(['app.env', 'db-password', 'db-root-password', 'google.json', 'recovery-20261101'],
                         sorted(path.name for path in (self.root / 'secrets').iterdir()))
        self.assertFalse((self.root / 'phpmyadmin').exists())

    def test_docker_failure_keeps_the_legacy_files(self):
        self.legacy_tree()
        docker = DockerListing(self.config, ['ctc-manager_old-a_data'], failing=True)
        with self.assertRaises(ValueError):
            rollout.remove_legacy(docker, self.root)
        self.assertTrue((self.root / 'state/update.json').exists(), 'files stay so the next deploy retries')
        self.assertTrue((self.root / 'secrets/old-a').exists())


class DeployHostFilesTest(RolloutCase):
    systemctl, deploy, current = DeployTest.systemctl, DeployTest.deploy, DeployTest.current
    capture = DeployTest.capture

    def test_failed_reload_restores_the_previous_host_files(self):
        units = self.base / 'home/.config/systemd/user'
        units.mkdir(parents=True)
        (units / 'ctc-manager-backup.service').write_text('old unit')
        self.release('1.52.0', app='b')
        def systemctl(command, **kwargs):
            if 'daemon-reload' in command and not hasattr(self, 'reloaded'):
                self.reloaded = True
                raise subprocess.CalledProcessError(1, command)
            return original(command, **kwargs)
        original = self.systemctl
        with patch.object(self, 'systemctl', systemctl), self.assertRaises(rollout.DeployFailed):
            self.deploy('1.52.0')
        self.assertEqual('old unit', (units / 'ctc-manager-backup.service').read_text())
        self.assertEqual('1.51.0', self.current())

    def test_legacy_cleanup_failure_only_warns(self):
        self.release('1.52.0', app='b')
        with patch.object(rollout, 'remove_legacy', side_effect=ValueError('volume in use')):
            result = self.deploy('1.52.0')
        self.assertEqual('1.52.0', result['deployed'])
        self.assertIn('volume in use', result['warning'])

    def test_partial_host_file_install_is_rolled_back(self):
        units = self.base / 'home/.config/systemd/user'
        units.mkdir(parents=True)
        (units / 'ctc-manager-backup.service').write_text('old unit')
        self.release('1.52.0', app='b')
        original, written = rollout.write_private, []
        def write_private(target, content, mode):
            written.append(target)
            if len(written) == 2:
                raise OSError('disk full')
            original(target, content, mode)
        with patch.object(rollout, 'write_private', write_private), self.assertRaises(rollout.DeployFailed):
            self.deploy('1.52.0')
        self.assertEqual('old unit', (units / 'ctc-manager-backup.service').read_text())
        self.assertEqual('1.51.0', self.current())


class ObtainTest(RolloutCase):
    def test_obtain_verifies_and_extracts_with_runtime_modes(self):
        output = self.base / 'dist-obtain'
        subprocess.run(['python3', str(ROOT / 'scripts/ci/deployment-bundle.py'), 'create', '--version', '1.52.0',
                        '--app-digest', 'sha256:' + 'b' * 64, '--database-digest', DATABASE.split('@')[1],
                        '--output', str(output)], check=True, capture_output=True)
        name = 'ctc-manager-deployment-1.52.0.tar.gz'
        archive = (output / name).read_bytes()
        checksum = hashlib.sha256(archive).hexdigest() + '  ' + name + '\n'
        files = {name: archive, name + '.sha256': checksum.encode()}
        release = rollout.obtain(self.root, '1.52.0', lambda url, limit=0: files[url.rsplit('/', 1)[1]])
        self.assertEqual(self.root / 'releases/1.52.0', release)
        mode = lambda relative: (release / relative).stat().st_mode & 0o777
        self.assertEqual(0o700, mode('ctc-manager'))
        self.assertEqual(0o700, mode('phpmyadmin/start.sh'))
        self.assertEqual(0o600, mode('lib/rollout.py'))
        self.assertEqual(0o644, mode('phpmyadmin/config.user.inc.php'), 'the phpMyAdmin container reads it through a bind mount')

    def test_obtain_rejects_a_wrong_checksum(self):
        files = {'ctc-manager-deployment-1.52.0.tar.gz': b'x',
                 'ctc-manager-deployment-1.52.0.tar.gz.sha256': ('0' * 64 + '  ctc-manager-deployment-1.52.0.tar.gz\n').encode()}
        with self.assertRaisesRegex(ValueError, 'checksum'):
            rollout.obtain(self.root, '1.52.0', lambda url, limit=0: files[url.rsplit('/', 1)[1]])
        self.assertFalse((self.root / 'releases/1.52.0').exists())
