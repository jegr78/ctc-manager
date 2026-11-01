#!/usr/bin/env python3
"""Exercise unreleased capture code while retaining an installed release's real source provenance."""
import argparse
import json
import os
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
from config import load
from recovery import NativeRuntime, capture, installed_release, restore_check, validate_unit


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--directory', required=True)
    parser.add_argument('--source-release', required=True)
    parser.add_argument('--destination', required=True)
    args = parser.parse_args()
    os.umask(0o077)
    runtime = NativeRuntime.__new__(NativeRuntime)
    runtime.root, runtime.config = load(args.directory)
    runtime.deploy_root = Path(args.source_release).resolve()
    runtime.release = installed_release(runtime.deploy_root)
    assert all(runtime.config[key] == runtime.release[key] for key in ['appImage', 'databaseImage']), 'retain actual source image provenance'
    runtime.diagnostic = runtime.root / 'state/qualification-capture.log'
    runtime.diagnostic.touch(mode=0o600)
    assert (runtime.root / '.ctc-manager.lock/owner').is_file(), 'caller must hold the shared installation lock'
    assert runtime.is_app_running(), 'qualification starts from a running app'
    destination = Path(args.destination)
    completed = capture(runtime, destination)
    manifest = validate_unit(completed)
    assert runtime.is_app_running(), 'standalone capture must restore the prior running state'
    try:
        restore_check(runtime, completed)
    except ValueError as error:
        assert 'recovery capability' in str(error), 'historical source must fail explicit image admission'
    else:
        raise AssertionError('historical source image unexpectedly accepted for full restore')
    original_export = runtime.export
    def failed_export(staging):
        raise ValueError('intentional capture failure')
    runtime.export = failed_export
    before = {p.name for p in destination.glob('recovery-*')}
    try:
        capture(runtime, destination)
        raise AssertionError('intentional failed capture succeeded')
    except ValueError as error:
        assert str(error) == 'intentional capture failure'
    finally:
        runtime.export = original_export
    assert runtime.is_app_running(), 'handled failure must restore the app'
    assert before == {p.name for p in destination.glob('recovery-*')}, 'failed capture must publish no unit'
    try:
        runtime.stop_app()
        stopped_unit = capture(runtime, destination)
        assert not runtime.is_app_running(), 'originally stopped app must remain stopped'
        validate_unit(stopped_unit)
    finally:
        runtime.start_app()
    try:
        held_unit = capture(runtime, destination, hold_stopped=True)
        assert not runtime.is_app_running(), 'update capture must keep the app stopped'
        validate_unit(held_unit)
    finally:
        runtime.start_app()
    print(json.dumps({'status': 'passed', 'sourceRelease': manifest['releaseVersion'], 'unit': str(completed),
                      'tables': len(manifest['records']), 'historicalFullRestore': 'refused',
                      'handledFailure': 'app-restored', 'originallyStopped': 'preserved',
                      'holdStopped': 'preserved', 'macSourceTouched': False}))


if __name__ == '__main__':
    main()
