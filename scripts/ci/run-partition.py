#!/usr/bin/env python3
import importlib.util
from pathlib import Path
import subprocess
import sys

spec = importlib.util.spec_from_file_location('partitions', Path(__file__).with_name('test-partitions.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
group = sys.argv[1]
if group not in module.GROUPS:
    raise SystemExit('Unknown partition')
profile = 'ci-core' if group == 'core' else 'ci-browser,e2e' if group == 'browser' else 'ci-database'
for folder in ['surefire-reports', 'failsafe-reports', 'test-perf']:
    if (Path('target') / folder).exists():
        raise SystemExit('A partition requires fresh test output')
if Path('target/jacoco.exec').exists():
    raise SystemExit('A partition requires fresh coverage output')
code = subprocess.run(['./mvnw', 'integration-test', '-P' + profile,
    '-Dctc.ci.includes=target/ci-plan/' + group + '.txt', '-Dctc.ci.partition=' + group,
    '-Dspring.profiles.active=dev', '-Ddocker.available=true', '--no-transfer-progress']).returncode
if code:
    raise SystemExit(code)
module.receipt(group)
