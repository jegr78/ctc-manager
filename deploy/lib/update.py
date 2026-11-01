#!/usr/bin/env python3
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import tempfile
import urllib.request
import uuid

from config import atomic_json, private_path
from recovery import (NativeRuntime, admit_recovery_image, capture, flush_directory,
                      installed_release, read_json, restore_check, sha256)
from release import MAX_ARCHIVE, verify, version

REPOSITORY = 'jegr78/ctc-manager'
MIN_FREE = 3 * 1024 ** 3


def version_key(value):
    version(value)
    release, separator, rc = value.partition('-rc.')
    return (*map(int, release.split('.')), -1 if separator else 0, int(rc) if separator else 0)


class GitHubReleases:
    def retrieve(self, url, limit):
        request = urllib.request.Request(url, headers={'User-Agent': 'ctc-manager-deployment',
                                                       'Accept': 'application/vnd.github+json'})
        with urllib.request.urlopen(request, timeout=30) as response:
            value = response.read(limit + 1)
        if len(value) > limit:
            raise ValueError('release download exceeds limit')
        return value

    def describe(self, selected):
        return json.loads(self.retrieve('https://api.github.com/repos/' + REPOSITORY +
                                       '/releases/tags/v' + selected, 2 * 1024 ** 2))

    def download(self, url, destination, limit):
        with Path(destination).open('xb') as output:
            output.write(self.retrieve(url, limit))


def upgrade_contract(directory, source_version):
    contract = read_json(Path(directory) / 'upgrade.json')
    if (type(contract) is not dict
            or set(contract) != {'format', 'minimumSourceVersion', 'recoveryCapability', 'notes'}
            or type(contract['format']) is not int or contract['format'] != 1
            or type(contract['recoveryCapability']) is not int or contract['recoveryCapability'] != 1
            or contract['notes'] != 'guides/updates.md'):
        raise ValueError('unsupported upgrade contract')
    if version_key(source_version) < version_key(contract['minimumSourceVersion']):
        raise ValueError('source release is older than the supported upgrade minimum')
    if not (Path(directory) / contract['notes']).read_text().strip():
        raise ValueError('candidate upgrade notes are missing')


def compatibility(runtime, directory, manifest):
    upgrade_contract(directory, runtime.release['version'])
    for key in ['appImage', 'databaseImage']:
        runtime.docker(['pull', manifest[key]], timeout=900)
    app = json.loads(runtime.docker(['image', 'inspect', manifest['appImage']]))[0]
    admit_recovery_image(app)
    database = json.loads(runtime.docker(['image', 'inspect', manifest['databaseImage']]))[0]
    database_version = (database.get('Config', {}).get('Labels') or {}).get('org.opencontainers.image.version', '')
    source_version = runtime.database_metadata()['databaseVersion']
    if (not re.match(r'^[0-9]+\.', database_version) or not re.match(r'^[0-9]+\.', source_version)
            or database_version.split('.')[0] != source_version.split('.')[0]):
        raise ValueError('ordinary updates cannot change the database major version')
    owners = []
    for image in [runtime.config['appImage'], manifest['appImage']]:
        owner = runtime.docker(['run', '--rm', '--network', 'none', '--read-only', '--cap-drop', 'ALL',
                                '--security-opt', 'no-new-privileges', '--entrypoint', 'id', image, '-u']).decode().strip()
        if not re.fullmatch(r'[1-9][0-9]*', owner):
            raise ValueError('application image must have a non-root numeric UID')
        owners.append(owner)
    if owners[0] != owners[1]:
        raise ValueError('application UID changes need an explicit storage ownership migration')


