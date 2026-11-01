#!/usr/bin/env python3
"""Exercise the real restic interface using only a temporary synthetic local repository."""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_maintenance import MaintenanceTest
import maintenance
from recovery import validate_unit


def main():
    case = MaintenanceTest('test_private_remote_configuration_accepts_provisioned_destination')
    case.setUp()
    try:
        unit = case.make_unit()
        Path(case.config['repositoryFile']).write_text(str(case.root.parent / 'isolated-restic-repo'))
        remote = maintenance.Restic(case.root, case.config)
        remote.command(['init', '--json'])
        case.config['repositoryId'] = json.loads(remote.command(['cat', 'config']))['id']
        remote = maintenance.Restic(case.root, case.config)
        remote.verify()
        receipt = remote.backup(unit, validate_unit(unit))
        staging = case.root / 'state/restored'
        staging.mkdir(mode=0o700)
        remote.retrieve(unit, receipt, staging)
        assert validate_unit(staging) == validate_unit(unit), 'real restic must restore identical sealed provenance'
        assert all((staging / p.name).read_bytes() == p.read_bytes() for p in unit.iterdir()), 'every artifact must survive real encrypted backup and retrieval'
        print(json.dumps({'status': 'passed', 'snapshotIdLength': len(receipt['snapshot']),
                          'exactSyntheticUnitRetrieved': True, 'productionOffHostAcceptance': False}))
    finally:
        case.doCleanups()


if __name__ == '__main__':
    main()
