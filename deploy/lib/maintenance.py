#!/usr/bin/env python3
import datetime as dt
import json
import math
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import time
import uuid

from config import atomic_json, private_path
from recovery import NativeRuntime, capture, read_json, restore_check, sha256, validate_unit, select_recovery_release

UNIT_ROOT = Path('/mnt/backup/ctc-manager/units')
REMOTE_FIELDS = {'format', 'repositoryFile', 'passwordFile', 'environmentFile',
                 'repositoryId', 'alertHook', 'keyCustodyConfirmed', 'offHostConfirmed'}
ACCESS_ENV = {'AWS_ACCESS_KEY_ID', 'AWS_SECRET_ACCESS_KEY', 'AWS_SESSION_TOKEN', 'AWS_DEFAULT_REGION',
              'AWS_REGION', 'B2_ACCOUNT_ID', 'B2_ACCOUNT_KEY', 'AZURE_ACCOUNT_NAME',
              'AZURE_ACCOUNT_KEY', 'AZURE_ACCOUNT_SAS', 'GOOGLE_APPLICATION_CREDENTIALS'}


def load_backup(root):
    config = read_json(private_path(root / 'config/backup.json'))
    if (type(config) is not dict or set(config) != REMOTE_FIELDS or config['format'] != 1
            or config['keyCustodyConfirmed'] is not True or config['offHostConfirmed'] is not True
            or not isinstance(config['repositoryId'], str) or not re.fullmatch('[a-f0-9]{64}', config['repositoryId'])):
        raise ValueError('invalid backup configuration or unconfirmed external key custody')
    for field in ['repositoryFile', 'passwordFile', 'environmentFile', 'alertHook']:
        path = private_path(config[field])
        if not path.is_relative_to(root / 'secrets'):
            raise ValueError('backup credentials and alert hook must stay inside private installation secrets')
        private_path(path.parent, directory=True)
    repository = Path(config['repositoryFile']).read_text().strip()
    if (len(repository) > 2048 or '\n' in repository or '\r' in repository
            or not repository.startswith(('sftp:', 's3:', 'b2:', 'azure:', 'gs:', 'rest:https://'))):
        raise ValueError('off-host restic repository required')
    if repository.startswith('sftp:'):
        host = repository[5:].split('@')[-1].split(':')[0]
        if not host or '/' in host or host in {'localhost', '127.0.0.1', '[', os.uname().nodename}:
            raise ValueError('off-host SFTP server required')
    if not Path(config['passwordFile']).read_bytes().strip():
        raise ValueError('restic password is empty')
    environment = read_json(Path(config['environmentFile']))
    if (type(environment) is not dict or set(environment) - ACCESS_ENV
            or any(not isinstance(v, str) or '\x00' in v for v in environment.values())):
        raise ValueError('unsupported restic access environment')
    if environment.get('GOOGLE_APPLICATION_CREDENTIALS') is not None:
        google = private_path(environment['GOOGLE_APPLICATION_CREDENTIALS'])
        if not google.is_relative_to(root / 'secrets'):
            raise ValueError('Google backup credentials must stay inside private installation secrets')
    if not os.access(config['alertHook'], os.X_OK):
        raise ValueError('private alert hook must be executable')
    return config


def evidence(root):
    path = root / 'state/backups.json'
    return read_json(private_path(path)) if path.exists() else {'format': 1, 'replicas': {}}


def save(root, value):
    atomic_json(private_path(root / 'state', directory=True) / 'backups.json', value)


def notify(root, config, stage):
    path = root / 'state/backup-alert.json'
    atomic_json(path, {'format': 1, 'source': 'ctc-manager-backups', 'stage': stage, 'time': time.time()})
    result = subprocess.run([config['alertHook'], str(path)], cwd=root, env={
        'PATH': os.defpath, 'HOME': str(Path.home())}, stdin=subprocess.DEVNULL,
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=30)
    if result.returncode:
        raise ValueError('backup alert delivery failed; inspect private hook and user journal')


