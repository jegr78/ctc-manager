import hashlib
import io
import json
from pathlib import Path
import sys
import tarfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import publication


class SnapshotTest(unittest.TestCase):
    def setUp(self):
        self.id = '30abccf2-0e8d-4f65-bd03-5db05d539b08'
        self.request = dict(format=1, id=self.id, snapshotId=self.id, attempt=1, approved=True)
        self.files = {'index.html': b'<html>public A</html>', 'assets/style.css': b'body{}'}
        self.manifest = dict(format=1, id=self.id, createdAt='2026-11-01T10:00:00Z', appVersion='test',
                             files={k: dict(bytes=len(v), sha256=hashlib.sha256(v).hexdigest()) for k, v in self.files.items()})

    def archive(self, extra=None):
        raw = json.dumps(self.manifest).encode()
        self.request['manifestSha256'] = hashlib.sha256(raw).hexdigest()
        output = io.BytesIO()
        with tarfile.open(fileobj=output, mode='w') as archive:
            entries = {'manifest.json': raw, **{'site/' + k: v for k, v in self.files.items()}, **(extra or {})}
            for name, content in entries.items():
                info = tarfile.TarInfo(name)
                info.size = len(content)
                archive.addfile(info, io.BytesIO(content))
        return output.getvalue()

    def test_exact_snapshot_exports_only_manifest_files(self):
        self.assertEqual(publication.validate_snapshot(self.archive(), self.request), self.files)

    def test_unlisted_private_and_traversing_entries_are_refused(self):
        for name in ['site/.env', '../index.html', 'site/extra.html', '/etc/passwd']:
            with self.subTest(name=name), self.assertRaises(ValueError):
                publication.validate_snapshot(self.archive({name: b'secret'}), self.request)

    def test_changed_content_and_mismatching_manifest_are_refused(self):
        self.files['index.html'] = b'changed'
        with self.assertRaises(ValueError):
            publication.validate_snapshot(self.archive(), self.request)
        archive = self.archive()
        self.request['manifestSha256'] = '0' * 64
        with self.assertRaises(ValueError):
            publication.validate_snapshot(archive, self.request)

    def test_request_identity_approval_and_attempt_are_validated(self):
        self.archive()
        for key, value in [('id', '../branch'), ('snapshotId', '-invalid'), ('approved', False), ('attempt', 0), ('attempt', True)]:
            request = {**self.request, key: value}
            with self.subTest(key=key, value=value), self.assertRaises(ValueError):
                publication.validate_request(request)

    def test_approved_manifest_cannot_include_unknown_extension(self):
        self.files['secrets.env'] = b'private'
        self.manifest['files']['secrets.env'] = dict(bytes=7, sha256=hashlib.sha256(b'private').hexdigest())
        with self.assertRaises(ValueError):
            publication.validate_snapshot(self.archive(), self.request)

    def test_boot_marker_must_be_current_and_enabled(self):
        for enabled, stamp in [(False, '2026-11-01T10:01:00Z'), (True, '2026-11-01T09:00:00Z')]:
            with self.subTest(enabled=enabled, stamp=stamp), self.assertRaises(ValueError):
                publication.validate_boot(dict(format=1, enabled=enabled, startedAt=stamp), '2026-11-01T10:00:00Z')
        publication.validate_boot(dict(format=1, enabled=True, startedAt='2026-11-01T10:01:00Z'), '2026-11-01T10:00:00Z')


