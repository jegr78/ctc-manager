#!/usr/bin/env python3
import datetime as dt
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import tarfile
import uuid

from config import atomic_json

REPOSITORY = 'jegr78/ctc-manager'
REMOTE = 'https://github.com/' + REPOSITORY + '.git'
PUBLIC_URL = 'https://jegr78.github.io/ctc-manager/'
EXTENSIONS = {'html', 'css', 'js', 'json', 'png', 'jpg', 'jpeg', 'webp', 'gif', 'svg', 'ico', 'woff', 'woff2', 'ttf'}
MAX_SITE = 512 * 1024 * 1024


def identity(value):
    if not isinstance(value, str) or str(uuid.UUID(value)) != value:
        raise ValueError('invalid publication identity')
    return value


def sha(value):
    if not isinstance(value, str) or not re.fullmatch('[a-f0-9]{64}', value):
        raise ValueError('invalid publication hash')
    return value


def validate_request(value):
    if value.get('format') != 1 or value.get('approved') is not True:
        raise ValueError('publication is not approved')
    identity(value.get('id'))
    identity(value.get('snapshotId'))
    sha(value.get('manifestSha256'))
    if type(value.get('attempt')) is not int or not 1 <= value['attempt'] <= 1000:
        raise ValueError('invalid publication attempt')
    return value


def timestamp(value):
    result = dt.datetime.fromisoformat(value.replace('Z', '+00:00'))
    if result.tzinfo is None:
        raise ValueError('publication timestamp needs timezone')
    return result


def validate_boot(marker, container_start):
    if marker.get('format') != 1 or marker.get('enabled') is not True or timestamp(marker['startedAt']) < timestamp(container_start):
        raise ValueError('publication is suspended for this application boot')


def public_path(name):
    path = PurePosixPath(name)
    if (not name or path.is_absolute() or str(path) != name or '..' in path.parts or '\\' in name
            or any(ord(c) < 32 or ord(c) == 127 for c in name)
            or any(part.startswith('.') for part in path.parts) and name != '.nojekyll'
            or name != '.nojekyll' and path.suffix.removeprefix('.').lower() not in EXTENSIONS):
        raise ValueError('invalid public snapshot path')
    return name


def validate_snapshot(raw, request):
    validate_request(request)
    if len(raw) > MAX_SITE + 64 * 1024 * 1024:
        raise ValueError('snapshot archive exceeds limit')
    entries = {}
    with tarfile.open(fileobj=io.BytesIO(raw), mode='r:') as archive:
        for entry in archive:
            name = entry.name
            if entry.isdir():
                directory = PurePosixPath(name)
                if (not (name == 'site' or name.startswith('site/')) or directory.is_absolute()
                        or '..' in directory.parts or str(directory) != name or '\\' in name
                        or any(part.startswith('.') for part in directory.parts)):
                    raise ValueError('unexpected snapshot directory')
                continue
            if (not entry.isfile() or entry.size > 32 * 1024 * 1024 or name in entries
                    or len(entries) >= 100_001 or name != 'manifest.json' and not name.startswith('site/')):
                raise ValueError('unexpected snapshot archive entry')
            if name != 'manifest.json':
                public_path(name.removeprefix('site/'))
            entries[name] = archive.extractfile(entry).read()
    manifest_raw = entries.pop('manifest.json', b'')
    if len(manifest_raw) > 4 * 1024 * 1024 or hashlib.sha256(manifest_raw).hexdigest() != request['manifestSha256']:
        raise ValueError('approved manifest does not match snapshot')
    manifest = json.loads(manifest_raw)
    if manifest.get('format') != 1 or manifest.get('id') != request['snapshotId']:
        raise ValueError('snapshot identity does not match approval')
    expected = manifest.get('files', {})
    if not expected or 'index.html' not in expected or set(entries) != {'site/' + name for name in expected}:
        raise ValueError('snapshot inventory does not match manifest')
    result = {}
    for name, artifact in expected.items():
        public_path(name)
        value = entries['site/' + name]
        if artifact.get('bytes') != len(value) or artifact.get('sha256') != hashlib.sha256(value).hexdigest():
            raise ValueError('snapshot bytes do not match manifest')
        result[name] = value
    if sum(map(len, result.values())) > MAX_SITE:
        raise ValueError('snapshot exceeds byte limit')
    return result



