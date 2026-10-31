import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
UUID = '31d7af2e-b6de-4680-9dfb-692ffb692fc4'
DNS = 'jegr-linux-box.example.ts.net'


def facts():
    return {
        'system': 'Linux', 'architecture': 'x86_64', 'missingTools': [], 'sudoAvailable': True,
        'operatorUid': 1000, 'user': {'uid': 1002, 'gid': 1002, 'home': '/home/ctc-manager'},
        'duplicateUid': False, 'duplicateGid': False, 'groups': ['ctc-manager'],
        'hostUids': [0,1000,1001,1002], 'hostGids': [0,1000,1001,1002],
        'subuid': 'jegr:100000:65536\ncourtside-dev:165536:65536\nctc-manager:231072:65536\n',
        'subgid': 'jegr:100000:65536\ncourtside-dev:165536:65536\nctc-manager:231072:65536\n',
        'directories': {
            '/srv': {'uid': 0, 'gid': 0, 'mode': 0o755, 'type': 'directory'},
            '/mnt/backup': {'uid': 0, 'gid': 0, 'mode': 0o755, 'type': 'directory'},
            '/srv/ctc-manager': {'uid': 1002, 'gid': 1002, 'mode': 0o700, 'type': 'directory'},
            '/home/ctc-manager': {'uid': 1002, 'gid': 1002, 'mode': 0o700, 'type': 'directory'},
            '/mnt/backup/ctc-manager': None,
        },
        'backupMount': {'target': '/mnt/backup', 'uuid': UUID, 'fstype': 'btrfs'},
        'listeners': ['127.0.0.1:8080', '100.125.21.47:443', '100.125.21.47:8443'],
        'ownAppListening': False, 'dnsName': DNS, 'tailscaleRunning': True,
        'tailscaleIPs': ['100.125.21.47','fd7a:115c:a1e0::1'],
        'serve': {
            'TCP': {'443': {'HTTPS': True}, '8443': {'HTTPS': True}},
            'Web': {DNS+':443': {'Handlers': {'/': {'Proxy': 'http://127.0.0.1:8080'}}},
                    DNS+':8443': {'Handlers': {'/': {'Proxy': 'http://127.0.0.1:8025'}}}},
            'AllowFunnel': {DNS+':443': True},
        },
        'socket': {'uid': 1002, 'type': 'socket', 'mode': 0o660},
        'runtimeDirectory': {'uid': 1002, 'type': 'directory', 'mode': 0o700},
        'docker': {'rootless': True, 'memory': True},
        'cgroupMemory': True, 'memoryTotal': 16*1024**3, 'memoryAvailable': 7*1024**3,
    }


class HostSetupTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location('ctc_host', ROOT/'deploy/lib/host.py')
        cls.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.module)

    def setUp(self):
        self.data = facts()

    def validate(self):
        return self.module.validate(self.data, UUID)

    def test_existing_private_rootless_host_is_accepted(self):
        result = self.validate()
        self.assertEqual(1002, result['uid'])
        self.assertEqual(DNS, result['dnsName'])
        self.assertFalse(result['serveReady'])

    def test_new_user_can_be_provisioned_without_touching_existing_ranges(self):
        self.data.update(user=None, duplicateUid=False, groups=[], socket=None,
                         runtimeDirectory=None, docker=None)
        for key in ['/srv/ctc-manager', '/home/ctc-manager']:
            self.data['directories'][key] = None
        self.data['subuid'] = self.data['subuid'].replace('ctc-manager:231072:65536\n', '')
        self.data['subgid'] = self.data['subgid'].replace('ctc-manager:231072:65536\n', '')
        result = self.validate()
        self.assertIsNone(result['uid'])

    def test_privileged_group_membership_is_rejected(self):
        self.data['groups'].append('docker')
        with self.assertRaisesRegex(ValueError, 'privileged'):
            self.validate()

    def test_duplicate_or_root_uid_is_rejected(self):
        self.data['duplicateUid'] = True
        with self.assertRaisesRegex(ValueError, 'UID'):
            self.validate()
        self.data['duplicateUid'] = False
        self.data['user']['uid'] = 0
        with self.assertRaisesRegex(ValueError, 'UID'):
            self.validate()

    def test_subordinate_uid_must_not_map_a_host_identity(self):
        self.data['subuid'] = 'ctc-manager:1:65536\n'
        with self.assertRaisesRegex(ValueError, 'host identity'):
            self.validate()

    def test_subordinate_gid_must_not_map_a_host_identity(self):
        self.data['subgid'] = 'ctc-manager:1:65536\n'
        with self.assertRaisesRegex(ValueError, 'host identity'):
            self.validate()

    def test_tailscale_interface_scoped_listener_is_accepted(self):
        self.data['serve']['TCP']['9443'] = {'HTTPS': True}
        self.data['serve']['Web'][DNS+':9443'] = {'Handlers': {'/': {'Proxy': 'http://127.0.0.1:9092'}}}
        self.data['listeners'] += ['100.125.21.47%tailscale0:9443', '[fd7a:115c:a1e0::1]%tailscale0:9443']
        self.assertTrue(self.validate()['serveReady'])

    def test_insufficient_host_memory_is_rejected(self):
        self.data['memoryTotal'] = 2*1024**3
        with self.assertRaisesRegex(ValueError, 'memory capacity'):
            self.validate()

    def test_missing_cgroup_memory_delegation_is_rejected(self):
        self.data['cgroupMemory'] = False
        with self.assertRaisesRegex(ValueError, 'delegation'):
            self.validate()

    def test_insufficient_installation_storage_is_rejected(self):
        self.data['diskFree'] = 1024
        with self.assertRaisesRegex(ValueError, 'storage'):
            self.validate()

    def test_offline_tailnet_is_rejected(self):
        self.data['tailscaleRunning'] = False
        with self.assertRaisesRegex(ValueError, 'Tailscale'):
            self.validate()

    def test_public_rootless_runtime_directory_is_rejected(self):
        self.data['runtimeDirectory']['mode'] = 0o755
        with self.assertRaisesRegex(ValueError, 'runtime directory'):
            self.validate()

    def test_operator_setup_source_must_be_private_and_not_linked(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in ['lib/host.py', 'setup-host.sh', 'systemd/docker-rootless.service']:
                path = root/name
                path.parent.mkdir(parents=True,exist_ok=True)
                path.write_text('unit')
            with patch.object(self.module, 'ROOT', root):
                self.assertEqual('unit',self.module.trusted_unit())
                (root/'lib/host.py').chmod(0o666)
                with self.assertRaisesRegex(ValueError,'operator-owned'):
                    self.module.trusted_unit()
                (root/'lib/host.py').unlink()
                (root/'lib/host.py').symlink_to(root/'setup-host.sh')
                with self.assertRaisesRegex(ValueError,'operator-owned'):
                    self.module.trusted_unit()

    def test_primary_group_must_have_a_unique_non_root_gid(self):
        self.data['user']['gid'] = 0
        with self.assertRaisesRegex(ValueError, 'GID'):
            self.validate()
        self.data['user']['gid'] = 1002
        self.data['duplicateGid'] = True
        with self.assertRaisesRegex(ValueError, 'GID'):
            self.validate()

    def test_numeric_subordinate_owner_is_checked(self):
        self.data['subuid'] += '1002:1:65536\n'
        with self.assertRaisesRegex(ValueError, 'host identity'):
            self.validate()

    def test_unexpected_home_is_rejected(self):
        self.data['user']['home'] = '/root'
        with self.assertRaisesRegex(ValueError, 'home'):
            self.validate()

    def test_subordinate_range_overlap_is_rejected(self):
        self.data['subuid'] = self.data['subuid'].replace('231072', '231071')
        with self.assertRaisesRegex(ValueError, 'overlap'):
            self.validate()

    def test_missing_subordinate_group_capacity_is_rejected(self):
        self.data['subgid'] = self.data['subgid'].replace('ctc-manager:231072:65536', 'ctc-manager:231072:10')
        with self.assertRaisesRegex(ValueError, '65536'):
            self.validate()

    def test_wrong_backup_mount_is_rejected(self):
        self.data['backupMount']['target'] = '/'
        with self.assertRaisesRegex(ValueError, 'mount'):
            self.validate()
        self.data['backupMount']['target'] = '/mnt/backup'
        self.data['backupMount']['uuid'] = '11111111-2222-3333-4444-555555555555'
        with self.assertRaisesRegex(ValueError, 'UUID'):
            self.validate()

    def test_foreign_or_linked_directory_is_rejected(self):
        self.data['directories']['/srv/ctc-manager']['uid'] = 1001
        with self.assertRaisesRegex(ValueError, 'owner'):
            self.validate()
        self.data['directories']['/srv/ctc-manager']['uid'] = 1002
        self.data['directories']['/srv/ctc-manager']['type'] = 'symbolic link'
        with self.assertRaisesRegex(ValueError, 'directory'):
            self.validate()

    def test_writable_backup_parent_is_rejected(self):
        self.data['directories']['/mnt/backup']['mode'] = 0o777
        with self.assertRaisesRegex(ValueError, 'parent'):
            self.validate()

    def test_foreign_app_port_is_rejected(self):
        self.data['listeners'].append('127.0.0.1:9092')
        with self.assertRaisesRegex(ValueError, '9092'):
            self.validate()
        self.data['ownAppListening'] = True
        self.validate()

    def test_foreign_https_port_is_rejected(self):
        self.data['listeners'].append('100.125.21.47:9443')
        with self.assertRaisesRegex(ValueError, '9443'):
            self.validate()

    def test_existing_exact_private_serve_route_is_idempotent(self):
        self.data['serve']['TCP']['9443'] = {'HTTPS': True}
        self.data['serve']['Web'][DNS+':9443'] = {'Handlers': {'/': {'Proxy': 'http://127.0.0.1:9092'}}}
        self.data['listeners'].append('100.125.21.47:9443')
        self.assertTrue(self.validate()['serveReady'])
        self.assertTrue(self.data['serve']['AllowFunnel'][DNS+':443'])

    def test_public_listener_cannot_hide_behind_existing_private_mapping(self):
        self.data['serve']['TCP']['9443'] = {'HTTPS':True}
        self.data['serve']['Web'][DNS+':9443'] = {'Handlers':{'/':{'Proxy':'http://127.0.0.1:9092'}}}
        self.data['listeners'].append('192.168.178.38:9443')
        with self.assertRaisesRegex(ValueError,'9443'):
            self.validate()

    def test_ctc_funnel_is_rejected(self):
        self.data['serve']['AllowFunnel'][DNS+':9443'] = True
        with self.assertRaisesRegex(ValueError, 'Funnel'):
            self.validate()

    def test_wrong_socket_owner_or_type_is_rejected(self):
        self.data['socket']['uid'] = 1001
        with self.assertRaisesRegex(ValueError, 'socket'):
            self.validate()
        self.data['socket'].update(uid=1002, type='regular file')
        with self.assertRaisesRegex(ValueError, 'socket'):
            self.validate()

    def test_rootful_or_unbounded_daemon_is_rejected(self):
        self.data['docker']['rootless'] = False
        with self.assertRaisesRegex(ValueError, 'rootless'):
            self.validate()
        self.data['docker'].update(rootless=True, memory=False)
        with self.assertRaisesRegex(ValueError, 'memory'):
            self.validate()

    def test_missing_tools_or_wrong_system_is_rejected(self):
        self.data['missingTools'] = ['dockerd-rootless.sh']
        with self.assertRaisesRegex(ValueError, 'prerequisite'):
            self.validate()
        self.data['missingTools'] = []
        self.data['system'] = 'Darwin'
        with self.assertRaisesRegex(ValueError, 'Linux'):
            self.validate()

    def test_shared_serve_changes_are_detected_without_rollback(self):
        before = self.data['serve']
        after = copy.deepcopy(before)
        after['TCP']['9443'] = {'HTTPS': True}
        after['Web'][DNS+':9443'] = {'Handlers': {'/': {'Proxy': 'http://127.0.0.1:9092'}}}
        self.module.verify_serve_preserved(before, after, DNS)
        after['AllowFunnel'][DNS+':443'] = False
        with self.assertRaisesRegex(ValueError, 'shared'):
            self.module.verify_serve_preserved(before, after, DNS)

class FakeHost:
    def __init__(self, data):
        self.data = copy.deepcopy(data)
        self.calls = []
        self.files = {}
        self.locked = False
        self.unit = 'known unit\n'
        self.unit_path = '/home/ctc-manager/.config/systemd/user/docker.service'
        self.files[self.unit_path] = self.unit

    def collect(self):
        return copy.deepcopy(self.data)

    def execute(self, argv, input=None, required=True):
        import subprocess
        self.calls.append((list(argv), input))
        args = list(argv)
        user_scoped = '-u' in args and args[args.index('-u')+1] == 'ctc-manager'
        if args[0] == 'sudo':
            if user_scoped:
                args = args[args.index('env')+1:]
                while args and '=' in args[0]:
                    args.pop(0)
            else:
                args = args[2:]
        code, output = 0, ''
        if args[0] == 'useradd':
            self.assert_scope(not user_scoped and args[-1] == 'ctc-manager')
            self.assert_scope('HOME_MODE=0700' in args)
            self.data['user'] = {'uid':1002,'gid':1002,'home':'/home/ctc-manager'}
            self.data['groups'] = ['ctc-manager']
            self.data['subuid'] += 'ctc-manager:231072:65536\n'
            self.data['subgid'] += 'ctc-manager:231072:65536\n'
            self.data['directories']['/home/ctc-manager'] = {'uid':1002,'gid':1002,'mode':0o700,'type':'directory'}
        elif args[0] == 'install':
            self.assert_scope(user_scoped or args[-1] in ['/srv/ctc-manager', '/mnt/backup/ctc-manager'])
            for path in args[args.index('0700')+1:]:
                if path.startswith('/'):
                    self.data['directories'][path] = {'uid':1002,'gid':1002,'mode':0o700,'type':'directory'}
        elif args[0] == 'mkdir':
            self.assert_scope(user_scoped and args[-1] == '/srv/ctc-manager/.ctc-manager.lock')
            if self.locked:
                raise subprocess.CalledProcessError(1,argv)
            self.locked = True
        elif args[0] == 'tee':
            self.assert_scope(user_scoped)
            self.files[args[-1]] = input
        elif args[0] == 'cat':
            self.assert_scope(user_scoped)
            if args[-1] in self.files:
                output = self.files[args[-1]]
            else:
                code = 1
        elif args[0] == 'chmod':
            self.assert_scope(user_scoped)
        elif args[0] == 'mv':
            self.assert_scope(user_scoped)
            self.files[args[-1]] = self.files.pop(args[-2])
        elif args[0] == 'rm':
            self.assert_scope(user_scoped)
            for value in args[1:]:
                self.files.pop(value,None)
        elif args[0] == 'rmdir':
            self.assert_scope(user_scoped)
            self.locked = False
        elif args[0] == 'loginctl':
            self.assert_scope(args == ['loginctl','enable-linger','ctc-manager'])
        elif args[0] == 'systemctl':
            self.assert_scope(user_scoped or args == ['systemctl','start','user@1002.service'])
            if user_scoped:
                self.assert_scope('--user' in args)
                if 'enable' in args:
                    self.data['docker'] = {'rootless':True,'memory':True}
                    self.data['socket'] = {'uid':1002,'type':'socket','mode':0o660}
                    self.data['runtimeDirectory'] = {'uid':1002,'type':'directory','mode':0o700}
        elif args[0] == 'tailscale':
            self.assert_scope(args == ['tailscale','serve','--bg','--https=9443','http://127.0.0.1:9092'])
            self.data['serve']['TCP']['9443'] = {'HTTPS':True}
            self.data['serve']['Web'][DNS+':9443'] = {'Handlers':{'/':{'Proxy':'http://127.0.0.1:9092'}}}
            self.data['listeners'].append('100.125.21.47:9443')
        else:
            raise AssertionError('unexpected native mutation: '+str(argv))
        if code and required:
            raise subprocess.CalledProcessError(code,argv)
        return subprocess.CompletedProcess(argv,code,output,'')

    @staticmethod
    def assert_scope(condition):
        if not condition:
            raise AssertionError('mutation escaped the dedicated CTC scope')


class HostApplyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        HostSetupTest.setUpClass.__func__(cls)

    def setUp(self):
        self.data = facts()

    def test_apply_preserves_courtside_and_records_private_host(self):
        fake = FakeHost(self.data)
        result = self.module.apply(self.data,UUID,execute=fake.execute,refresh=fake.collect,unit_text=fake.unit)
        self.assertTrue(result['serveReady'])
        self.assertTrue(fake.data['serve']['AllowFunnel'][DNS+':443'])
        self.assertEqual('http://127.0.0.1:8080', fake.data['serve']['Web'][DNS+':443']['Handlers']['/']['Proxy'])
        self.assertFalse(fake.locked)
        receipt = json.loads(fake.files['/srv/ctc-manager/state/host.json'])
        self.assertEqual(UUID,receipt['backupUuid'])
        self.assertEqual('complete',receipt['status'])

    def test_second_apply_does_not_rewrite_existing_serve_route(self):
        fake = FakeHost(self.data)
        self.module.apply(self.data,UUID,execute=fake.execute,refresh=fake.collect,unit_text=fake.unit)
        fake.calls = []
        self.module.apply(fake.collect(),UUID,execute=fake.execute,refresh=fake.collect,unit_text=fake.unit)
        self.assertFalse(any('tailscale' in args for args,_ in fake.calls))

    def test_foreign_port_refuses_before_any_mutation(self):
        self.data['listeners'].append('127.0.0.1:9092')
        fake = FakeHost(self.data)
        with self.assertRaisesRegex(ValueError,'9092'):
            self.module.apply(self.data,UUID,execute=fake.execute,refresh=fake.collect,unit_text=fake.unit)
        self.assertEqual([],fake.calls)

    def test_existing_lock_is_never_removed_or_bypassed(self):
        fake = FakeHost(self.data)
        fake.locked = True
        with self.assertRaisesRegex(ValueError, "lock"):
            self.module.apply(self.data,UUID,execute=fake.execute,refresh=fake.collect,unit_text=fake.unit)
        self.assertTrue(fake.locked)
        self.assertFalse(any('install' in args for args,_ in fake.calls))
        self.assertFalse(any('tailscale' in args or 'rmdir' in args for args,_ in fake.calls))

    def test_apply_creates_only_the_dedicated_new_user(self):
        self.data.update(user=None,duplicateUid=False,groups=[],socket=None,runtimeDirectory=None,docker=None)
        for path in ['/srv/ctc-manager','/home/ctc-manager']:
            self.data['directories'][path] = None
        self.data['subuid'] = self.data['subuid'].replace('ctc-manager:231072:65536\n','')
        self.data['subgid'] = self.data['subgid'].replace('ctc-manager:231072:65536\n','')
        fake = FakeHost(self.data)
        result = self.module.apply(self.data,UUID,execute=fake.execute,refresh=fake.collect,unit_text=fake.unit)
        self.assertEqual(1002,result['uid'])
        self.assertTrue(result['dockerReady'])
        self.assertIn('courtside-dev:165536:65536',fake.data['subuid'])
        self.assertFalse(fake.locked)

    def test_different_existing_daemon_unit_is_preserved_and_fails(self):
        fake = FakeHost(self.data)
        path = '/home/ctc-manager/.config/systemd/user/docker.service'
        fake.files[path] = 'foreign unit'
        with self.assertRaisesRegex(ValueError,'differs'):
            self.module.apply(self.data,UUID,execute=fake.execute,refresh=fake.collect,unit_text=fake.unit)
        self.assertEqual('foreign unit',fake.files[path])
        self.assertEqual('failed',json.loads(fake.files['/srv/ctc-manager/state/host-setup.json'])['status'])
        self.assertFalse(fake.locked)
        self.assertFalse(any('tailscale' in args for args,_ in fake.calls))
