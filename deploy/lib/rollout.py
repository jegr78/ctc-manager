#!/usr/bin/env python3
"""In-place deploy and restore for the single CTC Manager installation."""
from contextlib import contextmanager
import json
import os
from pathlib import Path
import subprocess
import sys

from config import private_path

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
    else:
        raise ValueError(arguments.operation + ' is not available yet')


if __name__ == '__main__':
    import rollout
    try:
        rollout.main()
    except (ValueError, OSError, KeyError, TypeError, json.JSONDecodeError, subprocess.CalledProcessError) as error:
        sys.exit('CTC: ' + str(error))
