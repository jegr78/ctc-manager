#!/usr/bin/env python3
import datetime as dt
import json
import math
import os
from pathlib import Path
import re
import shutil
import sys
import time
import uuid
from zoneinfo import ZoneInfo

from config import atomic_json, private_path
from recovery import NativeRuntime, capture, read_json, restore_check, sha256, validate_unit

UNIT_ROOT = Path('/mnt/backup/ctc-manager/units')


def evidence(root):
    path = root / 'state/backups.json'
    return read_json(private_path(path)) if path.exists() else {'format': 1}


def save(root, value):
    atomic_json(private_path(root / 'state', directory=True) / 'backups.json', value)


def unit_inventory(runtime, destination):
    private_path(destination, directory=True)
    units = {}
    for path in destination.iterdir():
        if not re.fullmatch(r'recovery-[a-zA-Z0-9-]+', path.name):
            raise ValueError('unknown backup entry; reconcile before pruning')
        manifest = validate_unit(path)
        if manifest['installationId'] != runtime.config.get('installationId'):
            raise ValueError('backup installation identity mismatch')
        units[path] = manifest
    return units


def retention_plan(runtime, destination, state, now):
    if state.get('lastAttempt', {}).get('stage') != 'completed':
        raise ValueError('latest backup failed or is incomplete; pruning forbidden')
    units = unit_inventory(runtime, destination)
    local, proof = state.get('local', {}), state.get('proof', {})
    latest = Path(local.get('unit', ''))
    if (latest not in units or local.get('manifestSha256') != sha256(latest / 'manifest.json')
            or proof.get('unit') != str(latest) or proof.get('manifestSha256') != local.get('manifestSha256')):
        raise ValueError('last verified backup is missing or its proof changed')
    ordered = sorted(units, key=lambda p: (units[p]['createdAt'], p.name), reverse=True)
    dates = {}
    for path in ordered:
        stamp = dt.datetime.strptime(units[path]['createdAt'], '%Y-%m-%dT%H:%M:%SZ').replace(tzinfo=dt.timezone.utc)
        if stamp.timestamp() > now + 60:
            raise ValueError('future backup timestamp')
        dates[path] = stamp.astimezone(ZoneInfo('Europe/Berlin'))
    keep, daily = {latest}, set()
    for path in ordered:
        day = dates[path].date()
        if day not in daily and len(daily) < 2:
            daily.add(day)
            keep.add(path)
    if ordered:
        month = (dates[ordered[0]].year, dates[ordered[0]].month)
        keep.add(next(path for path in reversed(ordered) if (dates[path].year, dates[path].month) == month))
    update = runtime.root / 'state/update.json'
    pending_update = not update.exists() or read_json(private_path(update)).get('stage') != 'completed'
    local, proof = state.get('local', {}), state.get('proof', {})
    verified_active = local.get('appImage') == runtime.config.get('appImage')
    protected_path = runtime.root / 'state/protected-recovery.json'
    if protected_path.exists():
        protected = read_json(private_path(protected_path))
        selected = Path(protected['unit'])
        if not selected.exists() or sha256(private_path(selected / 'manifest.json')) != protected['manifestSha256']:
            raise ValueError('protected recovery reference changed')
        if pending_update or not verified_active:
            keep.update([selected, *map(Path, protected.get('previousUnits', []))])
    if update.exists():
        receipt = read_json(private_path(update))
        if receipt.get('stage') != 'completed' and receipt.get('recoveryUnit'):
            keep.add(Path(receipt['recoveryUnit']))
    return [path for path in ordered if path not in keep]


def prune(runtime, destination, *, dry_run=False, now=None):
    runtime.check_destination(destination)
    timestamp = time.time() if now is None else now
    plan = retention_plan(runtime, destination, evidence(runtime.root), timestamp)
    if not dry_run:
        if retention_plan(runtime, destination, evidence(runtime.root), timestamp) != plan:
            raise ValueError('retention plan changed before deletion')
        for path in plan:
            shutil.rmtree(path)
        protected = runtime.root / 'state/protected-recovery.json'
        if protected.exists() and Path(read_json(private_path(protected))['unit']) in plan:
            protected.unlink()
    return plan


def proof_matches(proof, unit, manifest):
    return (proof.get('status') == 'passed' and proof.get('unitId') == manifest['id']
            and proof.get('manifestSha256') == sha256(unit / 'manifest.json')
            and proof.get('outboundEnabled') is False and proof.get('credentialsVerified') is True
            and proof.get('hostPorts') == [])


