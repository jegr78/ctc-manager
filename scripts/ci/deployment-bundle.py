#!/usr/bin/env python3
import argparse
import hashlib
import gzip
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import sys
import tarfile
import tempfile

ROOT = Path(__file__).resolve().parents[2]
FILES = ['ctc-manager', 'compose.yaml', 'config.example', 'lib/common.sh', 'lib/config.py',
         'README.md', 'database-image', 'release-manifest.schema.json']
MAX_MEMBER = 4 * 1024 * 1024
MAX_ARCHIVE = 32 * 1024 * 1024


def digest(value):
    if not re.fullmatch(r'sha256:[a-f0-9]{64}', value):
        raise ValueError('invalid image digest')
    return value


def version(value):
    if not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+(?:-rc\.[0-9]+)?', value):
        raise ValueError('invalid released version')
    return value


def create(args):
    release = version(args.version)
    app = 'ghcr.io/jegr78/ctc-manager@' + digest(args.app_digest)
    database = 'mariadb@' + digest(args.database_digest)
    content = {}
    for name in FILES:
        path = ROOT / 'deploy' / name
        if not path.is_file() or path.is_symlink():
            raise ValueError('missing deployment file: ' + name)
        content[name] = path.read_bytes()
    if database != content['database-image'].decode().strip():
        raise ValueError('database digest does not match version-controlled release input')
    manifest = {'format': 1, 'version': release, 'appImage': app, 'databaseImage': database,
                'files': {name: hashlib.sha256(value).hexdigest() for name, value in content.items()}}
    content['manifest.json'] = (json.dumps(manifest, indent=2, sort_keys=True) + '\n').encode()
    directory = Path(args.output)
    directory.mkdir(parents=True, exist_ok=True)
    prefix = 'ctc-manager-deployment-' + release
    target = directory / (prefix + '.tar.gz')
    descriptor, staging = tempfile.mkstemp(prefix='.deployment-', dir=directory)
    os.close(descriptor)
    try:
        with open(staging, 'wb') as raw, gzip.GzipFile(filename='', mode='wb', fileobj=raw, mtime=0) as compressed, tarfile.open(fileobj=compressed, mode='w') as archive:
            for name, value in sorted(content.items()):
                info = tarfile.TarInfo(prefix + '/' + name)
                info.size = len(value)
                info.mode = 0o755 if name == 'ctc-manager' else 0o644
                archive.addfile(info, io.BytesIO(value))
        verify(staging)
        os.replace(staging, target)
        checksum = hashlib.sha256(target.read_bytes()).hexdigest()
        (directory / (target.name + '.sha256')).write_text(checksum + '  ' + target.name + '\n')
        print(target)
    finally:
        if os.path.exists(staging):
            os.unlink(staging)


def verify(path):
    values = {}
    total = 0
    prefix = None
    with tarfile.open(path, 'r:gz') as archive:
        for member in archive:
            name = PurePosixPath(member.name)
            if not member.isfile() or member.size > MAX_MEMBER or name.is_absolute() or '..' in name.parts:
                raise ValueError('unsafe archive entry')
            if len(name.parts) < 2 or name.parts[0] != 'ctc-manager-deployment-' + version(name.parts[0].removeprefix('ctc-manager-deployment-')):
                raise ValueError('invalid archive prefix')
            if prefix is not None and prefix != name.parts[0]:
                raise ValueError('mixed archive releases')
            prefix = name.parts[0]
            relative = str(PurePosixPath(*name.parts[1:]))
            if relative in values or relative not in FILES + ['manifest.json']:
                raise ValueError('unexpected or duplicate archive entry')
            total += member.size
            if total > MAX_ARCHIVE:
                raise ValueError('archive too large')
            values[relative] = archive.extractfile(member).read(MAX_MEMBER + 1)
    if set(values) != set(FILES + ['manifest.json']):
        raise ValueError('incomplete archive inventory')
    manifest = json.loads(values.pop('manifest.json'))
    if set(manifest) != {'format', 'version', 'appImage', 'databaseImage', 'files'}:
        raise ValueError('unexpected manifest fields')
    if manifest.get('format') != 1 or prefix != 'ctc-manager-deployment-' + version(manifest.get('version', '')):
        raise ValueError('archive manifest version mismatch')
    for key, repository in [('appImage', 'ghcr.io/jegr78/ctc-manager'), ('databaseImage', 'mariadb')]:
        value = manifest.get(key, '')
        if not value.startswith(repository + '@'):
            raise ValueError('unexpected image repository')
        digest(value.removeprefix(repository + '@'))
    if values['database-image'].decode().strip() != manifest['databaseImage']:
        raise ValueError('database release identity mismatch')
    expected = {name: hashlib.sha256(value).hexdigest() for name, value in values.items()}
    if manifest.get('files') != expected:
        raise ValueError('archive checksum inventory mismatch')
    return manifest


def main():
    parser = argparse.ArgumentParser()
    commands = parser.add_subparsers(dest='command', required=True)
    build = commands.add_parser('create')
    for flag in ['version', 'app-digest', 'database-digest', 'output']:
        build.add_argument('--' + flag, required=True)
    check = commands.add_parser('verify')
    check.add_argument('archive')
    args = parser.parse_args()
    if args.command == 'create':
        create(args)
    else:
        manifest = verify(args.archive)
        print('Verified deployment ' + manifest['version'])


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, KeyError, TypeError, tarfile.TarError, EOFError) as error:
        sys.exit('CTC deployment: ' + str(error))
