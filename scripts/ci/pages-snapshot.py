#!/usr/bin/env python3
import os
import re
import subprocess
import sys


def git(*arguments):
    result = subprocess.run(['git', *arguments], check=True, capture_output=True, text=True)
    return result.stdout.strip()


def main():
    expected = os.environ.get('CTC_SITE_TREE', '')
    if not expected:
        markers = re.findall(r'^CTC-Site-Tree: ([a-f0-9]{40})$', git('log', '-1', '--format=%B'), re.MULTILINE)
        if len(markers) != 1:
            raise ValueError('Pages requires one explicitly approved website tree')
        expected = markers[0]
    if not re.fullmatch('[a-f0-9]{40}', expected):
        raise ValueError('invalid approved website tree')
    commit = os.environ.get('CTC_SITE_COMMIT', '')
    if commit and (not re.fullmatch('[a-f0-9]{40}', commit) or git('rev-parse', 'HEAD') != commit):
        raise ValueError('Pages checkout differs from the approved commit')
    if git('rev-parse', 'HEAD:docs/site') != expected:
        raise ValueError('Pages website tree differs from the approved snapshot')
    print('Pages website tree matches the approved snapshot')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, subprocess.CalledProcessError) as error:
        sys.exit(str(error))
