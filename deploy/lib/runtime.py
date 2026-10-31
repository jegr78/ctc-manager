#!/usr/bin/env python3
import os
from pathlib import Path
import sys

from config import load


def compose_spec(installation, config, deploy_root):
    environment = dict(os.environ)
    fields = {
        'CTC_APP_IMAGE': 'appImage', 'CTC_DATABASE_IMAGE': 'databaseImage',
        'CTC_APP_ENV_FILE': 'appEnvFile', 'CTC_DB_PASSWORD_FILE': 'databasePasswordFile',
        'CTC_DB_ROOT_PASSWORD_FILE': 'databaseRootPasswordFile', 'CTC_GOOGLE_FILE': 'googleCredentialsFile',
    }
    environment.update({key: config[field] for key, field in fields.items()})
    environment['CTC_OUTBOUND_ENABLED'] = 'false' if config.get('outboundPaused', False) else 'true'
    generation = config.get('storageGeneration')
    prefix = 'ctc-manager_' + (generation + '_' if generation else '')
    for volume in ['database', 'data', 'logs', 'credentials']:
        environment['CTC_' + volume.upper() + '_VOLUME'] = prefix + volume
    arguments = ['docker', '--host', config['dockerHost'], 'compose', '--project-name', 'ctc-manager',
                 '--project-directory', str(installation), '--env-file', '/dev/null',
                 '-f', str(Path(deploy_root) / 'compose.yaml')]
    return arguments, environment


def main():
    installation, config = load(sys.argv[1])
    arguments, environment = compose_spec(installation, config, sys.argv[2])
    os.chdir(installation)
    os.execvpe(arguments[0], arguments + sys.argv[3:], environment)


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, KeyError) as error:
        sys.exit('CTC: ' + str(error))
