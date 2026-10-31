#!/usr/bin/env python3
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import tarfile
import uuid

from config import private_path

ARTIFACTS = {'database.sql', 'data.tar', 'installation.json', 'app.env', 'db-password',
             'db-root-password', 'google.json', 'release.json'}
MANIFEST_FIELDS = {'format', 'id', 'installationId', 'createdAt', 'releaseVersion', 'appImage',
                   'databaseImage', 'databaseVersion', 'schemaHistory', 'records', 'files'}
MAX_DATA = 100 * 1024 ** 3
MAX_ENTRIES = 1000000


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('duplicate JSON key')
        result[key] = value
    return result


def read_json(path):
    if path.stat().st_size > 16 * 1024 ** 2:
        raise ValueError('JSON exceeds size limit')
    return json.loads(path.read_text(), object_pairs_hook=unique_object)


def sha256(path):
    digest = hashlib.sha256()
    with path.open('rb') as source:
        while block := source.read(1024 * 1024):
            digest.update(block)
    return digest.hexdigest()


def validate_manifest(value):
    def require(condition):
        if not condition:
            raise ValueError('invalid recovery manifest')

    require(type(value) is dict and set(value) == MANIFEST_FIELDS)
    require(type(value['format']) is int and value['format'] == 1)
    for field in ['id', 'installationId']:
        try:
            require(str(uuid.UUID(value[field])) == value[field])
        except (ValueError, TypeError, AttributeError):
            raise ValueError('invalid recovery manifest identity') from None
    patterns = {
        'createdAt': r'\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z',
        'releaseVersion': r'\d+\.\d+\.\d+(?:-rc\.\d+)?',
        'appImage': r'ghcr\.io/jegr78/ctc-manager@sha256:[a-f0-9]{64}',
        'databaseImage': r'mariadb@sha256:[a-f0-9]{64}',
        'databaseVersion': r'[0-9][a-zA-Z0-9.+~_-]{0,127}',
    }
    for field, pattern in patterns.items():
        require(isinstance(value[field], str) and re.fullmatch(pattern, value[field]))
    history = value['schemaHistory']
    require(type(history) is list and 0 < len(history) < 10000)
    previous = 0
    for row in history:
        require(type(row) is dict and set(row) == {'rank', 'version', 'checksum'})
        require(type(row['rank']) is int and row['rank'] > previous)
        require(row['version'] is None or isinstance(row['version'], str) and re.fullmatch(r'[0-9][0-9._]{0,63}', row['version']))
        require(row['checksum'] is None or type(row['checksum']) is int)
        previous = row['rank']
    require(type(value['records']) is dict and 0 < len(value['records']) < 10000)
    for name, count in value['records'].items():
        require(re.fullmatch(r'[a-zA-Z_][a-zA-Z0-9_]{0,63}', name) and type(count) is int and count >= 0)
    require(type(value['files']) is dict and set(value['files']) == ARTIFACTS)
    for name, item in value['files'].items():
        require(type(item) is dict and set(item) == {'bytes', 'sha256'})
        require(type(item['bytes']) is int and 0 <= item['bytes'] <= MAX_DATA)
        require(isinstance(item['sha256'], str) and re.fullmatch(r'[a-f0-9]{64}', item['sha256']))


def validate_data_archive(path):
    seen = set()
    total = 0
    try:
        with tarfile.open(path, 'r:') as archive:
            for member in archive:
                name = member.name
                if name in ['.', './'] and member.isdir():
                    continue
                if name.startswith('./'):
                    name = name[2:]
                parts = name.split('/')
                if (not name or name.startswith('/') or any(p in ['', '.', '..'] for p in parts)
                        or str(PurePosixPath(name)) != name or name in seen
                        or not (member.isfile() or member.isdir()) or member.sparse
                        or member.mode & 0o7000 or member.size < 0):
                    raise ValueError('unsafe data archive entry')
                seen.add(name)
                total += member.size
                if len(seen) > MAX_ENTRIES or total > MAX_DATA:
                    raise ValueError('data archive exceeds limits')
            if archive.offset > path.stat().st_size:
                raise ValueError('truncated data archive')
    except (tarfile.TarError, EOFError) as error:
        raise ValueError('invalid data archive') from error