class WorkflowTest(unittest.TestCase):
    def setUp(self):
        from unittest.mock import Mock
        self.publisher = object.__new__(publication.Publisher)
        self.publisher.github = Mock()
        self.publisher.github.git.return_value = 'f' * 40
        self.publisher.checkout = Path('/fixture')
        self.publisher.save = Mock()
        self.state = dict(id='30abccf2-0e8d-4f65-bd03-5db05d539b08', headSha='a' * 40,
                          baseSha='b' * 40, branch='site/publish-test', pr=12, stage='checking', siteTree='f' * 40)
        self.pr = dict(head=dict(sha='a' * 40, ref='site/publish-test', repo=dict(full_name=publication.REPOSITORY)),
                       base=dict(ref='master', repo=dict(full_name=publication.REPOSITORY)), state='open', merged=False)

    def test_changed_pr_head_never_merges(self):
        self.pr['head']['sha'] = 'c' * 40
        self.publisher.github.api.return_value = self.pr
        with self.assertRaisesRegex(ValueError, 'recorded head'):
            self.publisher.merge(self.state)
        self.assertEqual(self.publisher.github.api.call_count, 1)

    def test_already_merged_request_reconciles_without_second_merge(self):
        self.pr.update(merged=True, merge_commit_sha='d' * 40)
        self.publisher.github.api.return_value = self.pr
        self.publisher.merge(self.state)
        self.assertEqual(self.state['stage'], 'deploying')
        self.assertEqual(self.state['mergeSha'], 'd' * 40)
        self.assertEqual(self.publisher.github.api.call_count, 1)

    def test_changed_base_stops_before_checks_or_merge(self):
        self.publisher.github.api.side_effect = [self.pr, dict(object=dict(sha='e' * 40))]
        with self.assertRaisesRegex(ValueError, 'master changed'):
            self.publisher.merge(self.state)
        self.assertEqual(self.publisher.github.api.call_count, 2)

    def test_merge_uses_exact_sha_only_after_trusted_successful_ci(self):
        run = dict(id=1, head_sha='a' * 40, head_branch=self.state['branch'], head_repository=dict(full_name=publication.REPOSITORY), status='completed', conclusion='success')
        check = dict(name='build-and-test', status='completed', conclusion='success', app=dict(slug='github-actions'))
        merged = {**self.pr, 'merged': True, 'merge_commit_sha': 'd' * 40}
        self.publisher.github.api.side_effect = [self.pr, dict(object=dict(sha='b' * 40)), dict(workflow_runs=[run]), dict(jobs=[{**check, 'name': name} for name in ['build-and-test', 'dockerfile-noble-pin-guard', 'docker-build']]), dict(merged=True), merged]
        self.publisher.merge(self.state)
        self.assertEqual(self.state['stage'], 'deploying')
        args = self.publisher.github.api.call_args_list[-2].args
        self.assertEqual(args[1], 'PUT')
        self.assertEqual(args[2]['sha'], 'a' * 40)

    def test_failed_or_foreign_ci_cannot_authorize_merge(self):
        for conclusion, repository in [('failure', publication.REPOSITORY), ('success', 'attacker/fork')]:
            run = dict(id=1, head_sha='a' * 40, head_branch=self.state['branch'], head_repository=dict(full_name=repository), status='completed', conclusion=conclusion)
            self.publisher.github.api.return_value = dict(workflow_runs=[run])
            if conclusion == 'failure':
                with self.assertRaisesRegex(ValueError, 'CI failed'):
                    publication.check_runs(self.publisher.github, 'a' * 40, self.state['branch'])
            else:
                self.assertFalse(publication.check_runs(self.publisher.github, 'a' * 40, self.state['branch']))

    def test_successful_merge_with_failed_pages_is_not_published(self):
        self.state.update(stage='deploying', mergeSha='d' * 40)
        self.publisher.github.api.return_value = dict(workflow_runs=[dict(id=5, head_sha='d' * 40, head_branch='master', head_repository=dict(full_name=publication.REPOSITORY), status='completed', conclusion='failure')])
        with self.assertRaisesRegex(ValueError, 'Pages deployment failed'):
            self.publisher.deployment(self.state)
        self.assertEqual(self.state['stage'], 'deploying')

    def test_success_for_another_commit_is_ignored(self):
        self.state.update(stage='deploying', mergeSha='d' * 40)
        self.publisher.github.api.return_value = dict(workflow_runs=[dict(id=5, head_sha='e' * 40, head_branch='master', head_repository=dict(full_name=publication.REPOSITORY), status='completed', conclusion='success')])
        self.publisher.deployment(self.state)
        self.assertEqual(self.state['stage'], 'deploying')