class Restic:
    def __init__(self, root, config):
        self.root, self.config = root, config
        self.environment = {'PATH': os.environ.get('PATH', os.defpath), 'HOME': str(Path.home()),
                            'RESTIC_REPOSITORY_FILE': config['repositoryFile'],
                            'RESTIC_PASSWORD_FILE': config['passwordFile'],
                            **read_json(Path(config['environmentFile']))}

    def command(self, arguments, cwd=None):
        state = private_path(self.root / 'state', directory=True)
        with tempfile.TemporaryFile(dir=state) as output, tempfile.TemporaryFile(dir=state) as diagnostic:
            result = subprocess.run(['restic', *arguments], cwd=cwd or self.root, env=self.environment,
                                    stdin=subprocess.DEVNULL, stdout=output, stderr=diagnostic, timeout=3600)
            length = output.tell()
            if result.returncode or length > 16 * 1024 ** 2:
                diagnostic.seek(0)
                path = state / 'restic-last-error.log'
                if path.exists():
                    private_path(path)
                descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC | os.O_NOFOLLOW, 0o600)
                with os.fdopen(descriptor, 'wb') as target:
                    target.write(diagnostic.read(1024 * 1024))
                raise ValueError('restic operation failed; no remote completion recorded')
            output.seek(0)
            return output.read().decode()

    def verify(self):
        value = json.loads(self.command(['cat', 'config']))
        if value.get('id') != self.config['repositoryId']:
            raise ValueError('restic repository identity mismatch')

    def tags(self, manifest, checksum):
        return ['ctc-installation:' + manifest['installationId'], 'ctc-unit:' + manifest['id'],
                'ctc-manifest:' + checksum]

    def confirm(self, unit, receipt):
        manifest = validate_unit(unit)
        if (receipt.get('manifestSha256') != sha256(unit / 'manifest.json')
                or receipt.get('unitId') != manifest['id'] or receipt.get('unitPath') != str(unit)
                or receipt.get('repositoryId') != self.config['repositoryId']
                or not isinstance(receipt.get('snapshot'), str) or not re.fullmatch('[a-f0-9]{64}', receipt['snapshot'])):
            raise ValueError('replica identity mismatch')
        snapshots = json.loads(self.command(['snapshots', '--json', receipt['snapshot']]))
        if (not isinstance(snapshots, list) or len(snapshots) != 1
                or snapshots[0].get('id') != receipt['snapshot'] or snapshots[0].get('paths') != [str(unit)]
                or not set(self.tags(manifest, receipt['manifestSha256'])).issubset(snapshots[0].get('tags', []))):
            raise ValueError('remote snapshot identity mismatch')

    def backup(self, unit, manifest):
        checksum = sha256(unit / 'manifest.json')
        arguments = ['backup', '--json', '--host', 'ctc-manager-' + manifest['installationId']]
        for tag in self.tags(manifest, checksum):
            arguments += ['--tag', tag]
        output = self.command(arguments + ['--', unit.name], cwd=unit.parent)
        summaries = [row for line in output.splitlines() if (row := json.loads(line)).get('message_type') == 'summary']
        if len(summaries) != 1:
            raise ValueError('missing unique restic backup summary')
        receipt = {'snapshot': summaries[0].get('snapshot_id'), 'repositoryId': self.config['repositoryId'],
                   'manifestSha256': checksum, 'unitId': manifest['id'], 'unitPath': str(unit),
                   'completedAt': time.time()}
        self.confirm(unit, receipt)
        return receipt

    def retrieve(self, unit, receipt, staging):
        self.verify()
        self.confirm(unit, receipt)
        self.command(['restore', receipt['snapshot'] + ':/' + unit.name, '--target', str(staging)])
        if sha256(staging / 'manifest.json') != receipt['manifestSha256']:
            raise ValueError('retrieved manifest identity mismatch')
        validate_unit(staging)


def unit_inventory(runtime, destination):
    private_path(destination, directory=True)
    result = {}
    for path in destination.iterdir():
        if not re.fullmatch(r'recovery-[a-zA-Z0-9-]+', path.name):
            raise ValueError('unknown backup entry; reconcile before pruning')
        manifest = validate_unit(path)
        if manifest['installationId'] != runtime.config.get('installationId'):
            raise ValueError('backup installation identity mismatch')
        result[path] = manifest
    return result