class GitHub:
    def __init__(self, token):
        self.environment = {k: v for k, v in os.environ.items() if k not in {'GH_TOKEN', 'GITHUB_TOKEN', 'GH_DEBUG', 'GIT_TRACE', 'GIT_TRACE_CURL', 'GIT_CURL_VERBOSE', 'GIT_CONFIG_COUNT'}}
        self.environment.update(GH_TOKEN=token, GH_HOST='github.com', GH_PROMPT_DISABLED='true', GIT_TERMINAL_PROMPT='0')

    def command(self, arguments, directory=None, data=None):
        result = subprocess.run(arguments, cwd=directory, env=self.environment, input=data, capture_output=True, timeout=90)
        if result.returncode:
            raise ValueError('GitHub publication command failed; inspect repository state before retrying')
        return result.stdout.decode().strip()

    def api(self, path, method='GET', fields=None):
        args = ['gh', 'api', '-H', 'X-GitHub-Api-Version: 2026-03-10', '--method', method, 'repos/' + REPOSITORY + '/' + path]
        if fields is not None:
            args += ['--input', '-']
        output = self.command(args, data=json.dumps(fields).encode() if fields is not None else None)
        return json.loads(output) if output else {}

    def git(self, directory, *args):
        return self.command(['git', '-c', 'core.hooksPath=/dev/null', '-c', 'credential.helper=',
                             '-c', 'credential.helper=!gh auth git-credential', '-c', 'protocol.file.allow=never',
                             *args], directory)


def check_runs(github, head, branch):
    ci = github.api('actions/workflows/ci.yml/runs?head_sha=' + head + '&event=pull_request&per_page=100')['workflow_runs']
    runs = [run for run in ci if run.get('head_sha') == head and run.get('head_branch') == branch
            and run.get('head_repository', {}).get('full_name') == REPOSITORY]
    if not runs:
        return False
    newest = max(runs, key=lambda run: run['id'])
    if newest['status'] != 'completed':
        return False
    if newest['conclusion'] != 'success':
        raise ValueError('publication CI failed')
    checks = github.api('actions/runs/' + str(newest['id']) + '/jobs?filter=latest&per_page=100')['jobs']
    if len(checks) >= 100:
        raise ValueError('too many publication checks to establish completion')
    if any(check['status'] != 'completed' for check in checks):
        return False
    if any(check['conclusion'] not in {'success', 'skipped', 'neutral'} for check in checks):
        raise ValueError('a publication check failed')
    required = {'build-and-test', 'dockerfile-noble-pin-guard', 'docker-build'}
    trusted = {check['name'] for check in checks if check['conclusion'] in {'success', 'skipped'}}
    if not required.issubset(trusted):
        return False
    return True


