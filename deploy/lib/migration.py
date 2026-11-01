#!/usr/bin/env python3
import hashlib
import os
from pathlib import Path
import re
import stat


def source_contract(metadata):
    if (not re.fullmatch(r'13\.0\.[0-9]+-MariaDB', metadata.get('databaseVersion', ''))
            or metadata.get('schemaHistory') != [str(i) for i in range(1, 7)]):
        raise ValueError('unsupported native source database or schema')
    return {'format': 1, 'kind': 'ctc-native-mac', 'sourceApplicationVersion': None, **metadata}


def projection(query, columns):
    identifier = r'[a-zA-Z_][a-zA-Z0-9_]{0,63}'
    if (not columns or any(not re.fullmatch(identifier, table) or not names
                           or any(not re.fullmatch(identifier, name) for name in names)
                           for table, names in columns.items())):
        raise ValueError('unsupported source columns')
    result = {}
    for table, names in sorted(columns.items()):
        fields = ','.join('HEX(`' + name + '`)' for name in names)
        rows = query("SET time_zone='+00:00'; SELECT JSON_ARRAY(" + fields + ') FROM `' + table + '`;')
        result[table] = hashlib.sha256((''.join(row + '\n' for row in sorted(rows))).encode()).hexdigest()
    return result


def environment_values(*texts):
    values = {}
    for text in texts:
        found = set()
        for line in text.splitlines():
            if not line.strip() or line.lstrip().startswith('#'):
                continue
            key, separator, value = line.partition('=')
            if not separator or key not in ['DISCORD_BOT_TOKEN', 'GOOGLE_CALENDAR_ID'] or key in found:
                raise ValueError('unsupported source environment')
            found.add(key)
            value = value.strip()
            if value.startswith(('"', "'")):
                if len(value) < 2 or value[-1] != value[0] or '\\' in value:
                    raise ValueError('unsupported source environment quoting')
                value = value[1:-1]
            if '${' in value or '\x00' in value:
                raise ValueError('unsupported source environment interpolation')
            values[key] = value
    return values


def assert_source_idle(query):
    visibility = query("SELECT COUNT(*) FROM information_schema.USER_PRIVILEGES WHERE PRIVILEGE_TYPE='PROCESS' "
                              "AND GRANTEE=CONCAT(QUOTE(SUBSTRING_INDEX(CURRENT_USER(),'@',1)),'@',"
                              "QUOTE(SUBSTRING_INDEX(CURRENT_USER(),'@',-1)));")
    if visibility != ['1']:
        raise ValueError('global process visibility is required to verify source freeze')
    connections = query("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE ID<>CONNECTION_ID() "
                               "AND USER NOT IN ('system user','event_scheduler');")
    if connections != ['0']:
        raise ValueError('source writer or another database connection is active')
    if query("SELECT COUNT(*) FROM information_schema.EVENTS WHERE EVENT_SCHEMA=DATABASE() AND STATUS='ENABLED';") != ['0']:
        raise ValueError('source writer may be an enabled database event')


def before_write_rollback(receipt):
    if receipt.get('stage') not in ['exported', 'rehearsed', 'preparing', 'failed-before-writes']:
        raise ValueError('reverse migration of the authoritative data is required')
    return 'unchanged-source'


def source_files(root, uploads):
    root = Path(root)
    if uploads != 'data/local/uploads':
        raise ValueError('supported local upload path required')
    mapping = {'uploads': root / uploads, 'backup-staging': root / 'data/local/backup-staging',
               'import-backups': root / 'data/local/import-backups', 'site': root / 'docs/site'}
    if not mapping['uploads'].is_dir():
        raise ValueError('local upload directory is missing')
    entries = {}
    for role, path in mapping.items():
        if not path.exists() and not path.is_symlink():
            continue
        for selected in [path, *sorted(path.rglob('*'))]:
            if any(parent.is_symlink() for parent in [selected, *selected.parents]):
                raise ValueError('unsupported source file link')
            info = selected.stat()
            if info.st_uid != os.getuid() or not (stat.S_ISDIR(info.st_mode) or stat.S_ISREG(info.st_mode)):
                raise ValueError('unsupported source file owner or type')
            if stat.S_ISREG(info.st_mode) and info.st_nlink != 1:
                raise ValueError('unsupported source file hardlink')
            name = role if selected == path else role + '/' + selected.relative_to(path).as_posix()
            entries[name] = selected
    return entries


