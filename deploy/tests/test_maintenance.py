import datetime as dt
import json
import os
from pathlib import Path
import shutil
import sys
import tempfile
import unittest
from unittest.mock import patch
import uuid

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'deploy/lib'))
import maintenance
from config import atomic_json
from recovery import sha256, validate_unit
import test_recovery


class MaintenanceTest(unittest.TestCase):
    def setUp(self):
        self.fixture = test_recovery.RecoveryUnitTest('test_complete_unit_preserves_provenance_and_inventory')
        self.fixture.setUp()
        self.addCleanup(self.fixture.doCleanups)
        self.root = self.fixture.unit.parent / 'installation'
        self.root.mkdir(mode=0o700)
        for name in ['config', 'state']:
            (self.root / name).mkdir(mode=0o700)
        self.units = self.fixture.unit.parent / 'units'
        self.units.mkdir(mode=0o700)
        self.now = self.timestamp('2026-11-30T12:00:00Z')
        self.runtime = type('Runtime', (), {})()
        self.runtime.root = self.root
        self.runtime.config = {key: self.fixture.manifest[key] for key in ['installationId', 'appImage']}
        self.runtime.check_destination = lambda destination: None

    def timestamp(self, value):
        return dt.datetime.strptime(value, '%Y-%m-%dT%H:%M:%SZ').replace(tzinfo=dt.timezone.utc).timestamp()

    def unit(self, date='2026-11-30T03:00:00Z'):
        path = self.units / ('recovery-' + uuid.uuid4().hex)
        shutil.copytree(self.fixture.unit, path)
        manifest = json.loads((path / 'manifest.json').read_text())
        manifest.update(id=str(uuid.uuid4()), createdAt=date)
        atomic_json(path / 'manifest.json', manifest)
        return path

    def proof(self, unit):
        return {'status': 'passed', 'unitId': validate_unit(unit)['id'],
                'manifestSha256': sha256(unit / 'manifest.json'), 'outboundEnabled': False,
                'credentialsVerified': True, 'hostPorts': []}

    def state(self, unit):
        value = {'format': 1, 'installationId': self.runtime.config['installationId'],
                 'lastAttempt': {'stage': 'completed', 'time': self.now},
                 'local': {'completedAt': self.now, 'unit': str(unit),
                           'manifestSha256': sha256(unit / 'manifest.json'),
                           'appImage': self.runtime.config['appImage']},
                 'proof': {'completedAt': self.now, 'unit': str(unit),
                           'manifestSha256': sha256(unit / 'manifest.json')}}
        atomic_json(self.root / 'state/backups.json', value)
        return value

    def test_local_backup_needs_no_remote_config_or_alert_hook(self):
        unit = self.unit()
        trace = []
        def capture(*args):
            trace.append('capture')
            return unit
        def prove(*args):
            trace.append('proof')
            return self.proof(unit)
        result = maintenance.run_backup(self.runtime, self.units, capture_unit=capture, prove_unit=prove, now=self.now)
        self.assertEqual(['capture', 'proof'], trace)
        self.assertEqual('completed', result['lastAttempt']['stage'])
        self.assertEqual([], maintenance.freshness(result, self.now))
        self.assertNotIn('remote', result)
        self.assertFalse((self.root / 'config/backup.json').exists())

    def test_retention_keeps_two_daily_and_first_monthly_only(self):
        monthly = self.unit('2026-11-01T03:00:00Z')
        middle = self.unit('2026-11-15T03:00:00Z')
        yesterday = self.unit('2026-11-29T03:00:00Z')
        earlier = self.unit('2026-11-30T02:00:00Z')
        newest = self.unit()
        state = self.state(newest)
        plan = maintenance.retention_plan(self.runtime, self.units, state, self.now)
        self.assertEqual({middle, earlier}, set(plan))
        self.assertEqual({monthly, yesterday, newest}, set(self.units.iterdir()) - set(plan))

    def test_monthly_boundary_uses_berlin_and_replaces_previous_month(self):
        old = self.unit('2026-10-01T03:00:00Z')
        monthly = self.unit('2026-10-31T23:10:00Z')
        yesterday = self.unit('2026-11-29T03:00:00Z')
        newest = self.unit()
        state = self.state(newest)
        plan = maintenance.retention_plan(self.runtime, self.units, state, self.now)
        self.assertIn(old, plan)
        self.assertNotIn(monthly, plan)
        self.assertEqual({monthly, yesterday, newest}, set(self.units.iterdir()) - set(plan))

    def test_later_manual_backup_cannot_delete_last_proven_stand(self):
        monthly = self.unit('2026-11-01T03:00:00Z')
        yesterday = self.unit('2026-11-29T03:00:00Z')
        proven = self.unit()
        state = self.state(proven)
        manual = self.unit('2026-11-30T04:00:00Z')
        plan = maintenance.retention_plan(self.runtime, self.units, state, self.now)
        self.assertNotIn(proven, plan)
        self.assertTrue({monthly, yesterday, manual}.isdisjoint(plan))

    def test_failed_capture_preserves_last_success_without_leaking_error_payload(self):
        unit = self.unit()
        previous = self.state(unit)
        def failed(*args):
            raise ValueError('fixture-private-secret')
        with self.assertRaises(ValueError) as caught:
            maintenance.run_backup(self.runtime, self.units, capture_unit=failed, now=self.now)
        state = maintenance.evidence(self.root)
        self.assertEqual(previous['local'], state.get('local'))
        self.assertEqual(previous['proof'], state.get('proof'))
        self.assertEqual('capture-failed', state['lastAttempt']['stage'])
        self.assertNotIn('fixture-private-secret', json.dumps(state) + str(caught.exception))

    def test_pending_update_recovery_survives_retention(self):
        protected = self.unit('2026-09-01T03:00:00Z')
        older = self.unit('2026-09-02T03:00:00Z')
        newest = self.unit()
        self.unit('2026-11-29T03:00:00Z')
        inflight = self.unit('2026-08-01T03:00:00Z')
        state = self.state(newest)
        atomic_json(self.root / 'state/protected-recovery.json',
                    {'unit': str(protected), 'manifestSha256': sha256(protected / 'manifest.json'), 'previousUnits': [str(older)]})
        atomic_json(self.root / 'state/update.json', {'stage': 'failed-recovery-required', 'recoveryUnit': str(inflight)})
        self.assertEqual([], maintenance.retention_plan(self.runtime, self.units, state, self.now))
        atomic_json(self.root / 'state/update.json', {'stage': 'completed'})
        self.assertEqual({protected, older, inflight}, set(maintenance.retention_plan(self.runtime, self.units, state, self.now)))

    def test_preupdate_unit_waits_for_verified_backup_of_active_image(self):
        protected = self.unit('2026-09-01T03:00:00Z')
        newest = self.unit()
        state = self.state(newest)
        self.unit('2026-11-29T03:00:00Z')
        state['local']['appImage'] = 'old-image'
        atomic_json(self.root / 'state/protected-recovery.json',
                    {'unit': str(protected), 'manifestSha256': sha256(protected / 'manifest.json'), 'previousUnits': []})
        atomic_json(self.root / 'state/update.json', {'stage': 'completed'})
        self.assertNotIn(protected, maintenance.retention_plan(self.runtime, self.units, state, self.now))

    def test_failed_run_unknown_corrupt_or_foreign_unit_prevents_deletion(self):
        unit = self.unit()
        state = self.state(unit)
        state['lastAttempt']['stage'] = 'capture-failed'
        with self.assertRaisesRegex(ValueError, 'failed'):
            maintenance.retention_plan(self.runtime, self.units, state, self.now)
        state['lastAttempt']['stage'] = 'completed'
        (self.units / 'unknown').mkdir(mode=0o700)
        with self.assertRaisesRegex(ValueError, 'unknown'):
            maintenance.retention_plan(self.runtime, self.units, state, self.now)
        (self.units / 'unknown').rmdir()
        (unit / 'database.sql').write_text('corrupted')
        with self.assertRaises(ValueError):
            maintenance.retention_plan(self.runtime, self.units, state, self.now)
        shutil.rmtree(unit)
        unit = self.unit()
        self.runtime.config['installationId'] = str(uuid.uuid4())
        with self.assertRaisesRegex(ValueError, 'installation'):
            maintenance.retention_plan(self.runtime, self.units, state, self.now)

    def test_changed_protected_reference_refuses(self):
        unit = self.unit()
        state = self.state(unit)
        atomic_json(self.root / 'state/protected-recovery.json', {'unit': str(unit), 'manifestSha256': '0' * 64})
        with self.assertRaisesRegex(ValueError, 'protected'):
            maintenance.retention_plan(self.runtime, self.units, state, self.now)

    def test_dry_run_then_prune_removes_only_planned_units(self):
        old = self.unit('2026-10-01T03:00:00Z')
        newest = self.unit()
        self.unit('2026-11-29T03:00:00Z')
        self.state(newest)
        self.assertEqual([old], maintenance.prune(self.runtime, self.units, dry_run=True, now=self.now))
        self.assertTrue(old.exists())
        self.assertEqual([old], maintenance.prune(self.runtime, self.units, now=self.now))
        self.assertFalse(old.exists())
        self.assertTrue(newest.exists())

    def test_pruned_update_receipt_does_not_break_the_next_backup(self):
        old = self.unit('2026-09-01T03:00:00Z')
        newest = self.unit()
        self.unit('2026-11-29T03:00:00Z')
        self.state(newest)
        atomic_json(self.root / 'state/protected-recovery.json',
                    {'unit': str(old), 'manifestSha256': sha256(old / 'manifest.json')})
        atomic_json(self.root / 'state/update.json', {'stage': 'completed'})
        maintenance.prune(self.runtime, self.units, now=self.now)
        self.assertFalse((self.root / 'state/protected-recovery.json').exists())
        self.assertEqual([], maintenance.prune(self.runtime, self.units, now=self.now))

    def test_missing_latest_verified_unit_prevents_pruning(self):
        old = self.unit('2026-09-01T03:00:00Z')
        newest = self.unit()
        state = self.state(newest)
        shutil.rmtree(newest)
        with self.assertRaisesRegex(ValueError, 'verified backup'):
            maintenance.retention_plan(self.runtime, self.units, state, self.now)
        self.assertTrue(old.exists())

    def test_missing_mount_prevents_capture_and_reports_local_failure(self):
        self.runtime.check_destination = lambda path: (_ for _ in ()).throw(ValueError('missing disk'))
        with self.assertRaisesRegex(ValueError, 'mount-failed'):
            maintenance.run_backup(self.runtime, self.units, capture_unit=lambda *a: self.fail('capture must not run'), now=self.now)
        self.assertEqual('mount-failed', maintenance.evidence(self.root)['lastAttempt']['stage'])

    def test_interruption_cannot_leave_previous_success_as_current_attempt(self):
        unit = self.unit()
        self.state(unit)
        stages = []
        def capture(*args):
            stages.append(maintenance.evidence(self.root)['lastAttempt']['stage'])
            raise KeyboardInterrupt()
        with self.assertRaises(KeyboardInterrupt):
            maintenance.run_backup(self.runtime, self.units, capture_unit=capture, now=self.now)
        self.assertNotEqual(['completed'], stages)
        self.assertIn('last-attempt-failed', maintenance.freshness(maintenance.evidence(self.root), self.now))

    def test_every_proof_field_is_required(self):
        unit = self.unit()
        proof = self.proof(unit)
        for key, value in [('status', 'failed'), ('unitId', 'wrong'), ('manifestSha256', '0' * 64),
                           ('outboundEnabled', True), ('credentialsVerified', False), ('hostPorts', [9092])]:
            with self.subTest(field=key):
                self.assertFalse(maintenance.proof_matches({**proof, key: value}, unit, validate_unit(unit)))
        self.assertTrue(maintenance.proof_matches(proof, unit, validate_unit(unit)))

    def test_failed_proof_keeps_local_backup_and_skips_pruning(self):
        unit = self.unit()
        with patch.object(maintenance, 'prune', side_effect=AssertionError('must not prune')):
            with self.assertRaisesRegex(ValueError, 'proof-failed'):
                maintenance.run_backup(self.runtime, self.units, capture_unit=lambda *a: unit,
                                       prove_unit=lambda *a: {'status': 'passed'}, now=self.now)
        self.assertTrue(unit.exists())
        self.assertIn('local', maintenance.evidence(self.root))
        self.assertNotIn('proof', maintenance.evidence(self.root))

    def test_source_identity_is_required_before_capture(self):
        self.runtime.config.pop('installationId')
        with self.assertRaisesRegex(ValueError, 'identity'):
            maintenance.run_backup(self.runtime, self.units, now=self.now)

    def test_freshness_only_requires_local_and_proof(self):
        state = self.state(self.unit())
        self.assertEqual([], maintenance.freshness(state, self.now))
        state['local']['completedAt'] = self.now - 31 * 3600
        self.assertIn('local-stale', maintenance.freshness(state, self.now))
        state['proof']['completedAt'] = float('nan')
        self.assertIn('proof-invalid', maintenance.freshness(state, self.now))
        state['lastAttempt']['stage'] = 'proof-failed'
        self.assertIn('last-attempt-failed', maintenance.freshness(state, self.now))

    def test_doctor_works_without_backup_configuration_and_rejects_missing_mount(self):
        self.state(self.unit())
        with patch.object(sys, 'argv', ['maintenance.py', '--directory', str(self.root), 'doctor']), \
                patch.object(maintenance.time, 'time', return_value=self.now), \
                patch.object(maintenance, 'NativeRuntime', return_value=self.runtime), \
                patch.object(maintenance, 'UNIT_ROOT', self.units):
            maintenance.main()
            self.runtime.check_destination = lambda path: (_ for _ in ()).throw(ValueError('missing'))
            with self.assertRaisesRegex(ValueError, 'readiness'):
                maintenance.main()

    def test_changed_latest_proof_binding_prevents_pruning(self):
        unit = self.unit()
        state = self.state(unit)
        for section, key, value in [('local', 'manifestSha256', '0' * 64), ('proof', 'unit', '/wrong'),
                                    ('proof', 'manifestSha256', '0' * 64)]:
            with self.subTest(section=section, key=key):
                changed = json.loads(json.dumps(state))
                changed[section][key] = value
                with self.assertRaisesRegex(ValueError, 'proof changed'):
                    maintenance.retention_plan(self.runtime, self.units, changed, self.now)

    def test_matching_but_incorrect_proof_hash_still_refuses_pruning(self):
        unit = self.unit()
        state = self.state(unit)
        state['local']['manifestSha256'] = '0' * 64
        state['proof']['manifestSha256'] = '0' * 64
        with self.assertRaisesRegex(ValueError, 'proof changed'):
            maintenance.retention_plan(self.runtime, self.units, state, self.now)

    def test_changed_retention_plan_prevents_deletion(self):
        old = self.unit('2026-09-01T03:00:00Z')
        with patch.object(maintenance, 'retention_plan', side_effect=[[old], []]):
            with self.assertRaisesRegex(ValueError, 'plan changed'):
                maintenance.prune(self.runtime, self.units, now=self.now)
        self.assertTrue(old.exists())

    def test_mutating_cli_requires_the_launcher_parent_lock(self):
        lock = self.root / '.ctc-manager.lock'
        lock.mkdir(mode=0o700)
        owner = lock / 'owner'
        owner.write_text(str(os.getppid() + 1))
        owner.chmod(0o600)
        argv = ['maintenance.py', '--directory', str(self.root), 'maintenance']
        with patch.object(sys, 'argv', argv), patch.object(maintenance, 'NativeRuntime', return_value=self.runtime),                 patch.object(maintenance, 'UNIT_ROOT', self.units), patch.object(maintenance, 'run_backup') as run:
            run.return_value = {'lastAttempt': {'stage': 'completed'}, 'local': {'unit': '/fixture'}}
            with self.assertRaisesRegex(ValueError, 'launcher-owned'):
                maintenance.main()
            run.assert_not_called()
            owner.write_text(str(os.getppid()))
            run.return_value = {'lastAttempt': {'stage': 'completed'}, 'local': {'unit': '/fixture'}}
            maintenance.main()
            run.assert_called_once_with(self.runtime, self.units)

    def test_future_unit_or_mismatched_capture_identity_refuses(self):
        future = self.unit('2026-12-01T03:00:00Z')
        with self.assertRaisesRegex(ValueError, 'future'):
            maintenance.retention_plan(self.runtime, self.units, self.state(future), self.now)
        shutil.rmtree(future)
        unit = self.unit()
        outside = self.root / 'outside-unit'
        shutil.move(unit, outside)
        with self.assertRaisesRegex(ValueError, 'capture-failed'):
            maintenance.run_backup(self.runtime, self.units, capture_unit=lambda *a: outside, now=self.now)
        unit = self.unit()
        self.runtime.config['installationId'] = str(uuid.uuid4())
        with self.assertRaisesRegex(ValueError, 'capture-failed'):
            maintenance.run_backup(self.runtime, self.units, capture_unit=lambda *a: unit, now=self.now)

    def test_cli_rejects_ignored_flags_and_missing_mount_before_creating_units(self):
        with patch.object(sys, 'argv', ['maintenance.py', '--directory', str(self.root), 'maintenance', '--dry-run']), \
                patch.object(maintenance, 'NativeRuntime', side_effect=AssertionError('invalid flags must fail before runtime')):
            with self.assertRaises(SystemExit) as caught:
                maintenance.main()
            self.assertEqual(2, caught.exception.code)
        lock = self.root / '.ctc-manager.lock'
        lock.mkdir(mode=0o700)
        (lock / 'owner').write_text(str(os.getppid()))
        (lock / 'owner').chmod(0o600)
        destination = self.root / 'absent/units'
        destination.parent.mkdir(mode=0o700)
        self.runtime.check_destination = lambda path: (_ for _ in ()).throw(ValueError('missing device'))
        with patch.object(sys, 'argv', ['maintenance.py', '--directory', str(self.root), 'maintenance']),                 patch.object(maintenance, 'NativeRuntime', return_value=self.runtime),                 patch.object(maintenance, 'UNIT_ROOT', destination):
            with self.assertRaises(ValueError):
                maintenance.main()
        self.assertFalse(destination.exists())

    def test_doctor_rejects_missing_verified_unit_even_with_fresh_timestamps(self):
        unit = self.unit()
        self.state(unit)
        shutil.rmtree(unit)
        with patch.object(sys, 'argv', ['maintenance.py', '--directory', str(self.root), 'doctor']),                 patch.object(maintenance.time, 'time', return_value=self.now),                 patch.object(maintenance, 'NativeRuntime', return_value=self.runtime),                 patch.object(maintenance, 'UNIT_ROOT', self.units):
            with self.assertRaisesRegex(ValueError, 'readiness'):
                maintenance.main()

    def test_only_daily_backup_service_is_packaged(self):
        service = (ROOT / 'deploy/systemd/ctc-manager-backup.service').read_text()
        self.assertNotIn('OnFailure=', service)
        self.assertIn('UMask=0077', service)
        self.assertFalse((ROOT / 'deploy/systemd/ctc-manager-backup-alert@.service').exists())
        self.assertFalse((ROOT / 'deploy/systemd/ctc-manager-backup-check.timer').exists())
        timer = (ROOT / 'deploy/systemd/ctc-manager-backup.timer').read_text()
        self.assertIn('03:45:00 Europe/Berlin', timer)
        self.assertIn('Persistent=true', timer)


if __name__ == '__main__':
    unittest.main()
