#!/usr/bin/env python3
"""Check private-directory restore permissions and flush failure using isolated Docker volumes."""
import argparse
import io
import json
import os
from pathlib import Path
import sys
import tarfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
from config import atomic_json
from recovery import NativeRuntime, Probe


def main():
    parser = argparse.ArgumentParser(allow_abbrev=False)
    parser.add_argument('--directory', required=True)
    parser.add_argument('--docker-host', required=True)
    parser.add_argument('--app-image', required=True)
    args = parser.parse_args()
    os.umask(0o077)
    root = Path(args.directory).resolve()
    root.mkdir(mode=0o700)
    runtime = NativeRuntime.__new__(NativeRuntime)
    runtime.root, runtime.config = root, {'dockerHost': args.docker_host}
    runtime.diagnostic = None
    image = json.loads(runtime.docker(['image', 'inspect', args.app_image]))[0]['Id']
    unit = root / 'fixture'
    unit.mkdir(mode=0o700)
    credential = b'{"fixture":"private-directory"}'
    (unit / 'google.json').write_bytes(credential)
    # This synthetic file also supports rootful CI without granting access to real credentials.
    (unit / 'google.json').chmod(0o644)
    with tarfile.open(unit / 'data.tar', 'w') as archive:
        for name in ['.', 'uploads', 'uploads/empty']:
            member = tarfile.TarInfo(name)
            member.type, member.mode = tarfile.DIRTYPE, 0o700
            archive.addfile(member)
        payload = b'private persistent file\n'
        member = tarfile.TarInfo('uploads/fixture.txt')
        member.size, member.mode = len(payload), 0o600
        archive.addfile(member, io.BytesIO(payload))
    probe = Probe(runtime, unit, {'kind': 'synthetic-file-permission-fixture'}, image, image)
    try:
        probe.create_resources()
        probe.restore_files()
        probe.verify_files()
        actual = probe.helper('app-read', ['run', '--rm', '--network', 'none', '--read-only',
            '--cap-drop', 'ALL', '--security-opt', 'no-new-privileges',
            '--mount', 'type=volume,src=' + probe.volume('data') + ',dst=/app/data,readonly',
            '--mount', 'type=volume,src=' + probe.volume('credentials') + ',dst=/run/ctc,readonly',
            '--entrypoint', '/bin/sh', image, '-ec',
            'test "$(stat -c %u /app/data)" = "$(id -u)"; '
            'test "$(stat -c %a /app/data)" = 700; cat /run/ctc/google-credentials.json'])
        if actual != credential:
            raise ValueError('application owner cannot read the exact restored credential fixture')
    except ValueError as error:
        print(runtime.diagnostic.read_text(), file=sys.stderr)
        raise AssertionError('private storage must restore and flush with its application owner') from error
    finally:
        probe.cleanup()
    profile = root / 'deny-syncfs.json'
    atomic_json(profile, {'defaultAction': 'SCMP_ACT_ALLOW', 'syscalls': [
        {'names': ['syncfs'], 'action': 'SCMP_ACT_ERRNO', 'errnoRet': 5}]})
    failed = Probe(runtime, unit, {'kind': 'synthetic-flush-failure-fixture'}, image, image)
    native_docker = runtime.docker
    def fail_flush(arguments, **options):
        if arguments[0] == 'run' and any(value.startswith('type=volume,src=') and ',dst=/restore' in value for value in arguments):
            arguments = arguments[:1] + ['--security-opt', 'seccomp=' + str(profile)] + arguments[1:]
        return native_docker(arguments, **options)
    runtime.docker = fail_flush
    try:
        failed.create_resources()
        try:
            failed.restore_files()
        except ValueError:
            if 'Input/output error' not in runtime.diagnostic.read_text():
                raise AssertionError('observe the intended syncfs I/O failure')
        else:
            raise AssertionError('a filesystem flush failure must abort restoration')
    finally:
        failed.cleanup()
        runtime.docker = native_docker
    print(json.dumps({'status': 'passed', 'privateRootMode': '0700', 'ownerRead': True,
                      'fileInventory': 'matched', 'syncFailure': 'rejected', 'cleanup': 'completed'}))


if __name__ == '__main__':
    main()
