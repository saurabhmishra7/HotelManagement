#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
scanner="${1:-trivy}"
mkdir -p security-reports
bash scripts/package-appsail.sh
status=0
"$scanner" rootfs --scanners vuln --list-all-pkgs --severity HIGH,CRITICAL --exit-code 1 --format json --output security-reports/vulnerabilities.json target/appsail || status=$?
python3 - <<'PY'
import json
with open('security-reports/vulnerabilities.json') as report:
    results = json.load(report).get('Results') or []
packages = [p for r in results if r.get('Type') == 'jar' for p in r.get('Packages', [])]
if not packages:
    raise SystemExit('Java packages were not detected; refusing an empty vulnerability scan')
print(f'Inspected {len(packages)} packaged Java dependencies')
PY
exit "$status"
