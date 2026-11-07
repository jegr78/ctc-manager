#!/usr/bin/env python3
"""In-place deploy and restore for the single CTC Manager installation."""
from contextlib import contextmanager
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import uuid

from config import atomic_json, private_path
from recovery import (NativeRuntime, admit_recovery_image, capture, flush_directory, installed_release, read_json,
                      select_recovery_release, sha256, validate_unit)
from release import EXECUTABLES, MAX_ARCHIVE, verify

REPOSITORY = 'jegr78/ctc-manager'
UNIT_ROOT = Path('/mnt/backup/ctc-manager/units')
HISTORY = ("SELECT JSON_OBJECT('rank',installed_rank,'version',version,'checksum',checksum,'success',success) "
           "FROM flyway_schema_history ORDER BY installed_rank;")

SEMVER = r'(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)'
SECRETS = {'app.env': 'appEnvFile', 'db-password': 'databasePasswordFile',
           'db-root-password': 'databaseRootPasswordFile', 'google.json': 'googleCredentialsFile'}

PHPMYADMIN = 'ctc-manager-phpmyadmin.service'


def systemctl(command, **kwargs):
    return subprocess.run(command, **kwargs)


@contextmanager
def phpmyadmin_stopped(run=systemctl):
    """Stop phpMyAdmin for the block and start it again afterwards if it ran before."""
    state = run(['systemctl', '--user', 'show', PHPMYADMIN, '--property=ActiveState', '--value'],
                capture_output=True, text=True, check=False).stdout.strip()
    active = state in ('active', 'activating', 'reloading')
    if active:
        run(['systemctl', '--user', 'stop', PHPMYADMIN], check=True)
    try:
        yield
    except BaseException as error:
        if active:
            try:
                run(['systemctl', '--user', 'start', '--no-block', PHPMYADMIN], check=True)
            except (OSError, subprocess.CalledProcessError) as failure:
                error.add_note('phpMyAdmin did not start again: ' + str(failure))
        raise
    if active:
        try:
            run(['systemctl', '--user', 'start', '--no-block', PHPMYADMIN], check=True)
        except (OSError, subprocess.CalledProcessError) as failure:
            print('CTC: phpMyAdmin did not start again: ' + str(failure), file=sys.stderr)


def parse(version):
    if not isinstance(version, str) or not re.fullmatch(SEMVER, version):
        raise ValueError('invalid release version: ' + repr(version))
    return tuple(int(part) for part in version.split('.'))


def switch_release(root, release, outbound_paused):
    """Point installation.json images and the current symlink at one release."""
    root, release = Path(root), Path(release).resolve()
    manifest = read_json(release / 'manifest.json')
    path = root / 'config/installation.json'
    config = read_json(private_path(path))
    config.update(appImage=manifest['appImage'], databaseImage=manifest['databaseImage'], outboundPaused=outbound_paused)
    atomic_json(path, config)
    link = root / ('current-' + uuid.uuid4().hex)
    link.symlink_to(release)
    link.replace(root / 'current')


def volumes(runtime):
    from runtime import compose_spec
    _, environment = compose_spec(runtime.root, runtime.config, runtime.deploy_root)
    return environment['CTC_DATABASE_VOLUME'], environment['CTC_DATA_VOLUME']


