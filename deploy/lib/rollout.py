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
from recovery import NativeRuntime, read_json, select_recovery_release, validate_unit

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


def restore(root, unit, runtime_for=NativeRuntime):
    """Replace database, data volume and secrets with a unit, running the release that wrote it."""
    root, unit = Path(root), Path(unit).resolve()
    validate_unit(unit)
    expected = read_json(unit / 'release.json')
    parse(expected.get('version'))
    release = select_recovery_release(root, expected)
    if not (release / 'lib/rollout.py').is_file():
        raise ValueError('release ' + expected['version'] + ' still uses storage generations; restore it with its own tools')
    current = runtime_for(root, (root / 'current').resolve())
    current.stop_app()
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


def backup(root):
    import maintenance
    from recovery import NativeRuntime
    runtime = NativeRuntime(root, (root / 'current').resolve())
    runtime.check_destination(maintenance.UNIT_ROOT.parent)
    maintenance.UNIT_ROOT.mkdir(mode=0o700, exist_ok=True)
    with phpmyadmin_stopped():
        result = maintenance.run_backup(runtime, maintenance.UNIT_ROOT)
    return {'status': result['lastAttempt']['stage'], 'unit': result['local']['unit']}


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
    if arguments.operation == 'backup':
        print(json.dumps(backup(root)))
    elif arguments.operation == 'restore':
        options = argparse.ArgumentParser(prog='ctc-manager restore', allow_abbrev=False)
        options.add_argument('--unit', required=True)
        chosen = options.parse_args(remaining)
        with phpmyadmin_stopped():
            print(json.dumps(restore(root, chosen.unit)))
    else:
        raise ValueError(arguments.operation + ' is not available yet')


if __name__ == '__main__':
    import rollout
    try:
        rollout.main()
    except (ValueError, OSError, KeyError, TypeError, json.JSONDecodeError, subprocess.CalledProcessError) as error:
        sys.exit('CTC: ' + str(error))
