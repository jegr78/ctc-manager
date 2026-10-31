#!/usr/bin/env python3
import argparse
import copy
import grp
import json
import os
from pathlib import Path
import platform
import pwd
import re
import shutil
import subprocess
import sys
import time
from datetime import datetime, timezone
import uuid

USER = 'ctc-manager'
INSTALL = '/srv/ctc-manager'
CTC_HOME = '/home/ctc-manager'
BACKUP_PARENT = '/mnt/backup'
BACKUP = BACKUP_PARENT + '/ctc-manager'
ROOT = Path(__file__).resolve().parents[1]
TOOLS = ['python3', 'docker', 'sudo', 'systemctl', 'loginctl', 'tailscale', 'getent',
         'id', 'findmnt', 'ss', 'stat', 'useradd', 'dockerd-rootless.sh', 'rootlesskit',
         'slirp4netns', 'newuidmap', 'newgidmap']


def command(argv, input=None, required=True):
    result = subprocess.run(argv, input=input, text=True, capture_output=True,
                            cwd='/', env={**os.environ, 'LC_ALL': 'C'}, timeout=30)
    if required and result.returncode:
        raise ValueError('command failed: ' + argv[0] + ' ' + str(result.returncode))
    return result


def as_user(argv, uid, **kwargs):
    return command(['sudo', '-n', '-u', USER, 'env', 'LC_ALL=C',
                    'XDG_RUNTIME_DIR=/run/user/' + str(uid),
                    'DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/' + str(uid) + '/bus', *argv], **kwargs)


def metadata(path, uid=None):
    args = ['stat', '-c', '%u|%g|%a|%F', '--', path]
    result = command(args, required=False) if uid is None else as_user(args, uid, required=False)
    if result.returncode:
        if 'No such file' in result.stderr:
            return None
        raise ValueError('cannot inspect directory/socket metadata: ' + path)
    owner, group, mode, kind = result.stdout.strip().split('|', 3)
    return {'uid': int(owner), 'gid': int(group), 'mode': int(mode, 8), 'type': kind}


def collect():
    if platform.system() != 'Linux':
        raise ValueError('host setup requires Linux')
    missing = [name for name in TOOLS if shutil.which(name) is None]
    if missing:
        raise ValueError('missing prerequisite tools: ' + ', '.join(missing))
    sudo = command(['sudo', '-n', 'true'], required=False).returncode == 0
    if not sudo:
        raise ValueError('host setup requires an operator with noninteractive sudo')
    try:
        account = pwd.getpwnam(USER)
        user = {'uid': account.pw_uid, 'gid': account.pw_gid, 'home': account.pw_dir}
    except KeyError:
        user = None
    uid = user['uid'] if user else None
    directory_paths = ['/srv', BACKUP_PARENT, INSTALL, CTC_HOME, BACKUP]
    directories = {path: metadata(path, uid if path in [INSTALL, CTC_HOME, BACKUP] else None)
                   for path in directory_paths}
    mount = command(['findmnt', '--json', '--mountpoint', BACKUP_PARENT,
                     '--output', 'TARGET,UUID,FSTYPE'], required=False)
    backup_mount = json.loads(mount.stdout)['filesystems'][0] if mount.returncode == 0 else None
    ts = json.loads(command(['tailscale', 'status', '--json']).stdout)
    serve = json.loads(command(['tailscale', 'serve', 'status', '--json']).stdout)
    listening = command(['ss', '-H', '-ltn']).stdout
    listeners = [row.split()[3] for row in listening.splitlines() if len(row.split()) >= 5]
    socket = metadata('/run/user/' + str(uid) + '/docker.sock', uid) if uid is not None else None
    runtime = metadata('/run/user/' + str(uid), uid) if uid is not None else None
    docker = None
    own_app = False
    if uid is not None and socket is not None and socket['type'] == 'socket' and socket['uid'] == uid:
        info = as_user(['docker', '--host', 'unix:///run/user/' + str(uid) + '/docker.sock',
                        'info', '--format', '{{json .}}'], uid, required=False)
        if info.returncode == 0:
            parsed = json.loads(info.stdout)
            docker = {'rootless': 'name=rootless' in parsed.get('SecurityOptions', []),
                      'memory': parsed.get('MemoryLimit') is True}
            containers = as_user(['docker', '--host', 'unix:///run/user/' + str(uid) + '/docker.sock',
                                  'ps', '--filter', 'label=com.docker.compose.project=ctc-manager',
                                  '--filter', 'label=com.docker.compose.service=app', '--format', '{{json .}}'], uid)
            own_app = any('127.0.0.1:9092->8080/tcp' in json.loads(row).get('Ports', '')
                          for row in containers.stdout.splitlines() if row.strip())
    memory = {line.split(':')[0]: int(line.split()[1]) * 1024
              for line in Path('/proc/meminfo').read_text().splitlines() if line.startswith(('MemTotal:', 'MemAvailable:'))}
    return {
        'memoryTotal': memory['MemTotal'], 'memoryAvailable': memory['MemAvailable'],
        'system': platform.system(), 'architecture': platform.machine(), 'missingTools': missing,
        'sudoAvailable': sudo, 'operatorUid': os.getuid(), 'user': user,
        'hostUids': [entry.pw_uid for entry in pwd.getpwall()],
        'hostGids': [entry.gr_gid for entry in grp.getgrall()],
        'duplicateUid': bool(user and sum(account.pw_uid == uid for account in pwd.getpwall()) != 1),
        'groups': command(['id', '-nG', USER]).stdout.split() if user else [],
        'subuid': Path('/etc/subuid').read_text(), 'subgid': Path('/etc/subgid').read_text(),
        'directories': directories, 'backupMount': backup_mount,
        'listeners': listeners, 'ownAppListening': own_app,
        'tailscaleIPs': ts.get('Self', {}).get('TailscaleIPs', []),
        'dnsName': ts.get('Self', {}).get('DNSName', '').rstrip('.'),
        'tailscaleRunning': ts.get('BackendState') == 'Running' and ts.get('Self', {}).get('Online') is True,
        'serve': serve, 'socket': socket, 'runtimeDirectory': runtime, 'docker': docker,
        'cgroupMemory': Path('/sys/fs/cgroup/cgroup.controllers').exists()
                        and 'memory' in Path('/sys/fs/cgroup/cgroup.controllers').read_text().split(),
        'diskFree': shutil.disk_usage('/srv').free,
    }