def restore(root, unit, runtime_for=NativeRuntime, allow_legacy=False):
    """Replace database, data volume and secrets with a unit, running the release that wrote it."""
    root, unit = Path(root), Path(unit).resolve()
    validate_unit(unit)
    expected = read_json(unit / 'release.json')
    parse(expected.get('version'))
    release = select_recovery_release(root, expected)
    if not allow_legacy and not (release / 'lib/rollout.py').is_file():
        raise ValueError('release ' + expected['version'] + ' predates in-place restore; add --legacy-release to restore it anyway')
    current = runtime_for(root, (root / 'current').resolve())
    current.compose(['rm', '--force', '--stop', 'app', 'db', 'credentials-init'])
    current.docker(['volume', 'rm', '--force', *volumes(current)])
    for name, field in SECRETS.items():
        target = Path(current.config[field])
        staging = target.with_name(target.name + '.' + uuid.uuid4().hex + '.tmp')
        shutil.copyfile(unit / name, staging)
        staging.chmod(0o600)
        staging.replace(target)
    switch_release(root, release, outbound_paused=True)
    target = runtime_for(root, release)
    target.compose(['up', '-d', '--wait', '--wait-timeout', '180', 'db'])
    database = target.container('db')['Id']
    with (unit / 'database.sql').open('rb') as source:
        target.docker(['exec', '-i', database, '/bin/sh', '-ec',
                       'MYSQL_PWD="$(cat /run/secrets/db-password)" exec mariadb --user=ctc --database=ctcdb'],
                      stdin=source, timeout=1800)
    _, data = volumes(target)
    image = target.config['appImage']
    with (unit / 'data.tar').open('rb') as source:
        target.docker(['run', '--rm', '-i', '--network', 'none', '--read-only', '--user', '0:0',
                       '--cap-drop', 'ALL', '--cap-add', 'CHOWN', '--security-opt', 'no-new-privileges',
                       '--mount', 'type=volume,src=' + data + ',dst=/restore,volume-nocopy',
                       '--entrypoint', '/bin/sh', image, '-ec',
                       'umask 077; tar -xf - -C /restore --no-same-owner --no-same-permissions; '
                       'chown -R "$(id -u ctc):$(id -g ctc)" /restore'], stdin=source, timeout=1800)
    target.docker(['run', '--rm', '--network', 'none', '--read-only', '--user', 'ctc', '--cap-drop', 'ALL',
                   '--security-opt', 'no-new-privileges',
                   '--mount', 'type=volume,src=' + data + ',dst=/restore,readonly,volume-nocopy',
                   '--entrypoint', 'sync', image, '-f', '/restore'])
    target.compose(['up', '-d', '--wait', '--wait-timeout', '180'])
    return {'status': 'restored', 'release': expected['version'], 'outboundPaused': True}


class DeployFailed(RuntimeError):
    def __init__(self, step, cause, state, unit=None, rollback_error=None):
        self.step, self.cause, self.rolled_back = step, cause, state != 'stopped'
        if state == 'unchanged':
            outcome = 'nothing was changed'
        elif state == 'rolled back' and rollback_error is not None:
            outcome = 'rolled back to the previous release, but its host files were not reinstalled: ' + str(rollback_error)
        elif state == 'rolled back':
            outcome = 'rolled back to the previous release; integrations stay paused until resume-integrations'
        elif unit is None:
            outcome = 'restarting the app failed: ' + str(rollback_error) + '; the database is unchanged, start it with ctc-manager up'
        else:
            outcome = 'rollback failed: ' + str(rollback_error) + '; the app is stopped, recover with ' + str(unit)
        super().__init__('deploy failed at ' + step + ': ' + str(cause or type(cause).__name__) + '; ' + outcome)


class HostFilesFailed(Exception):
    """The previous release runs again, only its host files are missing."""


def fetch(url, limit=2 * 1024 ** 2):
    import urllib.request
    request = urllib.request.Request(url, headers={'User-Agent': 'ctc-manager-deployment',
                                                   'Accept': 'application/vnd.github+json'})
    with urllib.request.urlopen(request, timeout=60) as response:
        value = response.read(limit + 1)
    if len(value) > limit:
        raise ValueError('release download exceeds limit')
    return value


def resolve(version, fetch):
    if version != 'latest':
        parse(version)
        return version
    try:
        releases = json.loads(fetch('https://api.github.com/repos/' + REPOSITORY + '/releases?per_page=30'))
        candidates = [item['tag_name'][1:] for item in releases if item.get('draft') is False
                      and item.get('prerelease') is False and re.fullmatch('v' + SEMVER, item.get('tag_name', ''))]
        return max(candidates, key=parse)
    except Exception as error:
        raise ValueError('cannot resolve latest (' + str(error) + '); run ctc-manager deploy VERSION') from error


def obtain(root, version, fetch):
    import tarfile
    import tempfile
    from pathlib import PurePosixPath
    releases = private_path(root / 'releases', directory=True)
    target = releases / version
    if target.exists() or target.is_symlink():
        private_path(target, directory=True)
        installed_release(target)
        return target
    name = 'ctc-manager-deployment-' + version + '.tar.gz'
    base = 'https://github.com/' + REPOSITORY + '/releases/download/v' + version + '/'
    staging = Path(tempfile.mkdtemp(prefix='.candidate-', dir=private_path(root / 'state', directory=True)))
    try:
        (staging / name).write_bytes(fetch(base + name, MAX_ARCHIVE))
        checksum = fetch(base + name + '.sha256', 1024).decode()
        match = re.fullmatch(r'([a-f0-9]{64})  ' + re.escape(name) + r'\n?', checksum)
        if not match or sha256(staging / name) != match.group(1):
            raise ValueError('release archive checksum mismatch')
        manifest = verify(staging / name)
        if manifest['version'] != version:
            raise ValueError('release manifest version differs from requested version')
        extracted = staging / 'release'
        extracted.mkdir(mode=0o700)
        with tarfile.open(staging / name, 'r:gz') as archive:
            for member in archive:
                relative = PurePosixPath(*PurePosixPath(member.name).parts[1:])
                path = extracted / relative
                if not path.resolve().is_relative_to(extracted):
                    raise ValueError('unsafe release extraction path')
                path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
                with path.open('xb') as output:
                    output.write(archive.extractfile(member).read())
                    os.fsync(output.fileno())
                path.chmod(0o700 if str(relative) in EXECUTABLES else 0o600)
        if installed_release(extracted) != manifest:
            raise ValueError('extracted release identity differs')
        for directory in sorted([p for p in extracted.rglob('*') if p.is_dir()], key=lambda p: len(p.parts), reverse=True):
            flush_directory(directory)
        os.rename(extracted, target)
        flush_directory(releases)
        return target
    finally:
        shutil.rmtree(staging, ignore_errors=True)


