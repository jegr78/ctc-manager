#!/usr/bin/env python3
"""Qualify encrypted retrieval on the empty host stack without claiming off-host readiness."""
import argparse
import json
from pathlib import Path
import secrets
import shutil
import sys
import tempfile

sys.path.insert(0, str(Path(__file__).resolve().parent))
from native_update import assert_synthetic
from config import atomic_json
from maintenance import Restic, prove_retrieved
from recovery import NativeRuntime, validate_unit


def main():
    parser = argparse.ArgumentParser(allow_abbrev=False)
    parser.add_argument('--unit', required=True)
    args = parser.parse_args()
    root = Path('/srv/ctc-manager')
    runtime = NativeRuntime(root, (root / 'current').resolve())
    assert_synthetic(runtime)
    destination = Path('/mnt/backup/ctc-manager/qualification-units')
    runtime.check_destination(destination)
    unit = Path(args.unit)
    manifest = validate_unit(unit)
    assert manifest['releaseVersion'] != runtime.release['version'], 'qualify historical retrieval under a newer active release'
    directory = Path(tempfile.mkdtemp(prefix='restic-interface-', dir=destination.parent))
    directory.chmod(0o700)
    try:
        for name, value in [('repository', str(directory / 'encrypted-repository')),
                            ('password', secrets.token_urlsafe(48)), ('environment.json', '{}')]:
            artifact = directory / name
            artifact.write_text(value)
            artifact.chmod(0o600)
        config = {'repositoryFile': str(directory / 'repository'), 'passwordFile': str(directory / 'password'),
                  'environmentFile': str(directory / 'environment.json'), 'repositoryId': ''}
        remote = Restic(root, config)
        remote.command(['init', '--json'])
        config['repositoryId'] = json.loads(remote.command(['cat', 'config']))['id']
        remote = Restic(root, config)
        remote.verify()
        receipt = remote.backup(unit, manifest)
        staging = directory / 'retrieved'
        staging.mkdir(mode=0o700)
        remote.retrieve(unit, receipt, staging)
        assert validate_unit(staging) == manifest, 'encrypted retrieval must preserve sealed provenance'
        assert all((staging / p.name).read_bytes() == p.read_bytes() for p in unit.iterdir()), 'every recovered artifact must match'
        proof = prove_retrieved(runtime, staging)
        runtime.preflight()
        result = {'status': 'passed', 'activeVersion': runtime.release['version'],
                  'recoveredUnitVersion': manifest['releaseVersion'], 'exactEncryptedRetrieval': True,
                  'matchingTrustedSourceRelease': True, 'credentialsVerified': proof['credentialsVerified'],
                  'outboundEnabled': proof['outboundEnabled'], 'hostPorts': proof['hostPorts'],
                  'productionOffHostAcceptance': False}
        atomic_json(root / 'state/native-maintenance-result.json', result)
        print(json.dumps(result))
    finally:
        shutil.rmtree(directory)


if __name__ == '__main__':
    main()