def retention_plan(runtime, destination, state, now):
    if state.get('lastAttempt', {}).get('stage') != 'completed':
        raise ValueError('latest backup failed or is incomplete; pruning forbidden')
    units = unit_inventory(runtime, destination)
    replicas = state.get('replicas', {})
    keep = set()
    ordered = sorted(units, key=lambda p: units[p]['createdAt'], reverse=True)
    buckets = [set(), set(), set()]
    for path in ordered:
        date = dt.datetime.strptime(units[path]['createdAt'], '%Y-%m-%dT%H:%M:%SZ').replace(tzinfo=dt.timezone.utc)
        if date.timestamp() > now + 60:
            raise ValueError('future backup timestamp')
        for index, (key, limit) in enumerate([(date.date(), 14), (date.isocalendar()[:2], 8), ((date.year,date.month), 12)]):
            if key not in buckets[index] and len(buckets[index]) < limit:
                buckets[index].add(key)
                keep.add(path)
        replica = replicas.get(str(path))
        if replica is None:
            keep.add(path)
        elif (replica.get('manifestSha256') != sha256(path/'manifest.json')
              or replica.get('unitId') != units[path]['id'] or replica.get('unitPath') != str(path)):
            raise ValueError('replica receipt no longer matches sealed unit')
    protected_path = runtime.root / 'state/protected-recovery.json'
    if protected_path.exists():
        protected = read_json(private_path(protected_path))
        selected = Path(protected['unit'])
        if not selected.exists() or sha256(private_path(selected/'manifest.json')) != protected['manifestSha256']:
            raise ValueError('protected recovery reference changed')
        keep.update([selected, *map(Path, protected.get('previousUnits', []))])
    update = runtime.root / 'state/update.json'
    if update.exists():
        receipt = read_json(private_path(update))
        if receipt.get('stage') != 'completed' and receipt.get('recoveryUnit'):
            keep.add(Path(receipt['recoveryUnit']))
    if ordered:
        keep.add(ordered[0])
    return [path for path in ordered if path not in keep]


def prune(runtime, destination, remote, *, dry_run=False, now=None):
    runtime.check_destination(destination)
    remote.verify()
    state = evidence(runtime.root)
    plan = retention_plan(runtime, destination, state, time.time() if now is None else now)
    for path in plan:
        remote.confirm(path, state['replicas'][str(path)])
    if not dry_run:
        if retention_plan(runtime, destination, evidence(runtime.root), time.time() if now is None else now) != plan:
            raise ValueError('retention plan changed before deletion')
        for path in plan:
            shutil.rmtree(path)
            state['replicas'].pop(str(path))
        save(runtime.root, state)
    return plan


def proof_matches(proof, unit, manifest):
    return (proof.get('status') == 'passed' and proof.get('unitId') == manifest['id']
            and proof.get('manifestSha256') == sha256(unit/'manifest.json')
            and proof.get('outboundEnabled') is False and proof.get('credentialsVerified') is True
            and proof.get('hostPorts') == [])


def run_backup(runtime, destination, remote, *, capture_unit=capture, prove_unit=restore_check, alert=None, now=None):
    identity = runtime.config.get('installationId')
    try:
        if not isinstance(identity, str) or str(uuid.UUID(identity)) != identity:
            raise ValueError()
    except (ValueError, TypeError, AttributeError):
        raise ValueError('canonical installation identity required; create the initial backup first') from None
    stamp = lambda: time.time() if now is None else now
    state = evidence(runtime.root)
    state['installationId'] = identity
    state['lastAttempt'] = {'stage': 'running', 'time': stamp()}
    save(runtime.root, state)
    stage = 'mount-failed'
    try:
        runtime.check_destination(destination)
        stage = 'remote-configuration-failed'
        remote.verify()
        stage = 'capture-failed'
        unit = Path(capture_unit(runtime, destination))
        manifest = validate_unit(unit)
        if unit.parent != destination or manifest['installationId'] != runtime.config.get('installationId'):
            raise ValueError('captured backup identity mismatch')
        state['local'] = {'completedAt': stamp(), 'unit': str(unit), 'manifestSha256': sha256(unit/'manifest.json')}
        state['lastAttempt'] = {'stage': 'proving', 'time': stamp()}
        save(runtime.root, state)
        stage = 'proof-failed'
        proof = prove_unit(runtime, unit)
        if not proof_matches(proof, unit, manifest):
            raise ValueError('restore proof identity mismatch')
        state['proof'] = {'completedAt': stamp(), 'unit': str(unit)}
        save(runtime.root, state)
        stage = 'replication-failed'
        for path, metadata in unit_inventory(runtime, destination).items():
            if str(path) not in state['replicas']:
                state['replicas'][str(path)] = remote.backup(path, metadata)
                save(runtime.root, state)
        state['remote'] = {'completedAt': stamp(), 'unit': str(unit)}
        state['lastAttempt'] = {'stage': 'completed', 'time': stamp()}
        save(runtime.root, state)
        stage = 'retention-failed'
        prune(runtime, destination, remote, now=stamp())
        return state
    except Exception:
        state['lastAttempt'] = {'stage': stage, 'time': stamp()}
        save(runtime.root, state)
        if alert:
            alert(stage)
        raise ValueError(stage + '; inspect private backup evidence') from None