def compatibility(runtime, release, manifest, allow_database_upgrade):
    contract = read_json(release / 'upgrade.json')
    if contract.get('format') != 1 or contract.get('notes') != 'guides/updates.md':
        raise ValueError('unsupported upgrade contract')
    if parse(runtime.release['version']) < parse(contract['minimumSourceVersion']):
        raise ValueError('source release is older than the supported upgrade minimum')
    for key in ['appImage', 'databaseImage']:
        runtime.docker(['pull', manifest[key]], timeout=900)
    admit_recovery_image(json.loads(runtime.docker(['image', 'inspect', manifest['appImage']]))[0])
    database = json.loads(runtime.docker(['image', 'inspect', manifest['databaseImage']]))[0]
    target = (database.get('Config', {}).get('Labels') or {}).get('org.opencontainers.image.version', '')
    source = runtime.database_metadata()['databaseVersion']
    if not re.match(r'^[0-9]+\.', target) or not re.match(r'^[0-9]+\.', source):
        raise ValueError('database versions cannot be determined')
    if target.split('.')[0] != source.split('.')[0] and not allow_database_upgrade:
        raise ValueError('the database major version changes; MariaDB cannot downgrade, rerun with --allow-database-upgrade')
    owners = []
    for image in [runtime.config['appImage'], manifest['appImage']]:
        owner = runtime.docker(['run', '--rm', '--network', 'none', '--read-only', '--cap-drop', 'ALL',
                                '--security-opt', 'no-new-privileges', '--entrypoint', 'id', image, '-u']).decode().strip()
        if not re.fullmatch(r'[1-9][0-9]*', owner):
            raise ValueError('application image must have a non-root numeric UID')
        owners.append(owner)
    if owners[0] != owners[1]:
        raise ValueError('application UID changes need an explicit storage ownership migration')
    return manifest['databaseImage'] != runtime.config['databaseImage']


def history(runtime):
    return runtime.query(HISTORY)


def deploy(root, version, allow_database_upgrade=False, drill=False, runtime_for=NativeRuntime,
           fetch=fetch, run=systemctl, home=None):
    """Update the installation in place; any failure after the update unit rolls back by itself."""
    root = Path(root)
    previous = (root / 'current').resolve()
    old = runtime_for(root, previous)
    outbound = old.config.get('outboundPaused', False)
    with phpmyadmin_stopped(run):
        step = 'download'
        try:
            running = old.release['version']
            old.check_destination(UNIT_ROOT)
            old.preflight()
            version = resolve(version, fetch)
            if drill and version != running:
                raise ValueError('the rollback drill redeploys the running ' + running + ' only')
            if not drill and parse(version) <= parse(running):
                raise ValueError(version + ' is not newer than the running ' + running)
            release = previous if drill else obtain(root, version, fetch)
            manifest = installed_release(release)
            step = 'pull'
            database_changed = compatibility(old, release, manifest, allow_database_upgrade)
        except Exception as error:
            raise DeployFailed(step, error, 'unchanged') from error
        step = 'backup'
        try:
            before = history(old)
            unit = Path(capture(old, UNIT_ROOT, hold_stopped=True))
            unit = unit.rename(unit.with_name('recovery-update-' + unit.name.removeprefix('recovery-')))
        except BaseException as error:
            try:
                old.start_app()
            except Exception as failure:
                raise DeployFailed(step, error, 'stopped', None, failure) from error
            raise DeployFailed(step, error, 'rolled back') from error
        started, installed = False, None
        current = None
        try:
            step = 'start'
            switch_release(root, release, outbound_paused=True)
            current = runtime_for(root, release)
            started = True
            current.compose(['up', '-d', '--wait', '--wait-timeout', '180'])
            current.preflight()
            if drill:
                raise RuntimeError('rollback drill')
            step = 'host files'
            installed = snapshot_host_files(release, home)
            install_host_files(release, home)
            run(['systemctl', '--user', 'daemon-reload'], check=True)
            if not outbound:
                step = 'resume integrations'
                switch_release(root, release, outbound_paused=False)
                runtime_for(root, release).compose(['up', '-d', '--wait', '--wait-timeout', '180', 'app'])
        except BaseException as error:
            try:
                restore_data = database_changed or (started and migrated(current, before))
                rollback(root, previous, unit, restore_data, installed, runtime_for, run, home)
            except HostFilesFailed as failure:
                raise DeployFailed(step, error, 'rolled back', unit, failure.__cause__) from error
            except BaseException as failure:
                command = (str(release / 'ctc-manager') + ' --directory ' + str(root) + ' restore --unit ' + str(unit)
                           + ('' if (previous / 'lib/rollout.py').is_file() else ' --legacy-release'))
                raise DeployFailed(step, error, 'stopped', command, failure) from error
            raise DeployFailed(step, error, 'rolled back') from error
        result = {'deployed': version, 'unit': str(unit)}
        try:
            remove_legacy(current, root)
        except Exception as error:
            result['warning'] = 'legacy cleanup incomplete: ' + str(error)
    return result


