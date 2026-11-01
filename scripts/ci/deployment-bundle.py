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
sys.path.insert(0, str(ROOT / 'deploy/lib'))
from release import EXECUTABLES, FILES, canonical_database_image, digest, version, verify


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
    if database != canonical_database_image(content['database-image'].decode().strip()):
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
                info.mode = 0o755 if name in EXECUTABLES else 0o644
                archive.addfile(info, io.BytesIO(value))
        verify(staging)
        os.replace(staging, target)
        checksum = hashlib.sha256(target.read_bytes()).hexdigest()
        (directory / (target.name + '.sha256')).write_text(checksum + '  ' + target.name + '\n')
        print(target)
    finally:
        if os.path.exists(staging):
            os.unlink(staging)


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