def run_backup(runtime, destination, *, capture_unit=capture, prove_unit=restore_check, now=None):
    identity = runtime.config.get('installationId')
    try:
        if not isinstance(identity, str) or str(uuid.UUID(identity)) != identity:
            raise ValueError()
    except (ValueError, TypeError, AttributeError):
        raise ValueError('canonical installation identity required; create the initial backup first') from None
    def stamp():
        return time.time() if now is None else now
    previous = evidence(runtime.root)
    state = {key: previous[key] for key in ['local', 'proof'] if key in previous}
    state.update(format=1, installationId=identity, lastAttempt={'stage': 'running', 'time': stamp()})
    save(runtime.root, state)
    stage = 'mount-failed'
    try:
        runtime.check_destination(destination)
        stage = 'capture-failed'
        unit = Path(capture_unit(runtime, destination))
        manifest = validate_unit(unit)
        if unit.parent != destination or manifest['installationId'] != identity:
            raise ValueError('captured backup identity mismatch')
        state['local'] = {'completedAt': stamp(), 'unit': str(unit),
                          'manifestSha256': sha256(unit / 'manifest.json'), 'appImage': manifest['appImage']}
        state['lastAttempt'] = {'stage': 'proving', 'time': stamp()}
        save(runtime.root, state)
        stage = 'proof-failed'
        proof = prove_unit(runtime, unit)
        if not proof_matches(proof, unit, manifest):
            raise ValueError('restore proof identity mismatch')
        state['proof'] = {'completedAt': stamp(), 'unit': str(unit), 'manifestSha256': sha256(unit / 'manifest.json')}
        state['lastAttempt'] = {'stage': 'completed', 'time': stamp()}
        save(runtime.root, state)
        stage = 'retention-failed'
        prune(runtime, destination, now=stamp())
        return state
    except Exception:
        state['lastAttempt'] = {'stage': stage, 'time': stamp()}
        save(runtime.root, state)
        raise ValueError(stage + '; inspect local backup evidence') from None


def freshness(state, now):
    issues = []
    for field in ['local', 'proof']:
        timestamp = state.get(field, {}).get('completedAt')
        if type(timestamp) not in [int, float] or not math.isfinite(timestamp) or timestamp > now + 60:
            issues.append(field + ('-missing' if timestamp is None else '-invalid'))
        elif now - timestamp > 30 * 3600:
            issues.append(field + '-stale')
    if state.get('lastAttempt', {}).get('stage') != 'completed':
        issues.append('last-attempt-failed')
    return issues


def main():
    import argparse
    parser = argparse.ArgumentParser(allow_abbrev=False)
    parser.add_argument('--directory', required=True)
    parser.add_argument('--deploy-root')
    parser.add_argument('operation', choices=['maintenance', 'prune', 'backup-check', 'doctor'])
    parser.add_argument('--dry-run', action='store_true')
    args = parser.parse_args()
    if args.dry_run and args.operation != 'prune':
        parser.error('--dry-run is only valid for prune')
    os.umask(0o077)
    root = private_path(Path(args.directory), directory=True)
    if args.operation in ['doctor', 'backup-check']:
        issues = freshness(evidence(root), time.time())
        try:
            runtime = NativeRuntime(root, (root / 'current').resolve())
            runtime.check_destination(UNIT_ROOT)
        except (OSError, ValueError):
            issues.append('backup-mount-unavailable')
        if not issues:
            try:
                retention_plan(runtime, UNIT_ROOT, evidence(root), time.time())
            except (OSError, ValueError):
                issues.append('backup-verification-failed')
        print(json.dumps({'backupStatus': 'incomplete' if issues else 'ready', 'issues': issues}))
        if issues:
            raise ValueError('backup readiness incomplete')
        return
    runtime = NativeRuntime(root, (root / 'current').resolve())
    lock = private_path(root / '.ctc-manager.lock', directory=True)
    if private_path(lock / 'owner').read_text().strip() != str(os.getppid()):
        raise ValueError('backup command requires its launcher-owned installation lock')
    if args.operation == 'maintenance':
        runtime.check_destination(UNIT_ROOT.parent)
        UNIT_ROOT.mkdir(mode=0o700, exist_ok=True)
        result = run_backup(runtime, UNIT_ROOT)
        print(json.dumps({'status': result['lastAttempt']['stage'], 'unit': result['local']['unit']}))
    else:
        removed = prune(runtime, UNIT_ROOT, dry_run=args.dry_run)
        print(json.dumps({'status': 'planned' if args.dry_run else 'pruned', 'units': len(removed)}))


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, KeyError, TypeError, json.JSONDecodeError) as error:
        sys.exit('CTC backup: ' + str(error))
