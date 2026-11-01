#!/usr/bin/env python3
"""Exercise systemd OnFailure with a private local-file-only qualification alert hook."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import time
import uuid

sys.path.insert(0, str(Path(__file__).resolve().parent))
from native_update import assert_synthetic
from config import atomic_json
from recovery import NativeRuntime


def main():
    root = Path('/srv/ctc-manager')
    runtime = NativeRuntime(root, (root / 'current').resolve())
    assert_synthetic(runtime)
    directory = Path(tempfile.mkdtemp(prefix='native-alert-', dir=root / 'state'))
    units = Path(os.environ['XDG_RUNTIME_DIR']) / 'systemd/user'
    units.mkdir(mode=0o700, parents=True, exist_ok=True)
    name = 'ctc-manager-native-alert-' + uuid.uuid4().hex
    failed = name + '-failure.service'
    alert = name + '-delivery.service'
    files = [units / failed, units / alert]
    try:
        for child in ['config', 'secrets', 'state']:
            (directory / child).mkdir(mode=0o700)
        for artifact, value in [('repository', 'sftp:backup@example.invalid:/qualification'),
                                ('password', 'synthetic-test-password'), ('environment.json', '{}')]:
            path = directory / 'secrets' / artifact
            path.write_text(value)
            path.chmod(0o600)
        received = directory / 'received.json'
        hook = directory / 'secrets/alert'
        hook.write_text('#!/usr/bin/python3\nimport json,sys\nfrom pathlib import Path\n'
                        + 'value=json.loads(Path(sys.argv[1]).read_text())\n'
                        + 'Path(' + repr(str(received)) + ').write_text(json.dumps(value))\n')
        hook.chmod(0o700)
        atomic_json(directory / 'config/backup.json', {'format': 1,
            'repositoryFile': str(directory / 'secrets/repository'), 'passwordFile': str(directory / 'secrets/password'),
            'environmentFile': str(directory / 'secrets/environment.json'), 'repositoryId': 'a' * 64,
            'alertHook': str(hook), 'keyCustodyConfirmed': True, 'offHostConfirmed': True})
        controller = Path(__file__).resolve().parents[1] / 'lib/maintenance.py'
        files[0].write_text('[Unit]\nDescription=CTC qualification failure\nOnFailure=' + alert + '\n'
                            '[Service]\nType=oneshot\nExecStart=/usr/bin/false\nUMask=0077\n')
        files[1].write_text('[Unit]\nDescription=CTC qualification local alert\n[Service]\nType=oneshot\n'
                            'ExecStart=/usr/bin/python3 ' + str(controller) + ' --directory ' + str(directory)
                            + ' alert\nUMask=0077\nNoNewPrivileges=true\n')
        subprocess.run(['systemctl', '--user', 'daemon-reload'], check=True, capture_output=True)
        result = subprocess.run(['systemctl', '--user', 'start', failed], capture_output=True)
        assert result.returncode != 0, 'qualification service must actually fail'
        deadline = time.monotonic() + 15
        while not received.exists() and time.monotonic() < deadline:
            time.sleep(0.1)
        assert received.exists(), 'real systemd OnFailure must deliver the private local alert'
        value = json.loads(received.read_text())
        assert value['source'] == 'ctc-manager-backups' and value['stage'] == 'scheduled-service-failed', 'alert hook must receive safe structured failure metadata'
        assert 'synthetic-test-password' not in received.read_text(), 'alert payload must exclude credentials'
        result = {'status': 'passed', 'realSystemdOnFailure': True, 'localFileOnly': True,
                  'externalMessageSent': False, 'productionAlertAcceptance': False}
        atomic_json(root / 'state/native-alert-result.json', result)
        print(json.dumps(result))
    finally:
        subprocess.run(['systemctl', '--user', 'stop', failed, alert], capture_output=True)
        subprocess.run(['systemctl', '--user', 'reset-failed', failed, alert], capture_output=True)
        for path in files:
            path.unlink(missing_ok=True)
        subprocess.run(['systemctl', '--user', 'daemon-reload'], check=True, capture_output=True)
        shutil.rmtree(directory)


if __name__ == '__main__':
    main()