def validate_unit(path):
    root = private_path(Path(path), directory=True)
    if {p.name for p in root.iterdir()} != ARTIFACTS | {'manifest.json'}:
        raise ValueError('recovery unit inventory mismatch')
    for name in ARTIFACTS | {'manifest.json'}:
        private_path(root / name)
    manifest = read_json(root / 'manifest.json')
    validate_manifest(manifest)
    for name, expected in manifest['files'].items():
        artifact = root / name
        if artifact.stat().st_size != expected['bytes']:
            raise ValueError('recovery artifact size mismatch: ' + name)
        if sha256(artifact) != expected['sha256']:
            raise ValueError('recovery artifact checksum mismatch: ' + name)
    validate_data_archive(root / 'data.tar')
    return manifest


def installed_release(path):
    from release import SUPPORTED_INVENTORIES, canonical_database_image, digest, version
    root = Path(path).resolve()
    manifest = read_json(root / 'manifest.json')
    if set(manifest) != {'format', 'version', 'appImage', 'databaseImage', 'files'} or manifest['format'] != 1:
        raise ValueError('invalid installed release manifest')
    version(manifest['version'])
    for name, checksum in manifest['files'].items():
        relative = PurePosixPath(name)
        if relative.is_absolute() or '..' in relative.parts or str(relative) != name:
            raise ValueError('invalid installed release path')
        artifact = root / name
        for parent in [artifact, *artifact.parents]:
            if parent == root:
                break
            if parent.is_symlink():
                raise ValueError('installed release symlink')
        if not artifact.is_file() or sha256(artifact) != checksum:
            raise ValueError('installed release checksum mismatch: ' + name)
    if set(manifest['files']) not in SUPPORTED_INVENTORIES:
        raise ValueError('unsupported installed release inventory')
    for field, repository in [('appImage', 'ghcr.io/jegr78/ctc-manager'), ('databaseImage', 'mariadb')]:
        value = manifest[field]
        if not value.startswith(repository + '@'):
            raise ValueError('invalid installed release image')
        digest(value.removeprefix(repository + '@'))
    if canonical_database_image((root / 'database-image').read_text().strip()) != manifest['databaseImage']:
        raise ValueError('installed database release mismatch')
    return manifest