class GitSnapshotTest(unittest.TestCase):
    def setUp(self):
        import tempfile
        import subprocess
        from unittest.mock import Mock
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.environment = dict(publication.os.environ, GIT_CONFIG_NOSYSTEM='1', GIT_CONFIG_GLOBAL='/dev/null')
        def git(*args):
            return subprocess.run(['git', '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid', *args], cwd=self.root, env=self.environment, capture_output=True, check=True).stdout.decode().strip()
        self.git = git
        git('init', '--initial-branch=master')
        (self.root / 'README.md').write_text('existing private source')
        git('add', '.')
        git('commit', '-m', 'test base')
        self.base = git('rev-parse', 'HEAD')
        (self.root / 'docs/site').mkdir(parents=True)
        self.publisher = object.__new__(publication.Publisher)
        self.publisher.checkout = self.root
        self.publisher.github = Mock()
        self.publisher.github.git.side_effect = lambda directory, *args: git(*args)

    def test_exact_git_tree_returns_index_hash(self):
        (self.root / 'docs/site/index.html').write_bytes(b'public')
        self.git('add', '.')
        self.git('commit', '-m', 'snapshot')
        result = self.publisher.verify_commit(self.git('rev-parse', 'HEAD'), self.base, {'index.html': b'public'})
        self.assertEqual(result, hashlib.sha256(b'public').hexdigest())

    def test_git_symlink_cannot_replace_snapshot_file(self):
        (self.root / 'docs/site/index.html').symlink_to('../../README.md')
        self.git('add', '.')
        self.git('commit', '-m', 'linked snapshot')
        with self.assertRaisesRegex(ValueError, 'regular'):
            self.publisher.verify_commit(self.git('rev-parse', 'HEAD'), self.base, {'index.html': b'../../README.md'})


class ProxyRecoveryTest(unittest.TestCase):
    def test_restore_normalizes_https_forwarding_without_replaying_publication_flag(self):
        import tempfile
        from recovery import probe_environment
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'app.env'
            path.write_text('SPRING_SECURITY_USER_NAME=owner\nSPRING_SECURITY_USER_PASSWORD=fixture\nCTC_SITE_PUBLICATION_ENABLED=true\n')
            values = probe_environment(path, 'database-fixture')
            self.assertEqual(values['SERVER_FORWARD_HEADERS_STRATEGY'], 'framework')
            self.assertNotIn('CTC_SITE_PUBLICATION_ENABLED', values)


class IdleWorkerTest(unittest.TestCase):
    def test_no_request_is_an_idle_tick_without_git_or_status_write(self):
        from unittest.mock import Mock
        publisher = object.__new__(publication.Publisher)
        publisher.runtime = Mock(config={"outboundPaused": False})
        publisher.runtime.container.return_value = {"State": {"Health": {"Status": "healthy"}, "StartedAt": "2026-11-01T10:00:00Z"}}
        publisher.metadata = Mock(side_effect=[dict(format=1, enabled=True, startedAt="2026-11-01T10:01:00Z"), None])
        publisher.github = Mock()
        publisher.tick()
        publisher.github.api.assert_not_called()


class RequiredChecksTest(unittest.TestCase):
    def test_required_docker_checks_cannot_be_missing_even_for_owner_token(self):
        from unittest.mock import Mock
        github = Mock()
        run = dict(id=1, head_sha='a' * 40, head_branch='site/test', head_repository=dict(full_name=publication.REPOSITORY), status='completed', conclusion='success')
        check = dict(name='build-and-test', status='completed', conclusion='success', app=dict(slug='github-actions'))
        github.api.side_effect = [dict(workflow_runs=[run]), dict(jobs=[check])]
        self.assertFalse(publication.check_runs(github, 'a' * 40, 'site/test'))


