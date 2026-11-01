import copy
import hashlib
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'deploy/lib'))
import update
from config import atomic_json
from recovery import installed_release
import recovery


class ReleaseSource:
    def __init__(self, directory, version='1.24.0'):
        self.version = version
        self.files = {}
        for path in directory.iterdir():
            self.files[path.name] = path.read_bytes()
        self.metadata = {'tag_name': 'v' + version, 'draft': False, 'prerelease': False,
                         'body': 'Recovery-aware deployment update.', 'assets': [
            {'name': name, 'browser_download_url': self.url(name)} for name in self.files]}
        self.calls = []

    def url(self, name):
        return 'https://github.com/jegr78/ctc-manager/releases/download/v' + self.version + '/' + name

    def describe(self, version):
        self.calls.append(('describe', version))
        return copy.deepcopy(self.metadata)

    def download(self, url, destination, limit):
        self.calls.append(('download', url))
        name = url.rsplit('/', 1)[1]
        value = self.files[name]
        if len(value) > limit:
            raise ValueError('release download exceeds limit')
        Path(destination).write_bytes(value)


class Runtime:
    def __init__(self, root, release):
        self.root = root
        self.deploy_root = release
        self.release = installed_release(release)
        self.config = {'format': 1, 'project': 'ctc-manager',
                       'installationId': '8975b8e1-c388-4893-8832-039eb8d439cd',
                       'dockerHost': 'unix:///run/user/1002/docker.sock',
                       'appImage': self.release['appImage'], 'databaseImage': self.release['databaseImage'],
                       'storageGeneration': 'recovery-fixture', 'outboundPaused': False,
                       'appEnvFile': str(root / 'secrets/app.env'),
                       'databasePasswordFile': str(root / 'secrets/db-password'),
                       'databaseRootPasswordFile': str(root / 'secrets/db-root-password'),
                       'googleCredentialsFile': str(root / 'secrets/google.json')}
        atomic_json(root / 'config/installation.json', self.config)
        self.running = True
        self.trace = []
        self.fail_pull = False
        self.fail_health = False
        self.different_uid = False
        self.candidate_major = '13'

    def preflight(self):
        self.trace.append('preflight')

    def check_destination(self, destination):
        self.trace.append('destination')

    def docker(self, arguments, **options):
        if arguments[0] == 'pull':
            self.trace.append('pull')
            if self.fail_pull:
                raise ValueError('image pull failed')
            return b''
        if arguments[:2] == ['image', 'inspect']:
            return json.dumps([{'Id': arguments[2], 'Config': {'Labels': {
                'org.ctc.recovery.version': '1',
                'org.opencontainers.image.version': self.candidate_major + '.0.2'}}}]).encode()
        if arguments[0] == 'run':
            selected = arguments[-2]
            return b'999\n' if self.different_uid and selected != self.release['appImage'] else b'998\n'
        raise AssertionError(arguments)

    def database_metadata(self):
        return {'databaseVersion': '13.0.2-MariaDB', 'schemaHistory': [], 'records': {'teams': 1}}

    def is_app_running(self):
        return self.running

    def stop_app(self):
        self.trace.append('stop')
        self.running = False

    def start_app(self):
        self.trace.append('resume-source')
        self.running = True

    def compose(self, arguments):
        if arguments[0] == 'up':
            self.trace.append('activate-paused' if self.config['outboundPaused'] else 'activate-enabled')
            self.running = True
            if self.fail_health:
                raise ValueError('candidate health failed after migration')
        elif arguments == ['stop', 'app']:
            self.stop_app()
        else:
            raise AssertionError(arguments)
        return b''


class UpdateTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name).resolve()
        self.root = self.base / 'instance'
        self.root.mkdir(mode=0o700)
        for name in ['config', 'state', 'releases', 'secrets']:
            (self.root / name).mkdir(mode=0o700)
        self.source_directory = self.root / 'releases/1.23.0'
        self.create_bundle('1.23.0', 'a', self.base / 'old')
        self.extract(self.base / 'old/ctc-manager-deployment-1.23.0.tar.gz', self.source_directory)
        (self.root / 'current').symlink_to(self.source_directory)
        self.runtime = Runtime(self.root, self.source_directory)
        self.original = copy.deepcopy(self.runtime.config)
        self.output = self.base / 'release'
        self.create_bundle('1.24.0', 'b', self.output)
        self.provider = ReleaseSource(self.output)
        self.destination = self.base / 'units'
        self.destination.mkdir(mode=0o700)
        self.unit = self.destination / 'unit'
        self.unit.mkdir(mode=0o700)
        (self.unit / 'manifest.json').write_text('{"id":"fixture-unit"}')
        (self.unit / 'manifest.json').chmod(0o600)

    def create_bundle(self, version, digit, output):
        result = subprocess.run(['python3', str(ROOT / 'scripts/ci/deployment-bundle.py'), 'create',
            '--version', version, '--app-digest', 'sha256:' + digit * 64,
            '--database-digest', (ROOT / 'deploy/database-image').read_text().strip().split('@')[1],
            '--output', str(output)], capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)

    def extract(self, archive, destination):
        destination.mkdir(mode=0o700)
        with tarfile.open(archive) as source:
            for member in source:
                path = destination / member.name.split('/', 1)[1]
                path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
                path.write_bytes(source.extractfile(member).read())
                path.chmod(0o600)

    def stage(self):
        return update.stage_release(self.runtime, '1.24.0', provider=self.provider)

    def rewrite_asset(self, change):
        name = 'ctc-manager-deployment-1.24.0.tar.gz'
        with tarfile.open(fileobj=io.BytesIO(self.provider.files[name])) as archive:
            content = {member.name.split('/', 1)[1]: archive.extractfile(member).read() for member in archive}
        manifest = json.loads(content['manifest.json'])
        change(content, manifest)
        manifest['files'] = {key: hashlib.sha256(value).hexdigest() for key, value in content.items() if key != 'manifest.json'}
        content['manifest.json'] = json.dumps(manifest).encode()
        target = io.BytesIO()
        with tarfile.open(fileobj=target, mode='w:gz') as archive:
            for key, value in content.items():
                member = tarfile.TarInfo('ctc-manager-deployment-1.24.0/' + key)
                member.size = len(value)
                archive.addfile(member, io.BytesIO(value))
        self.provider.files[name] = target.getvalue()
        self.provider.files[name + '.sha256'] = (hashlib.sha256(target.getvalue()).hexdigest() + '  ' + name + '\n').encode()

    def capture(self, runtime, destination, hold_stopped=False):
        self.assertFalse(runtime.running, 'no accepted write may occur after the final freeze')
        self.assertTrue(hold_stopped, 'updates must never restart between capture and activation')
        runtime.trace.append('capture-held')
        return self.unit

    def prove(self, runtime, unit):
        self.assertFalse(runtime.running, 'source must stay stopped during the entire restore proof')
        self.assertEqual(self.unit, unit)
        runtime.trace.append('prove-held')
        return {'status': 'passed', 'unitId': 'fixture-unit',
                'manifestSha256': hashlib.sha256((unit / 'manifest.json').read_bytes()).hexdigest(),
                'outboundEnabled': False, 'credentialsVerified': True, 'hostPorts': []}

    def execute(self, candidate=None, **options):
        return update.execute_update(self.runtime, candidate or self.stage(), self.destination,
            capture_unit=options.get('capture_unit', self.capture),
            prove_unit=options.get('prove_unit', self.prove))

    def receipt(self):
        return json.loads((self.root / 'state/update.json').read_text())

    def assert_source_unchanged(self):
        self.assertEqual(self.original, json.loads((self.root / 'config/installation.json').read_text()))
        self.assertEqual(self.source_directory, (self.root / 'current').resolve())
        self.assertNotIn('stop', self.runtime.trace)

    def test_exact_candidate_stages_verified_notes_and_pulls_before_downtime(self):
        candidate = self.stage()
        self.assertEqual('1.24.0', installed_release(candidate)['version'])
        self.assertTrue((candidate / 'guides/updates.md').is_file())
        self.assertIn('pull', self.runtime.trace)
        self.assert_source_unchanged()

    def test_recovery_selects_installed_source_material_independently_of_current_tools(self):
        newer = self.stage()
        (self.root / 'current').unlink()
        (self.root / 'current').symlink_to(newer)
        selected = recovery.select_recovery_release(self.root, installed_release(self.source_directory))
        self.assertEqual(self.source_directory, selected)

    def test_changed_installed_recovery_source_rejects_before_recovery(self):
        expected = installed_release(self.source_directory)
        (self.source_directory / 'compose.yaml').write_text('changed source')
        with self.assertRaisesRegex(ValueError, 'checksum'):
            recovery.select_recovery_release(self.root, expected)

    def test_exact_candidate_retry_rechecks_remote_identity_without_overwriting(self):
        candidate = self.stage()
        self.assertEqual(candidate, self.stage())
        self.rewrite_asset(lambda content, manifest: content.update({'guides/updates.md': b'changed'}))
        with self.assertRaisesRegex(ValueError, 'changed|differ'):
            self.stage()
        self.assert_source_unchanged()

    def test_mutated_installed_candidate_is_rejected(self):
        candidate = self.stage()
        (candidate / 'compose.yaml').write_text('changed')
        with self.assertRaisesRegex(ValueError, 'checksum'):
            self.execute(candidate)
        self.assert_source_unchanged()

    def test_missing_asset_rejects_before_pull_or_stop(self):
        self.provider.metadata['assets'] = self.provider.metadata['assets'][:1]
        with self.assertRaisesRegex(ValueError, 'asset'):
            self.stage()
        self.assertNotIn('pull', self.runtime.trace)
        self.assert_source_unchanged()

    def test_foreign_asset_url_rejects_before_download(self):
        self.provider.metadata['assets'][0]['browser_download_url'] = 'https://example.invalid/arbitrary'
        with self.assertRaisesRegex(ValueError, 'asset|repository'):
            self.stage()
        self.assertFalse(any(call[0] == 'download' for call in self.provider.calls))

    def test_wrong_release_identity_rejects(self):
        for key, value in [('tag_name', 'v9.0.0'), ('draft', True), ('prerelease', True)]:
            with self.subTest(key=key):
                self.provider.metadata[key] = value
                with self.assertRaisesRegex(ValueError, 'release'):
                    self.stage()
                self.provider.metadata[key] = {'tag_name': 'v1.24.0', 'draft': False, 'prerelease': False}[key]
        self.assert_source_unchanged()

    def test_corrupted_checksum_rejects(self):
        name = 'ctc-manager-deployment-1.24.0.tar.gz.sha256'
        self.provider.files[name] = b'0' * 64 + b'  ctc-manager-deployment-1.24.0.tar.gz\n'
        with self.assertRaisesRegex(ValueError, 'checksum'):
            self.stage()
        self.assert_source_unchanged()

    def test_noncanonical_archive_member_is_rejected_before_external_write(self):
        name = 'ctc-manager-deployment-1.24.0.tar.gz'
        result = io.BytesIO()
        with tarfile.open(fileobj=io.BytesIO(self.provider.files[name])) as source, tarfile.open(fileobj=result, mode='w:gz') as target:
            for member in source:
                value = source.extractfile(member).read()
                if member.name.endswith('/compose.yaml'):
                    member.name = member.name.replace('/compose.yaml', '//compose.yaml')
                target.addfile(member, io.BytesIO(value))
        self.provider.files[name] = result.getvalue()
        self.provider.files[name + '.sha256'] = (hashlib.sha256(result.getvalue()).hexdigest() + '  ' + name + '\n').encode()
        original = Path.open
        def prevent_external_write(path, *arguments, **options):
            self.assertNotEqual(Path('/compose.yaml'), path, 'archive extraction must never attempt an external write')
            return original(path, *arguments, **options)
        with patch.object(Path, 'open', prevent_external_write), self.assertRaisesRegex(ValueError, 'canonical|unsafe'):
            self.stage()
        self.assert_source_unchanged()

    def test_downgrade_and_mutable_versions_are_rejected(self):
        for selected in ['latest', 'master', '1.24.0-SNAPSHOT', '1.23.0', '1.22.0', '../1.24.0']:
            with self.subTest(selected=selected), self.assertRaises(ValueError):
                update.stage_release(self.runtime, selected, provider=self.provider)
        self.assertEqual([], self.provider.calls)
        self.assert_source_unchanged()

    def test_unsupported_upgrade_contract_rejects(self):
        self.rewrite_asset(lambda content, manifest: content.update({'upgrade.json': json.dumps({
            'format': 1, 'minimumSourceVersion': '9.0.0', 'recoveryCapability': 1,
            'notes': 'guides/updates.md'}).encode()}))
        with self.assertRaisesRegex(ValueError, 'source|upgrade'):
            self.stage()
        self.assert_source_unchanged()

    def test_missing_upgrade_notes_rejects(self):
        self.rewrite_asset(lambda content, manifest: content.update({'guides/updates.md': b''}))
        with self.assertRaisesRegex(ValueError, 'notes'):
            self.stage()
        self.assert_source_unchanged()

    def test_database_major_change_rejects_without_stopping(self):
        self.runtime.candidate_major = '14'
        with self.assertRaisesRegex(ValueError, 'database major'):
            self.stage()
        self.assert_source_unchanged()

    def test_application_uid_change_rejects_without_stopping(self):
        self.runtime.different_uid = True
        with self.assertRaisesRegex(ValueError, 'owner|UID'):
            self.stage()
        self.assert_source_unchanged()

    def test_image_pull_failure_preserves_prior_instance(self):
        self.runtime.fail_pull = True
        with self.assertRaisesRegex(ValueError, 'pull'):
            self.stage()
        self.assert_source_unchanged()

    def test_low_disk_space_rejects_before_pull(self):
        with patch.object(update.shutil, 'disk_usage', return_value=shutil._ntuple_diskusage(100, 99, 1)):
            with self.assertRaisesRegex(ValueError, 'space'):
                self.stage()
        self.assertNotIn('pull', self.runtime.trace)
        self.assert_source_unchanged()

    def test_symlink_release_parent_rejects(self):
        other = self.base / 'elsewhere'
        (self.root / 'releases').rename(other)
        (self.root / 'releases').symlink_to(other)
        with self.assertRaisesRegex(ValueError, 'symlink'):
            self.stage()
        self.assertNotIn('pull', self.runtime.trace)

    def test_success_keeps_continuous_freeze_and_preserves_storage(self):
        result = self.execute()
        self.assertEqual('completed', result['stage'])
        trace = self.runtime.trace
        self.assertLess(trace.index('stop'), trace.index('capture-held'))
        self.assertLess(trace.index('capture-held'), trace.index('prove-held'))
        self.assertLess(trace.index('prove-held'), trace.index('activate-paused'))
        self.assertLess(trace.index('activate-paused'), trace.index('activate-enabled'))
        self.assertNotIn('resume-source', trace)
        self.assertTrue(self.runtime.running)
        self.assertEqual('1.24.0', self.receipt()['targetVersion'])
        self.assertEqual(str(self.unit), self.receipt()['recoveryUnit'])
        protected = json.loads((self.root / 'state/protected-recovery.json').read_text())
        self.assertEqual(str(self.unit), protected['unit'])
        for key in ['storageGeneration', 'databasePasswordFile', 'googleCredentialsFile']:
            self.assertEqual(self.original[key], self.runtime.config[key])

    def test_prior_paused_instance_stays_paused(self):
        self.runtime.config['outboundPaused'] = True
        atomic_json(self.root / 'config/installation.json', self.runtime.config)
        self.execute()
        self.assertTrue(self.runtime.config['outboundPaused'])
        self.assertNotIn('activate-enabled', self.runtime.trace)

    def test_prior_stopped_instance_stays_stopped_after_health_proof(self):
        self.runtime.running = False
        self.execute()
        self.assertFalse(self.runtime.running)
        self.assertNotIn('activate-enabled', self.runtime.trace)

    def test_capture_failure_resumes_unchanged_source(self):
        def fail(runtime, destination, hold_stopped):
            raise ValueError('capture failed')
        with self.assertRaisesRegex(ValueError, 'capture failed'):
            self.execute(capture_unit=fail)
        self.assertTrue(self.runtime.running)
        self.assertEqual(self.original, self.runtime.config)
        self.assertEqual(self.source_directory, (self.root / 'current').resolve())
        self.assertEqual('failed-before-activation', self.receipt()['stage'])
        self.assertNotIn('activate-paused', self.runtime.trace)

    def test_probe_failure_resumes_source_and_protects_unit(self):
        def fail(runtime, unit):
            raise ValueError('restore proof failed')
        with self.assertRaisesRegex(ValueError, 'restore proof failed'):
            self.execute(prove_unit=fail)
        self.assertTrue(self.runtime.running)
        self.assertEqual(self.original, self.runtime.config)
        self.assertTrue((self.root / 'state/protected-recovery.json').is_file())
        self.assertNotIn('activate-paused', self.runtime.trace)

    def test_prior_stopped_source_is_not_resumed_after_probe_failure(self):
        self.runtime.running = False
        def fail(runtime, unit):
            raise ValueError('proof failed')
        with self.assertRaisesRegex(ValueError, 'proof failed'):
            self.execute(prove_unit=fail)
        self.assertFalse(self.runtime.running)
        self.assertNotIn('resume-source', self.runtime.trace)

    def test_incomplete_or_unrelated_restore_receipt_prevents_activation(self):
        for key, value in [('status', 'failed'), ('unitId', 'other-unit'), ('manifestSha256', '0' * 64),
                           ('outboundEnabled', True), ('credentialsVerified', False), ('hostPorts', [9092])]:
            def unrelated(runtime, unit):
                return {**self.prove(runtime, unit), key: value}
            with self.subTest(field=key), self.assertRaisesRegex(ValueError, 'proof|receipt'):
                self.execute(prove_unit=unrelated)
            self.assertNotIn('activate-paused', self.runtime.trace)
            self.assertTrue(self.runtime.running)

    def test_non_newer_installed_candidate_rejects_before_freeze(self):
        with self.assertRaisesRegex(ValueError, 'newer released version'):
            self.execute(self.source_directory)
        self.assert_source_unchanged()

    def test_changed_unit_after_proof_rejects_before_activation(self):
        def changed(runtime, unit):
            proof = self.prove(runtime, unit)
            with (unit / 'manifest.json').open('a') as output:
                output.write('\n')
            return proof
        with self.assertRaisesRegex(ValueError, 'proof|receipt'):
            self.execute(prove_unit=changed)
        self.assertNotIn('activate-paused', self.runtime.trace)
        self.assertTrue(self.runtime.running)

    def test_restarted_source_during_proof_cannot_activate_candidate(self):
        def restarted(runtime, unit):
            proof = self.prove(runtime, unit)
            runtime.running = True
            return proof
        with self.assertRaisesRegex(ValueError, 'restarted|freeze'):
            self.execute(prove_unit=restarted)
        self.assertNotIn('activate-paused', self.runtime.trace)

    def test_candidate_changed_during_proof_is_rejected_before_activation(self):
        candidate = self.stage()
        def changed(runtime, unit):
            proof = self.prove(runtime, unit)
            (candidate / 'compose.yaml').write_text('changed during proof')
            return proof
        with self.assertRaisesRegex(ValueError, 'checksum|candidate'):
            self.execute(candidate, prove_unit=changed)
        self.assertTrue(self.runtime.running)
        self.assertNotIn('activate-paused', self.runtime.trace)

    def test_failed_source_resume_records_required_review(self):
        def bad_proof(runtime, unit):
            raise ValueError('restore proof failed')
        def bad_resume():
            raise ValueError('source resume failed')
        self.runtime.start_app = bad_resume
        with self.assertRaises(ValueError):
            self.execute(prove_unit=bad_proof)
        self.assertEqual('failed-review-required', self.receipt()['stage'])
        self.assertFalse(self.runtime.running)

    def test_unhealthy_candidate_after_migration_never_starts_old_code(self):
        self.runtime.fail_health = True
        with self.assertRaisesRegex(ValueError, 'health failed'):
            self.execute()
        self.assertFalse(self.runtime.running)
        self.assertTrue(self.runtime.config['outboundPaused'])
        self.assertNotEqual(self.original['appImage'], self.runtime.config['appImage'])
        self.assertEqual('failed-recovery-required', self.receipt()['stage'])
        self.assertEqual(str(self.unit), self.receipt()['recoveryUnit'])
        self.assertNotIn('resume-source', self.runtime.trace)
        self.assertNotIn('activate-enabled', self.runtime.trace)

    def test_interrupted_activation_leaves_evidence_and_rejects_blind_retry(self):
        candidate = self.stage()
        def interrupted(arguments):
            raise KeyboardInterrupt()
        self.runtime.compose = interrupted
        with self.assertRaises(KeyboardInterrupt):
            self.execute(candidate)
        self.assertEqual('activating', self.receipt()['stage'])
        with self.assertRaisesRegex(ValueError, 'reconcile|unresolved'):
            self.execute(candidate)

    def test_source_selection_drift_prevents_activation_and_unsafe_resume(self):
        def changed(runtime, unit):
            runtime.trace.append('prove-held')
            atomic_json(self.root / 'config/installation.json', {**runtime.config, 'storageGeneration': 'other'})
            return {'status': 'passed'}
        with self.assertRaisesRegex(ValueError, 'changed|source'):
            self.execute(prove_unit=changed)
        self.assertNotIn('activate-paused', self.runtime.trace)
        self.assertNotIn('resume-source', self.runtime.trace)
        self.assertEqual('failed-review-required', self.receipt()['stage'])


