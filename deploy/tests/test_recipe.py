import json
import importlib.util
from unittest.mock import patch
import os
from pathlib import Path
import subprocess
import socket
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
LAUNCHER = ROOT / 'deploy/ctc-manager'
DIGEST = 'sha256:' + 'a' * 64


class RecipeTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name).resolve()
        self.install = self.base / 'instance'
        self.install.mkdir(mode=0o700)
        (self.install / 'config').mkdir(mode=0o700)
        (self.install / 'secrets').mkdir(mode=0o700)
        self.runtime = self.base / 'runtime'
        self.runtime.mkdir(mode=0o700)
        self.socket_path = self.runtime / 'docker.sock'
        self.socket = socket.socket(socket.AF_UNIX)
        self.socket.bind(str(self.socket_path))
        self.socket_path.chmod(0o600)
        self.addCleanup(self.socket.close)
        self.config = {
            'format': 1, 'project': 'ctc-manager',
            'dockerHost': 'unix://' + str(self.socket_path),
            'appImage': 'ghcr.io/jegr78/ctc-manager@' + DIGEST,
            'databaseImage': 'mariadb@' + DIGEST,
            'appEnvFile': str(self.install / 'secrets/app.env'),
            'databasePasswordFile': str(self.install / 'secrets/db-password'),
            'databaseRootPasswordFile': str(self.install / 'secrets/db-root-password'),
            'googleCredentialsFile': str(self.install / 'secrets/google.json'),
        }
        for name, content in [('app.env', 'SPRING_SECURITY_USER_NAME=owner\nSPRING_SECURITY_USER_PASSWORD=private-fixture\nDATABASE_USERNAME=ctc\nDATABASE_PASSWORD=private-fixture\n'), ('db-password', 'private-fixture'), ('db-root-password', 'private-root'), ('google.json', '{}')]:
            target = self.install / 'secrets' / name
            target.write_text(content)
            target.chmod(0o600)
        self.bin = self.base / 'bin'
        self.bin.mkdir()
        self.calls = self.base / 'calls'
        docker = self.bin / 'docker'
        docker.write_text('''#!/usr/bin/env python3
import json, os, sys
if os.environ.get('EXPECTED_CWD') and os.getcwd() != os.environ['EXPECTED_CWD']:
 sys.exit('inaccessible inherited working directory')
with open(os.environ['CALLS'], 'a') as f: f.write(json.dumps(sys.argv[1:])+'\\n')
if 'compose' in sys.argv:
 with open(os.environ['CALLS']+'.env', 'w') as f: json.dump({k:v for k,v in os.environ.items() if k.startswith('CTC_')}, f)
if 'info' in sys.argv:
 print(json.dumps({'SecurityOptions': ['name='+os.environ.get('DAEMON','rootless')], 'MemoryLimit': os.environ.get('MEMORY','true') == 'true'}))
elif 'ps' in sys.argv: print('[]')
''')
        docker.chmod(0o700)
        self.env = {**os.environ, 'PATH': str(self.bin) + os.pathsep + os.environ['PATH'], 'CALLS': str(self.calls), 'XDG_RUNTIME_DIR': str(self.runtime)}
        self.write_config()

    def write_config(self):
        path = self.install / 'config/installation.json'
        path.write_text(json.dumps(self.config))
        path.chmod(0o600)

    def run_cli(self, command='doctor'):
        return subprocess.run(['bash', str(LAUNCHER), '--directory', str(self.install)] + ([command] if isinstance(command, str) else command), env=self.env, capture_output=True, text=True)

    def test_invalid_update_version_uses_update_validation_without_mutating_compose(self):
        for operation in ['update-check', 'update']:
            result = self.run_cli([operation, '--release', 'latest'])
            self.assertNotEqual(0, result.returncode)
            self.assertNotIn('unknown operation', result.stderr)
            self.assertIn('released version', result.stderr)
            self.assertNotIn('compose', self.calls.read_text())
            self.assertFalse((self.install / '.ctc-manager.lock').exists())

    def test_concurrent_update_refuses_before_candidate_work(self):
        lock = self.install / '.ctc-manager.lock'
        lock.mkdir(mode=0o700)
        (lock / 'owner').write_text('existing-owner')
        result = self.run_cli(['update', '--release', '1.24.0'])
        self.assertNotEqual(0, result.returncode)
        self.assertIn('lock exists', result.stderr)
        self.assertEqual('existing-owner', (lock / 'owner').read_text())
        self.assertNotIn('compose', self.calls.read_text())

    def test_terminated_update_child_retains_lock_and_interrupted_journal(self):
        import sys
        wrapper = self.bin / 'python3'
        wrapper.write_text('#!' + sys.executable + '\nimport os,sys\n'
                           'if sys.argv[1].endswith("/lib/update.py"): sys.exit(143)\n'
                           'os.execv(' + repr(sys.executable) + ', [' + repr(sys.executable) + '] + sys.argv[1:])\n')
        wrapper.chmod(0o700)
        result = self.run_cli(['update', '--release', '1.24.0'])
        self.assertEqual(143, result.returncode, result.stderr)
        self.assertTrue((self.install / '.ctc-manager.lock/owner').exists())
        journal = json.loads((self.install / 'state/operation.json').read_text())
        self.assertEqual({'operation': 'update', 'stage': 'interrupted'},
                         {key: journal[key] for key in ['operation', 'stage']})

    def test_recovery_generation_selects_separate_storage_and_pauses_outbound(self):
        self.config.update(storageGeneration='recovery-20261101', outboundPaused=True)
        self.write_config()
        result = self.run_cli('up')
        self.assertEqual(0, result.returncode, result.stderr)
        actual = json.loads(Path(str(self.calls) + '.env').read_text())
        self.assertEqual('ctc-manager_recovery-20261101_database', actual.get('CTC_DATABASE_VOLUME'))
        self.assertEqual('ctc-manager_recovery-20261101_data', actual.get('CTC_DATA_VOLUME'))
        self.assertEqual('false', actual.get('CTC_OUTBOUND_ENABLED'))

    def test_invalid_storage_generation_refuses_before_docker(self):
        self.config['storageGeneration'] = '../courtside'
        self.write_config()
        result = self.run_cli('up')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('storageGeneration', result.stderr)
        self.assertFalse(self.calls.exists())

    def test_outbound_pause_requires_boolean(self):
        self.config['outboundPaused'] = 'false'
        self.write_config()
        result = self.run_cli('up')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('outboundPaused', result.stderr)
        self.assertFalse(self.calls.exists())

    def test_invalid_recovery_unit_fails_without_compose_mutation(self):
        result = self.run_cli(['restore-check', '--recovery', str(self.base / 'missing-unit')])
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn('unknown operation', result.stderr)
        self.assertNotIn('usage: ctc-manager', result.stderr)
        self.assertFalse((self.install / '.ctc-manager.lock').exists())
        self.assertNotIn('compose', self.calls.read_text())
        journal = json.loads((self.install / 'state/operation.json').read_text())
        self.assertEqual('failed', journal['stage'])

    def test_terminated_recovery_leaves_lock_and_interrupted_journal(self):
        import sys
        wrapper = self.bin / 'python3'
        wrapper.write_text('#!' + sys.executable + '\nimport os,signal,sys\n'
                           'if sys.argv[1].endswith("/lib/recovery.py"):\n'
                           ' os.kill(os.getppid(),signal.SIGTERM)\n'
                           ' sys.exit(0)\n'
                           'os.execv(' + repr(sys.executable) + ', [' + repr(sys.executable) + '] + sys.argv[1:])\n')
        wrapper.chmod(0o700)
        result = self.run_cli(['backup', '--destination', str(self.base)])
        self.assertEqual(143, result.returncode, result.stderr)
        self.assertTrue((self.install / '.ctc-manager.lock/owner').exists())
        journal = json.loads((self.install / 'state/operation.json').read_text())
        self.assertEqual('interrupted', journal['stage'])

    def test_signalled_recovery_child_leaves_lock_and_interrupted_journal(self):
        import sys
        wrapper = self.bin / 'python3'
        wrapper.write_text('#!' + sys.executable + '\nimport os,sys\n'
                           'if sys.argv[1].endswith("/lib/recovery.py"): sys.exit(143)\n'
                           'os.execv(' + repr(sys.executable) + ', [' + repr(sys.executable) + '] + sys.argv[1:])\n')
        wrapper.chmod(0o700)
        result = self.run_cli(['backup', '--destination', str(self.base)])
        self.assertEqual(143, result.returncode, result.stderr)
        self.assertTrue((self.install / '.ctc-manager.lock/owner').exists())
        journal = json.loads((self.install / 'state/operation.json').read_text())
        self.assertEqual('interrupted', journal['stage'])

    def test_valid_rootless_configuration_reports_missing_backup_protection(self):
        result = self.run_cli()
        self.assertNotEqual(0, result.returncode)
        self.assertIn('local-missing', result.stdout)
        self.assertIn('rootless', result.stdout)
        self.assertNotIn('private-fixture', result.stdout + result.stderr)

    def test_rootful_daemon_refuses_before_compose(self):
        self.env['DAEMON'] = 'seccomp'
        result = self.run_cli('up')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('rootless', result.stderr)
        self.assertNotIn('compose', self.calls.read_text())

    def test_missing_memory_enforcement_refuses(self):
        self.env['MEMORY'] = 'false'
        result = self.run_cli('up')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('memory', result.stderr)
        self.assertNotIn('compose', self.calls.read_text())

    def test_wrong_user_socket_refuses_before_docker(self):
        self.config['dockerHost'] = 'unix:///var/run/docker.sock'
        self.write_config()
        result = self.run_cli('up')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('socket', result.stderr)
        self.assertFalse(self.calls.exists())

    def test_unpinned_image_refuses(self):
        self.config['appImage'] = 'ctc-manager:latest'
        self.write_config()
        result = self.run_cli()
        self.assertNotEqual(0, result.returncode)
        self.assertIn('digest', result.stderr)
        self.assertFalse(self.calls.exists())

    def test_missing_secret_refuses_without_printing_contents(self):
        Path(self.config['appEnvFile']).unlink()
        result = self.run_cli()
        self.assertNotEqual(0, result.returncode)
        self.assertIn('appEnvFile', result.stderr)
        self.assertNotIn('private-fixture', result.stderr)

    def test_world_readable_secret_refuses(self):
        Path(self.config['appEnvFile']).chmod(0o644)
        result = self.run_cli()
        self.assertNotEqual(0, result.returncode)
        self.assertIn('private', result.stderr)

    def test_secret_outside_installation_refuses(self):
        target = self.base / 'elsewhere'
        target.write_text('never print this')
        target.chmod(0o600)
        self.config['appEnvFile'] = str(target)
        self.write_config()
        result = self.run_cli()
        self.assertNotEqual(0, result.returncode)
        self.assertIn('installation', result.stderr)

    def test_symlink_config_refuses(self):
        path = self.install / 'config/installation.json'
        other = self.install / 'other.json'
        path.rename(other)
        path.symlink_to(other)
        result = self.run_cli()
        self.assertNotEqual(0, result.returncode)
        self.assertIn('symlink', result.stderr)

    def test_live_lock_refuses_mutation(self):
        lock = self.install / '.ctc-manager.lock'
        lock.mkdir(mode=0o700)
        (lock / 'owner').write_text(str(os.getpid()))
        result = self.run_cli('up')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('lock', result.stderr)
        self.assertNotIn('compose', self.calls.read_text() if self.calls.exists() else '')

    def test_stop_targets_only_ctc_app_and_releases_lock(self):
        result = self.run_cli('stop')
        self.assertEqual(0, result.returncode, result.stderr)
        calls = [json.loads(line) for line in self.calls.read_text().splitlines()]
        mutation = next(call for call in calls if 'compose' in call)
        self.assertEqual(['stop', 'app'], mutation[-2:])
        self.assertIn('ctc-manager', mutation)
        self.assertFalse((self.install / '.ctc-manager.lock').exists())

    def test_unknown_command_is_read_only(self):
        result = self.run_cli('prune-everything')
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(self.calls.exists())

    def test_occupied_port_refuses_before_start(self):
        with socket.socket() as listener:
            listener.bind(('127.0.0.1', 9092))
            listener.listen()
            result = self.run_cli('up')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('9092', result.stderr)
        calls = [json.loads(line) for line in self.calls.read_text().splitlines()]
        self.assertFalse(any('up' in call for call in calls))
        self.assertFalse((self.install / '.ctc-manager.lock').exists())

    def test_compose_has_private_binding_and_persistent_storage(self):
        environment = {**os.environ,
            'CTC_APP_IMAGE': self.config['appImage'], 'CTC_DATABASE_IMAGE': self.config['databaseImage'],
            'CTC_APP_ENV_FILE': self.config['appEnvFile'],
            'CTC_DB_PASSWORD_FILE': self.config['databasePasswordFile'],
            'CTC_DB_ROOT_PASSWORD_FILE': self.config['databaseRootPasswordFile'],
            'CTC_GOOGLE_FILE': self.config['googleCredentialsFile']}
        result = subprocess.run(['docker', 'compose', '--env-file', '/dev/null', '-f',
            str(ROOT / 'deploy/compose.yaml'), 'config', '--format', 'json'],
            env=environment, text=True, capture_output=True)
        self.assertEqual(0, result.returncode, result.stderr)
        services = json.loads(result.stdout)['services']
        self.assertNotIn('ports', services['db'])
        self.assertEqual('127.0.0.1', services['app']['ports'][0]['host_ip'])
        self.assertEqual('prod', services['app']['environment']['SPRING_PROFILES_ACTIVE'])
        self.assertEqual('framework', services['app']['environment']['SERVER_FORWARD_HEADERS_STRATEGY'])
        self.assertIn('-Duser.timezone=Europe/Berlin', services['app']['environment']['JAVA_TOOL_OPTIONS'])
        self.assertEqual(2147483648, int(services['app']['mem_limit']))
        self.assertTrue(any(v['target'] == '/app/data' for v in services['app']['volumes']))
        self.assertEqual('unless-stopped', services['db']['restart'])
        credentials = next(v for v in services['app']['volumes'] if v['target'] == '/run/ctc')
        self.assertTrue(credentials['read_only'])
        self.assertEqual('service_completed_successfully', services['app']['depends_on']['credentials-init']['condition'])
        self.assertEqual('none', services['credentials-init']['network_mode'])

    def test_symlink_socket_refuses_before_docker(self):
        target = self.runtime / 'other.sock'
        self.socket_path.rename(target)
        self.socket_path.symlink_to(target)
        result = self.run_cli()
        self.assertNotEqual(0, result.returncode)
        self.assertIn('socket', result.stderr)
        self.assertFalse(self.calls.exists())

    def test_regular_file_socket_refuses_before_docker(self):
        self.socket_path.unlink()
        self.socket_path.write_text('not a socket')
        result = self.run_cli()
        self.assertNotEqual(0, result.returncode)
        self.assertIn('socket', result.stderr)
        self.assertFalse(self.calls.exists())

    def test_foreign_socket_owner_refuses(self):
        spec = importlib.util.spec_from_file_location('deployment_config', ROOT / 'deploy/lib/config.py')
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        original = Path.lstat
        def foreign(path, *args, **kwargs):
            metadata = original(path, *args, **kwargs)
            if path == self.socket_path:
                values = list(metadata)
                values[4] = os.getuid() + 1
                return os.stat_result(values)
            return metadata
        with patch.dict(os.environ, {'XDG_RUNTIME_DIR': str(self.runtime)}), patch.object(Path, 'lstat', foreign):
            with self.assertRaisesRegex(ValueError, 'socket.*owned'):
                module.load(self.install)

    def test_launcher_does_not_inherit_operator_working_directory(self):
        self.env['EXPECTED_CWD'] = str(self.install)
        result = self.run_cli('stop')
        self.assertEqual(0,result.returncode,result.stderr)
