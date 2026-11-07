#!/usr/bin/env python3
"""Identify the installed browser dependency without a second pinned version."""
import argparse
import os
from pathlib import Path
import platform
import re
import shlex
import sys
from zipfile import ZipFile, BadZipFile

ARTIFACTS = ('playwright', 'driver', 'driver-bundle')


def version(classpath):
    found = {name: [] for name in ARTIFACTS}
    for entry in classpath.split(os.pathsep):
        if not entry or not entry.endswith('.jar'):
            continue
        with ZipFile(entry) as archive:
            for artifact in ARTIFACTS:
                name = 'META-INF/maven/com.microsoft.playwright/' + artifact + '/pom.properties'
                if name not in archive.namelist():
                    continue
                metadata = dict(line.split('=', 1) for line in archive.read(name).decode().splitlines()
                                if line and not line.startswith('#') and '=' in line)
                if metadata.get('groupId') != 'com.microsoft.playwright' or metadata.get('artifactId') != artifact:
                    raise ValueError('Unexpected Playwright dependency metadata')
                value = metadata.get('version', '')
                if not re.fullmatch(r'\d+\.\d+\.\d+', value):
                    raise ValueError('Playwright dependencies must resolve to one pinned release')
                found[artifact].append(value)
    if any(len(values) != 1 for values in found.values()):
        raise ValueError('Missing or duplicate resolved Playwright dependency')
    values = {values[0] for values in found.values()}
    if len(values) != 1:
        raise ValueError('Playwright and driver versions differ')
    return values.pop()


def cache_key(version, system, arch, release):
    components = [system, arch, release, version]
    if any(not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]*', value) for value in components):
        raise ValueError('Unsupported browser cache identity')
    return 'ctc-playwright-v1-' + '-'.join(components)


def os_release(path=Path('/etc/os-release')):
    if platform.system() == 'Linux':
        values = {}
        for line in path.read_text().splitlines():
            if line.startswith(('ID=', 'VERSION_ID=', 'BUILD_ID=')):
                key, value = line.split('=', 1)
                tokens = shlex.split(value)
                if len(tokens) != 1:
                    raise ValueError('Unsupported OS identity')
                values[key] = tokens[0]
        release = values.get('VERSION_ID') or values.get('BUILD_ID')
        if not values.get('ID') or not release:
            raise ValueError('Unsupported OS identity')
        return values['ID'] + '-' + release
    if platform.system() == 'Darwin':
        return 'macos-' + platform.mac_ver()[0]
    raise ValueError('Unsupported browser cache platform')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--classpath-file', required=True, type=Path)
    args = parser.parse_args()
    value = version(args.classpath_file.read_text().strip())
    key = cache_key(value, platform.system().lower(), platform.machine().lower(), os_release())
    print('version=' + value)
    print('key=' + key)


if __name__ == '__main__':
    try:
        main()
    except (ValueError, KeyError, OSError, BadZipFile):
        print('Could not identify compatible resolved browser dependencies', file=sys.stderr)
        sys.exit(1)
