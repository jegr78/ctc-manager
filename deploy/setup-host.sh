#!/usr/bin/env bash
set -euo pipefail
umask 077
setup_root=$(cd "$(dirname "$0")" && pwd -P)
case "${1:---check}" in
  --check) operation=check ;;
  --apply) operation=apply ;;
  *) printf 'usage: setup-host.sh --check|--apply --backup-uuid UUID\n' >&2; exit 1 ;;
esac
if [ "$#" -gt 0 ]; then shift; fi
exec python3 "$setup_root/lib/host.py" "$operation" "$@"