class MacSource:
    def __init__(self, root, observer_user=None):
        import subprocess
        from urllib.parse import urlsplit
        import yaml
        self.root = Path(root).resolve()
        self.profile = self.root / 'src/main/resources/application-local.yml'
        settings = yaml.safe_load(self.profile.read_text())
        database = settings['spring']['datasource']
        self.uploads = settings['app']['upload-dir']
        source_files(self.root, self.uploads)
        url = urlsplit(database['url'].removeprefix('jdbc:'))
        if (not database['url'].startswith('jdbc:mariadb://') or url.hostname not in ['localhost', '127.0.0.1']
                or url.path != '/ctcdb' or url.username or url.password or url.fragment
                or url.query not in ['', 'rewriteBatchedStatements=true']):
            raise ValueError('supported native loopback database required')
        if settings.get('server', {}).get('port') != 9091:
            raise ValueError('supported native source port required')
        if not database.get('username') or not database.get('password') or '${' in str(database):
            raise ValueError('explicit native database credentials required')
        self.environment = {**os.environ, 'MYSQL_PWD': str(database['password'])}
        self.options = ['--no-defaults', '--host=' + url.hostname, '--port=' + str(url.port or 3306),
                        '--user=' + str(database['username'])]
        self.client = ['/opt/homebrew/bin/mariadb', *self.options, '--database=ctcdb',
                       '--batch', '--raw', '--skip-column-names']
        self.observer_client = self.client
        self.observer_environment = self.environment
        if observer_user is not None:
            if not re.fullmatch(r'[a-z_][a-z0-9_-]{0,31}', observer_user):
                raise ValueError('unsupported local observer name')
            self.observer_client = ['/opt/homebrew/bin/mariadb', '--no-defaults', '--protocol=socket',
                                    '--user=' + observer_user, '--database=ctcdb', '--batch', '--raw', '--skip-column-names']
            self.observer_environment = {**os.environ, 'MYSQL_PWD': ''}
        self.material = {'application-local.yml': self.profile, '.env': self.root / '.env',
                         '.env.local': self.root / '.env.local', 'google.json': self.root / 'google-credentials.json'}
        for path in self.material.values():
            if path.is_symlink() or not path.is_file() or path.stat().st_uid != os.getuid():
                raise ValueError('missing or unsafe native configuration file')
        self.integrations = environment_values(*(self.material[name].read_text() for name in ['.env', '.env.local']))
        import json
        credential = json.loads(self.material['google.json'].read_text())
        if credential.get('type') != 'service_account' or not credential.get('client_email') or not credential.get('private_key'):
            raise ValueError('incomplete native Google credential')
        self.subprocess = subprocess

    def query(self, sql, *, observer=False):
        result = self.subprocess.run(self.observer_client if observer else self.client, input=sql, text=True, capture_output=True,
                                     env=self.observer_environment if observer else self.environment, timeout=120)
        if result.returncode:
            raise ValueError('native database read failed; no source changes made')
        return result.stdout.splitlines()

    def idle(self):
        result = self.subprocess.run(['/usr/sbin/lsof', '-nP', '-iTCP:9091', '-sTCP:LISTEN'], capture_output=True)
        if result.returncode != 1:
            raise ValueError('source writer may still listen on 9091; stop only the identified CTC process')
        fingerprint = 'SELECT @@port,@@datadir,DATABASE();'
        if self.query(fingerprint) != self.query(fingerprint, observer=True):
            raise ValueError('source observer connects to another database instance')
        assert_source_idle(lambda sql: self.query(sql, observer=True))

    def metadata(self):
        tables = self.query("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() "
                            "AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME;")
        if not tables or any(not re.fullmatch(r'[a-zA-Z_][a-zA-Z0-9_]{0,63}', name) for name in tables):
            raise ValueError('unsupported native source tables')
        if self.query('SELECT COUNT(*) FROM flyway_schema_history WHERE success=0;') != ['0']:
            raise ValueError('source has failed schema migration')
        metadata = source_contract({'databaseVersion': self.query('SELECT VERSION();')[0],
                                    'schemaHistory': self.query('SELECT version FROM flyway_schema_history ORDER BY installed_rank;')})
        columns = {}
        for row in self.query("SELECT TABLE_NAME,COLUMN_NAME FROM information_schema.COLUMNS "
                              "WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME,ORDINAL_POSITION;"):
            table, name = row.split('\t')
            if table != 'flyway_schema_history':
                columns.setdefault(table, []).append(name)
        metadata.update(records={table: int(self.query('SELECT COUNT(*) FROM `' + table + '`;')[0]) for table in tables},
                        columns=columns, projections=projection(self.query, columns))
        return metadata

    def export(self, destination):
        import datetime as dt
        import shutil
        import tarfile
        from config import atomic_json
        from recovery import data_inventory, sha256
        self.idle()
        baseline = self.metadata()
        files = source_files(self.root, self.uploads)
        def fingerprints():
            return {name: sha256(path) for name, path in {**files, **self.material}.items() if path.is_file()}
        before = fingerprints()
        destination = Path(destination)
        destination.mkdir(mode=0o700)
        with (destination / 'database.sql').open('xb') as output:
            (destination / 'database.sql').chmod(0o600)
            result = self.subprocess.run(['/opt/homebrew/bin/mariadb-dump', *self.options, '--single-transaction',
                                          '--routines', '--events', '--triggers', '--hex-blob', '--databases', 'ctcdb'],
                                         stdout=output, stderr=self.subprocess.PIPE, env=self.environment, timeout=1800)
            if result.returncode:
                raise ValueError('native export failed; retain partial private export for diagnosis')
        with tarfile.open(destination / 'data.tar', 'x:') as archive:
            (destination / 'data.tar').chmod(0o600)
            for name, path in files.items():
                archive.add(path, arcname=name, recursive=False)
        archived = data_inventory(destination / 'data.tar')
        if {name: entry['sha256'] for name, entry in archived.items() if entry['type'] == 'file'} != {name: before[name] for name, path in files.items() if path.is_file()}:
            raise ValueError('source files changed while archiving')
        for name, path in self.material.items():
            shutil.copyfile(path, destination / name)
            (destination / name).chmod(0o600)
            if sha256(destination / name) != before[name]:
                raise ValueError('source configuration changed while copying')
        self.idle()
        if self.metadata() != baseline or source_files(self.root, self.uploads) != files or fingerprints() != before:
            raise ValueError('source changed during export; do not migrate this payload')
        names = ['database.sql', 'data.tar', *self.material]
        baseline.update(createdAt=dt.datetime.now(dt.timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ'),
                        files={name: {'sha256': sha256(destination / name), 'bytes': (destination / name).stat().st_size}
                               for name in names})
        atomic_json(destination / 'source.json', baseline)
        for path in destination.iterdir():
            with path.open('rb') as artifact:
                os.fsync(artifact.fileno())
        from recovery import flush_directory
        flush_directory(destination)
        flush_directory(destination.parent)
        return baseline


def verify_export(root, expected_sha256):
    from config import private_path
    from recovery import read_json, sha256, validate_data_archive
    root = private_path(Path(root), directory=True)
    manifest = private_path(root / 'source.json')
    if not re.fullmatch(r'[a-f0-9]{64}', expected_sha256) or sha256(manifest) != expected_sha256:
        raise ValueError('native transfer checksum mismatch')
    source = read_json(manifest)
    source_contract(source)
    files = {'database.sql', 'data.tar', 'application-local.yml', '.env', '.env.local', 'google.json'}
    if (set(source.get('files', {})) != files or {path.name for path in root.iterdir()} != files | {'source.json'}
            or source.get('kind') != 'ctc-native-mac' or source.get('format') != 1
            or source.get('sourceApplicationVersion') is not None):
        raise ValueError('native export inventory mismatch')
    for name, expected in source['files'].items():
        path = private_path(root / name)
        if path.stat().st_nlink != 1 or path.stat().st_size != expected['bytes'] or sha256(path) != expected['sha256']:
            raise ValueError('native export artifact changed')
    validate_data_archive(root / 'data.tar')
    return source


def rehearse(runtime, source_root, source_sha256, destination):
    import datetime as dt
    import json
    import shutil
    import tempfile
    import uuid
    from config import atomic_json, private_path
    from probe import Probe
    from recovery import (ARTIFACTS, admit_recovery_image, flush_directory, probe_environment,
                          restore_check, sha256, validate_unit)
    source_root = Path(source_root)
    source = verify_export(source_root, source_sha256)
    runtime.check_destination(Path(destination))
    if runtime.config.get('outboundPaused') is not True:
        raise ValueError('migration must keep target integrations paused')
    for key in ['appImage', 'databaseImage']:
        runtime.docker(['pull', runtime.release[key]], timeout=1800)
    image = json.loads(runtime.docker(['image', 'inspect', runtime.release['appImage']]))[0]
    admit_recovery_image(image)
    work = Path(tempfile.mkdtemp(prefix='.native-migration-', dir=destination))
    for name in ['database.sql', 'data.tar', 'google.json']:
        shutil.copyfile(source_root / name, work / name)
        (work / name).chmod(0o600)
    for name, field in [('db-password', 'databasePasswordFile'), ('db-root-password', 'databaseRootPasswordFile')]:
        shutil.copyfile(private_path(runtime.config[field]), work / name)
        (work / name).chmod(0o600)
    values = probe_environment(runtime.config['appEnvFile'], (work / 'db-password').read_text().rstrip('\n'))
    values.update(environment_values(*((source_root / name).read_text() for name in ['.env', '.env.local'])))
    with (work / 'app.env').open('x') as output:
        (work / 'app.env').chmod(0o600)
        for key, value in values.items():
            if '\n' in value or '\r' in value or '\x00' in value:
                raise ValueError('unsupported migration environment value')
            output.write(key + '=' + value + '\n')
    probe = Probe(runtime, work, source, runtime.release['appImage'], runtime.release['databaseImage'])
    try:
        probe.create_resources()
        probe.record('container', probe.prefix + '-db')
        probe.docker(probe.database_arguments())
        probe.wait_database()
        if not probe.query('SELECT VERSION();')[0].startswith('13.0.'):
            raise ValueError('isolated database differs from qualified native major')
        probe.import_database()
        if projection(probe.query, source['columns']) != source['projections']:
            raise ValueError('native database import changed source rows')
        probe.restore_files()
        probe.verify_files()
        probe.record('container', probe.prefix + '-app')
        probe.docker(probe.app_arguments(values))
        probe.verify_application(values)
        if projection(probe.query, source['columns']) != source['projections']:
            raise ValueError('target Flyway migration changed source rows')
        history = [json.loads(row) for row in probe.query("SELECT JSON_OBJECT('rank',installed_rank,'version',version,'checksum',checksum) "
                    "FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank;")]
        if [row['version'] for row in history] != [str(i) for i in range(1, 22)]:
            raise ValueError('unexpected target migration history')
        tables = probe.query("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE' ORDER BY TABLE_NAME;")
        if any(not re.fullmatch(r'[a-zA-Z_][a-zA-Z0-9_]{0,63}', name) for name in tables):
            raise ValueError('unsupported migrated table identifier')
        records = {table: int(probe.query('SELECT COUNT(*) FROM `' + table + '`;')[0]) for table in tables}
        probe.docker(['stop', '--time', '60', probe.prefix + '-app'])
        args = ['exec', probe.prefix + '-db', '/bin/sh', '-ec',
                'MYSQL_PWD="$(cat /run/secrets/db-password)" exec mariadb-dump --user=ctc --single-transaction '
                '--routines --events --triggers --hex-blob --databases ctcdb']
        with (work / 'database.sql').open('wb') as output:
            probe.docker(args, stdout=output, timeout=1800)
        atomic_json(work / 'installation.json', {**runtime.config, **{key: runtime.release[key] for key in ['appImage', 'databaseImage']}})
        shutil.copyfile(runtime.deploy_root / 'manifest.json', work / 'release.json')
        (work / 'release.json').chmod(0o600)
        stamp = dt.datetime.now(dt.timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')
        manifest = {'format': 1, 'id': str(uuid.uuid4()), 'createdAt': stamp,
                    'installationId': runtime.config['installationId'], 'releaseVersion': runtime.release['version'],
                    'appImage': runtime.release['appImage'], 'databaseImage': runtime.release['databaseImage'],
                    'databaseVersion': probe.query('SELECT VERSION();')[0], 'schemaHistory': history, 'records': records,
                    'files': {name: {'bytes': (work / name).stat().st_size, 'sha256': sha256(work / name)} for name in ARTIFACTS}}
        atomic_json(work / 'manifest.json', manifest)
        validate_unit(work)
        verify_export(source_root, source_sha256)
    finally:
        probe.cleanup()
    for name in ARTIFACTS | {'manifest.json'}:
        with (work / name).open('rb') as artifact:
            os.fsync(artifact.fileno())
    flush_directory(work)
    completed = Path(destination) / ('recovery-' + stamp.replace(':', '') + '-' + manifest['id'])
    os.rename(work, completed)
    flush_directory(Path(destination))
    proof = restore_check(runtime, completed)
    atomic_json(runtime.root / 'state' / ('migration-' + manifest['id'] + '.json'),
                {'format': 1, 'stage': 'rehearsed', 'sourceManifestSha256': source_sha256,
                 'unit': str(completed), 'manifestSha256': sha256(completed / 'manifest.json'), 'proofRun': proof['run']})
    return completed


def cutover(runtime, source_root, source_sha256, unit, confirmation):
    import time
    from config import atomic_json, private_path
    from live import restore
    from recovery import read_json, sha256, validate_unit
    source = verify_export(Path(source_root), source_sha256)
    unit = private_path(Path(unit), directory=True)
    manifest = validate_unit(unit)
    proof = read_json(private_path(runtime.root / 'state' / ('migration-' + manifest['id'] + '.json')))
    if (proof.get('stage') != 'rehearsed' or proof.get('sourceManifestSha256') != source_sha256
            or proof.get('unit') != str(unit) or proof.get('manifestSha256') != sha256(unit / 'manifest.json')
            or runtime.release['version'] != manifest['releaseVersion']
            or manifest['installationId'] != runtime.config.get('installationId')):
        raise ValueError('cutover requires this exact source and rehearsed target recovery unit')
    authority = runtime.root / 'state/production-authority.json'
    if authority.exists() or authority.is_symlink():
        raise ValueError('target authority already recorded; reconcile before any migration retry')
    runtime.preflight()
    metadata = runtime.database_metadata()
    if (runtime.config.get('outboundPaused') is not True
            or metadata['records'].get('ctc_runtime_qualification') != 1
            or any(metadata['records'].get(name) != 0 for name in ['teams', 'drivers', 'seasons', 'races'])):
        raise ValueError('initial migration requires the empty paused qualification target')
    receipt = {'format': 1, 'stage': 'preparing', 'sourceManifestSha256': source_sha256,
               'unit': str(unit), 'manifestSha256': sha256(unit / 'manifest.json'),
               'target': confirmation, 'startedAt': time.time(), 'outboundPaused': True}
    atomic_json(authority, receipt)
    def writes_possible():
        receipt.update(stage='writes-possible', boundaryAt=time.time())
        atomic_json(authority, receipt)
    try:
        result = restore(runtime, unit, confirmation, before_activation=writes_possible)
        runtime.preflight()
        if projection(runtime.query, source['columns']) != source['projections']:
            raise ValueError('activated target differs from source data')
        receipt.update(stage='active-awaiting-acceptance', storageGeneration=runtime.config['storageGeneration'],
                       outboundPaused=True, restoreStage=result['stage'])
        atomic_json(authority, receipt)
        return receipt
    except Exception:
        receipt['stage'] = 'failed-before-writes' if receipt['stage'] == 'preparing' else 'failed-after-writes'
        if receipt['stage'] == 'failed-after-writes':
            try:
                runtime.stop_app()
                receipt['targetStopped'] = True
            except (ValueError, OSError):
                receipt['targetStopped'] = False
        atomic_json(authority, receipt)
        raise


def main():
    import argparse
    import json
    import sys
    from config import private_path
    from recovery import NativeRuntime, sha256
    parser = argparse.ArgumentParser(allow_abbrev=False)
    commands = parser.add_subparsers(dest='operation', required=True)
    for name in ['inventory', 'export']:
        command = commands.add_parser(name, allow_abbrev=False)
        command.add_argument('--source-root', required=True)
        command.add_argument('--observer-user')
        if name == 'export':
            command.add_argument('--destination', required=True)
    for name in ['rehearse', 'cutover']:
        command = commands.add_parser(name, allow_abbrev=False)
        command.add_argument('--directory', required=True)
        command.add_argument('--source-export', required=True)
        command.add_argument('--source-sha256', required=True)
        if name == 'rehearse':
            command.add_argument('--destination', required=True)
        else:
            command.add_argument('--recovery', required=True)
            command.add_argument('--confirm', required=True)
    args = parser.parse_args()
    os.umask(0o077)
    try:
        if args.operation in ['inventory', 'export']:
            if sys.platform != 'darwin':
                raise ValueError('native export supports the verified Mac layout only')
            source = MacSource(args.source_root, observer_user=args.observer_user)
            source.idle()
            if args.operation == 'inventory':
                metadata = source.metadata()
                print(json.dumps({'databaseVersion': metadata['databaseVersion'], 'records': metadata['records'],
                                  'sourceApplicationVersion': None, 'globalProcessVisibilityVerified': True}))
            else:
                destination = Path(args.destination)
                private_path(destination.parent, directory=True)
                source.export(destination)
                checksum = sha256(destination / 'source.json')
                verify_export(destination, checksum)
                print(json.dumps({'status': 'exported', 'path': str(destination), 'sourceManifestSha256': checksum}))
            return
        runtime = NativeRuntime(Path(args.directory), Path(__file__).resolve().parents[1], require_selected=False)
        owner = private_path(runtime.root / '.ctc-manager.lock/owner').read_text().strip()
        if owner != str(os.getppid()):
            raise ValueError('migration requires its launcher-owned installation lock')
        if args.operation == 'rehearse':
            unit = rehearse(runtime, args.source_export, args.source_sha256, Path(args.destination))
            print(json.dumps({'status': 'rehearsed', 'unit': str(unit), 'manifestSha256': sha256(unit / 'manifest.json')}))
        else:
            print(json.dumps(cutover(runtime, args.source_export, args.source_sha256, args.recovery, args.confirm)))
    except Exception as error:
        sys.exit('CTC migration ' + args.operation + ' refused. Inspect private source/target evidence. Error type: ' + type(error).__name__)


if __name__ == '__main__':
    main()
