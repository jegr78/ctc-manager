#!/usr/bin/env python3
import json, os, subprocess, sys, time, urllib.request
from pathlib import Path
ROOT = Path('/srv/ctc-manager')
HERE = Path(__file__).resolve().parent
NAME = 'ctc-manager-phpmyadmin'
IMAGE = 'phpmyadmin@sha256:f54a37b908b26caa7032c4eb0c7f3495fdd90208a06eed9c4b2c8e691dd1662c'
config = json.loads((ROOT / 'config/installation.json').read_text())
DOCKER = ['docker', '--host', config['dockerHost']]
def docker(*args, check=True):
    return subprocess.run(DOCKER + list(args), check=check, capture_output=True, text=True, timeout=90)
def existing():
    result = docker('inspect', NAME, check=False)
    if result.returncode:
        if 'no such' not in result.stderr.lower(): raise RuntimeError('Cannot inspect phpMyAdmin container')
        return None
    item=json.loads(result.stdout)[0]
    if item['Config']['Labels'].get('org.ctc.operator-service') != 'phpmyadmin':
        raise RuntimeError('Container name is owned by another service')
    return item
mode=sys.argv[1]
if mode == 'start':
    if existing(): raise RuntimeError('Existing phpMyAdmin container must be stopped first')
    db=json.loads(docker('inspect','ctc-manager-db-1').stdout)[0]
    if not db['State']['Running']: raise RuntimeError('CTC database is stopped')
    networks=list(db['NetworkSettings']['Networks'])
    if len(networks)!=1: raise RuntimeError('Unexpected database network configuration')
    private=networks[0]
    if not json.loads(docker('network','inspect',private).stdout)[0]['Internal']:
        raise RuntimeError('Database network is not private')
    web='ctc-manager-phpmyadmin-web'
    net=docker('network','inspect',web,check=False)
    if net.returncode:
        docker('network','create','--label','org.ctc.operator-service=phpmyadmin',web)
    elif json.loads(net.stdout)[0]['Labels'].get('org.ctc.operator-service') != 'phpmyadmin':
        raise RuntimeError('Web network is owned by another service')
    secret=Path(config['databasePasswordFile'])
    if not secret.is_file() or secret.stat().st_mode & 0o077:
        raise RuntimeError('Database secret must remain private')
    docker('run','--detach','--rm','--name',NAME,
        '--label','org.ctc.operator-service=phpmyadmin',
        '--memory','512m','--pids-limit','200','--security-opt','no-new-privileges',
        '--log-opt','max-size=10m','--log-opt','max-file=3',
        '--network',web,'--network',private,'--publish','127.0.0.1:9093:80',
        '--mount',f'type=bind,src={secret},dst=/run/secrets/db-password,readonly',
        '--mount',f'type=bind,src={HERE}/config.user.inc.php,dst=/etc/phpmyadmin/config.user.inc.php,readonly',
        '--tmpfs','/sessions:rw,noexec,nosuid,size=32m,uid=33,gid=33,mode=0700',
        '--env','PMA_HOST=db','--env','PMA_USER=ctc',
        '--env','PMA_PASSWORD_FILE=/run/secrets/db-password',
        '--env','PMA_ABSOLUTE_URI=https://jegr-linux-box.taile09c97.ts.net:9444/',
        '--env','PMA_VERBOSE=CTC Manager', '--env','MEMORY_LIMIT=256M',
        '--env','UPLOAD_LIMIT=64M', '--env','HIDE_PHP_VERSION=1',IMAGE)
    opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
    for _ in range(60):
        try:
            with opener.open('http://127.0.0.1:9093/',timeout=2) as response:
                body=response.read().decode()
                if response.status==200 and 'ctcdb' in body and 'pma_username' not in body:
                    print('phpMyAdmin ready with automatic ctcdb login',flush=True)
                    break
        except OSError: pass
        time.sleep(1)
    else: raise RuntimeError('phpMyAdmin automatic login did not become ready')
elif mode == 'wait':
    result=docker('inspect', NAME, check=False)
    if result.returncode: sys.exit(1)
    result=subprocess.run(DOCKER+['wait', NAME],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    sys.exit(1)
elif mode == 'stop':
    if existing(): docker('stop','--time','20',NAME)
else: raise ValueError('Unsupported operation')