class UnpublishedMatchingTreeTest(GitSnapshotTest):
    def test_matching_git_tree_still_waits_for_actual_pages_publication(self):
        from unittest.mock import Mock
        fixture = SnapshotTest()
        fixture.setUp()
        archive = fixture.archive()
        for name, value in fixture.files.items():
            target = self.root / 'docs/site' / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(value)
        self.git('add', '.')
        self.git('commit', '-m', 'same files already in Git, no Pages deployment yet')
        base = self.git('rev-parse', 'HEAD')
        self.publisher.root = self.root.parent
        self.publisher.helper = Mock(return_value=archive)
        self.publisher.save = Mock()
        def git(directory, *args):
            if args[:3] == ('remote', 'get-url', 'origin'): return publication.REMOTE
            if args[0] in {'fetch', 'ls-remote'}: return ''
            if args == ('rev-parse', 'FETCH_HEAD'): return base
            return self.git(*args)
        self.publisher.github.git.side_effect = git
        state = dict(id=fixture.id, branch='site/publish-' + fixture.id, stage='preparing')
        self.publisher.prepare(fixture.request, state)
        self.assertEqual(state['stage'], 'deploying')
        self.assertEqual(state['mergeSha'], base)
        self.assertEqual(state['indexSha256'], hashlib.sha256(fixture.files['index.html']).hexdigest())

    def test_git_normalization_cannot_turn_different_snapshot_bytes_into_no_change(self):
        from unittest.mock import Mock
        fixture = SnapshotTest()
        fixture.setUp()
        fixture.files['index.html'] = b'approved\r\n'
        fixture.manifest['files']['index.html'] = dict(bytes=10, sha256=hashlib.sha256(b'approved\r\n').hexdigest())
        archive = fixture.archive()
        (self.root / '.gitattributes').write_text('docs/site/** text eol=lf\n')
        for name, value in fixture.files.items():
            target = self.root / 'docs/site' / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(value)
        self.git('add', '.')
        self.git('commit', '-m', 'same files already in Git, no Pages deployment yet')
        base = self.git('rev-parse', 'HEAD')
        self.publisher.root = self.root.parent
        self.publisher.helper = Mock(return_value=archive)
        self.publisher.save = Mock()
        def git(directory, *args):
            if args[:3] == ('remote', 'get-url', 'origin'): return publication.REMOTE
            if args[0] in {'fetch', 'ls-remote'}: return ''
            if args == ('rev-parse', 'FETCH_HEAD'): return base
            return self.git(*args)
        self.publisher.github.git.side_effect = git
        state = dict(id=fixture.id, branch='site/publish-' + fixture.id, stage='preparing')
        with self.assertRaisesRegex(ValueError, 'tree differs'):
            self.publisher.prepare(fixture.request, state)


