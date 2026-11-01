#!/usr/bin/env python3
import hashlib
import json
from pathlib import PurePosixPath
import re
import tarfile

CORE_FILES = ['ctc-manager', 'compose.yaml', 'config.example', 'lib/common.sh', 'lib/config.py',
         'README.md', 'database-image', 'release-manifest.schema.json']
HOST_FILES = CORE_FILES + ['setup-host.sh', 'lib/host.py', 'systemd/docker-rootless.service', 'guides/host.md']
RECOVERY_FILES = HOST_FILES + ['lib/runtime.py', 'lib/release.py', 'lib/recovery.py', 'lib/recovery.sh',
                      'lib/probe.py', 'lib/live.py', 'recovery-manifest.schema.json', 'guides/recovery.md']
UPDATE_FILES = RECOVERY_FILES + ['lib/update.py', 'lib/update.sh', 'upgrade.json', 'guides/updates.md']
LEGACY_MAINTENANCE_FILES = UPDATE_FILES + ['lib/maintenance.py', 'lib/maintenance.sh', 'guides/backups.md',
                        'systemd/ctc-manager-backup.service', 'systemd/ctc-manager-backup.timer',
                        'systemd/ctc-manager-backup-check.service', 'systemd/ctc-manager-backup-check.timer',
                        'systemd/ctc-manager-backup-alert@.service']
FILES = UPDATE_FILES + ['lib/maintenance.py', 'lib/maintenance.sh', 'guides/backups.md',
                        'systemd/ctc-manager-backup.service', 'systemd/ctc-manager-backup.timer']
SUPPORTED_INVENTORIES = [set(CORE_FILES), set(HOST_FILES), set(RECOVERY_FILES), set(UPDATE_FILES),
                         set(LEGACY_MAINTENANCE_FILES), set(FILES)]
MAX_MEMBER = 4 * 1024 * 1024
MAX_ARCHIVE = 32 * 1024 * 1024


def digest(value):
    if not re.fullmatch(r'sha256:[a-f0-9]{64}', value):
        raise ValueError('invalid image digest')
    return value


def canonical_database_image(value):
    match = re.fullmatch(r'mariadb(?::[0-9]+(?:\.[0-9]+){0,2})?@(sha256:[a-f0-9]{64})', value)
    if not match:
        raise ValueError('invalid database release reference')
    return 'mariadb@' + match.group(1)


def version(value):
    if not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+(?:-rc\.[0-9]+)?', value):
        raise ValueError('invalid released version')
    return value


def verify(path):
    values = {}
    total = 0
    prefix = None
    with tarfile.open(path, 'r:gz') as archive:
        for member in archive:
            name = PurePosixPath(member.name)
            if (not member.isfile() or member.size > MAX_MEMBER or name.is_absolute() or '..' in name.parts
                    or str(name) != member.name):
                raise ValueError('unsafe archive entry')
            if len(name.parts) < 2 or name.parts[0] != 'ctc-manager-deployment-' + version(name.parts[0].removeprefix('ctc-manager-deployment-')):
                raise ValueError('invalid archive prefix')
            if prefix is not None and prefix != name.parts[0]:
                raise ValueError('mixed archive releases')
            prefix = name.parts[0]
            relative = str(PurePosixPath(*name.parts[1:]))
            if relative in values or relative not in set().union(*SUPPORTED_INVENTORIES) | {'manifest.json'}:
                raise ValueError('unexpected or duplicate archive entry')
            total += member.size
            if total > MAX_ARCHIVE:
                raise ValueError('archive too large')
            values[relative] = archive.extractfile(member).read(MAX_MEMBER + 1)
    if 'manifest.json' not in values or set(values) - {'manifest.json'} not in SUPPORTED_INVENTORIES:
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
    if canonical_database_image(values['database-image'].decode().strip()) != manifest['databaseImage']:
        raise ValueError('database release identity mismatch')
    expected = {name: hashlib.sha256(value).hexdigest() for name, value in values.items()}
    if manifest.get('files') != expected:
        raise ValueError('archive checksum inventory mismatch')
    return manifest

