#!/usr/bin/env python3
import json
from pathlib import Path
import time
import uuid

from config import atomic_json, private_path

LABEL = 'org.ctc.recovery.run'


class Probe:
    def __init__(self, runtime, unit, manifest, app_image, database_image):
        self.runtime = runtime
        self.unit = Path(unit)
        self.manifest = manifest
        self.app_image = app_image
        self.database_image = database_image
        self.id = uuid.uuid4().hex
        self.prefix = 'ctc-recovery-' + self.id
        self.network = self.prefix + '-network'
        self.resources = []
        state = runtime.root / 'state'
        state.mkdir(mode=0o700, exist_ok=True)
        private_path(state, directory=True)
        checks = state / 'recovery-checks'
        checks.mkdir(mode=0o700, exist_ok=True)
        private_path(checks, directory=True)
        self.directory = checks / self.id
        self.directory.mkdir(mode=0o700)
        self.runtime.diagnostic = self.directory / 'diagnostics.log'
        self.runtime.diagnostic.touch(mode=0o600)

    def docker(self, arguments, **kwargs):
        return self.runtime.docker(arguments, **kwargs)

    def volume(self, role):
        return self.prefix + '-' + role

    def record(self, kind, name):
        self.resources.append((kind, name))
        atomic_json(self.directory / 'resources.json', {'run': self.id, 'resources': self.resources})

    def volume_labels(self, role):
        return ['--label', LABEL + '=' + self.id]

    def create_resources(self):
        self.record('network', self.network)
        self.docker(['network', 'create', '--internal', '--label', LABEL + '=' + self.id, self.network])
        network = json.loads(self.docker(['network', 'inspect', self.network]))[0]
        if network.get('Internal') is not True or (network.get('Labels') or {}).get(LABEL) != self.id:
            raise ValueError('probe network ownership or isolation mismatch')
        for role in ['database', 'data', 'logs', 'credentials']:
            name = self.volume(role)
            self.record('volume', name)
            self.docker(['volume', 'create', *self.volume_labels(role), name])
            volume = json.loads(self.docker(['volume', 'inspect', name]))[0]
            if (volume.get('Labels') or {}).get(LABEL) != self.id:
                raise ValueError('probe volume ownership mismatch; existing volume refused')

    def container_arguments(self, role):
        return ['run', '--detach', '--name', self.prefix + '-' + role, '--label', LABEL + '=' + self.id,
                '--network', self.network, '--security-opt', 'no-new-privileges']

    def database_arguments(self):
        args = self.container_arguments('db') + ['--network-alias', 'db', '--memory', '1g']
        for key, value in {'MARIADB_DATABASE': 'ctcdb', 'MARIADB_USER': 'ctc',
                           'MARIADB_PASSWORD_FILE': '/run/secrets/db-password',
                           'MARIADB_ROOT_PASSWORD_FILE': '/run/secrets/db-root-password'}.items():
            args += ['--env', key + '=' + value]
        args += ['--mount', 'type=volume,src=' + self.volume('database') + ',dst=/var/lib/mysql']
        for name in ['db-password', 'db-root-password']:
            args += ['--mount', 'type=bind,src=' + str(self.unit / name) + ',dst=/run/secrets/' + name + ',readonly']
        return args + ['--health-cmd', 'healthcheck.sh --connect --innodb_initialized',
                       '--health-interval', '5s', '--health-timeout', '5s', '--health-retries', '30', self.database_image]

    def app_arguments(self, environment):
        path = self.directory / 'probe.env'
        with path.open('x') as output:
            path.chmod(0o600)
            for key, value in environment.items():
                if '\n' in value or '\r' in value or '\x00' in value:
                    raise ValueError('invalid probe environment value')
                output.write(key + '=' + value + '\n')
        args = self.container_arguments('app') + ['--memory', '2g', '--read-only', '--cap-drop', 'ALL',
                                                 '--tmpfs', '/tmp:rw,exec,nosuid,nodev,size=512m', '--env-file', str(path)]
        for role, target in [('data', '/app/data'), ('logs', '/app/logs'), ('credentials', '/run/ctc')]:
            args += ['--mount', 'type=volume,src=' + self.volume(role) + ',dst=' + target + (',readonly' if role == 'credentials' else '')]
        return args + [self.app_image]

    def helper(self, role, arguments, **kwargs):
        name = self.prefix + '-' + role
        self.record('container', name)
        arguments = [value for value in arguments if value != '--rm']
        return self.docker(arguments[:1] + ['--name', name, '--label', LABEL + '=' + self.id] + arguments[1:], **kwargs)

    def wait_database(self):
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline:
            state = json.loads(self.docker(['inspect', self.prefix + '-db']))[0]['State']
            if state.get('Health', {}).get('Status') == 'healthy':
                return
            if not state['Running']:
                raise ValueError('isolated database exited')
            time.sleep(2)
        raise ValueError('isolated database did not become healthy')

    def import_database(self):
        args = ['exec', '-i', self.prefix + '-db', '/bin/sh', '-ec',
                'export EDITOR=/bin/false VISUAL=/bin/false PAGER=/bin/false; '
                'MYSQL_PWD="$(cat /run/secrets/db-password)" exec mariadb --sandbox --user=ctc --database=ctcdb']
        with (self.unit / 'database.sql').open('rb') as source:
            self.docker(args, stdin=source, timeout=1800)

    def restore_files(self):
        args = ['run', '--rm', '--network', 'none', '--read-only', '--user', '0:0',
                '--cap-drop', 'ALL', '--cap-add', 'CHOWN', '--security-opt', 'no-new-privileges',
                '--mount', 'type=volume,src=' + self.volume('data') + ',dst=/restore,volume-nocopy',
                '--entrypoint', '/bin/sh', self.app_image, '-ec',
                'umask 077; tar -xf - -C /restore --no-same-owner --no-same-permissions; '
                'chown -R "$(id -u ctc):$(id -g ctc)" /restore; sync -f /restore']
        with (self.unit / 'data.tar').open('rb') as source:
            self.helper('files-restore', args[:1] + ['-i'] + args[1:], stdin=source, timeout=1800)
        self.helper('credentials-restore', ['run', '--rm', '--network', 'none', '--read-only', '--user', '0:0',
                     '--cap-drop', 'ALL', '--cap-add', 'CHOWN', '--security-opt', 'no-new-privileges',
                     '--mount', 'type=bind,src=' + str(self.unit / 'google.json') + ',dst=/source/google.json,readonly',
                     '--mount', 'type=volume,src=' + self.volume('credentials') + ',dst=/run/ctc',
                     '--entrypoint', '/bin/sh', self.app_image, '-ec',
                     'umask 077; cat /source/google.json > /run/ctc/google-credentials.json; '
                     'chmod 0400 /run/ctc/google-credentials.json; '
                     'chown "$(id -u ctc):$(id -g ctc)" /run/ctc/google-credentials.json; sync -f /run/ctc'])

    def query(self, sql):
        return self.docker(['exec', '-i', self.prefix + '-db', '/bin/sh', '-ec',
                            'MYSQL_PWD="$(cat /run/secrets/db-password)" exec mariadb --sandbox --user=ctc --database=ctcdb --batch --raw --skip-column-names'],
                           input=sql.encode()).decode().splitlines()

    def verify_database(self):
        for name, count in self.manifest['records'].items():
            actual = int(self.query('SELECT COUNT(*) FROM `' + name + '`;')[0])
            if actual != count:
                raise ValueError('restored record count differs: ' + name)
        history = [json.loads(row) for row in self.query("SELECT JSON_OBJECT('rank',installed_rank,'version',version,'checksum',checksum) FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank;")]
        if history != self.manifest['schemaHistory']:
            raise ValueError('restored schema history differs')

    def verify_files(self):
        from recovery import data_inventory
        path = self.directory / 'restored-data.tar'
        with path.open('xb') as output:
            path.chmod(0o600)
            self.helper('files-check', ['run', '--rm', '--network', 'none', '--read-only', '--cap-drop', 'ALL',
                         '--security-opt', 'no-new-privileges', '--mount',
                         'type=volume,src=' + self.volume('data') + ',dst=/app/data,readonly',
                         '--entrypoint', 'tar', self.app_image, '-C', '/app/data', '-cf', '-', '.'], stdout=output, timeout=1800)
        if data_inventory(path) != data_inventory(self.unit / 'data.tar'):
            raise ValueError('restored file inventory differs')
        path.unlink()

    def curl(self, path, environment, authenticated=True, data=None):
        def quote(value):
            return '"' + value.replace('\\', '\\\\').replace('"', '\\"') + '"'
        lines = ['url = ' + quote('http://localhost:8080' + path), 'silent', 'show-error',
                 'max-time = 120', 'cookie-jar = /tmp/ctc-recovery-cookies', 'cookie = /tmp/ctc-recovery-cookies']
        if authenticated:
            lines.append('user = ' + quote(environment['SPRING_SECURITY_USER_NAME'] + ':' + environment['SPRING_SECURITY_USER_PASSWORD']))
        if data is not None:
            lines.append('data = ' + quote(data))
        response = self.docker(['exec', '-i', self.prefix + '-app', 'curl', '--config', '-',
                                '--write-out', '\n%{http_code}'], input=('\n'.join(lines) + '\n').encode())
        body, code = response.rsplit(b'\n', 1)
        return int(code), body

    def verify_application(self, environment):
        deadline = time.monotonic() + 240
        while time.monotonic() < deadline:
            state = json.loads(self.docker(['inspect', self.prefix + '-app']))[0]['State']
            if not state['Running']:
                raise ValueError('isolated application exited')
            try:
                status, _ = self.curl('/actuator/health', environment, authenticated=False)
                if status == 200:
                    break
            except ValueError:
                pass
            time.sleep(3)
        else:
            raise ValueError('isolated application did not become healthy')
        status, body = self.curl('/actuator/info', environment)
        if status != 200 or json.loads(body).get('ctcRecovery') != {'version': 1, 'outboundEnabled': False}:
            raise ValueError('running application did not confirm disabled recovery policy')
        status, _ = self.curl('/admin/teams', environment, authenticated=False)
        if status != 401:
            raise ValueError('isolated admin endpoint is not authenticated')
        for path in ['/admin/seasons', '/admin/teams', '/admin/drivers']:
            status, _ = self.curl(path, environment)
            if status != 200:
                raise ValueError('authenticated recovery read failed: ' + path)

        self.verify_credentials()

    def verify_credentials(self):
        from recovery import sha256
        try:
            checksum = self.docker(['exec', self.prefix + '-app', 'sha256sum',
                                    '/run/ctc/google-credentials.json']).decode().split()
        except ValueError:
            raise ValueError('restored Google credentials are not readable by the application') from None
        if not checksum or checksum[0] != sha256(self.unit / 'google.json'):
            raise ValueError('restored Google credential bytes differ')
        try:
            result = self.docker(['exec', self.prefix + '-db', '/bin/sh', '-ec',
                                  'MYSQL_PWD="$(cat /run/secrets/db-root-password)" exec mariadb --sandbox '
                                  '--user=root --protocol=TCP --host=127.0.0.1 --batch --skip-column-names --execute "SELECT 1;"']).decode().strip()
        except ValueError:
            raise ValueError('restored database root credential login failed') from None
        if result != '1':
            raise ValueError('restored database root credential proof failed')

    def verify_graphic(self, environment):
        from html.parser import HTMLParser
        from urllib.parse import urlencode
        teams = self.query('SELECT CAST(id AS CHAR) FROM season_teams ORDER BY id LIMIT 1;')
        if not teams:
            self.docker(['exec', self.prefix + '-app', 'java', '-cp', '/app/ctc-manager.jar',
                         '-Dloader.main=com.microsoft.playwright.CLI', 'org.springframework.boot.loader.launch.PropertiesLauncher',
                         'screenshot', '--browser', 'chromium', '--timeout', '30000', 'about:blank', '/tmp/recovery-graphic.png'])
            graphic = self.docker(['exec', self.prefix + '-app', 'cat', '/tmp/recovery-graphic.png'])
            kind = 'chromium-smoke-no-season-team'
        else:
            team = str(uuid.UUID(teams[0]))
            status, body = self.curl('/admin/tools/team-cards', environment)
            class TokenParser(HTMLParser):
                token = None
                def handle_starttag(self, tag, attrs):
                    values = dict(attrs)
                    if tag == 'input' and values.get('name') == '_csrf':
                        self.token = values.get('value')
            parser = TokenParser()
            parser.feed(body.decode())
            if status != 200 or not parser.token:
                raise ValueError('local graphic probe has no CSRF token')
            self.docker(['exec', self.prefix + '-app', 'rm', '-rf', '/app/data/uploads/team-cards'])
            status, _ = self.curl('/admin/tools/team-cards/generate/' + team, environment, data=urlencode({'_csrf': parser.token}))
            if status != 302:
                raise ValueError('local team card generation failed')
            status, graphic = self.curl('/admin/tools/team-cards/download/' + team, environment)
            if status != 200:
                raise ValueError('generated local team card is unavailable')
            kind = 'restored-season-team-card'
        if not graphic.startswith(b'\x89PNG\r\n\x1a\n') or len(graphic) < 100:
            raise ValueError('local graphic is not a valid PNG')
        return kind

    def lookup_resource(self, kind, name):
        def listed():
            args = [kind, 'ls'] + (['--all'] if kind == 'container' else [])
            template = '{{.Names}}' if kind == 'container' else '{{.Name}}'
            return self.docker(args + ['--filter', 'name=' + name, '--format', template]).decode().splitlines()
        if name not in listed():
            return None
        try:
            return json.loads(self.docker([kind, 'inspect', name]))[0]
        except ValueError:
            if name not in listed():
                return None
            raise

    def cleanup(self, keep_volumes=False):
        remaining = []
        errors = []
        for kind, name in reversed(self.resources):
            if keep_volumes and kind == 'volume':
                remaining.append((kind, name))
                continue
            try:
                inspected = self.lookup_resource(kind, name)
                if inspected is None:
                    continue
                labels = inspected.get('Config', {}).get('Labels', {}) if kind == 'container' else inspected.get('Labels', {})
                if (labels or {}).get(LABEL) != self.id:
                    raise ValueError('resource ownership mismatch')
                self.docker(['rm', '--force', name] if kind == 'container' else [kind, 'rm', name])
            except ValueError as error:
                remaining.append((kind, name))
                errors.append(str(error))
        self.resources = remaining
        atomic_json(self.directory / 'resources.json', {'run': self.id, 'resources': remaining})
        if errors:
            raise ValueError('probe cleanup incomplete: ' + '; '.join(errors))

    def run(self):
        from recovery import probe_environment, sha256
        environment = probe_environment(self.unit / 'app.env', (self.unit / 'db-password').read_text().rstrip('\n'))
        try:
            self.create_resources()
            self.record('container', self.prefix + '-db')
            self.docker(self.database_arguments())
            self.wait_database()
            self.import_database()
            self.verify_database()
            self.restore_files()
            self.verify_files()
            self.record('container', self.prefix + '-app')
            self.docker(self.app_arguments(environment))
            self.verify_application(environment)
            self.verify_database()
            graphic = self.verify_graphic(environment)
            result = {'format': 1, 'run': self.id, 'unitId': self.manifest['id'],
                      'manifestSha256': sha256(self.unit / 'manifest.json'), 'status': 'passed',
                      'graphic': graphic, 'credentialsVerified': True, 'outboundEnabled': False, 'hostPorts': [],
                      'appImageId': self.app_image, 'databaseImageId': self.database_image}
        except Exception:
            for role in ['app', 'db']:
                try:
                    logs = self.docker(['logs', '--tail', '200', self.prefix + '-' + role])
                    with (self.directory / (role + '.log')).open('wb') as output:
                        (self.directory / (role + '.log')).chmod(0o600)
                        output.write(logs)
                except ValueError:
                    pass
            atomic_json(self.directory / 'result.json', {'format': 1, 'run': self.id, 'status': 'failed'})
            raise
        finally:
            try:
                self.cleanup()
            except Exception:
                atomic_json(self.directory / 'result.json', {'format': 1, 'run': self.id, 'status': 'failed', 'stage': 'cleanup'})
                raise
        atomic_json(self.directory / 'result.json', result)
        return result