class PagesArtifactGuardTest(GitSnapshotTest):
    def test_concurrent_site_addition_cannot_pass_artifact_guard(self):
        (self.root / 'docs/site/index.html').write_bytes(b'approved')
        self.git('add', '.')
        self.git('commit', '-m', 'approved')
        expected = self.git('rev-parse', 'HEAD:docs/site')
        (self.root / 'docs/site/unapproved.html').write_bytes(b'unapproved')
        self.git('add', '.')
        self.git('commit', '-m', 'concurrent addition')
        import subprocess
        script = Path(__file__).resolve().parents[2] / 'scripts/ci/pages-snapshot.py'
        result = subprocess.run([sys.executable, str(script)], cwd=self.root,
                                env={**self.environment, 'CTC_SITE_TREE': expected}, capture_output=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn(b'differs from the approved snapshot', result.stderr)

    def test_exact_approved_tree_passes_artifact_guard(self):
        (self.root / 'docs/site/index.html').write_bytes(b'approved')
        self.git('add', '.')
        self.git('commit', '-m', 'approved')
        expected = self.git('rev-parse', 'HEAD:docs/site')
        import subprocess
        script = Path(__file__).resolve().parents[2] / 'scripts/ci/pages-snapshot.py'
        result = subprocess.run([sys.executable, str(script)], cwd=self.root,
                                env={**self.environment, 'CTC_SITE_TREE': expected}, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stderr)


class DispatchTest(WorkflowTest):
    def test_matching_undeployed_tree_dispatches_exact_approved_commit_and_records_run(self):
        from unittest.mock import Mock
        self.state.update(stage='deploying', mergeSha='d' * 40, needsDispatch=True)
        self.publisher.save = Mock()
        self.publisher.github.api.side_effect = [dict(workflow_runs=[]), dict(workflow_run_id=987)]
        self.publisher.deployment(self.state)
        self.assertEqual(self.state['dispatchRunId'], 987)
        args = self.publisher.github.api.call_args.args
        self.assertEqual(args[1], 'POST')
        self.assertEqual(args[2]['inputs'], dict(commit='d' * 40, site_tree='f' * 40, request_id=self.state['id']))
        self.assertTrue(self.state['dispatchRequested'])
        self.assertEqual(self.state['stage'], 'deploying')

    def test_ambiguous_dispatch_is_not_automatically_repeated(self):
        self.state.update(stage='deploying', mergeSha='d' * 40, needsDispatch=True, dispatchRequested=True)
        self.publisher.github.api.side_effect = [dict(workflow_runs=[]), dict(workflow_run_id=123)]
        with self.assertRaisesRegex(ValueError, 'ambiguous Pages dispatch'):
            self.publisher.deployment(self.state)
        self.assertEqual(self.publisher.github.api.call_count, 1)

    def test_changed_merged_tree_cannot_reach_pages_success_or_dispatch(self):
        self.state.update(stage='deploying', mergeSha='d' * 40, needsDispatch=True)
        self.publisher.github.git.return_value = 'e' * 40
        self.publisher.github.api.side_effect = [dict(workflow_runs=[]), dict(workflow_run_id=42)]
        with self.assertRaisesRegex(ValueError, 'differs from the approved snapshot'):
            self.publisher.deployment(self.state)
        self.publisher.github.api.assert_not_called()


class BoundaryGuardTest(SnapshotTest):
    def test_manifest_hash_is_checked_even_when_all_files_are_valid(self):
        archive = self.archive()
        self.request['manifestSha256'] = '0' * 64
        with self.assertRaises(ValueError):
            publication.validate_snapshot(archive, self.request)

    def test_approved_private_or_traversing_paths_are_refused(self):
        for name in ['../outside.html', '/outside.html', 'nested/../outside.html', '.private.html', 'assets/.private.html', 'nested\\outside.html']:
            fixture = SnapshotTest()
            fixture.setUp()
            fixture.files[name] = b'private'
            fixture.manifest['files'][name] = dict(bytes=7, sha256=hashlib.sha256(b'private').hexdigest())
            with self.subTest(name=name), self.assertRaises(ValueError):
                publication.validate_snapshot(fixture.archive(), fixture.request)

    def test_aggregate_byte_limit_is_enforced(self):
        from unittest.mock import patch
        archive = self.archive()
        with patch.object(publication, 'MAX_SITE', 4), self.assertRaises(ValueError):
            publication.validate_snapshot(archive, self.request)

    def test_pull_request_binding_rejects_head_base_and_repository_changes(self):
        state = dict(headSha='a' * 40, branch='site/test')
        for field, value in [('sha', 'b' * 40), ('ref', 'other'), ('repository', 'other/repo')]:
            pr = dict(head=dict(sha='a' * 40, ref='site/test', repo=dict(full_name=publication.REPOSITORY)),
                      base=dict(ref='master', repo=dict(full_name=publication.REPOSITORY)))
            if field == 'repository':
                pr['base']['repo']['full_name'] = value
            else:
                pr['head'][field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                publication.Publisher.validate_pr(pr, state)


class DurableStateTest(unittest.TestCase):
    def test_state_replacement_is_private_complete_and_leaves_no_partial_file(self):
        import tempfile
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            path = root / 'worker.json'
            publication.atomic_json(path, dict(stage='preparing', id='first'))
            publication.atomic_json(path, dict(stage='checking', id='second'))
            self.assertEqual(json.loads(path.read_text()), dict(stage='checking', id='second'))
            self.assertEqual(path.stat().st_mode & 0o777, 0o600)
            self.assertEqual([file.name for file in root.iterdir()], ['worker.json'])


class ApiVersionTest(unittest.TestCase):
    def test_pull_metadata_preserves_merge_sha_and_dispatch_returns_run_id(self):
        from unittest.mock import Mock
        github = object.__new__(publication.GitHub)
        github.command = Mock(return_value='{}')
        for endpoint, expected in [('pulls/1', '2022-11-28'), ('actions/workflows/deploy-site.yml/dispatches', '2026-03-10')]:
            github.api(endpoint)
            arguments = github.command.call_args.args[0]
            self.assertEqual(arguments[arguments.index('-H') + 1], 'X-GitHub-Api-Version: ' + expected)
