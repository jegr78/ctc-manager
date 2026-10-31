#!/usr/bin/env python3
import json
import os
from pathlib import Path
import re
import stat
import sys
import tempfile
import time


def private_path(path, directory=False):
    path = Path(path)
    if not path.is_absolute() or '..' in path.parts:
        raise ValueError('installation paths must be absolute without traversal')
    for part in [path, *path.parents]:
        if part.is_symlink():
            raise ValueError('symlink in installation path')
    metadata = path.stat()
    expected = stat.S_ISDIR if directory else stat.S_ISREG
    if not expected(metadata.st_mode) or metadata.st_uid != os.getuid():
        raise ValueError('installation path must have the current owner and expected file type')
    if metadata.st_mode & 0o077:
        raise ValueError('installation configuration and secrets must be private')
    return path


def load(installation):
    root = private_path(installation, directory=True)
    private_path(root / 'config', directory=True)
    path = private_path(root / 'config/installation.json')
    config = json.loads(path.read_text())
    if config.get('format') != 1 or config.get('project') != 'ctc-manager':
        raise ValueError('unsupported installation configuration')
    socket = config.get('dockerHost', '')
    runtime = Path(os.environ.get('XDG_RUNTIME_DIR', '/run/user/' + str(os.getuid())))
    endpoint = Path(socket.removeprefix('unix://'))
    if not socket.startswith('unix://') or not endpoint.is_relative_to(runtime) or '..' in endpoint.parts:
        raise ValueError('Docker socket must belong to the current user runtime directory')
    try:
        private_path(runtime, directory=True)
        for parent in endpoint.parents:
            if parent == runtime:
                break
            private_path(parent, directory=True)
        metadata = endpoint.lstat()
        if not stat.S_ISSOCK(metadata.st_mode) or metadata.st_uid != os.getuid():
            raise ValueError('expected a Unix socket owned by the current user')
    except (OSError, ValueError) as error:
        raise ValueError('Docker socket: ' + str(error)) from None
    for field, repository in [('appImage', 'ghcr.io/jegr78/ctc-manager'), ('databaseImage', 'mariadb')]:
        if not re.fullmatch(re.escape(repository) + r'@sha256:[a-f0-9]{64}', config.get(field, '')):
            raise ValueError(field + ' must use the expected repository and immutable digest')
    for field in ['appEnvFile', 'databasePasswordFile', 'databaseRootPasswordFile', 'googleCredentialsFile']:
        try:
            value = Path(config[field])
            if not value.is_relative_to(root):
                raise ValueError('secret outside installation')
            private_path(value)
            private_path(value.parent, directory=True)
        except (KeyError, OSError, ValueError) as error:
            raise ValueError(field + ': private file inside installation required: ' + str(error)) from None
    return root, config


def atomic_json(path, data):
    descriptor, temporary = tempfile.mkstemp(prefix='.state-', dir=path.parent)
    try:
        with os.fdopen(descriptor, 'w') as output:
            json.dump(data, output, indent=2)
            output.write('\n')
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def main():
    command, installation = sys.argv[1:3]
    root, config = load(installation)
    if command == 'validate':
        return
    if command == 'journal':
        state = root / 'state'
        state.mkdir(mode=0o700, exist_ok=True)
        private_path(state, directory=True)
        atomic_json(state / 'operation.json', {'operation': sys.argv[3], 'stage': sys.argv[4], 'updatedAt': time.time()})
    elif command == 'get':
        value = config[sys.argv[3]]
        if not isinstance(value, str) or '\n' in value or '\r' in value:
            raise ValueError('invalid configuration value')
        print(value)
    else:
        raise ValueError('unknown configuration command')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, KeyError, json.JSONDecodeError) as failure:
        sys.exit('CTC: ' + str(failure))
