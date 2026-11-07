import base64
import contextlib
import hashlib
import importlib.util
import os
import json
from pathlib import Path
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'ci/test-partitions.py'


class PartitionTests(unittest.TestCase):
    def setUp(self):
        spec = importlib.util.spec_from_file_location('partitions', SCRIPT)
        self.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.module)
        self.roots = [dict(name='CoreTest', stage='unit', nodes=[dict(id='core', container=False)]),
                      dict(name='H2IT', stage='integration', nodes=[dict(id='h2', container=False)]),
                      dict(name='FirstMariaDbIT', stage='integration', nodes=[dict(id='db1', container=False)]),
                      dict(name='SecondMariaDbIT', stage='integration', nodes=[dict(id='db2', container=False)]),
                      dict(name='WebE2ETest', stage='browser', nodes=[dict(id='web', container=False)])]
        self.plan = self.module.assign(self.roots, {'FirstMariaDbIT': 8, 'SecondMariaDbIT': 3})

    def test_complete_balanced_assignment(self):
        self.assertEqual(set(self.plan), {'core', 'mariadb-1', 'mariadb-2', 'browser'})
        names = [root['name'] for roots in self.plan.values() for root in roots]
        self.assertEqual(sorted(names), sorted(root['name'] for root in self.roots))
        self.assertEqual([r['name'] for r in self.plan['mariadb-1']], ['FirstMariaDbIT'])

    def test_missing_duplicate_or_unknown_inventory_is_rejected(self):
        for roots in [self.roots[:-1], self.roots + [self.roots[0]],
                      [dict(root, stage='unknown') for root in self.roots]]:
            with self.subTest(roots=roots), self.assertRaises(ValueError):
                self.module.assign(roots, {})

    def test_exact_source_classes_and_all_workers_are_required(self):
        expected = dict(source='a', tree='b', classes='c', run='1', attempt='1')
        receipts = {key: dict(identity=expected, group=key, passed=True) for key in self.plan}
        self.module.require_workers(self.plan, receipts, expected)
        for field in expected:
            broken = json.loads(json.dumps(receipts))
            broken['browser']['identity'][field] = 'different'
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.module.require_workers(self.plan, broken, expected)
        for key in self.plan:
            with self.subTest(key=key), self.assertRaises(ValueError):
                self.module.require_workers(self.plan, {k: v for k, v in receipts.items() if k != key}, expected)
        receipts['core']['passed'] = False
        with self.assertRaises(ValueError):
            self.module.require_workers(self.plan, receipts, expected)

    def test_missing_duplicate_failed_or_wrong_partition_events_are_rejected(self):
        roots = self.plan['core']
        events = [('CoreTest', 'SUCCESSFUL', True, 'core'), ('H2IT', 'SUCCESSFUL', True, 'h2')]
        self.module.require_events(roots, events)
        for broken in [events[:-1], events + [events[0]],
                       [('CoreTest', 'FAILED', True, 'core'), events[1]],
                       [('CoreTest', 'SKIPPED', True, 'core'), events[1]],
                       [('CoreTest', 'ABORTED', True, 'core'), events[1]],
                       events + [('OtherIT', 'SUCCESSFUL', True, 'other')]]:
            with self.subTest(broken=broken), self.assertRaises(ValueError):
                self.module.require_events(roots, broken)

    def test_nested_parameterized_and_dynamic_nodes_must_really_run(self):
        roots = [dict(name='NestedIT', nodes=[dict(id='root/nested/method', container=False),
                    dict(id='root/parameterized', container=True), dict(id='root/factory', container=True)])]
        events = [('NestedIT', 'SUCCESSFUL', True, 'root/nested/method'),
                  ('NestedIT', 'SUCCESSFUL', False, 'root/parameterized'),
                  ('NestedIT', 'SUCCESSFUL', True, 'root/parameterized/[invocation:1]'),
                  ('NestedIT', 'SUCCESSFUL', False, 'root/factory'),
                  ('NestedIT', 'SUCCESSFUL', True, 'root/factory/[dynamic:1]')]
        self.module.require_events(roots, events)
        for index in [0, 2, 4]:
            with self.subTest(index=index), self.assertRaises(ValueError):
                self.module.require_events(roots, events[:index] + events[index+1:])

    def test_bad_empty_skipped_and_deferred_reports_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            reports = root / 'surefire-reports'
            reports.mkdir()
            path = reports / 'TEST-CoreTest.xml'
            for attrs in ['tests="0"', 'tests="1" failures="1"', 'tests="1" errors="1"',
                          'tests="1" skipped="1"', 'not xml']:
                path.write_text('<testsuite '+attrs+'/>' if attrs != 'not xml' else attrs)
                with self.subTest(attrs=attrs), self.assertRaises(ValueError):
                    self.module.require_reports(root)
            path.write_text('<testsuite tests="1" failures="0" errors="0" skipped="0"/>')
            self.assertEqual(self.module.require_reports(root), 1)
            failsafe = root / 'failsafe-reports'
            failsafe.mkdir()
            (failsafe / 'failsafe-summary.xml').write_text('<failsafe-summary result="255" timeout="false"><completed>1</completed><failures>1</failures></failsafe-summary>')
            with self.assertRaises(ValueError):
                self.module.require_reports(root)

    def test_real_failsafe_success_and_independent_failure_signals(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); reports = root / 'failsafe-reports'; reports.mkdir()
            (reports / 'TEST-Example.xml').write_text('<testsuite tests="22" failures="0" errors="0" skipped="0"/>')
            path = reports / 'failsafe-summary.xml'
            success = ('<failsafe-summary xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" result="null" timeout="false">'
                       '<completed>22</completed><errors>0</errors><failures>0</failures><skipped>0</skipped>'
                       '<failureMessage xsi:nil="true"/></failsafe-summary>')
            path.write_text(success)
            self.assertEqual(self.module.require_reports(root), 22)
            for broken in [success.replace('result="null"', 'result="255"'),
                           success.replace('timeout="false"', 'timeout="true"'),
                           success.replace('<errors>0', '<errors>1'),
                           success.replace('<failures>0', '<failures>1'),
                           success.replace('<skipped>0', '<skipped>1'),
                           success.replace('<failureMessage xsi:nil="true"/>', '<failureMessage>failed</failureMessage>')]:
                path.write_text(broken)
                with self.subTest(summary=broken), self.assertRaises(ValueError):
                    self.module.require_reports(root)

    def test_all_expected_github_children_must_succeed_even_if_skipped_is_green(self):
        children = {'changes': {'result': 'success', 'outputs': {'code': 'true'}},
                    'prepare-tests': {'result': 'success'}, 'test-core': {'result': 'success'},
                    'test-mariadb-1': {'result': 'success'}, 'test-mariadb-2': {'result': 'success'},
                    'test-browser': {'result': 'success'}}
        self.assertTrue(self.module.require_jobs(children))
        for key in children:
            for result in ['skipped', 'failure', 'cancelled', '']:
                broken = json.loads(json.dumps(children)); broken[key]['result'] = result
                with self.subTest(key=key, result=result), self.assertRaises(ValueError):
                    self.module.require_jobs(broken)
        with self.assertRaises(ValueError):
            self.module.require_jobs({key: value for key, value in children.items() if key != 'test-browser'})
        children['changes']['outputs']['code'] = 'false'
        for key in children:
            if key != 'changes': children[key]['result'] = 'skipped'
        self.assertFalse(self.module.require_jobs(children))

    def test_class_identity_changes_when_a_shared_bytecode_file_changes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'classes').mkdir(); (root / 'test-classes').mkdir()
            path = root / 'classes/Example.class'; path.write_bytes(b'first compiled output')
            before = self.module.classes_hash(root)
            path.write_bytes(b'different compiled output')
            self.assertNotEqual(before, self.module.classes_hash(root))
            with self.assertRaises(ValueError):
                self.module.classes_hash(root / 'absent')

    def test_complete_artifact_aggregation_and_missing_or_corrupt_inputs(self):
        with tempfile.TemporaryDirectory() as directory:
            previous = Path.cwd()
            os.chdir(directory)
            try:
                root = Path('target'); (root / 'ci-plan').mkdir(parents=True)
                identity = dict(source='a', tree='b', classes='c', run='1', attempt='1')
                self.module.identity = lambda: identity
                (root / 'ci-plan/plan.json').write_text(json.dumps(dict(identity=identity, groups=self.plan)))
                for group, members in self.plan.items():
                    artifact = root / 'ci-results' / ('test-partition-' + group)
                    (artifact / 'test-perf').mkdir(parents=True)
                    (artifact / 'surefire-reports').mkdir()
                    events = ''.join(member['name']+'\tSUCCESSFUL\ttrue\t'+base64.b64encode(node['id'].encode()).decode()+'\n'
                        for member in members for node in member['nodes'])
                    (artifact / 'test-perf/ci-events-1.tsv').write_text(events)
                    (artifact / 'surefire-reports/TEST-Example.xml').write_text('<testsuite tests="'+str(len(members))+'"/>')
                    coverage = ('synthetic coverage '+group).encode()
                    (artifact / 'jacoco.exec').write_bytes(coverage)
                    (artifact / 'receipt.json').write_text(json.dumps(dict(identity=identity, group=group,
                        passed=True, tests=len(members), xmlTests=len(members), coverageSha256=hashlib.sha256(coverage).hexdigest())))
                artifacts = root / 'ci-results'
                with contextlib.redirect_stdout(None): self.module.aggregate(artifacts)
                self.assertEqual(len(list((root / 'ci-coverage').glob('*.exec'))), 4)
                artifact = artifacts / 'test-partition-browser'
                for name, replacement in [('jacoco.exec', b'corrupt'), ('test-perf/ci-events-1.tsv', b''),
                                          ('surefire-reports/TEST-Example.xml', b'<testsuite tests="5"/>')]:
                    path = artifact / name; original = path.read_bytes(); path.write_bytes(replacement)
                    with self.subTest(name=name), self.assertRaises(ValueError):
                        self.module.aggregate(artifacts)
                    path.write_bytes(original)
                path = artifact / 'receipt.json'; receipt = json.loads(path.read_text()); receipt['group'] = 'core'
                path.write_text(json.dumps(receipt))
                with self.assertRaises(ValueError): self.module.aggregate(artifacts)
            finally:
                os.chdir(previous)


if __name__ == '__main__':
    unittest.main()