def flush_directory(path):
    import os
    descriptor = os.open(path, os.O_RDONLY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def capture(runtime, destination, hold_stopped=False):
    import datetime
    import os
    import tempfile
    from config import atomic_json
    destination = private_path(Path(destination), directory=True)
    runtime.check_destination(destination)
    runtime.preflight()
    was_running = runtime.is_app_running()
    staging = Path(tempfile.mkdtemp(prefix='.partial-', dir=destination))
    try:
        if was_running:
            runtime.stop_app()
        metadata = runtime.export(staging)
        manifest = {**metadata, 'format': 1, 'id': str(uuid.uuid4()),
                    'createdAt': datetime.datetime.now(datetime.timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ'),
                    'files': {name: {'bytes': (staging / name).stat().st_size,
                                     'sha256': sha256(staging / name)} for name in ARTIFACTS}}
        atomic_json(staging / 'manifest.json', manifest)
        validate_unit(staging)
        for name in ARTIFACTS | {'manifest.json'}:
            with (staging / name).open('rb') as artifact:
                os.fsync(artifact.fileno())
        flush_directory(staging)
        completed = destination / ('recovery-' + manifest['createdAt'].replace(':', '') + '-' + manifest['id'])
        os.rename(staging, completed)
        flush_directory(destination)
        return completed
    finally:
        if was_running and not hold_stopped and not runtime.is_app_running():
            runtime.start_app()


class NativeRuntime:
    def __init__(self, installation, deploy_root):
        from config import load
        self.root, self.config = load(installation)
        self.deploy_root = Path(deploy_root).resolve()
        self.release = installed_release(self.deploy_root)
        if any(self.release[key] != self.config[key] for key in ['appImage', 'databaseImage']):
            raise ValueError('selected release does not match installation images')
        self.diagnostic = None

    def check_destination(self, destination):
        import shutil
        import subprocess
        receipt = read_json(private_path(self.root / 'state/host.json'))
        expected = str(uuid.UUID(receipt['backupUuid']))
        if not destination.is_relative_to('/mnt/backup/ctc-manager'):
            raise ValueError('destination must be inside the CTC backup mount')
        result = subprocess.run(['findmnt', '--json', '--mountpoint', '/mnt/backup',
                                 '--output', 'TARGET,UUID'], capture_output=True)
        if result.returncode:
            raise ValueError('backup mount is missing')
        mounts = json.loads(result.stdout)['filesystems']
        if len(mounts) != 1 or mounts[0]['target'] != '/mnt/backup' or mounts[0]['uuid'] != expected:
            raise ValueError('backup device does not match host setup')
        if shutil.disk_usage(destination).free < 3 * 1024 ** 3:
            raise ValueError('insufficient backup space')

    def docker(self, arguments, **kwargs):
        import subprocess
        options = {'stdout': subprocess.PIPE, 'stderr': subprocess.PIPE, 'timeout': 180}
        options.update(kwargs)
        result = subprocess.run(['docker', '--host', self.config['dockerHost'], *arguments], **options)
        if result.returncode:
            if self.diagnostic:
                with self.diagnostic.open('ab') as output:
                    output.write(result.stderr or b'')
            raise ValueError('Docker recovery operation failed; inspect private diagnostics')
        return result.stdout

    def compose(self, arguments):
        import subprocess
        from runtime import compose_spec
        command, environment = compose_spec(self.root, self.config, self.deploy_root)
        result = subprocess.run(command + arguments, env=environment, cwd=self.root,
                                capture_output=True, timeout=240)
        if result.returncode:
            if self.diagnostic:
                with self.diagnostic.open('ab') as output:
                    output.write(result.stderr)
            raise ValueError('Compose recovery operation failed; inspect private diagnostics')
        return result.stdout

    def container(self, service):
        identifiers = self.compose(['ps', '--all', '--quiet', service]).decode().split()
        if len(identifiers) != 1:
            raise ValueError('exactly one selected ' + service + ' container required')
        return json.loads(self.docker(['inspect', identifiers[0]]))[0]

    def preflight(self):
        info = json.loads(self.docker(['info', '--format', '{{json .}}']))
        if 'name=rootless' not in info.get('SecurityOptions', []) or info.get('MemoryLimit') is not True:
            raise ValueError('rootless Docker with memory enforcement required')
        self.db = self.container('db')
        self.app = self.container('app')
        if self.db['State']['Running'] is not True:
            raise ValueError('selected database must already be running')
        for container, key in [(self.db, 'databaseImage'), (self.app, 'appImage')]:
            selected = self.config[key]
            image = json.loads(self.docker(['image', 'inspect', selected]))[0]
            if container['Config']['Image'] != selected or container['Image'] != image['Id']:
                raise ValueError('running container image differs from selected immutable release')
        self.assert_no_other_writers()

    def assert_no_other_writers(self):
        from runtime import compose_spec
        _, environment = compose_spec(self.root, self.config, self.deploy_root)
        expected = {'/var/lib/mysql': environment['CTC_DATABASE_VOLUME'],
                    '/app/data': environment['CTC_DATA_VOLUME']}
        for container, target in [(self.db, '/var/lib/mysql'), (self.app, '/app/data')]:
            selected = [m for m in container['Mounts'] if m['Destination'] == target]
            if len(selected) != 1 or selected[0].get('Name') != expected[target]:
                raise ValueError('container storage differs from selected generation')
        allowed = {self.db['Id'], self.app['Id']}
        identifiers = self.docker(['ps', '--quiet']).decode().split()
        if identifiers:
            for container in json.loads(self.docker(['inspect', *identifiers])):
                if container['Id'] not in allowed and any(m.get('Name') in expected.values() for m in container['Mounts']):
                    raise ValueError('unexpected writer attached to recovery source storage')
        for name, network in self.db['NetworkSettings']['Networks'].items():
            inspected = json.loads(self.docker(['network', 'inspect', network['NetworkID']]))[0]
            if inspected.get('Internal') is not True or set(inspected.get('Containers', {})) - allowed:
                raise ValueError('unexpected writer on private database network')

    def is_app_running(self):
        return self.container('app')['State']['Running'] is True

    def stop_app(self):
        self.compose(['stop', 'app'])
        state = self.container('app')['State']
        if state['Running'] or state.get('OOMKilled') or state.get('ExitCode') in [137, 139]:
            raise ValueError('application did not stop cleanly before capture')

    def start_app(self):
        self.compose(['up', '-d', '--wait', '--wait-timeout', '180', 'app'])

    def query(self, sql):
        command = ['exec', '-i', self.db['Id'], '/bin/sh', '-ec',
                   'MYSQL_PWD="$(cat /run/secrets/db-password)" exec mariadb --user=ctc --database=ctcdb --batch --raw --skip-column-names']
        return self.docker(command, input=sql.encode()).decode().splitlines()

    def database_metadata(self):
        tables = self.query("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME;")
        if not tables or any(not re.fullmatch(r'[a-zA-Z_][a-zA-Z0-9_]{0,63}', name) for name in tables):
            raise ValueError('unsupported source database table inventory')
        records = {name: int(self.query('SELECT COUNT(*) FROM `' + name + '`;')[0]) for name in tables}
        if int(self.query('SELECT COUNT(*) FROM flyway_schema_history WHERE success=0;')[0]):
            raise ValueError('source has failed schema migrations')
        history = [json.loads(row) for row in self.query("SELECT JSON_OBJECT('rank',installed_rank,'version',version,'checksum',checksum) FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank;")]
        return {'databaseVersion': self.query('SELECT VERSION();')[0], 'schemaHistory': history, 'records': records}

    def export(self, staging):
        import shutil
        from config import atomic_json
        from runtime import compose_spec
        self.assert_no_other_writers()
        if self.is_app_running():
            raise ValueError('application still running during capture')
        metadata = self.database_metadata()
        dump = ['exec', self.db['Id'], '/bin/sh', '-ec',
                'MYSQL_PWD="$(cat /run/secrets/db-password)" exec mariadb-dump --user=ctc --single-transaction --routines --events --triggers --hex-blob --databases ctcdb']
        with (staging / 'database.sql').open('xb') as output:
            self.docker(dump, stdout=output, timeout=1800)
        _, environment = compose_spec(self.root, self.config, self.deploy_root)
        with (staging / 'data.tar').open('xb') as output:
            self.docker(['run', '--rm', '--network', 'none', '--read-only', '--cap-drop', 'ALL',
                         '--security-opt', 'no-new-privileges', '--mount',
                         'type=volume,src=' + environment['CTC_DATA_VOLUME'] + ',dst=/app/data,readonly',
                         '--entrypoint', 'tar', self.config['appImage'], '-C', '/app/data', '-cf', '-', '.'],
                        stdout=output, timeout=1800)
        if 'installationId' not in self.config:
            self.config['installationId'] = str(uuid.uuid4())
            atomic_json(self.root / 'config/installation.json', self.config)
        for name, field in [('app.env', 'appEnvFile'), ('db-password', 'databasePasswordFile'),
                            ('db-root-password', 'databaseRootPasswordFile'), ('google.json', 'googleCredentialsFile')]:
            shutil.copyfile(private_path(self.config[field]), staging / name)
        shutil.copyfile(self.root / 'config/installation.json', staging / 'installation.json')
        shutil.copyfile(self.deploy_root / 'manifest.json', staging / 'release.json')
        for artifact in staging.iterdir():
            artifact.chmod(0o600)
        return {**metadata, 'installationId': self.config['installationId'], 'releaseVersion': self.release['version'],
                'appImage': self.config['appImage'], 'databaseImage': self.config['databaseImage']}


def admit_recovery_image(image):
    if (image.get('Config', {}).get('Labels') or {}).get('org.ctc.recovery.version') != '1':
        raise ValueError('image has no supported recovery capability; automatic full restore-check refused')


def probe_environment(path, database_password):
    allowed = {'SPRING_SECURITY_USER_NAME', 'SPRING_SECURITY_USER_PASSWORD',
               'DISCORD_BOT_TOKEN', 'GOOGLE_CALENDAR_ID'}
    values = {}
    for line in Path(path).read_text().splitlines():
        if not line.strip() or line.lstrip().startswith('#'):
            continue
        key, separator, value = line.partition('=')
        key = key.strip()
        if key not in allowed:
            continue
        if not separator or key in values:
            raise ValueError('invalid or duplicate recovery environment key')
        value = value.strip()
        if value.startswith(('"', "'")):
            if len(value) < 2 or value[-1] != value[0] or '\\' in value:
                raise ValueError('unsupported quoted recovery environment value')
            value = value[1:-1]
        if '${' in value or '\x00' in value:
            raise ValueError('unsupported interpolated recovery environment value')
        values[key] = value
    if not values.get('SPRING_SECURITY_USER_NAME') or not values.get('SPRING_SECURITY_USER_PASSWORD'):
        raise ValueError('explicit Basic authentication credentials required for recovery')
    values.update(SPRING_PROFILES_ACTIVE='recovery-check', CTC_OUTBOUND_ENABLED='false',
                  DATABASE_URL='jdbc:mariadb://db:3306/ctcdb?rewriteBatchedStatements=true',
                  DATABASE_USERNAME='ctc', DATABASE_PASSWORD=database_password,
                  GOOGLE_SHEETS_CREDENTIALS_PATH='/run/ctc/google-credentials.json',
                  JAVA_TOOL_OPTIONS='-XX:MaxRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError -Duser.timezone=Europe/Berlin',
                  TZ='Europe/Berlin')
    return values