def check_ranges(text, uid, host_ids):
    ranges = []
    for line in text.splitlines():
        if not line.strip():
            continue
        owner, start, size = line.split(':')
        start, size = int(start), int(size)
        if start < 1 or size < 1 or start + size > 2**32:
            raise ValueError('invalid subordinate ID range')
        ranges.append((owner, start, start + size))
    if len(set(ranges)) != len(ranges):
        raise ValueError('subordinate ID range overlap')
    own = [entry for entry in ranges if entry[0] in {USER, str(uid)}]
    if not any(end - start >= 65536 for _, start, end in own):
        raise ValueError('ctc-manager requires at least 65536 subordinate IDs')
    for owner, start, end in own:
        if any(start <= identity < end for identity in host_ids):
            raise ValueError('subordinate IDs must not map an existing host identity')
        for other, other_start, other_end in ranges:
            if (owner, start, end) == (other, other_start, other_end):
                continue
            if start < other_end and other_start < end:
                raise ValueError('subordinate ID range overlap')


def serve_ready(serve, dns):
    key = dns + ':9443'
    if serve.get('AllowFunnel', {}).get(key):
        raise ValueError('CTC port9443 must not have Funnel enabled')
    other_hosts = [name for name in serve.get('Web', {}) if name.endswith(':9443') and name != key]
    if other_hosts:
        raise ValueError('HTTPS port9443 has another host mapping')
    web = serve.get('Web', {}).get(key)
    tcp = serve.get('TCP', {}).get('9443')
    if web is None and tcp is None:
        return False
    if web != {'Handlers': {'/': {'Proxy': 'http://127.0.0.1:9092'}}} or tcp != {'HTTPS': True}:
        raise ValueError('HTTPS port9443 has an unrelated mapping')
    return True