def prove_retrieved(runtime, directory, *, prove_unit=restore_check):
    manifest = validate_unit(directory)
    selected = select_recovery_release(runtime.root, read_json(directory / 'release.json'))
    proof_runtime = NativeRuntime(runtime.root, selected, require_selected=False)
    proof = prove_unit(proof_runtime, directory)
    if not proof_matches(proof, directory, manifest):
        raise ValueError('retrieved isolated proof mismatch')
    return proof


def freshness(state, now):
    problems = []
    for field in ['local', 'remote', 'proof']:
        timestamp = state.get(field, {}).get('completedAt')
        if type(timestamp) not in [int, float] or not math.isfinite(timestamp) or timestamp > now + 60:
            problems.append(field + ('-missing' if timestamp is None else '-invalid'))
        elif now - timestamp > 30 * 3600:
            problems.append(field + '-stale')
    if state.get('lastAttempt', {}).get('stage') != 'completed':
        problems.append('last-attempt-failed')
    return problems


def main():
    import argparse
    parser = argparse.ArgumentParser(allow_abbrev=False)
    parser.add_argument('--directory', required=True)
    parser.add_argument('--deploy-root')
    parser.add_argument('operation', choices=['maintenance', 'prune', 'backup-check', 'retrieve-check', 'alert', 'doctor'])
    parser.add_argument('--dry-run', action='store_true')
    parser.add_argument('--unit')
    parser.add_argument('--source')
    args = parser.parse_args()
    if (args.dry_run and args.operation != 'prune'
            or args.unit is not None and args.operation != 'retrieve-check'
            or args.operation == 'retrieve-check' and args.unit is None
            or args.source is not None and args.operation != 'alert'):
        parser.error('options must match the selected backup operation')
    os.umask(0o077)
    root = private_path(Path(args.directory), directory=True)
    if args.operation == 'doctor':
        problems = freshness(evidence(root), time.time())
        try:
            load_backup(root)
        except (OSError, ValueError, KeyError, TypeError):
            problems.append('backup-configuration-incomplete')
        print(json.dumps({'backupStatus': 'incomplete' if problems else 'ready', 'issues': problems}))
        if problems:
            raise ValueError('backup readiness incomplete')
        return
    config = load_backup(root)
    if args.operation == 'alert':
        notify(root, config, 'scheduled-service-failed')
        return
    runtime = NativeRuntime(root, args.deploy_root or (root/'current').resolve())
    remote = Restic(root, config)
    if args.operation == 'backup-check':
        issues = freshness(evidence(root), time.time())
        try:
            runtime.check_destination(UNIT_ROOT)
        except (OSError, ValueError):
            issues.append('backup-mount-unavailable')
        result = {'status': 'failed' if issues else 'ready', 'issues': issues}
        print(json.dumps(result))
        if issues:
            raise ValueError('backup readiness failed')
    elif args.operation == 'maintenance':
        result = run_backup(runtime, UNIT_ROOT, remote, alert=lambda stage: notify(root, config, stage))
        print(json.dumps({'status': result['lastAttempt']['stage']}))
    elif args.operation == 'prune':
        removed = prune(runtime, UNIT_ROOT, remote, dry_run=args.dry_run)
        print(json.dumps({'status':'planned' if args.dry_run else 'pruned','units':len(removed)}))
    else:
        unit = private_path(Path(args.unit or ''), directory=True)
        if unit.parent != UNIT_ROOT:
            raise ValueError('select one known local unit for remote retrieval')
        receipt = evidence(root)['replicas'][str(unit)]
        directory = Path(tempfile.mkdtemp(prefix='remote-check-', dir=root/'state'))
        try:
            remote.retrieve(unit, receipt, directory)
            prove_retrieved(runtime, directory)
            state = evidence(root)
            state['retrieval'] = {'completedAt': time.time(), 'unit': str(unit), 'snapshot': receipt['snapshot']}
            save(root, state)
            print(json.dumps({'status':'remote-retrieval-proved'}))
        finally:
            shutil.rmtree(directory)


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, KeyError, TypeError, json.JSONDecodeError, subprocess.SubprocessError):
        __import__('sys').exit('CTC: backup operation failed; inspect private evidence and user service journal')
