#!/usr/bin/env python3
"""Qualify published updates with real Flyway migrations on the empty host qualification stack."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import uuid

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
from config import atomic_json, private_path
from live import restore
from recovery import NativeRuntime, installed_release, probe_environment, read_json
from runtime import compose_spec
from update import execute_update, stage_release


class MigrationFixtureRuntime(NativeRuntime):
    def __init__(self, installation, source, target_version, fixtures, fail_health):
        super().__init__(installation, source)
        self.target_version = target_version
        self.fail_health = fail_health
        self.health_failure_observed = False
        self.fixture_directory = fixtures
        migrations = fixtures / 'migrations'
        migrations.mkdir(mode=0o755)
        migrations.chmod(0o755)
        migration = migrations / 'V9000__native_update_fixture.sql'
        migration.write_text('CREATE TABLE ctc_update_native_fixture(id INT PRIMARY KEY);\n'
                             'INSERT INTO ctc_update_native_fixture VALUES(17);\n')
        migration.chmod(0o444)
        self.health_failure = migrations / 'force-unhealthy'
        self.override = fixtures / 'compose-fixture.json'
        atomic_json(self.override, {'services': {'app': {
            'environment': {'SPRING_FLYWAY_LOCATIONS': 'classpath:db/migration,filesystem:/run/ctc-native-update'},
            'volumes': [{'type': 'bind', 'source': str(migrations), 'target': '/run/ctc-native-update', 'read_only': True}],
            'healthcheck': {'test': ['CMD-SHELL', 'test ! -f /run/ctc-native-update/force-unhealthy && '
                                    'curl --fail --silent http://localhost:8080/actuator/health'],
                            'interval': '1s', 'timeout': '5s', 'retries': 2, 'start_period': '90s'}}}})

    def compose(self, arguments):
        if self.release['version'] != self.target_version:
            return super().compose(arguments)
        command, environment = compose_spec(self.root, self.config, self.deploy_root)
        result = subprocess.run(command + ['-f', str(self.override)] + arguments, env=environment,
                                cwd=self.root, capture_output=True, timeout=240)
        if result.returncode:
            with self.diagnostic.open('ab') as output:
                output.write(result.stderr)
            raise ValueError('native candidate Compose operation failed')
        if arguments[0] == 'up' and self.fail_health:
            self.db = self.container('db')
            assert self.query('SELECT id FROM ctc_update_native_fixture;') == ['17'], 'real migration must finish before forced health failure'
            self.health_failure.write_text('native qualification failure\n')
            self.health_failure.chmod(0o444)
            deadline = time.monotonic() + 30
            while time.monotonic() < deadline:
                app = self.container('app')
                if app['State']['Health']['Status'] == 'unhealthy':
                    self.health_failure_observed = True
                    raise ValueError('native candidate unhealthy after real Flyway migration')
                time.sleep(1)
            raise AssertionError('candidate Docker health did not become unhealthy')
        return result.stdout


def assert_synthetic(runtime):
    assert runtime.root == Path('/srv/ctc-manager'), 'only the known host qualification installation is eligible'
    assert (runtime.root / '.ctc-manager.lock/owner').read_text().strip() == str(os.getppid()), 'caller must hold the installation lock'
    runtime.preflight()
    assert runtime.config.get('outboundPaused') is True, 'qualification must keep integrations paused'
    metadata = runtime.database_metadata()
    assert all(metadata['records'].get(name) == 0 for name in ['seasons', 'teams', 'drivers', 'races']), 'never qualify against real domain data'
    assert metadata['records'].get('ctc_runtime_qualification') == 1, 'require the dedicated qualification marker'
    assert 'ctc_update_native_fixture' not in metadata['records'], 'an earlier migration fixture needs recovery first'
    assert json.loads(Path(runtime.config['googleCredentialsFile']).read_text()) == {}, 'never use real Google credentials'
    environment = probe_environment(runtime.config['appEnvFile'], Path(runtime.config['databasePasswordFile']).read_text().strip())
    assert not environment.get('DISCORD_BOT_TOKEN') and not environment.get('GOOGLE_CALENDAR_ID'), 'no live integration identities'
    return metadata


def main():
    parser = argparse.ArgumentParser(allow_abbrev=False)
    parser.add_argument('--directory', required=True)
    parser.add_argument('--release', required=True)
    parser.add_argument('--evidence', required=True)
    parser.add_argument('--destination', required=True)
    args = parser.parse_args()
    os.umask(0o077)
    root = Path(args.directory).resolve()
    source = (root / 'current').resolve()
    baseline_runtime = NativeRuntime(root, source)
    baseline = assert_synthetic(baseline_runtime)
    evidence = Path(args.evidence)
    evidence.mkdir(mode=0o700)
    private_path(evidence, directory=True)
    destination = private_path(Path(args.destination), directory=True)
    results = []
    for case, fail_health in [('migration-success', False), ('post-migration-unhealthy', True)]:
        directory = evidence / case
        directory.mkdir(mode=0o700)
        runtime = MigrationFixtureRuntime(root, source, args.release, directory, fail_health)
        assert_synthetic(runtime)
        runtime.diagnostic = directory / 'operation.log'
        runtime.diagnostic.touch(mode=0o600)
        candidate = stage_release(runtime, args.release)
        assert installed_release(candidate)['version'] == args.release, 'only genuinely published candidate provenance'
        if fail_health:
            try:
                execute_update(runtime, candidate, destination)
            except ValueError as error:
                assert str(error) == 'native candidate unhealthy after real Flyway migration', 'observe the intended post-migration health failure'
            else:
                raise AssertionError('unhealthy candidate unexpectedly succeeded')
            assert runtime.health_failure_observed, 'Docker must actually report unhealthy'
            assert not runtime.is_app_running(), 'failed candidate must remain stopped'
            assert runtime.config['outboundPaused'] is True, 'failed candidate must remain paused'
            receipt = read_json(root / 'state/update.json')
            assert receipt['stage'] == 'failed-recovery-required', 'never report an image-only rollback'
        else:
            receipt = execute_update(runtime, candidate, destination)
            assert receipt['stage'] == 'completed', 'real migration update must complete'
        runtime.db = runtime.container('db')
        assert runtime.query('SELECT id FROM ctc_update_native_fixture;') == ['17'], 'candidate actually migrated MariaDB'
        assert runtime.query("SELECT version FROM flyway_schema_history WHERE version='9000' AND success=1;") == ['9000'], 'real Flyway must record migration success'
        assert runtime.config['appImage'] == installed_release(candidate)['appImage'], 'selected image must remain the genuine target'
        unit = Path(receipt['recoveryUnit'])
        before_generation = runtime.config.get('storageGeneration')
        recovery = NativeRuntime(root, source, require_selected=False)
        recovery.diagnostic = directory / 'recovery.log'
        recovery.diagnostic.touch(mode=0o600)
        generation = 'recovery-native-update-' + uuid.uuid4().hex[:10]
        target = 'ctc-manager:' + recovery.config['installationId'] + ':' + generation
        restored = restore(recovery, unit, target)
        assert restored['stage'] == 'active-integrations-paused', 'complete earlier unit must restore into independent storage'
        assert recovery.config['storageGeneration'] != before_generation, 'never reuse migrated source storage'
        assert (root / 'current').resolve() == source, 'old release tools must match the recovered old image'
        recovery.preflight()
        assert recovery.database_metadata() == baseline, 'restore must recover exact earlier records and Flyway state'
        assert_synthetic(recovery)
        atomic_json(directory / 'update.json', receipt)
        atomic_json(directory / 'recovery.json', restored)
        (root / 'state/update.json').rename(directory / 'reconciled-update.json')
        result = {'case': case, 'status': 'passed', 'targetVersion': args.release,
                  'realFlywayMigration': True, 'completeIndependentRecovery': True,
                  'dockerUnhealthyObserved': runtime.health_failure_observed,
                  'sourceReleaseRestored': installed_release(source)['version'], 'macSourceTouched': False}
        results.append(result)
        atomic_json(evidence / 'result.json', {'format': 1, 'results': results})
        print(json.dumps(result), flush=True)


if __name__ == '__main__':
    main()
