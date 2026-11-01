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
        fixture = test_recovery.RecoveryUnitTest('test_complete_unit_preserves_provenance_and_inventory')
        fixture.setUp()
        self.addCleanup(fixture.doCleanups)
        self.fixture = fixture
        self.root = fixture.unit.parent / 'installation'
        self.root.mkdir(mode=0o700)
        for name in ['config', 'state', 'secrets']:
            (self.root/name).mkdir(mode=0o700)
        self.units = fixture.unit.parent / 'units'
        self.units.mkdir(mode=0o700)
        self.identity = fixture.manifest['installationId']
        self.now = dt.datetime(2026, 11, 1, 5, tzinfo=dt.timezone.utc).timestamp()
        self.config = {'format': 1, 'repositoryFile': str(self.root/'secrets/repository'),
                       'passwordFile': str(self.root/'secrets/restic-password'),
                       'environmentFile': str(self.root/'secrets/restic-environment.json'),
                       'repositoryId': 'c'*64, 'alertHook': str(self.root/'secrets/alert'),
                       'keyCustodyConfirmed': True, 'offHostConfirmed': True}
        for name, value in [('repository', 'sftp:backup@example.invalid:/ctc\n'),
                            ('restic-password', 'private-key\n'), ('restic-environment.json', '{}'),
                            ('alert', '#!/bin/sh\nexit 0\n')]:
            path=self.root/'secrets'/name;path.write_text(value);path.chmod(0o700 if name=='alert' else 0o600)
        atomic_json(self.root/'config/backup.json', self.config)
        self.runtime = type('Runtime', (), {})()
        self.runtime.root = self.root
        self.runtime.config = {'installationId': self.identity}
        self.runtime.check_destination = lambda destination: None
        self.remote = type('Remote', (), {})()
        self.remote.config = self.config
        self.remote.verify = lambda: None
        self.remote.backup = lambda unit, manifest: self.receipt(unit)
        self.remote.confirm = lambda unit, receipt: None
        self.alerts = []

    def make_unit(self, age=0):
        path=self.units/('recovery-'+uuid.uuid4().hex)
        shutil.copytree(self.fixture.unit,path)
        manifest=json.loads((path/'manifest.json').read_text())
        manifest['id']=str(uuid.uuid4())
        manifest['createdAt']=dt.datetime.fromtimestamp(self.now-age*86400,dt.timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')
        atomic_json(path/'manifest.json',manifest)
        return path

    def receipt(self, unit):
        manifest=validate_unit(unit)
        return {'snapshot': 'd'*64, 'repositoryId': self.config['repositoryId'],
                'manifestSha256': sha256(unit/'manifest.json'), 'unitId': manifest['id'],
                'unitPath': str(unit), 'completedAt': self.now}

    def evidence(self, units, status='completed'):
        value={'format':1,'installationId':self.identity,'lastAttempt':{'stage':status,'time':self.now},
               'local':{'completedAt':self.now},'remote':{'completedAt':self.now},
               'proof':{'completedAt':self.now},'replicas':{str(u):self.receipt(u) for u in units}}
        atomic_json(self.root/'state/backups.json',value)
        return value

    def test_private_remote_configuration_accepts_provisioned_destination(self):
        self.assertEqual(self.config, maintenance.load_backup(self.root))

    def test_missing_local_unconfirmed_or_nonprivate_destination_refuses(self):
        for field,value in [('repositoryId','wrong'),('keyCustodyConfirmed',False),('format',2),('offHostConfirmed',False)]:
            with self.subTest(field=field):
                changed={**self.config,field:value};atomic_json(self.root/'config/backup.json',changed)
                with self.assertRaises(ValueError):maintenance.load_backup(self.root)
        atomic_json(self.root/'config/backup.json',self.config)
        p=Path(self.config['repositoryFile'])
        for value in ['/local/repository','local:/tmp/repo','rest:http://example.invalid/ctc','sftp:localhost:/ctc','sftp:backup@127.0.0.1:/ctc','sftp:/tmp/repo']:
            p.write_text(value)
            with self.assertRaisesRegex(ValueError,'off-host'):maintenance.load_backup(self.root)
        p.write_text('sftp:backup@example.invalid:/ctc');p.chmod(0o644)
        with self.assertRaisesRegex(ValueError,'private'):maintenance.load_backup(self.root)

    def test_empty_password_unexecutable_hook_or_outside_secret_refuses(self):
        password=Path(self.config['passwordFile']);password.write_text('')
        with self.assertRaisesRegex(ValueError,'password'):maintenance.load_backup(self.root)
        password.write_text('private-key')
        hook=Path(self.config['alertHook']);hook.chmod(0o600)
        with self.assertRaisesRegex(ValueError,'executable'):maintenance.load_backup(self.root)
        hook.chmod(0o700)
        outside=self.root.parent/'outside';outside.write_text('private-key');outside.chmod(0o600)
        atomic_json(self.root/'config/backup.json',{**self.config,'passwordFile':str(outside)})
        with self.assertRaisesRegex(ValueError,'inside'):maintenance.load_backup(self.root)

    def test_environment_cannot_override_command_or_restic_identity(self):
        p=Path(self.config['environmentFile'])
        for key in ['RESTIC_PASSWORD_COMMAND','RESTIC_REPOSITORY','LD_PRELOAD','PATH']:
            atomic_json(p,{key:'private-value'})
            with self.assertRaisesRegex(ValueError,'environment'):maintenance.load_backup(self.root)
        for value in [1,'invalid\x00value']:
            atomic_json(p,{'AWS_SECRET_ACCESS_KEY':value})
            with self.assertRaisesRegex(ValueError,'environment'):maintenance.load_backup(self.root)

    def test_retention_keeps_calendar_union_newest_and_unreplicated(self):
        units=[self.make_unit(age) for age in range(0,550,7)]
        state=self.evidence(units);state['replicas'].pop(str(units[-1]))
        plan=maintenance.retention_plan(self.runtime,self.units,state,self.now)
        self.assertNotIn(units[0],plan)
        self.assertNotIn(units[-1],plan)
        self.assertGreater(len(plan),20)
        kept=set(units)-set(plan)
        self.assertGreaterEqual(len(kept),14)

    def test_protected_and_unresolved_update_units_never_prune(self):
        units=[self.make_unit(age) for age in range(0,550,7)]
        state=self.evidence(units)
        atomic_json(self.root/'state/protected-recovery.json',{'unit':str(units[-1]),
                    'manifestSha256':sha256(units[-1]/'manifest.json'),'previousUnits':[str(units[-2])]})
        atomic_json(self.root/'state/update.json',{'stage':'failed-recovery-required','recoveryUnit':str(units[-3])})
        plan=maintenance.retention_plan(self.runtime,self.units,state,self.now)
        self.assertTrue(set(units[-3:]).isdisjoint(plan))

    def test_unknown_corrupt_foreign_or_latest_failed_prevents_pruning(self):
        unit=self.make_unit();state=self.evidence([unit])
        (self.units/'unknown').mkdir(mode=0o700)
        with self.assertRaisesRegex(ValueError,'unknown'):maintenance.retention_plan(self.runtime,self.units,state,self.now)
        (self.units/'unknown').rmdir()
        (unit/'database.sql').write_text('corrupted')
        with self.assertRaises(ValueError):maintenance.retention_plan(self.runtime,self.units,state,self.now)
        shutil.rmtree(unit);unit=self.make_unit();state=self.evidence([unit],status='replication-failed')
        with self.assertRaisesRegex(ValueError,'failed'):maintenance.retention_plan(self.runtime,self.units,state,self.now)
        state=self.evidence([unit]);self.runtime.config['installationId']=str(uuid.uuid4())
        with self.assertRaisesRegex(ValueError,'installation'):maintenance.retention_plan(self.runtime,self.units,state,self.now)

    def test_changed_replica_identity_refuses(self):
        unit=self.make_unit();state=self.evidence([unit]);state['replicas'][str(unit)]['manifestSha256']='0'*64
        with self.assertRaisesRegex(ValueError,'replica'):maintenance.retention_plan(self.runtime,self.units,state,self.now)

    def test_changed_protected_reference_aborts_retention(self):
        unit=self.make_unit();state=self.evidence([unit])
        atomic_json(self.root/'state/protected-recovery.json',{'unit':str(unit),'manifestSha256':'0'*64,'previousUnits':[]})
        with self.assertRaisesRegex(ValueError,'protected'):maintenance.retention_plan(self.runtime,self.units,state,self.now)

    def test_mount_failure_prevents_capture(self):
        self.runtime.check_destination=lambda path: (_ for _ in ()).throw(ValueError('wrong mount'))
        capture=lambda *a: self.fail('capture must not run with wrong mount')
        with self.assertRaises(ValueError):
            maintenance.run_backup(self.runtime,self.units,self.remote,capture_unit=capture,alert=self.alerts.append,now=self.now)
        self.assertEqual('mount-failed',maintenance.evidence(self.root)['lastAttempt']['stage'])
        self.assertEqual(1,len(self.alerts))

    def test_run_records_local_proof_remote_completion_separately(self):
        unit=self.make_unit();trace=[]
        def capture(*args):trace.append('capture');return unit
        def prove(*args):trace.append('proof');return {'status':'passed','unitId':validate_unit(unit)['id'],'manifestSha256':sha256(unit/'manifest.json'),'outboundEnabled':False,'credentialsVerified':True,'hostPorts':[]}
        def replicate(unit,manifest):trace.append('replicate');return self.receipt(unit)
        self.remote.backup=replicate
        with patch.object(maintenance,'prune',return_value=[]):
            result=maintenance.run_backup(self.runtime,self.units,self.remote,capture_unit=capture,prove_unit=prove,alert=self.alerts.append,now=self.now)
        self.assertEqual(['capture','proof','replicate'],trace)
        self.assertEqual('completed',result['lastAttempt']['stage'])
        self.assertEqual(self.now,result['local']['completedAt'])
        self.assertIn(str(unit),result['replicas'])
        self.assertEqual([],self.alerts)

    def test_attempt_is_incomplete_before_capture_so_interruptions_cannot_look_green(self):
        self.evidence([])
        stages=[]
        def interrupted(*args):
            stages.append(maintenance.evidence(self.root)['lastAttempt']['stage'])
            raise KeyboardInterrupt()
        with self.assertRaises(KeyboardInterrupt):
            maintenance.run_backup(self.runtime,self.units,self.remote,capture_unit=interrupted,alert=self.alerts.append,now=self.now)
        self.assertNotEqual(['completed'],stages)
        self.assertIn('last-attempt-failed',maintenance.freshness(maintenance.evidence(self.root),self.now))

    def test_missing_source_identity_refuses_before_capture(self):
        self.runtime.config.pop('installationId')
        with self.assertRaisesRegex(ValueError,'identity'):
            maintenance.run_backup(self.runtime,self.units,self.remote,capture_unit=lambda *a:self.fail('must not capture unknown installation'),alert=self.alerts.append,now=self.now)

    def test_failed_replication_preserves_local_success_and_does_not_prune(self):
        unit=self.make_unit();self.remote.backup=lambda *a: (_ for _ in ()).throw(ValueError('private-password'))
        proof={'status':'passed','unitId':validate_unit(unit)['id'],'manifestSha256':sha256(unit/'manifest.json'),'outboundEnabled':False,'credentialsVerified':True,'hostPorts':[]}
        with patch.object(maintenance,'prune',side_effect=AssertionError('must not prune failed run')):
            with self.assertRaisesRegex(ValueError,'replication-failed'):
                maintenance.run_backup(self.runtime,self.units,self.remote,capture_unit=lambda *a:unit,prove_unit=lambda *a:proof,alert=self.alerts.append,now=self.now)
        state=maintenance.evidence(self.root)
        self.assertIn('local',state);self.assertNotIn('remote',state)
        self.assertNotIn('private-password',json.dumps(state)+json.dumps(self.alerts))
        self.assertTrue(unit.exists())

    def test_wrong_restore_proof_aborts_replication(self):
        unit=self.make_unit();self.remote.backup=lambda *a:self.fail('must not replicate unproved unit')
        with self.assertRaisesRegex(ValueError,'proof-failed'):
            maintenance.run_backup(self.runtime,self.units,self.remote,capture_unit=lambda *a:unit,prove_unit=lambda *a:{'status':'passed'},alert=self.alerts.append,now=self.now)

    def test_each_restore_proof_field_is_required_before_replication(self):
        unit=self.make_unit();proof={'status':'passed','unitId':validate_unit(unit)['id'],'manifestSha256':sha256(unit/'manifest.json'),'outboundEnabled':False,'credentialsVerified':True,'hostPorts':[]}
        for key,value in [('status','failed'),('unitId','wrong'),('manifestSha256','0'*64),('outboundEnabled',True),('credentialsVerified',False),('hostPorts',[9092])]:
            with self.subTest(field=key):
                changed={**proof,key:value}
                self.assertFalse(maintenance.proof_matches(changed,unit,validate_unit(unit)))
        self.assertTrue(maintenance.proof_matches(proof,unit,validate_unit(unit)))

    def test_freshness_distinguishes_missing_stale_and_failed_evidence(self):
        self.assertIn('local-missing',maintenance.freshness({},self.now))
        state=self.evidence([]);self.assertEqual([],maintenance.freshness(state,self.now))
        state['local']['completedAt']=self.now-31*3600
        self.assertIn('local-stale',maintenance.freshness(state,self.now))
        state['lastAttempt']['stage']='replication-failed'
        self.assertIn('last-attempt-failed',maintenance.freshness(state,self.now))
        state['remote']['completedAt']=float('nan')
        self.assertIn('remote-invalid',maintenance.freshness(state,self.now))
        state['remote']['completedAt']=self.now+120
        self.assertIn('remote-invalid',maintenance.freshness(state,self.now))

    def test_doctor_returns_failure_for_missing_configuration_or_stale_evidence(self):
        self.evidence([])
        argv=['maintenance.py','--directory',str(self.root),'doctor']
        with patch.object(sys,'argv',argv),patch.object(maintenance.time,'time',return_value=self.now):maintenance.main()
        (self.root/'config/backup.json').unlink()
        with patch.object(sys,'argv',argv),patch.object(maintenance.time,'time',return_value=self.now),self.assertRaisesRegex(ValueError,'readiness'):maintenance.main()
        atomic_json(self.root/'config/backup.json',self.config)
        state=self.evidence([]);state['local']['completedAt']=0;atomic_json(self.root/'state/backups.json',state)
        with patch.object(sys,'argv',argv),patch.object(maintenance.time,'time',return_value=self.now),self.assertRaisesRegex(ValueError,'readiness'):maintenance.main()

    def test_historical_remote_unit_uses_matching_trusted_installed_release(self):
        unit=self.make_unit();expected=json.loads((unit/'release.json').read_text())
        old=self.root/'releases/old';proof_runtime=object();seen=[]
        def prove(runtime,directory):
            seen.append(runtime)
            return {'status':'passed','unitId':validate_unit(directory)['id'],'manifestSha256':sha256(directory/'manifest.json'),'outboundEnabled':False,'credentialsVerified':True,'hostPorts':[]}
        with patch.object(maintenance,'select_recovery_release',return_value=old) as select,patch.object(maintenance,'NativeRuntime',return_value=proof_runtime) as construct:
            maintenance.prove_retrieved(self.runtime,unit,prove_unit=prove)
        select.assert_called_once_with(self.root,expected)
        construct.assert_called_once_with(self.root,old,require_selected=False)
        self.assertEqual([proof_runtime],seen)

    def test_operation_specific_flags_cannot_be_silently_ignored(self):
        for operation,flags in [('maintenance',['--dry-run']),('prune',['--unit','/wrong']),('retrieve-check',[]),('backup-check',['--source','anything'])]:
            with self.subTest(operation=operation),patch.object(sys,'argv',['maintenance.py','--directory',str(self.root),operation,*flags]):
                with self.assertRaises(SystemExit) as caught:maintenance.main()
                self.assertEqual(2,caught.exception.code)

    def test_systemd_units_are_private_persistent_and_use_current_rootless_launcher(self):
        root=ROOT/'deploy/systemd'
        timer=(root/'ctc-manager-backup.timer').read_text()
        self.assertIn('03:45:00 Europe/Berlin',timer);self.assertIn('RandomizedDelaySec=15m',timer);self.assertIn('Persistent=true',timer)
        for name in ['ctc-manager-backup.service','ctc-manager-backup-check.service']:
            text=(root/name).read_text();self.assertIn('UMask=0077',text);self.assertIn('/srv/ctc-manager/current/ctc-manager',text)
            self.assertIn('OnFailure=ctc-manager-backup-alert@%n.service',text)
            self.assertNotIn('User=root',text)


    def test_repository_identity_and_snapshot_binding_are_enforced(self):
        unit=self.make_unit();receipt=self.receipt(unit)
        remote=maintenance.Restic(self.root,self.config)
        metadata=validate_unit(unit)
        snapshot={'id':receipt['snapshot'],'paths':[str(unit)],'tags':remote.tags(metadata,receipt['manifestSha256'])}
        remote.command=lambda args,**kwargs: json.dumps({'id':self.config['repositoryId']}) if args[0]=='cat' else json.dumps([snapshot])
        remote.verify();remote.confirm(unit,receipt)
        for key,value in [('manifestSha256','0'*64),('unitId','wrong'),('unitPath','/wrong'),('repositoryId','0'*64),('snapshot','short')]:
            with self.subTest(receiptField=key):
                with self.assertRaisesRegex(ValueError,'replica'):remote.confirm(unit,{**receipt,key:value})
        for field,value in [('id','e'*64),('paths',['/another/unit']),('tags',[])]:
            with self.subTest(field=field):
                changed={**snapshot,field:value};remote.command=lambda args,**kwargs:json.dumps([changed])
                with self.assertRaisesRegex(ValueError,'snapshot'):remote.confirm(unit,receipt)
        remote.command=lambda args,**kwargs:json.dumps({'id':'0'*64})
        with self.assertRaisesRegex(ValueError,'repository'):remote.verify()

    def test_remote_backup_requires_summary_and_verified_exact_snapshot(self):
        unit=self.make_unit();remote=maintenance.Restic(self.root,self.config);calls=[]
        manifest=validate_unit(unit);snapshot={'id':'d'*64,'paths':[str(unit)],'tags':remote.tags(manifest,sha256(unit/'manifest.json'))}
        def command(args,**options):
            calls.append(args)
            return json.dumps({'message_type':'summary','snapshot_id':'d'*64}) if args[0]=='backup' else json.dumps([snapshot])
        remote.command=command
        receipt=remote.backup(unit,manifest)
        self.assertEqual('d'*64,receipt['snapshot']);self.assertEqual('snapshots',calls[-1][0])
        remote.command=lambda *a,**kw:json.dumps({'message_type':'status'})
        with self.assertRaisesRegex(ValueError,'summary'):remote.backup(unit,manifest)

    def test_remote_command_does_not_leak_credentials_or_stderr(self):
        remote=maintenance.Restic(self.root,self.config)
        def run(args,**options):
            self.assertNotIn('private-key',str(args))
            self.assertEqual(self.config['passwordFile'],options['env']['RESTIC_PASSWORD_FILE'])
            self.assertNotIn('RESTIC_PASSWORD',options['env'])
            options['stderr'].write(b'private-key diagnostic')
            return type('Result',(),{'returncode':1})()
        with patch.object(maintenance.subprocess,'run',side_effect=run):
            with self.assertRaises(ValueError) as caught:remote.command(['cat','config'])
        self.assertNotIn('private-key',str(caught.exception))

    def test_restic_stderr_does_not_corrupt_successful_json_output(self):
        remote=maintenance.Restic(self.root,self.config)
        def run(args,**options):
            options['stdout'].write(b'{"id":"expected"}')
            options['stderr'].write(b'repository opened\n')
            return type('Result',(),{'returncode':0})()
        with patch.object(maintenance.subprocess,'run',side_effect=run):
            self.assertEqual({'id':'expected'},json.loads(remote.command(['cat','config'])))

    def test_failed_remote_confirmation_never_deletes_local_units(self):
        units=[self.make_unit(age) for age in range(0,550,7)];self.evidence(units)
        self.remote.confirm=lambda *a:(_ for _ in ()).throw(ValueError('remote missing'))
        with self.assertRaisesRegex(ValueError,'remote missing'):maintenance.prune(self.runtime,self.units,self.remote,now=self.now)
        self.assertTrue(all(u.exists() for u in units))

    def test_dry_run_keeps_all_files_and_successful_prune_removes_only_plan(self):
        units=[self.make_unit(age) for age in range(0,550,7)];self.evidence(units)
        plan=maintenance.prune(self.runtime,self.units,self.remote,dry_run=True,now=self.now)
        self.assertTrue(plan);self.assertTrue(all(u.exists() for u in units))
        removed=maintenance.prune(self.runtime,self.units,self.remote,now=self.now)
        self.assertEqual(plan,removed)
        self.assertTrue(all(not u.exists() for u in removed))
        self.assertTrue((set(units)-set(removed)).issubset(set(self.units.iterdir())))

    def test_alert_hook_failure_is_nonzero_and_receipt_contains_no_secrets(self):
        def run(args,**options):
            self.assertEqual([self.config['alertHook'],str(self.root/'state/backup-alert.json')],args)
            self.assertEqual(maintenance.subprocess.DEVNULL,options['stdout'])
            return type('Result',(),{'returncode':7})()
        with patch.object(maintenance.subprocess,'run',side_effect=run):
            with self.assertRaisesRegex(ValueError,'alert delivery'):maintenance.notify(self.root,self.config,'replication-failed')
        value=(self.root/'state/backup-alert.json').read_text()
        self.assertNotIn('private-key',value);self.assertIn('replication-failed',value)

    def test_retrieval_uses_relative_snapshot_tree_and_preserves_complete_unit(self):
        unit=self.make_unit();remote=maintenance.Restic(self.root,self.config);receipt=self.receipt(unit)
        remote.verify=lambda:None;remote.confirm=lambda *a:None
        staging=self.root/'state/retrieved';staging.mkdir(mode=0o700)
        def restore(args,**options):
            self.assertEqual(['restore',receipt['snapshot']+':/'+unit.name,'--target',str(staging)],args)
            for p in unit.iterdir():shutil.copyfile(p,staging/p.name);(staging/p.name).chmod(0o600)
            return ''
        remote.command=restore;remote.retrieve(unit,receipt,staging)
        self.assertEqual(validate_unit(unit),validate_unit(staging))

    def test_retrieval_rejects_changed_manifest_after_remote_restore(self):
        unit=self.make_unit();remote=maintenance.Restic(self.root,self.config);receipt=self.receipt(unit)
        remote.verify=lambda:None;remote.confirm=lambda *a:None
        staging=self.root/'state/retrieved';staging.mkdir(mode=0o700)
        remote.command=lambda *a,**kw: (staging/'manifest.json').write_text('{}')
        with self.assertRaisesRegex(ValueError,'retrieved manifest'):remote.retrieve(unit,receipt,staging)


if __name__=='__main__':unittest.main()
