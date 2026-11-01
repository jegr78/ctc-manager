#!/usr/bin/env python3
"""Qualify unreleased recovery code against immutable local images in isolated rootless Docker."""
import argparse
import datetime
import io
import json
import os
from pathlib import Path
import sys
import tarfile
import uuid

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
from config import atomic_json
from recovery import NativeRuntime, Probe, admit_recovery_image, probe_environment


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--directory', required=True)
    parser.add_argument('--docker-host', required=True)
    parser.add_argument('--app-image-id', required=True)
    parser.add_argument('--database-image', required=True)
    args = parser.parse_args()
    os.umask(0o077)
    root = Path(args.directory).resolve()
    root.mkdir(mode=0o700)
    runtime = NativeRuntime.__new__(NativeRuntime)
    runtime.root = root
    runtime.config = {'dockerHost': args.docker_host}
    runtime.diagnostic = None
    info = json.loads(runtime.docker(['info', '--format', '{{json .}}']))
    assert 'name=rootless' in info['SecurityOptions'], 'qualification requires rootless Docker'
    app = json.loads(runtime.docker(['image', 'inspect', args.app_image_id]))[0]
    assert app['Id'] == args.app_image_id, 'use an immutable local candidate image ID'
    admit_recovery_image(app)
    database = json.loads(runtime.docker(['image', 'inspect', args.database_image]))[0]['Id']
    unit = root / 'fixture'
    unit.mkdir(mode=0o700)
    for name, value in {'db-password': uuid.uuid4().hex, 'db-root-password': uuid.uuid4().hex,
                        'google.json': '{"type":"service_account","project_id":"recovery-fixture"}',
                        'app.env': 'SPRING_SECURITY_USER_NAME=fixture-owner\nSPRING_SECURITY_USER_PASSWORD=' + uuid.uuid4().hex + '\nDISCORD_BOT_TOKEN=fixture-unreleased-token\nGOOGLE_CALENDAR_ID=recovery@example.test\n'}.items():
        (unit / name).write_text(value)
    with tarfile.open(unit / 'data.tar', 'w') as archive:
        entry = tarfile.TarInfo('.')
        entry.type, entry.mode = tarfile.DIRTYPE, 0o700
        archive.addfile(entry)
        for name in ['uploads', 'site']:
            entry = tarfile.TarInfo(name)
            entry.type = tarfile.DIRTYPE
            entry.mode = 0o700
            archive.addfile(entry)
        payload = b'qualification persistent file\n'
        entry = tarfile.TarInfo('uploads/recovery-fixture.txt')
        entry.size = len(payload)
        entry.mode = 0o600
        archive.addfile(entry, io.BytesIO(payload))
    manifest = {'id': str(uuid.uuid4()), 'kind': 'unreleased-native-qualification',
                'candidateImageId': app['Id'], 'sourceProvenance': 'synthetic fixture, no published release claim'}
    bootstrap = Probe(runtime, unit, manifest, app['Id'], database)
    try:
        bootstrap.create_resources()
        bootstrap.record('container', bootstrap.prefix + '-db')
        bootstrap.docker(bootstrap.database_arguments())
        bootstrap.wait_database()
        marker = '/tmp/ctc-recovery-client-escape'
        (unit / 'database.sql').write_text('\\! touch ' + marker + '\n')
        try:
            bootstrap.import_database()
            raise AssertionError('sandbox did not reject client shell escape')
        except ValueError:
            bootstrap.docker(['exec', bootstrap.prefix + '-db', '/bin/sh', '-ec', 'test ! -e ' + marker])
        (unit / 'database.sql').write_text('INSERT INTO missing_recovery_fixture VALUES (1);\n')
        try:
            bootstrap.import_database()
            raise AssertionError('invalid SQL did not fail import')
        except ValueError:
            pass
        bootstrap.restore_files()
        environment = probe_environment(unit / 'app.env', (unit / 'db-password').read_text())
        bootstrap.record('container', bootstrap.prefix + '-app')
        bootstrap.docker(bootstrap.app_arguments(environment))
        bootstrap.verify_application(environment)
        original_root_password = (unit / 'db-root-password').read_bytes()
        try:
            (unit / 'db-root-password').write_bytes(b'wrong-root-password-native-fixture')
            try:
                bootstrap.verify_credentials()
                raise AssertionError('wrong restored root password unexpectedly authenticated')
            except ValueError as error:
                assert 'root credential login failed' in str(error), 'reject the wrong root password over TCP'
        finally:
            (unit / 'db-root-password').write_bytes(original_root_password)
        runtime.db = {'Id': bootstrap.prefix + '-db'}
        runtime.query((Path(__file__).parent / 'fixtures/recovery-domain.sql').read_text())
        bootstrap.docker(['stop', '--time', '60', bootstrap.prefix + '-app'])
        manifest.update(runtime.database_metadata())
        with (unit / 'database.sql').open('wb') as output:
            runtime.docker(['exec', bootstrap.prefix + '-db', '/bin/sh', '-ec',
                            'MYSQL_PWD="$(cat /run/secrets/db-password)" exec mariadb-dump --user=ctc --single-transaction --routines --events --triggers --hex-blob --databases ctcdb'], stdout=output)
        atomic_json(unit / 'manifest.json', manifest)
    finally:
        bootstrap.cleanup()
    probe = Probe(runtime, unit, manifest, app['Id'], database)
    result = probe.run()
    assert result['graphic'] == 'restored-season-team-card', 'prove graphic generation from restored domain data'
    assert result['credentialsVerified'] is True, 'prove Google bytes as app user and the restored database root login'
    remaining = runtime.docker(['ps', '--all', '--quiet', '--filter', 'label=org.ctc.recovery.run=' + probe.id]).decode().strip()
    assert not remaining, 'probe containers must be removed'
    failed = Probe(runtime, unit, manifest, app['Id'], database)
    original_docker = runtime.docker
    def fail_app_creation(arguments, **kwargs):
        if arguments[0] == 'run' and '--name' in arguments and arguments[arguments.index('--name') + 1] == failed.prefix + '-app':
            arguments = [arguments[0], '--invalid-ctc-recovery-fixture-option', *arguments[1:]]
        return original_docker(arguments, **kwargs)
    runtime.docker = fail_app_creation
    try:
        try:
            failed.run()
            raise AssertionError('invalid container creation unexpectedly succeeded')
        except ValueError:
            pass
    finally:
        runtime.docker = original_docker
    evidence = json.loads((failed.directory / 'result.json').read_text())
    assert evidence['status'] == 'failed', 'failed creation must not leave passed evidence'
    for kind in ['container', 'volume', 'network']:
        command = [kind, 'ls'] + (['--all'] if kind == 'container' else [])
        leftover = runtime.docker(command + ['--quiet', '--filter', 'label=org.ctc.recovery.run=' + failed.id]).decode().strip()
        assert not leftover, 'failed creation must clean all owned ' + kind + ' resources'
    print(json.dumps({'status': 'passed', 'candidateImageId': app['Id'], 'restoredTables': len(manifest['records']),
                      'graphic': result['graphic'], 'credentialsVerified': result['credentialsVerified'], 'sandboxEscape': 'rejected', 'invalidSql': 'rejected',
                      'failedContainerCreate': 'all-owned-resources-cleaned', 'wrongRootPassword': 'rejected', 'productionTouched': False}))


if __name__ == '__main__':
    main()
