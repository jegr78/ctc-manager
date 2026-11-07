from pathlib import Path
import subprocess
import sys
from types import SimpleNamespace
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'lib'))
import rollout


class PhpMyAdminTest(unittest.TestCase):
    def fake(self, state, fail_start=False):
        calls = []
        def run(command, **kwargs):
            calls.append(command[2])
            if command[2] == 'start' and fail_start:
                raise subprocess.CalledProcessError(1, command)
            return SimpleNamespace(stdout=state + '\n', returncode=0)
        return calls, run

    def test_phpmyadmin_restarts_only_if_it_ran(self):
        for state, expected in [('inactive', ['show']), ('active', ['show', 'stop', 'start'])]:
            with self.subTest(state=state):
                calls, run = self.fake(state)
                with self.assertRaisesRegex(RuntimeError, 'body'):
                    with rollout.phpmyadmin_stopped(run):
                        raise RuntimeError('body')
                self.assertEqual(expected, calls)

    def test_failed_start_after_a_failure_keeps_the_original_error(self):
        calls, run = self.fake('active', fail_start=True)
        with self.assertRaisesRegex(RuntimeError, 'body') as caught:
            with rollout.phpmyadmin_stopped(run):
                raise RuntimeError('body')
        self.assertTrue(any('phpMyAdmin' in note for note in caught.exception.__notes__))

    def test_failed_start_after_success_only_warns(self):
        import contextlib, io
        calls, run = self.fake('active', fail_start=True)
        stderr = io.StringIO()
        with contextlib.redirect_stderr(stderr):
            with rollout.phpmyadmin_stopped(run):
                pass
        self.assertIn('phpMyAdmin', stderr.getvalue())


if __name__ == '__main__':
    unittest.main()