def validate(facts, expected_uuid):
    if facts['system'] != 'Linux' or facts['architecture'] != 'x86_64':
        raise ValueError('host setup requires Linux x86_64')
    if facts['missingTools'] or not facts['sudoAvailable']:
        raise ValueError('host setup prerequisite missing')
    if not facts['cgroupMemory']:
        raise ValueError('rootless memory control requires cgroupv2 memory delegation')
    if facts.get('diskFree', 4 * 1024**3) < 3 * 1024**3:
        raise ValueError('insufficient installation storage')
    if facts['memoryTotal'] < 4 * 1024**3:
        raise ValueError('insufficient host memory capacity for CTC and shared services')
    mount = facts['backupMount']
    if not mount or mount['target'] != BACKUP_PARENT:
        raise ValueError('backup device is not mounted at /mnt/backup')
    if str(uuid.UUID(mount['uuid'])) != str(uuid.UUID(expected_uuid)):
        raise ValueError('backup mount UUID does not match the expected device')
    if not facts['tailscaleRunning'] or not re.fullmatch(r'[a-zA-Z0-9.-]+\.ts\.net', facts['dnsName']):
        raise ValueError('Tailscale must be online with a valid node DNS name')
    user = facts['user']
    uid = user['uid'] if user else None
    if user:
        if type(uid) is not int or uid <= 0 or facts['duplicateUid']:
            raise ValueError('dedicated user must have a unique non-root UID')
        if user['home'] != CTC_HOME:
            raise ValueError('dedicated user home must be /home/ctc-manager')
        if set(facts['groups']) != {USER}:
            raise ValueError('dedicated user has privileged or unexpected supplementary groups')
        check_ranges(facts['subuid'], uid, facts['hostUids'])
        check_ranges(facts['subgid'], uid, facts['hostGids'])
    for parent in ['/srv', BACKUP_PARENT]:
        value = facts['directories'][parent]
        if not value or value['type'] != 'directory' or value['uid'] not in {0, facts['operatorUid']} or value['mode'] & 0o022:
            raise ValueError('installation/backup parent must be a trusted non-writable directory')
    for path in [INSTALL, CTC_HOME, BACKUP]:
        value = facts['directories'].get(path)
        if value is not None:
            if not user or value['uid'] != uid:
                raise ValueError('existing directory has a foreign owner: ' + path)
            if value['type'] != 'directory' or value['mode'] & 0o077:
                raise ValueError('existing directory must be private and not linked: ' + path)
    socket = facts['socket']
    runtime = facts['runtimeDirectory']
    if socket and (socket['uid'] != uid or socket['type'] != 'socket'):
        raise ValueError('rootless Docker socket must be owned by the dedicated user')
    if runtime and (runtime['uid'] != uid or runtime['type'] != 'directory' or runtime['mode'] & 0o077):
        raise ValueError('rootless runtime directory must be private')
    if facts['docker'] and not facts['docker']['rootless']:
        raise ValueError('configured Docker daemon must be rootless')
    if facts['docker'] and not facts['docker']['memory']:
        raise ValueError('rootless daemon cannot enforce memory limits')
    ready = serve_ready(facts['serve'], facts['dnsName'])
    app_ports = [item for item in facts['listeners'] if item.endswith(':9092')]
    if app_ports and (not facts['ownAppListening'] or any(item != '127.0.0.1:9092' for item in app_ports)):
        raise ValueError('host port9092 is occupied by an unrelated listener')
    https_ports = [item for item in facts['listeners'] if item.endswith(':9443')]
    if https_ports and (not ready or any(item.rsplit(':', 1)[0].split('%', 1)[0].strip('[]') not in facts.get('tailscaleIPs', []) for item in https_ports)):
        raise ValueError('host port9443 is occupied by an unrelated listener')
    return {'uid': uid, 'dnsName': facts['dnsName'], 'serveReady': ready,
            'backupUuid': str(uuid.UUID(expected_uuid)), 'dockerReady': facts['docker'] is not None}


def verify_serve_preserved(before, after, dns):
    stripped = []
    for original in [before, after]:
        value = copy.deepcopy(original)
        for section, key in [('TCP', '9443'), ('Web', dns + ':9443'), ('AllowFunnel', dns + ':9443')]:
            value.setdefault(section, {}).pop(key, None)
        stripped.append(value)
    if stripped[0] != stripped[1]:
        raise ValueError('shared Tailscale Serve/Funnel configuration changed; do not reset it')
    if not serve_ready(after, dns):
        raise ValueError('private CTC Serve mapping is missing')



def trusted_unit():
    for path in [ROOT, ROOT / 'lib', ROOT / 'lib/host.py', ROOT / 'setup-host.sh',
                 ROOT / 'systemd', ROOT / 'systemd/docker-rootless.service']:
        value = path.lstat()
        if path.is_symlink() or value.st_uid not in {0, os.getuid()} or value.st_mode & 0o022:
            raise ValueError('run host setup from the operator-owned verified source copy')
    return (ROOT / 'systemd/docker-rootless.service').read_text()