class Publisher:
    def __init__(self, runtime, github):
        self.runtime = runtime
        self.github = github
        self.root = runtime.root / 'state/pages-publisher'
        self.root.mkdir(mode=0o700, exist_ok=True)
        self.checkout = self.root / 'checkout'
        self.state_path = self.root / 'worker.json'

    def helper(self, script, args=(), data=None, writable=False):
        config = self.runtime.config
        generation = config.get('storageGeneration')
        volume = 'ctc-manager_' + (generation + '_' if generation else '') + 'data'
        clean_environment = {key: value for key, value in os.environ.items() if key not in {'GH_TOKEN', 'GITHUB_TOKEN'}}
        return self.runtime.docker(['run', '--rm', '-i', '--network', 'none', '--read-only', '--user', 'ctc',
                '--cap-drop', 'ALL', '--security-opt', 'no-new-privileges', '--memory', '128m', '--pids-limit', '32',
                '--mount', 'type=volume,src=' + volume + ',dst=/data,volume-nocopy' + ('' if writable else ',readonly'),
                '--entrypoint', '/bin/sh', config['appImage'], '-ec', script, 'publisher', *args],
                input=data, env=clean_environment)

    def metadata(self, name):
        if name not in {'runtime.json', 'request.json'}:
            raise ValueError('unsupported publisher metadata')
        raw = self.helper('test ! -L /data/site-publication; cd /data/site-publication; if [ ! -e "$1" ]; then printf null; exit 0; fi; test -f "$1"; test ! -L "$1"; test "$(wc -c < "$1")" -lt 4194304; cat "$1"', [name])
        return json.loads(raw)

    def acknowledge(self, request, state):
        status = dict(format=1, requestId=request['id'], snapshotId=request['snapshotId'],
                      manifestSha256=request['manifestSha256'], attempt=request['attempt'], stage=state['stage'],
                      detail=state.get('detail', ''), branch=state.get('branch'), headSha=state.get('headSha'),
                      pr=state.get('pr'), mergeSha=state.get('mergeSha'), runId=state.get('runId'),
                      updatedAt=dt.datetime.now(dt.timezone.utc).isoformat())
        self.helper('umask 077; test ! -L /data/site-publication; cd /data/site-publication; '
                    "temporary=$(mktemp .host-status.XXXXXXXX); trap 'rm -f \"$temporary\"' EXIT; "
                    'cat > "$temporary"; chmod 600 "$temporary"; sync -f "$temporary"; '
                    'mv -T "$temporary" status.json; sync -f .', data=json.dumps(status).encode(), writable=True)

    def save(self, state):
        atomic_json(self.state_path, state)

    def tick(self):
        if self.runtime.config.get('outboundPaused', False):
            return
        container = self.runtime.container('app')
        if container['State'].get('Health', {}).get('Status') != 'healthy':
            return
        marker = self.metadata('runtime.json')
        if marker is None or marker.get('enabled') is False:
            return
        validate_boot(marker, container['State']['StartedAt'])
        request = self.metadata('request.json')
        if request is None or request.get('approved') is False:
            return
        request = validate_request(request)
        state = json.loads(self.state_path.read_text()) if self.state_path.exists() else None
        binding = {key: request[key] for key in ['id', 'snapshotId', 'manifestSha256']}
        if state and any(state.get(key) != value for key, value in binding.items()):
            if state['stage'] not in {'published', 'no-change'}:
                raise ValueError('previous remote publication needs reconciliation')
            state = None
        if state is None:
            state = dict(**binding, stage='preparing', branch='site/publish-' + request['id'])
            self.save(state)
        try:
            if state['stage'] == 'preparing':
                self.prepare(request, state)
            elif state['stage'] == 'checking':
                self.merge(state)
            elif state['stage'] == 'deploying':
                self.deployment(state)
            elif state['stage'] == 'failed':
                if request['attempt'] > state.get('attempt', 0):
                    state['stage'] = state.get('resumeStage', 'preparing')
                    state['detail'] = 'Explicit retry accepted. Rechecking the same remote request.'
        except (ValueError, OSError, KeyError, subprocess.TimeoutExpired) as error:
            state['resumeStage'] = state['stage']
            state['stage'] = 'failed'
            state['detail'] = 'Publication stopped. Review the pull request and deployment before retrying this snapshot.'
            self.save(state)
            self.acknowledge(request, state)
            raise ValueError(state['detail']) from error
        state['attempt'] = request['attempt']
        self.save(state)
        self.acknowledge(request, state)

    def prepare(self, request, state):
        identifier = identity(request['snapshotId'])
        raw = self.helper('test ! -L /data/site-publication; cd /data/site-publication/snapshots; '
                          'test ! -L "$1"; cd "$1"; tar -cf - manifest.json site', [identifier])
        files = validate_snapshot(raw, request)
        if not self.checkout.exists():
            self.github.git(self.root, 'clone', '--no-checkout', '--no-local', REMOTE, str(self.checkout))
        if self.github.git(self.checkout, 'remote', 'get-url', 'origin') != REMOTE:
            raise ValueError('publisher checkout has an unexpected remote')
        self.github.git(self.checkout, 'fetch', '--no-tags', 'origin', 'master')
        base = self.github.git(self.checkout, 'rev-parse', 'FETCH_HEAD')
        if state.get('baseSha') and state['baseSha'] != base:
            raise ValueError('publication base changed; reconcile the existing branch')
        state['baseSha'] = base
        self.save(state)
        # A retry inspects an existing deterministic branch before creating any remote object.
        refs = self.github.git(self.checkout, 'ls-remote', '--heads', 'origin', state['branch'])
        if refs:
            self.github.git(self.checkout, 'fetch', '--no-tags', 'origin', state['branch'])
            head = self.github.git(self.checkout, 'rev-parse', 'FETCH_HEAD')
            state['indexSha256'] = self.verify_commit(head, base, files)
            state['headSha'] = head
        else:
            if state.get('headSha'):
                raise ValueError('published branch disappeared; reconcile before retrying')
            self.github.git(self.checkout, 'switch', '--detach', base)
            site = self.checkout / 'docs/site'
            if (self.checkout / 'docs').is_symlink():
                raise ValueError('documentation directory is linked')
            if site.exists() or site.is_symlink():
                if site.is_symlink():
                    raise ValueError('website directory is linked')
                shutil.rmtree(site)
            if (self.checkout / 'docs').is_symlink():
                raise ValueError('documentation directory is linked')
            site.mkdir(parents=True)
            for name, value in files.items():
                target = site / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(value)
            self.github.git(self.checkout, 'add', '--all', '--', 'docs/site')
            if not self.github.git(self.checkout, 'diff', '--cached', '--name-only'):
                state.update(stage='deploying', mergeSha=base, needsDispatch=True,
                             siteTree=self.github.git(self.checkout, 'rev-parse', base + ':docs/site'),
                             indexSha256=hashlib.sha256(files['index.html']).hexdigest(),
                             detail='Git already matches. Verifying actual Pages publication.')
                return
            self.github.git(self.checkout, '-c', 'user.name=CTC Pages Publisher', '-c', 'user.email=ctc-pages@users.noreply.github.com',
                            'commit', '-m', 'docs(site): publish approved snapshot ' + request['snapshotId'])
            head = self.github.git(self.checkout, 'rev-parse', 'HEAD')
            state['indexSha256'] = self.verify_commit(head, base, files)
            state['headSha'] = head
            state['siteTree'] = self.github.git(self.checkout, 'rev-parse', head + ':docs/site')
            self.save(state)
            self.github.git(self.checkout, 'push', 'origin', head + ':refs/heads/' + state['branch'])
        state['siteTree'] = self.github.git(self.checkout, 'rev-parse', state['headSha'] + ':docs/site')
        prs = self.github.api('pulls?state=all&head=jegr78:' + state['branch'] + '&base=master&per_page=100')
        if len(prs) > 1:
            raise ValueError('ambiguous publication pull requests')
        if not prs:
            pr = self.github.api('pulls', 'POST', dict(title='Publish approved CTC website snapshot', head=state['branch'], base='master',
                    body='Owner-approved immutable public website snapshot. Only docs/site changes are permitted.'))
        else:
            pr = prs[0]
        self.validate_pr(pr, state)
        state.update(pr=pr['number'], stage='checking', detail='Waiting for repository checks for this exact snapshot.')

    def verify_commit(self, head, base, files):
        if not re.fullmatch('[a-f0-9]{40}', head) or not re.fullmatch('[a-f0-9]{40}', base):
            raise ValueError('invalid Git commit')
        if self.github.git(self.checkout, 'rev-list', '--parents', '-n', '1', head) != head + ' ' + base:
            raise ValueError('publication commit does not have the recorded base')
        paths = self.github.git(self.checkout, 'diff', '--name-only', base, head).splitlines()
        if not paths or any(not path.startswith('docs/site/') for path in paths):
            raise ValueError('publication contains changes outside docs/site')
        tree = self.github.git(self.checkout, 'ls-tree', '-rz', head + ':docs/site')
        actual = {}
        for record in tree.split('\0'):
            if not record:
                continue
            metadata, name = record.split('\t', 1)
            mode, kind, blob = metadata.split(' ')
            if mode != '100644' or kind != 'blob':
                raise ValueError('Git snapshot requires regular non-executable files')
            actual[name] = blob
        expected = {name: hashlib.sha1(b'blob ' + str(len(value)).encode() + b'\0' + value).hexdigest()
                    for name, value in files.items()}
        if actual != expected:
            raise ValueError('Git website tree differs from approved snapshot')
        return hashlib.sha256(files['index.html']).hexdigest()

    @staticmethod
    def validate_pr(pr, state):
        if (pr['head']['sha'] != state['headSha'] or pr['head']['ref'] != state['branch']
                or pr['head']['repo']['full_name'] != REPOSITORY or pr['base']['repo']['full_name'] != REPOSITORY
                or pr['base']['ref'] != 'master'):
            raise ValueError('publication pull request does not match recorded head')

    def verify_merged_tree(self, state, commit):
        if not re.fullmatch('[a-f0-9]{40}', commit):
            raise ValueError('invalid Pages merge commit')
        self.github.git(self.checkout, 'fetch', '--no-tags', 'origin', commit)
        if self.github.git(self.checkout, 'rev-parse', commit + ':docs/site') != state['siteTree']:
            raise ValueError('merged website differs from the approved snapshot')

    def merge(self, state):
        pr = self.github.api('pulls/' + str(state['pr']))
        self.validate_pr(pr, state)
        if pr.get('merged'):
            self.verify_merged_tree(state, pr['merge_commit_sha'])
            state.update(stage='deploying', mergeSha=pr['merge_commit_sha'])
            return
        if pr['state'] != 'open':
            raise ValueError('publication pull request is closed without merging')
        if self.github.api('git/ref/heads/master')['object']['sha'] != state['baseSha']:
            raise ValueError('master changed; reconcile the publication pull request')
        if not check_runs(self.github, state['headSha'], state['branch']):
            return
        self.github.api('pulls/' + str(state['pr']) + '/merge', 'PUT', dict(sha=state['headSha'], merge_method='squash',
                        commit_title='docs(site): publish approved CTC website snapshot',
                        commit_message='CTC-Site-Tree: ' + state['siteTree']))
        pr = self.github.api('pulls/' + str(state['pr']))
        if not pr.get('merged'):
            raise ValueError('merge result is not confirmed')
        self.verify_merged_tree(state, pr['merge_commit_sha'])
        state.update(stage='deploying', mergeSha=pr['merge_commit_sha'], detail='Merged. Waiting for the matching Pages deployment.')

    def deployment(self, state):
        commit = state['mergeSha']
        self.verify_merged_tree(state, commit)
        if state.get('dispatchRunId'):
            run = self.github.api('actions/runs/' + str(state['dispatchRunId']))
            if (run.get('event') != 'workflow_dispatch' or run.get('path') != '.github/workflows/deploy-site.yml'
                    or run.get('head_repository', {}).get('full_name') != REPOSITORY):
                raise ValueError('unexpected dispatched Pages workflow')
            runs = [run]
        else:
            runs = self.github.api('actions/workflows/deploy-site.yml/runs?head_sha=' + commit + '&event=push&per_page=100')['workflow_runs']
            runs = [run for run in runs if run['head_sha'] == commit and run['head_branch'] == 'master'
                and run.get('head_repository', {}).get('full_name') == REPOSITORY]
        if not runs:
            if state.get('needsDispatch'):
                if state.get('dispatchRequested'):
                    raise ValueError('ambiguous Pages dispatch needs reconciliation')
                state['dispatchRequested'] = True
                self.save(state)
                result = self.github.api('actions/workflows/deploy-site.yml/dispatches', 'POST',
                        dict(ref='master', inputs=dict(commit=commit, site_tree=state['siteTree'], request_id=state['id'])))
                state['dispatchRunId'] = result['workflow_run_id']
                self.save(state)
            return
        run = max(runs, key=lambda item: item['id'])
        state['runId'] = run['id']
        if run['status'] != 'completed':
            return
        if run['conclusion'] != 'success':
            raise ValueError('matching Pages deployment failed')
        import urllib.request
        with urllib.request.urlopen(PUBLIC_URL + 'index.html?ctc=' + commit, timeout=30) as response:
            content = response.read(32 * 1024 * 1024 + 1)
            if response.geturl().split('?')[0] != PUBLIC_URL + 'index.html':
                raise ValueError('public Pages response redirected unexpectedly')
        if hashlib.sha256(content).hexdigest() != state['indexSha256']:
            return
        state.update(stage='published', detail='The matching Pages deployment succeeded and the public index matches the approved snapshot.')


def main():
    import argparse
    from config import private_path
    from recovery import NativeRuntime
    parser = argparse.ArgumentParser()
    parser.add_argument('--directory', required=True)
    parser.add_argument('--deploy-root', required=True)
    args = parser.parse_args()
    runtime = NativeRuntime(args.directory, args.deploy_root)
    token_file = private_path(runtime.root / 'secrets/pages-token')
    token = token_file.read_text().strip()
    if not token or '\n' in token or len(token) > 1024:
        raise ValueError('invalid private Pages token file')
    Publisher(runtime, GitHub(token)).tick()


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, KeyError, TypeError, subprocess.TimeoutExpired, tarfile.TarError) as error:
        raise SystemExit('CTC Pages: publisher stopped; inspect the private request and repository state') from error