def migrated(current, before):
    try:
        if getattr(current, 'db', None) is None:
            current.db = current.container('db')
        return history(current) != before
    except Exception:
        return True


def rollback(root, release, unit, restore_data, installed, runtime_for, run, home):
    if restore_data:
        restore(root, unit, runtime_for, allow_legacy=True)
    else:
        runtime_for(root, (root / 'current').resolve()).compose(['stop', 'app'])
        switch_release(root, release, outbound_paused=True)
        runtime_for(root, release).compose(['up', '-d', '--wait', '--wait-timeout', '180'])
    if installed is not None:
        try:
            restore_host_files(installed)
            run(['systemctl', '--user', 'daemon-reload'], check=True)
        except Exception as error:
            raise HostFilesFailed() from error


LEGACY_STATE = ['operation.json', 'update.json', 'protected-recovery.json', 'live-recovery.json',
                'production-authority.json', 'phpmyadmin-backup-resume']
LEGACY_STATE_PATTERNS = ['update-before-*.json', 'before-*.json']
DROP_INS = ['ctc-manager-backup.service.d/phpmyadmin.conf', 'ctc-manager-backup.service.d/ordering.conf']
VOLUME = re.compile(r'ctc-manager_(?:[a-z0-9][a-z0-9-]{0,47}_)?(?:database|data|logs|credentials)')


def host_files(release, home=None):
    """Map each host file target of a release to its source; drop-ins to remove map to None."""
    release, home = Path(release), Path(home or Path.home())
    units = home / '.config/systemd/user'
    sources = {units / path.name: path for path in sorted((release / 'systemd').glob('ctc-manager-*'))}
    sources.update({units / path.name: path for path in sorted((release / 'phpmyadmin').glob('*.service'))})
    if (release / 'ctc-manager-launcher').is_file():
        sources[home / '.local/bin/ctc-manager'] = release / 'ctc-manager-launcher'
    sources.update({units / name: None for name in DROP_INS})
    for target in sources:
        if target.is_symlink():
            raise ValueError('refusing to replace symlink ' + str(target))
    return sources


def snapshot_host_files(release, home=None):
    return {target: (target.read_bytes(), target.stat().st_mode & 0o777) if target.is_file() else None
            for target in host_files(release, home)}


def install_host_files(release, home=None):
    """Install the units and the launcher of a release and remove the old backup drop-ins."""
    for target, source in host_files(release, home).items():
        if source is None:
            target.unlink(missing_ok=True)
        else:
            write_private(target, source.read_bytes(), 0o700 if target.parent.name == 'bin' else 0o600)
    dropins = Path(home or Path.home()) / '.config/systemd/user/ctc-manager-backup.service.d'
    if dropins.is_dir() and not any(dropins.iterdir()):
        dropins.rmdir()


def restore_host_files(snapshot):
    for target, previous in snapshot.items():
        if previous is None:
            target.unlink(missing_ok=True)
        else:
            write_private(target, *previous)


def write_private(target, content, mode):
    if target.is_symlink():
        raise ValueError('refusing to replace symlink ' + str(target))
    target.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    temporary = target.with_name('.' + target.name + '.' + str(os.getpid()) + '.tmp')
    temporary.write_bytes(content)
    temporary.chmod(mode)
    temporary.replace(target)