def apply(facts, expected_uuid, execute=command, refresh=collect, unit_text=None):
    report = validate(facts, expected_uuid)
    before = copy.deepcopy(facts['serve'])
    unit_text = trusted_unit() if unit_text is None else unit_text
    if facts['user'] is None:
        execute(['sudo', '-n', 'useradd', '--create-home', '--user-group', '--key',
                 'HOME_MODE=0700', '--shell', '/bin/bash', USER])
        facts = refresh()
        report = validate(facts, expected_uuid)
    uid = report['uid']
    if uid is None:
        raise ValueError('dedicated user was not created')

    def run_user(args, **kwargs):
        return execute(['sudo', '-n', '-u', USER, 'env', 'LC_ALL=C',
                        'XDG_RUNTIME_DIR=/run/user/' + str(uid),
                        'DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/' + str(uid) + '/bus', *args], **kwargs)

    for path in [INSTALL]:
        if facts['directories'].get(path) is None:
            execute(['sudo', '-n', 'install', '-d', '-m', '0700', '-o', USER, '-g', USER, path])
    lock = INSTALL + '/.ctc-manager.lock'
    try:
        run_user(['mkdir', '-m', '0700', '--', lock])
    except (ValueError, subprocess.CalledProcessError) as error:
        raise ValueError('installation lock exists or cannot be acquired; reconcile it before retrying') from error
    owner = str(os.getpid()) + '\n'
    owner_written = False

    def write_state(path, value):
        temporary = path + '.tmp-' + str(os.getpid())
        run_user(['tee', temporary], input=json.dumps(value, indent=2) + '\n')
        run_user(['chmod', '0600', temporary])
        run_user(['mv', '--', temporary, path])

    try:
        run_user(['tee', lock + '/owner'], input=owner)
        owner_written = True
        if facts['directories'].get(BACKUP) is None:
            execute(['sudo', '-n', 'install', '-d', '-m', '0700', '-o', USER, '-g', USER, BACKUP])
        run_user(['install', '-d', '-m', '0700', INSTALL + '/config', INSTALL + '/secrets',
                  INSTALL + '/state', INSTALL + '/releases', CTC_HOME + '/.config',
                  CTC_HOME + '/.config/systemd', CTC_HOME + '/.config/systemd/user'])
        run_user(['tee', lock + '/metadata.json'], input=json.dumps({
            'pid': os.getpid(), 'callerUid': os.getuid(), 'operation': 'setup-host',
            'startedAt': datetime.now(timezone.utc).isoformat()}) + '\n')
        write_state(INSTALL + '/state/host-setup.json', {'status': 'started', 'operation': 'setup-host'})
        target_unit = CTC_HOME + '/.config/systemd/user/docker.service'
        existing = run_user(['cat', target_unit], required=False)
        if existing.returncode == 0 and existing.stdout != unit_text:
            raise ValueError('existing rootless Docker unit differs; reconcile it before replacing')
        if existing.returncode:
            run_user(['tee', target_unit], input=unit_text)
            run_user(['chmod', '0644', target_unit])
        execute(['sudo', '-n', 'loginctl', 'enable-linger', USER])
        execute(['sudo', '-n', 'systemctl', 'start', 'user@' + str(uid) + '.service'])
        run_user(['systemctl', '--user', 'daemon-reload'])
        run_user(['systemctl', '--user', 'enable', '--now', 'docker.service'])
        deadline = time.monotonic() + 90
        while True:
            facts = refresh()
            report = validate(facts, expected_uuid)
            if report['dockerReady']:
                break
            if time.monotonic() >= deadline:
                raise ValueError('rootless Docker readiness timed out')
            time.sleep(1)
        if not report['serveReady']:
            execute(['sudo', '-n', 'tailscale', 'serve', '--bg', '--https=9443', 'http://127.0.0.1:9092'])
        after = refresh()
        report = validate(after, expected_uuid)
        verify_serve_preserved(before, after['serve'], report['dnsName'])
        receipt = {'format': 1, 'status': 'complete', **report,
                   'updatedAt': datetime.now(timezone.utc).isoformat()}
        write_state(INSTALL + '/state/host.json', receipt)
        write_state(INSTALL + '/state/host-setup.json', {'status': 'complete', 'operation': 'setup-host'})
        return report
    except Exception:
        write_state(INSTALL + '/state/host-setup.json', {'status': 'failed', 'operation': 'setup-host'})
        raise
    finally:
        if owner_written:
            current = run_user(['cat', lock + '/owner']).stdout
            if current != owner:
                raise ValueError('installation lock owner changed; manual reconciliation required')
            run_user(['rm', '-f', '--', lock + '/owner', lock + '/metadata.json'])
        run_user(['rmdir', '--', lock])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('command', choices=['check', 'validate', 'apply'])
    parser.add_argument('fixture', nargs='?')
    parser.add_argument('--backup-uuid', required=True)
    args = parser.parse_args()
    facts = json.loads(Path(args.fixture).read_text()) if args.command == 'validate' else collect()
    result = apply(facts, args.backup_uuid) if args.command == 'apply' else validate(facts, args.backup_uuid)
    print(json.dumps({'result': result, 'facts': facts}, indent=2))


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, KeyError, json.JSONDecodeError, subprocess.TimeoutExpired, subprocess.CalledProcessError) as error:
        sys.exit('CTC host: ' + str(error))
