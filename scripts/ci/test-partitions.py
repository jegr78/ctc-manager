#!/usr/bin/env python3
"""Plan and verify complete CI partitions from JUnit's discovered test plan."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET

GROUPS = ('core', 'mariadb-1', 'mariadb-2', 'browser')


def assign(roots, durations):
    result = {group: [] for group in GROUPS}
    names = set()
    database = []
    for root in roots:
        name = root['name']
        if name in names or not root['nodes']:
            raise ValueError('Duplicate or empty root: ' + name)
        names.add(name)
        if root['stage'] == 'browser':
            result['browser'].append(root)
        elif root['stage'] == 'unit':
            result['core'].append(root)
        elif root['stage'] == 'integration':
            if root.get('database', 'MariaDb' in name):
                database.append(root)
            else:
                result['core'].append(root)
        else:
            raise ValueError('Unknown stage: ' + root['stage'])
    loads = {'mariadb-1': 0.0, 'mariadb-2': 0.0}
    for root in sorted(database, key=lambda r: (-durations.get(r['name'], 60), r['name'])):
        group = min(loads, key=lambda key: (loads[key], key))
        result[group].append(root)
        loads[group] += durations.get(root['name'], 60)
    if any(not roots for roots in result.values()):
        raise ValueError('Every expected partition must contain discovered tests')
    return result


def require_workers(plan, receipts, identity):
    if set(plan) != set(GROUPS) or set(receipts) != set(plan):
        raise ValueError('Missing or unexpected worker evidence')
    for group, receipt in receipts.items():
        if receipt.get('identity') != identity or receipt.get('passed') is not True or receipt.get('group') != group:
            raise ValueError('Failed or incompatible worker: ' + group)


def require_events(roots, events):
    names = {root['name'] for root in roots}
    seen = {}
    for name, status, is_test, unique_id in events:
        if name not in names or status != 'SUCCESSFUL' or unique_id in seen:
            raise ValueError('Unexpected, skipped, failed or duplicate execution: ' + name)
        seen[unique_id] = is_test
    if not any(seen.values()):
        raise ValueError('No tests executed')
    for root in roots:
        for node in root['nodes']:
            key = node['id']
            if key not in seen:
                raise ValueError('Discovered test was not executed: ' + key)
            if node['container'] and not any(test and uid.startswith(key + '/') for uid, test in seen.items()):
                raise ValueError('Empty parameterized or dynamic test: ' + key)
            if not node['container'] and not seen[key]:
                raise ValueError('Discovered leaf did not execute as a test: ' + key)


def require_reports(root):
    total = 0
    try:
        paths = list(root.glob('*-reports/TEST-*.xml'))
        for path in paths:
            suite = ET.parse(path).getroot()
            counts = {key: int(suite.get(key, '0')) for key in ['tests', 'failures', 'errors', 'skipped']}
            if suite.tag != 'testsuite' or counts['tests'] <= 0 or any(counts[key] for key in ['failures', 'errors', 'skipped']):
                raise ValueError('Failed, skipped or empty test report: ' + path.name)
            total += counts['tests']
        for path in root.glob('failsafe-reports/failsafe-summary*.xml'):
            summary = ET.parse(path).getroot()
            if summary.tag != 'failsafe-summary' or summary.get('result') not in [None, '0', 'null'] or summary.get('timeout') not in [None, 'false']:
                raise ValueError('Deferred Failsafe failure')
            for key in ['failures', 'errors', 'skipped']:
                if int(summary.findtext(key) or '0'):
                    raise ValueError('Deferred Failsafe failure')
            if summary.findtext('failureMessage'):
                raise ValueError('Deferred Failsafe failure')
    except (ET.ParseError, TypeError) as error:
        raise ValueError('Malformed test reports') from error
    if total <= 0:
        raise ValueError('No fresh test reports')
    return total


def require_jobs(children):
    change = children.get('changes', {})
    if change.get('result') != 'success' or change.get('outputs', {}).get('code') not in ['true', 'false']:
        raise ValueError('Change detection failed or returned no decision')
    if change['outputs']['code'] == 'false':
        return False
    for key in ['prepare-tests', 'test-core', 'test-mariadb-1', 'test-mariadb-2', 'test-browser']:
        if children.get(key, {}).get('result') != 'success':
            raise ValueError('Required child did not succeed: ' + key)
    return True


def classes_hash(root):
    files = sorted(path for folder in ['classes', 'test-classes'] for path in (root / folder).rglob('*.class'))
    if not files:
        raise ValueError('Missing compiled classes')
    digest = hashlib.sha256()
    for path in files:
        digest.update(path.relative_to(root).as_posix().encode() + b'\0' + path.read_bytes())
    return digest.hexdigest()


def identity():
    return dict(source=subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
                tree=subprocess.check_output(['git', 'rev-parse', 'HEAD^{tree}'], text=True).strip(),
                classes=classes_hash(Path('target')), run=os.environ.get('GITHUB_RUN_ID', 'local'),
                attempt=os.environ.get('GITHUB_RUN_ATTEMPT', 'local'))


def read_events(root):
    result = []
    for path in sorted((root / 'test-perf').glob('ci-events-*.tsv')):
        for line in path.read_text().splitlines():
            name, status, is_test, encoded = line.split('\t')
            if is_test not in ['true', 'false']:
                raise ValueError('Malformed execution event')
            result.append((name, status, is_test == 'true', base64.b64decode(encoded, validate=True).decode()))
    return result


def plan():
    roots = {}
    for line in Path('target/ci-inventory.tsv').read_text().splitlines():
        name, stage, database, kind, encoded = line.split('\t')
        if kind not in ['test', 'container'] or database not in ['true', 'false']:
            raise ValueError('Malformed discovery inventory')
        root = roots.setdefault(name, dict(name=name, stage=stage, database=database == 'true', nodes=[]))
        if root['stage'] != stage or root['database'] != (database == 'true'):
            raise ValueError('Inconsistent root inventory')
        node = dict(id=base64.b64decode(encoded, validate=True).decode(), container=kind == 'container')
        if node in root['nodes']:
            raise ValueError('Duplicate discovered node')
        root['nodes'].append(node)
    durations = json.loads(Path('config/ci-test-durations.json').read_text())['seconds']
    groups = assign(list(roots.values()), durations)
    path = Path('target/ci-plan'); path.mkdir(parents=True, exist_ok=True)
    data = dict(identity=identity(), groups=groups)
    (path / 'plan.json').write_text(json.dumps(data, indent=2) + '\n')
    for group, members in groups.items():
        integrations = [root['name'] for root in members if root['stage'] != 'unit']
        (path / (group + '.txt')).write_text('\n'.join(integrations) + '\n')
    print(json.dumps({group: len(roots) for group, roots in groups.items()}))


def receipt(group):
    data = json.loads(Path('target/ci-plan/plan.json').read_text())
    if data['identity'] != identity():
        raise ValueError('Tested classes or source differ from prepared artifact')
    events = read_events(Path('target'))
    require_events(data['groups'][group], events)
    total = require_reports(Path('target'))
    executed = sum(test for _, _, test, _ in events)
    coverage = Path('target/jacoco.exec')
    if not coverage.is_file() or not coverage.stat().st_size:
        raise ValueError('Missing coverage execution data')
    Path('target/receipt.json').write_text(json.dumps(dict(identity=identity(), group=group,
        passed=True, tests=executed, xmlTests=total, coverageSha256=hashlib.sha256(coverage.read_bytes()).hexdigest()), indent=2) + '\n')


def aggregate(directory):
    data = json.loads(Path('target/ci-plan/plan.json').read_text())
    expected = identity()
    if data['identity'] != expected:
        raise ValueError('Aggregate classes/source differ from plan')
    receipts = {p.name.removeprefix('test-partition-'): json.loads((p / 'receipt.json').read_text()) for p in directory.iterdir() if p.is_dir()}
    require_workers(data['groups'], receipts, expected)
    seen = set()
    destination = Path('target/ci-coverage'); destination.mkdir(parents=True, exist_ok=True)
    total = 0
    for group in GROUPS:
        path = directory / ('test-partition-' + group)
        events = read_events(path)
        require_events(data['groups'][group], events)
        count = require_reports(path)
        if count != receipts[group]['xmlTests'] or receipts[group]['tests'] != sum(test for _, _, test, _ in events):
            raise ValueError('Worker report totals differ')
        tests = {uid for _, _, test, uid in events if test}
        if seen & tests:
            raise ValueError('Test executed in more than one partition')
        seen |= tests
        coverage = (path / 'jacoco.exec').read_bytes()
        if not coverage or hashlib.sha256(coverage).hexdigest() != receipts[group]['coverageSha256']:
            raise ValueError('Missing or incompatible coverage input')
        (destination / (group + '.exec')).write_bytes(coverage)
        total += receipts[group]['tests']
    print(json.dumps(dict(tests=total, partitions=list(GROUPS), identity=expected)))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('operation', choices=['plan', 'receipt', 'aggregate', 'jobs'])
    parser.add_argument('argument', nargs='?')
    args = parser.parse_args()
    if args.operation == 'plan': plan()
    elif args.operation == 'receipt': receipt(args.argument)
    elif args.operation == 'aggregate': aggregate(Path(args.argument))
    else:
        required = require_jobs(json.loads(os.environ['CI_NEEDS']))
        print('Code partitions required' if required else 'Documentation-only change')
    return 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (ValueError, KeyError, OSError) as error:
        print('CI evidence rejected: ' + str(error), file=sys.stderr)
        sys.exit(1)