def remove_legacy(current, root):
    """Delete the volumes, secrets and state files of former storage generations."""
    root = Path(root)
    generation = current.config.get('storageGeneration')
    prefix = 'ctc-manager_' + (generation + '_' if generation else '')
    keep = {prefix + kind for kind in ['database', 'data', 'logs', 'credentials']}
    volumes = [name for name in current.docker(['volume', 'ls', '--format', '{{.Name}}']).decode().split()
               if VOLUME.fullmatch(name) and name not in keep]
    for volume in volumes:
        users = current.docker(['ps', '--all', '--quiet', '--filter', 'volume=' + volume]).decode().split()
        if users:
            current.docker(['rm', '--force', *users])
    if volumes:
        current.docker(['volume', 'rm', *volumes])
    referenced = {Path(current.config[key]).resolve().parent for key in
                  ['appEnvFile', 'databasePasswordFile', 'databaseRootPasswordFile', 'googleCredentialsFile']}
    secrets = root / 'secrets'
    for path in secrets.iterdir():
        if path.is_dir() and not path.is_symlink() and path.resolve() not in referenced:
            shutil.rmtree(path)
    state = root / 'state'
    for path in [*(state / name for name in LEGACY_STATE),
                 *(match for pattern in LEGACY_STATE_PATTERNS for match in state.glob(pattern))]:
        path.unlink(missing_ok=True)
    if (root / 'phpmyadmin').is_dir() and not (root / 'phpmyadmin').is_symlink():
        shutil.rmtree(root / 'phpmyadmin')


def backup(root):
    import maintenance
    from recovery import NativeRuntime
    runtime = NativeRuntime(root, (root / 'current').resolve())
    runtime.check_destination(maintenance.UNIT_ROOT.parent)
    maintenance.UNIT_ROOT.mkdir(mode=0o700, exist_ok=True)
    with phpmyadmin_stopped():
        result = maintenance.run_backup(runtime, maintenance.UNIT_ROOT)
    return {'status': result['lastAttempt']['stage'], 'unit': result['local']['unit']}


def diagnosed(root, deploy_root):
    runtime = NativeRuntime(root, deploy_root)
    log = private_path(Path(root) / 'state', directory=True) / 'deploy.log'
    log.touch(mode=0o600)
    runtime.diagnostic = log
    return runtime


def main(argv=None):
    import argparse
    parser = argparse.ArgumentParser(allow_abbrev=False)
    parser.add_argument('--directory', required=True)
    parser.add_argument('--deploy-root', required=True)
    parser.add_argument('operation', choices=['deploy', 'backup', 'restore'])
    arguments, remaining = parser.parse_known_args(argv)
    os.umask(0o077)
    root = private_path(Path(arguments.directory), directory=True)
    lock = private_path(root / '.ctc-manager.lock', directory=True)
    if private_path(lock / 'owner').read_text().strip() != str(os.getppid()):
        raise ValueError(arguments.operation + ' requires its launcher-owned installation lock')
    if arguments.operation == 'deploy':
        import signal
        options = argparse.ArgumentParser(prog='ctc-manager deploy', allow_abbrev=False)
        options.add_argument('version', nargs='?', default='latest')
        options.add_argument('--allow-database-upgrade', action='store_true')
        options.add_argument('--drill-rollback', action='store_true')
        chosen = options.parse_args(remaining)
        handlers = signal.signal(signal.SIGHUP, signal.SIG_IGN), signal.signal(signal.SIGTERM, signal.default_int_handler)
        try:
            print(json.dumps(deploy(root, chosen.version, chosen.allow_database_upgrade, chosen.drill_rollback,
                                    runtime_for=diagnosed)))
        finally:
            signal.signal(signal.SIGHUP, handlers[0])
            signal.signal(signal.SIGTERM, handlers[1])
    elif arguments.operation == 'backup':
        print(json.dumps(backup(root)))
    elif arguments.operation == 'restore':
        options = argparse.ArgumentParser(prog='ctc-manager restore', allow_abbrev=False)
        options.add_argument('--unit', required=True)
        options.add_argument('--legacy-release', action='store_true')
        chosen = options.parse_args(remaining)
        with phpmyadmin_stopped():
            print(json.dumps(restore(root, chosen.unit, allow_legacy=chosen.legacy_release)))
    else:
        raise ValueError(arguments.operation + ' is not available yet')


if __name__ == '__main__':
    import rollout
    try:
        rollout.main()
    except (ValueError, RuntimeError, OSError, KeyError, TypeError, json.JSONDecodeError, subprocess.CalledProcessError) as error:
        sys.exit('CTC: ' + str(error))
