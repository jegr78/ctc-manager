import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class PublishBundleTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.remote = self.base / 'remote'
        self.remote.mkdir()
        self.candidate = self.base / 'candidate'
        self.candidate.mkdir()
        self.names = ['ctc-manager-deployment-1.21.0.tar.gz', 'ctc-manager-deployment-1.21.0.tar.gz.sha256']
        for name in self.names:
            (self.candidate / name).write_text('original')
        fake = self.base / 'gh'
        fake.write_text('''#!/usr/bin/env python3
import json, os, pathlib, shutil, sys
remote = pathlib.Path(os.environ['REMOTE'])
if sys.argv[1:3] == ['release','view']:
 print(json.dumps({'assets':[{'name':p.name} for p in remote.iterdir()]}))
elif sys.argv[1:3] == ['release','download']:
 pattern=sys.argv[sys.argv.index('--pattern')+1]
 directory=sys.argv[sys.argv.index('--dir')+1]
 shutil.copyfile(remote/pattern,pathlib.Path(directory)/pattern)
elif sys.argv[1:3] == ['release','upload']:
 source=pathlib.Path(sys.argv[4])
 destination=remote/source.name
 if destination.exists(): sys.exit('refusing asset replacement')
 with open(os.environ['MUTATION'],'a') as output: output.write(source.name+'\\n')
 shutil.copyfile(source,destination)
else: sys.exit('unexpected command')
''')
        fake.chmod(0o700)

    def publish(self):
        env = {**os.environ, 'PATH': str(self.base)+os.pathsep+os.environ['PATH'],
               'REMOTE':str(self.remote), 'MUTATION':str(self.base/'mutation')}
        return subprocess.run(['bash', str(ROOT/'scripts/ci/publish-deployment.sh'),
                               '1.21.0', str(self.candidate)], env=env, capture_output=True, text=True)

    def test_completed_release_is_not_replaced_by_changed_dependency(self):
        for name in self.names:
            (self.remote / name).write_text('original')
            (self.candidate / name).write_text('changed')
        result = self.publish()
        self.assertNotEqual(0,result.returncode)
        self.assertIn('differs',result.stderr)
        self.assertFalse((self.base/'mutation').exists())
        self.assertEqual('original',(self.remote/self.names[0]).read_text())

    def test_missing_assets_uploaded_once_and_identical_retry_does_not_write(self):
        first = self.publish()
        self.assertEqual(0,first.returncode,first.stderr)
        self.assertEqual(set(self.names),set(p.name for p in self.remote.iterdir()))
        before = (self.base/'mutation').read_text()
        retry = self.publish()
        self.assertEqual(0,retry.returncode,retry.stderr)
        self.assertEqual(before,(self.base/'mutation').read_text())

    def test_partial_upload_resumes_only_missing_asset(self):
        (self.remote/self.names[0]).write_text('original')
        result = self.publish()
        self.assertEqual(0,result.returncode,result.stderr)
        self.assertEqual(self.names[1]+'\n',(self.base/'mutation').read_text())