def stage_release(runtime, selected, provider=None):
    version(selected)
    if version_key(selected) <= version_key(runtime.release['version']):
        raise ValueError('select a newer released version; downgrades and mutable versions are unsupported')
    releases = private_path(runtime.root / 'releases', directory=True)
    state = private_path(runtime.root / 'state', directory=True)
    if shutil.disk_usage(runtime.root).free < MIN_FREE:
        raise ValueError('insufficient staging/image space')
    runtime.preflight()
    provider = provider or GitHubReleases()
    metadata = provider.describe(selected)
    if (metadata.get('tag_name') != 'v' + selected or metadata.get('draft') is not False
            or metadata.get('prerelease') is not ('-rc.' in selected)):
        raise ValueError('requested release identity or publication status differs')
    name = 'ctc-manager-deployment-' + selected + '.tar.gz'
    names = [name, name + '.sha256']
    urls = {}
    for asset in metadata.get('assets', []):
        if asset.get('name') in names:
            key = asset['name']
            expected = 'https://github.com/' + REPOSITORY + '/releases/download/v' + selected + '/' + key
            if key in urls or asset.get('browser_download_url') != expected:
                raise ValueError('unexpected release asset repository or duplicate identity')
            urls[key] = expected
    if set(urls) != set(names):
        raise ValueError('release deployment/checksum asset is missing')
    staging = Path(tempfile.mkdtemp(prefix='.candidate-', dir=state))
    try:
        for key in names:
            provider.download(urls[key], staging / key, MAX_ARCHIVE if key == name else 1024)
            (staging / key).chmod(0o600)
        checksum = (staging / (name + '.sha256')).read_text()
        match = re.fullmatch(r'([a-f0-9]{64})  ' + re.escape(name) + r'\n?', checksum)
        if not match or sha256(staging / name) != match.group(1):
            raise ValueError('release archive checksum mismatch')
        manifest = verify(staging / name)
        if manifest['version'] != selected:
            raise ValueError('release manifest version differs from requested version')
        extracted = staging / 'release'
        extracted.mkdir(mode=0o700)
        import tarfile
        with tarfile.open(staging / name, 'r:gz') as archive:
            for member in archive:
                relative = PurePosixPath(*PurePosixPath(member.name).parts[1:])
                path = extracted / relative
                if not path.is_relative_to(extracted):
                    raise ValueError('unsafe release extraction path')
                path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
                with path.open('xb') as output:
                    output.write(archive.extractfile(member).read())
                    output.flush()
                    os.fsync(output.fileno())
                path.chmod(0o700 if path.name in ['ctc-manager', 'setup-host.sh'] else 0o600)
        if installed_release(extracted) != manifest:
            raise ValueError('extracted release identity differs')
        compatibility(runtime, extracted, manifest)
        target = releases / selected
        if target.exists() or target.is_symlink():
            private_path(target, directory=True)
            if installed_release(target) != manifest:
                raise ValueError('published release changed; never overwrite installed release material')
        else:
            for directory in sorted([p for p in extracted.rglob('*') if p.is_dir()],
                                    key=lambda p: len(p.parts), reverse=True):
                flush_directory(directory)
            flush_directory(extracted)
            os.rename(extracted, target)
            flush_directory(releases)
        return target
    finally:
        shutil.rmtree(staging)