class NativeUpdateSafetyTest(unittest.TestCase):
    def test_optimized_python_still_refuses_real_domain_data_before_native_changes(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for name, value in [('google.json', '{}'), ('db-password', 'fixture'),
                                ('app.env', 'SPRING_SECURITY_USER_NAME=fixture\nSPRING_SECURITY_USER_PASSWORD=fixture\n')]:
                (directory / name).write_text(value)
            program = '''
import importlib.util
import os
from pathlib import Path
import sys
from types import SimpleNamespace
from unittest.mock import patch
spec=importlib.util.spec_from_file_location('native_update',sys.argv[1])
module=importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
directory=Path(sys.argv[2])
runtime=SimpleNamespace(root=Path('/srv/ctc-manager'),preflight=lambda:None,
 config={'outboundPaused':True,'googleCredentialsFile':str(directory/'google.json'),
         'appEnvFile':str(directory/'app.env'),'databasePasswordFile':str(directory/'db-password')},
 database_metadata=lambda:{'records':{'seasons':4,'teams':0,'drivers':0,'races':0,'ctc_runtime_qualification':1}})
original=Path.read_text
def read(path,*args,**kwargs):
 if path.name=='owner': return str(os.getppid())
 return original(path,*args,**kwargs)
with patch.object(Path,'read_text',read): module.assert_synthetic(runtime)
print('unsafe acceptance')
'''
            result = subprocess.run([sys.executable, '-O', '-c', program,
                str(ROOT / 'deploy/tests/native_update.py'), str(directory)], capture_output=True, text=True,
                env={**os.environ, 'PYTHONOPTIMIZE': '1'})
            self.assertNotEqual(0, result.returncode, 'optimization must never remove the real-data refusal')
            self.assertIn('never qualify against real domain data', result.stderr)
            self.assertNotIn('unsafe acceptance', result.stdout)


if __name__ == '__main__':
    unittest.main()
