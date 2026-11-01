#!/usr/bin/env python3
import json
from pathlib import Path
import re
import shutil
import uuid

from config import atomic_json, private_path
from probe import Probe


def recovery_target(config, confirmation):
    identity = config.get('installationId', '')
    try:
        if str(uuid.UUID(identity)) != identity:
            raise ValueError()
    except (ValueError, TypeError, AttributeError):
        raise ValueError('target installation needs a valid identity before recovery') from None
    prefix = 'ctc-manager:' + identity + ':'
    generation = confirmation.removeprefix(prefix)
    if (not confirmation.startswith(prefix) or not re.fullmatch(r'recovery-[a-z0-9][a-z0-9-]{0,38}', generation)
            or generation == config.get('storageGeneration')):
        raise ValueError('target confirmation must name this installation and a fresh recovery generation')
    return generation


class RecoveryStorage(Probe):
    def __init__(self, *arguments, generation):
        self.generation = generation
        super().__init__(*arguments)

    def volume(self, role):
        return 'ctc-manager_' + self.generation + '_' + role

    def volume_labels(self, role):
        return super().volume_labels(role) + ['--label', 'com.docker.compose.project=ctc-manager',
                                               '--label', 'com.docker.compose.volume=' + role]


def restore(runtime, unit, confirmation, *, before_activation=None):
    from recovery import admit_recovery_image, flush_directory, probe_environment, restore_check, validate_unit
    import os
    generation = recovery_target(runtime.config, confirmation)
    manifest = validate_unit(unit)
    release_directory = private_path(runtime.deploy_root, directory=True)
    if release_directory.parent != runtime.root / 'releases' or release_directory.name != manifest['releaseVersion']:
        raise ValueError('recovery requires the matching owned installed release directory')
    current = runtime.root / 'current'
    if current.exists() and not current.is_symlink():
        raise ValueError('current must be a deployment symlink')
    previous_release = str(current.resolve()) if current.is_symlink() else None
    secrets_parent = private_path(runtime.root / 'secrets', directory=True)
    existing = set(runtime.docker(['volume', 'ls', '--quiet']).decode().splitlines())
    targets = {'ctc-manager_' + generation + '_' + role for role in ['database', 'data', 'logs', 'credentials']}
    if targets & existing:
        raise ValueError('target recovery storage already exists; never overwrite it')
    if runtime.compose(['ps', '--status', 'running', '--quiet', 'db']).strip():
        runtime.preflight(allow_missing_app=True)
    proof = restore_check(runtime, unit)
    app = json.loads(runtime.docker(['image', 'inspect', manifest['appImage']]))[0]
    admit_recovery_image(app)
    database = json.loads(runtime.docker(['image', 'inspect', manifest['databaseImage']]))[0]
    storage = RecoveryStorage(runtime, unit, manifest, app['Id'], database['Id'], generation=generation)
    keep_volumes = False
    receipt = {'format': 1, 'unitId': manifest['id'], 'target': confirmation, 'stage': 'preparing',
               'proofRun': proof['run'], 'outboundPaused': True, 'releaseDirectory': str(release_directory),
               'previousReleaseDirectory': previous_release}
    receipt_path = runtime.root / 'state/live-recovery.json'
    atomic_json(receipt_path, receipt)
    try:
        storage.create_resources()
        storage.record('container', storage.prefix + '-db')
        storage.docker(storage.database_arguments())
        storage.wait_database()
        storage.import_database()
        storage.verify_database()
        storage.restore_files()
        storage.verify_files()
        storage.docker(['stop', '--time', '60', storage.prefix + '-db'])
        keep_volumes = True
        storage.cleanup(keep_volumes=True)
        secrets = secrets_parent / generation
        secrets.mkdir(mode=0o700)
        private_path(secrets, directory=True)
        for name in ['db-password', 'db-root-password', 'google.json']:
            shutil.copyfile(Path(unit) / name, secrets / name)
            (secrets / name).chmod(0o600)
        environment = probe_environment(Path(unit) / 'app.env', (secrets / 'db-password').read_text().rstrip('\n'))
        environment['SPRING_PROFILES_ACTIVE'] = 'prod'
        with (secrets / 'app.env').open('x') as output:
            (secrets / 'app.env').chmod(0o600)
            for key, value in environment.items():
                if '\n' in value or '\r' in value:
                    raise ValueError('unsupported restored environment value')
                output.write(key + '=' + value + '\n')
        for artifact in secrets.iterdir():
            with artifact.open('rb') as source:
                os.fsync(source.fileno())
        flush_directory(secrets)
        flush_directory(secrets.parent)
        previous = runtime.root / 'state' / ('before-' + generation + '.json')
        if previous.exists():
            raise ValueError('target predecessor receipt already exists')
        atomic_json(previous, runtime.config)
        target = {**runtime.config, 'appImage': manifest['appImage'], 'databaseImage': manifest['databaseImage'],
                  'storageGeneration': generation, 'outboundPaused': True,
                  'appEnvFile': str(secrets / 'app.env'), 'databasePasswordFile': str(secrets / 'db-password'),
                  'databaseRootPasswordFile': str(secrets / 'db-root-password'),
                  'googleCredentialsFile': str(secrets / 'google.json')}
        receipt.update(stage='activating', previousConfiguration=str(previous))
        atomic_json(receipt_path, receipt)
        if runtime.compose(['ps', '--all', '--quiet', 'app']).strip():
            runtime.stop_app()
        runtime.compose(['stop', 'db'])
        runtime.compose(['rm', '--force', 'app', 'db', 'credentials-init'])
        atomic_json(runtime.root / 'config/installation.json', target)
        temporary = runtime.root / ('.current-' + uuid.uuid4().hex)
        temporary.symlink_to(release_directory)
        os.replace(temporary, current)
        flush_directory(runtime.root)
        runtime.config = target
        if before_activation is not None:
            before_activation()
        runtime.compose(['up', '-d', '--wait', '--wait-timeout', '180'])
        receipt['stage'] = 'active-integrations-paused'
        atomic_json(receipt_path, receipt)
        return receipt
    except Exception:
        receipt['stage'] = 'failed-review-required'
        atomic_json(receipt_path, receipt)
        raise
    finally:
        if storage.resources and not keep_volumes:
            storage.cleanup()