def execute_update(runtime, candidate, destination, *, capture_unit=capture, prove_unit=restore_check):
    state = private_path(runtime.root / 'state', directory=True)
    journal = state / 'update.json'
    if journal.exists():
        previous = read_json(private_path(journal))
        if type(previous) is not dict or previous.get('stage') not in ['completed', 'failed-before-activation']:
            raise ValueError('reconcile the unresolved update journal before another update')
    candidate = private_path(Path(candidate), directory=True)
    releases = private_path(runtime.root / 'releases', directory=True)
    manifest = installed_release(candidate)
    if candidate.parent != releases or candidate.name != manifest['version']:
        raise ValueError('candidate must be an owned versioned installed release')
    if version_key(manifest['version']) <= version_key(runtime.release['version']):
        raise ValueError('candidate must be a newer released version')
    runtime.preflight()
    compatibility(runtime, candidate, manifest)
    destination = private_path(Path(destination), directory=True)
    runtime.check_destination(destination)
    source = dict(runtime.config)
    source_release = runtime.deploy_root.resolve()
    current = runtime.root / 'current'

    def unchanged():
        try:
            return (read_json(private_path(runtime.root / 'config/installation.json')) == source
                    and current.is_symlink() and current.resolve() == source_release)
        except (OSError, ValueError):
            return False

    if not unchanged():
        raise ValueError('active source selection changed before update')
    was_running = runtime.is_app_running()
    operation = uuid.uuid4().hex
    predecessor = state / ('update-before-' + operation + '.json')
    atomic_json(predecessor, {'configuration': source, 'releaseDirectory': str(source_release)})
    receipt = {'format': 1, 'operationId': operation, 'stage': 'prepared',
               'sourceVersion': runtime.release['version'], 'targetVersion': manifest['version'],
               'oldAppImage': source['appImage'], 'newAppImage': manifest['appImage'],
               'oldDatabaseImage': source['databaseImage'], 'newDatabaseImage': manifest['databaseImage'],
               'previousConfiguration': str(predecessor), 'wasRunning': was_running,
               'recoveryUnit': None}

    def record(stage):
        receipt['stage'] = stage
        atomic_json(journal, receipt)

    activated = False
    record('prepared')
    try:
        record('freezing')
        if was_running:
            runtime.stop_app()
        record('capturing-held')
        unit = private_path(Path(capture_unit(runtime, destination, hold_stopped=True)), directory=True)
        if not unit.is_relative_to(destination):
            raise ValueError('captured recovery unit is outside the selected destination')
        unit_manifest = read_json(private_path(unit / 'manifest.json'))
        unit_hash = sha256(unit / 'manifest.json')
        receipt.update(recoveryUnit=str(unit), recoveryManifestSha256=unit_hash, recoveryUnitId=unit_manifest['id'])
        protected_path = state / 'protected-recovery.json'
        retained = read_json(private_path(protected_path)) if protected_path.exists() else {}
        protected = {'format': 1, 'unit': str(unit), 'manifestSha256': unit_hash,
                     'previousUnits': retained.get('previousUnits', []) + ([retained['unit']] if retained.get('unit') else [])}
        atomic_json(protected_path, protected)
        record('proving-held')
        proof = prove_unit(runtime, unit)
        if not unchanged():
            raise ValueError('active source selection changed during the restore proof')
        if installed_release(candidate) != manifest:
            raise ValueError('candidate identity changed during the restore proof')
        if (proof.get('status') != 'passed' or proof.get('unitId') != unit_manifest['id']
                or proof.get('manifestSha256') != unit_hash or sha256(unit / 'manifest.json') != unit_hash
                or proof.get('outboundEnabled') is not False or proof.get('credentialsVerified') is not True
                or proof.get('hostPorts') != []):
            raise ValueError('restore proof receipt does not match the captured unit and isolation contract')
        if runtime.is_app_running():
            raise ValueError('source app restarted during the update freeze')
        runtime.preflight()
        protected['previousUnits'] = []
        atomic_json(protected_path, protected)
        target = {**source, 'appImage': manifest['appImage'], 'databaseImage': manifest['databaseImage'],
                  'outboundPaused': True}
        record('activating')
        activated = True
        atomic_json(runtime.root / 'config/installation.json', target)
        temporary = runtime.root / ('.current-' + operation)
        temporary.symlink_to(candidate)
        os.replace(temporary, current)
        flush_directory(runtime.root)
        runtime.config, runtime.release, runtime.deploy_root = target, manifest, candidate
        runtime.compose(['up', '-d', '--wait', '--wait-timeout', '180'])
        runtime.preflight()
        record('healthy-integrations-paused')
        if not was_running:
            runtime.stop_app()
        runtime.config = {**target, 'outboundPaused': source.get('outboundPaused', False)}
        atomic_json(runtime.root / 'config/installation.json', runtime.config)
        if was_running and runtime.config['outboundPaused'] is False:
            runtime.compose(['up', '-d', '--wait', '--wait-timeout', '180', 'app'])
            runtime.preflight()
        record('completed')
        return receipt
    except Exception:
        if activated:
            runtime.config = {**runtime.config, 'outboundPaused': True}
            atomic_json(runtime.root / 'config/installation.json', runtime.config)
            try:
                runtime.compose(['stop', 'app'])
            except Exception:
                receipt['candidateStopFailed'] = True
            record('failed-recovery-required')
        elif unchanged():
            if was_running and not runtime.is_app_running():
                record('failed-review-required')
                runtime.start_app()
            record('failed-before-activation')
        else:
            record('failed-review-required')
        raise


def main():
    import argparse
    parser = argparse.ArgumentParser(allow_abbrev=False)
    parser.add_argument('--directory', required=True)
    parser.add_argument('--deploy-root', required=True)
    parser.add_argument('operation', choices=['update-check', 'update'])
    parser.add_argument('--release', required=True)
    arguments = parser.parse_args()
    version(arguments.release)
    root = private_path(Path(arguments.directory), directory=True)
    lock = private_path(root / '.ctc-manager.lock', directory=True)
    if private_path(lock / 'owner').read_text().strip() != str(os.getppid()):
        raise ValueError('update command must run under its launcher-owned installation lock')
    tools = private_path(Path(arguments.deploy_root).resolve(), directory=True)
    installed_release(tools)
    source = (root / 'current').resolve()
    if source.parent != root / 'releases':
        raise ValueError('current must select an owned versioned installed release')
    runtime = NativeRuntime(root, source)
    state = private_path(root / 'state', directory=True)
    runtime.diagnostic = state / ('update-operation-' + uuid.uuid4().hex + '.log')
    runtime.diagnostic.touch(mode=0o600)
    candidate = stage_release(runtime, arguments.release)
    if arguments.operation == 'update-check':
        manifest = installed_release(candidate)
        result = {'status': 'ready', 'version': manifest['version'], 'appImage': manifest['appImage'],
                  'databaseImage': manifest['databaseImage'], 'directory': str(candidate),
                  'notes': str(candidate / 'guides/updates.md')}
    else:
        parent = private_path(Path('/mnt/backup/ctc-manager'), directory=True)
        runtime.check_destination(parent)
        destination = parent / 'units'
        destination.mkdir(mode=0o700, exist_ok=True)
        result = execute_update(runtime, candidate, destination)
    print(json.dumps(result))


if __name__ == '__main__':
    import sys
    import tarfile
    try:
        main()
    except (ValueError, OSError, KeyError, TypeError, json.JSONDecodeError, tarfile.TarError) as error:
        sys.exit('CTC update: ' + str(error))
